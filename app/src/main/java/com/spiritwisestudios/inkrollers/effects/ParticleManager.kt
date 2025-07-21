package com.spiritwisestudios.inkrollers.effects

import android.graphics.Canvas
import android.util.Log
import kotlin.random.Random

/**
 * Manages all particle effects in the game, including paint splats.
 * Handles particle creation, updates, rendering, and cleanup.
 */
class ParticleManager {
    
    companion object {
        private const val TAG = "ParticleManager"
        private const val MAX_PARTICLES = 200 // Performance limit
        private const val PARTICLES_PER_SPLAT = 8 // Number of particles per paint action
        private const val PERFORMANCE_WARNING_THRESHOLD = 150 // Warn when approaching limit
    }
    
    // Active particles
    private val particles = mutableListOf<Particle>()
    
    // Performance tracking
    private var totalParticlesCreated = 0
    private var totalParticlesRemoved = 0
    private var lastPerformanceLogTime = 0L
    
    /**
     * Create a paint splat effect at the specified location
     * @param x X coordinate for the splat
     * @param y Y coordinate for the splat
     * @param color Color of the paint splat
     */
    fun createPaintSplat(x: Float, y: Float, color: Int) {
        // Don't create particles if we're at the limit
        if (particles.size >= MAX_PARTICLES) {
            // Log.v(TAG, "Particle limit reached, skipping paint splat")
            return
        }
        
        // Warn if approaching limit
        if (particles.size >= PERFORMANCE_WARNING_THRESHOLD) {
            // Log.w(TAG, "High particle count: ${particles.size}/${MAX_PARTICLES}")
        }
        
        // Create multiple particles for a more realistic splat effect
        repeat(PARTICLES_PER_SPLAT) {
            // Add some randomness to the spawn position
            val spawnX = x + Random.nextFloat() * 20f - 10f
            val spawnY = y + Random.nextFloat() * 20f - 10f
            
            // Vary particle properties for natural look
            val velocity = 80f + Random.nextFloat() * 60f
            val lifetime = 0.6f + Random.nextFloat() * 0.4f
            val size = 6f + Random.nextFloat() * 6f
            
            val particle = Particle(spawnX, spawnY, color, velocity, lifetime, size)
            particles.add(particle)
            totalParticlesCreated++
        }
        
        // Log.v(TAG, "Created paint splat with ${PARTICLES_PER_SPLAT} particles at ($x, $y)")
    }
    
    /**
     * Update all particles and remove dead ones
     * @param deltaTime Time elapsed since last update in seconds
     */
    fun update(deltaTime: Float) {
        // Update particles and collect dead ones
        val deadParticles = mutableListOf<Particle>()
        
        for (particle in particles) {
            if (!particle.update(deltaTime)) {
                deadParticles.add(particle)
            }
        }
        
        // Remove dead particles
        particles.removeAll(deadParticles)
        totalParticlesRemoved += deadParticles.size
        
        // Log performance stats occasionally (every 5 seconds)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPerformanceLogTime > 5000) {
            if (totalParticlesCreated > 0) {
                // Log.d(TAG, "Particle stats - Created: $totalParticlesCreated, Removed: $totalParticlesRemoved, Active: ${particles.size}")
                lastPerformanceLogTime = currentTime
            }
        }
    }
    
    /**
     * Draw all particles to the canvas
     * @param canvas Canvas to draw on
     */
    fun draw(canvas: Canvas) {
        for (particle in particles) {
            try {
                particle.draw(canvas)
            } catch (e: Exception) {
                // Log.w(TAG, "Error drawing particle", e)
            }
        }
    }
    
    /**
     * Clear all particles (useful for game reset)
     */
    fun clear() {
        val removedCount = particles.size
        particles.clear()
        // Log.d(TAG, "Cleared $removedCount particles")
    }
    
    /**
     * Get the current number of active particles
     */
    fun getActiveParticleCount(): Int = particles.size
    
    /**
     * Check if particle system is active (has particles)
     */
    fun hasActiveParticles(): Boolean = particles.isNotEmpty()
    
    /**
     * Check if particle system is under heavy load
     */
    fun isUnderHeavyLoad(): Boolean = particles.size >= PERFORMANCE_WARNING_THRESHOLD
    
    /**
     * Get performance statistics
     */
    fun getStats(): ParticleStats {
        return ParticleStats(
            activeCount = particles.size,
            totalCreated = totalParticlesCreated,
            totalRemoved = totalParticlesRemoved,
            isUnderHeavyLoad = isUnderHeavyLoad()
        )
    }
    
    /**
     * Data class for particle system statistics
     */
    data class ParticleStats(
        val activeCount: Int,
        val totalCreated: Int,
        val totalRemoved: Int,
        val isUnderHeavyLoad: Boolean
    )
} 