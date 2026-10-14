package ir.laptops.mobile

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.ComponentName
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MobileApp : Application() {
    lateinit var repository: Repository
    override fun onCreate() { super.onCreate(); repository=Repository(this) }
}

class Repository(private val app: MobileApp) {
    val store=Store(app)
    val vault=Vault(app)
    val api=Api(BuildConfig.SERVER_URL)
    private val executor=Executors.newSingleThreadScheduledExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var poll: ScheduledFuture<*>?=null
    private var token=""
    private var password=charArrayOf()
    private var sessionUsername=""
    @Volatile private var foregroundActive=false
    private var retrySeconds=2L
    @Volatile var unlocked=false; private set
    @Volatile var busy=false; private set
    @Volatile var message="برای باز کردن برنامه وارد شوید."; private set
    @Volatile var pending=0; private set
    @Volatile var conflicts=listOf<JSONObject>(); private set
    @Volatile var rows=mapOf<String,JSONObject>(); private set
    @Volatile var userName=""; private set
    @Volatile var role=""; private set
    var listener: (() -> Unit)?=null
    init {
        val connectivity=app.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback(){override fun onAvailable(network:Network){if(unlocked&&foregroundActive)requestSync()}})
    }
    private fun notifyUi(){main.post { listener?.invoke() }}
    private fun refresh(){rows=store.state();pending=store.count("pending");conflicts=store.conflicts();userName=store.setting("display_name");role=store.setting("role");notifyUi()}
    fun entities(table:String)=rows.entries.filter{it.key.startsWith("$table:")}.map{Protocol.row("id",it.key.substringAfter(':'),"data",Protocol.copy(it.value))}
    fun current(table:String,id:String)=rows["$table:$id"]?.let{Protocol.copy(it)} ?: error("این رکورد حذف شده است.")
    fun foreground(active:Boolean){foregroundActive=active;executor.execute{poll?.cancel(false);poll=null;if(active&&unlocked)requestSync()}}
    fun login(username:String,secret:String,offline:Boolean=false,done:(Boolean,Boolean,String)->Unit){
        executor.execute {
            busy=true;notifyUi()
            try {
                if(offline){vault.verify(store.setting("user_id"),username.trim(),secret.toCharArray());token=vault.token()}
                else authenticate(username.trim(),secret.toCharArray())
                password.fill('\u0000');password=secret.toCharArray();sessionUsername=username.trim();unlocked=true;retrySeconds=2
                message=if(offline)"آفلاین — تغییرات محلی محفوظ است." else "در حال دریافت اطلاعات…";refresh()
                main.post{done(true,false,"")};requestSync()
            } catch(error:Exception) {message=error.message?:"ورود انجام نشد.";val available=error is Api.Failure&&error.offlineEligible;main.post{done(false,available,message)}}
            finally{busy=false;notifyUi()}
        }
    }
    private fun authenticate(username:String,secret:CharArray){
        val response=api.`object`("POST","/v1/login",Protocol.row("username",username,"password",String(secret),"device_id",store.device()),"")
        require(response.getInt("protocol")==1){"نسخه سرور سازگار نیست."}
        val user=response.getJSONObject("user");store.user(user);token=response.getString("token")
        vault.remember(user.getString("id"),username,secret,token)
    }
    fun logout(){executor.execute{val previous=token;unlocked=false;token="";password.fill('\u0000');password=charArrayOf();sessionUsername="";poll?.cancel(false);vault.clearToken();rows=emptyMap();message="برای باز کردن برنامه وارد شوید.";notifyUi();try{if(previous.isNotEmpty())api.`object`("POST","/v1/logout",null,previous)}catch(_:Exception){};app.getSystemService(JobScheduler::class.java).cancel(41)}}
    fun mutate(operation:String,build:()->JSONArray,done:(String?)->Unit){executor.execute{try{check(unlocked&&vault.fresh()){ "اعتبار ورود آفلاین پایان یافته است؛ دوباره با اینترنت وارد شوید." };store.enqueue(operation,build());refresh();scheduleBackground();main.post{done(null)};requestSync()}catch(error:Exception){main.post{done(error.message?:"ذخیره انجام نشد.")}}}}
    fun resolve(id:String,retry:Boolean,done:(String?)->Unit){executor.execute{try{check(unlocked);if(retry)store.retry(id)else store.discard(id);refresh();main.post{done(null)};requestSync()}catch(error:Exception){main.post{done(error.message)}}}}
    fun requestSync(){executor.execute{if(unlocked)sync(false)}}
    fun background(done:(Boolean)->Unit){executor.execute{if(token.isEmpty())token=vault.token();if(token.isEmpty()){done(true);return@execute};try{sync(true);done(true)}catch(_:Exception){done(false)}}}
    private fun pull(){
        if(store.setting("instance_id").isEmpty()){store.snapshot(api.`object`("GET","/v1/snapshot",null,token));return}
        do {
            val feed=try{api.`object`("GET","/v1/changes?instance_id=${URLEncoder.encode(store.setting("instance_id"),"UTF-8")}&after=${store.setting("cursor")}",null,token)}catch(error:Api.Failure){if(error.status==409){store.snapshot(api.`object`("GET","/v1/snapshot",null,token));return};throw error}
            store.feed(feed)
        }while(feed.optBoolean("has_more"))
    }
    private fun sync(background:Boolean){
        if(!background&&!unlocked)return
        poll?.cancel(false);busy=true;message="در حال همگام‌سازی…";notifyUi()
        try {
            if(token.isEmpty()){check(password.isNotEmpty()){ "برای اتصال دوباره وارد شوید." };authenticate(sessionUsername,password)}
            pull()
            while(true){val batch=store.next()?:break;val result=api.`object`("POST","/v1/batches",batch,token);check(result.getString("id")==batch.getString("id")){"پاسخ ارسال معتبر نیست."};store.result(result)}
            pull();refresh();retrySeconds=2;message="متصل · $pending در صف · ${conflicts.size} نیاز به بررسی"
            if(unlocked&&foregroundActive)poll=executor.schedule({sync(false)},10,TimeUnit.SECONDS)
        }catch(error:Exception){
            if(error is Api.Failure&&error.status==401){token="";vault.clearToken();if(password.isNotEmpty()&&unlocked){try{authenticate(sessionUsername,password);if(foregroundActive)poll=executor.schedule({sync(false)},1,TimeUnit.SECONDS)}catch(auth:Exception){if(auth is Api.Failure&&auth.offlineEligible){offline(auth,background)}else lock(auth.message?:"ورود مجدد لازم است.")}}else lock("ورود مجدد لازم است.",false)}
            else if(error is Api.Failure&&error.status==403)lock(error.message?:"دسترسی شما تغییر کرده است.")
            else offline(error,background)
            if(background)throw error
        }finally{busy=false;notifyUi()}
    }
    private fun offline(error:Exception,background:Boolean=false){message="${error.message?:"ارتباط قطع شد."} · تغییرات محلی محفوظ است.";if(unlocked&&foregroundActive){poll=executor.schedule({sync(false)},retrySeconds,TimeUnit.SECONDS);retrySeconds=(retrySeconds*2).coerceAtMost(60)};if(!background)scheduleBackground()}
    private fun lock(reason:String,revoke:Boolean=true){unlocked=false;token="";password.fill('\u0000');password=charArrayOf();if(revoke)vault.invalidate()else vault.clearToken();poll?.cancel(false);rows=emptyMap();message=reason;notifyUi()}
    private fun scheduleBackground(){app.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(41,ComponentName(app,SyncJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())}
    fun admin(method:String,path:String,body:JSONObject?,done:(Any?,String?)->Unit){executor.execute{try{check(unlocked&&role=="admin"){ "دسترسی مدیر لازم است." };val result=api.request(method,path,body,token);main.post{done(result,null)}}catch(error:Exception){main.post{done(null,error.message)}}}}
}
class SyncJob:JobService(){
    override fun onStartJob(params:JobParameters):Boolean{(application as MobileApp).repository.background{success->Handler(Looper.getMainLooper()).post{jobFinished(params,!success)}};return true}
    override fun onStopJob(params:JobParameters)=true
}
