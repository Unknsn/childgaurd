package com.example.service

import android.location.Location
import android.util.Log
import com.example.data.local.SchoolProfile
import com.example.data.local.SchoolScheduleException
import com.example.data.repository.SafetyRepository
import com.example.model.LocationConfidence
import com.example.model.SchoolStatus
import com.example.model.VerifiedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * School Arrival and Departure Verification System.
 *
 * Implements:
 * - Institutional Safe-Zone verification.
 * - Expected Arrival and Departure time window tracking.
 * - Late arrival warnings on active school days.
 * - Unexpected departure warnings during school hours.
 * - Temporary school holidays and schedule exceptions.
 * - Strict Location Evidence standard: Never confirms arrival if location confidence is insufficient.
 */
class SchoolSafetyManager(
    private val repository: SafetyRepository,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "SchoolSafetyManager"
    }

    private val _schoolStatus = MutableStateFlow(SchoolStatus())
    val schoolStatus: StateFlow<SchoolStatus> = _schoolStatus.asStateFlow()

    private var hasReportedArrivalToday = false
    private var hasReportedDepartureToday = false
    private var hasReportedLateArrivalToday = false
    private var lastRecordedDayOfYear = -1

    /**
     * Evaluates child location against the registered active school profile.
     */
    fun evaluateSchoolLocation(
        verifiedLocation: VerifiedLocation?,
        school: SchoolProfile?,
        exceptions: List<SchoolScheduleException> = emptyList(),
        calendar: Calendar = Calendar.getInstance()
    ) {
        if (school == null || !school.isActive) {
            _schoolStatus.value = SchoolStatus(profile = school)
            return
        }

        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
        if (currentDay != lastRecordedDayOfYear) {
            hasReportedArrivalToday = false
            hasReportedDepartureToday = false
            hasReportedLateArrivalToday = false
            lastRecordedDayOfYear = currentDay
        }

        // Check if today is a scheduled school day (Mon-Sun bitmask)
        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, Tuesday=3, ... Saturday=7
        val dayOfWeekIndex = calendar.get(Calendar.DAY_OF_WEEK)
        val bitShift = when (dayOfWeekIndex) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        val isSchoolDay = (school.schoolDaysMask and (1 shl bitShift)) != 0

        // Check holiday exception
        val epochDay = System.currentTimeMillis() / (24 * 3600 * 1000L)
        val isHoliday = exceptions.any { it.dateEpochDay == epochDay && it.isHoliday }

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val isArrivalWindow = currentMinutes in school.expectedArrivalStartMinutes..school.expectedArrivalEndMinutes
        val isDepartureWindow = currentMinutes in school.expectedDepartureStartMinutes..school.expectedDepartureEndMinutes
        val isSchoolHours = currentMinutes in school.expectedArrivalEndMinutes..school.expectedDepartureStartMinutes

        // Check location evidence
        val distanceToSchool = if (verifiedLocation != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                verifiedLocation.latitude,
                verifiedLocation.longitude,
                school.latitude,
                school.longitude,
                results
            )
            results[0]
        } else {
            Float.MAX_VALUE
        }

        val isInsideZone = distanceToSchool <= school.radiusMeters

        _schoolStatus.value = SchoolStatus(
            profile = school,
            isInsideZone = isInsideZone,
            isArrivalWindowActive = isArrivalWindow,
            isDepartureWindowActive = isDepartureWindow,
            isHolidayToday = isHoliday
        )

        // If today is a holiday or not a school day, skip alerts
        if (isHoliday || !isSchoolDay) {
            return
        }

        // Strict Evidence Requirement: Never confirm arrival if confidence is LOW or UNKNOWN or stale
        val isHighQualityLocation = verifiedLocation != null &&
                (verifiedLocation.confidence == LocationConfidence.HIGH || verifiedLocation.confidence == LocationConfidence.MEDIUM) &&
                !verifiedLocation.isStale()

        // 1. Confirmed Arrival
        if (isInsideZone && isHighQualityLocation && !hasReportedArrivalToday) {
            hasReportedArrivalToday = true
            scope.launch {
                repository.logSchoolVerification(
                    schoolId = school.schoolId,
                    eventType = "ARRIVAL_CONFIRMED",
                    confidence = verifiedLocation?.confidence?.displayName ?: "HIGH",
                    details = "Child arrived safely at ${school.schoolName} (${distanceToSchool.toInt()}m from center)"
                )
            }
        }

        // 2. Late Arrival Warning (Arrival window ended, child not in school, and never arrived today)
        if (!hasReportedArrivalToday && !hasReportedLateArrivalToday && currentMinutes > school.expectedArrivalEndMinutes && currentMinutes < school.expectedDepartureStartMinutes) {
            hasReportedLateArrivalToday = true
            scope.launch {
                repository.logSchoolVerification(
                    schoolId = school.schoolId,
                    eventType = "LATE_ARRIVAL_WARNING",
                    confidence = verifiedLocation?.confidence?.displayName ?: "UNKNOWN",
                    details = "Expected arrival at ${school.schoolName} ended at ${formatMinutes(school.expectedArrivalEndMinutes)}. Child not detected in school zone."
                )
            }
        }

        // 3. Unexpected Departure Alert (Departed school during school hours after having arrived)
        if (hasReportedArrivalToday && !isInsideZone && isSchoolHours && isHighQualityLocation && !hasReportedDepartureToday) {
            scope.launch {
                repository.logSchoolVerification(
                    schoolId = school.schoolId,
                    eventType = "UNEXPECTED_DEPARTURE_ALERT",
                    confidence = verifiedLocation?.confidence?.displayName ?: "HIGH",
                    details = "Unexpected departure from ${school.schoolName} during school hours (${distanceToSchool.toInt()}m away)"
                )
            }
        }

        // 4. Expected Departure Notification
        if (hasReportedArrivalToday && !isInsideZone && isDepartureWindow && !hasReportedDepartureToday) {
            hasReportedDepartureToday = true
            scope.launch {
                repository.logSchoolVerification(
                    schoolId = school.schoolId,
                    eventType = "DEPARTURE_CONFIRMED",
                    confidence = verifiedLocation?.confidence?.displayName ?: "HIGH",
                    details = "Child safely departed ${school.schoolName} during regular dismissal window"
                )
            }
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }
}

