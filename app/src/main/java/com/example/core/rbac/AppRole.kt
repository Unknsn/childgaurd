package com.example.core.rbac

/**
 * Strict Role Definitions for SafeBand Guardian (v3.3)
 * Governs UI navigation, ViewModel execution, and data access.
 */
enum class AppRole {
    PARENT,
    CHILD,
    TRUSTED_GUARDIAN,
    INSTITUTION,
    EMERGENCY_AUTHORITY,
    ANONYMOUS_RELAY;

    val isParent: Boolean get() = this == PARENT
    val isChild: Boolean get() = this == CHILD
    val isGuardian: Boolean get() = this == TRUSTED_GUARDIAN || this == PARENT
}

enum class Permission {
    VIEW_INCIDENTS,
    TRIGGER_SOS,
    MANAGE_CHILDREN,
    MANAGE_SAFE_ZONES,
    MANAGE_SCHOOLS,
    MANAGE_JOURNEYS,
    MANAGE_GUARDIANS,
    APPROVE_PICKUP,
    ACCESS_SETTINGS,
    SWITCH_ROLE,
    VIEW_FULL_BIO
}
