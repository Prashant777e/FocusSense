package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.ai.ThreatAnalysisResult
import com.example.ai.ThreatEvaluationEngine
import com.example.data.local.FocusSenseDatabase
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.util.InstalledAppScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class SentinelEvent {
    data class ContentFlagged(val log: ActivityLogEntity, val threatResult: ThreatAnalysisResult) : SentinelEvent()
    data class AppRestricted(val appName: String, val ruleName: String, val endTime: String) : SentinelEvent()
    data class LocationUpdated(val locationName: String, val lat: Double, val lng: Double) : SentinelEvent()
    data class SyncCompleted(val syncedCount: Int) : SentinelEvent()
}

data class ActiveAppBlockInfo(
    val isBlocked: Boolean,
    val ruleName: String = "",
    val category: String = "",
    val endTime: String = "",
    val restrictedAppName: String = ""
)

class FocusSenseRepository(context: Context) {

    private val db = FocusSenseDatabase.getInstance(context)
    private val threatEngine = ThreatEvaluationEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Current Session State
    private val prefs = context.getSharedPreferences("focussense_session_prefs", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isSessionReady = MutableStateFlow<Boolean>(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Network & Zero Data-Loss Sync State
    private val _isNetworkConnected = MutableStateFlow<Boolean>(true)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Central Server & Database URL (Render + Supabase PostgreSQL)
    private val _serverUrl = MutableStateFlow<String>(
        prefs.getString("server_api_url", "https://parental-controll.onrender.com") ?: "https://parental-controll.onrender.com"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Real-Time Push / Sentinel Event Stream
    private val _eventStream = MutableSharedFlow<SentinelEvent>(extraBufferCapacity = 64)
    val eventStream: SharedFlow<SentinelEvent> = _eventStream.asSharedFlow()

    fun updateServerUrl(url: String) {
        val cleanUrl = url.trim()
        _serverUrl.value = cleanUrl
        prefs.edit().putString("server_api_url", cleanUrl).apply()
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val service = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            val response = service.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val dbStatus = body.database ?: "connected"
                val counts = body.supabase_counts
                val countsSummary = if (counts != null && counts.isNotEmpty()) {
                    " (Users: ${counts["users"] ?: 0}, Logs: ${counts["activity_logs"] ?: 0}, Locations: ${counts["location_points"] ?: 0})"
                } else ""
                Pair(true, "Connected: ${body.service} • DB: $dbStatus$countsSummary")
            } else {
                Pair(false, "Server responded with HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Connection failed")
        }
    }

    // In-Memory App Blocking Cache for 0ms Latency in AccessibilityService
    private val _instantBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val instantBlockedPackages: StateFlow<Set<String>> = _instantBlockedPackages.asStateFlow()

    private val _temporaryUnlockedPackages = ConcurrentHashMap<String, Long>()

    // Pomodoro Child Focus Lock State
    data class PomodoroState(
        val packageName: String,
        val appName: String,
        val durationMinutes: Int,
        val remainingSeconds: Long,
        val isRunning: Boolean
    )

    private val _pomodoroState = MutableStateFlow<PomodoroState?>(null)
    val pomodoroState: StateFlow<PomodoroState?> = _pomodoroState.asStateFlow()

    private var pomodoroJob: Job? = null

    fun startPomodoro(packageName: String, appName: String, durationMinutes: Int) {
        pomodoroJob?.cancel()
        val totalSecs = (durationMinutes * 60).toLong()
        _pomodoroState.value = PomodoroState(
            packageName = packageName.lowercase(),
            appName = appName,
            durationMinutes = durationMinutes,
            remainingSeconds = totalSecs,
            isRunning = true
        )

        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = totalSecs
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = 0, isRunning = false)
            }
        }
    }

    fun pausePomodoro() {
        _pomodoroState.value = _pomodoroState.value?.copy(isRunning = false)
        pomodoroJob?.cancel()
    }

    fun resumePomodoro() {
        val current = _pomodoroState.value ?: return
        if (current.remainingSeconds <= 0) return
        _pomodoroState.value = current.copy(isRunning = true)

        pomodoroJob?.cancel()
        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = current.remainingSeconds
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = 0, isRunning = false)
            }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        _pomodoroState.value = null
    }

    fun isPackageBlockedSync(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        if (tempExpiry > System.currentTimeMillis()) {
            return false
        }
        return _instantBlockedPackages.value.any { it.equals(lower, ignoreCase = true) }
    }

    fun temporarilyUnlockApp(packageName: String, minutes: Int = 15) {
        val lower = packageName.lowercase()
        _temporaryUnlockedPackages[lower] = System.currentTimeMillis() + (minutes * 60 * 1000L)
    }

    fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        return tempExpiry > System.currentTimeMillis()
    }

    init {
        // Restore active user session from local preferences or auto-seed demo family on fresh install
        scope.launch {
            val savedUserId = prefs.getString("saved_user_id", null)
            var activeUser: UserEntity? = null
            if (!savedUserId.isNullOrBlank()) {
                activeUser = db.userDao().getUserByIdSync(savedUserId)
            }

            if (activeUser == null) {
                val existingParents = db.userDao().getUsersByRoleSync("parent")
                if (existingParents.isNotEmpty()) {
                    activeUser = existingParents.first()
                } else {
                    // Seed initial demo data for instant out-of-the-box exploration in Android Studio & Emulators
                    val defaultGroup = FamilyGroupEntity(
                        groupId = "group-demo-01",
                        familyName = "FocusSense Family"
                    )
                    db.familyGroupDao().insert(defaultGroup)

                    val defaultParent = UserEntity(
                        userId = "parent-sarah-01",
                        groupId = defaultGroup.groupId,
                        email = "parent@demo.com",
                        password = "demo",
                        role = "parent",
                        name = "Sarah Connor (Parent)",
                        pin = "1234"
                    )
                    db.userDao().insert(defaultParent)

                    val defaultChild = UserEntity(
                        userId = "child-leo-01",
                        groupId = defaultGroup.groupId,
                        email = "leo@family.internal",
                        password = "demo",
                        role = "child",
                        name = "Leo",
                        pin = ""
                    )
                    db.userDao().insert(defaultChild)

                    val defaultChildDevice = DeviceEntity(
                        deviceId = "dev-child-01",
                        userId = defaultChild.userId,
                        deviceName = "Leo's Phone (Galaxy A54)",
                        batteryPercent = 88,
                        isOnline = true
                    )
                    db.deviceDao().insert(defaultChildDevice)

                    val defaultRule1 = ScheduleRuleEntity(
                        ruleId = "rule-school-hours",
                        childId = defaultChild.userId,
                        ruleName = "School Focus Hours",
                        category = "Education",
                        startTime = "08:30",
                        endTime = "15:00",
                        dayOfWeek = "Mon,Tue,Wed,Thu,Fri",
                        restrictedPackages = "com.zhiliaoapp.musically,com.instagram.android,com.roblox.client",
                        isActive = true
                    )
                    val defaultRule2 = ScheduleRuleEntity(
                        ruleId = "rule-bedtime-curfew",
                        childId = defaultChild.userId,
                        ruleName = "Bedtime Curfew",
                        category = "Bedtime",
                        startTime = "21:30",
                        endTime = "06:30",
                        dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
                        restrictedPackages = "com.google.android.youtube,com.zhiliaoapp.musically,com.instagram.android",
                        isActive = true
                    )
                    db.scheduleRuleDao().insert(defaultRule1)
                    db.scheduleRuleDao().insert(defaultRule2)

                    val now = System.currentTimeMillis()
                    db.activityLogDao().insert(
                        ActivityLogEntity(
                            logId = "log-seed-01",
                            childId = defaultChild.userId,
                            packageName = "com.google.android.youtube",
                            appName = "YouTube",
                            contentTitle = "Crash Course Biology: Cell Structures",
                            extractedText = "Learning about mitochondria and cell biology structures for exam.",
                            recordedAt = now - (15 * 60 * 1000L),
                            isFlagged = false,
                            threatCategory = "Safe"
                        )
                    )
                    db.activityLogDao().insert(
                        ActivityLogEntity(
                            logId = "log-seed-02",
                            childId = defaultChild.userId,
                            packageName = "com.google.android.apps.messaging",
                            appName = "Messages",
                            contentTitle = "Family Group Chat",
                            extractedText = "Dad: Remember to head home right after science club practice!",
                            recordedAt = now - (45 * 60 * 1000L),
                            isFlagged = false,
                            threatCategory = "Safe"
                        )
                    )

                    db.locationDao().insert(
                        LocationPointEntity(
                            locId = "loc-seed-01",
                            childId = defaultChild.userId,
                            latitude = 37.7749,
                            longitude = -122.4194,
                            accuracy = 4.5f,
                            locationName = "Lincoln High School Campus",
                            recordedAt = now - (10 * 60 * 1000L)
                        )
                    )

                    val seedApps = listOf(
                        InstalledAppEntity("app-1", defaultChild.userId, "com.google.android.youtube", "YouTube", "Streaming", isBlocked = false),
                        InstalledAppEntity("app-2", defaultChild.userId, "com.zhiliaoapp.musically", "TikTok", "Social", isBlocked = true),
                        InstalledAppEntity("app-3", defaultChild.userId, "com.roblox.client", "Roblox", "Gaming", isBlocked = false),
                        InstalledAppEntity("app-4", defaultChild.userId, "com.instagram.android", "Instagram", "Social", isBlocked = true),
                        InstalledAppEntity("app-5", defaultChild.userId, "com.android.chrome", "Chrome", "Browsers", isBlocked = false),
                        InstalledAppEntity("app-6", defaultChild.userId, "com.whatsapp", "WhatsApp", "Social", isBlocked = false),
                        InstalledAppEntity("app-7", defaultChild.userId, "com.google.android.apps.messaging", "Messages", "Social", isBlocked = false)
                    )
                    db.installedAppDao().insertAll(seedApps)

                    activeUser = defaultParent
                }
            }

            if (activeUser != null) {
                _currentUser.value = activeUser
                prefs.edit().putString("saved_user_id", activeUser.userId).apply()
                if (activeUser.role == "child") {
                    _selectedChildId.value = activeUser.userId
                } else {
                    val children = db.userDao().getUsersByRoleSync("child")
                    if (children.isNotEmpty()) {
                        _selectedChildId.value = children.first().userId
                    }
                }
            }

            // Initialize in-memory blocked apps cache from DB
            try {
                val blocked = db.installedAppDao().getBlockedPackageNamesSync()
                _instantBlockedPackages.value = blocked.map { it.lowercase() }.toSet()
            } catch (_: Exception) {}

            _isSessionReady.value = true
        }
    }

    suspend fun launchDemoParent() = withContext(Dispatchers.IO) {
        val parent = db.userDao().getUsersByRoleSync("parent").firstOrNull()
        if (parent != null) {
            _currentUser.value = parent
            prefs.edit().putString("saved_user_id", parent.userId).apply()
            val children = db.userDao().getUsersByRoleSync("child")
            if (children.isNotEmpty()) {
                _selectedChildId.value = children.first().userId
            }
        }
    }

    suspend fun launchDemoChild() = withContext(Dispatchers.IO) {
        val child = db.userDao().getUsersByRoleSync("child").firstOrNull()
        if (child != null) {
            _currentUser.value = child
            prefs.edit().putString("saved_user_id", child.userId).apply()
            _selectedChildId.value = child.userId
        }
    }

    // Role & User Authentication Methods
    suspend fun signUpParent(
        name: String,
        familyName: String,
        email: String,
        password: String,
        pin: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()

            // 1. Attempt to register on Central Server (Render / Supabase)
            var serverParentUser: UserEntity? = null
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.registerParent(
                    com.example.data.remote.RegisterParentDto(
                        name = name.trim(),
                        family_name = familyName.trim(),
                        email = normalizedEmail,
                        password = password,
                        pin = if (pin.isNotBlank()) pin.trim() else "1234"
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val group = FamilyGroupEntity(
                        groupId = body.user.group_id,
                        familyName = body.family_name.ifBlank { "${name.trim()}'s Family" }
                    )
                    db.familyGroupDao().insert(group)

                    val parent = UserEntity(
                        userId = body.user.user_id,
                        groupId = body.user.group_id,
                        email = body.user.email,
                        password = password,
                        role = body.user.role,
                        name = body.user.name,
                        pin = body.user.pin,
                        avatar = body.user.avatar
                    )
                    db.userDao().insert(parent)
                    serverParentUser = parent
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local creation below
            }

            if (serverParentUser != null) {
                prefs.edit().putString("saved_user_id", serverParentUser.userId).apply()
                _currentUser.value = serverParentUser
                return@withContext Result.success(serverParentUser)
            }

            // 2. Offline / Local Fallback
            val existing = db.userDao().getUserByEmail(normalizedEmail)
            if (existing != null) {
                return@withContext Result.failure(Exception("An account with email '$normalizedEmail' already exists. Please sign in."))
            }

            val group = FamilyGroupEntity(
                groupId = "group-${UUID.randomUUID().toString().take(8)}",
                familyName = if (familyName.isNotBlank()) familyName.trim() else "${name.trim()}'s Family"
            )
            db.familyGroupDao().insert(group)

            val parentUser = UserEntity(
                userId = "parent-${UUID.randomUUID().toString().take(8)}",
                groupId = group.groupId,
                email = normalizedEmail,
                password = password,
                role = "parent",
                name = name.trim(),
                pin = if (pin.isNotBlank()) pin.trim() else "1234"
            )
            db.userDao().insert(parentUser)

            val parentDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = parentUser.userId,
                deviceName = "${name.trim()}'s Parent Phone",
                batteryPercent = 100,
                isOnline = true
            )
            db.deviceDao().insert(parentDevice)

            // Persist session
            prefs.edit().putString("saved_user_id", parentUser.userId).apply()
            _currentUser.value = parentUser
            Result.success(parentUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInParent(email: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()

            // 1. Attempt Server Login (Render / Supabase)
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.loginParent(
                    com.example.data.remote.LoginParentDto(
                        email = normalizedEmail,
                        password = password
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val group = FamilyGroupEntity(
                        groupId = body.user.group_id,
                        familyName = body.family_name.ifBlank { "My Family" }
                    )
                    db.familyGroupDao().insert(group)

                    val parent = UserEntity(
                        userId = body.user.user_id,
                        groupId = body.user.group_id,
                        email = body.user.email,
                        password = password,
                        role = body.user.role,
                        name = body.user.name,
                        pin = body.user.pin,
                        avatar = body.user.avatar
                    )
                    db.userDao().insert(parent)

                    // Insert synced children
                    val children = body.children.map { c ->
                        UserEntity(
                            userId = c.user_id,
                            groupId = c.group_id,
                            email = c.email,
                            password = password,
                            role = c.role,
                            name = c.name,
                            pin = c.pin,
                            avatar = c.avatar
                        )
                    }
                    if (children.isNotEmpty()) {
                        db.userDao().insertAll(children)
                        _selectedChildId.value = children.first().userId
                    }

                    prefs.edit().putString("saved_user_id", parent.userId).apply()
                    _currentUser.value = parent
                    return@withContext Result.success(parent)
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local check
            }

            // 2. Offline / Local fallback check
            val user = db.userDao().getUserByEmail(normalizedEmail)
            if (user == null) {
                return@withContext Result.failure(Exception("No account found for '$normalizedEmail'. Please verify credentials or create an account."))
            }
            if (user.password != password) {
                return@withContext Result.failure(Exception("Incorrect password. Please verify and try again."))
            }

            prefs.edit().putString("saved_user_id", user.userId).apply()
            _currentUser.value = user
            if (user.role == "child") {
                _selectedChildId.value = user.userId
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairChildDevice(
        parentEmail: String,
        parentPassword: String,
        childName: String,
        deviceName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedParentEmail = parentEmail.trim().lowercase()

            // 1. Attempt Server Device Pairing (Render / Supabase)
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.pairChildDevice(
                    com.example.data.remote.PairChildDto(
                        parent_email = normalizedParentEmail,
                        parent_password = parentPassword,
                        child_name = childName.trim(),
                        device_name = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone"
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val childUser = UserEntity(
                        userId = body.child_user.user_id,
                        groupId = body.group_id,
                        email = body.child_user.email,
                        password = parentPassword,
                        role = "child",
                        name = body.child_user.name,
                        pin = ""
                    )
                    db.userDao().insert(childUser)

                    val childDevice = DeviceEntity(
                        deviceId = body.device_id,
                        userId = childUser.userId,
                        deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone",
                        batteryPercent = 95,
                        isOnline = true
                    )
                    db.deviceDao().insert(childDevice)

                    prefs.edit().putString("saved_user_id", childUser.userId).apply()
                    _currentUser.value = childUser
                    _selectedChildId.value = childUser.userId
                    return@withContext Result.success(childUser)
                }
            } catch (e: Exception) {
                // Server unavailable or offline -> fallback to local creation
            }

            // 2. Offline Local Creation Fallback
            var parentUser = db.userDao().getUserByEmail(normalizedParentEmail)
            val groupId = if (parentUser != null) {
                if (parentUser.password != parentPassword) {
                    return@withContext Result.failure(Exception("Parent password incorrect. Cannot link device."))
                }
                parentUser.groupId
            } else {
                val newGroupId = "group-${UUID.randomUUID().toString().take(8)}"
                val newGroup = FamilyGroupEntity(
                    groupId = newGroupId,
                    familyName = "${childName.trim()}'s Family"
                )
                db.familyGroupDao().insert(newGroup)

                val newParent = UserEntity(
                    userId = "parent-${UUID.randomUUID().toString().take(8)}",
                    groupId = newGroupId,
                    email = normalizedParentEmail,
                    password = parentPassword,
                    role = "parent",
                    name = "Parent ($normalizedParentEmail)",
                    pin = "1234"
                )
                db.userDao().insert(newParent)
                newGroupId
            }

            val childId = "child-${UUID.randomUUID().toString().take(8)}"
            val childUser = UserEntity(
                userId = childId,
                groupId = groupId,
                email = "${childName.trim().lowercase().replace(" ", "")}@family.focussense",
                password = parentPassword,
                role = "child",
                name = childName.trim(),
                pin = ""
            )
            db.userDao().insert(childUser)

            val childDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = childId,
                deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Device",
                batteryPercent = 95,
                isOnline = true
            )
            db.deviceDao().insert(childDevice)

            // Persist session as child device
            prefs.edit().putString("saved_user_id", childId).apply()
            _currentUser.value = childUser
            _selectedChildId.value = childId

            Result.success(childUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        prefs.edit().remove("saved_user_id").apply()
        _currentUser.value = null
        _selectedChildId.value = ""
    }

    // Role & User Switching
    fun switchUser(user: UserEntity) {
        _currentUser.value = user
    }

    fun setSelectedChild(childId: String) {
        _selectedChildId.value = childId
    }

    fun toggleNetworkSimulation(connected: Boolean) {
        _isNetworkConnected.value = connected
    }

    // Database Flows
    val familyGroup: Flow<FamilyGroupEntity?> = db.familyGroupDao().getFamilyGroup()
    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    val childrenUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("child")
    val parentUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("parent")
    val allDevices: Flow<List<DeviceEntity>> = db.deviceDao().getAllDevices()

    fun getDevicesForUser(userId: String): Flow<List<DeviceEntity>> =
        db.deviceDao().getDevicesForUser(userId)

    val allLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getAllLogs()
    val flaggedLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getFlaggedLogs()

    fun getLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getLogsForChild(childId)

    fun getFlaggedLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getFlaggedLogsForChild(childId)

    val allScheduleRules: Flow<List<ScheduleRuleEntity>> = db.scheduleRuleDao().getAllRules()

    fun getRulesForChild(childId: String): Flow<List<ScheduleRuleEntity>> =
        db.scheduleRuleDao().getRulesForChild(childId)

    fun getLatestLocationForChild(childId: String): Flow<LocationPointEntity?> =
        db.locationDao().getLatestLocationForChild(childId)

    fun getLocationHistoryForChild(childId: String): Flow<List<LocationPointEntity>> =
        db.locationDao().getLocationHistoryForChild(childId)

    val unsyncedLogsCount: Flow<Int> = db.activityLogDao().getUnsyncedCount()
    val unsyncedLocationCount: Flow<Int> = db.locationDao().getUnsyncedCount()

    // 1. Accessibility Content Processing & AI Evaluation
    suspend fun processExtractedContent(
        childId: String,
        packageName: String,
        appName: String,
        contentTitle: String,
        extractedText: String,
        forceOffline: Boolean = false
    ): ActivityLogEntity = withContext(Dispatchers.IO) {
        val analysis = threatEngine.evaluateContent(
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            serverUrl = _serverUrl.value,
            forceOfflineOnly = forceOffline || !_isNetworkConnected.value
        )

        val log = ActivityLogEntity(
            logId = UUID.randomUUID().toString(),
            childId = childId,
            packageName = packageName,
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            isFlagged = analysis.isFlagged,
            threatCategory = analysis.threatCategory,
            confidenceScore = analysis.confidenceScore,
            aiAnalysisSummary = analysis.aiAnalysisSummary,
            recordedAt = System.currentTimeMillis(),
            isSynced = _isNetworkConnected.value, // Auto-synced if online, queued if offline
            isAcknowledged = false
        )

        // Zero Data-Loss Guarantee: Insert into local Room SQLite DB immediately
        db.activityLogDao().insert(log)

        if (analysis.isFlagged) {
            _eventStream.tryEmit(SentinelEvent.ContentFlagged(log, analysis))

            // Instantly block process for EVERY flagged harmful content class
            toggleAppBlock(childId, packageName, true)
        }

        // Live Real-Time Server Sync: push directly to Supabase if connected
        if (_isNetworkConnected.value) {
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                api.syncData(
                    com.example.data.remote.SyncRequest(
                        child_id = childId.ifBlank { "child-default" },
                        logs = listOf(log),
                        locations = emptyList()
                    )
                )
            } catch (e: Exception) {
                // Stored locally in SQLite, will sync on next cycle
            }
        }

        log
    }

    // 2. Scheduled & Instant App Blocking Engine
    suspend fun checkAppRestriction(childId: String, packageName: String): ActiveAppBlockInfo = withContext(Dispatchers.IO) {
        val lowerPkg = packageName.lowercase()

        // 1. Temporary PIN Override Check
        if (isPackageTemporarilyUnlocked(lowerPkg)) {
            return@withContext ActiveAppBlockInfo(isBlocked = false)
        }

        // 2. Instant Parent Block List Check
        val isInstantBlocked = _instantBlockedPackages.value.any { it.equals(lowerPkg, ignoreCase = true) }
        if (isInstantBlocked) {
            return@withContext ActiveAppBlockInfo(
                isBlocked = true,
                ruleName = "Parent Instant Lock",
                category = "Instant Restriction",
                endTime = "Until Unlocked by Parent",
                restrictedAppName = packageName
            )
        }

        // 2b. Pomodoro Child Focus Lock Check
        val pomodoro = _pomodoroState.value
        if (pomodoro != null && pomodoro.isRunning && pomodoro.remainingSeconds > 0) {
            val isPomodoroTarget = pomodoro.packageName == "*" ||
                    pomodoro.packageName == "all" ||
                    pomodoro.packageName.equals(lowerPkg, ignoreCase = true) ||
                    (pomodoro.packageName.isNotBlank() && lowerPkg.contains(pomodoro.packageName, ignoreCase = true))

            if (isPomodoroTarget) {
                val minsLeft = (pomodoro.remainingSeconds / 60) + 1
                return@withContext ActiveAppBlockInfo(
                    isBlocked = true,
                    ruleName = "Pomodoro Study Lock",
                    category = "Child Focus Mode",
                    endTime = "$minsLeft min remaining",
                    restrictedAppName = pomodoro.appName
                )
            }
        }

        // 3. Scheduled Curfew Rules Check
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { _currentUser.value?.userId ?: "child-default" } }
        val childRules = if (effectiveChildId.isNotBlank()) {
            db.scheduleRuleDao().getRulesForChildSync(effectiveChildId)
        } else {
            emptyList()
        }
        val allActive = db.scheduleRuleDao().getAllActiveRulesSync()
        val rules = (childRules + allActive).distinctBy { it.ruleId }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Day of week check (e.g. Mon, Tue)
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val currentDayName = dayNames[now.get(Calendar.DAY_OF_WEEK) - 1]

        for (rule in rules) {
            if (!rule.isActive) continue

            // Check days (allows "Daily", "All", "Everyday", empty, or comma-separated days)
            val daySpec = rule.dayOfWeek.trim()
            val dayMatches = daySpec.isBlank() ||
                    daySpec.contains("All", ignoreCase = true) ||
                    daySpec.contains("Daily", ignoreCase = true) ||
                    daySpec.contains("Everyday", ignoreCase = true) ||
                    daySpec.contains(currentDayName, ignoreCase = true)

            if (!dayMatches) continue

            // Parse start and end time
            val startParts = rule.startTime.split(":")
            val endParts = rule.endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            val isTimeMatch = when {
                // If explicitly configured for 24/7 (00:00 to 23:59)
                startMinutes == 0 && endMinutes >= 1439 -> true
                // Equal non-full-day start and end time means zero duration
                startMinutes == endMinutes -> false
                startMinutes < endMinutes -> currentTimeMinutes >= startMinutes && currentTimeMinutes < endMinutes
                startMinutes > endMinutes -> currentTimeMinutes >= startMinutes || currentTimeMinutes < endMinutes
                else -> false
            }

            if (isTimeMatch) {
                val packages = rule.restrictedPackages.split(",").map { it.trim().lowercase() }
                val matchesApp = packages.any { pkgPattern ->
                    pkgPattern == "*" ||
                    pkgPattern == "all" ||
                    pkgPattern == lowerPkg ||
                    (pkgPattern.isNotBlank() && lowerPkg.contains(pkgPattern))
                }

                if (matchesApp) {
                    val displayAppName = rule.ruleName.substringAfter("Parent App Lock: ").takeIf { it.isNotBlank() } ?: packageName
                    return@withContext ActiveAppBlockInfo(
                        isBlocked = true,
                        ruleName = rule.ruleName,
                        category = rule.category,
                        endTime = rule.endTime,
                        restrictedAppName = displayAppName
                    )
                }
            }
        }

        ActiveAppBlockInfo(isBlocked = false)
    }

    // 3. Location Logging
    suspend fun recordLocation(
        childId: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        locationName: String
    ): Unit = withContext(Dispatchers.IO) {
        val point = LocationPointEntity(
            locId = UUID.randomUUID().toString(),
            childId = childId,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            locationName = locationName,
            recordedAt = System.currentTimeMillis(),
            isSynced = _isNetworkConnected.value
        )
        db.locationDao().insert(point)
        _eventStream.tryEmit(SentinelEvent.LocationUpdated(locationName, lat, lng))

        if (_isNetworkConnected.value) {
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                api.reportLocation(point)
            } catch (_: Exception) {}
        }
    }

    // 4. Zero Data-Loss Guarantee: Sync queued offline data to Central Python REST / Supabase PostgreSQL
    suspend fun syncOfflineQueue(): Int = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        var totalSynced = 0
        try {
            val childId = _selectedChildId.value.ifBlank {
                _currentUser.value?.userId ?: "child-default"
            }
            val unsyncedLogs = db.activityLogDao().getUnsyncedLogs()
            val unsyncedLocations = db.locationDao().getUnsyncedLocations()

            // If no pending unsynced records, sync recent logs and locations to guarantee Supabase has all data
            val logsToSync = if (unsyncedLogs.isNotEmpty()) unsyncedLogs else db.activityLogDao().getAllLogsSync().take(30)
            val locationsToSync = if (unsyncedLocations.isNotEmpty()) unsyncedLocations else db.locationDao().getAllLocationsSync().take(30)

            if (logsToSync.isNotEmpty() || locationsToSync.isNotEmpty()) {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val response = api.syncData(
                    com.example.data.remote.SyncRequest(
                        child_id = childId,
                        logs = logsToSync,
                        locations = locationsToSync
                    )
                )
                if (response.isSuccessful) {
                    db.activityLogDao().markAllSynced()
                    db.locationDao().markAllSynced()
                    totalSynced = logsToSync.size + locationsToSync.size
                    Log.i("FocusSenseRepo", "Successfully synced $totalSynced records to Supabase.")
                } else {
                    Log.e("FocusSenseRepo", "Server sync returned HTTP ${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.e("FocusSenseRepo", "Sync to server failed: ${e.message}")
        } finally {
            _isSyncing.value = false
            _eventStream.tryEmit(SentinelEvent.SyncCompleted(totalSynced))
        }
        totalSynced
    }

    // 5. Parent Administrative Actions & App Management
    suspend fun acknowledgeAlert(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().markAcknowledged(logId)
    }

    suspend fun removeVulnerableLog(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().deleteLog(logId)
    }

    suspend fun addScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().insert(rule)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.saveSchedule(rule)
        } catch (_: Exception) {}
    }

    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().update(rule)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.saveSchedule(rule)
        } catch (_: Exception) {}
    }

    suspend fun deleteScheduleRule(ruleId: String) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().delete(ruleId)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.deleteSchedule(ruleId)
        } catch (_: Exception) {}
    }

    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().toggleRuleActive(ruleId, isActive)
        val rule = db.scheduleRuleDao().getAllActiveRulesSync().find { it.ruleId == ruleId }
        if (rule != null) {
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                api.saveSchedule(rule.copy(isActive = isActive))
            } catch (_: Exception) {}
        }
    }

    suspend fun syncSchedulesWithServer(childId: String) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { "child-default" } }
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            val resp = api.getSchedules(effectiveChildId)
            if (resp.isSuccessful && resp.body() != null) {
                db.scheduleRuleDao().insertAll(resp.body()!!)
            }
        } catch (_: Exception) {}
    }

    // Installed Applications & Instant App Locker
    fun getInstalledApps(childId: String): Flow<List<InstalledAppEntity>> {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value }
        return if (effectiveChildId.isNotBlank()) {
            db.installedAppDao().getInstalledApps(effectiveChildId)
        } else {
            db.installedAppDao().getAllInstalledApps()
        }
    }

    suspend fun scanAndSyncInstalledApps(context: Context, childId: String) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { "child-default" } }
        val role = _currentUser.value?.role ?: "parent"

        if (role == "child") {
            // Child Phone: Scan local hardware packages and push to server
            val scannedApps = InstalledAppScanner.getInstalledLauncherApps(context, effectiveChildId)
            val existingBlocked = db.installedAppDao().getBlockedPackageNamesSync().toSet()
            val mergedApps = scannedApps.map { app ->
                if (existingBlocked.contains(app.packageName)) app.copy(isBlocked = true) else app
            }

            db.installedAppDao().insertAll(mergedApps)

            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                api.syncInstalledApps(effectiveChildId, mergedApps)
            } catch (_: Exception) {}
        } else {
            // Parent Phone: Fetch the child's real installed apps from server
            var remoteFound = false
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val remoteResp = api.getInstalledApps(effectiveChildId)
                if (remoteResp.isSuccessful && !remoteResp.body().isNullOrEmpty()) {
                    db.installedAppDao().insertAll(remoteResp.body()!!)
                    val remoteBlocked = remoteResp.body()!!.filter { it.isBlocked }.map { it.packageName.lowercase() }.toSet()
                    _instantBlockedPackages.value = _instantBlockedPackages.value + remoteBlocked
                    remoteFound = true
                }
            } catch (_: Exception) {}

            // If running in single-device test mode or no server apps yet, scan device to provide real installed apps
            if (!remoteFound) {
                val localAppsCount = db.installedAppDao().getInstalledAppsSync(effectiveChildId).size
                if (localAppsCount == 0) {
                    val scannedApps = InstalledAppScanner.getInstalledLauncherApps(context, effectiveChildId)
                    if (scannedApps.isNotEmpty()) {
                        db.installedAppDao().insertAll(scannedApps)
                        try {
                            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                            api.syncInstalledApps(effectiveChildId, scannedApps)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    suspend fun toggleAppBlock(childId: String, packageName: String, isBlocked: Boolean) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { "child-default" } }
        val lowerPkg = packageName.lowercase()

        // 1. Update Room DB
        db.installedAppDao().updateAppBlockStatus(packageName, isBlocked)
        db.installedAppDao().updateAppBlockStatusForChild(effectiveChildId, packageName, isBlocked)

        // 2. Update In-Memory Instant Block Cache
        _instantBlockedPackages.value = if (isBlocked) {
            _instantBlockedPackages.value + lowerPkg
        } else {
            _instantBlockedPackages.value - lowerPkg
        }

        // 3. Create or Remove Instant Rule in schedule_rules
        val instantRuleId = "instant-block-${lowerPkg.replace('.', '-')}"
        val appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        val rule = ScheduleRuleEntity(
            ruleId = instantRuleId,
            childId = effectiveChildId,
            ruleName = "Parent App Lock: $appName",
            category = "Instant Block",
            startTime = "00:00",
            endTime = "23:59",
            dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
            restrictedPackages = packageName,
            isActive = isBlocked
        )

        if (isBlocked) {
            db.scheduleRuleDao().insert(rule)
        } else {
            db.scheduleRuleDao().delete(instantRuleId)
        }

        // 4. Push to Server
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.toggleAppBlock(
                childId = effectiveChildId,
                payload = com.example.data.remote.AppBlockToggleDto(package_name = packageName, is_blocked = isBlocked)
            )
            if (isBlocked) {
                api.saveSchedule(rule)
            } else {
                api.deleteSchedule(instantRuleId)
            }
        } catch (_: Exception) {}
    }

    suspend fun updateParentPin(userId: String, newPin: String) = withContext(Dispatchers.IO) {
        db.userDao().updatePin(userId, newPin)
    }
}
