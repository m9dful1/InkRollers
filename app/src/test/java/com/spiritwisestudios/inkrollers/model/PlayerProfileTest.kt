package com.spiritwisestudios.inkrollers.model

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import android.graphics.Color

class PlayerProfileTest {
    
    private lateinit var testProfile: PlayerProfile
    
    @Before
    fun setUp() {
        testProfile = PlayerProfile(
            uid = "test_uid_123",
            playerName = "TestPlayer",
            favoriteColors = listOf(
                PlayerColorPalette.COLORS[0], // Neon Green
                PlayerColorPalette.COLORS[1], // Neon Blue  
                PlayerColorPalette.COLORS[2]  // Neon Pink
            ),
            catchPhrase = "Test phrase",
            friendCode = "ABC123",
            friends = listOf("friend1", "friend2"),
            winCount = 5,
            lossCount = 3,
            isOnline = true
        )
    }

    @Test
    fun isValidColorSelection_withUniqueColors_returnsTrue() {
        val uniqueColors = listOf(
            PlayerColorPalette.COLORS[0], // Neon Green
            PlayerColorPalette.COLORS[1], // Neon Blue
            PlayerColorPalette.COLORS[2]  // Neon Pink
        )
        assertTrue("Unique colors should be valid", PlayerProfile.isValidColorSelection(uniqueColors))
    }

    @Test
    fun isValidColorSelection_withDuplicateColors_returnsFalse() {
        val duplicateColors = listOf(
            PlayerColorPalette.COLORS[0], // Neon Green
            PlayerColorPalette.COLORS[0], // Neon Green (duplicate)
            PlayerColorPalette.COLORS[1]  // Neon Blue
        )
        assertFalse("Duplicate colors should be invalid", PlayerProfile.isValidColorSelection(duplicateColors))
    }

    @Test
    fun isValidColorSelection_withLessThanThreeColors_returnsFalse() {
        val twoColors = listOf(
            PlayerColorPalette.COLORS[0], // Neon Green
            PlayerColorPalette.COLORS[1]  // Neon Blue
        )
        assertFalse("Less than 3 colors should be invalid", PlayerProfile.isValidColorSelection(twoColors))
    }

    @Test
    fun isValidColorSelection_withMoreThanThreeColors_returnsFalse() {
        val fourColors = listOf(
            PlayerColorPalette.COLORS[0], // Neon Green
            PlayerColorPalette.COLORS[1], // Neon Blue
            PlayerColorPalette.COLORS[2], // Neon Pink
            PlayerColorPalette.COLORS[3]  // Neon Orange
        )
        assertFalse("More than 3 colors should be invalid", PlayerProfile.isValidColorSelection(fourColors))
    }

    @Test
    fun isValidColorSelection_withEmptyList_returnsFalse() {
        val emptyColors = emptyList<Int>()
        assertFalse("Empty color list should be invalid", PlayerProfile.isValidColorSelection(emptyColors))
    }

    @Test
    fun isValidColorSelection_withInvalidColors_returnsFalse() {
        val invalidColors = listOf(
            Color.RED,   // Not in palette
            Color.BLUE,  // Not in palette
            Color.GREEN  // Not in palette
        )
        assertFalse("Colors not in palette should be invalid", PlayerProfile.isValidColorSelection(invalidColors))
    }

    @Test
    fun profileCreation_withValidData_succeeds() {
        assertNotNull("Profile should be created successfully", testProfile)
        assertEquals("UID should match", "test_uid_123", testProfile.uid)
        assertEquals("Player name should match", "TestPlayer", testProfile.playerName)
        assertEquals("Friend code should match", "ABC123", testProfile.friendCode)
    }

    @Test
    fun profileCopy_withUpdatedColors_maintainsOtherFields() {
        val newColors = listOf(
            PlayerColorPalette.COLORS[3], // Neon Orange
            PlayerColorPalette.COLORS[4], // Neon Yellow
            PlayerColorPalette.COLORS[5]  // Neon Red
        )
        val updatedProfile = testProfile.copy(favoriteColors = newColors)
        
        assertEquals("Colors should be updated", newColors, updatedProfile.favoriteColors)
        assertEquals("UID should remain same", testProfile.uid, updatedProfile.uid)
        assertEquals("Name should remain same", testProfile.playerName, updatedProfile.playerName)
        assertEquals("Win count should remain same", testProfile.winCount, updatedProfile.winCount)
    }

    @Test
    fun friendCodeValidation_withValidCode_passes() {
        val validCode = "ABC123"
        assertTrue("Valid friend code format", validCode.length == 6)
        assertTrue("Friend code should be alphanumeric", validCode.all { it.isLetterOrDigit() })
    }

    @Test
    fun profileStats_calculation_isCorrect() {
        val totalGames = testProfile.winCount + testProfile.lossCount
        assertEquals("Total games should be 8", 8, totalGames)
        
        val winRate = testProfile.winCount.toFloat() / totalGames
        assertEquals("Win rate should be 0.625", 0.625f, winRate, 0.001f)
    }

    @Test
    fun profileSerialization_preservesData() {
        // Test that all fields are properly accessible for Firebase serialization
        assertNotNull("UID should not be null", testProfile.uid)
        assertNotNull("Player name should not be null", testProfile.playerName)
        assertNotNull("Favorite colors should not be null", testProfile.favoriteColors)
        assertNotNull("Friend code should not be null", testProfile.friendCode)
        assertNotNull("Friends list should not be null", testProfile.friends)
        
        // Test default constructor exists for Firebase
        val defaultProfile = PlayerProfile()
        assertNotNull("Default constructor should work", defaultProfile)
    }

    @Test
    fun instanceMethod_isValidColorSelection_worksCorrectly() {
        // Test the instance method as well
        assertTrue("Test profile should have valid color selection", testProfile.isValidColorSelection())
        
        // Test with invalid profile
        val invalidProfile = PlayerProfile(
            favoriteColors = listOf(Color.RED, Color.BLUE) // Only 2 colors, not from palette
        )
        assertFalse("Invalid profile should fail validation", invalidProfile.isValidColorSelection())
    }
}
