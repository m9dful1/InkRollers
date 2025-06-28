package com.spiritwisestudios.inkrollers

import android.graphics.Color
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Data class representing the state of a player to be synced over Firebase.
 * Uses NORMALIZED maze coordinates (0-1) for position.
 * Use @IgnoreExtraProperties to allow for future flexibility if the database
 * has fields this version of the client doesn't know about.
 * Use default values for Firebase to be able to deserialize missing fields.
 */
@IgnoreExtraProperties
data class PlayerState(
    var normX: Float = 0.5f,    // Normalized X (0.0 to 1.0)
    var normY: Float = 0.5f,    // Normalized Y (0.0 to 1.0)
    var color: Int = Color.GRAY, // Default color
    var mode: Int = 0,          // 0=PAINT, 1=FILL
    var ink: Float = Player.MAX_INK, // Use Player constant for default max ink
    var active: Boolean = true,    // To indicate if player is currently connected
    var playerName: String = "",  // Player name from profile
    var uid: String = ""         // Add UID field
) {
    // No-argument constructor required by Firebase
    constructor() : this(0.5f, 0.5f, Color.GRAY, 0, Player.MAX_INK, true, "", "")
}