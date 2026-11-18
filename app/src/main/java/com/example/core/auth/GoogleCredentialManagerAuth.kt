package com.example.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
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
     * Resolves the configured Google Web Client ID from resources, environment, or parameter.
     */
    fun resolveWebClientId(explicitId: String? = null): String {
        if (!explicitId.isNullOrBlank() && explicitId != DEFAULT_WEB_CLIENT_ID) {
            return explicitId
        }
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val configured = context.getString(resId)
                if (configured.isNotBlank()) return configured
            }
        } catch (_: Exception) {}

        val envKey = System.getenv("GOOGLE_SERVER_CLIENT_ID")
        if (!envKey.isNullOrBlank()) return envKey

        return explicitId ?: DEFAULT_WEB_CLIENT_ID
    }

    /**
     * Parses the JWT ID token payload to reliably extract claims without confusing subject ID and email.
     */
    fun parseJwtClaims(idToken: String): Map<String, String> {
        return try {
            val parts = idToken.split(".")
            if (parts.size >= 2) {
                val payloadBase64 = parts[1]
                val decodedBytes = android.util.Base64.decode(
                    payloadBase64,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                val jsonString = String(decodedBytes, Charsets.UTF_8)
                val json = org.json.JSONObject(jsonString)
                val map = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.optString(key)
                }
                map
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Executes Google Sign-In via Android Credential Manager.
     * Returns an AuthenticatedUser or Failure.
     */
    suspend fun signIn(serverClientId: String? = null): Result<AuthenticatedUser> {
        val effectiveClientId = resolveWebClientId(serverClientId)
        if (effectiveClientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Google Sign-In configuration missing: No valid server client ID found. Configure google-services.json or use Offline Parent mode.")
            )
        }

        return try {
            val rawNonce = UUID.randomUUID().toString()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(effectiveClientId)
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
                val claims = parseJwtClaims(googleIdTokenCredential.idToken)

                val actualSub = claims["sub"]?.takeIf { it.isNotBlank() } ?: googleIdTokenCredential.id
                val actualEmail = claims["email"]?.takeIf { it.isNotBlank() }
                    ?: if (googleIdTokenCredential.id.contains("@")) googleIdTokenCredential.id else "$actualSub@gmail.com"
                val displayName = claims["name"]?.takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: "Parent Guardian"
                val photoUrl = claims["picture"]?.takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.profilePictureUri?.toString()

                val user = AuthenticatedUser(
                    userId = "PARENT-" + UUID.nameUUIDFromBytes(actualSub.toByteArray()).toString().take(12).uppercase(),
                    googleSub = actualSub,
                    email = actualEmail,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    role = com.example.core.rbac.AppRole.PARENT,
                    authStatus = AuthStatus.AUTHENTICATED,
                    authProvider = "GOOGLE"
                )
                Log.i(TAG, "Google Sign-In successful for sub: $actualSub, email: $actualEmail")
                Result.success(user)
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                Result.failure(IllegalStateException("Unsupported credential returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In prompt")
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No credentials available on device: ${e.message}")
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
