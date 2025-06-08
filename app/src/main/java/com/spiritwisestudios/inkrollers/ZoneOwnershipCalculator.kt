package com.spiritwisestudios.inkrollers

import android.graphics.RectF
import android.util.Log

/**
 * Calculates zone control for Zones game mode by analyzing paint distribution.
 * 
 * Samples pixels within each zone boundary while excluding wall areas to determine
 * which player has majority control. Used by GameView to update ZoneHudView and
 * evaluate win conditions. Requires >50% painted area to control a zone.
 */
object ZoneOwnershipCalculator {
    
    private const val TAG = "ZoneOwnershipCalculator"
    
    /**
     * Determines ownership of all zones in the maze based on paint coverage.
     * 
     * @param level Maze level containing zone definitions and collision detection
     * @param paintSurface Surface containing painted pixels to analyze
     * @param sampleStep Pixel sampling frequency (higher values improve performance)
     * @return Map of zone indices to controlling player colors (null if neutral)
     */
    fun calculateZoneOwnership(
        level: MazeLevel,
        paintSurface: PaintSurface,
        sampleStep: Int = 4
    ): Map<Int, Int?> {
        val zones = level.getZones()
        val zoneOwnership = mutableMapOf<Int, Int?>()
        
        if (zones.isEmpty()) {
            Log.w(TAG, "No zones defined for level")
            return zoneOwnership
        }
        
        val bitmap = paintSurface.getBitmap()
        val viewportOffset = level.getViewportOffset()
        
        zones.forEachIndexed { zoneIndex, normalizedZone ->
            val ownership = calculateSingleZoneOwnership(
                zoneIndex,
                normalizedZone,
                level,
                bitmap,
                viewportOffset,
                sampleStep
            )
            zoneOwnership[zoneIndex] = ownership
        }
        
        return zoneOwnership
    }
    
    /** Analyzes a single zone to determine its controlling player based on majority paint coverage. */
    private fun calculateSingleZoneOwnership(
        zoneIndex: Int,
        normalizedZone: RectF,
        level: MazeLevel,
        bitmap: android.graphics.Bitmap,
        viewportOffset: Pair<Float, Float>,
        sampleStep: Int
    ): Int? {
        val colorCounts = mutableMapOf<Int, Int>()
        var totalSamples = 0
        
        val (offsetX, offsetY) = viewportOffset
        val mazeWidth = bitmap.width - 2 * offsetX
        val mazeHeight = bitmap.height - 2 * offsetY
        
        val screenLeft = (offsetX + normalizedZone.left * mazeWidth).toInt()
        val screenTop = (offsetY + normalizedZone.top * mazeHeight).toInt()
        val screenRight = (offsetX + normalizedZone.right * mazeWidth).toInt()
        val screenBottom = (offsetY + normalizedZone.bottom * mazeHeight).toInt()
        
        for (y in screenTop..screenBottom step sampleStep) {
            for (x in screenLeft..screenRight step sampleStep) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    if (!level.checkCollision(x.toFloat(), y.toFloat())) {
                        val pixel = bitmap.getPixel(x, y)
                        if (android.graphics.Color.alpha(pixel) > 0) {
                            colorCounts[pixel] = colorCounts.getOrDefault(pixel, 0) + 1
                            totalSamples++
                        }
                    }
                }
            }
        }
        
        val majorityThreshold = totalSamples * 0.5
        val majorityOwner = colorCounts.entries
            .filter { it.value > majorityThreshold }
            .maxByOrNull { it.value }
            ?.key
        
        Log.v(TAG, "Zone $zoneIndex: ${colorCounts.size} colors, $totalSamples samples, owner: ${majorityOwner?.let { "#${Integer.toHexString(it)}" } ?: "none"}")
        
        return majorityOwner
    }
} 