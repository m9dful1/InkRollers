package com.spiritwisestudios.inkrollers.items

/**
 * Enum defining all available item types in the game
 */
enum class ItemType(
    val displayName: String,
    val description: String,
    val iconResource: String? = null // Will be used for UI display
) {
    INK_REFILL("Ink Refill", "Refills your ink tank"),
    SPEED_BOOST("Speed Boost", "Temporarily increases movement speed"),
    PAINT_MULTIPLIER("Paint Multiplier", "Increases paint coverage area"),
    SHIELD("Shield", "Provides temporary protection from robot attacks"),
    FREEZE("Freeze", "Temporarily stops all robots"),
    TELEPORT("Teleport", "Instantly moves to a random safe location");
    
    companion object {
        /**
         * Get all available item types as a list
         */
        fun getAllTypes(): List<ItemType> = values().toList()
    }
} 