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
        private const val PROXIMITY_RADIUS = 30f
        private const val MEDIUM_DISTANCE = 80f
        private const val FAR_DISTANCE = 120f
    }
    
    private var isDiscovered = false
    private var discoveryAnimation = 0f
    private var proximityAnimation = 0f
    private var lastPlayerDistance = Float.MAX_VALUE
    
    // Visual elements for different states
    private val discoveryPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val pulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
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
        // TODO: Use playerX and playerY for paint detection and robot proximity checks
        return when (secretData.unlockCondition) {
            SecretUnlockCondition.PROXIMITY_ONLY -> true
            SecretUnlockCondition.FREQUENCY_MATCH -> playerFrequency == secretData.requiredFrequency
            SecretUnlockCondition.PAINT_REQUIRED -> {
                // For now, just check frequency match
                // TODO: Implement actual paint detection
                playerFrequency == secretData.requiredFrequency
            }
            SecretUnlockCondition.TIME_THRESHOLD -> {
                // For now, always allow
                // TODO: Implement time-based unlocking
                true
            }
            SecretUnlockCondition.ROBOT_ASSISTED -> {
                // For now, always allow
                // TODO: Implement robot proximity check
                true
            }
            null -> true
        }
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
     * Draw the secret area with enhanced visual feedback
     */
    fun draw(canvas: Canvas) {
        if (isDiscovered) {
            drawDiscoveredSecret(canvas)
        } else {
            drawUndiscoveredSecret(canvas)
        }
    }
    
    /**
     * Draw undiscovered secret with proximity-based feedback
     */
    private fun drawUndiscoveredSecret(canvas: Canvas) {
        val secretColor = getSecretColor()
        
        when {
            lastPlayerDistance <= PROXIMITY_RADIUS -> {
                // Very close - bright glow
                val alpha = 150
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                glowPaint.color = secretColor
                glowPaint.alpha = (alpha * 0.3f).toInt()
                
                canvas.drawRect(secretData.area, glowPaint)
                canvas.drawRect(secretData.area, discoveryPaint)
            }
            lastPlayerDistance <= MEDIUM_DISTANCE -> {
                // Medium distance - pulsing outline
                val pulseIntensity = (0.5f + 0.5f * kotlin.math.sin(proximityAnimation)).toFloat()
                val alpha = (100 * pulseIntensity).toInt()
                
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                discoveryPaint.strokeWidth = 3f
                
                canvas.drawRect(secretData.area, discoveryPaint)
            }
            lastPlayerDistance <= FAR_DISTANCE -> {
                // Far distance - very subtle hint
                val alpha = 30
                discoveryPaint.color = secretColor
                discoveryPaint.alpha = alpha
                discoveryPaint.strokeWidth = 1f
                
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