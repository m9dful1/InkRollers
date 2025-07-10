package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.*

/**
 * HardenedPaint class for environmental puzzles in campaign mode.
 * Represents hardened paint areas that can only be dissolved with the correct color frequency.
 */
class HardenedPaint(
    private val hardenedPaintData: HardenedPaintData
) {
    companion object {
        private const val TAG = "HardenedPaint"
        private const val DISSOLUTION_PROGRESS_PER_PAINT = 5f
        private const val FULL_DISSOLUTION_THRESHOLD = 100f
    }
    
    // State
    private var isDissolved: Boolean = false
    private var dissolutionProgress: Float = 0f
    private var animationPhase: Float = 0f
    
    // Visual elements
    private val hardenedPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val dissolutionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    /**
     * Update hardened paint state and animations
     */
    fun update(deltaTime: Float) {
        // Update animation phase for visual effects
        animationPhase += deltaTime * 3f
        if (animationPhase > 2 * PI) {
            animationPhase -= (2 * PI).toFloat()
        }
    }
    
    /**
     * Attempt to dissolve hardened paint with a color frequency
     */
    fun attemptDissolution(frequency: ColorFrequency, playerX: Float, playerY: Float): Boolean {
        if (isDissolved) return false
        
        // Check if player is within the hardened paint area
        if (!hardenedPaintData.area.contains(playerX, playerY)) return false
        
        // Check if correct frequency is used
        if (frequency != hardenedPaintData.requiredFrequency) {
            Log.d(TAG, "Wrong frequency used: $frequency, required: ${hardenedPaintData.requiredFrequency}")
            return false
        }
        
        // Progress dissolution
        dissolutionProgress += DISSOLUTION_PROGRESS_PER_PAINT
        
        if (dissolutionProgress >= FULL_DISSOLUTION_THRESHOLD) {
            isDissolved = true
            Log.d(TAG, "Hardened paint dissolved with frequency: $frequency")
            return true
        }
        
        Log.d(TAG, "Dissolution progress: $dissolutionProgress / $FULL_DISSOLUTION_THRESHOLD")
        return false
    }
    
    /**
     * Check if hardened paint can be dissolved with given frequency
     */
    fun canBeDissolved(frequency: ColorFrequency): Boolean {
        return !isDissolved && frequency == hardenedPaintData.requiredFrequency
    }
    
    /**
     * Check if the hardened paint is blocking a position
     */
    fun isBlocking(x: Float, y: Float): Boolean {
        if (isDissolved) return false
        return hardenedPaintData.area.contains(x, y)
    }
    
    /**
     * Get the area of the hardened paint
     */
    fun getArea(): RectF = RectF(hardenedPaintData.area)
    
    /**
     * Check if hardened paint is dissolved
     */
    fun isDissolved(): Boolean = isDissolved
    
    /**
     * Get required frequency for dissolution
     */
    fun getRequiredFrequency(): ColorFrequency = hardenedPaintData.requiredFrequency
    
    /**
     * Get dissolution progress (0.0 to 1.0)
     */
    fun getDissolutionProgress(): Float = dissolutionProgress / FULL_DISSOLUTION_THRESHOLD
    
    /**
     * Draw the hardened paint area
     */
    fun draw(canvas: Canvas) {
        if (isDissolved) return
        
        val area = hardenedPaintData.area
        val alpha = if (dissolutionProgress > 0) {
            (255 * (1f - getDissolutionProgress())).toInt().coerceIn(50, 255)
        } else {
            255
        }
        
        // Set color based on required frequency
        val baseColor = when (hardenedPaintData.requiredFrequency) {
            ColorFrequency.RED -> Color.rgb(150, 50, 50)
            ColorFrequency.BLUE -> Color.rgb(50, 50, 150)
            ColorFrequency.GREEN -> Color.rgb(50, 150, 50)
            ColorFrequency.YELLOW -> Color.rgb(150, 150, 50)
        }
        
        // Draw main hardened paint area
        hardenedPaint.color = baseColor
        hardenedPaint.alpha = alpha
        canvas.drawRect(area, hardenedPaint)
        
        // Draw animated border to indicate it's interactive
        val borderAlpha = (sin(animationPhase) * 50 + 150).toInt()
        val borderColor = when (hardenedPaintData.requiredFrequency) {
            ColorFrequency.RED -> Color.RED
            ColorFrequency.BLUE -> Color.BLUE
            ColorFrequency.GREEN -> Color.GREEN
            ColorFrequency.YELLOW -> Color.YELLOW
        }
        
        borderPaint.color = borderColor
        borderPaint.alpha = borderAlpha
        canvas.drawRect(area, borderPaint)
        
        // Draw dissolution effect if being dissolved
        if (dissolutionProgress > 0 && !isDissolved) {
            drawDissolutionEffect(canvas, area)
        }
        
        // Draw frequency indicator in center
        drawFrequencyIndicator(canvas, area)
    }
    
    /**
     * Draw dissolution effect
     */
    private fun drawDissolutionEffect(canvas: Canvas, area: RectF) {
        val progress = getDissolutionProgress()
        val centerX = area.centerX()
        val centerY = area.centerY()
        
        // Draw expanding dissolution circles
        dissolutionPaint.color = Color.WHITE
        dissolutionPaint.alpha = (100 * (1f - progress)).toInt()
        
        val maxRadius = min(area.width(), area.height()) / 2
        val currentRadius = maxRadius * progress
        
        canvas.drawCircle(centerX, centerY, currentRadius, dissolutionPaint)
        
        // Draw sparkling effect
        val sparkleCount = (progress * 10).toInt()
        dissolutionPaint.alpha = (150 * (1f - progress)).toInt()
        
        for (i in 0 until sparkleCount) {
            val angle = (i * 2 * PI / sparkleCount + animationPhase).toFloat()
            val sparkleRadius = currentRadius * 0.8f
            val sparkleX = centerX + cos(angle) * sparkleRadius
            val sparkleY = centerY + sin(angle) * sparkleRadius
            
            canvas.drawCircle(sparkleX, sparkleY, 3f, dissolutionPaint)
        }
    }
    
    /**
     * Draw frequency indicator in the center of the hardened paint
     */
    private fun drawFrequencyIndicator(canvas: Canvas, area: RectF) {
        val centerX = area.centerX()
        val centerY = area.centerY()
        
        // Draw frequency symbol
        val symbolPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        
        val symbol = when (hardenedPaintData.requiredFrequency) {
            ColorFrequency.RED -> "R"
            ColorFrequency.BLUE -> "B"
            ColorFrequency.GREEN -> "G"
            ColorFrequency.YELLOW -> "Y"
        }
        
        val symbolColor = when (hardenedPaintData.requiredFrequency) {
            ColorFrequency.RED -> Color.RED
            ColorFrequency.BLUE -> Color.BLUE
            ColorFrequency.GREEN -> Color.GREEN
            ColorFrequency.YELLOW -> Color.YELLOW
        }
        
        // Draw background circle for symbol
        val symbolBgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.WHITE
            alpha = 200
        }
        
        canvas.drawCircle(centerX, centerY, 20f, symbolBgPaint)
        
        // Draw symbol
        symbolPaint.color = symbolColor
        canvas.drawText(symbol, centerX, centerY + 8f, symbolPaint)
    }
} 