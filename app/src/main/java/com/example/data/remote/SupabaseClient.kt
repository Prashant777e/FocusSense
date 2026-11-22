package com.example.data.remote

import android.util.Log
import com.example.data.model.ActivityLogEntity
import com.example.data.model.LocationPointEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SupabaseUserRecord(
    val id: String,
    val email: String,
    val username: String,
    val role: String,
    val displayName: String,
    val familyId: String?
)

object SupabaseClient {
    const val SUPABASE_URL = "https://mukkrkppvfxnbbxozvwj.supabase.co"
    const val API_KEY = "sb_publishable_qcE48d96L0OVpVVV6rQsMw_EKLpPBIi"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun newAuthRequest(endpoint: String): Request.Builder {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$SUPABASE_URL/rest/v1/$endpoint"
        return Request.Builder()
            .url(fullUrl)
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
    }

    /**
     * Test connection to Supabase REST API
     */
    fun ping(): Pair<Boolean, String> {
        return try {
            val req = newAuthRequest("fs_users?select=id&limit=1")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                Pair(true, "Connected to Supabase PostgreSQL (mukkrkppvfxnbbxozvwj)")
            } else {
                Pair(false, "Supabase HTTP ${resp.code}: ${resp.message}")
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Ping error: ${e.message}", e)
            Pair(false, e.message ?: "Network error connecting to Supabase")
        }
    }

    /**
     * Register a new Parent in Supabase
     */
    fun registerParent(
        name: String,
        familyName: String,
        email: String,
        passwordHash: String
    ): Result<SupabaseUserRecord> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val cleanName = name.trim()
            val cleanFamilyName = if (familyName.isNotBlank()) familyName.trim() else "$cleanName's Family"
            val now = isoNow()

            // Check if user already exists
            val checkReq = newAuthRequest("fs_users?email=eq.$cleanEmail&select=id,email")
                .get()
                .build()
            val checkResp = httpClient.newCall(checkReq).execute()
            if (checkResp.isSuccessful) {
                val existing = JSONArray(checkResp.body?.string() ?: "[]")
                if (existing.length() > 0) {
                    return Result.failure(Exception("An account with email '$cleanEmail' already exists. Please sign in."))
                }
            }

            // 1. Create Family Group
            val familyId = UUID.randomUUID().toString()
            val inviteCode = UUID.randomUUID().toString().take(8).uppercase()
            val groupJson = JSONObject().apply {
                put("id", familyId)
                put("family_name", cleanFamilyName)
                put("invite_code", inviteCode)
                put("created_at", now)
            }

            val groupReq = newAuthRequest("fs_family_groups")
                .addHeader("Prefer", "return=representation")
                .post(groupJson.toString().toRequestBody(jsonMediaType))
                .build()
            val groupResp = httpClient.newCall(groupReq).execute()
            if (!groupResp.isSuccessful) {
                val err = groupResp.body?.string() ?: ""
                Log.e("SupabaseClient", "Failed to create family group: $err")
                return Result.failure(Exception("Supabase family creation failed: $err"))
            }

            // 2. Create Parent User in fs_users
            val userId = UUID.randomUUID().toString()
            val userJson = JSONObject().apply {
                put("id", userId)
                put("email", cleanEmail)
                put("username", cleanEmail)
                put("password", passwordHash)
                put("role", "PARENT")
                put("display_name", cleanName)
                put("family_id", familyId)
                put("is_active", true)
                put("is_staff", false)
                put("is_superuser", false)
                put("date_joined", now)
            }

            val userReq = newAuthRequest("fs_users")
                .addHeader("Prefer", "return=representation")
                .post(userJson.toString().toRequestBody(jsonMediaType))
                .build()
            val userResp = httpClient.newCall(userReq).execute()
            if (!userResp.isSuccessful) {
                val err = userResp.body?.string() ?: ""
                Log.e("SupabaseClient", "Failed to create user in fs_users: $err")
                return Result.failure(Exception("Supabase user creation failed: $err"))
            }

            val record = SupabaseUserRecord(
                id = userId,
                email = cleanEmail,
                username = cleanEmail,
                role = "parent",
                displayName = cleanName,
                familyId = familyId
            )
            Result.success(record)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Parent register error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sign in Parent from Supabase fs_users
     */
    fun loginParent(
        email: String,
        passwordExpected: String
    ): Result<SupabaseUserRecord> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val req = newAuthRequest("fs_users?email=eq.$cleanEmail&role=eq.PARENT&select=*")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("Supabase query failed: HTTP ${resp.code}"))
            }
            val arr = JSONArray(resp.body?.string() ?: "[]")
            if (arr.length() == 0) {
                return Result.failure(Exception("No parent account found for '$cleanEmail'."))
            }
            val obj = arr.getJSONObject(0)
            val dbPass = obj.optString("password")
            if (dbPass != passwordExpected) {
                return Result.failure(Exception("Incorrect password. Please verify and try again."))
            }

            val record = SupabaseUserRecord(
                id = obj.getString("id"),
                email = obj.optString("email", cleanEmail),
                username = obj.optString("username", cleanEmail),
                role = "parent",
                displayName = obj.optString("display_name", cleanEmail),
                familyId = obj.optString("family_id", "")
            )
            Result.success(record)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Child device pairing directly via Parent credentials
     */
    fun pairChildDevice(
        parentEmail: String,
        parentPasswordExpected: String,
        childName: String,
        deviceName: String
    ): Result<Pair<SupabaseUserRecord, String>> {
        return try {
            val parentRes = loginParent(parentEmail, parentPasswordExpected)
            if (parentRes.isFailure) {
                return Result.failure(parentRes.exceptionOrNull() ?: Exception("Parent authentication failed."))
            }
            val parent = parentRes.getOrThrow()
            val familyId = parent.familyId ?: return Result.failure(Exception("Parent has no family group."))

            // Check if child already exists under this family
            val cleanChildName = childName.trim()
            val cleanUsername = cleanChildName.lowercase().replace(" ", "")
            val childEmail = "$cleanUsername@focussense.local"
            val now = isoNow()

            var childId = UUID.randomUUID().toString()
            val checkChildReq = newAuthRequest("fs_users?email=eq.$childEmail&select=id,display_name")
                .get()
                .build()
            val checkChildResp = httpClient.newCall(checkChildReq).execute()
            val existingChildren = JSONArray(checkChildResp.body?.string() ?: "[]")

            if (existingChildren.length() > 0) {
                childId = existingChildren.getJSONObject(0).getString("id")
            } else {
                // Insert Child into fs_users
                val childJson = JSONObject().apply {
                    put("id", childId)
                    put("email", childEmail)
                    put("username", cleanUsername)
                    put("password", parentPasswordExpected)
                    put("role", "CHILD")
                    put("display_name", cleanChildName)
                    put("family_id", familyId)
                    put("created_by_id", parent.id)
                    put("is_active", true)
                    put("is_staff", false)
                    put("is_superuser", false)
                    put("date_joined", now)
                }
                val cReq = newAuthRequest("fs_users")
                    .addHeader("Prefer", "return=representation")
                    .post(childJson.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(cReq).execute()
            }

            // Register Child Device in fs_devices
            val deviceId = UUID.randomUUID().toString()
            val cleanDevName = if (deviceName.isNotBlank()) deviceName.trim() else "$cleanChildName's Device"
            val devJson = JSONObject().apply {
                put("id", deviceId)
                put("user_id", childId)
                put("device_name", cleanDevName)
                put("device_model", "Android")
                put("os_version", "14.0")
                put("app_version", "1.0.0")
                put("fcm_token", "fcm_" + UUID.randomUUID().toString().take(16))
                put("is_active", true)
                put("last_seen", now)
            }
            val dReq = newAuthRequest("fs_devices")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(devJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(dReq).execute()

            val record = SupabaseUserRecord(
                id = childId,
                email = childEmail,
                username = cleanUsername,
                role = "child",
                displayName = cleanChildName,
                familyId = familyId
            )
            Result.success(Pair(record, deviceId))
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Pair child error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Create Child account directly in Supabase fs_users
     */
    fun createChildAccount(
        username: String,
        displayName: String,
        familyId: String,
        parentId: String,
        passwordHash: String
    ): Result<SupabaseUserRecord> {
        return try {
            val cleanUsername = username.trim().lowercase()
            val cleanName = displayName.trim()
            val childEmail = "$cleanUsername@focussense.local"
            val now = isoNow()

            val childId = UUID.randomUUID().toString()
            val childJson = JSONObject().apply {
                put("id", childId)
                put("email", childEmail)
                put("username", cleanUsername)
                put("password", passwordHash)
                put("role", "CHILD")
                put("display_name", cleanName)
                put("family_id", familyId)
                put("created_by_id", parentId)
                put("is_active", true)
                put("is_staff", false)
                put("is_superuser", false)
                put("date_joined", now)
            }

            val req = newAuthRequest("fs_users")
                .addHeader("Prefer", "return=representation")
                .post(childJson.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: ""
                Log.e("SupabaseClient", "Failed to create child: $err")
                return Result.failure(Exception("Failed to save child in Supabase: $err"))
            }

            val record = SupabaseUserRecord(
                id = childId,
                email = childEmail,
                username = cleanUsername,
                role = "child",
                displayName = cleanName,
                familyId = familyId
            )
            Result.success(record)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Create child error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch children belonging to a family from Supabase
     */
    fun fetchFamilyChildren(familyId: String): List<SupabaseUserRecord> {
        return try {
            val req = newAuthRequest("fs_users?family_id=eq.$familyId&role=eq.CHILD&select=*")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string() ?: "[]")
            val list = mutableListOf<SupabaseUserRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SupabaseUserRecord(
                        id = obj.getString("id"),
                        email = obj.optString("email"),
                        username = obj.optString("username"),
                        role = "child",
                        displayName = obj.optString("display_name"),
                        familyId = familyId
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Fetch children error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Helper to safely format or validate UUID string
     */
    fun formatUuid(input: String?): String {
        if (input.isNullOrBlank()) return UUID.randomUUID().toString()
        return try {
            UUID.fromString(input).toString()
        } catch (_: Exception) {
            UUID.nameUUIDFromBytes(input.toByteArray()).toString()
        }
    }

    /**
     * Helper to normalize child ID to an existing user in fs_users
     */
    fun normalizeChildId(childId: String?): String? {
        if (!childId.isNullOrBlank() && childId != "child-default") {
            try {
                return UUID.fromString(childId).toString()
            } catch (_: Exception) {}
        }
        // Fallback: look up the first registered CHILD in fs_users
        return try {
            val req = newAuthRequest("fs_users?role=eq.CHILD&select=id&limit=1")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ensure a device is registered in fs_devices with all NOT NULL columns satisfied
     */
    fun ensureDeviceRegistered(
        childId: String,
        preferredDeviceId: String? = null,
        deviceName: String? = null
    ): String {
        val validChildId = normalizeChildId(childId) ?: childId
        // Check if device already exists in fs_devices for this child
        try {
            val checkReq = newAuthRequest("fs_devices?user_id=eq.$validChildId&select=id&limit=1")
                .get()
                .build()
            val checkResp = httpClient.newCall(checkReq).execute()
            if (checkResp.isSuccessful) {
                val arr = JSONArray(checkResp.body?.string() ?: "[]")
                if (arr.length() > 0) {
                    return arr.getJSONObject(0).getString("id")
                }
            }
        } catch (_: Exception) {}

        // Create new device record
        val deviceId = if (!preferredDeviceId.isNullOrBlank()) formatUuid(preferredDeviceId) else UUID.randomUUID().toString()
        val now = isoNow()
        val devJson = JSONObject().apply {
            put("id", deviceId)
            put("user_id", validChildId)
            put("device_name", deviceName ?: "Child Phone")
            put("device_model", "Android")
            put("os_version", "14.0")
            put("app_version", "1.0.0")
            put("fcm_token", "fcm_" + UUID.randomUUID().toString().take(16))
            put("is_active", true)
            put("last_seen", now)
        }
        try {
            val req = newAuthRequest("fs_devices")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(devJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute()
        } catch (e: Exception) {
            Log.w("SupabaseClient", "ensureDeviceRegistered insert: ${e.message}")
        }
        return deviceId
    }

    /**
     * Upload telemetry activity log to Supabase fs_activity_logs
     */
    fun pushActivityLog(log: ActivityLogEntity, childId: String, deviceId: String? = null): Boolean {
        return try {
            val now = isoNow()
            val validChildId = normalizeChildId(childId) ?: return false
            val validDeviceId = ensureDeviceRegistered(validChildId, deviceId)
            val validId = formatUuid(log.logId)

            val json = JSONObject().apply {
                put("id", validId)
                put("child_id", validChildId)
                put("device_id", validDeviceId)
                put("package_name", log.packageName)
                put("app_name", log.appName)
                put("content_title", log.contentTitle ?: "")
                put("threat_category", log.threatCategory ?: "Safe")
                put("confidence_score", log.confidenceScore)
                put("ai_analysis_summary", log.aiAnalysisSummary ?: "")
                put("is_flagged", log.isFlagged)
                put("timestamp", now)
                put("created_at", now)
            }
            val req = newAuthRequest("fs_activity_logs")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w("SupabaseClient", "Failed to upload activity log HTTP ${resp.code}: ${resp.body?.string()}")
            }
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "Failed to upload activity log: ${e.message}")
            false
        }
    }

    /**
     * Fetch activity logs for child from Supabase fs_activity_logs
     */
    fun fetchChildLogs(childId: String): List<JSONObject> {
        return try {
            val validChildId = normalizeChildId(childId) ?: childId
            val req = newAuthRequest("fs_activity_logs?child_id=eq.$validChildId&order=timestamp.desc&limit=50")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string() ?: "[]")
            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Acknowledge a Safety Alert in Supabase fs_activity_logs (updates is_acknowledged or falls back to ai_analysis_summary marker).
     */
    fun acknowledgeActivityLog(logId: String): Boolean {
        return try {
            val validId = formatUuid(logId)
            val ackJson = JSONObject().apply { put("is_acknowledged", true) }
            val patchReq = newAuthRequest("fs_activity_logs?id=eq.$validId")
                .patch(ackJson.toString().toRequestBody(jsonMediaType))
                .build()
            val patchResp = httpClient.newCall(patchReq).execute()
            if (patchResp.isSuccessful) {
                return true
            }

            // Schema fallback: if fs_activity_logs has no is_acknowledged column, tag ai_analysis_summary
            val getReq = newAuthRequest("fs_activity_logs?id=eq.$validId&select=ai_analysis_summary&limit=1")
                .get()
                .build()
            val getResp = httpClient.newCall(getReq).execute()
            val existingSummary = if (getResp.isSuccessful) {
                val arr = JSONArray(getResp.body?.string() ?: "[]")
                if (arr.length() > 0) arr.getJSONObject(0).optString("ai_analysis_summary", "") else ""
            } else ""
            val updatedSummary = if (existingSummary.startsWith("[ACKNOWLEDGED]")) {
                existingSummary
            } else {
                "[ACKNOWLEDGED] $existingSummary".trim()
            }
            val summaryJson = JSONObject().apply { put("ai_analysis_summary", updatedSummary) }
            val fallbackReq = newAuthRequest("fs_activity_logs?id=eq.$validId")
                .patch(summaryJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(fallbackReq).execute().isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "acknowledgeActivityLog error: ${e.message}")
            false
        }
    }

    /**
     * Delete an Activity Log / Safety Alert from Supabase fs_activity_logs
     */
    fun deleteActivityLog(logId: String): Boolean {
        return try {
            val validId = formatUuid(logId)
            val req = newAuthRequest("fs_activity_logs?id=eq.$validId")
                .delete()
                .build()
            httpClient.newCall(req).execute().isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "deleteActivityLog error: ${e.message}")
            false
        }
    }

    /**
     * Upload GPS location fix to Supabase fs_location_logs
     */
    fun pushLocation(point: LocationPointEntity, childId: String, deviceId: String? = null): Boolean {
        return try {
            val now = isoNow()
            val validChildId = normalizeChildId(childId) ?: return false
            val validDeviceId = ensureDeviceRegistered(validChildId, deviceId)
            val validId = formatUuid(point.locId)

            val json = JSONObject().apply {
                put("id", validId)
                put("child_id", validChildId)
                put("device_id", validDeviceId)
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("accuracy", point.accuracy)
                put("timestamp", now)
                put("created_at", now)
            }
            val req = newAuthRequest("fs_location_logs")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w("SupabaseClient", "Failed to upload location HTTP ${resp.code}: ${resp.body?.string()}")
            }
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "Failed to upload location: ${e.message}")
            false
        }
    }

    /**
     * Fetch latest GPS location for child from Supabase fs_location_logs
     */
    fun fetchLatestLocation(childId: String): JSONObject? {
        return try {
            val validChildId = normalizeChildId(childId) ?: childId
            val req = newAuthRequest("fs_location_logs?child_id=eq.$validChildId&order=timestamp.desc&limit=1")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string() ?: "[]")
            if (arr.length() > 0) arr.getJSONObject(0) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Upload Schedule Rule to Supabase fs_schedule_rules
     */
    fun pushScheduleRule(rule: com.example.data.model.ScheduleRuleEntity, childId: String): Boolean {
        return try {
            val now = isoNow()
            val validChildId = normalizeChildId(childId) ?: return false
            val validId = formatUuid(rule.ruleId)

            fun formatTime(t: String): String {
                val trimmed = t.trim()
                return when {
                    trimmed.count { it == ':' } == 1 -> "$trimmed:00"
                    trimmed.count { it == ':' } == 2 -> trimmed
                    else -> "00:00:00"
                }
            }

            val json = JSONObject().apply {
                put("id", validId)
                put("child_id", validChildId)
                put("rule_name", rule.ruleName.ifBlank { "Schedule Rule" })
                put("rule_type", rule.category.ifBlank { "SCHEDULE" }.uppercase())
                if (rule.restrictedPackages.isNotBlank()) {
                    put("target_package", rule.restrictedPackages)
                }
                put("days_of_week", rule.dayOfWeek.ifBlank { "Mon,Tue,Wed,Thu,Fri,Sat,Sun" })
                put("start_time", formatTime(rule.startTime))
                put("end_time", formatTime(rule.endTime))
                put("is_active", rule.isActive)
                put("version", 1)
                put("updated_at", now)
            }

            val req = newAuthRequest("fs_schedule_rules")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w("SupabaseClient", "pushScheduleRule failed HTTP ${resp.code}: ${resp.body?.string()}")
            }
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "pushScheduleRule error: ${e.message}")
            false
        }
    }

    /**
     * Delete Schedule Rule from Supabase fs_schedule_rules
     */
    fun deleteScheduleRule(ruleId: String): Boolean {
        return try {
            val validId = formatUuid(ruleId)
            val req = newAuthRequest("fs_schedule_rules?id=eq.$validId")
                .delete()
                .build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Toggle Schedule Rule Active Status in Supabase fs_schedule_rules
     */
    fun toggleScheduleRule(ruleId: String, isActive: Boolean): Boolean {
        return try {
            val validId = formatUuid(ruleId)
            val now = isoNow()
            val json = JSONObject().apply {
                put("is_active", isActive)
                put("updated_at", now)
            }
            val req = newAuthRequest("fs_schedule_rules?id=eq.$validId")
                .patch(json.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch Schedule Rules for child from Supabase fs_schedule_rules
     */
    fun fetchScheduleRules(childId: String): List<com.example.data.model.ScheduleRuleEntity> {
        return try {
            val validChildId = normalizeChildId(childId) ?: return emptyList()
            val req = newAuthRequest("fs_schedule_rules?child_id=eq.$validChildId&order=updated_at.desc")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string() ?: "[]")
            val list = mutableListOf<com.example.data.model.ScheduleRuleEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ruleType = obj.optString("rule_type", "General")
                val ruleName = obj.optString("rule_name", "Rule")
                if (ruleType.equals("APP_INVENTORY", ignoreCase = true) ||
                    ruleType.equals("YOUTUBE_STUDY", ignoreCase = true) ||
                    ruleName == "__SYSTEM_APP_INVENTORY__" ||
                    ruleName == "__SYSTEM_YOUTUBE_STUDY_CHANNELS__"
                ) {
                    continue
                }
                list.add(
                    com.example.data.model.ScheduleRuleEntity(
                        ruleId = obj.getString("id"),
                        childId = validChildId,
                        ruleName = ruleName,
                        category = ruleType,
                        startTime = obj.optString("start_time", "08:00").take(5),
                        endTime = obj.optString("end_time", "17:00").take(5),
                        dayOfWeek = obj.optString("days_of_week", "Mon,Tue,Wed,Thu,Fri,Sat,Sun"),
                        restrictedPackages = obj.optString("target_package", ""),
                        isActive = obj.optBoolean("is_active", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w("SupabaseClient", "fetchScheduleRules error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Push Child phone's installed applications to Supabase (tries fs_installed_apps, falls back to fs_schedule_rules manifest row).
     */
    fun pushInstalledApps(
        apps: List<com.example.data.model.InstalledAppEntity>,
        childId: String
    ): Boolean {
        return try {
            val validChildId = normalizeChildId(childId) ?: return false
            val now = isoNow()
            val cappedApps = apps.take(120)

            // 1. Try dedicated fs_installed_apps table if present in Supabase
            try {
                val rows = JSONArray()
                for (app in cappedApps) {
                    rows.put(JSONObject().apply {
                        put("id", formatUuid("${validChildId}_${app.packageName}"))
                        put("child_id", validChildId)
                        put("package_name", app.packageName)
                        put("app_name", app.appName)
                        put("category", app.category)
                        put("is_blocked", app.isBlocked)
                        put("updated_at", now)
                    })
                }
                val directReq = newAuthRequest("fs_installed_apps")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(rows.toString().toRequestBody(jsonMediaType))
                    .build()
                val directResp = httpClient.newCall(directReq).execute()
                if (directResp.isSuccessful) {
                    return true
                }
            } catch (_: Exception) {}

            // 2. Fallback: persist compact JSON manifest inside inactive APP_INVENTORY row in fs_schedule_rules
            val compactArray = JSONArray()
            for (app in cappedApps) {
                compactArray.put(JSONObject().apply {
                    put("p", app.packageName)
                    put("n", app.appName)
                    put("c", app.category)
                    put("b", app.isBlocked)
                })
            }
            val manifestJson = JSONObject().apply {
                put("id", formatUuid("app-inventory-$validChildId"))
                put("child_id", validChildId)
                put("rule_name", "__SYSTEM_APP_INVENTORY__")
                put("rule_type", "APP_INVENTORY")
                put("target_package", compactArray.toString())
                put("days_of_week", "SYSTEM")
                put("start_time", "00:00:00")
                put("end_time", "00:00:00")
                put("is_active", false)
                put("version", 1)
                put("updated_at", now)
            }
            val req = newAuthRequest("fs_schedule_rules")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(manifestJson.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "pushInstalledApps error: ${e.message}")
            false
        }
    }

    /**
     * Fetch Child phone's remote installed application inventory from Supabase for Parent Dashboard.
     */
    fun fetchInstalledApps(childId: String): List<com.example.data.model.InstalledAppEntity> {
        return try {
            val validChildId = normalizeChildId(childId) ?: return emptyList()

            // 1. Try dedicated fs_installed_apps table first
            try {
                val directReq = newAuthRequest("fs_installed_apps?child_id=eq.$validChildId&order=app_name.asc")
                    .get()
                    .build()
                val directResp = httpClient.newCall(directReq).execute()
                if (directResp.isSuccessful) {
                    val arr = JSONArray(directResp.body?.string() ?: "[]")
                    val list = mutableListOf<com.example.data.model.InstalledAppEntity>()
                    val targetChildId = childId.ifBlank { validChildId }
                    val now = System.currentTimeMillis()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pkg = obj.optString("package_name")
                        if (pkg.isBlank()) continue
                        list.add(
                            com.example.data.model.InstalledAppEntity(
                                id = "${targetChildId}_$pkg",
                                childId = targetChildId,
                                packageName = pkg,
                                appName = obj.optString("app_name", pkg),
                                category = obj.optString("category", "General"),
                                isBlocked = obj.optBoolean("is_blocked", false),
                                lastUpdated = now
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Exception) {}

            // 2. Fallback: read APP_INVENTORY manifest row from fs_schedule_rules
            val req = newAuthRequest("fs_schedule_rules?child_id=eq.$validChildId&rule_type=eq.APP_INVENTORY&limit=1")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string() ?: "[]")
            if (arr.length() == 0) return emptyList()

            val payloadStr = arr.getJSONObject(0).optString("target_package", "")
            if (!payloadStr.startsWith("[")) return emptyList()
            val items = JSONArray(payloadStr)
            val result = mutableListOf<com.example.data.model.InstalledAppEntity>()
            val targetChildId = childId.ifBlank { validChildId }
            val now = System.currentTimeMillis()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val pkg = item.optString("p")
                if (pkg.isBlank()) continue
                result.add(
                    com.example.data.model.InstalledAppEntity(
                        id = "${targetChildId}_$pkg",
                        childId = targetChildId,
                        packageName = pkg,
                        appName = item.optString("n", pkg),
                        category = item.optString("c", "General"),
                        isBlocked = item.optBoolean("b", false),
                        lastUpdated = now
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.w("SupabaseClient", "fetchInstalledApps error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Push allowed YouTube Study channels to Supabase (tries fs_allowed_youtube_channels, falls back to fs_schedule_rules YOUTUBE_STUDY row).
     */
    fun pushAllowedYouTubeChannels(
        channels: List<com.example.data.model.AllowedYouTubeChannelEntity>,
        childId: String
    ): Boolean {
        return try {
            val validChildId = normalizeChildId(childId) ?: return false
            val now = isoNow()

            // 1. Try dedicated fs_allowed_youtube_channels table if present in Supabase
            try {
                if (channels.isNotEmpty()) {
                    val rows = JSONArray()
                    for (ch in channels) {
                        rows.put(JSONObject().apply {
                            put("id", formatUuid("${validChildId}_${ch.channelId}"))
                            put("child_id", validChildId)
                            put("channel_id", ch.channelId)
                            put("channel_name", ch.channelName)
                            put("handle", ch.handle)
                            put("category", ch.category)
                            put("channel_url", ch.channelUrl)
                            put("is_allowed", ch.isAllowed)
                            put("updated_at", now)
                        })
                    }
                    val directReq = newAuthRequest("fs_allowed_youtube_channels")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(rows.toString().toRequestBody(jsonMediaType))
                        .build()
                    val directResp = httpClient.newCall(directReq).execute()
                    if (directResp.isSuccessful) {
                        return true
                    }
                }
            } catch (_: Exception) {}

            // 2. Fallback: persist compact JSON whitelist inside inactive YOUTUBE_STUDY row in fs_schedule_rules
            val compactArray = JSONArray()
            for (ch in channels) {
                compactArray.put(JSONObject().apply {
                    put("id", ch.channelId)
                    put("n", ch.channelName)
                    put("h", ch.handle)
                    put("c", ch.category)
                    put("u", ch.channelUrl)
                    put("a", ch.isAllowed)
                })
            }
            val manifestJson = JSONObject().apply {
                put("id", formatUuid("youtube-study-$validChildId"))
                put("child_id", validChildId)
                put("rule_name", "__SYSTEM_YOUTUBE_STUDY_CHANNELS__")
                put("rule_type", "YOUTUBE_STUDY")
                put("target_package", compactArray.toString())
                put("days_of_week", "SYSTEM")
                put("start_time", "00:00:00")
                put("end_time", "00:00:00")
                put("is_active", false)
                put("version", 1)
                put("updated_at", now)
            }
            val req = newAuthRequest("fs_schedule_rules")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(manifestJson.toString().toRequestBody(jsonMediaType))
                .build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            Log.w("SupabaseClient", "pushAllowedYouTubeChannels error: ${e.message}")
            false
        }
    }

    /**
     * Fetch allowed YouTube Study channels for child from Supabase.
     */
    fun fetchAllowedYouTubeChannels(childId: String): List<com.example.data.model.AllowedYouTubeChannelEntity> {
        return try {
            val validChildId = normalizeChildId(childId) ?: return emptyList()
            val targetChildId = childId.ifBlank { validChildId }

            // 1. Try dedicated fs_allowed_youtube_channels table first
            try {
                val directReq = newAuthRequest("fs_allowed_youtube_channels?child_id=eq.$validChildId")
                    .get()
                    .build()
                val directResp = httpClient.newCall(directReq).execute()
                if (directResp.isSuccessful) {
                    val arr = JSONArray(directResp.body?.string() ?: "[]")
                    val list = mutableListOf<com.example.data.model.AllowedYouTubeChannelEntity>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("channel_name")
                        if (name.isBlank()) continue
                        list.add(
                            com.example.data.model.AllowedYouTubeChannelEntity(
                                channelId = obj.optString("channel_id", obj.optString("id")),
                                childId = targetChildId,
                                channelName = name,
                                handle = obj.optString("handle", ""),
                                category = obj.optString("category", "Study & Focus"),
                                channelUrl = obj.optString("channel_url", ""),
                                isAllowed = obj.optBoolean("is_allowed", true)
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Exception) {}

            // 2. Fallback: read YOUTUBE_STUDY row from fs_schedule_rules
            val req = newAuthRequest("fs_schedule_rules?child_id=eq.$validChildId&rule_type=eq.YOUTUBE_STUDY&limit=1")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string() ?: "[]")
            if (arr.length() == 0) return emptyList()

            val payloadStr = arr.getJSONObject(0).optString("target_package", "")
            if (!payloadStr.startsWith("[")) return emptyList()
            val items = JSONArray(payloadStr)
            val result = mutableListOf<com.example.data.model.AllowedYouTubeChannelEntity>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val name = item.optString("n")
                if (name.isBlank()) continue
                result.add(
                    com.example.data.model.AllowedYouTubeChannelEntity(
                        channelId = item.optString("id", "yt_${targetChildId}_$i"),
                        childId = targetChildId,
                        channelName = name,
                        handle = item.optString("h", ""),
                        category = item.optString("c", "Study & Focus"),
                        channelUrl = item.optString("u", ""),
                        isAllowed = item.optBoolean("a", true)
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.w("SupabaseClient", "fetchAllowedYouTubeChannels error: ${e.message}")
            emptyList()
        }
    }
}


