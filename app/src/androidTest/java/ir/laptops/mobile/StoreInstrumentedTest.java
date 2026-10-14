package ir.laptops.mobile;

import android.test.AndroidTestCase;
import org.json.*;
import java.util.*;

/** All test writes use uniquely named local databases; never the shared school server. */
public class StoreInstrumentedTest extends AndroidTestCase {
    private Store store;private String database;
    @Override protected void setUp() throws Exception {super.setUp();database="test-"+Protocol.uuid()+".db";store=new Store(getContext(),database);store.user(Protocol.row("id",Protocol.uuid(),"username","test","display_name","Test","role","admin"));store.device();}
    @Override protected void tearDown() throws Exception {store.close();getContext().deleteDatabase(database);super.tearDown();}
    private JSONObject update(String table,String id,JSONObject data,int revision) throws Exception{return Protocol.row("table",table,"entity_id",id,"data",data,"revision",revision,"deleted",false);}
    private JSONObject accepted(JSONObject batch) throws Exception {
        JSONArray updates=new JSONArray();for(JSONObject entry:Protocol.list(batch.getJSONArray("entries")))updates.put(update(entry.getString("table"),entry.getString("entity_id"),entry.getJSONObject("after"),entry.getInt("revision")+1));
        return Protocol.row("id",batch.getString("id"),"status","accepted","updates",updates);
    }
    private String stock() throws Exception {
        String type=Protocol.uuid(),item=Protocol.uuid();JSONObject t=Protocol.row("name","Cable","brand","","model","","description","");JSONObject i=Protocol.row("type_id",type,"name","USB","brand","","model","","code","","description","","location","","total",4,"low_stock_threshold",0);
        store.enqueue("saveInventoryItem",new JSONArray().put(store.edit("stock_types",type,null,t)).put(store.edit("inventory_items",item,null,i)));store.result(accepted(store.next()));return item;
    }
    public void testOfflineRestartDependenciesAndLostFullReturnAcknowledgement() throws Exception {
        String item=stock(),loan=Protocol.uuid();JSONObject row=Protocol.row("item_id",item,"borrower","Person","quantity",1,"returned",0,"missing",0,"lend_time",Protocol.now(),"due_time",null,"last_return_time",null,"notes","");
        store.enqueue("lendInventory",new JSONArray().put(store.edit("inventory_loans",loan,null,row)));JSONObject lend=store.next();JSONObject returned=Protocol.copy(row).put("returned",1).put("last_return_time",Protocol.now());
        store.enqueue("returnInventory",new JSONArray().put(store.edit("inventory_loans",loan,row,returned)));assertEquals(2,store.count("pending"));store.close();store=new Store(getContext(),database);assertEquals(lend.toString(),store.next().toString());assertEquals(4,Protocol.available(store.state(),item));
        store.result(accepted(lend));JSONObject returning=store.next();assertEquals(lend.getString("id"),returning.getJSONArray("entries").getJSONObject(0).getString("after_batch"));
        store.setting("instance_id","test-instance");store.setting("cursor","0");JSONObject tx=Protocol.row("batch_id",returning.getString("id"),"user_id",store.setting("user_id"),"device_id",store.device(),"cursor",1,"updates",new JSONArray().put(update("inventory_loans",loan,returned,2)));
        store.feed(Protocol.row("instance_id","test-instance","cursor",1,"role","admin","transactions",new JSONArray().put(tx)));assertEquals(0,store.count("pending"));assertEquals(0,store.count("conflict"));assertEquals(4,Protocol.available(store.state(),item));
        try{store.enqueue("returnInventory",new JSONArray().put(store.edit("inventory_loans",loan,returned,Protocol.copy(returned).put("returned",2))));fail("over-return accepted");}catch(IllegalArgumentException expected){}assertEquals(0,store.count("pending"));
    }
    public void testIncomingRefreshKeepsPendingDescriptionAndUntouchedLocation() throws Exception {
        String item=stock();JSONObject original=store.data("inventory_items",item);JSONObject edit=Protocol.copy(original).put("description","My description");store.enqueue("saveInventoryItem",new JSONArray().put(store.edit("inventory_items",item,original,edit)));JSONObject batch=store.next();
        store.setting("instance_id","instance");store.setting("cursor","0");JSONObject peer=Protocol.copy(original).put("description","Peer description").put("location","Peer shelf");JSONObject tx=Protocol.row("cursor",1,"user_id","another-user","device_id","another-device","batch_id",Protocol.uuid(),"updates",new JSONArray().put(update("inventory_items",item,peer,2)));
        store.feed(Protocol.row("instance_id","instance","cursor",1,"role","admin","transactions",new JSONArray().put(tx)));assertEquals("My description",store.data("inventory_items",item).getString("description"));assertEquals("Peer shelf",store.data("inventory_items",item).getString("location"));
        JSONObject finalRow=Protocol.copy(peer).put("description","My description");store.result(Protocol.row("id",batch.getString("id"),"status","accepted","updates",new JSONArray().put(update("inventory_items",item,finalRow,3))));assertEquals(0,store.count("pending"));assertEquals("My description",store.data("inventory_items",item).getString("description"));
    }
    public void testConflictRetryPreservesPayloadAndViewerIsReadOnly() throws Exception {
        String item=stock();JSONObject before=store.data("inventory_items",item),edited=Protocol.copy(before).put("description","Keep this");store.enqueue("saveInventoryItem",new JSONArray().put(store.edit("inventory_items",item,before,edited)));JSONObject batch=store.next();store.result(Protocol.row("id",batch.getString("id"),"status","conflict","message","Test conflict","updates",new JSONArray()));
        assertEquals(1,store.conflicts().size());store.retry(batch.getString("id"));assertEquals(0,store.count("conflict"));assertEquals(1,store.count("pending"));assertFalse(batch.getString("id").equals(store.next().getString("id")));assertEquals("Keep this",store.data("inventory_items",item).getString("description"));
        store.result(accepted(store.next()));store.setting("role","viewer");try{store.enqueue("addLoan",new JSONArray());fail("viewer write");}catch(IllegalStateException expected){}
    }
    public void testOfflinePasswordAndKeystoreToken() throws Exception {
        String name="test-vault-"+Protocol.uuid();Vault vault=new Vault(getContext(),name);try {
            vault.remember("test-user","test","test-password".toCharArray(),"secret-session-token");assertEquals("secret-session-token",vault.token());assertFalse(getContext().getSharedPreferences(name,0).getString("token","").contains("secret-session-token"));
            try{vault.verify("test-user","test","bad-password".toCharArray());fail("wrong offline password");}catch(IllegalArgumentException expected){}
            vault.verify("test-user","test","test-password".toCharArray());vault.clearToken();assertEquals("",vault.token());vault.verify("test-user","test","test-password".toCharArray());
            vault.invalidate();try{vault.verify("test-user","test","test-password".toCharArray());fail("invalidated verifier");}catch(IllegalArgumentException expected){}
        }finally{getContext().deleteSharedPreferences(name);}
    }
}
