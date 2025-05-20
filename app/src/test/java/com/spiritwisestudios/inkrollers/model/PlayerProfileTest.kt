import com.spiritwisestudios.inkrollers.model.PlayerColorPalette
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerProfileTest {
    @Test
    fun `isValidColorSelection returns true with three distinct valid colors`() {
        val colors = PlayerColorPalette.COLORS.take(3)
        val profile = PlayerProfile(favoriteColors = colors)
        assertTrue(profile.isValidColorSelection())
    }

    @Test
    fun `isValidColorSelection returns false with duplicate colors`() {
        val color = PlayerColorPalette.COLORS.first()
        val colors = listOf(color, color, PlayerColorPalette.COLORS[1])
        val profile = PlayerProfile(favoriteColors = colors)
        assertFalse(profile.isValidColorSelection())
    }

    @Test
    fun `isValidColorSelection returns false with an invalid color`() {
        val invalidColor = 0x000000
        val colors = listOf(PlayerColorPalette.COLORS[0], PlayerColorPalette.COLORS[1], invalidColor)
        val profile = PlayerProfile(favoriteColors = colors)
        assertFalse(profile.isValidColorSelection())
    }
}
