package com.fitgpt.app.data.auth

data class UserSession(
    val userId: String,
    val email: String? = null,
    val provider: AuthProvider = AuthProvider.GOOGLE,
    val isVerified: Boolean = false
)
