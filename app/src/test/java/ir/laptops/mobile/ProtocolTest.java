package ir.laptops.mobile;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void changedFieldsDoNotUndoConcurrentReturn() throws Exception {
        JSONObject before=Protocol.row("notes","Old","status","ACTIVE","returner_name",null);
        JSONObject current=Protocol.copy(before).put("status","RETURNED").put("returner_name","Other person");
        JSONObject desired=Protocol.copy(before).put("notes","My note");JSONObject merged=Protocol.patch("loans",current,before,desired);
        assertEquals("My note",merged.getString("notes"));assertEquals("RETURNED",merged.getString("status"));assertEquals("Other person",merged.getString("returner_name"));
    }
    @Test public void returnCountersMergeAsDeltas() throws Exception {
        JSONObject before=Protocol.row("returned",0,"missing",0),current=Protocol.row("returned",2,"missing",0),after=Protocol.row("returned",1,"missing",0);
        assertEquals(3,Protocol.patch("inventory_loans",current,before,after).getInt("returned"));
    }
    @Test public void uuidAndLaptopNormalizationMatchDesktop() {assertTrue(Protocol.uuid().matches("[0-9a-f]{32}"));assertEquals("12",Protocol.normalizedLaptop(" ۱۲ "));assertEquals("12",Protocol.normalizedLaptop("١٢"));}
    @Test public void apiRejectsInsecureProductionEndpoint(){try{new Api("http://13.42.221.242");fail("plaintext endpoint");}catch(IllegalArgumentException expected){}}
}
