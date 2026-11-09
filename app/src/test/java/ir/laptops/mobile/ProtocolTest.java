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
    @Test public void authFailuresAndAmbiguousUserCreationNeverReplayOnFallback() throws Exception {
        try(java.net.ServerSocket first=new java.net.ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));java.net.ServerSocket backup=new java.net.ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"))) {
            backup.setSoTimeout(250);
            Thread responder=new Thread(()->{
                try {
                    for(int code:new int[]{401,503})try(java.net.Socket socket=first.accept()) {
                        java.io.BufferedReader input=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));String line;int length=0;
                        while((line=input.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
                        for(int i=0;i<length;i++)input.read();
                        byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        socket.getOutputStream().write(("HTTP/1.1 "+code+" Error\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));socket.getOutputStream().write(body);socket.getOutputStream().flush();
                    }
                }catch(java.io.IOException error){throw new RuntimeException(error);}
            });responder.setDaemon(true);responder.start();
            Api api=new Api("http://127.0.0.1:"+first.getLocalPort(),"http://127.0.0.1:"+backup.getLocalPort(),true);
            try{api.object("POST","/v1/login",new JSONObject(),"");fail("authentication rejection hidden");}catch(Api.Failure error){assertEquals(401,error.status);}
            try{api.object("POST","/v1/users",new JSONObject(),"");fail("ambiguous creation replayed");}catch(Api.Failure error){assertEquals(503,error.status);}
            try(java.net.Socket unexpected=backup.accept()){fail("fallback received unsafe retry");}catch(java.net.SocketTimeoutException expected){}
            responder.join(1000);
        }
    }
}
