package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.spiritwisestudios.inkrollers.PaintSurface
import kotlin.math.*

/**
 * Robot class for Color Suppressor Robots in campaign mode.
 * Robots patrol areas, remove paint, and can be converted by painting them.
 */
class Robot(
    startX: Float, 
    startY: Float,
    private val robotData: RobotData
) {
    companion object {
        private const val TAG = "Robot"
        private const val ROBOT_RADIUS = 30f
        private const val MOVE_SPEED = 50f // Slower than player
        private const val CONVERSION_THRESHOLD = 100f // Amount of paint needed to convert
        private const val UNPAINT_RADIUS = 40f // Radius around robot that gets unpainted
        private const val PAINT_FREQUENCY = 0.1f // How often robot removes paint (per second)
    }
    
    // Robot state
    private var x: Float = startX
    private var y: Float = startY
    private var isConverted: Boolean = false
    private var conversionProgress: Float = 0f
    private var currentPatrolIndex: Int = 0
    private var patrolDirection: Int = 1 // 1 for forward, -1 for backward
    
    // Timing for paint removal
    private var lastUnpaintTime: Long = 0
    
    // Visual state
    private val robotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val conversionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.CYAN
    }
    
    /**
     * Update robot state, movement, and actions
     */
    fun update(deltaTime: Float, paintSurface: PaintSurface) {
        if (!isConverted) {
            // Move along patrol path
            updatePatrol(deltaTime)
            
            // Remove paint in area around robot
            removeNearbyPaint(paintSurface)
        } else {
            // Converted robots paint instead of removing paint
            paintNearbyArea(paintSurface)
        }
    }
    
    /**
     * Handle player painting the robot for conversion
     */
    fun paintRobot(playerColor: Int, paintSurface: PaintSurface): Boolean {
        if (isConverted) return false
        
        conversionProgress += 10f // Each paint action adds progress
        
        if (conversionProgress >= CONVERSION_THRESHOLD) {
            isConverted = true
            Log.d(TAG, "Robot converted at position ($x, $y)")
            return true
        }
        
        return false
    }
    
    /**
     * Check if robot is fully converted
     */
    fun isFullyConverted(): Boolean = isConverted
    
    /**
     * Get current robot position
     */
    fun getPosition(): Pair<Float, Float> = Pair(x, y)
    
    /**
     * Get robot's collision bounds for interaction detection
     */
    fun getBounds(): RectF {
        return RectF(
            x - ROBOT_RADIUS,
            y - ROBOT_RADIUS,
            x + ROBOT_RADIUS,
            y + ROBOT_RADIUS
        )
    }
    
    /**
     * Check if a point is within the robot's unpaint area
     */
    fun shouldUnpaintArea(checkX: Float, checkY: Float): Boolean {
        if (isConverted) return false
        
        val distance = sqrt((checkX - x).pow(2) + (checkY - y).pow(2))
        return distance <= robotData.unpaintRadius
    }
    
    /**
     * Draw the robot on the canvas
     */
    fun draw(canvas: Canvas) {
        // Set robot color based on conversion state
        robotPaint.color = if (isConverted) {
            Color.GREEN // Converted robots are green
        } else {
            Color.GRAY // Unconverted robots are gray
        }
        
        // Draw main robot body
        canvas.drawCircle(x, y, ROBOT_RADIUS, robotPaint)
        
        // Draw conversion progress if being converted
        if (!isConverted && conversionProgress > 0f) {
            val progressAngle = (conversionProgress / CONVERSION_THRESHOLD) * 360f
            canvas.drawArc(
                x - ROBOT_RADIUS - 5f,
                y - ROBOT_RADIUS - 5f,
                x + ROBOT_RADIUS + 5f,
                y + ROBOT_RADIUS + 5f,
                -90f, // Start at top
                progressAngle,
                false,
                conversionPaint
            )
        }
        
        // Draw eyes/face
        val eyePaint = Paint().apply {
            color = if (isConverted) Color.WHITE else Color.RED
            isAntiAlias = true
        }
        
        // Left eye
        canvas.drawCircle(x - 8f, y - 8f, 4f, eyePaint)
        // Right eye
        canvas.drawCircle(x + 8f, y - 8f, 4f, eyePaint)
    }
    
    /**
     * Update robot patrol movement
     */
    private fun updatePatrol(deltaTime: Float) {
        if (robotData.patrolPath.isEmpty()) return
        
        val currentTarget = robotData.patrolPath[currentPatrolIndex]
        val targetX = currentTarget.first
        val targetY = currentTarget.second
        
        // Calculate direction to target
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx.pow(2) + dy.pow(2))
        
        if (distance < 5f) {
            // Reached current patrol point, move to next
            if (robotData.patrolPath.size > 1) {
                currentPatrolIndex += patrolDirection
                
                // Reverse direction if reached end of patrol path
                if (currentPatrolIndex >= robotData.patrolPath.size) {
                    currentPatrolIndex = robotData.patrolPath.size - 2
                    patrolDirection = -1
                } else if (currentPatrolIndex < 0) {
                    currentPatrolIndex = 1
                    patrolDirection = 1
                }
            }
        } else {
            // Move towards target
            val moveAmount = MOVE_SPEED * deltaTime
            val normalizedDx = dx / distance
            val normalizedDy = dy / distance
            
            x += normalizedDx * moveAmount
            y += normalizedDy * moveAmount
        }
    }
    
    /**
     * Remove paint in area around unconverted robot
     */
    private fun removeNearbyPaint(paintSurface: PaintSurface) {
        val currentTime = System.currentTimeMillis()
        
        // Only unpaint at specified frequency
        if (currentTime - lastUnpaintTime < (1000 / PAINT_FREQUENCY)) return
        
        lastUnpaintTime = currentTime
        
        // Remove paint in circular area around robot
        val startX = (x - robotData.unpaintRadius).toInt().coerceAtLeast(0)
        val endX = (x + robotData.unpaintRadius).toInt().coerceAtMost(paintSurface.w - 1)
        val startY = (y - robotData.unpaintRadius).toInt().coerceAtLeast(0)
        val endY = (y + robotData.unpaintRadius).toInt().coerceAtMost(paintSurface.h - 1)
        
        for (px in startX..endX) {
            for (py in startY..endY) {
                val distance = sqrt((px - x).pow(2) + (py - y).pow(2))
                if (distance <= robotData.unpaintRadius) {
                    // Remove paint by setting to transparent
                    paintSurface.paintAt(px.toFloat(), py.toFloat(), Color.TRANSPARENT)
                }
            }
        }
    }
    
    /**
     * Paint area around converted robot (autonomous painting)
     */
    private fun paintNearbyArea(paintSurface: PaintSurface) {
        val currentTime = System.currentTimeMillis()
        
        // Paint at same frequency as unpainting
        if (currentTime - lastUnpaintTime < (1000 / PAINT_FREQUENCY)) return
        
        lastUnpaintTime = currentTime
        
        // Paint in smaller area than unpainting
        val paintRadius = robotData.unpaintRadius * 0.6f
        
        val startX = (x - paintRadius).toInt().coerceAtLeast(0)
        val endX = (x + paintRadius).toInt().coerceAtMost(paintSurface.w - 1)
        val startY = (y - paintRadius).toInt().coerceAtLeast(0)
        val endY = (y + paintRadius).toInt().coerceAtMost(paintSurface.h - 1)
        
        for (px in startX..endX) {
            for (py in startY..endY) {
                val distance = sqrt((px - x).pow(2) + (py - y).pow(2))
                if (distance <= paintRadius) {
                    // Paint with green color (converted robot color)
                    paintSurface.paintAt(px.toFloat(), py.toFloat(), Color.GREEN)
                }
            }
        }
    }
} 