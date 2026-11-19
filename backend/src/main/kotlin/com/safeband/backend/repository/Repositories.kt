package com.safeband.backend.repository

import com.safeband.backend.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): Optional<UserEntity>
    fun findByGoogleSub(googleSub: String): Optional<UserEntity>
}

@Repository
interface ChildProfileRepository : JpaRepository<ChildProfileEntity, String> {
    fun findByParentUserId(parentUserId: String): List<ChildProfileEntity>
    fun findByPairedDeviceId(pairedDeviceId: String): Optional<ChildProfileEntity>
    fun findByChildModePhoneId(childModePhoneId: String): Optional<ChildProfileEntity>
}

@Repository
interface DeviceRepository : JpaRepository<DeviceEntity, String> {
    fun findByOwnerParentId(ownerParentId: String): List<DeviceEntity>
    fun findByPairedChildId(pairedChildId: String): Optional<DeviceEntity>
}

@Repository
interface PairingSessionRepository : JpaRepository<PairingSessionEntity, String> {
    fun findByParentUserIdAndStatus(parentUserId: String, status: String): List<PairingSessionEntity>
    fun findByDeviceIdAndStatus(deviceId: String, status: String): Optional<PairingSessionEntity>
}

@Repository
interface LocationRepository : JpaRepository<LocationEntity, String> {
    fun findTopByChildIdOrderByFixTimestampDesc(childId: String): Optional<LocationEntity>
    fun findByChildIdOrderByFixTimestampDesc(childId: String, pageable: Pageable): Page<LocationEntity>
    fun findByChildIdAndFixTimestampBetweenOrderByFixTimestampDesc(
        childId: String,
        from: Instant,
        to: Instant,
        pageable: Pageable
    ): Page<LocationEntity>
}

@Repository
interface GeofenceZoneRepository : JpaRepository<GeofenceZoneEntity, String> {
    fun findByChildId(childId: String): List<GeofenceZoneEntity>
    fun findByChildIdAndIsActiveTrue(childId: String): List<GeofenceZoneEntity>
}

@Repository
interface IncidentRepository : JpaRepository<IncidentEntity, String> {
    fun findByChildId(childId: String): List<IncidentEntity>
    fun findByChildIdAndStatus(childId: String, status: String): List<IncidentEntity>
    fun findByDeviceIdAndStatus(deviceId: String, status: String): List<IncidentEntity>
    fun findByStatusOrderByStartTimestampDesc(status: String): List<IncidentEntity>
}

@Repository
interface RelayObservationRepository : JpaRepository<RelayObservationEntity, String> {
    fun findByIncidentIdOrderByObservedAtDesc(incidentId: String): List<RelayObservationEntity>
    fun existsByIncidentIdAndRelayDeviceIdAndObservedAt(incidentId: String, relayDeviceId: String, observedAt: Instant): Boolean
}

@Repository
interface NotificationRecordRepository : JpaRepository<NotificationRecordEntity, String> {
    fun findByUserIdOrderBySentAtDesc(userId: String): List<NotificationRecordEntity>
}

@Repository
interface AuditLogRepository : JpaRepository<AuditLogEntity, String> {
    fun findByUserIdOrderByTimestampDesc(userId: String, pageable: Pageable): Page<AuditLogEntity>
}
