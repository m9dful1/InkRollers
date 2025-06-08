package com.spiritwisestudios.inkrollers

import android.graphics.Color

/**
 * Abstraction for objects that provide pixel color access for coverage calculations.
 * Allows CoverageCalculator to work with different pixel data sources.
 */
interface PaintPixelProvider {
    fun getPixelColor(x: Int, y: Int): Int
    val w: Int
    val h: Int
}

/** Extension function to adapt PaintSurface to PaintPixelProvider interface. */
fun PaintSurface.asPixelProvider(): PaintPixelProvider = object : PaintPixelProvider {
    override fun getPixelColor(x: Int, y: Int): Int = this@asPixelProvider.getPixelColor(x, y)
    override val w: Int = this@asPixelProvider.w
    override val h: Int = this@asPixelProvider.h
}

/**
 * Calculates paint coverage statistics across the playable maze area.
 * 
 * Samples pixels from the paint surface while excluding wall areas to determine
 * how much of the level each player has painted. Used by Coverage game mode
 * for win condition evaluation and by GameView for HUD updates.
 */
object CoverageCalculator {
    
    /**
     * Calculates coverage percentages for each color on the paint surface.
     * Excludes wall areas and unpainted pixels from the calculation.
     * 
     * @param level Maze level used for collision detection to exclude walls
     * @param paintSurface Surface containing all painted pixels
     * @param sampleStep Sampling frequency (1=every pixel, higher=faster but less precise)
     * @return Map of player colors to their coverage fraction (0.0-1.0)
     */
    fun calculate(level: Level, paintSurface: PaintSurface, sampleStep: Int = 4): Map<Int, Float> {
        return calculate(level, paintSurface.asPixelProvider(), sampleStep)
    }
    
    /** Internal calculation method using the pixel provider abstraction. */
    fun calculate(level: Level, pixelProvider: PaintPixelProvider, sampleStep: Int = 4): Map<Int, Float> {
        val actualSampleStep = when {
            sampleStep <= 0 -> 1
            else -> sampleStep
        }
        
        val colorCounts = mutableMapOf<Int, Int>()
        var totalValidPixels = 0
        
        for (y in 0 until pixelProvider.h step actualSampleStep) {
            for (x in 0 until pixelProvider.w step actualSampleStep) {
                if (level.checkCollision(x.toFloat(), y.toFloat())) {
                    continue
                }
                
                val pixelColor = pixelProvider.getPixelColor(x, y)
                
                if (pixelColor == Color.TRANSPARENT || Color.alpha(pixelColor) == 0) {
                    totalValidPixels++
                    continue
                }
                
                colorCounts[pixelColor] = colorCounts.getOrDefault(pixelColor, 0) + 1
                totalValidPixels++
            }
        }
        
        return if (totalValidPixels > 0) {
            colorCounts.mapValues { (_, count) -> count.toFloat() / totalValidPixels }
        } else {
            emptyMap()
        }
    }
} 