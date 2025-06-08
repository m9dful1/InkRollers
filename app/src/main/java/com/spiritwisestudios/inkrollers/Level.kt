package com.spiritwisestudios.inkrollers

import android.graphics.Canvas
import android.graphics.RectF

/**
 * Defines the contract for game level implementations.
 * 
 * Levels provide the game world structure including collision detection, player spawning,
 * and coverage analysis. Currently implemented by MazeLevel for maze-based gameplay.
 */
interface Level {
    /** Updates level state each frame. Returns true if level should transition/complete. */
    fun update(): Boolean
    
    /** Renders level elements (walls, obstacles) onto the provided canvas. */
    fun draw(canvas: Canvas)
    
    /** Returns true if the given coordinates collide with level geometry. */
    fun checkCollision(x: Float, y: Float): Boolean
    
    /** Returns starting screen coordinates for the specified player index. */
    fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float>
    
    /** Calculates paint coverage percentage by color for Coverage game mode. */
    fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float>
    
    /** Returns zone boundaries in screen coordinates for Zones game mode. */
    fun getZones(): List<RectF>
} 