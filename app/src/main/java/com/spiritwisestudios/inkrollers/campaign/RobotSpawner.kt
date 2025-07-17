package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Robot Spawner class for creating robots at timed intervals in campaign mode.
 * A stationary device that spawns robots with patrol paths around the spawner area.
 */
class RobotSpawner(
    private val spawnerData: RobotSpawnerData
) {
    companion object {
        private const val TAG = "RobotSpawner"
        private const val SPAWNER_SIZE = 50f
        private const val SPAWN_RADIUS = 80f // How far from spawner robots can spawn
        private const val DEFAULT_PATROL_RADIUS = 120f // Default patrol area around spawner
    }

    // Spawner state
    private var x: Float = spawnerData.x
    private var y: Float = spawnerData.y
    private var lastSpawnTime: Long = 0
    private var spawnedRobotCount: Int = 0
    private var isActive: Boolean = true
    
    // Visual representation
    private val spawnerBodyPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.DKGRAY
    }
    
    private val spawnerBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.RED
    }
    
    private val spawnerDetailsPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.YELLOW
    }
    
    private val spawnEffectPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.CYAN
    }
    
    // Spawn timing animation
    private var spawnAnimationTimer: Float = 0f
    private var isSpawning: Boolean = false
    
    /**
     * Update spawner state and check for robot spawning
     */
    fun update(deltaTime: Float, campaignLevel: CampaignLevel) {
        if (!isActive) return
        
        val currentTime = System.currentTimeMillis()
        
        // Update spawn animation
        if (isSpawning) {
            spawnAnimationTimer += deltaTime
            if (spawnAnimationTimer >= 1.0f) {
                isSpawning = false
                spawnAnimationTimer = 0f
            }
        }
        
        // Check if we should spawn a robot
        val timeSinceLastSpawn = currentTime - lastSpawnTime
        if (timeSinceLastSpawn >= spawnerData.spawnInterval) {
            // Check if we haven't reached the maximum spawn limit
            if (spawnerData.maxRobots <= 0 || spawnedRobotCount < spawnerData.maxRobots) {
                spawnRobot(campaignLevel)
                lastSpawnTime = currentTime
            }
        }
    }
    
    /**
     * Spawn a new robot near the spawner
     */
    private fun spawnRobot(campaignLevel: CampaignLevel) {
        // Find a safe spawn position near the spawner
        val spawnPosition = findSafeSpawnPosition(campaignLevel)
        if (spawnPosition == null) {
            Log.w(TAG, "Could not find safe spawn position near spawner at ($x, $y)")
            return
        }
        
        // Create patrol path around the spawner area
        val patrolPath = generatePatrolPath(spawnPosition, campaignLevel)
        
        // Create robot data for the spawned robot
        val robotData = RobotData(
            x = spawnPosition.first,
            y = spawnPosition.second,
            patrolPath = patrolPath,
            unpaintRadius = spawnerData.spawnedRobotUnpaintRadius
        )
        
        // Create and add the robot to the campaign level
        val newRobot = Robot(spawnPosition.first, spawnPosition.second, robotData)
        campaignLevel.addSpawnedRobot(newRobot)
        
        spawnedRobotCount++
        isSpawning = true
        spawnAnimationTimer = 0f
        
        Log.d(TAG, "Spawned robot #$spawnedRobotCount at position (${spawnPosition.first}, ${spawnPosition.second})")
    }
    
    /**
     * Find a safe position to spawn a robot near the spawner
     */
    private fun findSafeSpawnPosition(campaignLevel: CampaignLevel): Pair<Float, Float>? {
        val maxAttempts = 20
        var attempts = 0
        
        while (attempts < maxAttempts) {
            // Generate random position within spawn radius
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val distance = Random.nextFloat() * SPAWN_RADIUS
            
            val spawnX = x + cos(angle) * distance
            val spawnY = y + sin(angle) * distance
            
            // Check if position is safe (no collision)
            if (!campaignLevel.checkCollision(spawnX, spawnY)) {
                return Pair(spawnX, spawnY)
            }
            
            attempts++
        }
        
        return null
    }
    
    /**
     * Generate a patrol path for spawned robots around the spawner area
     */
    private fun generatePatrolPath(spawnPosition: Pair<Float, Float>, campaignLevel: CampaignLevel): List<Pair<Float, Float>> {
        val patrolPath = mutableListOf<Pair<Float, Float>>()
        
        // If custom patrol path is specified, use it (relative to spawner position)
        if (spawnerData.spawnedRobotPatrolPath.isNotEmpty()) {
            spawnerData.spawnedRobotPatrolPath.forEach { (relX, relY) ->
                val absoluteX = x + relX
                val absoluteY = y + relY
                if (!campaignLevel.checkCollision(absoluteX, absoluteY)) {
                    patrolPath.add(Pair(absoluteX, absoluteY))
                }
            }
        } else {
            // Generate default circular patrol path around spawner
            val numPoints = 4
            for (i in 0 until numPoints) {
                val angle = (i * 2 * PI / numPoints).toFloat()
                val patrolX = x + cos(angle) * DEFAULT_PATROL_RADIUS
                val patrolY = y + sin(angle) * DEFAULT_PATROL_RADIUS
                
                if (!campaignLevel.checkCollision(patrolX, patrolY)) {
                    patrolPath.add(Pair(patrolX, patrolY))
                }
            }
        }
        
        // If no valid patrol points found, create a simple back-and-forth pattern
        if (patrolPath.isEmpty()) {
            patrolPath.add(spawnPosition)
            patrolPath.add(Pair(spawnPosition.first + 50f, spawnPosition.second))
        }
        
        return patrolPath
    }
    
    /**
     * Draw the robot spawner on the canvas
     */
    fun draw(canvas: Canvas) {
        if (!isActive) return
        
        // Draw spawner body (large square device)
        val halfSize = SPAWNER_SIZE / 2
        canvas.drawRect(
            x - halfSize,
            y - halfSize,
            x + halfSize,
            y + halfSize,
            spawnerBodyPaint
        )
        
        // Draw border
        canvas.drawRect(
            x - halfSize,
            y - halfSize,
            x + halfSize,
            y + halfSize,
            spawnerBorderPaint
        )
        
        // Draw spawner details (antenna/core)
        canvas.drawCircle(x, y, 8f, spawnerDetailsPaint)
        canvas.drawRect(x - 15f, y - 2f, x + 15f, y + 2f, spawnerDetailsPaint)
        canvas.drawRect(x - 2f, y - 15f, x + 2f, y + 15f, spawnerDetailsPaint)
        
        // Draw spawn animation effect
        if (isSpawning) {
            val pulseRadius = SPAWNER_SIZE * (1f + spawnAnimationTimer * 0.5f)
            spawnEffectPaint.alpha = ((1f - spawnAnimationTimer) * 255).toInt()
            canvas.drawCircle(x, y, pulseRadius, spawnEffectPaint)
        }
        
        // Draw spawn radius indicator (subtle)
        if (spawnerData.showSpawnRadius) {
            val radiusPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.GRAY
                alpha = 60
            }
            canvas.drawCircle(x, y, SPAWN_RADIUS, radiusPaint)
        }
    }
    
    /**
     * Check if a point is within the spawner's collision area
     */
    fun checkCollision(checkX: Float, checkY: Float): Boolean {
        val halfSize = SPAWNER_SIZE / 2
        return checkX >= x - halfSize && checkX <= x + halfSize &&
               checkY >= y - halfSize && checkY <= y + halfSize
    }
    
    /**
     * Get spawner bounds for interaction detection
     */
    fun getBounds(): RectF {
        val halfSize = SPAWNER_SIZE / 2
        return RectF(
            x - halfSize,
            y - halfSize,
            x + halfSize,
            y + halfSize
        )
    }
    
    /**
     * Get spawner position
     */
    fun getPosition(): Pair<Float, Float> = Pair(x, y)
    
    /**
     * Disable the spawner (stops spawning but remains visible)
     */
    fun disable() {
        isActive = false
        spawnerBorderPaint.color = Color.GRAY
    }
    
    /**
     * Enable the spawner
     */
    fun enable() {
        isActive = true
        spawnerBorderPaint.color = Color.RED
    }
    
    /**
     * Check if spawner is active
     */
    fun isActive(): Boolean = isActive
    
    /**
     * Get current spawned robot count
     */
    fun getSpawnedRobotCount(): Int = spawnedRobotCount
    
    /**
     * Reset spawned robot count (call when robots are destroyed)
     */
    fun decrementSpawnedRobotCount() {
        if (spawnedRobotCount > 0) {
            spawnedRobotCount--
        }
    }
} 