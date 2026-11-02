package org.hermesmobile.app.data

import android.content.Context
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
data class AppPreferences(val theme: String = "LAVENDER", val chinese: Boolean = true, val pinyin: Boolean = true, val reducedMotion: Boolean = false, val demo: Boolean = true, val url: String = "", val debugHttp: Boolean = false, val onDeviceSpeechOnly: Boolean = true, val allowNetworkSpeech: Boolean = false, val speechRate: Float = 1f, val notifications: Boolean = false, val monitorRuns: Boolean = false, val automationScope: String = "")
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
    val flow = store.data.map { p -> AppPreferences(p[theme] ?: "LAVENDER", p[chinese] ?: true, p[pinyin] ?: true, p[motion] ?: false, p[demo] ?: true, p[url].orEmpty(), p[http] ?: false,p[localSpeech] ?: true,p[networkSpeech] ?: false,(p[rate] ?: 1f).coerceIn(.75f,1.25f),p[notify] ?: false,p[monitor] ?: false,p[automation].orEmpty()) }
    suspend fun update(p: AppPreferences) { store.edit { it[theme]=p.theme; it[chinese]=p.chinese; it[pinyin]=p.pinyin; it[motion]=p.reducedMotion; it[demo]=p.demo; it[url]=p.url; it[http]=p.debugHttp;it[localSpeech]=p.onDeviceSpeechOnly;it[networkSpeech]=p.allowNetworkSpeech;it[rate]=p.speechRate.coerceIn(.75f,1.25f);it[notify]=p.notifications;it[monitor]=p.monitorRuns;it[automation]=p.automationScope } }
}
class SecureCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
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
    @Synchronized fun writeConfig(config: ServerConfig) {
        val payload=buildJsonObject {put("url",config.url);put("token",config.token);put("debugHttp",config.allowDebugHttp)}.toString()
        check(prefs.edit().putString("connection_v2",encrypt(payload)).remove("token").commit()) {"Credential storage unavailable"}
    }
    @Synchronized fun readConfig(legacy: AppPreferences? = null): ServerConfig? {
        prefs.getString("connection_v2",null)?.let {
            val j=Json.parseToJsonElement(decrypt(it)).jsonObject
            return ServerConfig(j.text("url"),j.text("token"),j["debugHttp"]?.jsonPrimitive?.booleanOrNull==true)
        }
        // Only a foreground caller supplies the legacy URL. Workers never pair independent stores.
        if(legacy!=null && legacy.url.isNotBlank()) prefs.getString("token",null)?.let {
            val config=ServerConfig(legacy.url,decrypt(it),legacy.debugHttp)
            writeConfig(config);return config
        }
        return null
    }
    @Synchronized fun clear() {check(prefs.edit().clear().commit())}
}
