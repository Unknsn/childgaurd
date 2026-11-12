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
        // Generate secure 6-digit PIN (e.g., 481920)
        val codeInt = 100000 + secureRandom.nextInt(900000)
        val code = codeInt.toString()

        val challenge = ProvisioningChallengeEntity(
            challengeId = "CHAL-${UUID.randomUUID().toString().take(8).uppercase()}",
            childDeviceId = childDeviceId.trim().uppercase(),
            oneTimeCode = code,
            expiresAt = System.currentTimeMillis() + CHALLENGE_TTL_MS,
            status = "PENDING",
            parentUserId = parentUserId
        )

        provisioningDao.insertChallenge(challenge)
        Log.i(TAG, "Generated pairing challenge $code for device $childDeviceId")
        return challenge
    }

    /**
     * Child device verifies and claims the pairing challenge.
     */
    suspend fun verifyAndClaimChallenge(
        code: String,
        childName: String = "Child Device"
    ): ProvisioningResult {
        return try {
            val cleanCode = code.trim().filter { it.isDigit() }
            if (cleanCode.length != 6) {
                return ProvisioningResult.InvalidCode("Pairing code must be 6 digits")
            }

            val challenge = provisioningDao.getChallengeByCode(cleanCode)
                ?: return ProvisioningResult.InvalidCode("Invalid pairing code")

            if (challenge.expiresAt < System.currentTimeMillis()) {
                provisioningDao.updateChallengeStatus(challenge.challengeId, "EXPIRED")
                return ProvisioningResult.Expired("Challenge code has expired")
            }

            if (challenge.status != "PENDING") {
                return ProvisioningResult.InvalidCode("Pairing code has already been used")
            }

            // Mark challenge claimed
            provisioningDao.updateChallengeStatus(challenge.challengeId, "CLAIMED")

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
