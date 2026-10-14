package ir.laptops.mobile;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLException;

/** Platform HTTPS validation is retained; redirects and TLS bypasses are forbidden. */
public final class Api {
    private final String endpoint;
    public Api(String endpoint) { this(endpoint,false); }
    Api(String endpoint,boolean loopbackTest) {
        URI uri=URI.create(endpoint);
        if(!"https".equals(uri.getScheme())&&!(loopbackTest&&"http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())))throw new IllegalArgumentException("آدرس سرور باید HTTPS باشد.");
        this.endpoint=endpoint;
    }
    public static final class Failure extends IOException {
        public final int status;public final boolean offlineEligible;
        Failure(int status,String message,boolean offlineEligible,Throwable cause){super(message,cause);this.status=status;this.offlineEligible=offlineEligible;}
    }
    public Object request(String method,String path,JSONObject body,String token) throws IOException {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(endpoint+path).openConnection();
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type","application/json");
            if(token!=null&&!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
            if(!method.equals("GET")){connection.setDoOutput(true);byte[] bytes=(body==null?"{}":body.toString()).getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int code=connection.getResponseCode();InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            String text="";if(stream!=null)try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1){if(out.size()+read>20*1024*1024)throw new IOException("پاسخ سرور بیش از حد بزرگ است.");out.write(buffer,0,read);}text=out.toString(StandardCharsets.UTF_8.name());}
            if(code<200||code>=300) {
                String message="ارتباط با سرور انجام نشد ("+code+")";try{Object detail=new JSONObject(text).opt("detail");if(detail instanceof String)message=(String)detail;}catch(JSONException ignored){}
                throw new Failure(code,message,code==429||code==502||code==503||code==504,null);
            }
            try{return new JSONTokener(text).nextValue();}catch(JSONException invalid){throw new Failure(code,"پاسخ سرور معتبر نیست.",false,invalid);}
        } catch(Failure error){throw error;}catch(SSLException error){throw new Failure(0,"گواهی امن سرور تأیید نشد.",false,error);}catch(IOException error){throw new Failure(0,"اینترنت یا سرور در دسترس نیست.",true,error);}finally{if(connection!=null)connection.disconnect();}
    }
    public JSONObject object(String method,String path,JSONObject body,String token) throws IOException {
        Object value=request(method,path,body,token);if(!(value instanceof JSONObject))throw new Failure(0,"پاسخ سرور معتبر نیست.",false,null);return (JSONObject)value;
    }
}
