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
        private const val PAINT_FREQUENCY = 5f // How often robot removes/paints (per second) - more frequent
        private const val SCAN_RADIUS = 150f // How far robot can detect player paint
        private const val TARGET_UPDATE_INTERVAL = 500L // Update target every 0.5 seconds - more responsive
        private const val PAINT_SPOT_SIZE = 8f // Small paint spots for strategic painting
    }
    
    // Robot state
    private var x: Float = startX
    private var y: Float = startY
    private var isConverted: Boolean = false
    private var conversionProgress: Float = 0f
    private var currentPatrolIndex: Int = 0
    private var patrolDirection: Int = 1 // 1 for forward, -1 for backward
    
    // AI targeting system
    private var currentTarget: Pair<Float, Float>? = null
    private var lastTargetUpdate: Long = 0
    private var isFollowingPatrol: Boolean = true
    
    // Timing for paint removal/addition
    private var lastPaintActionTime: Long = 0
    private var isActivelyRemoving: Boolean = false
    
    // Stuck detection and escape system
    private var lastPosition: Pair<Float, Float> = Pair(startX, startY)
    private var stuckCounter: Int = 0
    private var lastStuckCheck: Long = 0
    private var isEscaping: Boolean = false
    private var escapeDirection: Float = 0f // Angle in radians
    private var escapeDistance: Float = 0f
    private var maxEscapeAttempts: Int = 8 // Try 8 different directions
    
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
    fun update(deltaTime: Float, paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level? = null) {
        // Store previous position for trail painting
        val prevX = x
        val prevY = y
        
        // Check if robot is stuck and handle escape behavior
        updateStuckDetection(deltaTime, level)
        
        if (!isConverted) {
            // Unconverted robot: seek and destroy player paint
            if (!isEscaping) {
                updateUnconvertedAI(deltaTime, paintSurface, level)
            } else {
                executeEscapeMovement(deltaTime, level)
            }
            removeNearbyPaint(paintSurface)
        } else {
            // Converted robot: seek unpainted areas and paint strategically
            if (!isEscaping) {
                updateConvertedAI(deltaTime, paintSurface, level)
            } else {
                executeEscapeMovement(deltaTime, level)
            }
            
            // Paint continuous trail when moving
            if (prevX != x || prevY != y) {
                paintMovementTrail(prevX, prevY, x, y, paintSurface, level)
            }
            
            // Also paint at current position even when not moving (to ensure visible paint)
            paintCurrentPosition(paintSurface, level)
        }
    }
    
    /**
     * AI for unconverted robots - seek player paint and destroy it
     */
    private fun updateUnconvertedAI(deltaTime: Float, paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?) {
        val currentTime = System.currentTimeMillis()
        
        // Update target periodically
        if (currentTime - lastTargetUpdate > TARGET_UPDATE_INTERVAL) {
            val nearestPaint = findNearestPlayerPaint(paintSurface)
            if (nearestPaint != null) {
                currentTarget = nearestPaint
                isFollowingPatrol = false
                Log.d(TAG, "Robot targeting player paint at (${nearestPaint.first}, ${nearestPaint.second})")
            } else {
                // No paint found, return to patrol
                currentTarget = null
                isFollowingPatrol = true
            }
            lastTargetUpdate = currentTime
        }
        
        // Move based on current strategy
        if (!isFollowingPatrol && currentTarget != null) {
            moveTowardsTarget(currentTarget!!, deltaTime, level)
        } else {
            updatePatrol(deltaTime, level)
        }
    }
    
    /**
     * AI for converted robots - seek unpainted areas and paint strategically
     */
    private fun updateConvertedAI(deltaTime: Float, paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?) {
        val currentTime = System.currentTimeMillis()
        
        // Update target periodically to find unpainted areas
        if (currentTime - lastTargetUpdate > TARGET_UPDATE_INTERVAL) {
            val nearestUnpainted = findNearestUnpaintedArea(paintSurface, level)
            if (nearestUnpainted != null) {
                currentTarget = nearestUnpainted
                isFollowingPatrol = false
                Log.d(TAG, "Converted robot targeting unpainted area at (${nearestUnpainted.first}, ${nearestUnpainted.second})")
            } else {
                // No unpainted areas nearby, return to patrol
                currentTarget = null
                isFollowingPatrol = true
            }
            lastTargetUpdate = currentTime
        }
        
        // Move based on current strategy
        if (!isFollowingPatrol && currentTarget != null) {
            moveTowardsTarget(currentTarget!!, deltaTime, level)
        } else {
            updatePatrol(deltaTime, level)
        }
    }
    
    /**
     * Find nearest player paint within scan radius
     */
    private fun findNearestPlayerPaint(paintSurface: PaintSurface): Pair<Float, Float>? {
        var nearestDistance = Float.MAX_VALUE
        var nearestPaint: Pair<Float, Float>? = null
        
        val scanArea = SCAN_RADIUS.toInt()
        val stepSize = 8 // Sample every 8 pixels for performance
        
        for (dx in -scanArea..scanArea step stepSize) {
            for (dy in -scanArea..scanArea step stepSize) {
                val checkX = (x + dx).toInt()
                val checkY = (y + dy).toInt()
                
                if (checkX >= 0 && checkX < paintSurface.w && checkY >= 0 && checkY < paintSurface.h) {
                    val pixelColor = paintSurface.getPixelColor(checkX, checkY)
                    
                    // Check if this pixel has player paint (not transparent, not green)
                    if (pixelColor != Color.TRANSPARENT && 
                        Color.alpha(pixelColor) > 0 && 
                        pixelColor != Color.GREEN) { // Green is converted robot paint
                        
                        val distance = sqrt(dx.toFloat().pow(2) + dy.toFloat().pow(2))
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearestPaint = Pair(checkX.toFloat(), checkY.toFloat())
                        }
                    }
                }
            }
        }
        
        return nearestPaint
    }
    
    /**
     * Find nearest unpainted area for converted robots to target
     * Considers green robot paint as "painted" so robot doesn't avoid its own work
     */
    private fun findNearestUnpaintedArea(paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?): Pair<Float, Float>? {
        var nearestDistance = Float.MAX_VALUE
        var nearestUnpainted: Pair<Float, Float>? = null
        
        val scanArea = SCAN_RADIUS.toInt()
        val stepSize = 15 // Larger step for better performance and broader search
        
        for (dx in -scanArea..scanArea step stepSize) {
            for (dy in -scanArea..scanArea step stepSize) {
                val checkX = x + dx
                val checkY = y + dy
                
                // Ensure coordinates are within bounds
                if (checkX >= 0 && checkX < paintSurface.w && checkY >= 0 && checkY < paintSurface.h) {
                    // Skip walls
                    if (level?.checkCollision(checkX, checkY) == true) continue
                    
                    val pixelColor = paintSurface.getPixelColor(checkX.toInt(), checkY.toInt())
                    
                    // Check if this area needs painting: transparent/unpainted AND not green (robot paint)
                    // This means robot will seek areas that haven't been painted by anyone yet
                    if ((pixelColor == Color.TRANSPARENT || Color.alpha(pixelColor) == 0)) {
                        val distance = sqrt(dx.toFloat().pow(2) + dy.toFloat().pow(2))
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearestUnpainted = Pair(checkX, checkY)
                        }
                    }
                }
            }
        }
        
        return nearestUnpainted
    }
    
    /**
     * Move towards a specific target with collision detection
     */
    private fun moveTowardsTarget(target: Pair<Float, Float>, deltaTime: Float, level: com.spiritwisestudios.inkrollers.Level?) {
        val targetX = target.first
        val targetY = target.second
        
        val dx = targetX - x
        val dy = targetY - y
        val distance = sqrt(dx.pow(2) + dy.pow(2))
        
        if (distance < 10f) {
            // Reached target, clear it to find new one
            currentTarget = null
            return
        }
        
        val moveAmount = MOVE_SPEED * deltaTime
        val normalizedDx = dx / distance
        val normalizedDy = dy / distance
        
        val newX = x + normalizedDx * moveAmount
        val newY = y + normalizedDy * moveAmount
        
        // Only check collision with walls/obstacles, NOT with paint
        if (level?.checkCollision(newX, newY) != true) {
            x = newX
            y = newY
        } else {
            // Collision detected, try alternative paths
            tryAlternativePath(deltaTime, level)
        }
    }
    
    /**
     * Try to find alternative path around obstacles
     */
    private fun tryAlternativePath(deltaTime: Float, level: com.spiritwisestudios.inkrollers.Level?) {
        val moveAmount = MOVE_SPEED * deltaTime
        val angles = listOf(PI/4, -PI/4, PI/2, -PI/2, 3*PI/4, -3*PI/4) // Try different angles
        
        for (angle in angles) {
            val newX = x + cos(angle).toFloat() * moveAmount
            val newY = y + sin(angle).toFloat() * moveAmount
            
            // Only check collision with walls/obstacles, NOT with paint
            if (level?.checkCollision(newX, newY) != true) {
                x = newX
                y = newY
                break
            }
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
            // Reset AI state for converted behavior
            currentTarget = null
            isFollowingPatrol = true
            
            // Immediately paint around conversion spot to show the change
            paintConversionSpot(paintSurface)
            
            Log.d(TAG, "Robot converted at position ($x, $y) - switching to ally mode")
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
        
        // Draw AI state indicator
        if (!isConverted) {
            val statePaint = Paint().apply {
                color = when {
                    isEscaping -> Color.MAGENTA // Bright magenta when escaping/stuck
                    isActivelyRemoving -> Color.RED // Bright red when removing paint
                    isFollowingPatrol -> Color.BLUE // Blue when patrolling
                    else -> Color.YELLOW // Yellow when seeking paint
                }
                style = Paint.Style.STROKE
                strokeWidth = when {
                    isEscaping -> 6f // Extra thick when escaping
                    isActivelyRemoving -> 4f // Thick when removing
                    else -> 2f // Normal thickness
                }
                isAntiAlias = true
            }
            // Small indicator ring showing AI state
            canvas.drawCircle(x, y, ROBOT_RADIUS + 5f, statePaint)
            
            // Special visual effects
            if (isEscaping) {
                // Draw escape direction indicator
                val escapeIndicatorPaint = Paint().apply {
                    color = Color.MAGENTA
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                val indicatorLength = 40f
                val endX = x + cos(escapeDirection) * indicatorLength
                val endY = y + sin(escapeDirection) * indicatorLength
                canvas.drawLine(x, y, endX, endY, escapeIndicatorPaint)
                
                // Draw escape area highlight
                val escapePaint = Paint().apply {
                    color = Color.MAGENTA
                    style = Paint.Style.FILL
                    alpha = 50 // Very transparent
                    isAntiAlias = true
                }
                canvas.drawCircle(x, y, ROBOT_RADIUS * 1.5f, escapePaint)
            } else if (isActivelyRemoving) {
                val removalPaint = Paint().apply {
                    color = Color.RED
                    style = Paint.Style.FILL
                    alpha = 100 // Semi-transparent
                    isAntiAlias = true
                }
                canvas.drawCircle(x, y, robotData.unpaintRadius * 0.8f, removalPaint)
            }
        } else {
            // Show indicators for converted robots
            val statePaint = Paint().apply {
                color = when {
                    isEscaping -> Color.MAGENTA // Magenta when escaping
                    isFollowingPatrol -> Color.CYAN // Cyan when patrolling
                    else -> Color.YELLOW // Yellow when seeking unpainted areas
                }
                style = Paint.Style.STROKE
                strokeWidth = if (isEscaping) 6f else 2f // Thicker when escaping
                isAntiAlias = true
            }
            canvas.drawCircle(x, y, ROBOT_RADIUS + 5f, statePaint)
            
            // Show escape indicator for converted robots too
            if (isEscaping) {
                val escapeIndicatorPaint = Paint().apply {
                    color = Color.MAGENTA
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                val indicatorLength = 40f
                val endX = x + cos(escapeDirection) * indicatorLength
                val endY = y + sin(escapeDirection) * indicatorLength
                canvas.drawLine(x, y, endX, endY, escapeIndicatorPaint)
            }
        }
        
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
        
        // Draw target indicator for debugging (optional)
        if (currentTarget != null && !isFollowingPatrol) {
            val targetPaint = Paint().apply {
                color = if (isConverted) Color.CYAN else Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(x, y, currentTarget!!.first, currentTarget!!.second, targetPaint)
        }
    }
    
    /**
     * Update robot patrol movement with collision detection
     */
    private fun updatePatrol(deltaTime: Float, level: com.spiritwisestudios.inkrollers.Level?) {
        if (robotData.patrolPath.isEmpty()) return
        
        val currentPatrolTarget = robotData.patrolPath[currentPatrolIndex]
        val targetX = currentPatrolTarget.first
        val targetY = currentPatrolTarget.second
        
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
            // Move towards target with collision detection
            val moveAmount = MOVE_SPEED * deltaTime
            val normalizedDx = dx / distance
            val normalizedDy = dy / distance
            
            val newX = x + normalizedDx * moveAmount
            val newY = y + normalizedDy * moveAmount
            
            // Only check collision with walls/obstacles, NOT with paint
            if (level?.checkCollision(newX, newY) != true) {
                x = newX
                y = newY
            } else {
                // If blocked, try to move to next patrol point
                currentPatrolIndex += patrolDirection
                if (currentPatrolIndex >= robotData.patrolPath.size) {
                    currentPatrolIndex = robotData.patrolPath.size - 2
                    patrolDirection = -1
                } else if (currentPatrolIndex < 0) {
                    currentPatrolIndex = 1
                    patrolDirection = 1
                }
            }
        }
    }
    
    /**
     * Remove paint in area around unconverted robot - very aggressive
     */
    private fun removeNearbyPaint(paintSurface: PaintSurface) {
        val currentTime = System.currentTimeMillis()
        
        // Very frequent paint removal - 10 times per second when near paint
        if (currentTime - lastPaintActionTime < 100L) return // 100ms = 10fps
        
        lastPaintActionTime = currentTime
        
        val removeRadius = robotData.unpaintRadius
        val startX = (x - removeRadius).toInt().coerceAtLeast(0)
        val endX = (x + removeRadius).toInt().coerceAtMost(paintSurface.w - 1)
        val startY = (y - removeRadius).toInt().coerceAtLeast(0)
        val endY = (y + removeRadius).toInt().coerceAtMost(paintSurface.h - 1)
        
        var paintRemoved = false
        // Aggressive removal - check every pixel in range
        for (px in startX..endX) {
            for (py in startY..endY) {
                val distance = sqrt((px - x).pow(2) + (py - y).pow(2))
                if (distance <= removeRadius) {
                    val currentColor = paintSurface.getPixelColor(px, py)
                    // Remove any non-transparent paint that isn't green (robot paint)
                    if (currentColor != Color.TRANSPARENT && 
                        Color.alpha(currentColor) > 0 && 
                        currentColor != Color.GREEN) {
                        
                        // Use eraseAt method which is more effective than paintAt with transparent
                        paintSurface.eraseAt(px.toFloat(), py.toFloat())
                        paintRemoved = true
                    }
                }
            }
        }
        
        isActivelyRemoving = paintRemoved
        if (paintRemoved) {
            Log.d(TAG, "Robot aggressively removing player paint at position ($x, $y)")
        }
    }
    
    /**
     * Paint a continuous trail as the robot moves (behind the robot)
     */
    private fun paintMovementTrail(fromX: Float, fromY: Float, toX: Float, toY: Float, paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?) {
        // Calculate distance and steps needed for smooth trail
        val dx = toX - fromX
        val dy = toY - fromY
        val distance = sqrt(dx.pow(2) + dy.pow(2))
        
        // Lower threshold - paint even for small movements
        if (distance < 0.5f) return 
        
        // Paint every 2 pixels along the path for dense coverage
        val steps = (distance / 2f).toInt().coerceAtLeast(1)
        
        Log.d(TAG, "Painting trail: distance=$distance, steps=$steps, from ($fromX, $fromY) to ($toX, $toY)")
        
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val paintX = fromX + dx * progress
            val paintY = fromY + dy * progress
            
            // Check bounds and walls (not paint collision)
            if (paintX >= 0 && paintX < paintSurface.w && 
                paintY >= 0 && paintY < paintSurface.h &&
                level?.checkCollision(paintX, paintY) != true) {
                
                // Paint at current position with green color
                paintSurface.paintAt(paintX, paintY, Color.GREEN)
                Log.v(TAG, "Painted trail spot at ($paintX, $paintY)")
            }
        }
        
        // Also paint at the exact previous position to ensure no gaps
        if (fromX >= 0 && fromX < paintSurface.w && fromY >= 0 && fromY < paintSurface.h &&
            level?.checkCollision(fromX, fromY) != true) {
            paintSurface.paintAt(fromX, fromY, Color.GREEN)
            Log.v(TAG, "Painted start position at ($fromX, $fromY)")
        }
        
        // Paint at current position too
        if (toX >= 0 && toX < paintSurface.w && toY >= 0 && toY < paintSurface.h &&
            level?.checkCollision(toX, toY) != true) {
            paintSurface.paintAt(toX, toY, Color.GREEN)
            Log.v(TAG, "Painted end position at ($toX, $toY)")
        }
        
        Log.d(TAG, "Converted robot painted movement trail from ($fromX, $fromY) to ($toX, $toY), distance: $distance")
    }
    
    /**
     * Paint at the robot's current position (for when robot is stationary or moving slowly)
     */
    private fun paintCurrentPosition(paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?) {
        val currentTime = System.currentTimeMillis()
        
        // Paint at current position every 200ms to ensure visibility
        if (currentTime - lastPaintActionTime < 200L) return
        
        lastPaintActionTime = currentTime
        
        // Paint a small area around current position
        val paintRadius = 12f // Smaller than player paint for subtle effect
        
        for (angle in 0 until 360 step 45) { // 8 directions
            val radians = angle * PI / 180
            for (radius in 0..paintRadius.toInt() step 3) {
                val paintX = x + cos(radians).toFloat() * radius
                val paintY = y + sin(radians).toFloat() * radius
                
                if (paintX >= 0 && paintX < paintSurface.w && 
                    paintY >= 0 && paintY < paintSurface.h &&
                    level?.checkCollision(paintX, paintY) != true) {
                    
                    paintSurface.paintAt(paintX, paintY, Color.GREEN)
                }
            }
        }
        
        Log.v(TAG, "Converted robot painted at current position ($x, $y)")
    }
    
    /**
     * Paint immediately around the robot when it gets converted
     */
    private fun paintConversionSpot(paintSurface: PaintSurface) {
        // Paint a large green spot to clearly show the conversion
        val conversionRadius = 25f
        
        for (angle in 0 until 360 step 30) { // 12 directions for full coverage
            val radians = angle * PI / 180
            for (radius in 0..conversionRadius.toInt() step 2) {
                val paintX = x + cos(radians).toFloat() * radius
                val paintY = y + sin(radians).toFloat() * radius
                
                if (paintX >= 0 && paintX < paintSurface.w && 
                    paintY >= 0 && paintY < paintSurface.h) {
                    
                    paintSurface.paintAt(paintX, paintY, Color.GREEN)
                }
            }
        }
        
        Log.d(TAG, "Robot painted conversion spot at ($x, $y)")
    }
    
    /**
     * Detect if robot is stuck and initiate escape behavior
     */
    private fun updateStuckDetection(deltaTime: Float, level: com.spiritwisestudios.inkrollers.Level?) {
        val currentTime = System.currentTimeMillis()
        
        // Check for stuck condition every 1 second
        if (currentTime - lastStuckCheck > 1000L) {
            val currentPosition = Pair(x, y)
            val distanceMoved = sqrt((x - lastPosition.first).pow(2) + (y - lastPosition.second).pow(2))
            
            // If robot hasn't moved more than 5 pixels in 1 second, it's likely stuck
            if (distanceMoved < 5f) {
                stuckCounter++
                Log.d(TAG, "Robot potentially stuck. Counter: $stuckCounter, distance moved: $distanceMoved")
                
                // If stuck for 2 consecutive checks (2 seconds), initiate escape
                if (stuckCounter >= 2 && !isEscaping) {
                    initiateEscape()
                }
            } else {
                // Robot is moving, reset stuck counter and exit escape mode if active
                stuckCounter = 0
                if (isEscaping) {
                    exitEscapeMode()
                }
            }
            
            lastPosition = currentPosition
            lastStuckCheck = currentTime
        }
    }
    
    /**
     * Initiate escape behavior when robot is stuck
     */
    private fun initiateEscape() {
        isEscaping = true
        val possibleDirections = listOf(0, 45, 90, 135, 180, 225, 270, 315)
        escapeDirection = possibleDirections.random() * PI.toFloat() / 180f // Random direction in 45-degree increments
        escapeDistance = 50f + (0..50).toList().random() // Random escape distance 50-100 pixels
        stuckCounter = 0 // Reset counter to prevent immediate re-triggering
        
        Log.d(TAG, "Robot initiating escape! Direction: ${(escapeDirection * 180 / PI).toInt()}°, Distance: $escapeDistance")
    }
    
    /**
     * Execute escape movement in the chosen direction
     */
    private fun executeEscapeMovement(deltaTime: Float, level: com.spiritwisestudios.inkrollers.Level?) {
        val moveAmount = MOVE_SPEED * deltaTime * 1.5f // Move faster when escaping
        
        val targetX = x + cos(escapeDirection) * moveAmount
        val targetY = y + sin(escapeDirection) * moveAmount
        
        // Try to move in escape direction
        if (level?.checkCollision(targetX, targetY) != true) {
            x = targetX
            y = targetY
            escapeDistance -= moveAmount
            
            // Check if we've completed the escape distance
            if (escapeDistance <= 0) {
                exitEscapeMode()
            }
        } else {
            // Hit wall while escaping, try a different direction
            stuckCounter++
            if (stuckCounter < maxEscapeAttempts) {
                // Try a new random direction
                val possibleDirections = listOf(0, 45, 90, 135, 180, 225, 270, 315)
                escapeDirection = possibleDirections.random() * PI.toFloat() / 180f
                escapeDistance = 30f + (0..30).toList().random() // Shorter distance for subsequent attempts
                Log.d(TAG, "Escape blocked, trying new direction: ${(escapeDirection * 180 / PI).toInt()}°")
            } else {
                // Tried too many directions, give up and resume normal behavior
                exitEscapeMode()
                Log.d(TAG, "Escape attempts exhausted, resuming normal behavior")
            }
        }
    }
    
    /**
     * Exit escape mode and resume normal AI behavior
     */
    private fun exitEscapeMode() {
        isEscaping = false
        escapeDirection = 0f
        escapeDistance = 0f
        stuckCounter = 0
        
        // Reset AI targets to allow fresh pathfinding
        currentTarget = null
        isFollowingPatrol = true
        
        Log.d(TAG, "Robot exited escape mode, resuming normal AI")
    }
    
    /**
     * Strategic painting for converted robots - seek unpainted areas
     */
    private fun paintStrategically(paintSurface: PaintSurface, level: com.spiritwisestudios.inkrollers.Level?) {
        val currentTime = System.currentTimeMillis()
        
        // Paint strategically less frequently since robot now paints continuously while moving
        if (currentTime - lastPaintActionTime < (2000 / PAINT_FREQUENCY)) return // Reduced frequency
        
        lastPaintActionTime = currentTime
        
        // Paint in small strategic spots rather than large circles
        var areasPainted = 0
        val maxPaintSpots = 3 // Paint up to 3 spots per action
        
        // Look for unpainted areas around the robot
        for (angle in 0 until 360 step 45) { // Check 8 directions
            if (areasPainted >= maxPaintSpots) break
            
            val radians = angle * PI / 180
            val checkDistance = PAINT_SPOT_SIZE * 2
            val checkX = x + cos(radians).toFloat() * checkDistance
            val checkY = y + sin(radians).toFloat() * checkDistance
            
            // Ensure we're within bounds and not hitting walls
            if (checkX >= 0 && checkX < paintSurface.w && 
                checkY >= 0 && checkY < paintSurface.h &&
                level?.checkCollision(checkX, checkY) != true) {
                
                val pixelColor = paintSurface.getPixelColor(checkX.toInt(), checkY.toInt())
                
                // Only paint if area is unpainted
                if (pixelColor == Color.TRANSPARENT || Color.alpha(pixelColor) == 0) {
                    // Paint a small strategic spot
                    for (dx in -PAINT_SPOT_SIZE.toInt()..PAINT_SPOT_SIZE.toInt() step 2) {
                        for (dy in -PAINT_SPOT_SIZE.toInt()..PAINT_SPOT_SIZE.toInt() step 2) {
                            val paintX = checkX + dx
                            val paintY = checkY + dy
                            
                            if (paintX >= 0 && paintX < paintSurface.w && 
                                paintY >= 0 && paintY < paintSurface.h &&
                                level?.checkCollision(paintX, paintY) != true) {
                                
                                val distance = sqrt(dx.toFloat().pow(2) + dy.toFloat().pow(2))
                                if (distance <= PAINT_SPOT_SIZE) {
                                    paintSurface.paintAt(paintX, paintY, Color.GREEN)
                                }
                            }
                        }
                    }
                    areasPainted++
                }
            }
        }
        
        if (areasPainted > 0) {
            Log.v(TAG, "Converted robot painted $areasPainted strategic spots at ($x, $y)")
        }
    }
} 