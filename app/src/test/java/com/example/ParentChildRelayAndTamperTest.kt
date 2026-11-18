package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.device.BleProvisioningManager
import com.example.core.device.BleTamperMaintenanceManager
import com.example.data.local.PairedChild
import com.example.data.local.RelayForwardingRecord
import com.example.data.local.SafeBandDatabase
import com.example.data.preferences.SafeBandPreferences
import com.example.service.EmergencyRelayManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ParentChildRelayAndTamperTest {

    private lateinit var context: Context
    private lateinit var database: SafeBandDatabase
    private lateinit var preferences: SafeBandPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = SafeBandPreferences(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // Feature 1: Parent-Child Provisioning & Endpoint Configuration
    // =========================================================================

    @Test
    fun testProvisioningPayloadSerializationAndDefaults() {
        val payload = BleProvisioningManager.buildProvisioningPayload(
            childId = "CHLD-8812",
            parentId = "PRNT-5501",
            parentNodeIp = "192.168.1.150",
            parentNodePort = 8080,
            endpointId = "PN-HUB-01",
            authToken = "AUTH-KEY-SECRET",
            version = 2
        )

        assertEquals("PROV:CHLD-8812|PRNT-5501|192.168.1.150|8080|PN-HUB-01|AUTH-KEY-SECRET|2", payload)

        // Test default fallbacks
        val defaultPayload = BleProvisioningManager.buildProvisioningPayload(
            childId = "CHLD-8812",
            parentId = "PRNT-5501",
            parentNodeIp = "",
            parentNodePort = 0,
            endpointId = "",
            authToken = "",
            version = 1
        )
        assertEquals("PROV:CHLD-8812|PRNT-5501|127.0.0.1|8080|PN-DEFAULT|TOKEN-DEFAULT|1", defaultPayload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testProvisioningPayloadRejectsBlankChildId() {
        BleProvisioningManager.buildProvisioningPayload(
            childId = "",
            parentId = "PRNT-01",
            parentNodeIp = "127.0.0.1",
            parentNodePort = 8080,
            endpointId = "PN-1",
            authToken = "TKN"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testProvisioningPayloadRejectsBlankParentId() {
        BleProvisioningManager.buildProvisioningPayload(
            childId = "CHLD-01",
            parentId = "   ",
            parentNodeIp = "127.0.0.1",
            parentNodePort = 8080,
            endpointId = "PN-1",
            authToken = "TKN"
        )
    }

    @Test
    fun testProvisioningAckParsing() {
        assertTrue(BleProvisioningManager.parseAckResponse("ACK:PROV:OK:v=1"))
        assertTrue(BleProvisioningManager.parseAckResponse("  ACK:PROV:OK  "))
        assertFalse(BleProvisioningManager.parseAckResponse("ERR:INVALID_PREFIX"))
        assertFalse(BleProvisioningManager.parseAckResponse("ERR:AUTH_FAILED"))
    }

    @Test
    fun testRoomDatabasePairedChildProvisioningAndEndpointPersistence() = runTest {
        val childDao = database.pairedChildDao()

        val child = PairedChild(
            deviceId = "EP-C401",
            childName = "Aarav",
            age = "8",
            bloodType = "O+",
            primaryGuardianPhone = "+91 98765 43210",
            parentNodeIp = "127.0.0.1",
            parentNodePort = 8080,
            parentNodeEndpointId = "PN-DEFAULT",
            tamperStatus = "PROTECTED",
            isProvisioned = false
        )
        childDao.insertOrUpdateChild(child)

        var retrieved = childDao.findChildByDeviceId("EP-C401")
        assertNotNull(retrieved)
        assertFalse(retrieved!!.isProvisioned)

        // Simulate successful provisioning update
        val provisionTimestamp = System.currentTimeMillis()
        childDao.updateProvisioningStatus("EP-C401", true, provisionTimestamp)
        childDao.updateParentNodeEndpoint("EP-C401", "10.0.0.12", 9090, "PN-CAMPUS-02")

        retrieved = childDao.findChildByDeviceId("EP-C401")
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isProvisioned)
        assertEquals(provisionTimestamp, retrieved.provisionedAt)
        assertEquals("10.0.0.12", retrieved.parentNodeIp)
        assertEquals(9090, retrieved.parentNodePort)
        assertEquals("PN-CAMPUS-02", retrieved.parentNodeEndpointId)
    }

    // =========================================================================
    // Feature 3: Hardware Tamper Monitoring & Maintenance Mode
    // =========================================================================

    @Test
    fun testStrapMaintenanceCommandSerializationAndClamping() {
        // Standard 5 minutes (300 seconds)
        val cmd = BleTamperMaintenanceManager.buildMaintenanceCommand(300L, "TOKEN-SEC-44")
        assertEquals("MAINT:AUTH:300:TOKEN-SEC-44", cmd)

        // Below minimum duration (clamped to 10s)
        val cmdMin = BleTamperMaintenanceManager.buildMaintenanceCommand(3L, "TOKEN")
        assertEquals("MAINT:AUTH:10:TOKEN", cmdMin)

        // Above maximum duration (clamped to 3600s / 1 hour)
        val cmdMax = BleTamperMaintenanceManager.buildMaintenanceCommand(7200L, "TOKEN")
        assertEquals("MAINT:AUTH:3600:TOKEN", cmdMax)

        // Cancel command
        assertEquals("MAINT:CANCEL", BleTamperMaintenanceManager.buildCancelCommand())

        // ACK parsing
        assertTrue(BleTamperMaintenanceManager.parseAckResponse("ACK:MAINT:OK:300"))
        assertTrue(BleTamperMaintenanceManager.parseAckResponse("ACK:MAINT:CANCELLED"))
        assertFalse(BleTamperMaintenanceManager.parseAckResponse("ERR:MAINT:UNAUTHORIZED"))
    }

    @Test
    fun testRoomDatabaseTamperStateTransitions() = runTest {
        val childDao = database.pairedChildDao()

        val child = PairedChild(
            deviceId = "EP-C402",
            childName = "Diya",
            age = "7",
            bloodType = "B+",
            primaryGuardianPhone = "+91 98123 45678",
            tamperStatus = "PROTECTED"
        )
        childDao.insertOrUpdateChild(child)

        // 1. Guardian authorizes 15 minutes maintenance
        val now = System.currentTimeMillis()
        val expiry = now + (900 * 1000L)
        childDao.updateTamperStatus("EP-C402", "AUTHORIZED_DISCONNECTION", expiry)

        var retrieved = childDao.findChildByDeviceId("EP-C402")
        assertEquals("AUTHORIZED_DISCONNECTION", retrieved?.tamperStatus)
        assertEquals(expiry, retrieved?.tamperAuthorizedUntil)

        // 2. Strap reconnected / Maintenance ended
        childDao.updateTamperStatus("EP-C402", "PROTECTED", 0L)
        retrieved = childDao.findChildByDeviceId("EP-C402")
        assertEquals("PROTECTED", retrieved?.tamperStatus)
        assertEquals(0L, retrieved?.tamperAuthorizedUntil)

        // 3. Unauthorized physical cut / Tamper confirmed
        childDao.updateTamperStatus("EP-C402", "TAMPER_CONFIRMED", 0L)
        retrieved = childDao.findChildByDeviceId("EP-C402")
        assertEquals("TAMPER_CONFIRMED", retrieved?.tamperStatus)
    }

    // =========================================================================
    // Feature 2: Community Relay Propagation & Strict Privacy
    // =========================================================================

    @Test
    fun testRelayPayloadJsonCreationStrictPrivacy() {
        val json = EmergencyRelayManager.createRelayPayloadJson(
            broadcastId = "EP-99AA",
            alertType = "TAMPER",
            lat = 37.774929,
            lon = -122.419416,
            accuracy = 12.5f,
            detectedAt = 1700000000000L
        )

        // Must include routing metadata
        assertTrue(json.contains("\"broadcastId\":\"EP-99AA\""))
        assertTrue(json.contains("\"alertType\":\"TAMPER\""))
        assertTrue(json.contains("\"relayLatitude\":37.774929"))
        assertTrue(json.contains("\"relayLongitude\":-122.419416"))
        assertTrue(json.contains("\"accuracy\":12.5"))
        assertTrue(json.contains("\"timestamp\":1700000000000"))

        // STRICT PRIVACY: Public relay payload MUST NEVER include Child PII
        assertFalse(json.contains("childName"))
        assertFalse(json.contains("primaryParentPhone"))
        assertFalse(json.contains("medicalConditions"))
        assertFalse(json.contains("allergies"))
        assertFalse(json.contains("bloodType"))
        assertFalse(json.contains("homeAddress"))
    }

    @Test
    fun testRelayForwardingRecordsDatabaseCrud() = runTest {
        val relayDao = database.relayForwardingDao()

        val record = RelayForwardingRecord(
            incidentId = "INC-FWD-001",
            deviceId = "EP-88CC",
            eventType = "SOS",
            relayLatitude = 28.6139,
            relayLongitude = 77.2090,
            relayAccuracy = 8.0f,
            targetEndpoint = "http://127.0.0.1:8080/api/relay/alert",
            forwardStatus = "QUEUED",
            detectionTimestamp = System.currentTimeMillis()
        )

        val insertedId = relayDao.insertRecord(record)

        val queued = relayDao.getQueuedRecords()
        assertEquals(1, queued.size)
        assertEquals(insertedId, queued[0].id)
        assertEquals("QUEUED", queued[0].forwardStatus)

        // Simulate direct delivery
        val deliveryTime = System.currentTimeMillis()
        relayDao.markDeliveredDirect(insertedId, deliveryTime, "ACK-TOKEN-123")

        val updatedQueued = relayDao.getQueuedRecords()
        assertEquals(0, updatedQueued.size)

        val all = relayDao.getAllRecords().first()
        assertEquals(1, all.size)
        assertEquals("DELIVERED_DIRECT", all[0].forwardStatus)
        assertEquals(deliveryTime, all[0].deliveryTimestamp)
    }

    @Test
    fun testEmergencyRelayManagerDeduplication() = runTest {
        val relayDao = database.relayForwardingDao()
        preferences.setCommunityRelayEnabled(true)

        val relayManager = EmergencyRelayManager(
            context = context,
            relayForwardingDao = relayDao,
            preferences = preferences,
            coroutineScope = this
        )

        // First broadcast ingested -> creates record and returns true
        val firstHandled = relayManager.handleEmergencyBroadcast(
            broadcastId = "EP-UNIQUE-99",
            alertType = "SOS",
            overrideLocation = Pair(28.6139, 77.2090)
        )
        assertTrue(firstHandled)

        // Rapid duplicate broadcast within 60s -> suppressed, returns false
        val secondHandled = relayManager.handleEmergencyBroadcast(
            broadcastId = "EP-UNIQUE-99",
            alertType = "SOS",
            overrideLocation = Pair(28.6139, 77.2090)
        )
        assertFalse(secondHandled)

        // Database should only have ONE record despite two calls
        val records = relayDao.getAllRecords().first()
        assertEquals(1, records.size)
        assertEquals("EP-UNIQUE-99", records[0].deviceId)
    }
}
