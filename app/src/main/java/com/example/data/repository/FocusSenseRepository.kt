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
    private val securePrefs = com.example.data.local.SecurePreferences(context)
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

    // Central Server & Database URL (Django + Supabase PostgreSQL)
    private val defaultBackendUrl = "https://ais-dev-oopvudz6wyelc5jkmrnerc-562689220446.asia-east1.run.app"
    private val _serverUrl = MutableStateFlow<String>(
        prefs.getString("server_api_url", defaultBackendUrl) ?: defaultBackendUrl
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
        // Direct ping to Supabase REST API
        com.example.data.remote.SupabaseClient.ping()
    }

    fun getActiveDeviceId(): String {
        val saved = prefs.getString("saved_device_id", null)
        if (!saved.isNullOrBlank()) return saved
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("saved_device_id", newId).apply()
        return newId
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

            // 1. Direct register to Supabase PostgreSQL (mukkrkppvfxnbbxozvwj)
            val supabaseResult = com.example.data.remote.SupabaseClient.registerParent(
                name = name,
                familyName = familyName,
                email = normalizedEmail,
                passwordHash = password
            )

            if (supabaseResult.isSuccess) {
                val record = supabaseResult.getOrThrow()
                val group = FamilyGroupEntity(
                    groupId = record.familyId ?: UUID.randomUUID().toString(),
                    familyName = if (familyName.isNotBlank()) familyName.trim() else "${name.trim()}'s Family"
                )
                db.familyGroupDao().insert(group)

                val parent = UserEntity(
                    userId = record.id,
                    groupId = group.groupId,
                    email = record.email,
                    password = password,
                    role = "parent",
                    name = record.displayName,
                    pin = pin
                )
                db.userDao().insert(parent)

                val parentDevice = DeviceEntity(
                    deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                    userId = parent.userId,
                    deviceName = "${name.trim()}'s Parent Phone",
                    batteryPercent = 100,
                    isOnline = true
                )
                db.deviceDao().insert(parentDevice)

                prefs.edit().putString("saved_user_id", parent.userId).apply()
                _currentUser.value = parent
                return@withContext Result.success(parent)
            } else {
                val err = supabaseResult.exceptionOrNull()
                Log.e("FocusSenseRepo", "Supabase register failed: ${err?.message}", err)
                return@withContext Result.failure(err ?: Exception("Registration failed on Supabase."))
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

            // 1. Attempt login with Supabase
            val supabaseResult = com.example.data.remote.SupabaseClient.loginParent(
                email = normalizedEmail,
                passwordExpected = password
            )
            if (supabaseResult.isSuccess) {
                val record = supabaseResult.getOrThrow()
                val familyId = record.familyId ?: UUID.randomUUID().toString()
                val group = FamilyGroupEntity(
                    groupId = familyId,
                    familyName = "My Family"
                )
                db.familyGroupDao().insert(group)

                val parent = UserEntity(
                    userId = record.id,
                    groupId = familyId,
                    email = record.email,
                    password = password,
                    role = "parent",
                    name = record.displayName,
                    pin = "1234"
                )
                db.userDao().insert(parent)

                // Sync all children of this family from Supabase
                val children = com.example.data.remote.SupabaseClient.fetchFamilyChildren(familyId)
                for (c in children) {
                    val childEntity = UserEntity(
                        userId = c.id,
                        groupId = familyId,
                        email = c.email,
                        password = password,
                        role = "child",
                        name = c.displayName,
                        pin = ""
                    )
                    db.userDao().insert(childEntity)
                }
                if (children.isNotEmpty()) {
                    _selectedChildId.value = children.first().id
                }

                prefs.edit().putString("saved_user_id", parent.userId).apply()
                _currentUser.value = parent
                return@withContext Result.success(parent)
            }

            // 2. Offline / Local fallback check
            val user = db.userDao().getUserByEmail(normalizedEmail)
            if (user == null) {
                val err = supabaseResult.exceptionOrNull()
                return@withContext Result.failure(err ?: Exception("No account found for '$normalizedEmail'. Please verify credentials or create an account."))
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

    suspend fun createChildAccount(
        username: String,
        password: String,
        displayName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val parent = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in as parent."))
            val cleanUsername = username.trim().lowercase()

            // 1. Direct creation in Supabase fs_users
            val supabaseResult = com.example.data.remote.SupabaseClient.createChildAccount(
                username = cleanUsername,
                displayName = displayName,
                familyId = parent.groupId,
                parentId = parent.userId,
                passwordHash = password
            )

            val childId = if (supabaseResult.isSuccess) {
                supabaseResult.getOrThrow().id
            } else {
                Log.w("FocusSenseRepo", "Supabase child creation failed: ${supabaseResult.exceptionOrNull()?.message}")
                UUID.randomUUID().toString()
            }

            val newChild = UserEntity(
                userId = childId,
                groupId = parent.groupId,
                email = "$cleanUsername@focussense.local",
                password = password,
                role = "child",
                name = displayName.trim(),
                pin = ""
            )
            db.userDao().insert(newChild)
            if (_selectedChildId.value.isBlank()) {
                _selectedChildId.value = childId
            }
            Result.success(newChild)
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

            // 1. Direct Supabase Device Pairing
            val supabasePairResult = com.example.data.remote.SupabaseClient.pairChildDevice(
                parentEmail = normalizedParentEmail,
                parentPasswordExpected = parentPassword,
                childName = childName,
                deviceName = deviceName
            )

            if (supabasePairResult.isSuccess) {
                val (childRecord, deviceId) = supabasePairResult.getOrThrow()
                val familyId = childRecord.familyId ?: UUID.randomUUID().toString()

                val group = FamilyGroupEntity(
                    groupId = familyId,
                    familyName = "${childName.trim()}'s Family"
                )
                db.familyGroupDao().insert(group)

                val childUser = UserEntity(
                    userId = childRecord.id,
                    groupId = familyId,
                    email = childRecord.email,
                    password = parentPassword,
                    role = "child",
                    name = childRecord.displayName,
                    pin = ""
                )
                db.userDao().insert(childUser)

                val childDevice = DeviceEntity(
                    deviceId = deviceId,
                    userId = childUser.userId,
                    deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone",
                    batteryPercent = 95,
                    isOnline = true
                )
                db.deviceDao().insert(childDevice)

                prefs.edit()
                    .putString("saved_user_id", childUser.userId)
                    .putString("saved_device_id", deviceId)
                    .apply()
                _currentUser.value = childUser
                _selectedChildId.value = childUser.userId
                return@withContext Result.success(childUser)
            } else {
                Log.w("FocusSenseRepo", "Supabase pair failed: ${supabasePairResult.exceptionOrNull()?.message}")
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
            val devId = getActiveDeviceId()
            val effectiveChildId = childId.ifBlank { _currentUser.value?.userId ?: "" }
            com.example.data.remote.SupabaseClient.pushActivityLog(log, effectiveChildId, devId)
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
            val devId = getActiveDeviceId()
            val effectiveChildId = childId.ifBlank { _currentUser.value?.userId ?: "" }
            com.example.data.remote.SupabaseClient.pushLocation(point, effectiveChildId, devId)
        }
    }

    // 4. Zero Data-Loss Guarantee: Sync queued offline data to Central Python REST / Supabase PostgreSQL
    suspend fun syncOfflineQueue(): Int = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        var totalSynced = 0
        try {
            val childId = _selectedChildId.value.ifBlank {
                _currentUser.value?.userId ?: ""
            }
            val devId = getActiveDeviceId()
            val unsyncedLogs = db.activityLogDao().getUnsyncedLogs()
            val unsyncedLocations = db.locationDao().getUnsyncedLocations()

            // 1. Direct Supabase Cloud Sync
            var supabaseLogsCount = 0
            for (l in unsyncedLogs) {
                val ok = com.example.data.remote.SupabaseClient.pushActivityLog(l, childId, devId)
                if (ok) supabaseLogsCount++
            }
            var supabaseLocCount = 0
            for (p in unsyncedLocations) {
                val ok = com.example.data.remote.SupabaseClient.pushLocation(p, childId, devId)
                if (ok) supabaseLocCount++
            }
            if (supabaseLogsCount > 0 || supabaseLocCount > 0) {
                db.activityLogDao().markAllSynced()
                db.locationDao().markAllSynced()
                totalSynced = supabaseLogsCount + supabaseLocCount
                Log.i("FocusSenseRepo", "Direct Supabase Sync uploaded $totalSynced records.")
            }

            // 2. Also try legacy ApiClient endpoint if configured
            val logsToSync = if (unsyncedLogs.isNotEmpty()) unsyncedLogs else db.activityLogDao().getAllLogsSync().take(30)
            val locationsToSync = if (unsyncedLocations.isNotEmpty()) unsyncedLocations else db.locationDao().getAllLocationsSync().take(30)

            if (logsToSync.isNotEmpty() || locationsToSync.isNotEmpty()) {
                try {
                    val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                    val response = api.syncData(
                        com.example.data.remote.SyncRequest(
                            child_id = childId.ifBlank { "child-default" },
                            logs = logsToSync,
                            locations = locationsToSync
                        )
                    )
                    if (response.isSuccessful) {
                        db.activityLogDao().markAllSynced()
                        db.locationDao().markAllSynced()
                        totalSynced = maxOf(totalSynced, logsToSync.size + locationsToSync.size)
                    }
                } catch (_: Exception) {}
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
        val childId = rule.childId.ifBlank { _selectedChildId.value.ifBlank { _currentUser.value?.userId ?: "" } }
        com.example.data.remote.SupabaseClient.pushScheduleRule(rule, childId)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.saveSchedule(rule)
        } catch (_: Exception) {}
    }

    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().update(rule)
        val childId = rule.childId.ifBlank { _selectedChildId.value.ifBlank { _currentUser.value?.userId ?: "" } }
        com.example.data.remote.SupabaseClient.pushScheduleRule(rule, childId)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.saveSchedule(rule)
        } catch (_: Exception) {}
    }

    suspend fun deleteScheduleRule(ruleId: String) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().delete(ruleId)
        com.example.data.remote.SupabaseClient.deleteScheduleRule(ruleId)
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            api.deleteSchedule(ruleId)
        } catch (_: Exception) {}
    }

    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().toggleRuleActive(ruleId, isActive)
        com.example.data.remote.SupabaseClient.toggleScheduleRule(ruleId, isActive)
        val rule = db.scheduleRuleDao().getAllActiveRulesSync().find { it.ruleId == ruleId }
        if (rule != null) {
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                api.saveSchedule(rule.copy(isActive = isActive))
            } catch (_: Exception) {}
        }
    }

    suspend fun syncSchedulesWithServer(childId: String) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { _currentUser.value?.userId ?: "" } }
        if (effectiveChildId.isNotBlank()) {
            val supabaseRules = com.example.data.remote.SupabaseClient.fetchScheduleRules(effectiveChildId)
            if (supabaseRules.isNotEmpty()) {
                db.scheduleRuleDao().insertAll(supabaseRules)
            }
        }
        try {
            val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
            val resp = api.getSchedules(effectiveChildId.ifBlank { "child-default" })
            if (resp.isSuccessful && resp.body() != null) {
                db.scheduleRuleDao().insertAll(resp.body()!!)
            }
        } catch (_: Exception) {}
    }

    suspend fun refreshChildFromCloud(childId: String) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value }
        if (effectiveChildId.isBlank()) return@withContext

        // 1. Fetch latest location from Supabase
        val locObj = com.example.data.remote.SupabaseClient.fetchLatestLocation(effectiveChildId)
        if (locObj != null) {
            val point = LocationPointEntity(
                locId = locObj.optString("id", UUID.randomUUID().toString()),
                childId = effectiveChildId,
                latitude = locObj.optDouble("latitude", 0.0),
                longitude = locObj.optDouble("longitude", 0.0),
                accuracy = locObj.optDouble("accuracy", 5.0).toFloat(),
                locationName = "GPS Fix (${String.format("%.4f", locObj.optDouble("latitude", 0.0))}, ${String.format("%.4f", locObj.optDouble("longitude", 0.0))})",
                recordedAt = System.currentTimeMillis(),
                isSynced = true
            )
            db.locationDao().insert(point)
        }

        // 2. Fetch latest activity logs from Supabase
        val logsArr = com.example.data.remote.SupabaseClient.fetchChildLogs(effectiveChildId)
        for (obj in logsArr) {
            val log = ActivityLogEntity(
                logId = obj.optString("id", UUID.randomUUID().toString()),
                childId = effectiveChildId,
                packageName = obj.optString("package_name", ""),
                appName = obj.optString("app_name", ""),
                contentTitle = obj.optString("content_title", ""),
                extractedText = "",
                isFlagged = obj.optBoolean("is_flagged", false),
                threatCategory = obj.optString("threat_category", "Safe"),
                confidenceScore = obj.optDouble("confidence_score", 0.0).toFloat(),
                aiAnalysisSummary = obj.optString("ai_analysis_summary", ""),
                recordedAt = System.currentTimeMillis(),
                isSynced = true,
                isAcknowledged = false
            )
            db.activityLogDao().insert(log)
        }

        // 3. Fetch latest schedule rules from Supabase
        val rules = com.example.data.remote.SupabaseClient.fetchScheduleRules(effectiveChildId)
        if (rules.isNotEmpty()) {
            db.scheduleRuleDao().insertAll(rules)
        }
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
            try {
                val api = com.example.data.remote.ApiClient.getService(_serverUrl.value)
                val remoteResp = api.getInstalledApps(effectiveChildId)
                if (remoteResp.isSuccessful && !remoteResp.body().isNullOrEmpty()) {
                    db.installedAppDao().insertAll(remoteResp.body()!!)
                    val remoteBlocked = remoteResp.body()!!.filter { it.isBlocked }.map { it.packageName.lowercase() }.toSet()
                    _instantBlockedPackages.value = _instantBlockedPackages.value + remoteBlocked
                }
            } catch (_: Exception) {
                Log.e("FocusSenseRepo", "Parent failed to fetch remote app inventory for $effectiveChildId")
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

    fun getAllowedYouTubeChannels(childId: String): Flow<List<com.example.data.model.AllowedYouTubeChannelEntity>> {
        return db.youtubeDao().getAllChannelsForChild(childId)
    }

    fun getWhitelistedYouTubeChannels(childId: String): Flow<List<com.example.data.model.AllowedYouTubeChannelEntity>> {
        return db.youtubeDao().getWhitelistedChannels(childId)
    }

    suspend fun toggleYouTubeChannelAllowed(channelId: String, isAllowed: Boolean) = withContext(Dispatchers.IO) {
        db.youtubeDao().updateAllowedStatus(channelId, isAllowed)
    }

    suspend fun addYouTubeChannel(channel: com.example.data.model.AllowedYouTubeChannelEntity) = withContext(Dispatchers.IO) {
        db.youtubeDao().insert(channel)
    }

    suspend fun removeYouTubeChannel(channelId: String) = withContext(Dispatchers.IO) {
        db.youtubeDao().removeChannel(channelId)
    }
}
