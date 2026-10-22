package com.example.data.remote

import com.example.data.model.ActivityLogEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Auth & Pairing DTOs
data class UserDto(
    val user_id: String,
    val group_id: String,
    val email: String,
    val role: String,
    val name: String,
    val pin: String = "1234",
    val avatar: String = "default"
)

data class RegisterParentDto(
    val name: String,
    val family_name: String = "",
    val email: String,
    val password: String,
    val pin: String = "1234"
)

data class LoginParentDto(
    val email: String,
    val password: String
)

data class AuthResponseDto(
    val status: String,
    val user: UserDto,
    val family_name: String = "",
    val children: List<UserDto> = emptyList()
)

data class PairChildDto(
    val parent_email: String,
    val parent_password: String,
    val child_name: String,
    val device_name: String = "Child Phone"
)

data class PairChildResponseDto(
    val status: String,
    val child_user: UserDto,
    val device_id: String,
    val group_id: String
)

data class ThreatEvaluateRequestDto(
    val child_id: String,
    val package_name: String,
    val app_name: String,
    val content_title: String = "",
    val extracted_text: String
)

data class ThreatEvaluateResponseDto(
    val threat_detected: Boolean,
    val threat_category: String,
    val confidence_score: Float,
    val ai_analysis_summary: String,
    val model_used: String
)

data class SyncRequest(
    val child_id: String,
    val logs: List<ActivityLogEntity>,
    val locations: List<LocationPointEntity>
)

data class SyncResponse(
    val status: String,
    val synced_logs: Int,
    val flagged_threats: Int,
    val synced_locations: Int
)

data class HealthResponse(
    val status: String,
    val service: String,
    val deepseek_configured: Boolean = false,
    val database: String? = null,
    val supabase_counts: Map<String, Int>? = null,
    val timestamp: Long
)

data class AIConfigResponse(
    val status: String,
    val gemini_configured: Boolean = true,
    val gemini_model: String? = "gemini-3.5-flash",
    val deepseek_configured: Boolean = false,
    val deepseek_model: String = "gemini-3.5-flash",
    val active_endpoint: String = "Gemini 3.5 Flash Cloud AI",
    val fallback_engine: String? = null,
    val message: String? = null
)

data class AIConfigUpdateRequest(
    val gemini_api_key: String? = null,
    val deepseek_api_key: String? = null,
    val deepseek_server_url: String? = null,
    val deepseek_model: String? = null
)

data class AppBlockToggleDto(
    val package_name: String,
    val is_blocked: Boolean
)

interface FocusSenseApiService {

    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @GET("/api/ai/config")
    suspend fun getAIConfig(): Response<AIConfigResponse>

    @POST("/api/ai/config")
    suspend fun updateAIConfig(@Body payload: AIConfigUpdateRequest): Response<AIConfigResponse>

    @POST("/api/auth/register")
    suspend fun registerParent(@Body payload: RegisterParentDto): Response<AuthResponseDto>

    @POST("/api/auth/login")
    suspend fun loginParent(@Body payload: LoginParentDto): Response<AuthResponseDto>

    @POST("/api/devices/pair")
    suspend fun pairChildDevice(@Body payload: PairChildDto): Response<PairChildResponseDto>

    @GET("/api/family/{group_id}/children")
    suspend fun getFamilyChildren(@Path("group_id") groupId: String): Response<List<UserDto>>

    @POST("/api/ai/evaluate")
    suspend fun evaluateThreat(@Body payload: ThreatEvaluateRequestDto): Response<ThreatEvaluateResponseDto>

    @POST("/api/sync")
    suspend fun syncData(@Body payload: SyncRequest): Response<SyncResponse>

    @GET("/api/logs/{child_id}")
    suspend fun getLogs(
        @Path("child_id") childId: String,
        @Query("flagged_only") flaggedOnly: Boolean = false
    ): Response<List<ActivityLogEntity>>

    @DELETE("/api/logs/{log_id}")
    suspend fun deleteLog(@Path("log_id") logId: String): Response<Map<String, String>>

    @GET("/api/schedules/{child_id}")
    suspend fun getSchedules(@Path("child_id") childId: String): Response<List<ScheduleRuleEntity>>

    @POST("/api/schedules")
    suspend fun saveSchedule(@Body rule: ScheduleRuleEntity): Response<Map<String, String>>

    @DELETE("/api/schedules/{rule_id}")
    suspend fun deleteSchedule(@Path("rule_id") ruleId: String): Response<Map<String, String>>

    @POST("/api/location/report")
    suspend fun reportLocation(@Body point: LocationPointEntity): Response<Map<String, String>>

    @GET("/api/location/{child_id}/latest")
    suspend fun getLatestLocation(@Path("child_id") childId: String): Response<LocationPointEntity>

    @POST("/api/devices/{child_id}/apps/sync")
    suspend fun syncInstalledApps(
        @Path("child_id") childId: String,
        @Body apps: List<com.example.data.model.InstalledAppEntity>
    ): Response<Map<String, Any>>

    @GET("/api/devices/{child_id}/apps")
    suspend fun getInstalledApps(
        @Path("child_id") childId: String
    ): Response<List<com.example.data.model.InstalledAppEntity>>

    @POST("/api/devices/{child_id}/apps/toggle-block")
    suspend fun toggleAppBlock(
        @Path("child_id") childId: String,
        @Body payload: AppBlockToggleDto
    ): Response<Map<String, Any>>
}

object ApiClient {
    private var currentBaseUrl: String = "https://parental-controll.onrender.com"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getService(baseUrl: String = currentBaseUrl): FocusSenseApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FocusSenseApiService::class.java)
    }
}
