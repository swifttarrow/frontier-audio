package com.jarvis.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

interface SessionStore {
    fun getOrCreateDeviceId(): String
    fun getSessionToken(): String?
    fun setSessionToken(token: String)
    fun clearSession()
}

class EncryptedSessionStore(context: Context) : SessionStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "jarvis_session_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getOrCreateDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    override fun getSessionToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)

    override fun setSessionToken(token: String) {
        prefs.edit().putString(KEY_SESSION_TOKEN, token).apply()
    }

    override fun clearSession() {
        prefs.edit().remove(KEY_SESSION_TOKEN).apply()
        // deviceId is preserved
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_TOKEN = "session_token"
    }
}
