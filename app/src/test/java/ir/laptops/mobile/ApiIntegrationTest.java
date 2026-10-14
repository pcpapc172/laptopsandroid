package ir.laptops.mobile;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.net.ServerSocket;
import java.nio.file.*;
import static org.junit.Assert.*;

/** Tests the real shared API in a temporary loopback fixture, never production. */
public class ApiIntegrationTest {
    @Test public void sameAccountLoansOfflineEditsAndIdempotentReturns() throws Exception {
        File source=new File(System.getProperty("serverSource")),python=new File(source,".server-venv/bin/python");
        Assume.assumeTrue("Set LAPTOPS_SERVER_SOURCE to run API fixture checks",python.isFile());
        int port;try(ServerSocket reservation=new ServerSocket(0)){port=reservation.getLocalPort();}
        Path dir=Files.createTempDirectory("laptops-android-api-");Process server=new ProcessBuilder(python.toString(),"-m","server.test_fixture","--db",dir.resolve("server.db").toString(),"--port",Integer.toString(port)).directory(source).redirectErrorStream(true).redirectOutput(dir.resolve("server.log").toFile()).start();
        try {
            Api api=new Api("http://127.0.0.1:"+port,true);long deadline=System.currentTimeMillis()+15000;boolean ready=false;
            while(System.currentTimeMillis()<deadline){try{api.object("GET","/health",null,"");ready=true;break;}catch(IOException pending){Thread.sleep(50);}}
            assertTrue("fixture listener",ready);String device=Protocol.uuid();JSONObject auth=api.object("POST","/v1/login",Protocol.row("username","admin","password","integration-test-password","device_id",device),"");String token=auth.getString("token"),user=auth.getJSONObject("user").getString("id"),id=Protocol.uuid();assertEquals(1,auth.getInt("protocol"));
            JSONObject loan=Protocol.row("bell_period",1,"grade_level","Grade","teacher_name","Teacher","recipient_name","Borrower","laptop_number","ANDROID-TEST","initial_condition","Healthy","return_condition",null,"returner_name",null,"lend_time",Protocol.now(),"return_time",null,"status","ACTIVE","notes","");
            JSONObject create=batch(user,device,Protocol.row("table","loans","entity_id",id,"before",null,"after",loan,"revision",0));JSONObject accepted=api.object("POST","/v1/batches",create,token);assertEquals("accepted",accepted.getString("status"));assertTrue(sameJson(accepted,api.object("POST","/v1/batches",create,token)));
            JSONObject peerEdit=Protocol.copy(loan).put("teacher_name","Other teacher").put("notes","Other note");assertEquals("accepted",api.object("POST","/v1/batches",batch(user,device,Protocol.row("table","loans","entity_id",id,"before",loan,"after",peerEdit,"revision",1)),token).getString("status"));
            JSONObject offline=Protocol.copy(loan).put("notes","Android offline note");JSONObject merged=api.object("POST","/v1/batches",batch(user,device,Protocol.row("table","loans","entity_id",id,"before",loan,"after",offline,"revision",1)),token);assertEquals("accepted",merged.getString("status"));JSONObject current=merged.getJSONArray("updates").getJSONObject(0).getJSONObject("data");assertEquals("Android offline note",current.getString("notes"));assertEquals("Other teacher",current.getString("teacher_name"));
            JSONObject returned=Protocol.copy(current).put("status","RETURNED").put("returner_name","Returner").put("return_condition","Healthy").put("return_time",Protocol.now());assertEquals("accepted",api.object("POST","/v1/batches",batch(user,device,Protocol.row("table","loans","entity_id",id,"before",current,"after",returned,"revision",3)),token).getString("status"));
            JSONObject snapshot=api.object("GET","/v1/snapshot",null,token);assertEquals("RETURNED",snapshot.getJSONArray("entities").getJSONObject(0).getJSONObject("data").getString("status"));
            JSONObject feed=api.object("GET","/v1/changes?instance_id="+auth.getString("instance_id")+"&after=0",null,token);assertEquals(create.getString("id"),feed.getJSONArray("transactions").getJSONObject(0).getString("batch_id"));api.object("POST","/v1/logout",null,token);
            try{api.object("GET","/v1/snapshot",null,token);fail("logout did not revoke session");}catch(Api.Failure expected){assertEquals(401,expected.status);assertFalse(expected.offlineEligible);}
        } finally {server.destroy();server.waitFor();try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    }
    private boolean sameJson(Object left,Object right) throws Exception {
        if(left instanceof JSONObject&&right instanceof JSONObject) {
            JSONObject a=(JSONObject)left,b=(JSONObject)right;if(a.length()!=b.length())return false;
            for(java.util.Iterator<String> keys=a.keys();keys.hasNext();) {String key=keys.next();if(!b.has(key)||!sameJson(a.get(key),b.get(key)))return false;}return true;
        }
        if(left instanceof JSONArray&&right instanceof JSONArray) {JSONArray a=(JSONArray)left,b=(JSONArray)right;if(a.length()!=b.length())return false;for(int i=0;i<a.length();i++)if(!sameJson(a.get(i),b.get(i)))return false;return true;}
        return java.util.Objects.equals(left,right);
    }
    private JSONObject batch(String user,String device,JSONObject entry) throws Exception {return Protocol.row("id",Protocol.uuid(),"user_id",user,"device_id",device,"protocol",1,"operation","androidTest","entries",new JSONArray().put(entry));}
}
