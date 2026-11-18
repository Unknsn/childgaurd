package com.example.core.device

import android.util.Log
import com.example.data.local.PairedChild
import com.example.data.local.PairedChildDao
import com.example.data.local.ProvisioningChallengeEntity
import com.example.data.local.ProvisioningDao
import java.security.SecureRandom
import java.util.UUID

sealed class ProvisioningResult {
    data class Success(val pairedChild: PairedChild) : ProvisioningResult()
    data class InvalidCode(val message: String) : ProvisioningResult()
    data class Expired(val message: String) : ProvisioningResult()
    data class Error(val error: Throwable) : ProvisioningResult()
}

/**
 * DeviceProvisioningManager
 *
 * Implements secure, parent-authorized child device and wearable provisioning:
 * 1. Parent generates a time-limited 6-digit challenge code.
 * 2. Child device inputs the code to bind with the parent's family account.
 * 3. Prevents unauthorized pairing and accidental child-to-parent mode elevation.
 */
class DeviceProvisioningManager(
    private val provisioningDao: ProvisioningDao,
    private val pairedChildDao: PairedChildDao
) {
    companion object {
        private const val TAG = "DeviceProvisioningMgr"
        const val CHALLENGE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates a 6-digit one-time pairing challenge for a child band/device.
     */
    suspend fun createPairingChallenge(
        childDeviceId: String,
        parentUserId: String
    ): ProvisioningChallengeEntity {
        require(parentUserId.isNotBlank()) { "Authenticated parent ID required to generate pairing challenge" }
        require(childDeviceId.isNotBlank()) { "Child device ID required to generate pairing challenge" }

        // Generate secure 6-digit PIN (e.g., 481920)
        val codeInt = 100000 + secureRandom.nextInt(900000)
        val code = codeInt.toString()

        val challenge = ProvisioningChallengeEntity(
            challengeId = "CHAL-${UUID.randomUUID().toString().take(8).uppercase()}",
            childDeviceId = childDeviceId.trim().uppercase(),
            oneTimeCode = code,
            expiresAt = System.currentTimeMillis() + CHALLENGE_TTL_MS,
            status = "PENDING",
            parentUserId = parentUserId.trim()
        )

        provisioningDao.insertChallenge(challenge)
        // Never log pairing codes to Logcat
        Log.i(TAG, "Generated pairing challenge for device ${challenge.childDeviceId} (expires in ${CHALLENGE_TTL_MS / 60000}m)")
        return challenge
    }

    /**
     * Child device verifies and atomically claims the pairing challenge.
     * Enforces single-use consumption, 15-minute TTL, and optional parent challenge ownership.
     */
    suspend fun verifyAndClaimChallenge(
        code: String,
        childName: String = "Child Device",
        expectedParentUserId: String? = null
    ): ProvisioningResult {
        return try {
            val cleanCode = code.trim().filter { it.isDigit() }
            if (cleanCode.length != 6) {
                return ProvisioningResult.InvalidCode("Pairing code must be 6 digits")
            }

            val challenge = provisioningDao.getChallengeByCode(cleanCode)
                ?: return ProvisioningResult.InvalidCode("Invalid pairing code")

            val now = System.currentTimeMillis()
            if (challenge.expiresAt < now) {
                provisioningDao.updateChallengeStatus(challenge.challengeId, "EXPIRED")
                return ProvisioningResult.Expired("Challenge code has expired")
            }

            if (challenge.status != "PENDING") {
                return ProvisioningResult.InvalidCode("Pairing code has already been used")
            }

            // Verify parent ownership if expected parent is specified
            if (expectedParentUserId != null && challenge.parentUserId != expectedParentUserId) {
                return ProvisioningResult.InvalidCode("Pairing code belongs to a different parent account")
            }

            // Atomically claim challenge to prevent race conditions & duplicate claims
            val updatedCount = provisioningDao.claimChallengeAtomically(challenge.challengeId, now)
            if (updatedCount == 0) {
                val current = provisioningDao.getChallengeById(challenge.challengeId)
                return if (current == null || current.status != "PENDING") {
                    ProvisioningResult.InvalidCode("Pairing code has already been used")
                } else {
                    ProvisioningResult.Expired("Challenge code has expired")
                }
            }

            // Create or link PairedChild record
            val existing = pairedChildDao.findChildByDeviceId(challenge.childDeviceId)
            val child = existing ?: PairedChild(
                deviceId = challenge.childDeviceId,
                childName = childName,
                age = "8",
                bloodType = "O+",
                primaryGuardianPhone = "+91 98765 43210",
                isPhysicalEsp32 = challenge.childDeviceId.startsWith("EP-") || challenge.childDeviceId.startsWith("T2")
            )

            pairedChildDao.insertOrUpdateChild(child)
            Log.i(TAG, "Successfully provisioned device ${child.deviceId} for child ${child.childName}")
            ProvisioningResult.Success(child)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during challenge verification", e)
            ProvisioningResult.Error(e)
        }
    }
}
