package com.fitgpt.app.data.auth

/**
 * Abstraction for authentication. Implementations should verify user
 * identity against a backend session (e.g. Firebase Auth with Google
 * Sign-In) and expose the verified [UserSession].
 *
 * When injected into [com.fitgpt.app.viewmodel.WardrobeViewModel],
 * recommendations are gated behind [isAuthenticated] — unauthenticated
 * or unverified sessions receive no recommendations.
 */
interface AuthManager {
    fun isAuthenticated(): Boolean
    fun getCurrentSession(): UserSession?
    fun getCurrentUserId(): String?
}
