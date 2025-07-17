package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import com.spiritwisestudios.inkrollers.*
import com.spiritwisestudios.inkrollers.campaign.effects.CampaignEffects

/**
 * CampaignLevel class for single-player campaign mode.
 * Extends MazeLevel functionality with robots, security devices, hardened paint areas, and puzzle doors.
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
    private val robotSpawners = mutableListOf<RobotSpawner>()
    private val securityDevices = mutableListOf<SecurityDevice>()
    private val hardenedPaintAreas = mutableListOf<HardenedPaint>()
    private val doorActivators = mutableListOf<DoorActivator>()
    private var exitZone: ExitZone? = null
    
    // Visual effects
    private val campaignEffects = CampaignEffects()
    
    // Level state
    private var isLevelComplete = false
    private var activatedDoors = 0
    private var totalDoors = 0
    private var playerInExitZone = false
    
    init {
        // Create base maze with campaign level settings
        val complexityStr = when (levelData.mazeComplexity) {
            "LOW" -> HomeActivity.COMPLEXITY_LOW
            "MEDIUM" -> HomeActivity.COMPLEXITY_MEDIUM
            "HIGH" -> HomeActivity.COMPLEXITY_HIGH
            else -> HomeActivity.COMPLEXITY_MEDIUM
        }
        
        // Use level ID as seed for consistent maze generation
        // For Level 1, use a custom seed to ensure the path works with the door placement
        val seed = when (levelData.levelId) {
            "level_1" -> 1337L  // Custom seed for tutorial level that creates a good path layout
            else -> levelData.levelId.hashCode().toLong()
        }
        
        // Use single-path mazes for puzzle levels to ensure door mechanics work properly
        val pathType = if (levelData.requiresSinglePath) {
            MazeLevel.PathType.SINGLE_PATH
        } else {
            MazeLevel.PathType.MULTIPLE_PATHS
        }
        
        mazeLevel = MazeLevel(
            screenW, screenH, 
            12, 20, 12f, // Default maze parameters
            seed, 
            complexityStr,
            topMargin,
            pathType
        )
        
        setupCampaignElements()
        Log.d(TAG, "Created campaign level: ${levelData.levelName} with ${if (levelData.requiresSinglePath) "single" else "multiple"} path(s)")
    }
    
    /**
     * Initialize campaign elements from level data
     */
    private fun setupCampaignElements() {
        // Initialize robots with proper coordinate transformation
        levelData.robotPositions.forEach { robotData ->
            // Convert maze cell coordinates to screen coordinates
            // Treat robot coordinates as relative positions within the maze (0.0-1.0)
            val (screenX, screenY) = mazeLevel.mazeToScreenCoord(
                robotData.x / 1000f, // Convert from 0-1000 range to 0-1 normalized
                robotData.y / 1000f
            )
            
            // Transform patrol path coordinates as well
            val transformedPatrolPath = robotData.patrolPath.map { (pathX, pathY) ->
                val (transformedX, transformedY) = mazeLevel.mazeToScreenCoord(
                    pathX / 1000f,
                    pathY / 1000f
                )
                transformedX to transformedY
            }
            
            // Create robot data with transformed coordinates
            val transformedRobotData = robotData.copy(
                x = screenX,
                y = screenY,
                patrolPath = transformedPatrolPath
            )
            
            val robot = Robot(screenX, screenY, transformedRobotData)
            robots.add(robot)
            Log.d(TAG, "Created robot at screen position ($screenX, $screenY) from data (${robotData.x}, ${robotData.y})")
        }
        
        // Initialize robot spawners with proper coordinate transformation
        levelData.robotSpawners.forEach { spawnerData ->
            // Convert spawner coordinates to screen coordinates
            val (screenX, screenY) = mazeLevel.mazeToScreenCoord(
                spawnerData.x / 1000f, // Convert from 0-1000 range to 0-1 normalized
                spawnerData.y / 1000f
            )
            
            // Transform spawned robot patrol path coordinates
            val transformedPatrolPath = spawnerData.spawnedRobotPatrolPath.map { (pathX, pathY) ->
                val (transformedX, transformedY) = mazeLevel.mazeToScreenCoord(
                    (spawnerData.x + pathX) / 1000f, // Relative to spawner, then to screen
                    (spawnerData.y + pathY) / 1000f
                )
                transformedX - screenX to transformedY - screenY // Make relative to spawner screen position
            }
            
            // Create spawner data with transformed coordinates
            val transformedSpawnerData = spawnerData.copy(
                x = screenX,
                y = screenY,
                spawnedRobotPatrolPath = transformedPatrolPath
            )
            
            val spawner = RobotSpawner(transformedSpawnerData)
            robotSpawners.add(spawner)
            Log.d(TAG, "Created robot spawner at screen position ($screenX, $screenY) from data (${spawnerData.x}, ${spawnerData.y})")
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
        
        // Initialize door activators with proper coordinate transformation
        levelData.doorActivators.forEach { activatorData ->
            // Transform door activator coordinates from level data to screen coordinates
            val originalActivatorArea = activatorData.activatorArea
            val originalWallArea = activatorData.wallArea
            
            // Convert from absolute pixel coordinates to normalized coordinates (0.0-1.0)
            // Assuming the level data coordinates are in a 0-1000 range, normalize them
            val normalizedActivatorLeft = originalActivatorArea.left / 1000f
            val normalizedActivatorTop = originalActivatorArea.top / 1000f
            val normalizedActivatorRight = originalActivatorArea.right / 1000f
            val normalizedActivatorBottom = originalActivatorArea.bottom / 1000f
            
            val normalizedWallLeft = originalWallArea.left / 1000f
            val normalizedWallTop = originalWallArea.top / 1000f
            val normalizedWallRight = originalWallArea.right / 1000f
            val normalizedWallBottom = originalWallArea.bottom / 1000f
            
            // Transform to screen coordinates using maze coordinate system
            val (screenActivatorLeft, screenActivatorTop) = mazeLevel.mazeToScreenCoord(normalizedActivatorLeft, normalizedActivatorTop)
            val (screenActivatorRight, screenActivatorBottom) = mazeLevel.mazeToScreenCoord(normalizedActivatorRight, normalizedActivatorBottom)
            
            val (screenWallLeft, screenWallTop) = mazeLevel.mazeToScreenCoord(normalizedWallLeft, normalizedWallTop)
            val (screenWallRight, screenWallBottom) = mazeLevel.mazeToScreenCoord(normalizedWallRight, normalizedWallBottom)
            
            // Create transformed door activator data
            val transformedActivatorArea = RectF(screenActivatorLeft, screenActivatorTop, screenActivatorRight, screenActivatorBottom)
            val transformedWallArea = RectF(screenWallLeft, screenWallTop, screenWallRight, screenWallBottom)
            val transformedActivatorData = activatorData.copy(
                activatorArea = transformedActivatorArea,
                wallArea = transformedWallArea
            )
            
            val doorActivator = DoorActivator(transformedActivatorData, audioManager)
            doorActivators.add(doorActivator)
            
            Log.d(TAG, "Created door activator:")
            Log.d(TAG, "  Original activator: (${originalActivatorArea.left}, ${originalActivatorArea.top}, ${originalActivatorArea.right}, ${originalActivatorArea.bottom})")
            Log.d(TAG, "  Screen activator: ($screenActivatorLeft, $screenActivatorTop, $screenActivatorRight, $screenActivatorBottom)")
            Log.d(TAG, "  Original wall: (${originalWallArea.left}, ${originalWallArea.top}, ${originalWallArea.right}, ${originalWallArea.bottom})")
            Log.d(TAG, "  Screen wall: ($screenWallLeft, $screenWallTop, $screenWallRight, $screenWallBottom)")
        }
        
        totalDoors = doorActivators.size
        
        // Initialize exit zone if present
        levelData.exitZone?.let { exitData ->
            val originalArea = exitData.area
            
            // Convert from absolute pixel coordinates to normalized coordinates
            val normalizedLeft = originalArea.left / 1000f
            val normalizedTop = originalArea.top / 1000f
            val normalizedRight = originalArea.right / 1000f
            val normalizedBottom = originalArea.bottom / 1000f
            
            // Transform to screen coordinates using maze coordinate system
            val (screenLeft, screenTop) = mazeLevel.mazeToScreenCoord(normalizedLeft, normalizedTop)
            val (screenRight, screenBottom) = mazeLevel.mazeToScreenCoord(normalizedRight, normalizedBottom)
            
            // Create transformed exit zone data
            val transformedArea = RectF(screenLeft, screenTop, screenRight, screenBottom)
            val transformedExitData = exitData.copy(area = transformedArea)
            
            exitZone = ExitZone(transformedExitData, audioManager)
            
            Log.d(TAG, "Created exit zone:")
            Log.d(TAG, "  Original: (${originalArea.left}, ${originalArea.top}, ${originalArea.right}, ${originalArea.bottom})")
            Log.d(TAG, "  Screen: ($screenLeft, $screenTop, $screenRight, $screenBottom)")
        } ?: run {
            // For levels without explicit exit zone, create one at the maze exit
            // Get the maze exit position (bottom-right corner in normalized coordinates)
            val (exitScreenX, exitScreenY) = mazeLevel.getPlayerStartPosition(1) // Player 1 starts at exit
            
            // Create exit zone around the maze exit position
            val exitSize = 60f // Size of the exit zone in pixels
            val exitArea = RectF(
                exitScreenX - exitSize / 2,
                exitScreenY - exitSize / 2,
                exitScreenX + exitSize / 2,
                exitScreenY + exitSize / 2
            )
            
            val autoExitData = ExitZoneData(
                area = exitArea,
                description = "Maze Exit"
            )
            
            exitZone = ExitZone(autoExitData, audioManager)
            
            Log.d(TAG, "Auto-created exit zone at maze exit:")
            Log.d(TAG, "  Position: ($exitScreenX, $exitScreenY)")
            Log.d(TAG, "  Area: (${exitArea.left}, ${exitArea.top}, ${exitArea.right}, ${exitArea.bottom})")
        }
        
        Log.d(TAG, "Setup campaign elements: ${robots.size} robots, ${robotSpawners.size} robot spawners, ${securityDevices.size} devices, ${hardenedPaintAreas.size} hardened areas, ${doorActivators.size} door activators, exit zone: ${exitZone != null}")
    }
    
    /**
     * Update level state and campaign elements
     */
    override fun update(): Boolean {
        // Update base maze
        mazeLevel.update()
        
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
        // Update robots with level for collision detection and AI
        robots.forEach { robot ->
            // Note: deltaTime would need to be passed from GameView
            // For now, use a fixed deltaTime of 1/60 second
            getPaintSurface()?.let { paintSurface ->
                robot.update(1f / 60f, paintSurface, this)
            }
        }
        
        // Update robot spawners
        robotSpawners.forEach { spawner ->
            spawner.update(1f / 60f, this)
        }
        
        // Update security devices
        securityDevices.forEach { device ->
            device.update(1f / 60f)
        }
        
        // Update hardened paint areas
        hardenedPaintAreas.forEach { hardenedPaint ->
            hardenedPaint.update(1f / 60f)
        }
        
        // Update door activators
        doorActivators.forEach { doorActivator ->
            doorActivator.update(1f / 60f)
        }
        
        // Update exit zone
        exitZone?.update(1f / 60f)
        
        // Update visual effects
        campaignEffects.update(1f / 60f)
    }
    
    /**
     * Check if level completion conditions are met
     */
    private fun checkLevelCompletion() {
        if (isLevelComplete) return
        
        // For tutorial level (level_1), completion is based on reaching the exit zone
        if (levelData.levelId == "level_1") {
            if (playerInExitZone) {
                isLevelComplete = true
                audioManager?.playSound(AudioManager.SoundType.LEVEL_COMPLETE)
                Log.d(TAG, "Tutorial level complete! Player reached exit zone.")
                return
            }
        }
        
        // For other levels, check coverage requirement and exit zone
        val paintSurface = getPaintSurface()
        if (paintSurface != null) {
            val coverage = calculateCoverage(paintSurface)
            val totalCoverage = coverage.values.sum()
            
            if (totalCoverage >= levelData.requiredCoverage && playerInExitZone) {
                isLevelComplete = true
                audioManager?.playSound(AudioManager.SoundType.LEVEL_COMPLETE)
                Log.d(TAG, "Level complete! Coverage: $totalCoverage / ${levelData.requiredCoverage}, in exit zone: $playerInExitZone")
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
        
        // Draw door activators (walls and activator squares)
        doorActivators.forEach { doorActivator ->
            doorActivator.draw(canvas)
        }
        
        // Draw exit zone
        exitZone?.draw(canvas)
        
        // Draw robot spawners (before robots so they appear behind)
        robotSpawners.forEach { spawner ->
            spawner.draw(canvas)
        }
        
        // Draw security devices
        securityDevices.forEach { device ->
            device.draw(canvas)
        }
        
        // Draw robots
        robots.forEach { robot ->
            robot.draw(canvas)
        }
        
        // Draw visual effects (on top of everything)
        campaignEffects.drawEffects(canvas)
    }
    
    /**
     * Check collision with maze walls, campaign elements, and door walls
     */
    override fun checkCollision(x: Float, y: Float): Boolean {
        // Check maze walls first
        if (mazeLevel.checkCollision(x, y)) {
            return true
        }
        
        // Check robot spawners
        robotSpawners.forEach { spawner ->
            if (spawner.checkCollision(x, y)) {
                return true
            }
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
        
        // Check door activator walls (only if not activated)
        doorActivators.forEach { doorActivator ->
            if (doorActivator.isBlocking(x, y)) {
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
        
        // Update door activator distances for visual feedback
        doorActivators.forEach { doorActivator ->
            doorActivator.updatePlayerDistance(playerX, playerY)
        }
        
        // Update exit zone player status
        exitZone?.let { exit ->
            playerInExitZone = exit.checkPlayerInZone(playerX, playerY)
            exit.updatePlayerDistance(playerX, playerY)
        }
        
        // Trigger color shift effect when player changes frequency
        // This will be called from Player.toggleColorShift()
        campaignEffects.triggerColorShiftEffect(player)
        
        // Check robot interactions (painting robots for conversion)
        robots.forEach { robot ->
            val robotBounds = robot.getBounds()
            if (robotBounds.contains(playerX, playerY) && player.mode == 0) { // Paint mode
                if (robot.paintRobot(player.getColor(), paintSurface)) {
                    // Robot was successfully converted
                    audioManager?.playSound(AudioManager.SoundType.ROBOT_CONVERSION)
                    campaignEffects.triggerRobotConversionEffect(robot)
                }
            }
        }
        
        // Check security device interactions
        securityDevices.forEach { device ->
            if (device.interactWithControlPanel(currentFrequency, playerX, playerY)) {
                // Device was successfully disabled
                audioManager?.playSound(AudioManager.SoundType.SECURITY_DEVICE_DEACTIVATE)
                campaignEffects.triggerBloomEffect(Pair(playerX, playerY))
            }
        }
        
        // Check hardened paint interactions
        hardenedPaintAreas.forEach { hardenedPaint ->
            if (hardenedPaint.attemptDissolution(currentFrequency, playerX, playerY)) {
                // Hardened paint was dissolved
                audioManager?.playSound(AudioManager.SoundType.HARDENED_PAINT_DISSOLVE)
                campaignEffects.triggerAreaCompletionEffect(hardenedPaint.getArea())
            }
        }
        
        // Door activators are automatically checked during their update cycle
        // Count activated doors for objectives
        activatedDoors = doorActivators.count { it.isActivated() }
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
     * Get all door activators in this level
     */
    fun getDoorActivators(): List<DoorActivator> = doorActivators
    
    /**
     * Get exit zone if present
     */
    fun getExitZone(): ExitZone? = exitZone
    
    /**
     * Check if level is complete
     */
    fun isComplete(): Boolean = isLevelComplete
    
    /**
     * Get the required coverage for level completion
     */
    fun getRequiredCoverage(): Float = levelData.requiredCoverage
    
    /**
     * Get campaign effects for external triggering
     */
    fun getCampaignEffects(): CampaignEffects = campaignEffects
    
    /**
     * Get time limit for this level (if any)
     */
    fun getTimeLimit(): Long? = levelData.timeLimit
    
    /**
     * Get grading statistics for level completion
     */
    fun getGradingStats(): Map<String, Any> {
        val convertedRobots = robots.count { it.isFullyConverted() }
        val totalRobots = robots.size
        
        return mapOf(
            "robotsConverted" to convertedRobots,
            "totalRobots" to totalRobots,
            "doorsActivated" to activatedDoors,
            "totalDoors" to totalDoors,
            "reachedExit" to playerInExitZone
        )
    }
    
    /**
     * Get activated doors count
     */
    fun getActivatedDoors(): Int = activatedDoors
    
    /**
     * Get total doors count
     */
    fun getTotalDoors(): Int = totalDoors
    
    /**
     * Check if player is in exit zone
     */
    fun isPlayerInExitZone(): Boolean = playerInExitZone
    
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
     * Add a spawned robot to the campaign level (called by robot spawners)
     */
    fun addSpawnedRobot(robot: Robot) {
        robots.add(robot)
        Log.d(TAG, "Added spawned robot at position ${robot.getPosition()} - Total robots: ${robots.size}")
    }
    
    /**
     * Remove a robot from the campaign level (for cleanup when robots are destroyed)
     */
    fun removeRobot(robot: Robot) {
        if (robots.remove(robot)) {
            // Notify spawners that a robot was removed so they can update their count
            robotSpawners.forEach { spawner ->
                spawner.decrementSpawnedRobotCount()
            }
            Log.d(TAG, "Removed robot - Total robots: ${robots.size}")
        }
    }
    
    /**
     * Helper method to get paint surface from game context
     * This would need to be set by the GameView when the level is created
     */
    private var paintSurface: PaintSurface? = null
    
    fun setPaintSurface(surface: PaintSurface) {
        this.paintSurface = surface
        doorActivators.forEach { it.setPaintSurface(surface) }
    }
    
    private fun getPaintSurface(): PaintSurface? = paintSurface
} 