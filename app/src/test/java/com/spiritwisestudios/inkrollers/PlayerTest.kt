package com.spiritwisestudios.inkrollers

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito

class PlayerTest {

    private lateinit var player: Player
    private lateinit var mockPaintSurface: PaintSurface
    private lateinit var mockLevel: Level
    // private lateinit var mockMultiplayerManager: MultiplayerManager // For later tests

    @Before
    fun setUp() {
        mockPaintSurface = Mockito.mock(PaintSurface::class.java)
        Mockito.`when`(mockPaintSurface.w).thenReturn(800)
        Mockito.`when`(mockPaintSurface.h).thenReturn(600)

        // Initialize mockLevel
        mockLevel = Mockito.mock(Level::class.java)

        // Create player instance with the mocked Level
        player = Player(
            surface = mockPaintSurface,
            startX = 100f,
            startY = 100f,
            playerColor = 0, // Dummy color, not relevant for these initial tests
            level = mockLevel, // Pass the mocked level here
            multiplayerManager = null // Explicitly null for now
        )
    }

    @Test
    fun toggleMode_switchesModeCorrectly() {
        assertEquals("Initial mode should be 0 (PAINT)", 0, player.mode)
        player.toggleMode()
        assertEquals("Mode should be 1 (FILL) after first toggle", 1, player.mode)
        player.toggleMode()
        assertEquals("Mode should be 0 (PAINT) after second toggle", 0, player.mode)
    }

    @Test
    fun getModeText_returnsCorrectTextForMode() {
        player.mode = 0 // Set to PAINT mode
        assertEquals("PAINT should be returned for mode 0", "PAINT", player.getModeText()) 

        player.mode = 1 // Set to FILL mode
        assertEquals("FILL should be returned for mode 1", "FILL", player.getModeText()) 
    }

    @Test
    fun inkDecreases_whenPainting() {
        player.mode = 0 // PAINT mode
        val initialInk = player.ink
        player.move(dirX = 1f, dirY = 0f, magnitude = 1f, deltaTime = 0.1f)
        assertTrue("Ink should decrease when painting", player.ink < initialInk)
        assertEquals("Ink should decrease by PAINT_COST", initialInk - Player.PAINT_COST, player.ink, 0.001f)
    }

    @Test
    fun inkDoesNotDecrease_whenPaintingWithNoInk() {
        player.mode = 0 // PAINT mode
        player.ink = 0f
        player.move(dirX = 1f, dirY = 0f, magnitude = 1f, deltaTime = 0.1f)
        assertEquals("Ink should remain 0 when painting with no ink", 0f, player.ink, 0.001f)
    }

    @Test
    fun inkIncreases_whenFillingOnOwnColor() {
        player.mode = 1 // FILL mode
        player.ink = Player.MAX_INK / 2 // Start with partial ink
        val initialInk = player.ink
        val playerColor = player.getColor()

        // Calculate expected position after move
        val dirX = 1f; val dirY = 0f; val magnitude = 1f; val deltaTime = 0.1f
        val moveAmount = Player.MOVE_SPEED * magnitude * deltaTime
        val expectedXAfterMove = (player.x + dirX * moveAmount).coerceIn(0f, mockPaintSurface.w -1f)
        val expectedYAfterMove = (player.y + dirY * moveAmount).coerceIn(0f, mockPaintSurface.h -1f)

        Mockito.`when`(mockPaintSurface.getPixelColor(expectedXAfterMove.toInt(), expectedYAfterMove.toInt())).thenReturn(playerColor)

        player.move(dirX, dirY, magnitude, null, deltaTime) // Pass null for level
        assertTrue("Ink should increase when filling on own color", player.ink > initialInk)
        assertEquals("Ink should increase by REFILL_GAIN", initialInk + Player.REFILL_GAIN, player.ink, 0.001f)
    }

    @Test
    fun inkDoesNotIncrease_whenFillingOnDifferentColor() {
        player.mode = 1 // FILL mode
        player.ink = Player.MAX_INK / 2
        val initialInk = player.ink
        val playerColor = player.getColor()
        val differentColor = playerColor + 1 // Ensure it's a different color

        // Calculate expected position after move
        val dirX = 1f; val dirY = 0f; val magnitude = 1f; val deltaTime = 0.1f
        val moveAmount = Player.MOVE_SPEED * magnitude * deltaTime
        val expectedXAfterMove = (player.x + dirX * moveAmount).coerceIn(0f, mockPaintSurface.w -1f)
        val expectedYAfterMove = (player.y + dirY * moveAmount).coerceIn(0f, mockPaintSurface.h -1f)

        Mockito.`when`(mockPaintSurface.getPixelColor(expectedXAfterMove.toInt(), expectedYAfterMove.toInt())).thenReturn(differentColor)

        player.move(dirX, dirY, magnitude, null, deltaTime) // Pass null for level
        assertEquals("Ink should not change when filling on a different color", initialInk, player.ink, 0.001f)
    }

    @Test
    fun inkDoesNotExceedMaxInk_whenFilling() {
        player.mode = 1 // FILL mode
        player.ink = Player.MAX_INK - Player.REFILL_GAIN / 2 // Almost full ink
        val playerColor = player.getColor()

        // First move
        val dirX = 1f; val dirY = 0f; val magnitude = 1f; val deltaTime = 0.1f
        var moveAmount = Player.MOVE_SPEED * magnitude * deltaTime
        var currentExpectedX = (player.x + dirX * moveAmount).coerceIn(0f, mockPaintSurface.w -1f)
        var currentExpectedY = (player.y + dirY * moveAmount).coerceIn(0f, mockPaintSurface.h -1f)
        Mockito.`when`(mockPaintSurface.getPixelColor(currentExpectedX.toInt(), currentExpectedY.toInt())).thenReturn(playerColor)
        player.move(dirX, dirY, magnitude, null, deltaTime)

        // Second move - important to update mock for new position if player moves again
        // For this test, we assume the player keeps moving over their own color.
        // We need to ensure the mock covers the position after the *second* move if it were to change.
        // To simplify and ensure we test the MAX_INK cap, let's assume the second move happens over an area that *would* refill.
        // The player.x, player.y will have been updated by the first move.
        moveAmount = Player.MOVE_SPEED * magnitude * deltaTime // re-calculate for clarity, though it's the same
        currentExpectedX = (player.x + dirX * moveAmount).coerceIn(0f, mockPaintSurface.w -1f) // player.x is now the position after the first move
        currentExpectedY = (player.y + dirY * moveAmount).coerceIn(0f, mockPaintSurface.h -1f)
        Mockito.`when`(mockPaintSurface.getPixelColor(currentExpectedX.toInt(), currentExpectedY.toInt())).thenReturn(playerColor)
        player.move(dirX, dirY, magnitude, null, deltaTime) // Perform second fill

        assertEquals("Ink should not exceed MAX_INK", Player.MAX_INK, player.ink, 0.001f)
    }

    @Test
    fun inkDoesNotGoBelowZero_whenPainting() {
        player.mode = 0 // PAINT mode
        player.ink = Player.PAINT_COST / 2 // Less ink than one paint action
        player.move(dirX = 1f, dirY = 0f, magnitude = 1f, deltaTime = 0.1f)
        assertEquals("Ink should not go below 0", 0f, player.ink, 0.001f)
    }

    @Test
    fun getInkPercent_calculatesCorrectly() {
        player.ink = Player.MAX_INK / 2
        assertEquals("Ink percent should be 0.5", 0.5f, player.getInkPercent(), 0.001f)

        player.ink = Player.MAX_INK
        assertEquals("Ink percent should be 1.0", 1.0f, player.getInkPercent(), 0.001f)

        player.ink = 0f
        assertEquals("Ink percent should be 0.0", 0.0f, player.getInkPercent(), 0.001f)
    }

    @Test
    fun move_updatesPlayerPositionCorrectly() {
        val startX = player.x
        val startY = player.y
        val dirX = 1f
        val dirY = 0f // Move only horizontally for simplicity
        val magnitude = 1f
        val deltaTime = 0.1f

        player.move(dirX, dirY, magnitude, null, deltaTime)

        val expectedX = startX + dirX * Player.MOVE_SPEED * magnitude * deltaTime
        assertEquals("Player X position should be updated correctly", expectedX, player.x, 0.001f)
        assertEquals("Player Y position should remain unchanged", startY, player.y, 0.001f)
    }

    @Test
    fun move_coercesPositionToBeWithinSurfaceBounds_minX() {
        player.x = 10f // Start close to the left edge
        // Move far left, enough to go out of bounds if not coerced
        player.move(dirX = -1f, dirY = 0f, magnitude = 1f, deltaTime = 1f) 
        assertEquals("Player X position should be coerced to 0f (minX)", 0f, player.x, 0.001f)
    }

    @Test
    fun move_coercesPositionToBeWithinSurfaceBounds_maxX() {
        player.x = mockPaintSurface.w - 10f // Start close to the right edge
        // Move far right, enough to go out of bounds if not coerced
        player.move(dirX = 1f, dirY = 0f, magnitude = 1f, deltaTime = 1f) 
        val expectedMaxX = mockPaintSurface.w.toFloat() - 1
        assertEquals("Player X position should be coerced to surface.w - 1 (maxX)", expectedMaxX, player.x, 0.001f)
    }

    @Test
    fun move_coercesPositionToBeWithinSurfaceBounds_minY() {
        player.y = 10f // Start close to the top edge
        // Move far up, enough to go out of bounds if not coerced
        player.move(dirX = 0f, dirY = -1f, magnitude = 1f, deltaTime = 1f) 
        assertEquals("Player Y position should be coerced to 0f (minY)", 0f, player.y, 0.001f)
    }

    @Test
    fun move_coercesPositionToBeWithinSurfaceBounds_maxY() {
        player.y = mockPaintSurface.h - 10f // Start close to the bottom edge
        // Move far down, enough to go out of bounds if not coerced
        player.move(dirX = 0f, dirY = 1f, magnitude = 1f, deltaTime = 1f) 
        val expectedMaxY = mockPaintSurface.h.toFloat() - 1
        assertEquals("Player Y position should be coerced to surface.h - 1 (maxY)", expectedMaxY, player.y, 0.001f)
    }

    @Test
    fun move_doesNotChangePosition_whenMagnitudeIsZero() {
        val startX = player.x
        val startY = player.y
        player.move(dirX = 1f, dirY = 1f, magnitude = 0f, null, deltaTime = 0.1f)
        assertEquals("Player X position should not change when magnitude is 0", startX, player.x, 0.001f)
        assertEquals("Player Y position should not change when magnitude is 0", startY, player.y, 0.001f)
    }

    @Test
    fun move_doesNotChangePosition_whenDeltaTimeIsZero() {
        val startX = player.x
        val startY = player.y
        // Technically, the Player.move guards against magnitude == 0f, but not deltaTime == 0f directly.
        // However, the moveAmount calculation will result in 0 if deltaTime is 0.
        player.move(dirX = 1f, dirY = 1f, magnitude = 1f, null, deltaTime = 0f)
        assertEquals("Player X position should not change when deltaTime is 0", startX, player.x, 0.001f)
        assertEquals("Player Y position should not change when deltaTime is 0", startY, player.y, 0.001f)
    }

    // --- Collision-based Movement Tests ---

    @Test
    fun move_whenNoCollision_updatesPositionAsExpected() {
        val startX = player.x
        val startY = player.y
        val dirX = 0.6f // Arbitrary direction
        val dirY = 0.8f
        val magnitude = 1f
        val deltaTime = 0.1f

        // Ensure level reports no collision for any relevant check
        Mockito.`when`(mockLevel.checkCollision(anyFloat(), anyFloat())).thenReturn(false)

        player.move(dirX, dirY, magnitude, mockLevel, deltaTime)

        val expectedX = startX + dirX * Player.MOVE_SPEED * magnitude * deltaTime
        val expectedY = startY + dirY * Player.MOVE_SPEED * magnitude * deltaTime
        assertEquals("Player X should update correctly with no collision", expectedX, player.x, 0.001f)
        assertEquals("Player Y should update correctly with no collision", expectedY, player.y, 0.001f)
    }

    @Test
    fun move_whenFullCollisionAhead_doesNotUpdatePosition() {
        val startX = player.x
        val startY = player.y

        // Simulate collision at the next direct position and also if trying to slide
        Mockito.`when`(mockLevel.checkCollision(anyFloat(), anyFloat())).thenReturn(true)

        player.move(dirX = 1f, dirY = 0f, magnitude = 1f, mockLevel, deltaTime = 0.1f)

        assertEquals("Player X should not change on full collision", startX, player.x, 0.001f)
        assertEquals("Player Y should not change on full collision", startY, player.y, 0.001f)
    }

    @Test
    fun move_whenCollisionAllowsSlidingInX_updatesXOnly() {
        val startX = player.x
        val startY = player.y
        val dirX = 1f
        val dirY = 1f // Try to move diagonally into a corner
        val magnitude = 1f
        val deltaTime = 0.1f

        val nextXPotential = startX + dirX * Player.MOVE_SPEED * magnitude * deltaTime
        val nextYPotential = startY + dirY * Player.MOVE_SPEED * magnitude * deltaTime

        // Collision at (nextXPotential, nextYPotential) - the diagonal move
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, nextYPotential)).thenReturn(true)
        // No collision if moving only in X (nextXPotential, startY)
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, startY)).thenReturn(false)
        // Collision if moving only in Y (startX, nextYPotential) - to force X slide
        Mockito.`when`(mockLevel.checkCollision(startX, nextYPotential)).thenReturn(true)

        player.move(dirX, dirY, magnitude, mockLevel, deltaTime)

        assertEquals("Player X should update (slide in X)", nextXPotential, player.x, 0.001f)
        assertEquals("Player Y should not change (slide in X)", startY, player.y, 0.001f)
    }

    @Test
    fun move_whenCollisionAllowsSlidingInY_updatesYOnly() {
        val startX = player.x
        val startY = player.y
        val dirX = 1f
        val dirY = 1f // Try to move diagonally into a corner
        val magnitude = 1f
        val deltaTime = 0.1f

        val nextXPotential = startX + dirX * Player.MOVE_SPEED * magnitude * deltaTime
        val nextYPotential = startY + dirY * Player.MOVE_SPEED * magnitude * deltaTime

        // Collision at (nextXPotential, nextYPotential) - the diagonal move
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, nextYPotential)).thenReturn(true)
        // Collision if moving only in X (nextXPotential, startY) - to force Y slide
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, startY)).thenReturn(true)
        // No collision if moving only in Y (startX, nextYPotential)
        Mockito.`when`(mockLevel.checkCollision(startX, nextYPotential)).thenReturn(false)

        player.move(dirX, dirY, magnitude, mockLevel, deltaTime)

        assertEquals("Player X should not change (slide in Y)", startX, player.x, 0.001f)
        assertEquals("Player Y should update (slide in Y)", nextYPotential, player.y, 0.001f)
    }

    @Test
    fun move_whenBlockedAndNoSlidePossible_doesNotUpdatePosition() {
        val startX = player.x
        val startY = player.y
        val dirX = 1f
        val dirY = 1f // Try to move diagonally
        val magnitude = 1f
        val deltaTime = 0.1f

        val nextXPotential = startX + dirX * Player.MOVE_SPEED * magnitude * deltaTime
        val nextYPotential = startY + dirY * Player.MOVE_SPEED * magnitude * deltaTime

        // Collision at (nextXPotential, nextYPotential)
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, nextYPotential)).thenReturn(true)
        // Collision if moving only in X (nextXPotential, startY)
        Mockito.`when`(mockLevel.checkCollision(nextXPotential, startY)).thenReturn(true)
        // Collision if moving only in Y (startX, nextYPotential)
        Mockito.`when`(mockLevel.checkCollision(startX, nextYPotential)).thenReturn(true)

        player.move(dirX, dirY, magnitude, mockLevel, deltaTime)

        assertEquals("Player X should not change (blocked, no slide)", startX, player.x, 0.001f)
        assertEquals("Player Y should not change (blocked, no slide)", startY, player.y, 0.001f)
    }
} 