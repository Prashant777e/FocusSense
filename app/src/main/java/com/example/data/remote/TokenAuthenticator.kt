package com.example.data.remote

import com.example.data.local.SecurePreferences
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class TokenAuthenticator(
    private val securePreferences: SecurePreferences,
    private val getServerUrl: () -> String
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops by counting prior responses
        var count = 0
        var current: Response? = response
        while (current != null) {
            count++
            current = current.priorResponse
        }
        if (count >= 3) {
            return null
        }

        val refreshToken = securePreferences.refreshToken
        if (refreshToken.isBlank()) {
            return null
        }

        val serverUrl = getServerUrl()
        val refreshUrl = "$serverUrl/api/v1/auth/token/refresh/"

        try {
            val client = OkHttpClient.Builder().build()
            val jsonBody = JSONObject().put("refresh", refreshToken).toString()
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(requestBody)
                .build()

            val refreshResponse = client.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val responseString = refreshResponse.body?.string() ?: return null
                val jsonObject = JSONObject(responseString)
                val newAccessToken = jsonObject.optString("access", "")

                if (newAccessToken.isNotBlank()) {
                    securePreferences.accessToken = newAccessToken
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                }
            } else {
                securePreferences.clearSession()
            }
        } catch (e: Exception) {
            // Network failure during refresh
        }

        return null
    }
}
