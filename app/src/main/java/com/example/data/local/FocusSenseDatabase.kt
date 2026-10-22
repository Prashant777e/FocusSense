package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity

@Database(
    entities = [
        FamilyGroupEntity::class,
        UserEntity::class,
        DeviceEntity::class,
        ActivityLogEntity::class,
        ScheduleRuleEntity::class,
        LocationPointEntity::class,
        InstalledAppEntity::class
    ],
    version = 4,
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

    companion object {
        @Volatile
        private var INSTANCE: FocusSenseDatabase? = null

        fun getInstance(context: Context): FocusSenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusSenseDatabase::class.java,
                    "focussense_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
