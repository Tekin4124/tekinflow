package com.tekinflow.demo.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import net.sqlcipher.database.SQLiteDatabase
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun prepareEncryptedDatabase(databaseName: String) {
        val databaseFile = context.getDatabasePath(databaseName)
        if (!prefs.getBoolean(KEY_ENCRYPTED_DB_INITIALIZED, false) && databaseFile.exists()) {
            context.deleteDatabase(databaseName)
        }
    }

    fun createPassphrase(): ByteArray {
        val encodedPassphrase = prefs.getString(KEY_DATABASE_PASSPHRASE, null)
            ?: generatePassphrase().also { generated ->
                prefs.edit()
                    .putString(KEY_DATABASE_PASSPHRASE, generated)
                    .apply()
            }

        return SQLiteDatabase.getBytes(encodedPassphrase.toCharArray())
    }

    fun markEncryptedDatabaseInitialized() {
        prefs.edit()
            .putBoolean(KEY_ENCRYPTED_DB_INITIALIZED, true)
            .apply()
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val PREFS_NAME = "satinalma_secure_database"
        const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
        const val KEY_ENCRYPTED_DB_INITIALIZED = "encrypted_database_initialized"
        const val PASSPHRASE_BYTES = 32
    }
}
