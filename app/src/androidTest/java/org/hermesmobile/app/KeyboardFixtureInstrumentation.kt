package org.hermesmobile.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*

/** Explicit emulator-only setup. Standard JUnit and release APKs never execute this runner. */
class KeyboardFixtureInstrumentation:Instrumentation() {
    private var arguments=Bundle()
    private val imePackage="com.google.android.inputmethod.latin"
    private val imeComponent="$imePackage/com.android.inputmethod.latin.LatinIME"
    private val hintId="$imePackage:id/hint_box_text"
    private val closeId="$imePackage:id/access_points_hint_close_button"
    private val hintText="Tap to Google Search and more"
    private val evidence=linkedMapOf<String,JsonElement>()
    private val actions=linkedSetOf<String>()
    private val acknowledgements=linkedMapOf<String,Boolean>()
    private var stage="identity_guard"
    private var lastKeyboard:Keyboard?=null
    internal data class Keyboard(val bounds:Rect,val keyTop:Int,val keys:Int,val hints:Int,val closes:Int,val exactText:Boolean) {
        val ready get()=keys>=20 && hints==0 && closes==0 && bounds.top==keyTop
        // A visible key row with no hint can still have a larger observed IME extent.
        // It is eligible for one explicit hide/reopen, never accepted as final readiness.
        val reopenableGap get()=keys in 20..256 && hints==0 && closes==0 && !exactText &&
            !bounds.isEmpty && bounds.top>=0 && bounds.top<keyTop && keyTop<bounds.bottom
        fun json()=buildJsonObject {put("top",bounds.top);put("bottom",bounds.bottom);put("keyTop",keyTop);put("keys",keys);put("hints",hints);put("closes",closes);put("exactHintText",exactText);put("ready",ready)}
    }
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);this.arguments=arguments ?: Bundle();start()}
    @Suppress("DEPRECATION") private fun recycle(node:AccessibilityNodeInfo)=node.recycle()
    @Suppress("DEPRECATION") private fun copy(node:AccessibilityNodeInfo)=AccessibilityNodeInfo.obtain(node)
    private fun walk(node:AccessibilityNodeInfo,depth:Int=0,visit:(AccessibilityNodeInfo)->Unit) {
        check(depth<=40);try {visit(node);check(node.childCount<=256);for(i in 0 until node.childCount) node.getChild(i)?.let {walk(it,depth+1,visit)}} finally {recycle(node)}
    }
    private fun matches(type:Int,pkg:String,predicate:(AccessibilityNodeInfo)->Boolean):List<AccessibilityNodeInfo> {
        val found=mutableListOf<AccessibilityNodeInfo>();var visited=0
        for(window in uiAutomation.windows) if(window.type==type && (type!=AccessibilityWindowInfo.TYPE_APPLICATION || window.isFocused)) window.root?.let {root->
            walk(root) {node->check(++visited<=2048);if(node.packageName?.toString()==pkg && node.isVisibleToUser && predicate(node)) found+=copy(node)}
        }
        return found
    }
    private fun keyboard():Keyboard? {
        val windows=uiAutomation.windows.filter {it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD};if(windows.size!=1) return null
        val window=windows.single();val rect=Rect();window.getBoundsInScreen(rect);if(rect.isEmpty) return null
        var keys=0;var top=Int.MAX_VALUE;var hints=0;var closes=0;var exact=false;var visited=0
        val root=window.root ?: return null
        walk(root) {node->check(++visited<=1024);if(node.packageName?.toString()==imePackage && node.isVisibleToUser) {
            val id=node.viewIdResourceName.orEmpty();val bounds=Rect();node.getBoundsInScreen(bounds)
            if(id.startsWith("$imePackage:id/key_pos_") && !bounds.isEmpty) {keys++;top=minOf(top,bounds.top)}
            if(id==hintId) {hints++;exact=node.text?.toString()==hintText}
            if(id==closeId && node.isClickable && node.isEnabled && !bounds.isEmpty) closes++
        }}
        return Keyboard(rect,top,keys,hints,closes,exact)
    }
    private fun awaitKeyboard(description:String,predicate:(Keyboard)->Boolean,stableMillis:Long=0):Keyboard {
        stage=description
        val deadline=SystemClock.uptimeMillis()+7000;var previous:Keyboard?=null;var stableAt=0L
        while(SystemClock.uptimeMillis()<deadline) {
            val value=keyboard();lastKeyboard=value;val now=SystemClock.uptimeMillis()
            if(value!=null && predicate(value)) {
                if(previous!=value) {previous=value;stableAt=now}
                if(now-stableAt>=stableMillis) return value
            } else {previous=null;stableAt=now}
            SystemClock.sleep(75)
        }
        error("Owned keyboard fixture did not reach $description")
    }
    private fun oneAction(name:String,action:()->Boolean) {
        check(actions.add(name));stage=name;evidence["actions"]=JsonArray(actions.map(::JsonPrimitive))
        val acknowledged=action();acknowledgements[name]=acknowledged
        evidence["actionAcknowledgements"]=JsonObject(acknowledgements.mapValues {JsonPrimitive(it.value)})
        check(acknowledged) {"Owned keyboard fixture action was not acknowledged"}
    }
    private fun clickOne(type:Int,pkg:String,predicate:(AccessibilityNodeInfo)->Boolean,name:String) {
        val found=matches(type,pkg,predicate)
        try {check(found.size==1);oneAction(name) {found.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)}} finally {found.forEach(::recycle)}
    }
    private fun hasEditor():Boolean {
        val found=matches(AccessibilityWindowInfo.TYPE_APPLICATION,"com.android.settings") {it.viewIdResourceName=="android:id/search_src_text" && it.className=="android.widget.EditText" && it.isFocused}
        try {return found.size==1} finally {found.forEach(::recycle)}
    }
    private fun prepare() {
        if(!hasEditor()) {
            val intent=Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            check(targetContext.packageManager.resolveActivity(intent,0)?.activityInfo?.packageName=="com.android.settings")
            targetContext.startActivity(intent)
            val deadline=SystemClock.uptimeMillis()+7000
            while(SystemClock.uptimeMillis()<deadline) {
                val found=matches(AccessibilityWindowInfo.TYPE_APPLICATION,"com.android.settings") {it.contentDescription?.toString()=="Search settings" && it.isClickable}
                val ready=found.size==1;found.forEach(::recycle);if(ready) break;SystemClock.sleep(75)
            }
            clickOne(AccessibilityWindowInfo.TYPE_APPLICATION,"com.android.settings",{it.contentDescription?.toString()=="Search settings" && it.isClickable},"open_settings_search")
        }
        val first=awaitKeyboard("reviewed first-use hint or prepared keyboard",{it.keys>=20 && ((it.hints==1 && it.closes==1 && it.exactText) || it.ready || it.reopenableGap)},250)
        evidence["before"]=first.json();evidence["hintSeen"]=JsonPrimitive(first.hints==1)
        if(first.hints==1) {
            clickOne(AccessibilityWindowInfo.TYPE_INPUT_METHOD,imePackage,{it.viewIdResourceName==closeId && it.className=="android.widget.ImageView" && it.isClickable && it.isEnabled},"dismiss_hint")
        }
        // API26 can expose a window/key-row gap even when no hint was observed.
        // This sample proves hint absence only; reopened below must prove complete native geometry.
        val after=awaitKeyboard("reviewed hint absent after preparation",{it.keys>=20 && it.hints==0 && it.closes==0 && !it.exactText && (first.hints==1 || first.reopenableGap || it.ready)},250);evidence["afterDismiss"]=after.json()
        oneAction("hide_keyboard") {uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)}
        val deadline=SystemClock.uptimeMillis()+7000
        while(keyboard()!=null && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(75)
        check(keyboard()==null)
        clickOne(AccessibilityWindowInfo.TYPE_APPLICATION,"com.android.settings",{it.viewIdResourceName=="android:id/search_src_text" && it.className=="android.widget.EditText" && it.isClickable},"reopen_editor")
        val reopened=awaitKeyboard("unobscured keyboard after reopening",{it.ready},1000);evidence["reopened"]=reopened.json()
        check(Settings.Secure.getString(targetContext.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)==imeComponent)
        evidence["selectedImeAfter"]=JsonPrimitive(imeComponent);evidence["dismissAttempts"]=JsonPrimitive(if("dismiss_hint" in actions) 1 else 0)
        oneAction("return_home") {uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)}
        val cleanupDeadline=SystemClock.uptimeMillis()+7000
        while(keyboard()!=null && SystemClock.uptimeMillis()<cleanupDeadline) SystemClock.sleep(75)
        check(keyboard()==null);evidence["cleanupKeyboardHidden"]=JsonPrimitive(true)
        evidence["state"]=JsonPrimitive(when {first.hints==1->"READY_FRESH_KEYBOARD";first.reopenableGap->"READY_REOPENED_KEYBOARD";else->"READY_EXISTING_KEYBOARD"})
    }
    override fun onStart() {
        val result=Bundle();var nonce=""
        try {
            check(targetContext.packageName=="org.hermesmobile.app.debug" && Build.VERSION.SDK_INT==26 && Build.HARDWARE in setOf("ranchu","goldfish") && "x86_64" in Build.SUPPORTED_ABIS)
            nonce=checkNotNull(arguments.getString("ownedKeyboardFixture"));check(UUID.fromString(nonce).toString()==nonce)
            val mode=checkNotNull(arguments.getString("keyboardFixtureMode"));check(mode in setOf("observe","prepare","verify"))
            check(Settings.Secure.getString(targetContext.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)==imeComponent)
            val pkg=targetContext.packageManager.getPackageInfo(imePackage,0)
            @Suppress("DEPRECATION") check(pkg.versionCode==25623411 && pkg.versionName=="6.2.34.154174348-preload-x86_64")
            evidence.putAll(mapOf("schema" to JsonPrimitive(1),"nonce" to JsonPrimitive(nonce),"api" to JsonPrimitive(26),"hardware" to JsonPrimitive(Build.HARDWARE),"mode" to JsonPrimitive(mode),"imeVersion" to JsonPrimitive(checkNotNull(pkg.versionName)),"selectedImeBefore" to JsonPrimitive(imeComponent)))
            val automation=uiAutomation;val info=automation.serviceInfo;val original=info.flags
            try {
                info.flags=original or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
                automation.waitForIdle(100,3000)
                if(mode=="observe") {evidence["keyboard"]=checkNotNull(keyboard()).json();evidence["state"]=JsonPrimitive("OWNED_KEYBOARD_OBSERVED")} else prepare()
            } finally {info.flags=original;automation.serviceInfo=info}
            val text=JsonObject(evidence).toString();check(text.length<=8192);val file=File(targetContext.getExternalFilesDir(null),"qa/keyboard-fixture-$nonce.json");check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs());file.writeText(text)
            result.putString("keyboardFixtureReport",text);finish(Activity.RESULT_OK,result)
        } catch(error:Throwable) {evidence["state"]=JsonPrimitive("FAILED");evidence["errorType"]=JsonPrimitive(error.javaClass.simpleName);evidence["failureStage"]=JsonPrimitive(stage);evidence["latestKeyboard"]=lastKeyboard?.json() ?: JsonNull;result.putString("keyboardFixtureReport",JsonObject(evidence).toString());finish(Activity.RESULT_CANCELED,result)}
    }
}
