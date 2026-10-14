package ir.laptops.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Atomic journal + canonical server cache. UUIDs never depend on display row numbers. */
public final class Store extends SQLiteOpenHelper {
    private static Set<String> set(String... values){return new HashSet<>(Arrays.asList(values));}
    public Store(Context context){this(context,"mobile.db");}
    Store(Context context,String database){super(context,database,null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){try(Cursor timeout=db.rawQuery("PRAGMA busy_timeout=5000",null)){timeout.moveToFirst();}db.execSQL("PRAGMA synchronous=FULL");}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE shadow(table_name TEXT NOT NULL,entity_id TEXT NOT NULL,revision INTEGER NOT NULL,deleted INTEGER NOT NULL,body TEXT,PRIMARY KEY(table_name,entity_id))");
        db.execSQL("CREATE TABLE outbox(seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,user_id TEXT NOT NULL,device_id TEXT NOT NULL,operation TEXT NOT NULL,status TEXT NOT NULL,message TEXT NOT NULL,payload TEXT NOT NULL,result TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){throw new IllegalStateException("Unsupported database version");}
    public synchronized String setting(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized void setting(String key,String value){getWritableDatabase().execSQL("INSERT OR REPLACE INTO settings VALUES(?,?)",new Object[]{key,value});}
    public synchronized String device(){String id=setting("device_id");if(id.isEmpty()){id=Protocol.uuid();setting("device_id",id);}return id;}
    public synchronized void user(JSONObject user) throws JSONException {
        String old=setting("user_id"),id=user.getString("id");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(!old.isEmpty()&&!old.equals(id)) {
                if(count("pending")+count("conflict")>0)throw new IllegalStateException("تغییرات حساب قبلی باقی مانده است؛ ابتدا با همان حساب وارد شوید.");
                db.execSQL("DELETE FROM shadow");setting("instance_id","");setting("cursor","0");
            }
            for(String field:Arrays.asList("id","username","display_name","role"))setting(field.equals("id")?"user_id":field,user.getString(field));
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }
    public synchronized int count(String status){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM outbox WHERE status=?",new String[]{status})){c.moveToFirst();return c.getInt(0);}}
    public synchronized List<JSONObject> conflicts() throws JSONException {
        List<JSONObject> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,message,payload,result FROM outbox WHERE status='conflict' ORDER BY seq",null)) {
            while(c.moveToNext())rows.add(Protocol.row("id",c.getString(0),"message",c.getString(1),"payload",new JSONObject(c.getString(2)),"result",c.isNull(3)?null:new JSONObject(c.getString(3))));
        }return rows;
    }
    public synchronized Map<String,JSONObject> state() throws JSONException {
        Map<String,JSONObject> rows=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT table_name,entity_id,body FROM shadow WHERE deleted=0",null)){while(c.moveToNext())rows.put(Protocol.key(c.getString(0),c.getString(1)),new JSONObject(c.getString(2)));}
        List<String> invalid=new ArrayList<>();List<String> errors=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,payload FROM outbox WHERE status='pending' ORDER BY seq",null)) {
            while(c.moveToNext())try{rows=Protocol.apply(rows,new JSONObject(c.getString(1)).getJSONArray("entries"));}catch(IllegalArgumentException error){invalid.add(c.getString(0));errors.add(error.getMessage());}
        }
        for(int i=0;i<invalid.size();i++)getWritableDatabase().execSQL("UPDATE outbox SET status='conflict',message=? WHERE id=?",new Object[]{errors.get(i),invalid.get(i)});
        return rows;
    }
    public synchronized JSONArray entities(String table) throws JSONException {
        JSONArray result=new JSONArray();for(Map.Entry<String,JSONObject> e:state().entrySet())if(e.getKey().startsWith(table+":"))result.put(Protocol.row("id",e.getKey().substring(table.length()+1),"data",e.getValue()));return result;
    }
    public synchronized JSONObject data(String table,String id) throws JSONException {JSONObject row=state().get(Protocol.key(table,id));if(row==null)throw new IllegalArgumentException("این رکورد دیگر وجود ندارد.");return Protocol.copy(row);}
    public synchronized JSONObject edit(String table,String id,JSONObject before,JSONObject after) throws JSONException {
        int revision=0;try(Cursor c=getReadableDatabase().rawQuery("SELECT revision FROM shadow WHERE table_name=? AND entity_id=?",new String[]{table,id})){if(c.moveToFirst())revision=c.getInt(0);}
        JSONObject entry=Protocol.row("table",table,"entity_id",id,"before",before,"after",after,"revision",revision);
        // Dependent local writes must reference the precise earlier receipt.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,payload,status FROM outbox WHERE status IN ('pending','conflict') ORDER BY seq DESC",null)) {
            outer:while(c.moveToNext())for(JSONObject prior:Protocol.list(new JSONObject(c.getString(1)).getJSONArray("entries")))if(table.equals(prior.getString("table"))&&id.equals(prior.getString("entity_id"))) {
                if(c.getString(2).equals("conflict"))throw new IllegalArgumentException("تغییر قبلی این رکورد نیاز به بررسی دارد؛ از تنظیمات دوباره ارسال کنید.");
                entry.put("after_batch",c.getString(0));break outer;
            }
        }return entry;
    }
    public synchronized void enqueue(String operation,JSONArray entries) throws JSONException {
        if(setting("user_id").isEmpty()||setting("role").equals("viewer"))throw new IllegalStateException("دسترسی ویرایش ندارید.");
        if(!setting("role").equals("admin")&&!set("addLoan","updateLoan","updateLoanNotes","returnLoan","lendInventory","returnInventory","reportMissingStock","recoverMissingStock").contains(operation))throw new IllegalStateException("این عملیات به حساب مدیر نیاز دارد.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            Protocol.apply(state(),entries);String id=Protocol.uuid();
            JSONObject batch=Protocol.row("id",id,"device_id",device(),"user_id",setting("user_id"),"operation",operation,"protocol",1,"entries",entries);
            db.execSQL("INSERT INTO outbox(id,user_id,device_id,operation,status,message,payload) VALUES(?,?,?,?,'pending','',?)",new Object[]{id,setting("user_id"),device(),operation,batch.toString()});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized JSONObject next() throws JSONException {try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM outbox WHERE status='pending' AND user_id=? ORDER BY seq LIMIT 1",new String[]{setting("user_id")})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    private void updates(JSONArray updates) throws JSONException {
        for(JSONObject row:Protocol.list(updates)) {
            String table=row.getString("table"),id=row.getString("entity_id");boolean deleted=row.getBoolean("deleted");
            try(Cursor prior=getReadableDatabase().rawQuery("SELECT revision FROM shadow WHERE table_name=? AND entity_id=?",new String[]{table,id})) {if(prior.moveToFirst()&&prior.getInt(0)>row.getInt("revision"))continue;}
            getWritableDatabase().execSQL("INSERT OR REPLACE INTO shadow VALUES(?,?,?,?,?)",new Object[]{table,id,row.getInt("revision"),deleted?1:0,deleted?null:row.getJSONObject("data").toString()});
        }
    }
    public synchronized void snapshot(JSONObject snapshot) throws JSONException {
        if(snapshot.getInt("protocol")!=1)throw new IllegalArgumentException("نسخه سرور سازگار نیست.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            String prior=setting("instance_id");if(!prior.isEmpty()&&!prior.equals(snapshot.getString("instance_id")))db.execSQL("UPDATE outbox SET status='conflict',message='نسخه سرور پس از بازیابی تغییر کرده است؛ تغییر محلی حفظ شد.' WHERE status='pending'");
            db.execSQL("DELETE FROM shadow");updates(snapshot.getJSONArray("entities"));setting("instance_id",snapshot.getString("instance_id"));setting("cursor",Integer.toString(snapshot.getInt("cursor")));setting("role",snapshot.getString("role"));state();db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void feed(JSONObject feed) throws JSONException {
        if(!setting("instance_id").equals(feed.getString("instance_id")))throw new IllegalArgumentException("نسخه سرور تغییر کرده است.");
        if(feed.getInt("cursor")<Integer.parseInt(setting("cursor")))throw new IllegalArgumentException("ترتیب دریافت اطلاعات معتبر نیست.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            for(JSONObject tx:Protocol.list(feed.getJSONArray("transactions"))) {
                updates(tx.getJSONArray("updates"));
                if(setting("user_id").equals(tx.optString("user_id"))&&device().equals(tx.optString("device_id"))) {
                    JSONObject receipt=Protocol.row("id",tx.optString("batch_id"),"status","accepted","updates",tx.getJSONArray("updates"),"cursor",tx.getInt("cursor"));
                    db.execSQL("UPDATE outbox SET status='accepted',message='',result=? WHERE id=? AND user_id=? AND device_id=? AND status='pending'",new Object[]{receipt.toString(),tx.optString("batch_id"),setting("user_id"),device()});
                }
            }
            setting("cursor",Integer.toString(feed.getInt("cursor")));setting("role",feed.getString("role"));state();db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void result(JSONObject response) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            String status=response.getString("status");if(!status.equals("accepted")&&!status.equals("conflict"))throw new IllegalArgumentException("پاسخ ارسال معتبر نیست.");
            updates(response.getJSONArray("updates"));db.execSQL("UPDATE outbox SET status=?,message=?,result=? WHERE id=? AND status='pending'",new Object[]{status,response.optString("message"),response.toString(),response.getString("id")});state();db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void discard(String id){getWritableDatabase().execSQL("UPDATE outbox SET status='discarded' WHERE id=? AND status='conflict' AND user_id=?",new Object[]{id,setting("user_id")});}
    public synchronized void retry(String id) throws JSONException {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            JSONObject original=null,response=null;
            try(Cursor c=db.rawQuery("SELECT payload,result FROM outbox WHERE id=? AND status='conflict' AND user_id=?",new String[]{id,setting("user_id")})){if(c.moveToFirst()){original=new JSONObject(c.getString(0));response=c.isNull(1)?new JSONObject():new JSONObject(c.getString(1));}}
            if(original==null)throw new IllegalArgumentException("تغییر انتخاب‌شده پیدا نشد.");JSONArray entries=new JSONArray();Map<String,JSONObject> current=state();
            discard(id);
            for(JSONObject e:Protocol.list(original.getJSONArray("entries"))) {
                String table=e.getString("table"),entity=e.getString("entity_id");JSONObject before=e.optJSONObject("before"),after=e.optJSONObject("after"),row=current.get(Protocol.key(table,entity));
                if(before==null) {
                    if(row!=null)throw new IllegalArgumentException("این رکورد وجود دارد؛ آن را از فهرست ویرایش کنید.");
                    if(response.optString("code").equals("ALREADY_EXISTS"))entity=Protocol.uuid();
                    entries.put(edit(table,entity,null,after));
                } else {if(row==null)throw new IllegalArgumentException("رکورد حذف شده است.");entries.put(edit(table,entity,row,after==null?null:Protocol.patch(table,row,before,after)));}
            }
            // Exclude the original before building the replacement's dependency links.
            for(JSONObject e:Protocol.list(entries))e.remove("after_batch");
            enqueue(original.getString("operation"),entries);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
