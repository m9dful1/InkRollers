package com.spiritwisestudios.inkrollers.items

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import java.util.UUID

/**
 * Base abstract class providing common functionality for all items
 */
abstract class BaseItem(
    override val type: ItemType,
    private val x: Float,
    private val y: Float,
    private val radius: Float = 20f
) : Item {
    
    companion object {
        private const val PULSE_SPEED = 3f
        private const val PULSE_AMPLITUDE = 0.2f
        private const val ROTATION_SPEED = 90f // degrees per second
    }
    
    override val position: Pair<Float, Float> = Pair(x, y)
    private var _isActive: Boolean = true
    override val isActive: Boolean get() = _isActive
    
    override val bounds: RectF
        get() = RectF(x - radius, y - radius, x + radius, y + radius)
    
    override val id: String = UUID.randomUUID().toString()
    
    // Animation properties
    private var animationTime: Float = 0f
    private var rotationAngle: Float = 0f
    
    // Visual properties
    protected val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    protected val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    
    override fun update(deltaTime: Float) {
        if (!isActive) return
        
        animationTime += deltaTime
        rotationAngle += ROTATION_SPEED * deltaTime
        
        // Keep rotation angle within 0-360 degrees
        if (rotationAngle >= 360f) {
            rotationAngle -= 360f
        }
        
        // Call subclass update logic
        onUpdate(deltaTime)
    }
    
    override fun draw(canvas: Canvas) {
        if (!isActive) return
        
        canvas.save()
        
        // Apply pulsing effect
        val pulseScale = 1f + PULSE_AMPLITUDE * sin(animationTime * PULSE_SPEED)
        canvas.scale(pulseScale, pulseScale, x, y)
        
        // Apply rotation
        canvas.rotate(rotationAngle, x, y)
        
        // Draw the item
        onDraw(canvas)
        
        // Draw border
        canvas.drawCircle(x, y, radius, strokePaint)
        
        canvas.restore()
    }
    
    override fun onCollected(playerId: String): Boolean {
        if (!isActive || !canBeCollectedBy(playerId)) return false
        
        val success = onItemCollected(playerId)
        if (success) {
            deactivate()
        }
        return success
    }
    
    override fun onUsed(playerId: String): Boolean {
        if (!isActive) return false
        return onItemUsed(playerId)
    }
    
    override fun deactivate() {
        _isActive = false
    }
    
    override fun canBeCollectedBy(playerId: String): Boolean {
        return isActive
    }
    
    /**
     * Get the current pulse scale for animations
     */
    protected fun getPulseScale(): Float {
        return 1f + PULSE_AMPLITUDE * sin(animationTime * PULSE_SPEED)
    }
    
    /**
     * Get the current rotation angle in degrees
     */
    protected fun getRotationAngle(): Float {
        return rotationAngle
    }
    
    /**
     * Called during update() for subclass-specific logic
     */
    protected open fun onUpdate(deltaTime: Float) {
        // Override in subclasses for custom update logic
    }
    
    /**
     * Called during draw() for subclass-specific rendering
     */
    protected abstract fun onDraw(canvas: Canvas)
    
    /**
     * Called when the item is collected - implement collection logic
     */
    protected abstract fun onItemCollected(playerId: String): Boolean
    
    /**
     * Called when the item is used - implement usage logic
     */
    protected abstract fun onItemUsed(playerId: String): Boolean
} 