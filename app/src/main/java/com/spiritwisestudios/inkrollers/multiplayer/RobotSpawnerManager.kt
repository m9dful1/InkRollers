package com.spiritwisestudios.inkrollers.multiplayer

import android.graphics.Canvas
import android.util.Log
import com.spiritwisestudios.inkrollers.PaintSurface
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.RobotSpawnerState
import com.spiritwisestudios.inkrollers.RobotState
import com.spiritwisestudios.inkrollers.campaign.Robot
import com.spiritwisestudios.inkrollers.campaign.RobotSpawner
import com.spiritwisestudios.inkrollers.campaign.RobotSpawnerData
import java.util.UUID

/**
 * Manages robot spawners in multiplayer matches using campaign RobotSpawner.
 * Handles placement, updates, rendering, and robot lifecycle management.
 * Acts as a bridge between campaign spawners and multiplayer levels.
 */
class RobotSpawnerManager(
    private val surface: PaintSurface,
    private val level: Level,
    private val spawnerCount: Int,
    private val multiplayerManager: MultiplayerManager? = null,
    private val gameId: String? = null
) {
    companion object {
        private const val TAG = "RobotSpawnerManager"
        private const val WALL_BUFFER = 60f // Distance from walls
        private const val PLAYER_SPAWN_BUFFER = 100f // Distance from player spawn areas
        
        // Multiplayer spawner settings
        private const val SPAWN_INTERVAL = 20000L // 20 seconds
        private const val MAX_ROBOTS_PER_SPAWNER = 4 // Increased from 2 to 4
        private const val ROBOT_UNPAINT_RADIUS = 40f
        
        // Performance optimization settings
        private const val CULLING_DISTANCE = 200f // Distance beyond screen edges to consider for culling
        private const val REMOTE_UPDATE_INTERVAL = 150L // Update remote robots less frequently (milliseconds)
        private const val MAX_TOTAL_ROBOTS = 20 // Global limit on total robots for performance
        
        // Movement synchronization settings
        private const val POSITION_SYNC_INTERVAL = 100L // Sync robot positions every 100ms for smooth movement
        private const val SPAWNER_SYNC_INTERVAL = 1000L // Sync spawner states every 1 second (less frequent)
    }

    private val spawners = mutableListOf<RobotSpawner>()
    private val allSpawnedRobots = mutableListOf<Robot>()
    private val localRobots = mutableListOf<Robot>() // Only locally spawned robots
    private val levelAdapter = MultiplayerLevelAdapter(level, this)
    
    // Firebase synchronization state
    private val robotIdMap = mutableMapOf<Robot, String>() // Maps robot instances to unique IDs
    private val remoteRobots = mutableMapOf<String, Robot>() // Maps remote robot IDs to instances
    private var syncInitialized = false
    private var lastPositionSync = 0L // For frequent robot position updates
    private var lastSpawnerSync = 0L // For less frequent spawner state updates
    
    // Performance optimization state
    private var lastRemoteUpdateTime = 0L
    private val culledRobots = mutableSetOf<Robot>() // Robots currently culled from updates
    
    // Position interpolation for smooth remote robot movement
    private data class RobotInterpolationData(
        var targetX: Float,
        var targetY: Float,
        var startX: Float,
        var startY: Float,
        var interpolationStartTime: Long,
        var interpolationDuration: Long = POSITION_SYNC_INTERVAL
    )
    private val robotInterpolation = mutableMapOf<String, RobotInterpolationData>() // robotId -> interpolation data
    
    // Track recent conversions to prevent sync conflicts
    private val recentConversions = mutableMapOf<String, Long>() // robotId -> conversion timestamp
    
    // Spawner initialization state
    private var spawnersInitialized = false
    private val spawnerInitializationDelay = 2000L // 2 second delay before first spawn
    
    // Deterministic random generator using gameId as seed for consistent behavior across devices
    private val deterministicRandom = kotlin.random.Random(gameId?.hashCode()?.toLong() ?: 12345L)
    
    // Determine if this device should spawn robots (simplified ownership model)
    // NOTE: This is checked dynamically rather than cached to ensure proper host detection
    
    private val initializationStartTime = System.currentTimeMillis()
    
    init {
        if (spawnerCount > 0) {
            placeSpawners()
            initializeFirebaseSync()
        }
    }
    
    /**
     * Initialize Firebase synchronization for robot spawners and robots
     */
    private fun initializeFirebaseSync() {
        multiplayerManager?.let { manager ->
            // Set up listeners for remote state changes
            manager.setRobotSpawnerStateListener { spawnerIndex, spawnerState ->
                Log.d(TAG, "Received remote spawner state update: spawner $spawnerIndex")
                updateRemoteSpawnerState(spawnerIndex, spawnerState)
            }
            
            manager.setRobotStateListener { robotId, robotState, isRemoved ->
                if (isRemoved) {
                    Log.d(TAG, "Received remote robot removal: $robotId")
                    removeRemoteRobot(robotId)
                } else if (robotState != null) {
                    Log.d(TAG, "Received remote robot state update: $robotId at (${robotState.normX}, ${robotState.normY})")
                    updateRemoteRobotState(robotId, robotState)
                }
            }
            
            // Sync initial spawner states
            syncSpawnerStates()
            syncInitialized = true
            
            Log.d(TAG, "Firebase sync initialized for robot spawners and robots")
        }
    }
    
    
    /**
     * Create a campaign RobotSpawner with multiplayer settings and deterministic seed
     */
    private fun createRobotSpawner(x: Float, y: Float, spawnerIndex: Int): RobotSpawner {
        val spawnerData = RobotSpawnerData(
            x = x,
            y = y,
            spawnInterval = SPAWN_INTERVAL,
            maxRobots = MAX_ROBOTS_PER_SPAWNER,
            spawnedRobotUnpaintRadius = ROBOT_UNPAINT_RADIUS,
            spawnedRobotPatrolPath = emptyList(), // Let spawner generate default patrol path
            showSpawnRadius = false
        )
        
        // Create unique deterministic seed for each spawner based on gameId and spawner index
        val spawnerSeed = deterministicRandom.nextLong() + spawnerIndex
        
        // Determine if this device should control this spawner's robot spawning
        // Use spawner index to deterministically assign ownership
        val isControllingSpawner = shouldControlSpawner(spawnerIndex)
        
        Log.d(TAG, "Creating spawner $spawnerIndex: shouldSpawnRobots=${shouldSpawnRobots()}, isControllingSpawner=$isControllingSpawner")
        
        val spawner = if (isControllingSpawner) {
            Log.d(TAG, "Creating ACTIVE spawner $spawnerIndex (this device controls robot spawning)")
            RobotSpawner(spawnerData, spawnerSeed)
        } else {
            Log.d(TAG, "Creating PASSIVE spawner $spawnerIndex (remote device controls robot spawning)")
            // Create spawner but disable robot spawning - only visual and conversion
            val passiveData = RobotSpawnerData(
                x = spawnerData.x,
                y = spawnerData.y,
                spawnInterval = spawnerData.spawnInterval,
                maxRobots = 0, // No robot spawning
                spawnedRobotUnpaintRadius = spawnerData.spawnedRobotUnpaintRadius,
                spawnedRobotPatrolPath = spawnerData.spawnedRobotPatrolPath,
                showSpawnRadius = spawnerData.showSpawnRadius
            )
            RobotSpawner(passiveData, spawnerSeed)
        }
        
        // Initialize spawner to wait for proper initialization before first spawn
        spawner.resetSpawnTimer()
        return spawner
    }
    
    /**
     * Determine if this device should spawn robots (simplified ownership model)
     * Only one device should spawn robots to prevent conflicts and selective conversion issues
     */
    private fun shouldSpawnRobots(): Boolean {
        // Use MultiplayerManager to determine if this device is the host
        // Host device handles all robot spawning, others only receive remote robots via Firebase
        val isHost = multiplayerManager?.isHost() ?: true // Default to true if no multiplayer manager
        Log.d(TAG, "shouldSpawnRobots() check: isHost = $isHost, multiplayerManager = ${multiplayerManager != null}")
        return isHost
    }
    
    /**
     * Determine if this device should control robot spawning for a given spawner
     * Uses simplified model where only the spawning device creates active spawners
     */
    private fun shouldControlSpawner(spawnerIndex: Int): Boolean {
        // Simplified: only the robot spawning device controls any spawners
        return shouldSpawnRobots()
    }
    
    /**
     * Place spawners according to the specified placement pattern
     * 1: random place in the 500s for x and y positions that is not on a wall
     * 2: top right corner and bottom left corner
     * 3: bottom left corner, top right corner, middle x and y in 500s
     * 4: bottom left corner, top right corner, top left corner on the other side of the wall that the player spawns next to, and bottom right corner on the other side of the wall that the player spawn next to
     * 5: all of those places in 4 plus the middle
     */
    private fun placeSpawners() {
        when (spawnerCount) {
            1 -> place1Spawner()
            2 -> place2Spawners()
            3 -> place3Spawners()
            4 -> place4Spawners()
            5 -> place5Spawners()
        }
        
        Log.d(TAG, "Placed $spawnerCount robot spawners in multiplayer match with maxRobots=$MAX_ROBOTS_PER_SPAWNER each")
    }
    
    /**
     * Place 1 spawner: deterministic center position using normalized coordinates
     */
    private fun place1Spawner() {
        // Use fixed, deterministic position - center of maze (0.5, 0.5)
        val centerNormX = 0.5f
        val centerNormY = 0.5f
        
        // Always use exact center for consistency across devices
        val (screenX, screenY) = levelAdapter.mazeToScreenCoord(centerNormX, centerNormY)
        spawners.add(createRobotSpawner(screenX, screenY, 0))
        
        Log.d(TAG, "Placed spawner 1 at normalized coords ($centerNormX, $centerNormY) -> screen coords ($screenX, $screenY)")
    }
    
    /**
     * Place 2 spawners: top right corner and bottom left corner using normalized coordinates
     */
    private fun place2Spawners() {
        // Bottom left corner (normalized coordinates with buffer)
        val bottomLeftNormX = 0.15f // 15% from left edge
        val bottomLeftNormY = 0.85f // 85% from top (near bottom)
        val (screenX1, screenY1) = levelAdapter.mazeToScreenCoord(bottomLeftNormX, bottomLeftNormY)
        spawners.add(createRobotSpawner(screenX1, screenY1, 0))
        
        // Top right corner (normalized coordinates with buffer)
        val topRightNormX = 0.85f // 85% from left edge (near right)
        val topRightNormY = 0.15f // 15% from top (near top)
        val (screenX2, screenY2) = levelAdapter.mazeToScreenCoord(topRightNormX, topRightNormY)
        spawners.add(createRobotSpawner(screenX2, screenY2, 1))
    }
    
    /**
     * Place 3 spawners: bottom left corner, top right corner, middle using normalized coordinates
     */
    private fun place3Spawners() {
        // Use the 2-spawner placement first
        place2Spawners()
        
        // Add middle spawner
        val centerNormX = 0.5f
        val centerNormY = 0.5f
        val (screenX, screenY) = levelAdapter.mazeToScreenCoord(centerNormX, centerNormY)
        spawners.add(createRobotSpawner(screenX, screenY, 2))
    }
    
    /**
     * Place 4 spawners: all corners using normalized coordinates
     */
    private fun place4Spawners() {
        val buffer = 0.15f // 15% buffer from edges
        
        // Bottom left corner
        val (screenX1, screenY1) = levelAdapter.mazeToScreenCoord(buffer, 1.0f - buffer)
        spawners.add(createRobotSpawner(screenX1, screenY1, 0))
        
        // Top right corner
        val (screenX2, screenY2) = levelAdapter.mazeToScreenCoord(1.0f - buffer, buffer)
        spawners.add(createRobotSpawner(screenX2, screenY2, 1))
        
        // Top left corner
        val (screenX3, screenY3) = levelAdapter.mazeToScreenCoord(buffer, buffer)
        spawners.add(createRobotSpawner(screenX3, screenY3, 2))
        
        // Bottom right corner
        val (screenX4, screenY4) = levelAdapter.mazeToScreenCoord(1.0f - buffer, 1.0f - buffer)
        spawners.add(createRobotSpawner(screenX4, screenY4, 3))
    }
    
    /**
     * Place 5 spawners: all 4 corners plus the middle using normalized coordinates
     */
    private fun place5Spawners() {
        // Use the 4-spawner placement first
        place4Spawners()
        
        // Add center spawner
        val centerNormX = 0.5f
        val centerNormY = 0.5f
        val (screenX, screenY) = levelAdapter.mazeToScreenCoord(centerNormX, centerNormY)
        spawners.add(createRobotSpawner(screenX, screenY, 4))
    }
    
    /**
     * Find a safe position for a spawner around the target coordinates using normalized coordinates
     */
    private fun findSafeSpawnerPositionNormalized(targetNormX: Float, targetNormY: Float, searchRadius: Float): Pair<Float, Float>? {
        val maxAttempts = 20
        var attempts = 0
        
        while (attempts < maxAttempts) {
            // Generate position within search radius using deterministic random (in normalized space)
            val angle = deterministicRandom.nextFloat() * 2f * kotlin.math.PI.toFloat()
            val distance = deterministicRandom.nextFloat() * searchRadius
            
            val testNormX = targetNormX + kotlin.math.cos(angle) * distance
            val testNormY = targetNormY + kotlin.math.sin(angle) * distance
            
            // Check bounds (normalized coordinates should be 0.0-1.0)
            if (testNormX >= 0.1f && testNormX <= 0.9f &&
                testNormY >= 0.1f && testNormY <= 0.9f) {
                
                // Convert to screen coordinates to check collision
                val (screenX, screenY) = levelAdapter.mazeToScreenCoord(testNormX, testNormY)
                
                // Check if position is safe (no collision with walls)
                if (!levelAdapter.checkCollision(screenX, screenY)) {
                    // Check distance from other spawners
                    var validPosition = true
                    for (spawner in spawners) {
                        val spawnerPos = spawner.getPosition()
                        val distance = kotlin.math.sqrt((screenX - spawnerPos.first).let { it * it } + 
                                                       (screenY - spawnerPos.second).let { it * it })
                        if (distance < 120f) { // Minimum distance between spawners
                            validPosition = false
                            break
                        }
                    }
                    
                    if (validPosition) {
                        return Pair(testNormX, testNormY)
                    }
                }
            }
            
            attempts++
        }
        
        return null // No safe position found
    }
    
    /**
     * Find a safe position for a spawner around the target coordinates (legacy method, kept for compatibility)
     */
    private fun findSafeSpawnerPosition(targetX: Float, targetY: Float, searchRadius: Float): Pair<Float, Float>? {
        val maxAttempts = 20
        var attempts = 0
        
        while (attempts < maxAttempts) {
            // Generate position within search radius using deterministic random
            val angle = deterministicRandom.nextFloat() * 2f * kotlin.math.PI.toFloat()
            val distance = deterministicRandom.nextFloat() * searchRadius
            
            val testX = targetX + kotlin.math.cos(angle) * distance
            val testY = targetY + kotlin.math.sin(angle) * distance
            
            // Check bounds
            if (testX >= WALL_BUFFER && testX <= surface.w - WALL_BUFFER &&
                testY >= WALL_BUFFER && testY <= surface.h - WALL_BUFFER) {
                
                // Check distance from other spawners
                var validPosition = true
                for (spawner in spawners) {
                    val spawnerPos = spawner.getPosition()
                    val distance = kotlin.math.sqrt((testX - spawnerPos.first).let { it * it } + 
                                                   (testY - spawnerPos.second).let { it * it })
                    if (distance < 120f) { // Minimum distance between spawners
                        validPosition = false
                        break
                    }
                }
                
                if (validPosition) {
                    return Pair(testX, testY)
                }
            }
            
            attempts++
        }
        
        // Fallback to exact target if no safe position found
        if (targetX >= WALL_BUFFER && targetX <= surface.w - WALL_BUFFER &&
            targetY >= WALL_BUFFER && targetY <= surface.h - WALL_BUFFER) {
            return Pair(targetX, targetY)
        }
        
        return null
    }
    
    /**
     * Update all spawners and their robots with performance optimizations
     */
    fun update(deltaTime: Float) {
        val currentTime = System.currentTimeMillis()
        
        // Check if spawners should be fully initialized
        if (!spawnersInitialized && 
            currentTime - initializationStartTime >= spawnerInitializationDelay &&
            syncInitialized) {
            spawnersInitialized = true
            Log.d(TAG, "Spawners fully initialized after ${currentTime - initializationStartTime}ms delay")
        }
        
        // Only update spawners if they're properly initialized
        if (spawnersInitialized) {
            // Dynamic check: only update spawners if this device should spawn robots
            if (shouldSpawnRobots()) {
                spawners.forEach { spawner ->
                    spawner.update(deltaTime, levelAdapter, surface)
                }
                Log.v(TAG, "Updated ${spawners.size} spawners as robot-spawning device")
            } else {
                Log.v(TAG, "Skipping spawner updates - not the robot-spawning device")
            }
        }
        
        // Performance optimization: Update robots with culling and reduced frequency for remote robots
        updateRobotsOptimized(deltaTime, currentTime)
        
        // Periodic synchronization for tight multiplayer consistency
        performPeriodicSync()
    }
    
    /**
     * Optimized robot update method with performance improvements
     */
    private fun updateRobotsOptimized(deltaTime: Float, currentTime: Long) {
        // Performance culling: determine visible area
        val screenBounds = getScreenBounds()
        culledRobots.clear()
        
        // Always update local robots at full frequency
        localRobots.forEach { robot ->
            if (isRobotInUpdateArea(robot, screenBounds)) {
                robot.update(deltaTime, surface, levelAdapter)
            } else {
                culledRobots.add(robot)
            }
        }
        
        // Update remote robots with interpolation for smooth movement
        remoteRobots.forEach { (robotId, robot) ->
            if (isRobotInUpdateArea(robot, screenBounds)) {
                // Apply position interpolation for smooth movement
                updateRobotInterpolation(robotId, robot, currentTime)
                
                // Update robot AI/behavior at reduced frequency for performance
                val shouldUpdateRemoteRobots = currentTime - lastRemoteUpdateTime >= REMOTE_UPDATE_INTERVAL
                if (shouldUpdateRemoteRobots) {
                    // Use reduced deltaTime for smoother remote robot movement
                    val remoteDeltaTime = (currentTime - lastRemoteUpdateTime) / 1000f
                    robot.update(remoteDeltaTime, surface, levelAdapter)
                }
            } else {
                culledRobots.add(robot)
            }
        }
        
        // Update remote update time
        if (currentTime - lastRemoteUpdateTime >= REMOTE_UPDATE_INTERVAL) {
            lastRemoteUpdateTime = currentTime
        }
        
        Log.v(TAG, "Performance: Updated ${localRobots.size} local + ${remoteRobots.size} remote robots, culled ${culledRobots.size}")
    }
    
    /**
     * Get screen bounds extended by culling distance for performance calculations
     */
    private fun getScreenBounds(): android.graphics.RectF {
        return android.graphics.RectF(
            -CULLING_DISTANCE,
            -CULLING_DISTANCE,
            surface.w + CULLING_DISTANCE,
            surface.h + CULLING_DISTANCE
        )
    }
    
    /**
     * Check if robot is within the update area (screen + culling buffer)
     */
    private fun isRobotInUpdateArea(robot: Robot, screenBounds: android.graphics.RectF): Boolean {
        val position = robot.getPosition()
        return screenBounds.contains(position.first, position.second)
    }
    
    /**
     * Update robot position interpolation for smooth movement
     */
    private fun updateRobotInterpolation(robotId: String, robot: Robot, currentTime: Long) {
        val interpolationData = robotInterpolation[robotId] ?: return
        
        val elapsedTime = currentTime - interpolationData.interpolationStartTime
        val progress = (elapsedTime.toFloat() / interpolationData.interpolationDuration.toFloat()).coerceIn(0f, 1f)
        
        if (progress >= 1f) {
            // Interpolation complete - set final position and remove from tracking
            robot.setPosition(interpolationData.targetX, interpolationData.targetY)
            robotInterpolation.remove(robotId)
            Log.v(TAG, "Completed interpolation for robot $robotId at (${interpolationData.targetX}, ${interpolationData.targetY})")
        } else {
            // Calculate interpolated position using smooth easing
            val easedProgress = smoothStep(progress)
            val interpolatedX = interpolationData.startX + (interpolationData.targetX - interpolationData.startX) * easedProgress
            val interpolatedY = interpolationData.startY + (interpolationData.targetY - interpolationData.startY) * easedProgress
            
            robot.setPosition(interpolatedX, interpolatedY)
            Log.v(TAG, "Interpolating robot $robotId: progress=$progress, pos=($interpolatedX, $interpolatedY)")
        }
    }
    
    /**
     * Smooth step function for eased interpolation
     */
    private fun smoothStep(t: Float): Float {
        return t * t * (3f - 2f * t)
    }
    
    /**
     * Draw all spawners and their spawn effects
     */
    fun draw(canvas: Canvas) {
        spawners.forEach { spawner ->
            spawner.draw(canvas)
        }
    }
    
    /**
     * Draw all spawned robots with performance culling
     */
    fun drawRobots(canvas: Canvas) {
        // Performance optimization: Only draw robots that are visible or near the screen
        val screenBounds = getScreenBounds()
        var drawnCount = 0
        var culledCount = 0
        
        allSpawnedRobots.forEach { robot ->
            if (isRobotInUpdateArea(robot, screenBounds)) {
                robot.draw(canvas)
                drawnCount++
            } else {
                culledCount++
            }
        }
        
        // Log performance stats occasionally
        if (drawnCount + culledCount > 0 && (drawnCount + culledCount) % 10 == 0) {
            Log.v(TAG, "Render performance: Drew $drawnCount robots, culled $culledCount robots")
        }
    }
    
    /**
     * Get all spawned robots for game logic (collision, etc.)
     */
    fun getAllRobots(): List<Robot> = allSpawnedRobots.toList()
    
    /**
     * Get all spawners
     */
    fun getSpawners(): List<RobotSpawner> = spawners.toList()
    
    /**
     * Remove a destroyed robot from all spawners
     */
    fun removeRobot(robot: Robot) {
        // Only decrement spawner count for local robots
        if (localRobots.contains(robot)) {
            spawners.forEach { spawner ->
                spawner.decrementSpawnedRobotCount()
            }
            localRobots.remove(robot)
            
            // Remove from Firebase sync tracking
            robotIdMap[robot]?.let { robotId ->
                multiplayerManager?.removeRobotState(robotId)
                robotIdMap.remove(robot)
                recentConversions.remove(robotId) // Clean up conversion tracking
            }
        }
        
        allSpawnedRobots.remove(robot)
    }
    
    /**
     * Deactivate all spawners (end of match)
     */
    fun deactivateAll() {
        spawners.forEach { spawner ->
            spawner.disable()
        }
        allSpawnedRobots.clear()
        localRobots.clear()
        remoteRobots.clear()
        robotIdMap.clear()
        robotInterpolation.clear() // Clean up interpolation data
        recentConversions.clear() // Clean up conversion tracking
    }
    
    /**
     * Get spawner count
     */
    fun getSpawnerCount(): Int = spawners.size
    
    /**
     * Handle player interaction with spawners for conversion
     */
    fun handlePlayerInteraction(playerX: Float, playerY: Float, playerColor: Int, paintSurface: PaintSurface): Boolean {
        var interactionOccurred = false
        
        // Check robot interactions (painting robots for conversion)
        // Allow both players to interact with all robots for conversion
        allSpawnedRobots.forEach { robot ->
            val robotBounds = robot.getBounds()
            if (robotBounds.contains(playerX, playerY)) {
                // Determine if this is a local or remote robot
                val robotId = if (localRobots.contains(robot)) {
                    robotIdMap[robot] ?: "unknown"
                } else {
                    // Find the robotId for remote robots
                    remoteRobots.entries.find { it.value == robot }?.key ?: "unknown"
                }
                
                val robotType = if (localRobots.contains(robot)) "local" else "remote"
                Log.d(TAG, "Player attempting to convert $robotType robot $robotId")
                
                if (robot.paintRobot(playerColor, paintSurface)) {
                    // Robot was successfully converted
                    interactionOccurred = true
                    Log.d(TAG, "Successfully converted $robotType robot $robotId to color ${Integer.toHexString(playerColor)}")
                    
                    // Sync to Firebase regardless of whether it's local or remote
                    if (localRobots.contains(robot)) {
                        // For local robots, sync normally
                        syncRobotState(robot)
                    } else {
                        // For remote robots, sync the conversion state
                        syncRemoteRobotConversion(robotId, robot, playerColor)
                        // Mark this robot as recently converted to prevent host sync conflicts
                        recentConversions[robotId] = System.currentTimeMillis()
                    }
                }
            }
        }
        
        // Check robot spawner interactions (painting spawners for conversion)
        spawners.forEach { spawner ->
            val spawnerBounds = spawner.getBounds()
            // Expand interaction area slightly for easier interaction
            val expandedBounds = android.graphics.RectF(
                spawnerBounds.left - 10f,
                spawnerBounds.top - 10f, 
                spawnerBounds.right + 10f,
                spawnerBounds.bottom + 10f
            )
            
            if (expandedBounds.contains(playerX, playerY)) {
                if (spawner.paintSpawner(playerColor, paintSurface)) {
                    // Spawner was successfully converted - sync to Firebase
                    syncSpawnerStates()
                    interactionOccurred = true
                }
            }
        }
        
        return interactionOccurred
    }
    
    // Firebase Synchronization Methods
    
    /**
     * Perform periodic synchronization checks for robots and spawners with different frequencies
     */
    private fun performPeriodicSync() {
        if (!syncInitialized) return
        
        val currentTime = System.currentTimeMillis()
        
        // High-frequency robot position sync for smooth movement (every 100ms)
        if (currentTime - lastPositionSync >= POSITION_SYNC_INTERVAL) {
            // Only sync local robot positions for smooth movement on remote devices
            localRobots.forEach { robot ->
                syncRobotState(robot)
            }
            lastPositionSync = currentTime
            Log.v(TAG, "Synced positions for ${localRobots.size} local robots")
        }
        
        // Lower-frequency spawner state sync (every 1 second)
        if (currentTime - lastSpawnerSync >= SPAWNER_SYNC_INTERVAL) {
            // Sync spawner states (conversion, activation, etc.)
            syncSpawnerStates()
            lastSpawnerSync = currentTime
            Log.v(TAG, "Synced ${spawners.size} spawner states")
        }
    }
    
    /**
     * Sync all spawner states to Firebase
     */
    private fun syncSpawnerStates() {
        spawners.forEachIndexed { index, spawner ->
            val spawnerPos = spawner.getPosition()
            val (normX, normY) = if (level is com.spiritwisestudios.inkrollers.MazeLevel) {
                level.screenToMazeCoord(spawnerPos.first, spawnerPos.second)
            } else {
                Pair(spawnerPos.first, spawnerPos.second)
            }
            
            val spawnerState = RobotSpawnerState(
                normX = normX,
                normY = normY,
                isConverted = spawner.isFullyConverted(),
                playerColor = spawner.getPaintColor(),
                isActive = spawner.isActive(),
                spawnedRobotCount = spawner.getSpawnedRobotCount()
            )
            
            multiplayerManager?.updateRobotSpawnerState(index, spawnerState)
        }
    }
    
    /**
     * Update remote spawner state from Firebase
     */
    private fun updateRemoteSpawnerState(spawnerIndex: Int, spawnerState: RobotSpawnerState) {
        if (spawnerIndex < 0 || spawnerIndex >= spawners.size) return
        
        val spawner = spawners[spawnerIndex]
        
        // Update spawner conversion state if different
        if (spawner.isFullyConverted() != spawnerState.isConverted) {
            if (spawnerState.isConverted) {
                // Convert spawner to match remote state
                repeat(10) { // Ensure full conversion
                    spawner.paintSpawner(spawnerState.playerColor, surface)
                }
            }
        }
        
        // Update active state
        if (spawner.isActive() != spawnerState.isActive) {
            if (spawnerState.isActive) {
                spawner.enable()
            } else {
                spawner.disable()
            }
        }
        
        Log.v(TAG, "Updated remote spawner $spawnerIndex state")
    }
    
    /**
     * Sync remote robot conversion state to Firebase when joining player converts host's robot
     */
    private fun syncRemoteRobotConversion(robotId: String, robot: Robot, playerColor: Int) {
        if (!syncInitialized) return
        
        Log.d(TAG, "Syncing remote robot conversion for $robotId")
        
        val robotPos = robot.getPosition()
        // Convert screen coordinates to normalized coordinates (0.0 to 1.0)
        val normX = robotPos.first / surface.w.toFloat()
        val normY = robotPos.second / surface.h.toFloat()
        
        val robotState = RobotState(
            id = robotId,
            normX = normX,
            normY = normY,
            isConverted = robot.isFullyConverted(),
            paintColor = playerColor, // Use the player's color for conversion
            isActive = true,
            spawnerIndex = findSpawnerIndex(robot)
        )
        
        multiplayerManager?.updateRobotState(robotId, robotState)
        Log.d(TAG, "Synced remote robot $robotId conversion to color ${Integer.toHexString(playerColor)}")
    }
    
    /**
     * Sync robot state to Firebase when robot is created or changes
     */
    private fun syncRobotState(robot: Robot) {
        if (!syncInitialized) return
        
        val robotId = robotIdMap[robot] ?: run {
            Log.w(TAG, "Attempted to sync robot without robotId - this should not happen!")
            return
        }
        
        // Check if there was a recent conversion update for this robot
        val currentTime = System.currentTimeMillis()
        val recentConversionTime = recentConversions[robotId]
        val wasRecentlyConverted = recentConversionTime != null && (currentTime - recentConversionTime) < 2000L // 2 second grace period
        
        if (wasRecentlyConverted) {
            Log.d(TAG, "Skipping sync for robot $robotId - recent conversion in progress")
            return
        }
        
        val robotPos = robot.getPosition()
        // Convert screen coordinates to normalized coordinates (0.0 to 1.0)
        val normX = robotPos.first / surface.w.toFloat()
        val normY = robotPos.second / surface.h.toFloat()
        
        val robotState = RobotState(
            id = robotId,
            normX = normX,
            normY = normY,
            isConverted = robot.isFullyConverted(),
            paintColor = robot.getPaintColor(),
            isActive = true,
            spawnerIndex = findSpawnerIndex(robot)
        )
        
        multiplayerManager?.updateRobotState(robotId, robotState)
        Log.v(TAG, "Synced robot $robotId position: (${robotPos.first}, ${robotPos.second}) -> normalized ($normX, $normY)")
    }
    
    /**
     * Update remote robot state from Firebase
     */
    private fun updateRemoteRobotState(robotId: String, robotState: RobotState) {
        // Check if this is a conversion update for one of our local robots
        val existingLocalRobot = robotIdMap.entries.find { it.value == robotId }?.key
        if (existingLocalRobot != null && localRobots.contains(existingLocalRobot)) {
            Log.d(TAG, "Received conversion update for local robot $robotId")
            // Apply conversion state to local robot (from joining player's interaction)
            if (robotState.isConverted && !existingLocalRobot.isFullyConverted()) {
                Log.d(TAG, "Applying remote conversion to local robot $robotId")
                repeat(15) { // Ensure full conversion
                    existingLocalRobot.paintRobot(robotState.paintColor, surface)
                }
                // Mark this robot as recently converted to prevent immediate re-sync
                recentConversions[robotId] = System.currentTimeMillis()
            }
            return // Don't create a duplicate remote robot
        }
        
        // Also check if we already have this remote robot
        val existingRemote = remoteRobots[robotId]
        Log.d(TAG, "Processing remote robot $robotId update - exists: ${existingRemote != null}")
        
        // Convert normalized coordinates back to screen coordinates
        val screenX = robotState.normX * surface.w.toFloat()
        val screenY = robotState.normY * surface.h.toFloat()
        
        val existingRobot = remoteRobots[robotId]
        if (existingRobot != null) {
            // Set up smooth interpolation for position updates
            val currentPos = existingRobot.getPosition()
            val currentTime = System.currentTimeMillis()
            
            // Only interpolate if the position actually changed significantly
            val positionChanged = kotlin.math.abs(currentPos.first - screenX) > 2f || 
                                kotlin.math.abs(currentPos.second - screenY) > 2f
            
            if (positionChanged) {
                robotInterpolation[robotId] = RobotInterpolationData(
                    targetX = screenX,
                    targetY = screenY,
                    startX = currentPos.first,
                    startY = currentPos.second,
                    interpolationStartTime = currentTime,
                    interpolationDuration = POSITION_SYNC_INTERVAL
                )
                Log.v(TAG, "Started interpolation for robot $robotId from (${currentPos.first}, ${currentPos.second}) to ($screenX, $screenY)")
            }
            
            // Update conversion state if needed
            if (existingRobot.isFullyConverted() != robotState.isConverted && robotState.isConverted) {
                // Convert robot to match remote state
                repeat(15) { // Ensure full conversion
                    existingRobot.paintRobot(robotState.paintColor, surface)
                }
            }
            
            Log.v(TAG, "Updated remote robot $robotId target position to ($screenX, $screenY)")
        } else {
            // Create new remote robot
            createRemoteRobot(robotId, robotState, screenX, screenY)
        }
    }
    
    /**
     * Create a new remote robot from Firebase state
     */
    private fun createRemoteRobot(robotId: String, robotState: RobotState, screenX: Float, screenY: Float) {
        // Performance optimization: Check global robot limit
        if (allSpawnedRobots.size >= MAX_TOTAL_ROBOTS) {
            Log.w(TAG, "Cannot create remote robot $robotId: reached global limit of $MAX_TOTAL_ROBOTS robots for performance")
            return
        }
        
        // Create robot data for remote robot
        val robotData = com.spiritwisestudios.inkrollers.campaign.RobotData(
            x = screenX,
            y = screenY,
            patrolPath = emptyList(), // Remote robots follow Firebase updates, not patrol paths
            unpaintRadius = ROBOT_UNPAINT_RADIUS
        )
        
        // Create unique deterministic seed for remote robot
        val robotSeed = deterministicRandom.nextLong()
        val remoteRobot = Robot(screenX, screenY, robotData, robotState.paintColor, robotSeed, true)
        
        // Convert robot if needed
        if (robotState.isConverted) {
            repeat(15) { // Ensure full conversion
                remoteRobot.paintRobot(robotState.paintColor, surface)
            }
        }
        
        // Add to tracking (only allSpawnedRobots for rendering, NOT localRobots)
        remoteRobots[robotId] = remoteRobot
        allSpawnedRobots.add(remoteRobot)
        
        Log.d(TAG, "Created REMOTE robot $robotId at position ($screenX, $screenY), converted: ${robotState.isConverted}, color: ${Integer.toHexString(robotState.paintColor)}")
        Log.d(TAG, "Robot counts after remote creation - Total: ${allSpawnedRobots.size}, Local: ${localRobots.size}, Remote: ${remoteRobots.size}")
        
        // Debug robot state when remote robots are created
        debugRobotState()
    }
    
    /**
     * Remove remote robot from Firebase
     */
    private fun removeRemoteRobot(robotId: String) {
        remoteRobots.remove(robotId)?.let { robot ->
            allSpawnedRobots.remove(robot)
            robotInterpolation.remove(robotId) // Clean up interpolation data
            recentConversions.remove(robotId) // Clean up conversion tracking
            Log.v(TAG, "Removed remote robot $robotId")
        }
    }
    
    /**
     * Find which spawner index a robot belongs to
     */
    private fun findSpawnerIndex(robot: Robot): Int {
        // For now, return 0 as a placeholder
        // In a full implementation, you'd track which spawner created each robot
        return 0
    }
    
    /**
     * Debug method to log current robot state
     */
    private fun debugRobotState() {
        Log.d(TAG, "=== ROBOT STATE DEBUG ===")
        Log.d(TAG, "Total robots: ${allSpawnedRobots.size}")
        Log.d(TAG, "Local robots: ${localRobots.size}")
        Log.d(TAG, "Remote robots: ${remoteRobots.size}")
        Log.d(TAG, "Robot ID mappings: ${robotIdMap.size}")
        
        localRobots.forEachIndexed { index, robot ->
            val pos = robot.getPosition()
            val converted = robot.isFullyConverted()
            val color = robot.getPaintColor()
            val robotId = robotIdMap[robot] ?: "NO_ID"
            Log.d(TAG, "Local[$index]: ID=$robotId, pos=(${pos.first}, ${pos.second}), converted=$converted, color=${Integer.toHexString(color)}")
        }
        
        remoteRobots.entries.forEachIndexed { index, (robotId, robot) ->
            val pos = robot.getPosition()
            val converted = robot.isFullyConverted()
            val color = robot.getPaintColor()
            Log.d(TAG, "Remote[$index]: ID=$robotId, pos=(${pos.first}, ${pos.second}), converted=$converted, color=${Integer.toHexString(color)}")
        }
        Log.d(TAG, "=== END DEBUG ===")
    }
    
    /**
     * Add a spawned robot to the manager and sync to Firebase
     */
    fun addSpawnedRobot(robot: Robot) {
        // Performance optimization: Check global robot limit
        if (allSpawnedRobots.size >= MAX_TOTAL_ROBOTS) {
            Log.w(TAG, "Cannot add robot: reached global limit of $MAX_TOTAL_ROBOTS robots for performance")
            return
        }
        
        // IMPORTANT: Assign robotId BEFORE adding to any lists to prevent race conditions
        val robotId = UUID.randomUUID().toString()
        robotIdMap[robot] = robotId
        
        allSpawnedRobots.add(robot)
        localRobots.add(robot) // Track as local robot
        
        val robotPos = robot.getPosition()
        val isConverted = robot.isFullyConverted()
        val paintColor = robot.getPaintColor()
        
        Log.d(TAG, "Added LOCAL robot $robotId at (${robotPos.first}, ${robotPos.second}), converted: $isConverted, color: ${Integer.toHexString(paintColor)}")
        Log.d(TAG, "Robot counts - Total: ${allSpawnedRobots.size}, Local: ${localRobots.size}, Remote: ${remoteRobots.size}")
        
        syncRobotState(robot) // Sync to Firebase when robot is added
        
        // Debug robot state when new robots are added
        debugRobotState()
    }
}