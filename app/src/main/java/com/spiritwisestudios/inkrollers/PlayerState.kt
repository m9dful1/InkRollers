package com.spiritwisestudios.inkrollers

import android.graphics.Color
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Represents player state synchronized across all clients via Firebase Realtime Database.
 * 
 * Uses normalized coordinates (0.0-1.0) to ensure consistent positioning across different
 * device screen sizes. All fields have default values to support Firebase deserialization
 * and provide fallbacks for missing data.
 * 
 * @IgnoreExtraProperties allows forward compatibility with future database schema changes.
 */
@IgnoreExtraProperties
data class PlayerState(
    var normX: Float = 0.5f,    // Normalized X (0.0 to 1.0)
    var normY: Float = 0.5f,    // Normalized Y (0.0 to 1.0)
    var color: Int = Color.GRAY, // Default color
    var mode: Int = 0,          // 0=PAINT, 1=FILL
    var ink: Float = Player.MAX_INK, // Use Player constant for default max ink
    var active: Boolean = true,    // Player connection status
    var mazeSeed: Long = 0,        // Ensures synchronized maze generation across clients
    var playerName: String = "",  // Player name from profile
    var uid: String = ""         // Firebase Auth UID for profile association
) {
    /** No-argument constructor required by Firebase deserialization. */
    constructor() : this(0.5f, 0.5f, Color.GRAY, 0, Player.MAX_INK, true, 0, "", "")
}