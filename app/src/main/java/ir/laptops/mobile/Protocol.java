package ir.laptops.mobile;

import org.json.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** The desktop API's UUIDs, changed-field patches and bounded counter deltas. */
public final class Protocol {
    private Protocol() {}
    private static Set<String> set(String... values){return new HashSet<>(Arrays.asList(values));}
    public static String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
    public static String now() { return OffsetDateTime.now(ZoneOffset.UTC).toString(); }
    public static JSONObject copy(JSONObject value) throws JSONException { return new JSONObject(value.toString()); }
    public static JSONObject row(Object... pairs) throws JSONException {
        JSONObject result=new JSONObject();
        for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]==null?JSONObject.NULL:pairs[i+1]);
        return result;
    }
    public static List<JSONObject> list(JSONArray array) throws JSONException {
        List<JSONObject> result=new ArrayList<>();for(int i=0;i<array.length();i++)result.add(array.getJSONObject(i));return result;
    }
    public static String key(String table,String id) { return table+":"+id; }
    public static String normalizedLaptop(String value) {
        String from="۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩",to="01234567890123456789";
        StringBuilder result=new StringBuilder();for(char c:value.trim().toLowerCase(Locale.ROOT).toCharArray()){int p=from.indexOf(c);result.append(p<0?c:to.charAt(p));}return result.toString();
    }
    public static Set<String> counters(String table) {
        if(table.equals("inventory_loans"))return set("returned","missing");
        if(table.equals("inventory_missing"))return set("recovered");return set();
    }
    public static JSONObject patch(String table,JSONObject current,JSONObject before,JSONObject after) throws JSONException {
        JSONObject result=copy(current);
        for(Iterator<String> keys=after.keys();keys.hasNext();) {
            String field=keys.next();if(Objects.equals(before.opt(field),after.opt(field)))continue;
            if(counters(table).contains(field)) {
                int delta=after.getInt(field)-before.getInt(field);
                if(delta<=0)throw new IllegalArgumentException("تغییر تعداد باید افزایشی باشد.");
                result.put(field,current.getInt(field)+delta);
            } else result.put(field,after.get(field));
        }
        return result;
    }
    public static Map<String,JSONObject> apply(Map<String,JSONObject> state,JSONArray entries) throws JSONException {
        Map<String,JSONObject> candidate=new LinkedHashMap<>(state);
        for(JSONObject entry:list(entries)) {
            String table=entry.getString("table"),key=key(table,entry.getString("entity_id"));
            JSONObject before=entry.optJSONObject("before"),after=entry.optJSONObject("after");
            if(before==null) {
                if(after==null||candidate.containsKey(key))throw new IllegalArgumentException("شناسه رکورد قبلاً ثبت شده است.");
                candidate.put(key,copy(after));
            } else {
                if(!candidate.containsKey(key))throw new IllegalArgumentException("این رکورد حذف شده است؛ تغییر شما برای بررسی حفظ شد.");
                if(after==null)candidate.remove(key);else candidate.put(key,patch(table,candidate.get(key),before,after));
            }
        }
        validate(candidate);return candidate;
    }
    public static int available(Map<String,JSONObject> state,String itemId) throws JSONException {
        JSONObject item=state.get(key("inventory_items",itemId));if(item==null)return 0;
        int reserved=0;
        for(Map.Entry<String,JSONObject> e:state.entrySet()) {
            JSONObject row=e.getValue();
            if(!itemId.equals(row.optString("item_id")))continue;
            if(e.getKey().startsWith("inventory_loans:"))reserved+=row.getInt("quantity")-row.getInt("returned")-row.getInt("missing");
            if(e.getKey().startsWith("inventory_missing:"))reserved+=row.getInt("quantity")-row.getInt("recovered");
        }
        return item.getInt("total")-reserved;
    }
    public static void validate(Map<String,JSONObject> state) throws JSONException {
        Set<String> active=new HashSet<>();Map<String,String> names=new HashMap<>();
        for(Map.Entry<String,JSONObject> e:state.entrySet()) {
            String table=e.getKey().split(":",2)[0];JSONObject r=e.getValue();
            if(table.equals("loans")) {
                if(r.optString("status").equals("ACTIVE")&&!active.add(normalizedLaptop(r.getString("laptop_number"))))throw new IllegalArgumentException("این لپ‌تاپ هنوز در امانت است؛ ابتدا بازگشت آن را ثبت کنید.");
            }
            if(set("stock_types","preset_teachers","preset_grades").contains(table)) {
                String name=table+":"+r.getString("name").trim();if(names.put(name,e.getKey())!=null)throw new IllegalArgumentException("این نام قبلاً ثبت شده است.");
            }
            if(table.equals("inventory_items")) {
                if(!state.containsKey(key("stock_types",r.getString("type_id"))))throw new IllegalArgumentException("نوع کالا حذف شده است.");
                String code=r.optString("code").trim();if(!code.isEmpty()&&names.put("code:"+code,e.getKey())!=null)throw new IllegalArgumentException("کد کالا باید یکتا باشد.");
                if(r.getInt("total")<0||available(state,e.getKey().split(":",2)[1])<0)throw new IllegalArgumentException("موجودی کافی نیست یا تعداد کل از امانت و مفقودی کمتر است.");
            }
            if(set("inventory_loans","inventory_missing","inventory_events").contains(table)&&!state.containsKey(key("inventory_items",r.getString("item_id"))))throw new IllegalArgumentException("کالای وابسته حذف شده است.");
            if(table.equals("inventory_loans")&&(r.getInt("quantity")<=0||r.getInt("returned")<0||r.getInt("missing")<0||r.getInt("returned")+r.getInt("missing")>r.getInt("quantity")))throw new IllegalArgumentException("تعداد تحویل یا مفقودی از امانت بیشتر است.");
            if(table.equals("inventory_missing")) {
                if(r.getInt("quantity")<=0||r.getInt("recovered")<0||r.getInt("recovered")>r.getInt("quantity"))throw new IllegalArgumentException("تعداد پیدا شده معتبر نیست.");
                String loan=r.optString("loan_id");if(!loan.isEmpty()&&!state.containsKey(key("inventory_loans",loan)))throw new IllegalArgumentException("امانت وابسته حذف شده است.");
            }
        }
    }
}
