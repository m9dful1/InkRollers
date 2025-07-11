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
    }
    
    private var isDiscovered = false
    private var discoveryAnimation = 0f
    private val discoveryPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    /**
     * Check if player is near the secret area
     */
    fun checkPlayerProximity(playerX: Float, playerY: Float, proximityRadius: Float = 30f): Boolean {
        val centerX = (secretData.area.left + secretData.area.right) / 2f
        val centerY = (secretData.area.top + secretData.area.bottom) / 2f
        
        val distance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
        return distance <= proximityRadius
    }
    
    /**
     * Attempt to discover the secret area
     */
    fun attemptDiscovery(playerX: Float, playerY: Float): Boolean {
        if (isDiscovered) return false
        
        if (checkPlayerProximity(playerX, playerY)) {
            isDiscovered = true
            discoveryAnimation = 1f
            audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
            Log.d(TAG, "Secret discovered: ${secretData.description}")
            return true
        }
        return false
    }
    
    /**
     * Update discovery animation
     */
    fun update(deltaTime: Float) {
        if (isDiscovered && discoveryAnimation > 0f) {
            discoveryAnimation -= deltaTime * 2f // Fade out over 0.5 seconds
            if (discoveryAnimation < 0f) discoveryAnimation = 0f
        }
    }
    
    /**
     * Draw the secret area
     */
    fun draw(canvas: Canvas) {
        if (!isDiscovered) {
            // Draw subtle hint when player is nearby
            discoveryPaint.alpha = 50
            canvas.drawRect(secretData.area, discoveryPaint)
        } else if (discoveryAnimation > 0f) {
            // Draw discovery animation
            val alpha = (discoveryAnimation * 255).toInt()
            discoveryPaint.alpha = alpha
            glowPaint.alpha = (alpha * 0.3f).toInt()
            
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
} 