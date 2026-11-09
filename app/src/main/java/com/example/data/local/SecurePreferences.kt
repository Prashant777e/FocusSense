package com.example.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "focussense_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for secure storage initialization failures in emulators/tests
        context.getSharedPreferences("focussense_fallback_prefs", Context.MODE_PRIVATE)
    }

    var accessToken: String
        get() = sharedPreferences.getString("access_token", "") ?: ""
        set(value) = sharedPreferences.edit().putString("access_token", value).apply()

    var refreshToken: String
        get() = sharedPreferences.getString("refresh_token", "") ?: ""
        set(value) = sharedPreferences.edit().putString("refresh_token", value).apply()

    var userRole: String
        get() = sharedPreferences.getString("user_role", "") ?: ""
        set(value) = sharedPreferences.edit().putString("user_role", value).apply()

    var userId: String
        get() = sharedPreferences.getString("user_id", "") ?: ""
        set(value) = sharedPreferences.edit().putString("user_id", value).apply()

    var familyId: String
        get() = sharedPreferences.getString("family_id", "") ?: ""
        set(value) = sharedPreferences.edit().putString("family_id", value).apply()

    fun clearSession() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_role")
            .remove("user_id")
            .remove("family_id")
            .apply()
    }
}
