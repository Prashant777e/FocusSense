package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.BuildConfig
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safe migration from v5 to v6: ensure syncStatus and remove unneeded columns gracefully
        try {
            db.execSQL("ALTER TABLE activity_logs ADD COLUMN IF NOT EXISTS syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE location_history ADD COLUMN IF NOT EXISTS syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
        } catch (e: Exception) {}
    }
}

@Database(
    entities = [
        FamilyGroupEntity::class,
        UserEntity::class,
        DeviceEntity::class,
        ActivityLogEntity::class,
        ScheduleRuleEntity::class,
        LocationPointEntity::class,
        InstalledAppEntity::class,
        com.example.data.model.AllowedYouTubeChannelEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class FocusSenseDatabase : RoomDatabase() {

    abstract fun familyGroupDao(): FamilyGroupDao
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun locationDao(): LocationDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun youtubeDao(): YouTubeDao

    companion object {
        @Volatile
        private var INSTANCE: FocusSenseDatabase? = null

        fun getInstance(context: Context): FocusSenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    FocusSenseDatabase::class.java,
                    "focussense_database"
                ).addMigrations(MIGRATION_5_6)

                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration(dropAllTables = true)
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
