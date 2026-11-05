package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.AppReleaseMetadata
import com.example.model.UpdateInfo
import com.example.model.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * SafeBand Guardian Automatic Update Manager.
 *
 * Implements:
 * - Version Checking against GitHub Releases / Distribution Source.
 * - Non-intrusive in-app update notification & reminder handling.
 * - Secure HTTPS APK download with chunked progress dispatch.
 * - Post-download verification: Checksum, Package Identity, Signing Certificate.
 * - Android standard Package Installer Intent invocation via FileProvider.
 * - Seamless preservation of user data, preferences, paired devices, and Room DB.
 */
class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val updateSource: AppUpdateSource = GitHubReleaseUpdateSource()
) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val DEFAULT_REMINDER_HOURS = 24
    }

    private val currentVersionName: String
    private val currentVersionCode: Int

    init {
        val (vName, vCode) = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            Pair(pInfo.versionName ?: "1.1.0", code)
        } catch (e: Exception) {
            Pair("1.1.0", 5)
        }
        currentVersionName = vName
        currentVersionCode = vCode
    }

    private val _updateInfo = MutableStateFlow(
        UpdateInfo(
            state = UpdateState.CURRENT,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode
        )
    )
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * Checks for newer releases.
     * @param isManual true if triggered by user tap on "Check for updates" button.
     */
    fun checkForUpdates(isManual: Boolean = false) {
        val now = System.currentTimeMillis()
        val currentInfo = _updateInfo.value

        // Suppress background nag if user chose "Remind me later" within reminder window
        if (!isManual && now < currentInfo.updateRemindLaterTimestamp && currentInfo.availableRelease?.isMandatory != true) {
            return
        }

        scope.launch {
            val result = updateSource.checkLatestRelease()
            result.fold(
                onSuccess = { release ->
                    val isNewer = release.versionCode > currentVersionCode
                    if (isNewer) {
                        _updateInfo.value = _updateInfo.value.copy(
                            state = UpdateState.UPDATE_AVAILABLE,
                            availableRelease = release,
                            errorMessage = null,
                            lastCheckedTimestamp = now
                        )
                    } else {
                        _updateInfo.value = _updateInfo.value.copy(
                            state = UpdateState.UP_TO_DATE,
                            availableRelease = release,
                            errorMessage = null,
                            lastCheckedTimestamp = now
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Update check failed: ${error.message}")
                    if (isManual) {
                        _updateInfo.value = _updateInfo.value.copy(
                            state = UpdateState.UPDATE_ERROR,
                            errorMessage = "Unable to check for updates: ${error.localizedMessage ?: "Network error"}",
                            lastCheckedTimestamp = now
                        )
                    }
                }
            )
        }
    }

    /**
     * Downloads the APK securely over HTTPS to the application updates cache.
     */
    fun downloadUpdate(release: AppReleaseMetadata) {
        if (release.apkDownloadUrl.isBlank()) {
            _updateInfo.value = _updateInfo.value.copy(
                state = UpdateState.UPDATE_ERROR,
                errorMessage = "No APK download URL provided for release ${release.versionName}"
            )
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            _updateInfo.value = _updateInfo.value.copy(
                state = UpdateState.DOWNLOADING,
                downloadProgressPercent = 0,
                errorMessage = null
            )

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "safeband-v${release.versionName}.apk")

            try {
                val url = URL(release.apkDownloadUrl)
                if (!url.protocol.equals("https", ignoreCase = true)) {
                    throw SecurityException("Insecure HTTP download rejected. HTTPS required.")
                }

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "SafeBand-Guardian-Android")
                }

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Download server returned HTTP ${connection.responseCode}")
                }

                val rawLength = connection.contentLength
                val contentLength: Long = if (rawLength > 0) rawLength.toLong() else release.apkSize
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            if (contentLength > 0) {
                                val progress = ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 99)
                                _updateInfo.value = _updateInfo.value.copy(downloadProgressPercent = progress)
                            }
                        }
                    }
                }

                _updateInfo.value = _updateInfo.value.copy(downloadProgressPercent = 100)

                // Perform security verification
                val isVerified = verifyApk(targetFile, release)
                if (isVerified) {
                    _updateInfo.value = _updateInfo.value.copy(
                        state = UpdateState.DOWNLOADED,
                        downloadedApkPath = targetFile.absolutePath,
                        errorMessage = null
                    )
                } else {
                    targetFile.delete()
                    _updateInfo.value = _updateInfo.value.copy(
                        state = UpdateState.UPDATE_ERROR,
                        errorMessage = "Downloaded update failed security integrity verification."
                    )
                }
            } catch (e: Exception) {
                targetFile.delete()
                Log.e(TAG, "Download failed: ${e.message}", e)
                _updateInfo.value = _updateInfo.value.copy(
                    state = UpdateState.UPDATE_ERROR,
                    errorMessage = "Download failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Verifies downloaded APK with checksum, package ID, and certificates.
     */
    fun verifyApk(file: File, release: AppReleaseMetadata): Boolean {
        // 1. Checksum validation
        if (release.sha256Checksum.isNotBlank()) {
            if (!ApkVerifier.verifyChecksum(file, release.sha256Checksum)) {
                Log.e(TAG, "Security error: SHA-256 verification failed")
                return false
            }
        }

        // 2. Package identity verification
        if (!ApkVerifier.verifyPackageIdentity(context, file)) {
            Log.e(TAG, "Security error: Package identity verification failed")
            return false
        }

        // 3. Signing certificate verification
        if (!ApkVerifier.verifySigningCertificateCompatibility(context, file)) {
            Log.e(TAG, "Security error: Signing certificate verification failed")
            return false
        }

        return true
    }

    /**
     * Builds the Intent to start the package installation.
     * Uses Android FileProvider to grant secure, temporary read permission.
     */
    fun createInstallIntent(apkFile: File): Intent {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    /**
     * Checks whether installation from unknown sources is permitted.
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Returns Intent to prompt user for "Install Unknown Apps" permission.
     */
    fun createManageUnknownAppSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    /**
     * Delays update notifications for non-mandatory releases.
     */
    fun remindLater(hours: Int = DEFAULT_REMINDER_HOURS) {
        val reminderTime = System.currentTimeMillis() + (hours * 3600 * 1000L)
        _updateInfo.value = _updateInfo.value.copy(
            state = UpdateState.CURRENT,
            updateRemindLaterTimestamp = reminderTime
        )
    }

    fun dismissError() {
        _updateInfo.value = _updateInfo.value.copy(
            state = UpdateState.CURRENT,
            errorMessage = null
        )
    }

    fun resetToCurrent() {
        _updateInfo.value = _updateInfo.value.copy(state = UpdateState.CURRENT)
    }
}

