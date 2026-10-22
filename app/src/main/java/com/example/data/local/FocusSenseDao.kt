package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: FamilyGroupEntity)

    @Query("SELECT * FROM family_groups LIMIT 1")
    fun getFamilyGroup(): Flow<FamilyGroupEntity?>

    @Query("SELECT * FROM family_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): FamilyGroupEntity?

    @Query("DELETE FROM family_groups")
    suspend fun deleteAll()
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = :role ORDER BY name ASC")
    fun getUsersByRole(role: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = :role ORDER BY name ASC")
    suspend fun getUsersByRoleSync(role: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE userId = :id LIMIT 1")
    fun getUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE userId = :id LIMIT 1")
    suspend fun getUserByIdSync(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("UPDATE users SET pin = :pin WHERE userId = :userId")
    suspend fun updatePin(userId: String, pin: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Query("SELECT * FROM devices WHERE userId = :userId")
    fun getDevicesForUser(userId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("DELETE FROM devices")
    suspend fun deleteAllDevices()
}

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ActivityLogEntity>)

    @Query("SELECT * FROM activity_logs ORDER BY recordedAt DESC")
    fun getAllLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE childId = :childId ORDER BY recordedAt DESC")
    fun getLogsForChild(childId: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE isFlagged = 1 ORDER BY recordedAt DESC")
    fun getFlaggedLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE childId = :childId AND isFlagged = 1 ORDER BY recordedAt DESC")
    fun getFlaggedLogsForChild(childId: String): Flow<List<ActivityLogEntity>>

    @Query("UPDATE activity_logs SET isAcknowledged = 1 WHERE logId = :logId")
    suspend fun markAcknowledged(logId: String)

    @Query("DELETE FROM activity_logs WHERE logId = :logId")
    suspend fun deleteLog(logId: String)

    @Query("UPDATE activity_logs SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllSynced()

    @Query("SELECT COUNT(*) FROM activity_logs WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM activity_logs WHERE isSynced = 0 ORDER BY recordedAt ASC")
    suspend fun getUnsyncedLogs(): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs ORDER BY recordedAt DESC LIMIT 100")
    suspend fun getAllLogsSync(): List<ActivityLogEntity>
}

@Dao
interface ScheduleRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ScheduleRuleEntity>)

    @Update
    suspend fun update(rule: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE ruleId = :ruleId")
    suspend fun delete(ruleId: String)

    @Query("SELECT * FROM schedule_rules ORDER BY startTime ASC")
    fun getAllRules(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE childId = :childId ORDER BY startTime ASC")
    fun getRulesForChild(childId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE childId = :childId")
    suspend fun getRulesForChildSync(childId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE isActive = 1")
    suspend fun getAllActiveRulesSync(): List<ScheduleRuleEntity>

    @Query("UPDATE schedule_rules SET isActive = :isActive WHERE ruleId = :ruleId")
    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean)
}

@Dao
interface InstalledAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<com.example.data.model.InstalledAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: com.example.data.model.InstalledAppEntity)

    @Query("SELECT * FROM installed_apps WHERE childId = :childId ORDER BY appName ASC")
    fun getInstalledApps(childId: String): Flow<List<com.example.data.model.InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE childId = :childId ORDER BY appName ASC")
    suspend fun getInstalledAppsSync(childId: String): List<com.example.data.model.InstalledAppEntity>

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    fun getAllInstalledApps(): Flow<List<com.example.data.model.InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    suspend fun getAllInstalledAppsSync(): List<com.example.data.model.InstalledAppEntity>

    @Query("UPDATE installed_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun updateAppBlockStatus(packageName: String, isBlocked: Boolean)

    @Query("UPDATE installed_apps SET isBlocked = :isBlocked WHERE childId = :childId AND packageName = :packageName")
    suspend fun updateAppBlockStatusForChild(childId: String, packageName: String, isBlocked: Boolean)

    @Query("SELECT * FROM installed_apps WHERE isBlocked = 1")
    suspend fun getAllBlockedAppsSync(): List<com.example.data.model.InstalledAppEntity>

    @Query("SELECT packageName FROM installed_apps WHERE isBlocked = 1")
    suspend fun getBlockedPackageNamesSync(): List<String>
}

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: LocationPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_history WHERE childId = :childId ORDER BY recordedAt DESC LIMIT 1")
    fun getLatestLocationForChild(childId: String): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_history WHERE childId = :childId ORDER BY recordedAt DESC LIMIT 50")
    fun getLocationHistoryForChild(childId: String): Flow<List<LocationPointEntity>>

    @Query("UPDATE location_history SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllSynced()

    @Query("SELECT COUNT(*) FROM location_history WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM location_history WHERE isSynced = 0 ORDER BY recordedAt ASC")
    suspend fun getUnsyncedLocations(): List<LocationPointEntity>

    @Query("SELECT * FROM location_history ORDER BY recordedAt DESC LIMIT 50")
    suspend fun getAllLocationsSync(): List<LocationPointEntity>
}
