package com.spiritwisestudios.inkrollers

import android.graphics.Canvas
import android.graphics.RectF

/**
 * Interface for all level types in the game.
 * Levels manage the structure, collision detection, and state transitions
 */
interface Level {
    /**
     * Update the level's internal state each frame
     * @return true if the level is complete, false otherwise
     */
    fun update(): Boolean
    
    /**
     * Draw the level elements (walls, obstacles, etc.)
     * @param canvas The canvas to draw onto
     */
    fun draw(canvas: Canvas)
    
    /**
     * Check if a point collides with any walls/obstacles in the level
     * @param x X-coordinate to check
     * @param y Y-coordinate to check
     * @return true if collision detected, false if clear
     */
    fun checkCollision(x: Float, y: Float): Boolean
    
    /**
     * Get the starting position for a player index
     * @param playerIndex The index of the player (0 for player 1, 1 for player 2, etc.)
     * @return A pair containing (x, y) coordinates for the player's start position
     */
    fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float>
    
    /**
     * Calculate the percentage of the level covered by each color
     * @return Map of color (Int) to coverage percentage (0.0-1.0)
     */
    fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float>
    
    /**
     * Get the zones defined for this level (for Zones game mode)
     * @return List of zone rectangles in normalized coordinates (0.0-1.0)
     */
    fun getZones(): List<RectF>
} 