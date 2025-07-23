package com.spiritwisestudios.inkrollers.multiplayer

import android.graphics.Canvas
import android.graphics.RectF
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.PaintSurface
import com.spiritwisestudios.inkrollers.campaign.Robot
import com.spiritwisestudios.inkrollers.campaign.SpawnableLevel

/**
 * Adapter that wraps a multiplayer Level to provide CampaignLevel-like robot management.
 * This allows campaign RobotSpawners to work with multiplayer levels.
 */
class MultiplayerLevelAdapter(
    private val level: Level,
    private val robotManager: RobotSpawnerManager
) : Level, SpawnableLevel {
    
    /**
     * Check collision using the underlying level
     */
    override fun checkCollision(x: Float, y: Float): Boolean {
        return level.checkCollision(x, y)
    }
    
    /**
     * Add a spawned robot to the robot manager
     */
    override fun addSpawnedRobot(robot: Robot) {
        robotManager.addSpawnedRobot(robot)
    }
    
    /**
     * Update the underlying level - implements Level interface
     */
    override fun update(): Boolean {
        return level.update()
    }
    
    /**
     * Draw the underlying level - implements Level interface
     */
    override fun draw(canvas: Canvas) {
        level.draw(canvas)
    }
    
    /**
     * Get player start position from the underlying level - implements Level interface
     */
    override fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float> {
        return level.getPlayerStartPosition(playerIndex)
    }
    
    /**
     * Calculate coverage using the underlying level - implements Level interface
     */
    override fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float> {
        return level.calculateCoverage(paintSurface)
    }
    
    /**
     * Get zones from the underlying level - implements Level interface
     */
    override fun getZones(): List<RectF> {
        return level.getZones()
    }
    
    /**
     * Convert normalized coordinates to screen coordinates if the underlying level supports it
     */
    fun mazeToScreenCoord(relX: Float, relY: Float): Pair<Float, Float> {
        return if (level is com.spiritwisestudios.inkrollers.MazeLevel) {
            level.mazeToScreenCoord(relX, relY)
        } else {
            // Fallback for other level types
            Pair(relX, relY)
        }
    }
}