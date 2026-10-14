package ir.laptops.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import android.util.Base64;
import org.json.JSONObject;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Passwords are never persisted. Session tokens are protected by Android Keystore. */
public final class Vault {
    private final SharedPreferences prefs;
    private static final long LEASE=7*86400000L;
    private static final String ALIAS="laptops_session_v1";
    public Vault(Context context){this(context,"credentials");}
    Vault(Context context,String name){prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private byte[] derive(char[] password,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,600000,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    public void remember(String userId,String username,char[] password,String token) throws Exception {
        byte[] salt=new byte[32];new SecureRandom().nextBytes(salt);byte[] verifier=derive(password,salt);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!prefs.edit().putString("user_id",userId).putString("username",username).putString("salt",encode(salt)).putString("verifier",encode(verifier)).putLong("verified",System.currentTimeMillis()).putLong("blocked",0).putInt("attempts",0).putString("token",encode(encrypted)).putString("iv",encode(cipher.getIV())).commit())throw new IllegalStateException("ذخیره دسترسی آفلاین انجام نشد.");Arrays.fill(verifier,(byte)0);
    }
    public String username(){return prefs.getString("username","");}
    public boolean fresh(){long now=System.currentTimeMillis(),verified=prefs.getLong("verified",0);return verified>0&&verified<=now+60000&&now-verified<=LEASE;}
    public void verify(String userId,String username,char[] password) throws Exception {
        long now=System.currentTimeMillis();if(prefs.getLong("blocked",0)>now)throw new IllegalArgumentException("تلاش‌های ورود زیاد است؛ یک دقیقه دیگر تلاش کنید.");
        if(!fresh()||!prefs.getString("user_id","").equals(userId))throw new IllegalArgumentException("برای ورود اولیه یا تمدید دسترسی آفلاین به اینترنت وصل شوید.");
        byte[] actual=derive(password,decode(prefs.getString("salt",""))),expected=decode(prefs.getString("verifier",""));boolean valid=prefs.getString("username","").equals(username)&&MessageDigest.isEqual(actual,expected);Arrays.fill(actual,(byte)0);
        if(!valid){int attempts=prefs.getInt("attempts",0)+1;SharedPreferences.Editor editor=prefs.edit().putInt("attempts",attempts);if(attempts>=5)editor.putLong("blocked",now+60000).putInt("attempts",0);editor.commit();throw new IllegalArgumentException("نام کاربری یا رمز ورود صحیح نیست.");}prefs.edit().putInt("attempts",0).commit();
    }
    public String token(){try{String encrypted=prefs.getString("token","");if(encrypted.isEmpty()||!fresh())return "";Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,decode(prefs.getString("iv",""))));return new String(cipher.doFinal(decode(encrypted)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception error){return "";}}
    public void clearToken(){prefs.edit().remove("token").remove("iv").commit();}
    public void invalidate(){prefs.edit().remove("token").remove("iv").remove("salt").remove("verifier").remove("verified").commit();}
    private static String encode(byte[] value){return Base64.encodeToString(value,Base64.NO_WRAP);}
    private static byte[] decode(String value){return Base64.decode(value,Base64.NO_WRAP);}
}
