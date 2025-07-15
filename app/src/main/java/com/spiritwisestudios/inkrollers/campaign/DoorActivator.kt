package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * DoorActivator class for puzzle door mechanics in campaign mode.
 * Players must paint the activator square with the correct color to remove the blocking wall.
 */
class DoorActivator(
    private val activatorData: DoorActivatorData,
    private val audioManager: com.spiritwisestudios.inkrollers.AudioManager? = null
) {
    
    companion object {
        private const val TAG = "DoorActivator"
        private const val PROXIMITY_RADIUS = 80f
        private const val ACTIVATOR_SIZE = 40f
    }
    
    private var isActivated = false
    private var activationAnimation = 0f
    private var proximityAnimation = 0f
    private var lastPlayerDistance = Float.MAX_VALUE
    
    // References for paint checking
    private var paintSurface: com.spiritwisestudios.inkrollers.PaintSurface? = null
    
    // Visual elements
    private val activatorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val wallPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C0C0C0")
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    /**
     * Get the color for this activator based on required frequency
     */
    private fun getActivatorColor(): Int {
        return when (activatorData.requiredFrequency) {
            ColorFrequency.RED -> Color.RED
            ColorFrequency.BLUE -> Color.BLUE
            ColorFrequency.GREEN -> Color.GREEN
            ColorFrequency.YELLOW -> Color.YELLOW
        }
    }
    
    /**
     * Check if the activator square is painted with the correct color
     */
    private fun checkActivatorPainted(): Boolean {
        if (paintSurface == null) return false
        
        val requiredColor = getActivatorColor()
        val activatorRect = activatorData.activatorArea
        
        // Sample several points within the activator area
        val samplePoints = 3
        var paintedPoints = 0
        val totalPoints = samplePoints * samplePoints
        
        for (i in 0 until samplePoints) {
            for (j in 0 until samplePoints) {
                val sampleX = activatorRect.left + (activatorRect.width() * i / (samplePoints - 1))
                val sampleY = activatorRect.top + (activatorRect.height() * j / (samplePoints - 1))
                
                if (sampleX >= 0 && sampleX < paintSurface!!.w && sampleY >= 0 && sampleY < paintSurface!!.h) {
                    val pixelColor = paintSurface!!.getPixelColor(sampleX.toInt(), sampleY.toInt())
                    if (pixelColor == requiredColor) {
                        paintedPoints++
                    }
                }
            }
        }
        
        // Require at least 70% of sample points to be painted with correct color
        return paintedPoints >= (totalPoints * 0.7f)
    }
    
    /**
     * Check if player is near the activator
     */
    fun checkPlayerProximity(playerX: Float, playerY: Float): Boolean {
        val centerX = activatorData.activatorArea.centerX()
        val centerY = activatorData.activatorArea.centerY()
        
        val distance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
        lastPlayerDistance = distance
        return distance <= PROXIMITY_RADIUS
    }
    
    /**
     * Update activator state and check for activation
     */
    fun update(deltaTime: Float) {
        if (!isActivated && checkActivatorPainted()) {
            activate()
        }
        
        // Update activation animation
        if (isActivated && activationAnimation < 1f) {
            activationAnimation += deltaTime * 3f // Fast activation animation
            if (activationAnimation > 1f) activationAnimation = 1f
        }
        
        // Update proximity animation for visual feedback
        proximityAnimation += deltaTime * 4f
        if (proximityAnimation > 2f * Math.PI.toFloat()) {
            proximityAnimation -= 2f * Math.PI.toFloat()
        }
    }
    
    /**
     * Activate the door (remove the wall)
     */
    private fun activate() {
        if (isActivated) return
        
        isActivated = true
        activationAnimation = 0f
        
        // Play activation sound
        audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
        
        Log.d(TAG, "Door activated: ${activatorData.description}")
    }
    
    /**
     * Draw the door activator and wall (if not activated)
     */
    fun draw(canvas: Canvas) {
        // Draw the wall if not activated
        if (!isActivated) {
            drawWall(canvas)
        }
        
        // Always draw the activator square
        drawActivator(canvas)
        
        // Draw activation effects if recently activated
        if (isActivated && activationAnimation > 0f) {
            drawActivationEffect(canvas)
        }
    }
    
    /**
     * Draw the blocking wall
     */
    private fun drawWall(canvas: Canvas) {
        if (isActivated) return
        
        // Draw the wall that blocks the path
        canvas.drawRect(activatorData.wallArea, wallPaint)
        
        // Draw wall outline
        strokePaint.color = Color.GRAY
        strokePaint.strokeWidth = 2f
        canvas.drawRect(activatorData.wallArea, strokePaint)
    }
    
    /**
     * Draw the activator square with visual feedback
     */
    private fun drawActivator(canvas: Canvas) {
        val activatorColor = getActivatorColor()
        val isPainted = checkActivatorPainted()
        
        if (isActivated) {
            // Activated state - dim and slightly green tint
            activatorPaint.color = Color.argb(180, 0, 255, 0)
        } else if (isPainted) {
            // Painted with correct color - bright and pulsing
            val pulseIntensity = (0.7f + 0.3f * kotlin.math.sin(proximityAnimation * 2f))
            activatorPaint.color = Color.argb((255 * pulseIntensity).toInt(), 
                Color.red(activatorColor), Color.green(activatorColor), Color.blue(activatorColor))
        } else {
            // Unpainted - show required color with transparency
            activatorPaint.color = Color.argb(120, 
                Color.red(activatorColor), Color.green(activatorColor), Color.blue(activatorColor))
        }
        
        // Draw activator square
        canvas.drawRect(activatorData.activatorArea, activatorPaint)
        
        // Draw stroke around activator
        strokePaint.color = if (isActivated) Color.GREEN else activatorColor
        strokePaint.strokeWidth = if (isPainted && !isActivated) 6f else 3f
        canvas.drawRect(activatorData.activatorArea, strokePaint)
        
        // Draw proximity glow if player is nearby and not activated
        if (!isActivated && lastPlayerDistance <= PROXIMITY_RADIUS) {
            val glowAlpha = (100 * (1f - lastPlayerDistance / PROXIMITY_RADIUS)).toInt().coerceIn(0, 100)
            glowPaint.color = Color.argb(glowAlpha, 
                Color.red(activatorColor), Color.green(activatorColor), Color.blue(activatorColor))
            
            val expandedRect = RectF(
                activatorData.activatorArea.left - 10f,
                activatorData.activatorArea.top - 10f,
                activatorData.activatorArea.right + 10f,
                activatorData.activatorArea.bottom + 10f
            )
            canvas.drawRect(expandedRect, glowPaint)
        }
    }
    
    /**
     * Draw activation effect when door is opened
     */
    private fun drawActivationEffect(canvas: Canvas) {
        val progress = activationAnimation
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        
        if (alpha > 0) {
            // Draw expanding circle effect
            val centerX = activatorData.activatorArea.centerX()
            val centerY = activatorData.activatorArea.centerY()
            val radius = 50f + progress * 100f
            
            glowPaint.color = Color.argb(alpha / 2, 0, 255, 0)
            canvas.drawCircle(centerX, centerY, radius, glowPaint)
            
            strokePaint.color = Color.argb(alpha, 0, 255, 0)
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
        }
    }
    
    /**
     * Check if this door blocks the given position
     */
    fun isBlocking(x: Float, y: Float): Boolean {
        return !isActivated && activatorData.wallArea.contains(x, y)
    }
    
    /**
     * Set paint surface reference for color checking
     */
    fun setPaintSurface(surface: com.spiritwisestudios.inkrollers.PaintSurface) {
        this.paintSurface = surface
    }
    
    /**
     * Check if the door is activated (opened)
     */
    fun isActivated(): Boolean = isActivated
    
    /**
     * Get activator data
     */
    fun getActivatorData(): DoorActivatorData = activatorData
    
    /**
     * Get required frequency for this door
     */
    fun getRequiredFrequency(): ColorFrequency = activatorData.requiredFrequency
    
    /**
     * Get description
     */
    fun getDescription(): String = activatorData.description
    
    /**
     * Update player distance for visual feedback
     */
    fun updatePlayerDistance(playerX: Float, playerY: Float) {
        val centerX = activatorData.activatorArea.centerX()
        val centerY = activatorData.activatorArea.centerY()
        lastPlayerDistance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
    }
} 