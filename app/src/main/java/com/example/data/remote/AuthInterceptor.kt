package com.example.data.remote

import com.example.data.local.SecurePreferences
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val securePreferences: SecurePreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = securePreferences.accessToken

        if (token.isBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
