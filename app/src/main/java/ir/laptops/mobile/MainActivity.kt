package ir.laptops.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.Window
import android.view.WindowInsetsController
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Native Kotlin phone UI; Java implements the durable database/API protocol. */
class MainActivity:Activity() {
    private val repo get()=(application as MobileApp).repository
    private val blue=Color.rgb(23,104,217)
    private var dark=false
    private val bg get()=if(dark)Color.rgb(17,23,34)else Color.rgb(244,247,252)
    private val surface get()=if(dark)Color.rgb(29,38,54)else Color.WHITE
    private val ink get()=if(dark)Color.rgb(236,242,251)else Color.rgb(27,43,65)
    private val muted get()=if(dark)Color.rgb(168,181,200)else Color.rgb(102,119,142)
    private var tab=0
    private var inventoryTab=0
    private var archiveAll=false
    private var query=""
    private var mainShown=false
    private var offlineAvailable=false
    private var renderedData=""
    private lateinit var bodyScroll:ScrollView
    private lateinit var root:LinearLayout
    private lateinit var content:LinearLayout
    private lateinit var status:TextView
    private lateinit var progress:ProgressBar
    private lateinit var search:EditText
    private lateinit var loginButton:Button
    private lateinit var offlineButton:Button
    private lateinit var username:EditText
    private lateinit var password:EditText
    private val observer:()->Unit={ update() }
    override fun onCreate(state:Bundle?){super.onCreate(state);dark=getPreferences(MODE_PRIVATE).getBoolean("dark",false);tab=state?.getInt("tab")?:0;inventoryTab=state?.getInt("inventoryTab")?:0}
    override fun onStart(){super.onStart();repo.listener=observer;repo.foreground(true);update()}
    override fun onStop(){repo.listener=null;repo.foreground(false);super.onStop()}
    override fun onSaveInstanceState(out:Bundle){out.putInt("tab",tab);out.putInt("inventoryTab",inventoryTab);super.onSaveInstanceState(out)}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun round(color:Int,radius:Int=16)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
    private fun label(value:String,size:Int=14,color:Int=ink,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(color);typeface=resources.getFont(if(bold)R.font.vazir_bold else R.font.vazir);gravity=Gravity.START;setPadding(dp(2),dp(4),dp(2),dp(4))}
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{text=value;textSize=13f;isAllCaps=false;typeface=resources.getFont(R.font.vazir);setTextColor(if(primary)Color.WHITE else if(dark)Color.rgb(155,195,255)else blue);background=round(if(primary)blue else if(dark)Color.rgb(39,51,71)else Color.rgb(232,240,253),12);minimumHeight=dp(48);setPadding(dp(12),dp(2),dp(12),dp(2));setOnClickListener{action()}}
    private fun add(parent:LinearLayout,view:View,weight:Float=0f){parent.addView(view,LinearLayout.LayoutParams(if(weight>0)0 else ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,weight).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})}
    private fun field(hint:String,value:String="",numeric:Boolean=false,choices:List<String> = emptyList(),multiline:Boolean=false):EditText {
        val edit=if(choices.isNotEmpty())AutoCompleteTextView(this).apply{setAdapter(ArrayAdapter(this@MainActivity,android.R.layout.simple_dropdown_item_1line,choices));threshold=0;setOnClickListener{showDropDown()}}else EditText(this)
        return edit.apply{setText(value);this.hint=hint;textSize=15f;typeface=resources.getFont(R.font.vazir);setTextColor(ink);setHintTextColor(muted);background=round(if(dark)Color.rgb(37,48,67)else Color.rgb(241,245,251),10);setPadding(dp(14),dp(12),dp(14),dp(12));minHeight=dp(52);inputType=if(numeric)InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or if(multiline)InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0;if(multiline){minLines=3;gravity=Gravity.TOP or Gravity.START}else setSingleLine(true)}
    }
    private fun base(){root=column();root.setBackgroundColor(bg);root.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(root);if(android.os.Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener{view,insets->val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());view.setPadding(dp(12)+bars.left,dp(12)+bars.top,dp(12)+bars.right,dp(12)+bars.bottom);insets};root.requestApplyInsets();styleSystemBars(window)}
    private fun styleSystemBars(target:Window?) {
        target ?: return
        target.statusBarColor=bg
        target.navigationBarColor=surface
        if(android.os.Build.VERSION.SDK_INT>=30) {
            val flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            target.decorView.post { target.insetsController?.setSystemBarsAppearance(if(dark)0 else flags,flags) }
        } else {
            target.decorView.systemUiVisibility=if(dark)0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
    private fun quickMenu(title:String,choices:List<String>,edit:EditText) {
        if(choices.isEmpty()) {error("هنوز پیش‌فرضی تعریف نشده است؛ می‌توانید نام را تایپ کنید یا از تنظیمات اضافه کنید.");return}
        val adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,choices) {
            override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
                return label(getItem(position)?:"",16,ink).apply {setPadding(dp(18),dp(12),dp(18),dp(12));minHeight=dp(52);setBackgroundColor(surface)}
            }
        }
        val menu=alert().setTitle("انتخاب $title").setAdapter(adapter){_,index->edit.setText(choices[index]);edit.setSelection(edit.text.length)}.setNegativeButton("بستن",null).create()
        menu.show();menu.window?.setBackgroundDrawable(round(surface))
    }
    private fun update(){
        if(repo.unlocked){if(!mainShown){mainShown=true;buildMain()};status.text=repo.message;progress.visibility=if(repo.busy)View.VISIBLE else View.GONE;val current=repo.rows.entries.joinToString{"${it.key}:${it.value}"}+repo.role+repo.pending+repo.conflicts.toString();if(current!=renderedData){renderedData=current;render()}}
        else {if(mainShown||!::username.isInitialized){mainShown=false;buildLogin()};status.text=repo.message;progress.visibility=if(repo.busy)View.VISIBLE else View.GONE;loginButton.isEnabled=!repo.busy;offlineButton.isEnabled=!repo.busy&&offlineAvailable;username.isEnabled=!repo.busy;password.isEnabled=!repo.busy}
    }
    private fun buildLogin(){
        base();val scroll=ScrollView(this);val form=column();form.setPadding(dp(16),dp(30),dp(16),dp(18));scroll.addView(form);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        add(form,label("امانت لپ‌تاپ",30,blue,true));add(form,label("مدیریت امانت، همراه شما",18,ink,true));add(form,label("با همان حساب برنامه کامپیوتر وارد شوید. اطلاعات شما روی گوشی و کامپیوتر همگام می‌شود.",14,muted));
        add(form,label("نام کاربری"));username=field("نام کاربری",repo.vault.username());username.layoutDirection=View.LAYOUT_DIRECTION_LTR;add(form,username)
        add(form,label("رمز ورود"));password=field("رمز ورود");password.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;password.layoutDirection=View.LAYOUT_DIRECTION_LTR;add(form,password)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;visibility=View.GONE};add(form,progress)
        status=label(repo.message,13,muted);add(form,status)
        fun login(offline:Boolean){if(repo.busy)return;val u=username.text.toString().trim();val p=password.text.toString();if(u.isBlank()||p.isEmpty()){status.text="نام کاربری و رمز ورود را وارد کنید.";return};offlineAvailable=false;loginButton.isEnabled=false;offlineButton.isEnabled=false;repo.login(u,p,offline){success,available,error->offlineAvailable=available;if(success)password.text.clear()else{status.text=error;password.selectAll()};update()}}
        loginButton=button("ورود",true){login(false)};add(form,loginButton)
        offlineButton=button("باز کردن نسخه آفلاین"){login(true)};offlineButton.isEnabled=false;add(form,offlineButton)
        add(form,label("ورود اولیه به اینترنت نیاز دارد. بعد از ورود، نسخه آفلاین تا ۷ روز در دسترس است.",12,muted))
    }
    private fun buildMain(){
        renderedData="";base();val title=row();add(title,label("امانت لپ‌تاپ",22,blue,true),1f);add(title,button(if(dark)"☀" else "☾"){dark=!dark;getPreferences(MODE_PRIVATE).edit().putBoolean("dark",dark).apply();buildMain();render()},.25f);add(root,title)
        status=label(repo.message,12,muted);add(root,status);progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;visibility=View.GONE};add(root,progress)
        search=field("جستجو در شماره، نام یا توضیحات",query);add(root,search);search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){query=s.toString();render()};override fun afterTextChanged(s:Editable?) {}})
        val scroll=ScrollView(this);bodyScroll=scroll;content=column();scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=row();listOf("امانت‌ها","بایگانی","انبار","تنظیمات").forEachIndexed{i,t->add(nav,button(t,tab==i){tab=i;query="";search.setText("");buildMain();render()},1f)};add(root,nav)
    }
    private fun card(title:String,subtitle:String,details:String,actions:List<Pair<String,()->Unit>> = emptyList()){
        val card=column();card.background=round(surface);card.setPadding(dp(14),dp(12),dp(14),dp(12));add(card,label(title,18,ink,true));if(subtitle.isNotBlank())add(card,label(subtitle,13,blue));if(details.isNotBlank())add(card,label(details,13,muted));if(actions.isNotEmpty()){val buttons=row();actions.forEach{(name,action)->add(buttons,button(name,false,action),1f)};add(card,buttons)};add(content,card)
    }
    private fun matches(data:JSONObject)=query.isBlank()||data.toString().contains(query,ignoreCase=true)
    private fun render(){
        if(!mainShown||!::content.isInitialized)return
        val previousScroll=bodyScroll.scrollY
        content.removeAllViews();search.visibility=if(tab==3)View.GONE else View.VISIBLE
        when(tab){0->loans(false);1->loans(true);2->inventory();3->settings()}
        bodyScroll.post {bodyScroll.scrollTo(0,previousScroll)}
    }
    private fun writable()=repo.role!="viewer"
    private fun admin()=repo.role=="admin"
    private fun alert()=AlertDialog.Builder(this,if(dark)android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
    private fun error(message:String)=alert().setTitle("ثبت انجام نشد").setMessage(message).setPositiveButton("باشه",null).show()
    private fun confirm(message:String,action:()->Unit)=alert().setMessage(message).setNegativeButton("انصراف",null).setPositiveButton("تأیید"){_,_->action()}.show()
    private fun date(value:String):String=try{val d=try{OffsetDateTime.parse(value).toLocalDateTime()}catch(_:Exception){LocalDateTime.parse(value)};d.format(DateTimeFormatter.ofPattern("yyyy/MM/dd · HH:mm"))}catch(_:Exception){value}
    private fun names(table:String)=repo.entities(table).map{it.getJSONObject("data").getString("name")}.sorted()
    private data class Input(val key:String,val title:String,val required:Boolean=false,val numeric:Boolean=false,val choices:List<String> = emptyList(),val multiline:Boolean=false)
    private fun form(title:String,original:JSONObject,inputs:List<Input>,save:(JSONObject,Dialog,Button)->Unit){
        val dialog=Dialog(this);val layout=column();layout.setBackgroundColor(bg);layout.setPadding(dp(14),dp(20),dp(14),dp(20));add(layout,label(title,22,ink,true));val scroll=ScrollView(this);val fields=column();scroll.addView(fields);layout.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));val edits=linkedMapOf<String,EditText>()
        inputs.forEach { spec ->
            add(fields,label(spec.title+(if(spec.required)" *" else ""),13,muted))
            val edit=field(spec.title,if(original.isNull(spec.key))"" else original.opt(spec.key)?.toString()?:"",spec.numeric,emptyList(),spec.multiline)
            if(spec.key=="password")edit.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            edits[spec.key]=edit
            if(spec.choices.isNotEmpty()||spec.key in listOf("teacher_name","grade_level")) {
                val choiceRow=row()
                add(choiceRow,edit,1f)
                val open=button("⌄") {
                    val choices=when(spec.key){"teacher_name"->names("preset_teachers");"grade_level"->names("preset_grades");else->spec.choices}
                    quickMenu(spec.title,choices,edit)
                }
                open.contentDescription="انتخاب ${spec.title} از فهرست"
                choiceRow.addView(open,LinearLayout.LayoutParams(dp(52),dp(52)).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})
                add(fields,choiceRow)
            } else add(fields,edit)
        }
        val buttons=row();val submit=button("ذخیره",true){ };submit.setOnClickListener{
            try{val edited=Protocol.copy(original);inputs.forEach{spec->val value=edits.getValue(spec.key).text.toString().trim();require(!spec.required||value.isNotEmpty()){ "${spec.title} را وارد کنید." };if(spec.numeric){val n=value.toIntOrNull()?:0;require(n in 0..1000000){"${spec.title} معتبر نیست."};edited.put(spec.key,n)}else edited.put(spec.key,value)};submit.isEnabled=false;save(edited,dialog,submit)}catch(e:Exception){submit.isEnabled=true;error(e.message?:"ورودی معتبر نیست.")}}
        add(buttons,submit,1f);add(buttons,button("انصراف"){dialog.dismiss()},1f);add(layout,buttons);dialog.setContentView(layout);dialog.show();dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);styleSystemBars(dialog.window)
    }
    private fun saveDialog(operation:String,dialog:Dialog,submit:Button,build:()->JSONArray){repo.mutate(operation,build){failure->submit.isEnabled=true;if(failure==null){dialog.dismiss();Toast.makeText(this,"روی گوشی ذخیره شد؛ ارسال خودکار انجام می‌شود.",Toast.LENGTH_SHORT).show()}else error(failure)}}
    private fun change(table:String,id:String,original:JSONObject,edited:JSONObject):JSONObject {val current=repo.store.data(table,id);return repo.store.edit(table,id,current,Protocol.patch(table,current,original,edited))}
    private fun loans(archive:Boolean){
        val all=repo.entities("loans").sortedByDescending{it.getJSONObject("data").optString("lend_time")}
        val list=all.filter{val d=it.getJSONObject("data");(if(archive)archiveAll||d.optString("status")=="RETURNED" else d.optString("status")=="ACTIVE")&&matches(d)}
        add(content,label(if(archive)"بایگانی · ${list.size} رکورد" else "امانت‌های فعال · ${list.size} لپ‌تاپ",18,ink,true))
        if(archive)add(content,button(if(archiveAll)"نمایش فقط تحویل‌شده‌ها"else "نمایش کل سوابق"){archiveAll=!archiveAll;render()})
        else if(writable())add(content,button("＋ ثبت امانت جدید",true){loanForm()})
        if(list.isEmpty())card("رکوردی یافت نشد","","امانت‌های جدید و تغییرات دیگر دستگاه‌ها خودکار دریافت می‌شوند.")
        for(entity in list){val id=entity.getString("id");val d=entity.getJSONObject("data");val actions=mutableListOf<Pair<String,()->Unit>>()
            if(writable()){if(d.getString("status")=="ACTIVE")actions.add("تحویل گرفتن" to {returnLaptop(id,d)});actions.add("ویرایش" to {loanForm(id,d)})}
            if(admin())actions.add("حذف" to {confirm("این رکورد حذف شود؟"){repo.mutate("deleteLoan",{val current=repo.store.data("loans",id);JSONArray().put(repo.store.edit("loans",id,current,null))}){it?.let(::error)}}})
            card("لپ‌تاپ ${d.optString("laptop_number")}","${if(d.optString("status")=="ACTIVE")"در امانت"else"تحویل‌شده"} · زنگ ${d.optInt("bell_period")} · ${d.optString("grade_level")}","${d.optString("recipient_name")} · دبیر: ${d.optString("teacher_name")}\n${d.optString("initial_condition")}\n${date(d.optString("lend_time"))}"+(if(d.optString("notes").isNotBlank())"\n${d.optString("notes")}"else"")+(if(d.optString("status")=="RETURNED")"\nبازگشت: ${d.optString("returner_name")} · ${date(d.optString("return_time"))}\n${d.optString("return_condition")}"else""),actions)
        }
    }
    private fun loanForm(id:String?=null,original:JSONObject?=null){
        val row=original?:Protocol.row("bell_period",1,"grade_level","","teacher_name","","recipient_name","","laptop_number","","initial_condition","سالم","return_condition",null,"returner_name",null,"lend_time",Protocol.now(),"return_time",null,"status","ACTIVE","notes","")
        val inputs=mutableListOf(Input("laptop_number","شماره لپ‌تاپ",true),Input("bell_period","زنگ (۱ تا ۵)",true,true),Input("grade_level","پایه",true,choices=names("preset_grades")),Input("teacher_name","نام دبیر",true,choices=names("preset_teachers")),Input("recipient_name","نام تحویل‌گیرنده",true),Input("initial_condition","وضعیت دستگاه",true),Input("notes","یادداشت",multiline=true))
        if(row.optString("status")=="RETURNED"){inputs.add(Input("returner_name","نام تحویل‌دهنده",true));inputs.add(Input("return_condition","وضعیت بازگشت",true))}
        form(if(id==null)"ثبت امانت لپ‌تاپ" else "ویرایش امانت",row,inputs){edited,dialog,button->require(edited.getInt("bell_period") in 1..5){"زنگ باید بین ۱ تا ۵ باشد."};saveDialog(if(id==null)"addLoan"else"updateLoan",dialog,button){JSONArray().put(if(id==null)repo.store.edit("loans",Protocol.uuid(),null,edited)else change("loans",id,row,edited))}}
    }
    private fun returnLaptop(id:String,original:JSONObject){form("تحویل گرفتن لپ‌تاپ",Protocol.row("person","","condition","سالم","notes",original.optString("notes")),listOf(Input("person","نام تحویل‌دهنده",true),Input("condition","وضعیت بازگشت",true),Input("notes","یادداشت",multiline=true))){values,dialog,button->saveDialog("returnLoan",dialog,button){val current=repo.store.data("loans",id);require(current.getString("status")=="ACTIVE"){"این امانت قبلاً تحویل شده است."};val returned=Protocol.copy(current).put("status","RETURNED").put("returner_name",values.getString("person")).put("return_condition",values.getString("condition")).put("return_time",Protocol.now());if(values.getString("notes")!=original.optString("notes"))returned.put("notes",values.getString("notes"));JSONArray().put(repo.store.edit("loans",id,current,returned))}}}
    private fun inventory(){
        val tabs=row();listOf("موجودی","امانت لوازم","مفقودی").forEachIndexed{i,name->add(tabs,button(name,inventoryTab==i){inventoryTab=i;render()},1f)};add(content,tabs)
        when(inventoryTab){0->stock();1->inventoryLoans();else->missing()}
    }
    private fun stock(){
        if(admin())add(content,button("＋ افزودن کالا",true){itemForm()})
        val list=repo.entities("inventory_items").filter{matches(it.getJSONObject("data"))}
        if(list.isEmpty())card("انبار خالی است","","انواع کالا را از تنظیمات تعریف کنید، سپس کالا و تعداد را اضافه کنید.")
        for(entity in list){val id=entity.getString("id");val d=entity.getJSONObject("data");val available=Protocol.available(repo.rows,id);val borrowed=repo.entities("inventory_loans").filter{it.getJSONObject("data").optString("item_id")==id}.sumOf{val l=it.getJSONObject("data");l.getInt("quantity")-l.getInt("returned")-l.getInt("missing")};val lost=repo.entities("inventory_missing").filter{it.getJSONObject("data").optString("item_id")==id}.sumOf{val l=it.getJSONObject("data");l.getInt("quantity")-l.getInt("recovered")};val type=repo.rows["stock_types:${d.optString("type_id")}"]?.optString("name")?:""
            val actions=mutableListOf<Pair<String,()->Unit>>();if(writable()){if(available>0)actions.add("امانت" to {stockMovement("lend",id,d,available)});if(available>0)actions.add("مفقودی" to {stockMovement("missing",id,d,available)})};if(admin())actions.add("ویرایش" to {itemForm(id,d)})
            card(d.optString("name"),"$type · ${d.optString("brand")} ${d.optString("model")}","موجود: $available · امانت: $borrowed · مفقود: $lost · کل: ${d.optInt("total")}\n${d.optString("code")} · ${d.optString("location")}\n${d.optString("description")}"+(if(available<=d.optInt("low_stock_threshold")&&d.optInt("low_stock_threshold")>0)"\n⚠ موجودی کم"else""),actions)
        }
    }
    private fun itemForm(id:String?=null,original:JSONObject?=null){
        val types=repo.entities("stock_types");if(types.isEmpty()){error("ابتدا از تنظیمات یک نوع کالا تعریف کنید.");return}
        val row=original?:Protocol.row("type_id",types[0].getString("id"),"name","","brand","","model","","code","","description","","location","","total",0,"low_stock_threshold",0)
        // Human names are chosen in the form; UUIDs remain the wire identities.
        val formRow=Protocol.copy(row).put("type_name",repo.rows["stock_types:${row.getString("type_id")}"]?.optString("name")?:"").put("reason","")
        form(if(id==null)"افزودن کالا"else"ویرایش کالا",formRow,listOf(Input("type_name","نوع کالا",true,choices=types.map{it.getJSONObject("data").getString("name")}),Input("name","نام کالا",true),Input("brand","برند"),Input("model","نوع اتصال / مدل"),Input("code","کد کالا"),Input("location","محل نگهداری"),Input("description","توضیحات",multiline=true),Input("total","تعداد کل",true,true),Input("low_stock_threshold","حد هشدار",numeric=true),Input("reason","دلیل تغییر تعداد"))){values,dialog,button->val selected=types.find{it.getJSONObject("data").getString("name")==values.getString("type_name")}?:error("نوع کالا را از فهرست انتخاب کنید.");if(selected !is JSONObject){button.isEnabled=true;return@form};val reason=values.optString("reason");val edited=Protocol.copy(values);edited.remove("type_name");edited.remove("reason");edited.put("type_id",selected.getString("id"));saveDialog("saveInventoryItem",dialog,button){val itemId=id?:Protocol.uuid();val current=if(id==null)null else repo.store.data("inventory_items",id);val desired=if(current==null)edited else Protocol.patch("inventory_items",current,row,edited);val delta=desired.getInt("total")-(current?.getInt("total")?:0);require(id==null||delta==0||reason.isNotBlank()){ "دلیل تغییر تعداد را وارد کنید." };JSONArray().put(repo.store.edit("inventory_items",itemId,current,desired)).put(event(itemId,if(id==null)"ADD"else"EDIT",delta,"",reason))}}
    }
    private fun event(item:String,action:String,quantity:Int,person:String,notes:String)=repo.store.edit("inventory_events",Protocol.uuid(),null,Protocol.row("item_id",item,"action",action,"quantity",quantity,"person",person,"notes",notes,"time",Protocol.now()))
    private fun stockMovement(action:String,item:String,original:JSONObject,max:Int,loanId:String?=null,reportId:String?=null){
        val title=when(action){"lend"->"امانت لوازم";"return"->"تحویل لوازم";"recover"->"ثبت پیدا شدن";else->"گزارش مفقودی"}
        val inputs=mutableListOf(Input("quantity","تعداد (حداکثر $max)",true,true),Input("person",if(action=="lend")"نام تحویل‌گیرنده"else"نام ثبت‌کننده",true),Input("notes","توضیحات",action=="missing",multiline=true));if(action=="lend")inputs.add(Input("due","موعد بازگشت (YYYY-MM-DD، اختیاری)"))
        form(title,Protocol.row("quantity",1,"person","","notes","","due",""),inputs){values,dialog,button->val quantity=values.getInt("quantity");require(quantity in 1..max){"تعداد باید بین ۱ و $max باشد."};saveDialog(when(action){"lend"->"lendInventory";"return"->"returnInventory";"recover"->"recoverMissingStock";else->"reportMissingStock"},dialog,button){val person=values.getString("person");val notes=values.getString("notes");val entries=JSONArray();when(action){
            "lend"->{val due=values.optString("due").takeIf{it.isNotBlank()}?.let{java.time.LocalDate.parse(it).atStartOfDay().toString()};val loan=Protocol.row("item_id",item,"borrower",person,"quantity",quantity,"returned",0,"missing",0,"lend_time",Protocol.now(),"due_time",due,"last_return_time",null,"notes",notes);entries.put(repo.store.edit("inventory_loans",Protocol.uuid(),null,loan)).put(event(item,"LEND",quantity,person,notes))}
            "return"->{val current=repo.store.data("inventory_loans",loanId!!);val returned=Protocol.copy(current).put("returned",current.getInt("returned")+quantity).put("last_return_time",Protocol.now());entries.put(repo.store.edit("inventory_loans",loanId,current,returned)).put(event(item,"RETURN",quantity,person,notes))}
            "recover"->{val current=repo.store.data("inventory_missing",reportId!!);entries.put(repo.store.edit("inventory_missing",reportId,current,Protocol.copy(current).put("recovered",current.getInt("recovered")+quantity))).put(event(item,"FOUND",quantity,person,notes))}
            else->{if(loanId!=null){val current=repo.store.data("inventory_loans",loanId);entries.put(repo.store.edit("inventory_loans",loanId,current,Protocol.copy(current).put("missing",current.getInt("missing")+quantity)))};val report=Protocol.row("item_id",item,"loan_id",loanId,"quantity",quantity,"recovered",0,"reporter",person,"notes",notes,"reported_time",Protocol.now());entries.put(repo.store.edit("inventory_missing",Protocol.uuid(),null,report)).put(event(item,"MISSING",quantity,person,notes))}
        };entries}}
    }
    private fun inventoryLoans(){
        val list=repo.entities("inventory_loans").filter{matches(it.getJSONObject("data"))}.sortedByDescending{it.getJSONObject("data").optString("lend_time")};if(list.isEmpty())card("امانتی ثبت نشده","","")
        for(entity in list){val id=entity.getString("id");val d=entity.getJSONObject("data");val item=d.getString("item_id");val itemRow=repo.rows["inventory_items:$item"]?:continue;val outstanding=d.getInt("quantity")-d.getInt("returned")-d.getInt("missing");val actions=mutableListOf<Pair<String,()->Unit>>();if(writable()&&outstanding>0){actions.add("تحویل گرفتن" to {stockMovement("return",item,itemRow,outstanding,loanId=id)});actions.add("مفقودی" to {stockMovement("missing",item,itemRow,outstanding,loanId=id)})};card(itemRow.optString("name"),d.optString("borrower"),"در امانت: $outstanding · تحویل: ${d.optInt("returned")} · مفقود: ${d.optInt("missing")}\n${date(d.optString("lend_time"))}\n${d.optString("notes")}"+(if(!d.isNull("due_time"))"\nموعد: ${date(d.optString("due_time"))}"else""),actions)}
    }
    private fun missing(){
        val list=repo.entities("inventory_missing").filter{matches(it.getJSONObject("data"))};if(list.isEmpty())card("گزارش مفقودی ندارید","","")
        for(entity in list){val id=entity.getString("id");val d=entity.getJSONObject("data");val item=d.getString("item_id");val itemRow=repo.rows["inventory_items:$item"]?:continue;val remaining=d.getInt("quantity")-d.getInt("recovered");card(itemRow.optString("name"),"مفقود: $remaining · پیدا شده: ${d.optInt("recovered")}","${d.optString("reporter")} · ${date(d.optString("reported_time"))}\n${d.optString("notes")}",if(writable()&&remaining>0)listOf("ثبت پیدا شدن" to {stockMovement("recover",item,itemRow,remaining,reportId=id)})else emptyList())}
    }
    private fun settings(){
        card(repo.userName,"حساب: ${repo.role}","ارسال و دریافت خودکار است.\nدر صف: ${repo.pending} · نیاز به بررسی: ${repo.conflicts.size}",listOf("تازه‌سازی" to {repo.requestSync()},"خروج" to {confirm("از حساب خارج شوید؟ تغییرات ذخیره‌شده روی گوشی حفظ می‌شوند."){repo.logout()}}))
        for(conflict in repo.conflicts){val id=conflict.getString("id");card("تغییر نیاز به بررسی دارد","",conflict.optString("message"),listOf("جزئیات" to {alert().setTitle("تغییر ذخیره‌شده").setMessage(conflict.getJSONObject("payload").toString(2)).setPositiveButton("باشه",null).show()},"ارسال دوباره" to {confirm("با نسخه فعلی سرور دوباره ارسال شود؟"){repo.resolve(id,true){it?.let(::error)}}},"کنار گذاشتن" to {confirm("این تغییر کنار گذاشته شود؟ نسخه اصلی در تاریخچه محلی محفوظ می‌ماند."){repo.resolve(id,false){it?.let(::error)}}}))}
        if(admin()){
            add(content,label("پیش‌فرض‌ها",18,ink,true));listOf("preset_teachers" to "دبیران","preset_grades" to "پایه‌ها").forEach{(table,title)->add(content,button("مدیریت $title"){presets(table,title)})};add(content,button("انواع کالای انبار"){types()});add(content,button("مدیریت کاربران"){users()})
        }
        add(content,label("نسخه ${BuildConfig.VERSION_NAME} · اتصال امن به سرور مشترک",12,muted))
    }
    private fun presets(table:String,title:String){val entries=repo.entities(table);val names=entries.map{it.getJSONObject("data").getString("name")}.toTypedArray();alert().setTitle(title).setItems(names){_,which->val selected=entries[which];confirm("«${names[which]}» حذف شود؟"){repo.mutate(if(table=="preset_teachers")"removePresetTeacher"else"removePresetGrade",{val id=selected.getString("id");JSONArray().put(repo.store.edit(table,id,repo.store.data(table,id),null))}){it?.let(::error)}}}.setPositiveButton("افزودن"){_,_->form("افزودن $title",Protocol.row("name",""),listOf(Input("name","نام",true))){row,dialog,button->saveDialog(if(table=="preset_teachers")"addPresetTeacher"else"addPresetGrade",dialog,button){JSONArray().put(repo.store.edit(table,Protocol.uuid(),null,row))}}}.setNegativeButton("بستن",null).show()}
    private fun types() {
        val entries=repo.entities("stock_types")
        alert().setTitle("انواع کالا")
            .setItems(entries.map { it.getJSONObject("data").getString("name") }.toTypedArray()) { _,which ->
                val selected=entries[which]
                alert().setTitle(selected.getJSONObject("data").getString("name"))
                    .setItems(arrayOf("ویرایش","حذف")) { _,action ->
                        if(action==0)typeForm(selected.getString("id"),selected.getJSONObject("data"))
                        else confirm("نوع کالا حذف شود؟ نوع استفاده‌شده در انبار قابل حذف نیست.") {
                            repo.mutate("removeStockType", {
                                val id=selected.getString("id")
                                JSONArray().put(repo.store.edit("stock_types",id,repo.store.data("stock_types",id),null))
                            }) { it?.let(::error) }
                        }
                    }.show()
            }.setPositiveButton("افزودن") { _,_->typeForm() }.setNegativeButton("بستن",null).show()
    }
    private fun typeForm(id:String?=null,original:JSONObject?=null){val row=original?:Protocol.row("name","","brand","","model","","description","");form("نوع کالا",row,listOf(Input("name","نام نوع",true),Input("brand","برند پیش‌فرض"),Input("model","نوع / مدل پیش‌فرض"),Input("description","توضیحات",multiline=true))){edited,dialog,button->saveDialog("saveStockType",dialog,button){JSONArray().put(if(id==null)repo.store.edit("stock_types",Protocol.uuid(),null,edited)else change("stock_types",id,row,edited))}}}
    private fun users(){repo.admin("GET","/v1/users",null){result,failure->if(failure!=null){error(failure);return@admin};val users=Protocol.list(result as JSONArray);alert().setTitle("کاربران").setItems(users.map{"${it.getString("username")} · ${it.getString("role")}"}.toTypedArray()){_,index->userForm(users[index])}.setPositiveButton("افزودن"){_,_->userForm(null)}.setNegativeButton("بستن",null).show()}}
    private fun userForm(original:JSONObject?){val row=original?:Protocol.row("username","","display_name","","role","staff","active",true);row.put("password","");row.put("enabled",if(row.optBoolean("active"))"بله"else"خیر");form("حساب کاربری",row,listOf(Input("username","نام کاربری",true),Input("display_name","نام نمایشی",true),Input("password","رمز جدید (حداقل ۱۲ کاراکتر)"),Input("role","دسترسی",true,choices=listOf("admin","staff","viewer")),Input("enabled","فعال",true,choices=listOf("بله","خیر")))){edited,dialog,button->val secret=edited.getString("password");require((original!=null&&secret.isBlank())||secret.length>=12){"رمز باید حداقل ۱۲ کاراکتر باشد."};require(edited.getString("role") in listOf("admin","staff","viewer")){"دسترسی معتبر نیست."};val body=Protocol.row("username",edited.getString("username"),"display_name",edited.getString("display_name"),"role",edited.getString("role"),"active",edited.getString("enabled")=="بله");if(secret.isNotBlank())body.put("password",secret);repo.admin(if(original==null)"POST"else"PUT",if(original==null)"/v1/users"else"/v1/users/${original.getString("id")}",body){_,failure->button.isEnabled=true;if(failure!=null)error(failure)else dialog.dismiss()}}}
}
