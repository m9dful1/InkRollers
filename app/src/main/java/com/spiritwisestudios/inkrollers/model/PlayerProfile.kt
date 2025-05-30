package com.spiritwisestudios.inkrollers.model

object PlayerColorPalette {
    val COLORS = listOf(
        0xFF39FF14.toInt(), // Neon Green
        0xFF1F51FF.toInt(), // Neon Blue
        0xFFFF3EC8.toInt(), // Neon Pink
        0xFFFF9900.toInt(), // Neon Orange
        0xFFFFFF33.toInt(), // Neon Yellow
        0xFFFF3131.toInt(), // Neon Red
        0xFFB026FF.toInt(), // Neon Purple
        0xFF00FFF7.toInt()  // Neon Cyan
    )
}

data class PlayerProfile(
    var uid: String = "",
    var playerName: String = "",
    var favoriteColors: List<Int> = listOf(), // Must be 3 distinct colors from palette
    var catchPhrase: String = "",
    var friendCode: String = "",
    var friends: List<String> = listOf(),
    var winCount: Int = 0,
    var lossCount: Int = 0,
    var isOnline: Boolean = false // New field for online status
) {
    fun isValidColorSelection(): Boolean {
        return favoriteColors.size == 3 &&
            favoriteColors.all { it in PlayerColorPalette.COLORS } &&
            favoriteColors.toSet().size == 3
    }
    
    companion object {
        /**
         * Static method to validate color selection for testing purposes
         */
        fun isValidColorSelection(colors: List<Int>): Boolean {
            return colors.size == 3 &&
                colors.all { it in PlayerColorPalette.COLORS } &&
                colors.toSet().size == 3
        }
    }
} 