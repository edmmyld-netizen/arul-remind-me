package com.arulsundaresan.arulremindme.domain.model

/**
 * Recurrence support is a Session 4 feature, but the field lives in the schema from v1 so
 * that adding it later needs no destructive migration.
 */
enum class RepeatMode {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        fun fromStorage(value: String?): RepeatMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}
