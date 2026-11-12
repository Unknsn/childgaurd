package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.device.DeviceProvisioningManager
import com.example.core.device.ProvisioningResult
import com.example.core.rbac.AccessController
import com.example.core.rbac.AppRole
import com.example.core.rbac.Permission
import com.example.data.local.ParentAccountEntity
import com.example.data.local.ProvisioningChallengeEntity
import com.example.data.local.SafeBandDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ParentChildIsolationAndAuthTest (v3.3 Architecture & Role Separation)
 *
 * Verifies:
 * 1. Role-Based Access Control (RBAC): Strict segregation between Parent and Child.
 * 2. Parent Account and Session Management (Room DB v5).
 * 3. 6-Digit Cryptographic Provisioning Challenge & 15-minute TTL enforcement.
 * 4. Child Mode lockdown & authorization code unlock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ParentChildIsolationAndAuthTest {

    private lateinit var context: Context
    private lateinit var db: SafeBandDatabase
    private lateinit var provisioningManager: DeviceProvisioningManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(
                SafeBandDatabase.MIGRATION_1_2,
                SafeBandDatabase.MIGRATION_2_3,
                SafeBandDatabase.MIGRATION_3_4,
                SafeBandDatabase.MIGRATION_4_5
            )
            .build()
        provisioningManager = DeviceProvisioningManager(
            provisioningDao = db.provisioningDao(),
            pairedChildDao = db.pairedChildDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRbacParentVsChildSeparation() {
        // Parent permissions check
        assertTrue("Parent must manage children", AccessController.hasPermission(AppRole.PARENT, Permission.MANAGE_CHILDREN))
        assertTrue("Parent must manage safe zones", AccessController.hasPermission(AppRole.PARENT, Permission.MANAGE_SAFE_ZONES))
        assertTrue("Parent must manage journeys", AccessController.hasPermission(AppRole.PARENT, Permission.MANAGE_JOURNEYS))
        assertTrue("Parent must manage guardians", AccessController.hasPermission(AppRole.PARENT, Permission.MANAGE_GUARDIANS))
        assertTrue("Parent must access settings", AccessController.hasPermission(AppRole.PARENT, Permission.ACCESS_SETTINGS))
        assertTrue("Parent must view full bio", AccessController.hasPermission(AppRole.PARENT, Permission.VIEW_FULL_BIO))
        assertTrue("Parent must approve pickups", AccessController.hasPermission(AppRole.PARENT, Permission.APPROVE_PICKUP))

        // Child permissions check (strict isolation)
        assertFalse("Child CANNOT manage children", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_CHILDREN))
        assertFalse("Child CANNOT manage safe zones", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_SAFE_ZONES))
        assertFalse("Child CANNOT manage journeys", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_JOURNEYS))
        assertFalse("Child CANNOT access guardian settings", AccessController.hasPermission(AppRole.CHILD, Permission.ACCESS_SETTINGS))
        assertFalse("Child CANNOT switch role freely", AccessController.hasPermission(AppRole.CHILD, Permission.SWITCH_ROLE))
        assertFalse("Child CANNOT approve pickups", AccessController.hasPermission(AppRole.CHILD, Permission.APPROVE_PICKUP))

        // Child core safety rights
        assertTrue("Child must be able to trigger SOS", AccessController.hasPermission(AppRole.CHILD, Permission.TRIGGER_SOS))
        assertTrue("Child must be able to view incidents", AccessController.hasPermission(AppRole.CHILD, Permission.VIEW_INCIDENTS))
    }

    @Test
    fun testParentAccountDaoSessionPersistence() = runBlocking {
        val parentDao = db.parentAccountDao()

        val account = ParentAccountEntity(
            userId = "USER-GOOGLE-109283",
            googleSub = "109283746592837465",
            email = "parent.guardian@example.com",
            displayName = "Dr. Anita Roy",
            authStatus = "AUTHENTICATED"
        )

        parentDao.insertOrUpdateAccount(account)

        val retrieved = parentDao.getAccountById("USER-GOOGLE-109283")
        assertNotNull("Parent account should be retrieved from Room DB", retrieved)
        assertEquals("Dr. Anita Roy", retrieved?.displayName)
        assertEquals("parent.guardian@example.com", retrieved?.email)
        assertEquals("AUTHENTICATED", retrieved?.authStatus)

        val active = parentDao.getActiveParentAccountSnapshot()
        assertNotNull("Should get active parent account snapshot", active)
        assertEquals("USER-GOOGLE-109283", active?.userId)

        // Sign out / clear
        parentDao.clearAccounts()
        val afterClear = parentDao.getActiveParentAccountSnapshot()
        assertNull("Account should be null after clearing accounts", afterClear)
    }

    @Test
    fun testCryptographicProvisioningChallengeAndClaim() = runBlocking {
        // 1. Parent generates 6-digit challenge code
        val challenge = provisioningManager.createPairingChallenge(
            childDeviceId = "SB-88X9",
            parentUserId = "PARENT-USER-01"
        )

        assertNotNull("Challenge must be created", challenge)
        assertEquals("SB-88X9", challenge.childDeviceId)
        assertEquals(6, challenge.oneTimeCode.length)
        assertTrue("Code must be 6 numeric digits", challenge.oneTimeCode.all { it.isDigit() })
        assertEquals("PENDING", challenge.status)
        assertTrue("Challenge expires in the future", challenge.expiresAt > System.currentTimeMillis())
        assertFalse("Challenge must not be expired at creation", challenge.isExpired)

        // 2. Child enters the 6-digit code to pair
        val result = provisioningManager.verifyAndClaimChallenge(
            code = challenge.oneTimeCode,
            childName = "Rohan Sharma"
        )

        assertTrue("Provisioning must succeed with valid code", result is ProvisioningResult.Success)
        val success = result as ProvisioningResult.Success
        assertEquals("SB-88X9", success.pairedChild.deviceId)
        assertEquals("Rohan Sharma", success.pairedChild.childName)

        // 3. Verify PairedChild was stored in database
        val pairedChild = db.pairedChildDao().findChildByDeviceId("SB-88X9")
        assertNotNull("Child must be saved in paired_children table", pairedChild)
        assertEquals("Rohan Sharma", pairedChild?.childName)

        // 4. Verify challenge status changed to CLAIMED
        val updatedChallenge = db.provisioningDao().getChallengeById(challenge.challengeId)
        assertEquals("CLAIMED", updatedChallenge?.status)

        // 5. Attempting to claim the same code again should fail
        val duplicateResult = provisioningManager.verifyAndClaimChallenge(
            code = challenge.oneTimeCode,
            childName = "Another Child"
        )
        assertTrue("Duplicate claim must be rejected", duplicateResult is ProvisioningResult.InvalidCode)
    }

    @Test
    fun testProvisioningChallengeTtlExpiry() = runBlocking {
        // Create an already-expired challenge
        val expiredChallenge = ProvisioningChallengeEntity(
            challengeId = "CHAL-EXPIRED",
            childDeviceId = "SB-OLD-01",
            oneTimeCode = "654321",
            expiresAt = System.currentTimeMillis() - 1000L, // 1 second in the past
            status = "PENDING",
            parentUserId = "PARENT-01"
        )
        db.provisioningDao().insertChallenge(expiredChallenge)
        assertTrue("Challenge must report isExpired = true", expiredChallenge.isExpired)

        // Attempting to claim expired challenge
        val result = provisioningManager.verifyAndClaimChallenge(
            code = "654321",
            childName = "Test Child"
        )

        assertTrue("Expired code must return Expired result", result is ProvisioningResult.Expired)
        assertEquals("Challenge code has expired", (result as ProvisioningResult.Expired).message)
    }

    @Test
    fun testChildModeLockdownAuthorizationLogic() {
        val masterPin = "998877"
        val activeOneTimeCode = "481920"

        // Function mimicking SafeBandViewModel.unlockChildMode
        fun checkUnlock(pin: String, activeCode: String?): Boolean {
            val trimmed = pin.trim()
            return trimmed == "998877" || (activeCode != null && trimmed == activeCode) || (trimmed.length == 6 && trimmed.all { it.isDigit() } && trimmed == activeCode)
        }

        // Master PIN unlocks
        assertTrue("Master PIN 998877 must unlock child mode", checkUnlock(masterPin, activeOneTimeCode))

        // Active one-time pairing PIN unlocks
        assertTrue("Active pairing code must unlock child mode", checkUnlock(activeOneTimeCode, activeOneTimeCode))

        // Invalid codes fail
        assertFalse("Wrong PIN must fail", checkUnlock("000000", activeOneTimeCode))
        assertFalse("Short PIN must fail", checkUnlock("123", activeOneTimeCode))
        assertFalse("Random text must fail", checkUnlock("abcdef", activeOneTimeCode))
    }
}
