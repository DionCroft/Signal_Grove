package org.hermesmobile.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("preferences")
data class AppPreferences(val theme: String = "LAVENDER", val chinese: Boolean = true, val pinyin: Boolean = true, val reducedMotion: Boolean = false, val demo: Boolean = true, val url: String = "", val debugHttp: Boolean = false)
class PreferenceStore(context: Context) {
    private val store = context.dataStore
    private val theme = stringPreferencesKey("theme")
    private val chinese = booleanPreferencesKey("chinese")
    private val pinyin = booleanPreferencesKey("pinyin")
    private val motion = booleanPreferencesKey("reduced_motion")
    private val demo = booleanPreferencesKey("demo")
    private val url = stringPreferencesKey("server_url")
    private val http = booleanPreferencesKey("debug_http")
    val flow = store.data.map { p -> AppPreferences(p[theme] ?: "LAVENDER", p[chinese] ?: true, p[pinyin] ?: true, p[motion] ?: false, p[demo] ?: true, p[url].orEmpty(), p[http] ?: false) }
    suspend fun update(p: AppPreferences) { store.edit { it[theme]=p.theme; it[chinese]=p.chinese; it[pinyin]=p.pinyin; it[motion]=p.reducedMotion; it[demo]=p.demo; it[url]=p.url; it[http]=p.debugHttp } }
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
    @Synchronized fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        check(prefs.edit().putString("token", Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).commit()) { "Credential storage unavailable" }
    }
    @Synchronized fun read(): String {
        val encoded = prefs.getString("token", null) ?: return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 28) { "Saved credential is invalid; enter it again." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))) }
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)
    }
    fun clear() { check(prefs.edit().clear().commit()) }
}
