package com.example.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * GoogleCredentialManagerAuth
 *
 * Modern Android Credential Manager integration for Google Sign-In.
 * Compatible with Android 14+ and backward-compatible to Android 7.0 (API 24+)
 * via Play Services Auth bridge.
 */
class GoogleCredentialManagerAuth(private val context: Context) {

    companion object {
        private const val TAG = "GoogleCredManagerAuth"
        // Default Web Client ID placeholder for Google Identity Services
        const val DEFAULT_WEB_CLIENT_ID = "104192847120-safeband-guardian.apps.googleusercontent.com"
    }

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    /**
     * Executes Google Sign-In via Android Credential Manager.
     * Returns an AuthenticatedUser or Failure.
     */
    suspend fun signIn(serverClientId: String = DEFAULT_WEB_CLIENT_ID): Result<AuthenticatedUser> {
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val user = AuthenticatedUser(
                    userId = "PARENT-" + UUID.nameUUIDFromBytes(googleIdTokenCredential.id.toByteArray()).toString().take(12).uppercase(),
                    googleSub = googleIdTokenCredential.id,
                    email = googleIdTokenCredential.id, // Email or sub
                    displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: "Parent Guardian",
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    authStatus = AuthStatus.AUTHENTICATED
                )
                Log.i(TAG, "Google Sign-In successful for: ${user.email}")
                Result.success(user)
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                Result.failure(IllegalStateException("Unsupported credential returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In prompt")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.type}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Generic Sign-In failure", e)
            Result.failure(e)
        }
    }
}
