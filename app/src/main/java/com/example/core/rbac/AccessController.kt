package com.example.core.rbac

/**
 * AccessController
 *
 * Enforces role-based authorization rules across:
 * 1. UI Navigation
 * 2. ViewModel execution boundaries
 * 3. Repository data layer operations
 *
 * Disallows unauthorized access (e.g. child accessing parent dashboard,
 * modifying geofences, viewing other children's profiles).
 */
object AccessController {

    private val rolePermissions = mapOf(
        AppRole.PARENT to setOf(
            Permission.VIEW_INCIDENTS,
            Permission.TRIGGER_SOS,
            Permission.MANAGE_CHILDREN,
            Permission.MANAGE_SAFE_ZONES,
            Permission.MANAGE_SCHOOLS,
            Permission.MANAGE_JOURNEYS,
            Permission.MANAGE_GUARDIANS,
            Permission.APPROVE_PICKUP,
            Permission.ACCESS_SETTINGS,
            Permission.SWITCH_ROLE,
            Permission.VIEW_FULL_BIO
        ),
        AppRole.TRUSTED_GUARDIAN to setOf(
            Permission.VIEW_INCIDENTS,
            Permission.TRIGGER_SOS,
            Permission.APPROVE_PICKUP,
            Permission.VIEW_FULL_BIO
        ),
        AppRole.CHILD to setOf(
            Permission.TRIGGER_SOS,
            Permission.VIEW_INCIDENTS // Only self-incidents
        ),
        AppRole.INSTITUTION to setOf(
            Permission.VIEW_INCIDENTS,
            Permission.APPROVE_PICKUP
        ),
        AppRole.EMERGENCY_AUTHORITY to setOf(
            Permission.VIEW_INCIDENTS,
            Permission.VIEW_FULL_BIO
        ),
        AppRole.ANONYMOUS_RELAY to emptySet()
    )

    fun hasPermission(role: AppRole, permission: Permission): Boolean {
        return rolePermissions[role]?.contains(permission) == true
    }

    @Throws(SecurityException::class)
    fun enforce(role: AppRole, permission: Permission, operationDescription: String = "") {
        if (!hasPermission(role, permission)) {
            throw SecurityException(
                "Access Denied: Role $role is not authorized to execute $permission. $operationDescription"
            )
        }
    }
}
