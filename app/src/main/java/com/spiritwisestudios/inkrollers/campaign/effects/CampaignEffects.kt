package com.spiritwisestudios.inkrollers.campaign.effects

import android.graphics.*
import android.util.Log
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.campaign.Robot
import kotlin.math.*

/**
 * Visual effects manager for campaign mode
 * Handles all visual effects including color shift, robot conversion, area completion, and bloom effects
 */
class CampaignEffects {
    companion object {
        private const val TAG = "CampaignEffects"
        private const val COLOR_SHIFT_DURATION = 0.5f // seconds
        private const val CONVERSION_DURATION = 1.0f // seconds
        private const val COMPLETION_DURATION = 2.0f // seconds
        private const val BLOOM_DURATION = 1.5f // seconds
    }
    
    // Effect states
    private var colorShiftEffectActive = false
    private var colorShiftEffectTime = 0f
    private var colorShiftEffectCenter: Pair<Float, Float>? = null
    
    private var conversionEffectActive = false
    private var conversionEffectTime = 0f
    private var conversionEffectCenter: Pair<Float, Float>? = null
    
    private var completionEffectActive = false
    private var completionEffectTime = 0f
    private var completionEffectArea: RectF? = null
    
    private var bloomEffectActive = false
    private var bloomEffectTime = 0f
    private var bloomEffectCenter: Pair<Float, Float>? = null
    
    // Paint objects for effects
    private val colorShiftPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val conversionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val completionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val bloomPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    /**
     * Update all active effects
     */
    fun update(deltaTime: Float) {
        // Update color shift effect
        if (colorShiftEffectActive) {
            colorShiftEffectTime += deltaTime
            if (colorShiftEffectTime >= COLOR_SHIFT_DURATION) {
                colorShiftEffectActive = false
                colorShiftEffectTime = 0f
                colorShiftEffectCenter = null
            }
        }
        
        // Update conversion effect
        if (conversionEffectActive) {
            conversionEffectTime += deltaTime
            if (conversionEffectTime >= CONVERSION_DURATION) {
                conversionEffectActive = false
                conversionEffectTime = 0f
                conversionEffectCenter = null
            }
        }
        
        // Update completion effect
        if (completionEffectActive) {
            completionEffectTime += deltaTime
            if (completionEffectTime >= COMPLETION_DURATION) {
                completionEffectActive = false
                completionEffectTime = 0f
                completionEffectArea = null
            }
        }
        
        // Update bloom effect
        if (bloomEffectActive) {
            bloomEffectTime += deltaTime
            if (bloomEffectTime >= BLOOM_DURATION) {
                bloomEffectActive = false
                bloomEffectTime = 0f
                bloomEffectCenter = null
            }
        }
    }
    
    /**
     * Trigger color shift effect
     */
    fun triggerColorShiftEffect(player: Player) {
        colorShiftEffectActive = true
        colorShiftEffectTime = 0f
        colorShiftEffectCenter = Pair(player.x, player.y)
        Log.d(TAG, "Color shift effect triggered at (${player.x}, ${player.y})")
    }
    
    /**
     * Trigger robot conversion effect
     */
    fun triggerRobotConversionEffect(robot: Robot) {
        conversionEffectActive = true
        conversionEffectTime = 0f
        val position = robot.getPosition()
        conversionEffectCenter = position
        Log.d(TAG, "Robot conversion effect triggered at (${position.first}, ${position.second})")
    }
    
    /**
     * Trigger area completion effect
     */
    fun triggerAreaCompletionEffect(area: RectF) {
        completionEffectActive = true
        completionEffectTime = 0f
        completionEffectArea = RectF(area)
        Log.d(TAG, "Area completion effect triggered for area $area")
    }
    
    /**
     * Trigger bloom effect
     */
    fun triggerBloomEffect(center: Pair<Float, Float>) {
        bloomEffectActive = true
        bloomEffectTime = 0f
        bloomEffectCenter = center
        Log.d(TAG, "Bloom effect triggered at (${center.first}, ${center.second})")
    }
    
    /**
     * Draw all active effects
     */
    fun drawEffects(canvas: Canvas) {
        // Draw color shift effect
        if (colorShiftEffectActive && colorShiftEffectCenter != null) {
            drawColorShiftEffect(canvas, colorShiftEffectCenter!!)
        }
        
        // Draw conversion effect
        if (conversionEffectActive && conversionEffectCenter != null) {
            drawRobotConversionEffect(canvas, conversionEffectCenter!!)
        }
        
        // Draw completion effect
        if (completionEffectActive && completionEffectArea != null) {
            drawAreaCompletionEffect(canvas, completionEffectArea!!)
        }
        
        // Draw bloom effect
        if (bloomEffectActive && bloomEffectCenter != null) {
            drawBloomEffect(canvas, bloomEffectCenter!!)
        }
    }
    
    /**
     * Draw color shift visual feedback
     */
    private fun drawColorShiftEffect(canvas: Canvas, center: Pair<Float, Float>) {
        val progress = colorShiftEffectTime / COLOR_SHIFT_DURATION
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        val radius = 100f + progress * 50f
        
        // Create radial gradient for color shift effect
        val gradient = RadialGradient(
            center.first, center.second, radius,
            Color.CYAN, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        
        colorShiftPaint.shader = gradient
        colorShiftPaint.setAlpha(alpha)
        
        canvas.drawCircle(center.first, center.second, radius, colorShiftPaint)
        
        // Draw frequency indicator
        val frequencyRadius = 30f
        val frequencyPaint = Paint().apply {
            color = Color.WHITE
            setAlpha(alpha)
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        
        canvas.drawCircle(center.first, center.second, frequencyRadius, frequencyPaint)
    }
    
    /**
     * Draw robot conversion effect
     */
    private fun drawRobotConversionEffect(canvas: Canvas, center: Pair<Float, Float>) {
        val progress = conversionEffectTime / CONVERSION_DURATION
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        val radius = 60f + progress * 80f
        
        // Create conversion ring effect
        val ringPaint = Paint().apply {
            setAlpha(alpha)
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        
        canvas.drawCircle(center.first, center.second, radius, ringPaint)
        
        // Draw conversion particles
        val particleCount = 12
        for (i in 0 until particleCount) {
            val angle = (i * 2 * PI / particleCount).toFloat()
            val particleRadius = 40f + progress * 60f
            val particleX = center.first + cos(angle) * particleRadius
            val particleY = center.second + sin(angle) * particleRadius
            
            val particlePaint = Paint().apply {
                color = Color.CYAN
                setAlpha((alpha * (1f - progress)).toInt())
                isAntiAlias = true
            }
            
            canvas.drawCircle(particleX, particleY, 4f, particlePaint)
        }
    }
    
    /**
     * Draw area completion effect
     */
    private fun drawAreaCompletionEffect(canvas: Canvas, area: RectF) {
        val progress = completionEffectTime / COMPLETION_DURATION
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        
        // Draw completion glow
        val glowPaint = Paint().apply {
            setAlpha(alpha / 2)
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        
        canvas.drawRect(area, glowPaint)
        
        // Draw completion border
        val borderPaint = Paint().apply {
            setAlpha(alpha)
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        
        canvas.drawRect(area, borderPaint)
        
        // Draw completion particles
        val particleCount = 8
        for (i in 0 until particleCount) {
            val particleX = area.left + (area.right - area.left) * (i.toFloat() / particleCount)
            val particleY = area.top + sin((progress * 2 * PI + i).toFloat()) * 20f
            
            val particlePaint = Paint().apply {
                color = Color.WHITE
                setAlpha((alpha * (1f - progress)).toInt())
                isAntiAlias = true
            }
            
            canvas.drawCircle(particleX, particleY, 3f, particlePaint)
        }
    }
    
    /**
     * Draw bloom effect
     */
    private fun drawBloomEffect(canvas: Canvas, center: Pair<Float, Float>) {
        val progress = bloomEffectTime / BLOOM_DURATION
        val alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
        val radius = 80f + progress * 120f
        
        // Create bloom gradient
        val gradient = RadialGradient(
            center.first, center.second, radius,
            Color.WHITE, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        
        val bloomEffectPaint = Paint().apply {
            shader = gradient
            setAlpha(alpha / 3)
        }
        
        canvas.drawCircle(center.first, center.second, radius, bloomEffectPaint)
        
        // Draw bloom rays
        val rayCount = 8
        for (i in 0 until rayCount) {
            val angle = (i * 2 * PI / rayCount).toFloat()
            val rayLength = 100f + progress * 80f
            val endX = center.first + cos(angle) * rayLength
            val endY = center.second + sin(angle) * rayLength
            
            val rayPaint = Paint()
            rayPaint.color = Color.WHITE
            rayPaint.setAlpha((alpha * (1f - progress)).toInt())
            rayPaint.strokeWidth = 4f
            rayPaint.style = Paint.Style.STROKE
            
            canvas.drawLine(center.first, center.second, endX, endY, rayPaint)
        }
    }
    
    /**
     * Check if any effects are currently active
     */
    fun hasActiveEffects(): Boolean {
        return colorShiftEffectActive || conversionEffectActive || 
               completionEffectActive || bloomEffectActive
    }
} 