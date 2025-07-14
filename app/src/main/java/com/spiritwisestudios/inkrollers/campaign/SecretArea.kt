package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SecretArea class for handling secret area discovery and interaction
 */
class SecretArea(
    private val secretData: SecretAreaData,
    private val audioManager: com.spiritwisestudios.inkrollers.AudioManager? = null
) {
    
    companion object {
        private const val TAG = "SecretArea"
        private const val PROXIMITY_RADIUS = 100f  // Increased from 30f
        private const val MEDIUM_DISTANCE = 200f   // Increased from 80f
        private const val FAR_DISTANCE = 300f     // Increased from 120f
        private const val DEBUG_MODE = true        // Make secret areas always visible for testing
    }
    
    private var isDiscovered = false
    private var discoveryAnimation = 0f
    private var proximityAnimation = 0f
    private var lastPlayerDistance = Float.MAX_VALUE
    private var creationTime = System.currentTimeMillis()
    
    // References for unlock condition checking
    private var campaignLevel: com.spiritwisestudios.inkrollers.campaign.CampaignLevel? = null
    private var paintSurface: com.spiritwisestudios.inkrollers.PaintSurface? = null
    
    // Visual elements for different states
    private val discoveryPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f  // Increased from 4f
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val pulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f  // Increased from 2f
        isAntiAlias = true
    }
    
    private val debugPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        color = Color.WHITE
        alpha = 180
    }
    
    /**
     * Get the color for this secret based on frequency requirement
     */
    private fun getSecretColor(): Int {
        return when (secretData.requiredFrequency) {
            ColorFrequency.RED -> Color.RED
            ColorFrequency.BLUE -> Color.BLUE
            ColorFrequency.GREEN -> Color.GREEN
            ColorFrequency.YELLOW -> Color.YELLOW
            null -> Color.WHITE
        }
    }
    
    /**
     * Check if player is near the secret area
     */
    fun checkPlayerProximity(playerX: Float, playerY: Float, proximityRadius: Float = PROXIMITY_RADIUS): Boolean {
        val centerX = (secretData.area.left + secretData.area.right) / 2f
        val centerY = (secretData.area.top + secretData.area.bottom) / 2f
        
        val distance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
        lastPlayerDistance = distance
        return distance <= proximityRadius
    }
    
    /**
     * Attempt to discover the secret area with frequency check
     */
    fun attemptDiscovery(playerX: Float, playerY: Float, playerFrequency: ColorFrequency? = null): Boolean {
        if (isDiscovered) return false
        
        if (!checkPlayerProximity(playerX, playerY)) return false
        
        // Check frequency requirement
        if (secretData.requiredFrequency != null && playerFrequency != secretData.requiredFrequency) {
            // Wrong frequency - play error sound and show visual feedback
            audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.WRONG_FREQUENCY)
            Log.d(TAG, "Wrong frequency for secret: $playerFrequency, required: ${secretData.requiredFrequency}")
            return false
        }
        
        // Check unlock condition
        if (!checkUnlockCondition(playerX, playerY, playerFrequency)) {
            return false
        }
        
        isDiscovered = true
        discoveryAnimation = 1f
        audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
        Log.d(TAG, "Secret discovered: ${secretData.description}")
        return true
    }
    
    /**
     * Check if unlock condition is met
     */
    private fun checkUnlockCondition(playerX: Float, playerY: Float, playerFrequency: ColorFrequency?): Boolean {
        return when (secretData.unlockCondition) {
            SecretUnlockCondition.PROXIMITY_ONLY -> true
            SecretUnlockCondition.FREQUENCY_MATCH -> playerFrequency == secretData.requiredFrequency
            SecretUnlockCondition.PAINT_REQUIRED -> {
                // Check if the area is painted with the correct frequency color
                checkAreaPainted(playerFrequency)
            }
            SecretUnlockCondition.TIME_THRESHOLD -> {
                // Check if enough time has passed (e.g., 30 seconds)
                val timeElapsed = System.currentTimeMillis() - creationTime
                timeElapsed >= 30000L // 30 seconds
            }
            SecretUnlockCondition.ROBOT_ASSISTED -> {
                // Check if there's a converted robot nearby
                checkRobotProximity(playerX, playerY)
            }
            null -> true
        }
    }
    
    /**
     * Check if the secret area is painted with the correct color
     */
    private fun checkAreaPainted(playerFrequency: ColorFrequency?): Boolean {
        if (paintSurface == null || playerFrequency == null) return false
        
        val requiredColor = when (playerFrequency) {
            ColorFrequency.RED -> android.graphics.Color.RED
            ColorFrequency.BLUE -> android.graphics.Color.BLUE
            ColorFrequency.GREEN -> android.graphics.Color.GREEN
            ColorFrequency.YELLOW -> android.graphics.Color.YELLOW
        }
        
        // Sample several points within the secret area
        val samplePoints = 5
        var paintedPoints = 0
        
        for (i in 0 until samplePoints) {
            for (j in 0 until samplePoints) {
                val sampleX = secretData.area.left + (secretData.area.width() * i / (samplePoints - 1))
                val sampleY = secretData.area.top + (secretData.area.height() * j / (samplePoints - 1))
                
                if (sampleX >= 0 && sampleX < paintSurface!!.w && sampleY >= 0 && sampleY < paintSurface!!.h) {
                    val pixelColor = paintSurface!!.getPixelColor(sampleX.toInt(), sampleY.toInt())
                    if (pixelColor == requiredColor) {
                        paintedPoints++
                    }
                }
            }
        }
        
        // Require at least 60% of sample points to be painted with correct color
        return paintedPoints >= (samplePoints * samplePoints * 0.6f)
    }
    
    /**
     * Check if there's a converted robot within proximity
     */
    private fun checkRobotProximity(playerX: Float, playerY: Float): Boolean {
        if (campaignLevel == null) return false
        
        val robots = campaignLevel!!.getRobots()
        val proximityRadius = 100f // Robots must be within 100 pixels
        
        for (robot in robots) {
            if (robot.isFullyConverted()) {
                val robotBounds = robot.getBounds()
                val robotCenterX = robotBounds.centerX()
                val robotCenterY = robotBounds.centerY()
                
                val distance = sqrt((playerX - robotCenterX).pow(2) + (playerY - robotCenterY).pow(2))
                if (distance <= proximityRadius) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Set campaign level reference for unlock condition checking
     */
    fun setCampaignLevel(level: com.spiritwisestudios.inkrollers.campaign.CampaignLevel) {
        this.campaignLevel = level
    }
    
    /**
     * Set paint surface reference for unlock condition checking
     */
    fun setPaintSurface(surface: com.spiritwisestudios.inkrollers.PaintSurface) {
        this.paintSurface = surface
    }
    
    /**
     * Update discovery and proximity animations
     */
    fun update(deltaTime: Float) {
        // Update discovery animation
        if (isDiscovered && discoveryAnimation > 0f) {
            discoveryAnimation -= deltaTime * 2f // Fade out over 0.5 seconds
            if (discoveryAnimation < 0f) discoveryAnimation = 0f
        }
        
        // Update proximity animation
        proximityAnimation += deltaTime * 3f
        if (proximityAnimation > 2f * Math.PI.toFloat()) {
            proximityAnimation -= 2f * Math.PI.toFloat()
        }
    }
    
    /**
     * Update player distance for visual feedback (should be called from CampaignLevel)
     */
    fun updatePlayerDistance(playerX: Float, playerY: Float) {
        val centerX = (secretData.area.left + secretData.area.right) / 2f
        val centerY = (secretData.area.top + secretData.area.bottom) / 2f
        
        lastPlayerDistance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
    }
    
    /**
     * Draw the secret area with enhanced visual feedback
     */
    fun draw(canvas: Canvas) {
        if (DEBUG_MODE) {
            drawDebugSecret(canvas)
        }
        
        if (isDiscovered) {
            drawDiscoveredSecret(canvas)
        } else {
            drawUndiscoveredSecret(canvas)
        }
    }
    
    /**
     * Draw debug version of secret area (always visible for testing)
     */
    private fun drawDebugSecret(canvas: Canvas) {
        val secretColor = getSecretColor()
        
        // Always draw a visible outline
        debugPaint.color = secretColor
        debugPaint.alpha = 200
        canvas.drawRect(secretData.area, debugPaint)
        
        // Draw distance indicator
        val distanceText = "Dist: ${lastPlayerDistance.toInt()}"
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText(distanceText, secretData.area.left, secretData.area.top - 10f, textPaint)
        
        // Draw frequency requirement
        val freqText = "Freq: ${secretData.requiredFrequency ?: "None"}"
        canvas.drawText(freqText, secretData.area.left, secretData.area.bottom + 30f, textPaint)
    }
    
    /**
     * Draw undiscovered secret with proximity-based feedback
     */
    private fun drawUndiscoveredSecret(canvas: Canvas) {
        if (DEBUG_MODE) return // Skip normal drawing in debug mode
        
        val secretColor = getSecretColor()
        
        when {
            lastPlayerDistance <= PROXIMITY_RADIUS -> {
                // Very close - bright glow
                val alpha = 255  // Increased from 150
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                glowPaint.color = secretColor
                glowPaint.alpha = (alpha * 0.5f).toInt()  // Increased from 0.3f
                
                canvas.drawRect(secretData.area, glowPaint)
                canvas.drawRect(secretData.area, discoveryPaint)
            }
            lastPlayerDistance <= MEDIUM_DISTANCE -> {
                // Medium distance - pulsing outline
                val pulseIntensity = (0.5f + 0.5f * kotlin.math.sin(proximityAnimation)).toFloat()
                val alpha = (200 * pulseIntensity).toInt()  // Increased from 100
                
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                discoveryPaint.strokeWidth = 5f  // Increased from 3f
                
                canvas.drawRect(secretData.area, discoveryPaint)
            }
            lastPlayerDistance <= FAR_DISTANCE -> {
                // Far distance - subtle hint
                val alpha = 100  // Increased from 30
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                discoveryPaint.strokeWidth = 2f  // Increased from 1f
                
                canvas.drawRect(secretData.area, discoveryPaint)
            }
        }
    }
    
    /**
     * Draw discovered secret with animation
     */
    private fun drawDiscoveredSecret(canvas: Canvas) {
        if (discoveryAnimation > 0f) {
            val secretColor = getSecretColor()
            val alpha = (discoveryAnimation * 255).toInt()
            
            discoveryPaint.color = secretColor
            discoveryPaint.alpha = alpha
            discoveryPaint.strokeWidth = 4f
            
            glowPaint.color = secretColor
            glowPaint.alpha = (alpha * 0.5f).toInt()
            
            // Draw glow effect
            canvas.drawRect(secretData.area, glowPaint)
            canvas.drawRect(secretData.area, discoveryPaint)
        }
    }
    
    /**
     * Check if secret is discovered
     */
    fun isDiscovered(): Boolean = isDiscovered
    
    /**
     * Get secret data
     */
    fun getSecretData(): SecretAreaData = secretData
    
    /**
     * Get secret type
     */
    fun getSecretType(): SecretType = secretData.secretType
    
    /**
     * Get secret description
     */
    fun getDescription(): String = secretData.description
    
    /**
     * Get required frequency for this secret
     */
    fun getRequiredFrequency(): ColorFrequency? = secretData.requiredFrequency
    
    /**
     * Get unlock condition for this secret
     */
    fun getUnlockCondition(): SecretUnlockCondition? = secretData.unlockCondition
} 