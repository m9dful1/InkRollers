package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ExitZone class for level completion areas in campaign mode.
 * Players must reach this area to complete the level.
 */
class ExitZone(
    private val exitData: ExitZoneData,
    private val audioManager: com.spiritwisestudios.inkrollers.AudioManager? = null
) {
    
    companion object {
        private const val TAG = "ExitZone"
        private const val COMPLETION_RADIUS = 60f
    }
    
    private var playerInZone = false
    private var completionAnimation = 0f
    private var glowAnimation = 0f
    private var lastPlayerDistance = Float.MAX_VALUE
    
    // Visual elements
    private val zonePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    /**
     * Check if player is in the exit zone
     */
    fun checkPlayerInZone(playerX: Float, playerY: Float): Boolean {
        val wasInZone = playerInZone
        
        // Check if player is within the exit area
        playerInZone = exitData.area.contains(playerX, playerY)
        
        // Update distance for visual feedback
        val centerX = exitData.area.centerX()
        val centerY = exitData.area.centerY()
        lastPlayerDistance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
        
        // Play sound when entering zone
        if (playerInZone && !wasInZone) {
            audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
            Log.d(TAG, "Player entered exit zone")
        }
        
        return playerInZone
    }
    
    /**
     * Update animations and visual effects
     */
    fun update(deltaTime: Float) {
        // Update glow animation
        glowAnimation += deltaTime * 2f
        if (glowAnimation > 2f * Math.PI.toFloat()) {
            glowAnimation -= 2f * Math.PI.toFloat()
        }
        
        // Update completion animation when player is in zone
        if (playerInZone) {
            completionAnimation += deltaTime * 2f
            if (completionAnimation > 1f) completionAnimation = 1f
        } else {
            completionAnimation -= deltaTime * 3f
            if (completionAnimation < 0f) completionAnimation = 0f
        }
    }
    
    /**
     * Draw the exit zone with appropriate visual feedback
     */
    fun draw(canvas: Canvas) {
        drawExitZone(canvas)
        drawProximityGlow(canvas)
        drawExitLabel(canvas)
        
        if (playerInZone) {
            drawCompletionEffect(canvas)
        }
    }
    
    /**
     * Draw the main exit zone area
     */
    private fun drawExitZone(canvas: Canvas) {
        val baseAlpha = if (playerInZone) 180 else 120
        val pulseIntensity = (0.8f + 0.2f * sin(glowAnimation)).toFloat()
        val alpha = (baseAlpha * pulseIntensity).toInt()
        
        // Fill the exit area with green
        zonePaint.color = Color.argb(alpha, 0, 255, 0)
        canvas.drawRect(exitData.area, zonePaint)
        
        // Draw border
        strokePaint.color = Color.GREEN
        strokePaint.strokeWidth = if (playerInZone) 6f else 4f
        canvas.drawRect(exitData.area, strokePaint)
    }
    
    /**
     * Draw proximity glow effect
     */
    private fun drawProximityGlow(canvas: Canvas) {
        if (lastPlayerDistance <= COMPLETION_RADIUS && !playerInZone) {
            val glowIntensity = 1f - (lastPlayerDistance / COMPLETION_RADIUS)
            val alpha = (80 * glowIntensity).toInt()
            
            glowPaint.color = Color.argb(alpha, 0, 255, 0)
            
            val expandedRect = RectF(
                exitData.area.left - 20f,
                exitData.area.top - 20f,
                exitData.area.right + 20f,
                exitData.area.bottom + 20f
            )
            canvas.drawRect(expandedRect, glowPaint)
        }
    }
    
    /**
     * Draw exit label
     */
    private fun drawExitLabel(canvas: Canvas) {
        val centerX = exitData.area.centerX()
        val centerY = exitData.area.centerY()
        
        // Draw "EXIT" text
        val alpha = if (playerInZone) 255 else 200
        textPaint.color = Color.argb(alpha, 255, 255, 255)
        textPaint.textSize = if (playerInZone) 28f else 24f
        
        canvas.drawText("EXIT", centerX, centerY + 8f, textPaint)
    }
    
    /**
     * Draw completion effect when player is in zone
     */
    private fun drawCompletionEffect(canvas: Canvas) {
        if (completionAnimation > 0f) {
            val centerX = exitData.area.centerX()
            val centerY = exitData.area.centerY()
            val radius = 30f + completionAnimation * 50f
            val alpha = (255 * (1f - completionAnimation)).toInt().coerceIn(0, 255)
            
            // Draw expanding ring
            strokePaint.color = Color.argb(alpha, 0, 255, 0)
            strokePaint.strokeWidth = 6f
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
            
            // Draw inner glow
            glowPaint.color = Color.argb(alpha / 3, 0, 255, 0)
            canvas.drawCircle(centerX, centerY, radius * 0.7f, glowPaint)
        }
    }
    
    /**
     * Check if player is currently in the exit zone
     */
    fun isPlayerInZone(): Boolean = playerInZone
    
    /**
     * Get exit zone data
     */
    fun getExitData(): ExitZoneData = exitData
    
    /**
     * Get description
     */
    fun getDescription(): String = exitData.description
    
    /**
     * Update player distance for visual feedback
     */
    fun updatePlayerDistance(playerX: Float, playerY: Float) {
        val centerX = exitData.area.centerX()
        val centerY = exitData.area.centerY()
        lastPlayerDistance = sqrt((playerX - centerX).pow(2) + (playerY - centerY).pow(2))
    }
} 