package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthRepository
import com.example.core.auth.AuthStatus
import com.example.core.auth.GoogleCredentialManagerAuth
import com.example.core.device.DeviceProvisioningManager
import com.example.core.device.ProvisioningResult
import com.example.core.rbac.AccessController
import com.example.core.rbac.AppRole
import com.example.core.rbac.Permission
import com.example.data.local.ParentAccountEntity
import com.example.data.local.ProvisioningChallengeEntity
import com.example.data.local.SafeBandDatabase
import com.example.data.preferences.SafeBandPreferences
import com.example.model.AppMode
import com.example.model.ChildBioProfile
import com.example.model.TrustedRoute
import com.example.ui.SafeBandViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * ParentChildIsolationAndAuthTest (v3.3 Architecture & Production Verification)
 *
 * Verifies:
 * 1. Role-Based Access Control (RBAC): Strict segregation between Parent and Child at ViewModel boundary.
 * 2. Parent Account, Google Identity (JWT Claims) & Guest Offline Session Management (Room DB v5).
 * 3. 6-Digit Cryptographic Provisioning Challenge, Atomic Single-Use, Concurrency & 15-minute TTL.
 * 4. Production Child Mode lockdown: Rejects arbitrary 6-digit PINs, unlocks only via configured Master PIN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ParentChildIsolationAndAuthTest {

    private lateinit var context: Context
    private lateinit var db: SafeBandDatabase
    private lateinit var provisioningManager: DeviceProvisioningManager
    private lateinit var preferences: SafeBandPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferences = SafeBandPreferences(context)
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
        preferences.resetCache()
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
        assertTrue("Parent must install updates", AccessController.hasPermission(AppRole.PARENT, Permission.INSTALL_UPDATE))

        // Child permissions check (strict isolation)
        assertFalse("Child CANNOT manage children", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_CHILDREN))
        assertFalse("Child CANNOT manage safe zones", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_SAFE_ZONES))
        assertFalse("Child CANNOT manage journeys", AccessController.hasPermission(AppRole.CHILD, Permission.MANAGE_JOURNEYS))
        assertFalse("Child CANNOT access guardian settings", AccessController.hasPermission(AppRole.CHILD, Permission.ACCESS_SETTINGS))
        assertFalse("Child CANNOT switch role freely", AccessController.hasPermission(AppRole.CHILD, Permission.SWITCH_ROLE))
        assertFalse("Child CANNOT approve pickups", AccessController.hasPermission(AppRole.CHILD, Permission.APPROVE_PICKUP))
        assertFalse("Child CANNOT install updates", AccessController.hasPermission(AppRole.CHILD, Permission.INSTALL_UPDATE))

        // Child core safety rights
        assertTrue("Child must be able to trigger SOS", AccessController.hasPermission(AppRole.CHILD, Permission.TRIGGER_SOS))
        assertTrue("Child must be able to view incidents", AccessController.hasPermission(AppRole.CHILD, Permission.VIEW_INCIDENTS))
    }

    @Test
    fun testRbacEnforcementAtViewModelBoundary() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        preferences.setCachedAppMode(AppMode.CHILD)
        val viewModel = SafeBandViewModel(app)
        try {
            assertEquals("Current role must be CHILD", AppRole.CHILD, viewModel.currentRole.value)

            // Verify that calling Parent/Management operations in Child mode throws SecurityException
            var safeZoneBlocked = false
            try {
                viewModel.saveSafeZone(37.7749, -122.4194, 200f, "Test Zone")
            } catch (_: SecurityException) {
                safeZoneBlocked = true
            }
            assertTrue("Child mode must block saveSafeZone with SecurityException", safeZoneBlocked)

            var bioUpdateBlocked = false
            try {
                viewModel.updateChildBioProfile(ChildBioProfile(childName = "Hacked"))
            } catch (_: SecurityException) {
                bioUpdateBlocked = true
            }
            assertTrue("Child mode must block updateChildBioProfile with SecurityException", bioUpdateBlocked)

            var deleteChildBlocked = false
            try {
                viewModel.deletePairedChild("SB-9999")
            } catch (_: SecurityException) {
                deleteChildBlocked = true
            }
            assertTrue("Child mode must block deletePairedChild with SecurityException", deleteChildBlocked)

            var routeBlocked = false
            try {
                viewModel.saveTrustedRoute(TrustedRoute())
            } catch (_: SecurityException) {
                routeBlocked = true
            }
            assertTrue("Child mode must block saveTrustedRoute with SecurityException", routeBlocked)

            var deviceIdBlocked = false
            try {
                viewModel.setCustomDeviceId("SB-HACKED")
            } catch (_: SecurityException) {
                deviceIdBlocked = true
            }
            assertTrue("Child mode must block setCustomDeviceId with SecurityException", deviceIdBlocked)

            var switchBlocked = false
            try {
                viewModel.selectAppMode(AppMode.PARENT)
            } catch (_: SecurityException) {
                switchBlocked = true
            }
            assertTrue("Child mode must block mode switching with SecurityException", switchBlocked)
        } finally {
            viewModel.cleanup()
        }
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

        // Delete account
        parentDao.deleteAccountById("USER-GOOGLE-109283")
        val afterDelete = parentDao.getAccountById("USER-GOOGLE-109283")
        assertNull("Account should be null after deleteAccountById", afterDelete)
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
            childName = "Rohan Sharma",
            expectedParentUserId = "PARENT-USER-01"
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

        // 5. Attempting to claim the same code again should fail atomically
        val duplicateResult = provisioningManager.verifyAndClaimChallenge(
            code = challenge.oneTimeCode,
            childName = "Another Child"
        )
        assertTrue("Duplicate claim must be rejected", duplicateResult is ProvisioningResult.InvalidCode)
    }

    @Test
    fun testProvisioningConcurrencyRaceEnforcesSingleWinner() = runBlocking {
        val challenge = provisioningManager.createPairingChallenge(
            childDeviceId = "SB-CONCURRENT",
            parentUserId = "PARENT-01"
        )

        // Launch 10 simultaneous coroutines trying to claim the same code
        val jobs = (1..10).map { idx ->
            async {
                provisioningManager.verifyAndClaimChallenge(
                    code = challenge.oneTimeCode,
                    childName = "Candidate $idx"
                )
            }
        }

        val results = jobs.awaitAll()
        val successCount = results.count { it is ProvisioningResult.Success }
        val invalidCount = results.count { it is ProvisioningResult.InvalidCode }

        assertEquals("Exactly one concurrent claim must succeed", 1, successCount)
        assertEquals("All other concurrent claims must be rejected", 9, invalidCount)
    }

    @Test
    fun testProvisioningChallengeTtlExpiry() = runBlocking {
        val expiredChallenge = ProvisioningChallengeEntity(
            challengeId = "CHAL-EXPIRED",
            childDeviceId = "SB-OLD-01",
            oneTimeCode = "654321",
            expiresAt = System.currentTimeMillis() - 1000L,
            status = "PENDING",
            parentUserId = "PARENT-01"
        )
        db.provisioningDao().insertChallenge(expiredChallenge)
        assertTrue("Challenge must report isExpired = true", expiredChallenge.isExpired)

        val result = provisioningManager.verifyAndClaimChallenge(
            code = "654321",
            childName = "Test Child"
        )

        assertTrue("Expired code must return Expired result", result is ProvisioningResult.Expired)
    }

    @Test
    fun testProvisioningMismatchedParentRejection() = runBlocking {
        val challenge = provisioningManager.createPairingChallenge(
            childDeviceId = "SB-OTHER",
            parentUserId = "PARENT-REAL"
        )

        val result = provisioningManager.verifyAndClaimChallenge(
            code = challenge.oneTimeCode,
            childName = "Test Child",
            expectedParentUserId = "PARENT-IMPOSTOR"
        )

        assertTrue("Mismatched parent must be rejected", result is ProvisioningResult.InvalidCode)
        assertEquals(
            "Pairing code belongs to a different parent account",
            (result as ProvisioningResult.InvalidCode).message
        )
    }

    @Test
    fun testProductionChildModeLockdownRejectsArbitrarySixDigitNumbers() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        preferences.setCachedMasterPin("998877")
        val viewModel = SafeBandViewModel(app)
        try {
            // Production unlockChildMode MUST reject arbitrary 6-digit numbers!
            assertFalse("Arbitrary '000000' must be rejected", viewModel.unlockChildMode("000000"))
            assertFalse("Arbitrary '123456' must be rejected", viewModel.unlockChildMode("123456"))
            assertFalse("Arbitrary '555555' must be rejected", viewModel.unlockChildMode("555555"))
            assertFalse("Short code '123' must be rejected", viewModel.unlockChildMode("123"))
            assertFalse("Letters 'abcdef' must be rejected", viewModel.unlockChildMode("abcdef"))
            assertFalse("Blank input must be rejected", viewModel.unlockChildMode("   "))

            // Configured master PIN must unlock
            assertTrue("Configured Master PIN 998877 must unlock child mode", viewModel.unlockChildMode("998877"))
        } finally {
            viewModel.cleanup()
        }
    }

    @Test
    fun testGoogleJwtClaimsExtractionDistinguishesSubFromEmail() {
        val authHelper = GoogleCredentialManagerAuth(context)

        // Synthetic JWT payload: header.payload.signature
        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val payloadJson = """{
            "sub": "109876543210987654321",
            "email": "dr.anita.roy@gmail.com",
            "name": "Dr. Anita Roy",
            "picture": "https://lh3.googleusercontent.com/a/photo.jpg"
        }""".trimIndent()

        val b64Header = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.toByteArray())
        val b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        val syntheticJwt = "$b64Header.$b64Payload.c2lnbmF0dXJl"

        val claims = authHelper.parseJwtClaims(syntheticJwt)
        assertEquals("109876543210987654321", claims["sub"])
        assertEquals("dr.anita.roy@gmail.com", claims["email"])
        assertEquals("Dr. Anita Roy", claims["name"])
        assertEquals("https://lh3.googleusercontent.com/a/photo.jpg", claims["picture"])
        assertNotEquals("Subject ID must never equal email address", claims["sub"], claims["email"])
    }

    @Test
    fun testGuestParentOfflineLifecycle() = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            parentAccountDao = db.parentAccountDao(),
            preferences = preferences
        )

        val result = authRepo.signInAsGuestParent(
            name = "Offline Mother",
            email = "offline.guardian@local"
        )

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("Offline Mother", user.displayName)
        assertEquals("offline.guardian@local", user.email)
        assertEquals("GUEST_OFFLINE", user.authProvider)
        assertEquals(AuthStatus.OFFLINE_AUTHENTICATED, user.authStatus)

        // Verify session can be restored for offline parents
        val restoredResult = authRepo.restoreSession()
        assertTrue(restoredResult.isSuccess)
        val restoredUser = restoredResult.getOrThrow()
        assertNotNull(restoredUser)
        assertEquals("GUEST_OFFLINE", restoredUser?.authProvider)
        assertEquals(AuthStatus.OFFLINE_AUTHENTICATED, restoredUser?.authStatus)

        // Test sign-out
        authRepo.signOut()
        assertEquals(AuthStatus.SIGNED_OUT, authRepo.authStatus.value)
        assertNull(authRepo.currentUser.value)
    }
}
