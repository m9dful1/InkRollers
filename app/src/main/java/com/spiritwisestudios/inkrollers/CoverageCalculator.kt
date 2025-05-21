package com.spiritwisestudios.inkrollers

import android.graphics.RectF

/**
 * Utility class to calculate coverage percentages for painted floor within a maze.
 * Samples the paint surface at intervals and excludes wall regions.
 */
object CoverageCalculator {
    /**
     * Returns a map of color to coverage fraction (0.0-1.0).
     * @param level MazeLevel to obtain wall rectangles
     * @param paintSurface PaintSurface bitmap to sample
     * @param sampleStep pixel interval for sampling; higher improves performance at lower resolution
     */
    fun calculate(
        level: MazeLevel,
        paintSurface: PaintSurface,
        sampleStep: Int = 8
    ): Map<Int, Float> {
        val w = paintSurface.w
        val h = paintSurface.h
        val walls: List<RectF> = level.getWalls()
        val counts = mutableMapOf<Int, Int>()
        var totalSamples = 0

        // Get maze bounds from MazeLevel
        val (viewportOffsetX, viewportOffsetY) = level.getViewportOffset()
        val mazeWidthField = level.javaClass.getDeclaredField("mazeWidth")
        mazeWidthField.isAccessible = true
        val mazeWidth = mazeWidthField.get(level) as Float
        val mazeHeightField = level.javaClass.getDeclaredField("mazeHeight")
        mazeHeightField.isAccessible = true
        val mazeHeight = mazeHeightField.get(level) as Float

        val minX = viewportOffsetX
        val minY = viewportOffsetY
        val maxX = viewportOffsetX + mazeWidth
        val maxY = viewportOffsetY + mazeHeight

        for (y in 0 until h step sampleStep) {
            for (x in 0 until w step sampleStep) {
                val fx = x.toFloat()
                val fy = y.toFloat()
                // Only sample inside the maze bounds
                if (fx < minX || fx >= maxX || fy < minY || fy >= maxY) continue
                // skip wall regions
                if (walls.any { it.contains(fx, fy) }) continue
                val color = paintSurface.getPixelColor(x, y)
                counts[color] = counts.getOrDefault(color, 0) + 1
                totalSamples++
            }
        }

        // compute fractions
        return if (totalSamples > 0) {
            counts.mapValues { it.value.toFloat() / totalSamples.toFloat() }
        } else {
            emptyMap()
        }
    }
} 