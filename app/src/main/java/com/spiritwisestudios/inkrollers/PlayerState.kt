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
    var color: Int = Color.GRAY,
    var mode: Int = 0,          // PAINT=0, FILL=1
    var ink: Float = Player.MAX_INK,
    var active: Boolean = true,
    var mazeSeed: Long = 0,
    var playerName: String = "",
    var uid: String = ""
) {
    /** No-argument constructor required by Firebase deserialization. */
    constructor() : this(0.5f, 0.5f, Color.GRAY, 0, Player.MAX_INK, true, 0, "", "")
}