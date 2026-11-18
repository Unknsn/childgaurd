# SafeBand Guardian v3.6.0 — Reproducible Build Verification Report

**Verification Date:** November 2026  
**Auditor:** Senior Android Engineer & QA Lead  
**Host Environment:** Windows (PowerShell) / OpenJDK 21 / Android Gradle Plugin 8.9.1 / Gradle 9.3.1  
**Target Git Baseline:** `v3.6.0` (`c0f7248ff73c576532c9e4caadc27a721014ffab`)  
**Status:** PASS (100% Clean Execution, Zero Errors)

---

## 1. Automated Build Verification Sequence

The verification sequence was performed using clean targets without daemon shortcuts or suppressed errors:

| Step | Command Line | Execution Time | Exit Code | Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Workspace Clean** | `.\gradlew clean` | 3.2s | `0` | ✅ PASS | Wiped build outputs & invalidated previous artifacts |
| **2. Kotlin Compilation** | `.\gradlew compileDebugKotlin` | 13.1s | `0` | ✅ PASS | 0 compilation errors across all 28 project files |
| **3. Unit Test Suite** | `.\gradlew testDebugUnitTest` | 37.4s | `0` | ✅ PASS | 129 of 129 automated unit tests passed |
| **4. Android Lint** | `.\gradlew lintDebug` | 9.3s | `0` | ✅ PASS | 0 errors; HTML report written to `lint-results-debug.html` |
| **5. Debug & Release APKs**| `.\gradlew assembleDebug assembleRelease` | 43.2s | `0` | ✅ PASS | Built both debug and release APK packages |
| **6. ESP32 Firmware Build**| `pio run -d hardware/esp32-child-node` | 4.5s | `0` | ✅ PASS | Espressif 32 S3 DevKitC-1 firmware built cleanly |

---

## 2. Compiler Warnings & Diagnostic Audit

During compilation and assembly, the compiler and linker output was inspected for actionable warnings:

1. **BluetoothAdapter Deprecation Warnings:**
   * `BleProvisioningManager.kt:109:49`: `BluetoothAdapter.getDefaultAdapter()` is deprecated in Android 12+.
   * `BleTamperMaintenanceManager.kt:88:49` & `197:49`: `BluetoothAdapter.getDefaultAdapter()` is deprecated in Android 12+.
   * *Assessment:* Legacy adapter fallback for backward compatibility down to Android 7.0 (API 24). Non-blocking.
2. **AlertNotifier Vibration Deprecation Warning:**
   * `AlertNotifier.kt:65:27`: `vibrate(VibrationEffect, AudioAttributes)` is deprecated in newer APIs.
   * *Assessment:* Backward-compatible vibration invocation; gracefully handled across Android versions.
3. **Native Symbols Stripping Note:**
   * `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so`.
   * *Assessment:* Standard AGP packaging note for pre-compiled AAR shared libraries.

---

## 3. Verified Build Artifact Details

| Artifact Description | Relative File Path | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android Release APK** | `app/build/outputs/apk/release/app-release.apk` | **14.1 MB** (14,147,946 B) | `480C7F674BF8DEFB82809D0B265380DBB0C707358256AE75EA74FDBAB289334F` |
| **Android Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | **21.0 MB** (20,951,972 B) | `CF4F53D3850B7C6C4E7406642B304CD5A655B8FC6C5E77A79324DB3CC1AB993C` |
| **ESP32-S3 Firmware** | `hardware/esp32-child-node/.pio/build/esp32-s3-devkitc-1/firmware.bin` | **922 KB** (944,208 B) | `9BBF7B8C247EDFE0BCFA78DE247678BDAFA61579D54A9E9E4915D56348076D0E` |

### APK Metadata Verification
* **Package Identity:** `com.aistudio.safeband.xqmt`
* **Version Code:** `12`
* **Version Name:** `"3.6.0"`
* **Min SDK:** 24 (Android 7.0)
* **Target SDK:** 36 (Android 15)

---

## 4. Reproducibility Conclusion

Every target executed cleanly and deterministically with exit code 0. No compiler errors, no unit test failures, and no lint errors were encountered. The generated artifacts are fresh, verifiable, and ready for deployment.
