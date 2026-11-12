package com.example.core.auth

import com.example.core.rbac.AppRole

enum class AuthStatus {
    SIGNED_OUT,
    AUTHENTICATING,
    AUTHENTICATED,
    SESSION_RESTORED,
    AUTH_FAILED,
    OFFLINE_AUTHENTICATED
}

data class AuthenticatedUser(
    val userId: String,
    val googleSub: String = "",
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: AppRole = AppRole.PARENT,
    val authStatus: AuthStatus = AuthStatus.AUTHENTICATED,
    val lastLoginTimestamp: Long = System.currentTimeMillis()
)
