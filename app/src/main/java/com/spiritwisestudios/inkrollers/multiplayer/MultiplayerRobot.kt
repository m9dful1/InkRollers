package com.spiritwisestudios.inkrollers.multiplayer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.PaintSurface
import com.spiritwisestudios.inkrollers.RobotState
import com.spiritwisestudios.inkrollers.campaign.Robot
import com.spiritwisestudios.inkrollers.campaign.RobotData
import kotlin.math.*

/**
 * Multiplayer robot that uses deterministic AI and frequent synchronization
 * to ensure consistent behavior across all devices
 */
class MultiplayerRobot(
    startX: Float,
    startY: Float,
    private val robotData: RobotData,
    private val robotId: String,
    private val spawnerIndex: Int,
    private val multiplayerManager: MultiplayerManager?,
    customPaintColor: Int = Color.GREEN
) {
    companion object {
        private const val TAG = "MultiplayerRobot"
        private const val ROBOT_RADIUS = 30f
        private const val MOVE_SPEED = 50f
        private const val SYNC_INTERVAL = 100L // Sync every 100ms for tight synchronization
        private const val POSITION_TOLERANCE = 5f // Allow small position differences
    }
    
    // Core robot instance for rendering and basic functionality
    private val coreRobot = Robot(startX, startY, robotData, customPaintColor)
    
    // Multiplayer synchronization state
    private var lastSyncTime = 0L
    private var isLocallyControlled = true // This device controls this robot's AI
    private var remoteTargetX = startX
    private var remoteTargetY = startY
    private var isReceivingRemoteUpdates = false
    
    // Deterministic AI state (uses game time instead of real time for consistency)
    private var gameTime = 0f
    private var lastDecisionTime = 0f
    private val decisionInterval = 1000f // Make decisions every 1 second of game time
    
    /**
     * Update robot with deterministic AI and synchronization
     */
    fun update(deltaTime: Float, paintSurface: PaintSurface, level: Level?) {
        gameTime += deltaTime
        
        if (isLocallyControlled && !isReceivingRemoteUpdates) {
            // This device controls the robot - use deterministic AI
            updateDeterministicAI(deltaTime, paintSurface, level)
            
            // Sync to other devices periodically
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSyncTime >= SYNC_INTERVAL) {
                syncToRemote()
                lastSyncTime = currentTime
            }
        } else {
            // Follow remote updates
            interpolateToRemotePosition(deltaTime, level)
        }
        
        // Always update the core robot for painting/unpaint behavior
        coreRobot.update(deltaTime, paintSurface, level)
    }
    
    /**
     * Deterministic AI that makes the same decisions on all devices
     */
    private fun updateDeterministicAI(deltaTime: Float, paintSurface: PaintSurface, level: Level?) {
        // Make decisions at fixed intervals using game time
        if (gameTime - lastDecisionTime >= decisionInterval) {
            makeNextDecision(paintSurface, level)
            lastDecisionTime = gameTime
            
            Log.v(TAG, "Robot $robotId made decision at game time $gameTime")
        }
        
        // Execute current movement
        executeMovement(deltaTime, level)
    }
    
    /**
     * Make deterministic decisions based on current game state
     */
    private fun makeNextDecision(paintSurface: PaintSurface, level: Level?) {
        val position = coreRobot.getPosition()
        val currentX = position.first
        val currentY = position.second
        
        if (!coreRobot.isFullyConverted()) {
            // Unconverted robot: seek player paint deterministically
            val nearestPaint = findNearestTargetDeterministic(currentX, currentY, paintSurface, level, true)
            if (nearestPaint != null) {
                setTarget(nearestPaint.first, nearestPaint.second)
            } else {
                // No paint found, patrol deterministically
                patrolDeterministically()
            }
        } else {
            // Converted robot: seek unpainted areas deterministically
            val nearestUnpainted = findNearestTargetDeterministic(currentX, currentY, paintSurface, level, false)
            if (nearestUnpainted != null) {
                setTarget(nearestUnpainted.first, nearestUnpainted.second)
            } else {
                patrolDeterministically()
            }
        }
    }
    
    /**
     * Find nearest target using deterministic search (no randomness)
     */
    private fun findNearestTargetDeterministic(
        startX: Float, 
        startY: Float, 
        paintSurface: PaintSurface, 
        level: Level?,
        seekingPaint: Boolean
    ): Pair<Float, Float>? {
        val searchRadius = 150f
        val stepSize = 15f
        
        // Use deterministic spiral search pattern
        for (radius in stepSize.toInt() until searchRadius.toInt() step stepSize.toInt()) {
            val steps = (2 * PI * radius / stepSize).toInt()
            for (step in 0 until steps) {
                val angle = (step * 2 * PI / steps).toFloat()
                val testX = startX + cos(angle) * radius
                val testY = startY + sin(angle) * radius
                
                if (testX >= 0 && testX < paintSurface.w && testY >= 0 && testY < paintSurface.h) {
                    val pixelColor = paintSurface.getPixelColor(testX.toInt(), testY.toInt())
                    
                    if (seekingPaint) {
                        // Looking for player paint
                        if (pixelColor != Color.TRANSPARENT && 
                            Color.alpha(pixelColor) > 0 && 
                            pixelColor != Color.GREEN) {
                            return Pair(testX, testY)
                        }
                    } else {
                        // Looking for unpainted areas
                        if ((pixelColor == Color.TRANSPARENT || Color.alpha(pixelColor) == 0) &&
                            level?.checkCollision(testX, testY) != true) {
                            return Pair(testX, testY)
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Patrol using deterministic pattern
     */
    private fun patrolDeterministically() {
        if (robotData.patrolPath.isNotEmpty()) {
            // Use existing patrol path
            val patrolIndex = ((gameTime / 3000f) % robotData.patrolPath.size).toInt()
            val targetPoint = robotData.patrolPath[patrolIndex]
            setTarget(targetPoint.first, targetPoint.second)
        } else {
            // Create deterministic circular patrol around spawn point
            val angle = (gameTime / 5000f) * 2 * PI.toFloat()
            val radius = 80f
            val centerX = robotData.x
            val centerY = robotData.y
            val targetX = centerX + cos(angle) * radius
            val targetY = centerY + sin(angle) * radius
            setTarget(targetX, targetY)
        }
    }
    
    /**
     * Set movement target
     */
    private fun setTarget(targetX: Float, targetY: Float) {
        remoteTargetX = targetX
        remoteTargetY = targetY
    }
    
    /**
     * Execute movement towards current target
     */
    private fun executeMovement(deltaTime: Float, level: Level?) {
        val position = coreRobot.getPosition()
        val currentX = position.first
        val currentY = position.second
        
        val dx = remoteTargetX - currentX
        val dy = remoteTargetY - currentY
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 5f) {
            val moveAmount = MOVE_SPEED * deltaTime
            val normalizedDx = dx / distance
            val normalizedDy = dy / distance
            
            val newX = currentX + normalizedDx * moveAmount
            val newY = currentY + normalizedDy * moveAmount
            
            // Check collision before moving
            if (level?.checkCollision(newX, newY) != true) {
                // Move the core robot by updating its position
                // Note: This would require adding a setPosition method to Robot class
                Log.v(TAG, "Robot $robotId moving to ($newX, $newY)")
            }
        }
    }
    
    /**
     * Interpolate to remote position when receiving updates from other devices
     */
    private fun interpolateToRemotePosition(deltaTime: Float, level: Level?) {
        val position = coreRobot.getPosition()
        val currentX = position.first
        val currentY = position.second
        
        val dx = remoteTargetX - currentX
        val dy = remoteTargetY - currentY
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > POSITION_TOLERANCE) {
            // Smoothly interpolate to remote position
            val moveAmount = MOVE_SPEED * deltaTime * 1.2f // Slightly faster to catch up
            val normalizedDx = dx / distance
            val normalizedDy = dy / distance
            
            val newX = currentX + normalizedDx * moveAmount
            val newY = currentY + normalizedDy * moveAmount
            
            if (level?.checkCollision(newX, newY) != true) {
                // Update position - would need setPosition method in Robot class
                Log.v(TAG, "Robot $robotId interpolating to remote position ($newX, $newY)")
            }
        }
    }
    
    /**
     * Sync robot state to Firebase
     */
    private fun syncToRemote() {
        val position = coreRobot.getPosition()
        val robotState = RobotState(
            id = robotId,
            normX = position.first / 1000f, // Convert to normalized coordinates
            normY = position.second / 1000f,
            isConverted = coreRobot.isFullyConverted(),
            paintColor = coreRobot.getPaintColor(),
            isActive = true,
            spawnerIndex = spawnerIndex
        )
        
        multiplayerManager?.updateRobotState(robotId, robotState)
    }
    
    /**
     * Update from remote robot state
     */
    fun updateFromRemoteState(robotState: RobotState) {
        isReceivingRemoteUpdates = true
        isLocallyControlled = false
        
        // Convert normalized coordinates back to screen coordinates
        remoteTargetX = robotState.normX * 1000f
        remoteTargetY = robotState.normY * 1000f
        
        // Update conversion state if different
        if (coreRobot.isFullyConverted() != robotState.isConverted && robotState.isConverted) {
            // Convert robot to match remote state
            // This would require access to paintSurface
            Log.d(TAG, "Robot $robotId converted remotely")
        }
    }
    
    // Delegate methods to core robot
    fun draw(canvas: Canvas) = coreRobot.draw(canvas)
    fun getBounds(): RectF = coreRobot.getBounds()
    fun getPosition(): Pair<Float, Float> = coreRobot.getPosition()
    fun isFullyConverted(): Boolean = coreRobot.isFullyConverted()
    fun getPaintColor(): Int = coreRobot.getPaintColor()
    fun paintRobot(playerColor: Int, paintSurface: PaintSurface): Boolean = coreRobot.paintRobot(playerColor, paintSurface)
}