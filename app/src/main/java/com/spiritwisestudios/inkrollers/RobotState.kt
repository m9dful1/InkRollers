package com.spiritwisestudios.inkrollers

import android.graphics.Color

/**
 * Data class representing the state of a robot for Firebase synchronization
 */
data class RobotState(
    var id: String = "",            // Unique identifier for this robot
    var normX: Float = 0.5f,        // Normalized X position (0.0 to 1.0)
    var normY: Float = 0.5f,        // Normalized Y position (0.0 to 1.0)
    var isConverted: Boolean = false, // Whether robot has been converted by a player
    var paintColor: Int = Color.GRAY, // Current paint color of the robot - default to gray for unconverted
    var isActive: Boolean = true,    // Whether robot is currently active
    var spawnerIndex: Int = 0,       // Index of the spawner that created this robot
    var conversionProgress: Float = 0.0f,
    /** Epoch milliseconds when this RobotState was last written. Used for conflict resolution. */
    var lastUpdated: Long = 0L,
    /** Flag to differentiate between position updates and conversion updates */
    var updateType: String = "position", // "position" or "conversion"
    /** Flag to indicate this update should ignore conversion progress (position-only) */
    var ignoreConversionProgress: Boolean = false
) {
    // No-argument constructor required by Firebase
    constructor() : this("", 0.5f, 0.5f, false, Color.GRAY, true, 0, 0.0f, 0L, "position", false)
}