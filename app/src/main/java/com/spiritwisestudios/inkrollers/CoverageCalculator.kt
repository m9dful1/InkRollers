package com.spiritwisestudios.inkrollers

import android.graphics.Color

/**
 * Interface for objects that can provide pixel color information
 */
interface PaintPixelProvider {
    fun getPixelColor(x: Int, y: Int): Int
    val w: Int
    val h: Int
}

/**
 * Extension to make PaintSurface implement PaintPixelProvider
 */
fun PaintSurface.asPixelProvider(): PaintPixelProvider = object : PaintPixelProvider {
    override fun getPixelColor(x: Int, y: Int): Int = this@asPixelProvider.getPixelColor(x, y)
    override val w: Int = this@asPixelProvider.w
    override val h: Int = this@asPixelProvider.h
}

/**
 * Utility object for calculating paint coverage statistics across the maze level.
 * Used by both Coverage and Zone game modes for determining player performance.
 */
object CoverageCalculator {
    
    /**
     * Calculate coverage percentages for each color on the paint surface.
     * 
     * @param level The maze level to check for wall collisions
     * @param paintSurface The surface containing painted pixels
     * @param sampleStep Step size for sampling (1 = every pixel, higher = faster but less accurate)
     * @return Map of color to coverage fraction (0.0 to 1.0)
     */
    fun calculate(level: Level, paintSurface: PaintSurface, sampleStep: Int = 4): Map<Int, Float> {
        return calculate(level, paintSurface.asPixelProvider(), sampleStep)
    }
    
    /**
     * Calculate coverage percentages for each color using a pixel provider.
     * 
     * @param level The maze level to check for wall collisions
     * @param pixelProvider The provider that can return pixel colors
     * @param sampleStep Step size for sampling (1 = every pixel, higher = faster but less accurate)
     * @return Map of color to coverage fraction (0.0 to 1.0)
     */
    fun calculate(level: Level, pixelProvider: PaintPixelProvider, sampleStep: Int = 4): Map<Int, Float> {
        // Handle invalid sample steps
        val actualSampleStep = when {
            sampleStep <= 0 -> 1
            else -> sampleStep
        }
        
        val colorCounts = mutableMapOf<Int, Int>()
        var totalValidPixels = 0
        
        // Sample pixels across the surface
        for (y in 0 until pixelProvider.h step actualSampleStep) {
            for (x in 0 until pixelProvider.w step actualSampleStep) {
                // Skip pixels that are on walls
                if (level.checkCollision(x.toFloat(), y.toFloat())) {
                    continue
                }
                
                val pixelColor = pixelProvider.getPixelColor(x, y)
                
                // Skip transparent/unpainted pixels
                if (pixelColor == Color.TRANSPARENT || Color.alpha(pixelColor) == 0) {
                    totalValidPixels++
                    continue
                }
                
                // Count this colored pixel
                colorCounts[pixelColor] = colorCounts.getOrDefault(pixelColor, 0) + 1
                totalValidPixels++
            }
        }
        
        // Convert counts to percentages
        return if (totalValidPixels > 0) {
            colorCounts.mapValues { (_, count) -> count.toFloat() / totalValidPixels }
        } else {
            emptyMap()
        }
    }
} 