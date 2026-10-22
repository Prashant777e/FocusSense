package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "family_groups")
data class FamilyGroupEntity(
    @PrimaryKey val groupId: String = UUID.randomUUID().toString(),
    val familyName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),
    val groupId: String,
    val email: String,
    val password: String,
    val role: String, // "parent" or "child"
    val name: String,
    val pin: String = "1234",
    val avatar: String = "default"
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String = UUID.randomUUID().toString(),
    val userId: String,
    val deviceName: String,
    val token: String = UUID.randomUUID().toString(),
    val batteryPercent: Int = 85,
    val isOnline: Boolean = true,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val logId: String = UUID.randomUUID().toString(),
    val childId: String,
    val packageName: String,
    val appName: String,
    val contentTitle: String,
    val extractedText: String,
    val isFlagged: Boolean,
    val threatCategory: String? = null, // "Cyberbullying", "Explicit Content", "Academic Distraction", "Self-Harm Risk", "Stranger Risk", "Safe"
    val confidenceScore: Float = 0.0f,
    val aiAnalysisSummary: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isAcknowledged: Boolean = false
)

@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey val ruleId: String = UUID.randomUUID().toString(),
    val childId: String,
    val ruleName: String,
    val category: String, // "Study", "Homework", "Bedtime", "Outdoor", "Free Time"
    val startTime: String, // "15:00"
    val endTime: String,   // "17:00"
    val dayOfWeek: String = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
    val restrictedPackages: String, // comma-separated package names
    val isActive: Boolean = true
)

@Entity(tableName = "location_history")
data class LocationPointEntity(
    @PrimaryKey val locId: String = UUID.randomUUID().toString(),
    val childId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f,
    val locationName: String = "Current Location",
    val recordedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

data class RestrictedAppInfo(
    val packageName: String,
    val appName: String,
    val iconName: String,
    val category: String,
    val isInstalledOnDevice: Boolean = false
)

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val id: String, // childId + "_" + packageName
    val childId: String,
    val packageName: String,
    val appName: String,
    val category: String,
    val isBlocked: Boolean = false,
    val isSystemApp: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

val PREDEFINED_RESTRICTED_APPS = listOf(
    RestrictedAppInfo("com.google.android.youtube", "YouTube", "video", "Entertainment / Streaming"),
    RestrictedAppInfo("com.zhiliaoapp.musically", "TikTok", "social", "Social Media"),
    RestrictedAppInfo("com.roblox.client", "Roblox", "game", "Gaming"),
    RestrictedAppInfo("com.instagram.android", "Instagram", "social", "Social Media"),
    RestrictedAppInfo("com.discord", "Discord", "chat", "Messaging / Community"),
    RestrictedAppInfo("com.snapchat.android", "Snapchat", "camera", "Ephemeral Social"),
    RestrictedAppInfo("com.netflix.mediaclient", "Netflix", "video", "Entertainment / Video"),
    RestrictedAppInfo("com.epicgames.fortnite", "Fortnite", "game", "Gaming")
)
