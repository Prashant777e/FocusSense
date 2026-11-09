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
                familyId = obj.optString("family_id", null)
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
                put("is_active", true)
                put("last_seen", now)
            }
            val dReq = newAuthRequest("fs_devices")
                .addHeader("Prefer", "return=representation")
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
     * Upload telemetry activity log to Supabase fs_activity_logs
     */
    fun pushActivityLog(log: ActivityLogEntity, childId: String) {
        try {
            val now = isoNow()
            val json = JSONObject().apply {
                put("id", log.logId)
                put("child_id", childId)
                put("package_name", log.packageName)
                put("app_name", log.appName)
                put("content_title", log.contentTitle ?: "")
                put("threat_category", log.threatCategory ?: "Safe")
                put("confidence_score", log.confidenceScore)
                put("ai_analysis_summary", log.aiAnalysisSummary ?: "")
                put("is_flagged", log.isFlagged)
                put("timestamp", now)
            }
            val req = newAuthRequest("fs_activity_logs")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute()
        } catch (e: Exception) {
            Log.w("SupabaseClient", "Failed to upload activity log: ${e.message}")
        }
    }

    /**
     * Fetch activity logs for child from Supabase fs_activity_logs
     */
    fun fetchChildLogs(childId: String): List<JSONObject> {
        return try {
            val req = newAuthRequest("fs_activity_logs?child_id=eq.$childId&order=timestamp.desc&limit=50")
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
     * Upload GPS location fix to Supabase fs_location_logs
     */
    fun pushLocation(point: LocationPointEntity, childId: String) {
        try {
            val now = isoNow()
            val json = JSONObject().apply {
                put("id", point.locId)
                put("child_id", childId)
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("accuracy", point.accuracy)
                put("timestamp", now)
            }
            val req = newAuthRequest("fs_location_logs")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute()
        } catch (e: Exception) {
            Log.w("SupabaseClient", "Failed to upload location: ${e.message}")
        }
    }

    /**
     * Fetch latest GPS location for child from Supabase fs_location_logs
     */
    fun fetchLatestLocation(childId: String): JSONObject? {
        return try {
            val req = newAuthRequest("fs_location_logs?child_id=eq.$childId&order=timestamp.desc&limit=1")
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
}
