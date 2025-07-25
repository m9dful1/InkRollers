package com.spiritwisestudios.inkrollers

import android.graphics.Color

/**
 * Data class representing the state of a robot spawner for Firebase synchronization
 */
data class RobotSpawnerState(
    var normX: Float = 0.5f,        // Normalized X position (0.0 to 1.0)
    var normY: Float = 0.5f,        // Normalized Y position (0.0 to 1.0)
    var isConverted: Boolean = false, // Whether spawner has been converted by a player
    var playerColor: Int = Color.GREEN, // Color of the player who converted it
    var isActive: Boolean = true,    // Whether spawner is currently active
    var spawnedRobotCount: Int = 0,  // Number of robots currently spawned
    /** Epoch milliseconds when this RobotSpawnerState was last written. Used for conflict resolution. */
    var lastUpdated: Long = 0L,
    /** Flag to differentiate between status updates and conversion updates */
    var updateType: String = "status", // "status" or "conversion"
    /** Flag to indicate this update should ignore conversion progress (status-only) */
    var ignoreConversionProgress: Boolean = false
) {
    // No-argument constructor required by Firebase
    constructor() : this(0.5f, 0.5f, false, Color.GREEN, true, 0, 0L, "status", false)
}