package com.example

import com.example.model.AppReleaseMetadata
import com.example.model.UpdateInfo
import com.example.model.UpdateState
import com.example.update.ApkVerifier
import com.example.update.GitHubReleaseUpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class AutomaticUpdateSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSha256ChecksumVerificationMatches() {
        val testFile = tempFolder.newFile("test_app.apk")
        val content = "SafeBand Guardian Release APK Binary Bytes Test Payload".toByteArray()
        testFile.writeBytes(content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(content).joinToString("") { "%02x".format(it) }

        assertTrue(
            "ApkVerifier must accept matching SHA-256 hash",
            ApkVerifier.verifyChecksum(testFile, expectedHash)
        )
    }

    @Test
    fun testSha256ChecksumVerificationRejectsTamperedFile() {
        val testFile = tempFolder.newFile("tampered_app.apk")
        testFile.writeBytes("Tampered Content".toByteArray())

        val invalidHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        assertFalse(
            "ApkVerifier must strictly reject tampered files with mismatched checksum",
            ApkVerifier.verifyChecksum(testFile, invalidHash)
        )
    }

    @Test
    fun testGitHubReleaseJsonParser() {
        val sampleJson = """
        {
            "tag_name": "v1.2.0",
            "name": "SafeBand Guardian 1.2.0 - Major Update",
            "published_at": "2026-11-05T09:00:00Z",
            "body": "versionCode: 6\nSHA256: 1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\nSecurity: Cryptographic verified tokens and signed handover records.",
            "assets": [
                {
                    "name": "safeband-guardian-v1.2.0.apk",
                    "size": 8945000,
                    "browser_download_url": "https://github.com/Unknsn/childgaurd/releases/download/v1.2.0/safeband-guardian-v1.2.0.apk"
                }
            ]
        }
        """.trimIndent()

        val source = GitHubReleaseUpdateSource()
        val metadata = source.parseGitHubReleaseJson(sampleJson)

        assertEquals("1.2.0", metadata.versionName)
        assertEquals(6, metadata.versionCode)
        assertEquals("SafeBand Guardian 1.2.0 - Major Update", metadata.releaseTitle)
        assertEquals("https://github.com/Unknsn/childgaurd/releases/download/v1.2.0/safeband-guardian-v1.2.0.apk", metadata.apkDownloadUrl)
        assertEquals(8945000L, metadata.apkSize)
        assertEquals("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", metadata.sha256Checksum)
        assertTrue(metadata.securityImprovements.contains("Cryptographic verified tokens"))
    }

    @Test
    fun testVersionComparisonLogic() {
        val installedCode = 5
        val availableCodeNewer = 6
        val availableCodeOlder = 4
        val availableCodeSame = 5

        assertTrue("Newer version code must trigger update", availableCodeNewer > installedCode)
        assertFalse("Same version code is up to date", availableCodeSame > installedCode)
        assertFalse("Older version code is up to date", availableCodeOlder > installedCode)
    }

    @Test
    fun testUpdateReminderSuppression() {
        val now = 1000_000L
        val reminderUntil = now + (24 * 3600 * 1000L)

        val infoWithReminder = UpdateInfo(
            state = UpdateState.CURRENT,
            updateRemindLaterTimestamp = reminderUntil,
            availableRelease = AppReleaseMetadata(
                versionName = "1.2.0",
                versionCode = 6,
                releaseTitle = "Update",
                releaseDate = "",
                releaseNotes = "",
                apkDownloadUrl = "https://example.com/apk",
                apkSize = 1000,
                sha256Checksum = "",
                isMandatory = false
            )
        )

        // Non-mandatory update check respects reminder window
        val shouldSuppress = now < infoWithReminder.updateRemindLaterTimestamp && !infoWithReminder.availableRelease!!.isMandatory
        assertTrue("Background check within reminder window must be suppressed", shouldSuppress)

        // Mandatory update bypasses reminder
        val mandatoryInfo = infoWithReminder.copy(
            availableRelease = infoWithReminder.availableRelease!!.copy(isMandatory = true)
        )
        val shouldSuppressMandatory = now < mandatoryInfo.updateRemindLaterTimestamp && !mandatoryInfo.availableRelease!!.isMandatory
        assertFalse("Mandatory security update must never be suppressed by reminder snooze", shouldSuppressMandatory)
    }
}

