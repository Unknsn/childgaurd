package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChildSpecificContact
import com.example.data.local.PairedChild
import com.example.data.local.PlaybookActionLog
import com.example.data.local.SafeBandDatabase
import com.example.model.ChildBioProfile
import com.example.service.BleSafetyManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiChildAndProfileIsolationTest {

    private lateinit var context: Context
    private lateinit var db: SafeBandDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(
                SafeBandDatabase.MIGRATION_1_2,
                SafeBandDatabase.MIGRATION_2_3,
                SafeBandDatabase.MIGRATION_3_4
            )
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPairedChildDaoInsertAndLookup() = runBlocking {
        val child = PairedChild(
            deviceId = "SB-77A1",
            childName = "Aarav Sharma",
            age = "9",
            bloodType = "B+",
            primaryGuardianPhone = "+91 98765 43210",
            secondaryGuardianPhone = "+91 98765 12345",
            medicalConditions = "Asthma (Carries Inhaler)",
            allergies = "Peanuts, Dust",
            emergencyNotes = "Wears SafeBand at all times"
        )

        val rowId = db.pairedChildDao().insertOrUpdateChild(child)
        assertTrue(rowId > 0)

        val retrieved = db.pairedChildDao().findChildByDeviceId("SB-77A1")
        assertNotNull(retrieved)
        assertEquals("Aarav Sharma", retrieved?.childName)
        assertEquals("B+", retrieved?.bloodType)
        assertEquals("+91 98765 43210", retrieved?.primaryGuardianPhone)

        val nonExistent = db.pairedChildDao().findChildByDeviceId("UNKNOWN-ID")
        assertNull(nonExistent)
    }

    @Test
    fun testChildSpecificContactsIsolation() = runBlocking {
        val contactChild1 = ChildSpecificContact(
            childDeviceId = "SB-001",
            contactName = "Mom",
            phoneNumber = "1111111111",
            relationship = "Mother"
        )
        val contactChild2 = ChildSpecificContact(
            childDeviceId = "SB-002",
            contactName = "Uncle",
            phoneNumber = "2222222222",
            relationship = "Uncle"
        )

        db.pairedChildDao().insertContact(contactChild1)
        db.pairedChildDao().insertContact(contactChild2)

        val child1Contacts = db.pairedChildDao().getContactsSnapshotForChild("SB-001")
        assertEquals(1, child1Contacts.size)
        assertEquals("Mom", child1Contacts[0].contactName)

        val child2Contacts = db.pairedChildDao().getContactsSnapshotForChild("SB-002")
        assertEquals(1, child2Contacts.size)
        assertEquals("Uncle", child2Contacts[0].contactName)
    }

    @Test
    fun testBioCoreEncodingAndDecoding() {
        val bio = ChildBioProfile(
            childName = "Rohan",
            age = "10",
            bloodType = "O+",
            primaryParentPhone = "+1555123456"
        )

        val encoded = BleSafetyManager.encodeBioCorePayload("SB-100", bio)
        assertNotNull(encoded)
        assertEquals(22, encoded.size)

        val decoded = BleSafetyManager.decodePacket(encoded)
        assertNotNull(decoded)
        assertTrue(decoded is BleSafetyManager.Companion.DecodedPacket.BioCore)

        val bioCore = decoded as BleSafetyManager.Companion.DecodedPacket.BioCore
        assertEquals("SB-100", bioCore.deviceId)
        assertEquals("Rohan", bioCore.childName)
        assertEquals("10", bioCore.age)
        assertEquals("O+", bioCore.bloodType)
    }

    @Test
    fun testPlaybookActionLogging() = runBlocking {
        val log = PlaybookActionLog(
            incidentId = "INC-1234",
            stepNumber = 1,
            stepTitle = "Immediate Voice Triage",
            actionSummary = "Called primary parent (+91 98765 43210)",
            actor = "Primary Parent"
        )

        val rowId = db.playbookDao().insertPlaybookLog(log)
        assertTrue(rowId > 0)
    }
}

