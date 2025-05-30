package com.spiritwisestudios.inkrollers

import android.graphics.RectF
import android.util.Log

/**
 * Utility class to calculate zone ownership in Zones game mode.
 * Samples pixels within each zone, skips walls, and determines the majority owner.
 */
object ZoneOwnershipCalculator {
    
    private const val TAG = "ZoneOwnershipCalculator"
    
    /**
     * Calculate zone ownership for a maze level.
     * @param level The maze level containing zones
     * @param paintSurface The paint surface to sample
     * @param sampleStep Sampling step size (higher = faster but less accurate)
     * @return Map of zone index to owner color (null if uncontrolled)
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
        
        // Convert normalized zone coordinates to screen coordinates
        val (offsetX, offsetY) = viewportOffset
        val mazeWidth = bitmap.width - 2 * offsetX
        val mazeHeight = bitmap.height - 2 * offsetY
        
        val screenLeft = (offsetX + normalizedZone.left * mazeWidth).toInt()
        val screenTop = (offsetY + normalizedZone.top * mazeHeight).toInt()
        val screenRight = (offsetX + normalizedZone.right * mazeWidth).toInt()
        val screenBottom = (offsetY + normalizedZone.bottom * mazeHeight).toInt()
        
        // Sample pixels within the zone
        for (y in screenTop..screenBottom step sampleStep) {
            for (x in screenLeft..screenRight step sampleStep) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    // Skip wall pixels
                    if (!level.checkCollision(x.toFloat(), y.toFloat())) {
                        val pixel = bitmap.getPixel(x, y)
                        // Skip transparent/background pixels
                        if (android.graphics.Color.alpha(pixel) > 0) {
                            colorCounts[pixel] = colorCounts.getOrDefault(pixel, 0) + 1
                            totalSamples++
                        }
                    }
                }
            }
        }
        
        // Determine majority owner (need >50% to control zone)
        val majorityThreshold = totalSamples * 0.5
        val majorityOwner = colorCounts.entries
            .filter { it.value > majorityThreshold }
            .maxByOrNull { it.value }
            ?.key
        
        Log.v(TAG, "Zone $zoneIndex: ${colorCounts.size} colors, $totalSamples samples, owner: ${majorityOwner?.let { "#${Integer.toHexString(it)}" } ?: "none"}")
        
        return majorityOwner
    }
} 