package com.example.core.auth

import android.content.Context
import android.util.Log
import com.example.core.rbac.AppRole
import com.example.data.local.ParentAccountDao
import com.example.data.local.ParentAccountEntity
import com.example.data.preferences.SafeBandPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * AuthRepository
 *
 * Provider-independent authentication repository managing:
 * - Google Sign-In via Android Credential Manager
 * - Persistent Room DB parent account storage
 * - Offline session restoration and fallback
 * - Sign-out and session revocation
 */
class AuthRepository(
    private val context: Context,
    private val parentAccountDao: ParentAccountDao,
    private val preferences: SafeBandPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    private val googleAuthHelper = GoogleCredentialManagerAuth(context)

    private val _currentUser = MutableStateFlow<AuthenticatedUser?>(null)
    val currentUser: StateFlow<AuthenticatedUser?> = _currentUser.asStateFlow()

    private val _authStatus = MutableStateFlow(AuthStatus.SIGNED_OUT)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    init {
        scope.launch {
            restoreSession()
        }
    }

    /**
     * Restores an existing parent session from Room local database and DataStore.
     */
    suspend fun restoreSession(): Result<AuthenticatedUser?> {
        return try {
            val account = parentAccountDao.getActiveParentAccountSnapshot()
            if (account != null && account.authStatus == "AUTHENTICATED") {
                val user = AuthenticatedUser(
                    userId = account.userId,
                    googleSub = account.googleSub,
                    email = account.email,
                    displayName = account.displayName,
                    photoUrl = account.photoUrl,
                    role = AppRole.PARENT,
                    authStatus = AuthStatus.SESSION_RESTORED,
                    lastLoginTimestamp = account.lastLoginAt
                )
                _currentUser.value = user
                _authStatus.value = AuthStatus.SESSION_RESTORED
                Log.i(TAG, "Restored active parent session: ${user.email}")
                Result.success(user)
            } else {
                _currentUser.value = null
                _authStatus.value = AuthStatus.SIGNED_OUT
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore parent session", e)
            _authStatus.value = AuthStatus.AUTH_FAILED
            Result.failure(e)
        }
    }

    /**
     * Executes Google Sign-In via Credential Manager and persists parent profile.
     */
    suspend fun signInWithGoogle(): Result<AuthenticatedUser> {
        _authStatus.value = AuthStatus.AUTHENTICATING
        val result = googleAuthHelper.signIn()
        return if (result.isSuccess) {
            val user = result.getOrThrow()
            persistAccount(user)
            _currentUser.value = user
            _authStatus.value = AuthStatus.AUTHENTICATED
            Result.success(user)
        } else {
            val err = result.exceptionOrNull()
            Log.w(TAG, "Google Sign-In failed or cancelled: ${err?.message}")
            _authStatus.value = AuthStatus.AUTH_FAILED
            Result.failure(err ?: IllegalStateException("Authentication failed"))
        }
    }

    /**
     * Directly authenticate with user model (used for mock tests or guest fallback).
     */
    suspend fun authenticateWithUser(user: AuthenticatedUser): Result<AuthenticatedUser> {
        return try {
            persistAccount(user)
            _currentUser.value = user
            _authStatus.value = AuthStatus.AUTHENTICATED
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Offline Guest Parent Registration when Google Play Services is unavailable.
     */
    suspend fun signInAsGuestParent(name: String, email: String): Result<AuthenticatedUser> {
        val user = AuthenticatedUser(
            userId = "PARENT-" + UUID.randomUUID().toString().take(8).uppercase(),
            googleSub = "OFFLINE-" + UUID.randomUUID().toString().take(8),
            email = email.ifBlank { "parent@safeband.local" },
            displayName = name.ifBlank { "Parent Guardian" },
            role = AppRole.PARENT,
            authStatus = AuthStatus.OFFLINE_AUTHENTICATED
        )
        return authenticateWithUser(user)
    }

    /**
     * Sign out parent user and revoke active session.
     */
    suspend fun signOut() {
        try {
            parentAccountDao.clearAccounts()
            _currentUser.value = null
            _authStatus.value = AuthStatus.SIGNED_OUT
            Log.i(TAG, "Parent signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out", e)
        }
    }

    private suspend fun persistAccount(user: AuthenticatedUser) {
        val entity = ParentAccountEntity(
            userId = user.userId,
            googleSub = user.googleSub,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            lastLoginAt = System.currentTimeMillis(),
            authStatus = "AUTHENTICATED"
        )
        parentAccountDao.insertOrUpdateAccount(entity)
    }
}
