package com.tekinflow.demo.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tekinflow.demo.BuildConfig
import com.tekinflow.demo.data.local.DatabasePassphraseProvider
import com.tekinflow.demo.data.local.SatinalmaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): SatinalmaDatabase {
        passphraseProvider.prepareEncryptedDatabase(DATABASE_NAME)
        val builder = Room.databaseBuilder(
            context,
            SatinalmaDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(
                net.sqlcipher.database.SupportFactory(passphraseProvider.createPassphrase())
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build().also {
            passphraseProvider.markEncryptedDatabaseInitialized()
        }
    }

    @Provides
    fun provideRequestDao(db: SatinalmaDatabase) = db.requestDao()

    @Provides
    fun provideNotificationDao(db: SatinalmaDatabase) = db.notificationDao()

    @Provides
    fun provideUserDao(db: SatinalmaDatabase) = db.userDao()

    @Provides
    fun providePendingSyncDao(db: SatinalmaDatabase) = db.pendingSyncDao()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_sync ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE pending_sync SET idempotencyKey = id WHERE idempotencyKey = ''")
            db.execSQL("ALTER TABLE pending_sync ADD COLUMN failed INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN packageCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN spotDriverName TEXT")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN spotDriverIdentityNumber TEXT")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN spotDriverCompanyName TEXT")
            db.execSQL("ALTER TABLE notifications ADD COLUMN requestId TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN isSuperAdmin INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN items TEXT NOT NULL DEFAULT '[]'")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN assignedPurchasingStaffName TEXT")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN contactPersonName TEXT")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN actualPurchaseDate INTEGER")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN spotDriverPhone TEXT")
            db.execSQL("ALTER TABLE purchase_requests ADD COLUMN deliveredTo TEXT")
        }
    }

    private const val DATABASE_NAME = "satinalma_db"
}
