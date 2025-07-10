package com.spiritwisestudios.inkrollers.effects

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Represents a single particle in the paint splat effect system.
 * Particles are lightweight objects that move, fade, and eventually disappear.
 */
class Particle(
    private val startX: Float,
    private val startY: Float,
    private val color: Int,
    private val velocity: Float = 100f,
    private val lifetime: Float = 0.8f,
    private val size: Float = 8f
) {
    // Current position
    var x: Float = startX
    var y: Float = startY
    
    // Velocity components
    private val velocityX: Float
    private var velocityY: Float
    
    // Life tracking
    private var age: Float = 0f
    private val maxAge: Float = lifetime
    
    // Visual properties
    private val paint: Paint = Paint().apply {
        this.color = this@Particle.color
        isAntiAlias = true
        alpha = 255
    }
    
    // Random direction for natural splat effect
    init {
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        velocityX = cos(angle) * velocity * (0.5f + Random.nextFloat() * 0.5f)
        velocityY = sin(angle) * velocity * (0.5f + Random.nextFloat() * 0.5f)
    }
    
    /**
     * Update particle position and life
     * @param deltaTime Time elapsed since last update in seconds
     * @return true if particle is still alive, false if it should be removed
     */
    fun update(deltaTime: Float): Boolean {
        age += deltaTime
        
        if (age >= maxAge) {
            return false // Particle is dead
        }
        
        // Update position with velocity and gravity
        x += velocityX * deltaTime
        y += velocityY * deltaTime
        
        // Apply gravity effect
        velocityY += 200f * deltaTime // Gravity acceleration
        
        // Update visual properties based on age
        val lifeProgress = age / maxAge
        val alpha = (255 * (1f - lifeProgress)).toInt().coerceIn(0, 255)
        paint.alpha = alpha
        
        // Shrink particle as it ages
        val currentSize = size * (1f - lifeProgress * 0.5f)
        paint.strokeWidth = currentSize
        
        return true // Particle is still alive
    }
    
    /**
     * Draw the particle to the canvas
     */
    fun draw(canvas: Canvas) {
        canvas.drawCircle(x, y, paint.strokeWidth / 2f, paint)
    }
    
    /**
     * Check if particle is still alive
     */
    fun isAlive(): Boolean = age < maxAge
} 