package org.hermesmobile.app.data

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("preferences")
data class AppPreferences(val theme: String = "STARHAVEN", val chinese: Boolean = true, val pinyin: Boolean = true, val reducedMotion: Boolean = false, val demo: Boolean = true, val url: String = "", val debugHttp: Boolean = false, val onDeviceSpeechOnly: Boolean = true, val allowNetworkSpeech: Boolean = false, val speechRate: Float = 1f, val notifications: Boolean = false, val monitorRuns: Boolean = false, val automationScope: String = "",val runtimeChoices: String="{}")
class PreferenceStore(context: Context) {
    private val store = context.dataStore
    private val theme = stringPreferencesKey("theme")
    private val chinese = booleanPreferencesKey("chinese")
    private val pinyin = booleanPreferencesKey("pinyin")
    private val motion = booleanPreferencesKey("reduced_motion")
    private val demo = booleanPreferencesKey("demo")
    private val url = stringPreferencesKey("server_url")
    private val http = booleanPreferencesKey("debug_http")
    private val localSpeech=booleanPreferencesKey("on_device_speech_only")
    private val networkSpeech=booleanPreferencesKey("allow_network_speech")
    private val rate=floatPreferencesKey("speech_rate")
    private val notify=booleanPreferencesKey("notifications")
    private val monitor=booleanPreferencesKey("monitor_runs")
    private val automation=stringPreferencesKey("automation_scope")
    private val runtimes=stringPreferencesKey("runtime_choices")
    val flow = store.data.map { p -> AppPreferences(p[theme] ?: "STARHAVEN", p[chinese] ?: true, p[pinyin] ?: true, p[motion] ?: false, p[demo] ?: true, p[url].orEmpty(), p[http] ?: false,p[localSpeech] ?: true,p[networkSpeech] ?: false,(p[rate] ?: 1f).coerceIn(.75f,1.25f),p[notify] ?: false,p[monitor] ?: false,p[automation].orEmpty(),p[runtimes] ?: "{}") }
    suspend fun update(p: AppPreferences) { store.edit { it[theme]=p.theme; it[chinese]=p.chinese; it[pinyin]=p.pinyin; it[motion]=p.reducedMotion; it[demo]=p.demo; it[url]=p.url; it[http]=p.debugHttp;it[localSpeech]=p.onDeviceSpeechOnly;it[networkSpeech]=p.allowNetworkSpeech;it[rate]=p.speechRate.coerceIn(.75f,1.25f);it[notify]=p.notifications;it[monitor]=p.monitorRuns;it[automation]=p.automationScope } }
    suspend fun patchUserSettings(next: AppPreferences,base: AppPreferences) {store.edit {p ->
        if(next.theme!=base.theme) p[theme]=next.theme
        if(next.chinese!=base.chinese) p[chinese]=next.chinese
        if(next.pinyin!=base.pinyin) p[pinyin]=next.pinyin
        if(next.reducedMotion!=base.reducedMotion) p[motion]=next.reducedMotion
        if(next.onDeviceSpeechOnly!=base.onDeviceSpeechOnly) p[localSpeech]=next.onDeviceSpeechOnly
        if(next.allowNetworkSpeech!=base.allowNetworkSpeech) p[networkSpeech]=next.allowNetworkSpeech
        if(next.speechRate!=base.speechRate) p[rate]=next.speechRate.coerceIn(.75f,1.25f)
        if(next.notifications!=base.notifications) p[notify]=next.notifications
        if(next.monitorRuns!=base.monitorRuns) p[monitor]=next.monitorRuns
        if(next.automationScope!=base.automationScope) p[automation]=next.automationScope
    }}
    suspend fun connection(urlValue: String?=null,demoValue: Boolean?=null,httpValue: Boolean?=null) {store.edit {p ->
        urlValue?.let {p[url]=it};demoValue?.let {p[demo]=it};httpValue?.let {p[http]=it}
    }}
    suspend fun runtime(scope: String,choice: RuntimeChoice) {
        val fields=choice.storedFields();require(scope.isNotBlank())
        store.edit {p ->
            val old=runCatching {Json.parseToJsonElement(p[runtimes] ?: "{}").jsonObject}.getOrDefault(JsonObject(emptyMap()))
            val rows=old.filterKeys {it!=scope}.entries.toList().takeLast(63).associate {it.key to it.value}+mapOf(scope to fields)
            p[runtimes]=JsonObject(rows).toString()
        }
    }

}
class SecureCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val monitor=locks.getOrPut(context.applicationInfo.dataDir) {Any()}
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("hermes_token_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("hermes_token_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        return Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    private fun decrypt(encoded: String): String {
        val bytes=Base64.decode(encoded,Base64.NO_WRAP)
        require(bytes.size>28) {"Saved credential is invalid; enter it again."}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }
    private fun configRow(config:ServerConfig)=buildJsonObject {put("url",config.url);put("token",config.token);put("debugHttp",config.allowDebugHttp)}
    private fun parseConfig(j:JsonObject)=ServerConfig(j.text("url"),j.text("token"),j["debugHttp"]?.jsonPrimitive?.booleanOrNull==true)
    private fun active():ServerConfig?=prefs.getString("connection_v2",null)?.let {parseConfig(Json.parseToJsonElement(decrypt(it)).jsonObject)}
    private fun defaultName(config:ServerConfig)=runCatching {java.net.URI(config.url).let {it.host.orEmpty()+it.path.orEmpty().trimEnd('/')}}.getOrDefault("Hermes").ifBlank {"Hermes"}.take(80)
    private fun rows():Map<String,JsonObject> {
        prefs.getString("profiles_v1",null)?.let {value ->
            val data=Json.parseToJsonElement(decrypt(value)).jsonObject
            require(data.size<=10000) {"Saved connection metadata exceeds the mobile limit."}
            return data.mapValues {(id,row) ->
                val entry=row.jsonObject;require(id.matches(Regex("[a-f0-9]{64}")))
                if(entry.containsKey("token")) require(parseConfig(entry).scope==id) {"Saved credential scope does not match."}
                entry
            }
        }
        return active()?.let {config -> mapOf(config.scope to JsonObject(configRow(config)+mapOf("name" to JsonPrimitive(defaultName(config)))))}.orEmpty()
    }
    private fun encryptedRows(rows:Map<String,JsonObject>)=encrypt(JsonObject(rows).toString())
    fun profiles():List<SavedConnection> = synchronized(monitor) {rows().map {(id,row)->SavedConnection(id,row.text("name").ifBlank {"Hermes"},row.text("url"),row["debugHttp"]?.jsonPrimitive?.booleanOrNull==true,row.containsKey("token"))}.sortedBy {it.name.lowercase()}}
    fun profile(id:String):ServerConfig?=synchronized(monitor) {rows()[id]?.takeIf {it.containsKey("token")}?.let(::parseConfig)}
    fun writeConfig(config:ServerConfig)=synchronized(monitor) {
        val old=rows();require(old.count {it.value.containsKey("token")}<32 || old[config.scope]?.containsKey("token")==true) {"At most 32 saved credentials are supported. Remove an unused credential first."}
        val name=old[config.scope]?.text("name").orEmpty().ifBlank {defaultName(config)}
        val updated=old+mapOf(config.scope to JsonObject(configRow(config)+mapOf("name" to JsonPrimitive(name))))
        check(prefs.edit().putString("connection_v2",encrypt(configRow(config).toString())).putString("profiles_v1",encryptedRows(updated)).remove("token").commit()) {"Credential storage unavailable"}
    }
    fun activate(id:String):ServerConfig=synchronized(monitor) {val config=profile(id) ?: error("Enter the original credential again to use this connection.");writeConfig(config);config}
    fun rename(id:String,name:String)=synchronized(monitor) {
        require(name.isNotBlank() && name.length<=80 && name.none(Char::isISOControl)) {"Use a connection name of 1–80 characters."}
        val old=rows();val entry=old[id] ?: error("Saved connection no longer exists.")
        check(prefs.edit().putString("profiles_v1",encryptedRows(old+mapOf(id to JsonObject(entry+mapOf("name" to JsonPrimitive(name.trim())))))).commit()) {"Credential storage unavailable"}
    }
    fun forget(id:String)=synchronized(monitor) {
        val old=rows();val entry=old[id] ?: return@synchronized
        val editor=prefs.edit().putString("profiles_v1",encryptedRows(old+mapOf(id to JsonObject(entry.filterKeys {it!="token"}))))
        clearProviderCodes(id)
        clearRelayTargets(id)
        clearRelayCodes(id)
        val management=managementRows().filterKeys {it!=id}
        editor.putString("management_v1",encrypt(JsonObject(management).toString()))
        if(active()?.scope==id) editor.remove("connection_v2").remove("token")
        check(editor.commit()) {"Credential storage unavailable"}
    }
    fun forgetCurrent()=synchronized(monitor) {
        val current=active()
        if(current!=null) forget(current.scope) else check(prefs.edit().remove("connection_v2").remove("token").commit())
    }
    fun readConfig(legacy:AppPreferences?=null):ServerConfig?=synchronized(monitor) {
        active()?.let {return@synchronized it}
        // Only foreground migration pairs a legacy token with its original preference URL.
        if(legacy!=null && legacy.url.isNotBlank()) prefs.getString("token",null)?.let {
            val config=ServerConfig(legacy.url,decrypt(it),legacy.debugHttp)
            writeConfig(config);return@synchronized config
        }
        null
    }
    private fun managementRows()=prefs.getString("management_v1",null)?.let {Json.parseToJsonElement(decrypt(it)).jsonObject} ?: JsonObject(emptyMap())
    fun management(scope:String):JsonObject?=synchronized(monitor) {managementRows()[scope] as? JsonObject}
    fun saveManagement(scope:String,value:JsonObject?)=synchronized(monitor) {
        require(scope.matches(Regex("[a-f0-9]{64}")))
        if(value!=null) require(rows()[scope]?.containsKey("token")==true) {"The API connection credential was removed."}
        if(value==null) {clearProviderCodes(scope);clearRelayTargets(scope);clearRelayCodes(scope)}
        val previous=managementRows().filterKeys {it!=scope}
        val next=if(value==null) previous else previous+mapOf(scope to value)
        require(next.size<=32) {"Remove an unused management sign-in first."}
        check(prefs.edit().putString("management_v1",encrypt(JsonObject(next).toString())).commit()) {"Credential storage unavailable"}
    }
    private fun providerCodeRows()=prefs.getString("provider_codes_v1",null)?.let {Json.parseToJsonElement(decrypt(it)).jsonObject} ?: JsonObject(emptyMap())
    private fun liveProviderCodes(now:Long)=providerCodeRows().filterValues {row->(row as? JsonObject)?.get("expires_at")?.jsonPrimitive?.longOrNull?.let {it>now && it-now<=900_000}==true}
    private fun codeOwner(b:ProviderOAuthBinding)=buildJsonObject {put("api_scope",b.receipt.apiScope);put("url",b.receipt.url);put("profile",b.receipt.profile);put("actor",b.receipt.actor);put("provider",b.provider);put("home_id",b.homeId);put("session_id",b.sessionId)}
    val providerCodes:ProviderCodeStore get()=object:ProviderCodeStore {
        override fun save(binding:ProviderOAuthBinding,value:ProviderDeviceCode)=synchronized(monitor) {
            require(rows()[binding.receipt.apiScope]?.containsKey("token")==true && managementRows()[binding.receipt.apiScope]!=null)
            val live=liveProviderCodes(System.currentTimeMillis()).filterKeys {it!=binding.receipt.key}.entries.toList().takeLast(31).associate {it.key to it.value}
            val row=buildJsonObject {put("owner",codeOwner(binding));put("user_code",value.code);put("verification_url",value.url);put("expires_at",value.expiresAt)}
            check(prefs.edit().putString("provider_codes_v1",encrypt(JsonObject(live+mapOf(binding.receipt.key to row)).toString())).commit())
        }
        override fun read(binding:ProviderOAuthBinding,now:Long):ProviderDeviceCode?=synchronized(monitor) {
            val live=liveProviderCodes(now);check(prefs.edit().putString("provider_codes_v1",encrypt(JsonObject(live).toString())).commit())
            val row=live[binding.receipt.key] as? JsonObject ?: return@synchronized null
            if(row["owner"]!=codeOwner(binding) || rows()[binding.receipt.apiScope]?.containsKey("token")!=true || managementRows()[binding.receipt.apiScope]==null) return@synchronized null
            val expires=row["expires_at"]!!.jsonPrimitive.long
            ProviderDeviceCode.parse(JsonObject(row+mapOf("expires_in" to JsonPrimitive(maxOf(1,(expires-now)/1000)))),now).let {ProviderDeviceCode(it.code,it.url,expires)}
        }
        override fun remove(binding:ProviderOAuthBinding)=synchronized(monitor) {
            check(prefs.edit().putString("provider_codes_v1",encrypt(JsonObject(liveProviderCodes(System.currentTimeMillis()).filterKeys {it!=binding.receipt.key}).toString())).commit())
        }
    }
    private fun clearProviderCodes(scope:String)=check(prefs.edit().putString("provider_codes_v1",encrypt(JsonObject(providerCodeRows().filterValues {(it as? JsonObject)?.get("owner")?.jsonObject?.text("api_scope")!=scope}).toString())).commit())
    private fun relayTargetRows():JsonObject {
        val raw=prefs.getString("relay_targets_v1",null) ?: return JsonObject(emptyMap())
        return Json.parseToJsonElement(decrypt(raw)).jsonObject.also {require(it.size<=128)}
    }
    private fun relayOwner(row:JsonElement)=row.jsonObject["owner"]!!.jsonObject
    private fun relayCredentials(b:ManagementOperation)=relayCredentialScope(b.apiScope,b.url,b.profile,b.actor)
    private fun relayCredentialScope(scope:String,url:String,profile:String,actor:String):Boolean {
        val saved=managementRows()[scope] as? JsonObject ?: return false
        val token=saved["tokens"] as? JsonObject ?: return false
        return rows()[scope]?.containsKey("token")==true && saved.text("url").trim().trimEnd('/').plus("/").toHttpUrlOrNull()?.toString()==url && saved.text("profile")==profile && token.text("provider")+" · "+token.text("user_id")==actor
    }
    val relayTargets:HostRelayTargetStore get()=object:HostRelayTargetStore {
        override fun save(binding:HostRelayUnlinkBinding,account:HostRelayAccount)=synchronized(monitor) {
            require(relayCredentials(binding.receipt) && HostRelayUnlinkRequest.addressable(account.id) && account.name.length<=512 && account.handle.length<=512 && (account.name+account.handle).none(Char::isISOControl))
            val old=relayTargetRows();val owner=binding.owner();val current=old.filterKeys {it!=binding.receipt.key}
            if(current.size>=128 || current.values.count {val r=relayOwner(it);r["api_scope"]==owner["api_scope"] && r["url"]==owner["url"] && r["profile"]==owner["profile"] && r["actor"]==owner["actor"]}>=32) throw HostRelayPrivateFull()
            if(old[binding.receipt.key]!=null) {
                val previous=old[binding.receipt.key]!!.jsonObject
                require(relayOwner(previous)==owner && previous["account_id"]==JsonPrimitive(account.id) && previous["display_name"]==JsonPrimitive(account.name) && previous["handle"]==JsonPrimitive(account.handle))
            }
            val row=buildJsonObject {put("owner",owner);put("account_id",account.id);put("display_name",account.name);put("handle",account.handle)}
            check(prefs.edit().putString("relay_targets_v1",encrypt(JsonObject(current+mapOf(binding.receipt.key to row)).toString())).commit())
        }
        override fun read(binding:HostRelayUnlinkBinding):HostRelayAccount?=synchronized(monitor) {
            if(!relayCredentials(binding.receipt)) return@synchronized null
            val row=relayTargetRows()[binding.receipt.key] as? JsonObject ?: return@synchronized null
            if(row["owner"]!=binding.owner()) return@synchronized null
            fun text(key:String,max:Int):String {val v=row[key] as? JsonPrimitive ?: error("Invalid private target");require(v.isString && v.content.length<=max && v.content.none(Char::isISOControl));return v.content}
            HostRelayAccount(text("account_id",512),text("display_name",512),text("handle",512)).also {require(HostRelayUnlinkRequest.addressable(it.id))}
        }
        override fun remove(binding:HostRelayUnlinkBinding)=synchronized(monitor) {
            val old=relayTargetRows();if(old[binding.receipt.key]?.let(::relayOwner)!=binding.owner()) return@synchronized
            check(prefs.edit().putString("relay_targets_v1",encrypt(JsonObject(old.filterKeys {it!=binding.receipt.key}).toString())).commit())
        }
        override fun clear(target:ManagementTarget)=synchronized(monitor) {
            val next=relayTargetRows().filterValues {val o=relayOwner(it);!(o.text("api_scope")==target.apiScope && o.text("url")==target.url && o.text("profile")==target.profile && o.text("actor")==target.actor)}
            check(prefs.edit().putString("relay_targets_v1",encrypt(JsonObject(next).toString())).commit())
        }
    }
    private fun clearRelayTargets(scope:String) {
        val next=runCatching {relayTargetRows().filterValues {relayOwner(it).text("api_scope")!=scope}}.getOrNull()
        val editor=prefs.edit();if(next==null) editor.remove("relay_targets_v1") else editor.putString("relay_targets_v1",encrypt(JsonObject(next).toString()))
        check(editor.commit())
    }
    private fun relayCodeRows()=prefs.getString("relay_codes_v1",null)?.let {Json.parseToJsonElement(decrypt(it)).jsonObject.also {j->require(j.size<=128)}} ?: JsonObject(emptyMap())
    private fun liveRelayCodes(now:Long)=relayCodeRows().filterValues {row->val expiry=row.jsonObject["expires_at"]?.jsonPrimitive?.longOrNull;expiry!=null && expiry>now && expiry-now<=900_000}
    private fun relayCodeCapacity(target:ManagementTarget,rows:Map<String,JsonElement>) {
        if(rows.size>=128 || rows.values.count {val o=relayOwner(it);o.text("api_scope")==target.apiScope && o.text("url")==target.url && o.text("profile")==target.profile && o.text("actor")==target.actor}>=32) throw HostRelayCodeFull()
    }
    val relayCodes:HostRelayCodeStore get()=object:HostRelayCodeStore {
        override fun beforeStart(target:ManagementTarget,now:Long)=synchronized(monitor) {
            require(relayCredentialScope(target.apiScope,target.url,target.profile,target.actor));val live=liveRelayCodes(now);relayCodeCapacity(target,live)
            check(prefs.edit().putString("relay_codes_v1",encrypt(JsonObject(live).toString())).commit())
        }
        override fun save(binding:HostRelayLinkBinding,code:HostRelayCode,now:Long)=synchronized(monitor) {
            require(relayCredentials(binding.receipt) && binding.issued && binding.status in setOf("unverified","pending") && HostRelayLinkPolicy.code(code.code) && code.expiresAt>now && code.expiresAt-now<=900_000 && code.originalBot.length<=8192 && (code.botUrl==null || HostRelayLinkPolicy.bot(binding.receipt.target,code.code,code.botUrl)))
            val old=liveRelayCodes(now);val existing=old[binding.receipt.key] as? JsonObject
            if(existing!=null) {require(existing["owner"]==binding.owner() && existing["code"]==JsonPrimitive(code.code) && existing["bot_url"]==(code.botUrl?.let(::JsonPrimitive) ?: JsonNull) && existing["original_bot"]==JsonPrimitive(code.originalBot) && existing["expires_at"]==JsonPrimitive(code.expiresAt))}
            val next=old.filterKeys {it!=binding.receipt.key};relayCodeCapacity(ManagementTarget(binding.receipt.apiScope,binding.receipt.url,binding.receipt.profile,binding.receipt.actor),next)
            val row=buildJsonObject {put("owner",binding.owner());put("code",code.code);put("bot_url",code.botUrl?.let(::JsonPrimitive) ?: JsonNull);put("original_bot",code.originalBot);put("expires_at",code.expiresAt)}
            check(prefs.edit().putString("relay_codes_v1",encrypt(JsonObject(next+mapOf(binding.receipt.key to row)).toString())).commit())
        }
        override fun read(binding:HostRelayLinkBinding,now:Long):HostRelayCode?=synchronized(monitor) {
            if(!binding.issued || binding.status !in setOf("unverified","pending") || !relayCredentials(binding.receipt)) return@synchronized null
            val live=liveRelayCodes(now);check(prefs.edit().putString("relay_codes_v1",encrypt(JsonObject(live).toString())).commit());val row=live[binding.receipt.key] as? JsonObject ?: return@synchronized null
            if(row["owner"]!=binding.owner()) return@synchronized null
            val code=HostRelayLinkPolicy.text(row,"code",256);require(HostRelayLinkPolicy.code(code));val bot=(row["bot_url"] as? JsonPrimitive)?.takeIf {it.isString}?.content;require(bot==null || HostRelayLinkPolicy.bot(binding.receipt.target,code,bot))
            HostRelayCode(code,bot,HostRelayLinkPolicy.text(row,"original_bot",8192),row["expires_at"]!!.jsonPrimitive.long)
        }
        override fun remove(binding:HostRelayLinkBinding)=synchronized(monitor) {val old=relayCodeRows();if(old[binding.receipt.key]?.jsonObject?.get("owner")!=binding.owner()) return@synchronized;check(prefs.edit().putString("relay_codes_v1",encrypt(JsonObject(old.filterKeys {it!=binding.receipt.key}).toString())).commit())}
        override fun clear(target:ManagementTarget)=synchronized(monitor) {val next=relayCodeRows().filterValues {val o=relayOwner(it);!(o.text("api_scope")==target.apiScope && o.text("url")==target.url && o.text("profile")==target.profile && o.text("actor")==target.actor)};check(prefs.edit().putString("relay_codes_v1",encrypt(JsonObject(next).toString())).commit())}
    }
    private fun clearRelayCodes(scope:String) {val next=runCatching {relayCodeRows().filterValues {relayOwner(it).text("api_scope")!=scope}}.getOrNull();val edit=prefs.edit();if(next==null) edit.remove("relay_codes_v1") else edit.putString("relay_codes_v1",encrypt(JsonObject(next).toString()));check(edit.commit())}
    fun clear()=synchronized(monitor) {check(prefs.edit().clear().commit())}
    companion object {private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()}
}
