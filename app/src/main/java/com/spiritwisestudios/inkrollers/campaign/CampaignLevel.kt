package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import com.spiritwisestudios.inkrollers.*

/**
 * CampaignLevel class for single-player campaign mode.
 * Extends MazeLevel functionality with robots, security devices, and hardened paint areas.
 */
class CampaignLevel(
    screenW: Int,
    screenH: Int,
    private val levelData: CampaignLevelData,
    private val topMargin: Int = 0,
    private val audioManager: AudioManager? = null
) : Level {
    
    companion object {
        private const val TAG = "CampaignLevel"
    }
    
    // Base maze level
    private val mazeLevel: MazeLevel
    
    // Campaign elements
    private val robots = mutableListOf<Robot>()
    private val securityDevices = mutableListOf<SecurityDevice>()
    private val hardenedPaintAreas = mutableListOf<HardenedPaint>()
    
    // Level state
    private var isLevelComplete = false
    
    init {
        // Create base maze with campaign level settings
        val complexityStr = when (levelData.mazeComplexity) {
            "LOW" -> HomeActivity.COMPLEXITY_LOW
            "MEDIUM" -> HomeActivity.COMPLEXITY_MEDIUM
            "HIGH" -> HomeActivity.COMPLEXITY_HIGH
            else -> HomeActivity.COMPLEXITY_MEDIUM
        }
        
        // Use level ID as seed for consistent maze generation
        val seed = levelData.levelId.hashCode().toLong()
        
        mazeLevel = MazeLevel(
            screenW, screenH, 
            12, 20, 12f, // Default maze parameters
            seed, 
            complexityStr,
            topMargin
        )
        
        setupCampaignElements()
        Log.d(TAG, "Created campaign level: ${levelData.levelName}")
    }
    
    /**
     * Initialize campaign elements from level data
     */
    private fun setupCampaignElements() {
        // Initialize robots
        levelData.robotPositions.forEach { robotData ->
            val robot = Robot(robotData.x, robotData.y, robotData)
            robots.add(robot)
        }
        
        // Initialize security devices
        levelData.securityDevices.forEach { deviceData ->
            val device = SecurityDevice(deviceData)
            securityDevices.add(device)
        }
        
        // Initialize hardened paint areas
        levelData.hardenedPaintAreas.forEach { paintData ->
            val hardenedPaint = HardenedPaint(paintData)
            hardenedPaintAreas.add(hardenedPaint)
        }
        
        Log.d(TAG, "Setup campaign elements: ${robots.size} robots, ${securityDevices.size} devices, ${hardenedPaintAreas.size} hardened areas")
    }
    
    /**
     * Update level state and campaign elements
     */
    override fun update(): Boolean {
        // Update base maze
        val baseLevelComplete = mazeLevel.update()
        
        // Update campaign elements
        updateCampaignElements()
        
        // Check if level is complete
        checkLevelCompletion()
        
        return isLevelComplete
    }
    
    /**
     * Update all campaign elements
     */
    private fun updateCampaignElements() {
        // Update robots
        robots.forEach { robot ->
            // Note: deltaTime would need to be passed from GameView
            // For now, use a fixed deltaTime of 1/60 second
            robot.update(1f / 60f, getPaintSurface())
        }
        
        // Update security devices
        securityDevices.forEach { device ->
            device.update(1f / 60f)
        }
        
        // Update hardened paint areas
        hardenedPaintAreas.forEach { hardenedPaint ->
            hardenedPaint.update(1f / 60f)
        }
    }
    
    /**
     * Check if level completion conditions are met
     */
    private fun checkLevelCompletion() {
        if (isLevelComplete) return
        
        // Check coverage requirement
        val paintSurface = getPaintSurface()
        if (paintSurface != null) {
            val coverage = calculateCoverage(paintSurface)
            val totalCoverage = coverage.values.sum()
            
            if (totalCoverage >= levelData.requiredCoverage) {
                isLevelComplete = true
                audioManager?.playSound(AudioManager.SoundType.LEVEL_COMPLETE)
                Log.d(TAG, "Level complete! Coverage: $totalCoverage / ${levelData.requiredCoverage}")
            }
        }
    }
    
    /**
     * Draw the level and all campaign elements
     */
    override fun draw(canvas: Canvas) {
        // Draw base maze
        mazeLevel.draw(canvas)
        
        // Draw hardened paint areas (behind other elements)
        hardenedPaintAreas.forEach { hardenedPaint ->
            hardenedPaint.draw(canvas)
        }
        
        // Draw security devices
        securityDevices.forEach { device ->
            device.draw(canvas)
        }
        
        // Draw robots
        robots.forEach { robot ->
            robot.draw(canvas)
        }
    }
    
    /**
     * Check collision with maze walls and campaign elements
     */
    override fun checkCollision(x: Float, y: Float): Boolean {
        // Check maze walls first
        if (mazeLevel.checkCollision(x, y)) {
            return true
        }
        
        // Check security devices
        securityDevices.forEach { device ->
            if (device.isBlockingPath(x, y)) {
                return true
            }
        }
        
        // Check hardened paint areas
        hardenedPaintAreas.forEach { hardenedPaint ->
            if (hardenedPaint.isBlocking(x, y)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get player start position from base maze
     */
    override fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float> {
        return mazeLevel.getPlayerStartPosition(playerIndex)
    }
    
    /**
     * Calculate coverage using base maze level
     */
    override fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float> {
        return mazeLevel.calculateCoverage(paintSurface)
    }
    
    /**
     * Get zones from base maze level
     */
    override fun getZones(): List<RectF> {
        return mazeLevel.getZones()
    }
    
    /**
     * Handle player interaction with campaign elements
     */
    fun handlePlayerInteraction(player: Player, paintSurface: PaintSurface) {
        if (!player.isCampaignMode()) return
        
        val playerX = player.x
        val playerY = player.y
        val currentFrequency = player.getCurrentFrequency()
        
        // Check robot interactions (painting robots for conversion)
        robots.forEach { robot ->
            val robotBounds = robot.getBounds()
            if (robotBounds.contains(playerX, playerY) && player.mode == 0) { // Paint mode
                if (robot.paintRobot(player.getColor(), paintSurface)) {
                    // Robot was successfully converted
                    audioManager?.playSound(AudioManager.SoundType.ROBOT_CONVERSION)
                }
            }
        }
        
        // Check security device interactions
        securityDevices.forEach { device ->
            if (device.interactWithControlPanel(currentFrequency, playerX, playerY)) {
                // Device was successfully disabled
                audioManager?.playSound(AudioManager.SoundType.SECURITY_DEVICE_DEACTIVATE)
            }
        }
        
        // Check hardened paint interactions
        hardenedPaintAreas.forEach { hardenedPaint ->
            if (hardenedPaint.attemptDissolution(currentFrequency, playerX, playerY)) {
                // Hardened paint was dissolved
                audioManager?.playSound(AudioManager.SoundType.HARDENED_PAINT_DISSOLVE)
            }
        }
    }
    
    /**
     * Get level data for this campaign level
     */
    fun getLevelData(): CampaignLevelData = levelData
    
    /**
     * Get all robots in this level
     */
    fun getRobots(): List<Robot> = robots
    
    /**
     * Get all security devices in this level
     */
    fun getSecurityDevices(): List<SecurityDevice> = securityDevices
    
    /**
     * Get all hardened paint areas in this level
     */
    fun getHardenedPaintAreas(): List<HardenedPaint> = hardenedPaintAreas
    
    /**
     * Check if level is complete
     */
    fun isComplete(): Boolean = isLevelComplete
    
    /**
     * Get the required coverage for level completion
     */
    fun getRequiredCoverage(): Float = levelData.requiredCoverage
    
    /**
     * Get time limit for this level (if any)
     */
    fun getTimeLimit(): Long? = levelData.timeLimit
    
    /**
     * Convert screen coordinates to maze coordinates (for consistency)
     */
    fun screenToMazeCoord(x: Float, y: Float): Pair<Float, Float> {
        return mazeLevel.screenToMazeCoord(x, y)
    }
    
    /**
     * Convert maze coordinates to screen coordinates (for consistency)
     */
    fun mazeToScreenCoord(relX: Float, relY: Float): Pair<Float, Float> {
        return mazeLevel.mazeToScreenCoord(relX, relY)
    }
    
    /**
     * Get viewport offset from base maze level
     */
    fun getViewportOffset(): Pair<Float, Float> {
        return mazeLevel.getViewportOffset()
    }
    
    /**
     * Get scale factor from base maze level
     */
    fun getScale(): Float {
        return mazeLevel.getScale()
    }
    
    /**
     * Helper method to get paint surface from game context
     * This would need to be set by the GameView when the level is created
     */
    private var paintSurface: PaintSurface? = null
    
    fun setPaintSurface(surface: PaintSurface) {
        this.paintSurface = surface
    }
    
    private fun getPaintSurface(): PaintSurface? = paintSurface
} 