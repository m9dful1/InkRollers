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
    var paintColor: Int = Color.GREEN, // Current paint color of the robot
    var isActive: Boolean = true,    // Whether robot is currently active
    var spawnerIndex: Int = 0        // Index of the spawner that created this robot
) {
    // No-argument constructor required by Firebase
    constructor() : this("", 0.5f, 0.5f, false, Color.GREEN, true, 0)
}