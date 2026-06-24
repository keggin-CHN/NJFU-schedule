package com.njfu.schedule.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val FILE_NAME = "njfu_login_secure"

    fun get(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun migrateIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val oldId = oldPrefs.getString("student_id", "") ?: ""
        val oldPwd = oldPrefs.getString("password", "") ?: ""

        if (oldId.isEmpty() && oldPwd.isEmpty()) return

        val secure = get(context)
        if (secure.getString("student_id", "")?.isEmpty() == true) {
            secure.edit()
                .putString("student_id", oldId)
                .putString("password", oldPwd)
                .putString("remarks", oldPrefs.getString("remarks", ""))
                .putLong("global_cache_last_sync", oldPrefs.getLong("global_cache_last_sync", 0))
                .apply()
        }

        oldPrefs.edit().clear().apply()
    }
}
