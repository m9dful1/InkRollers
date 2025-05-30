package com.spiritwisestudios.inkrollers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ZoneOwnershipCalculatorTest {

    @Mock
    private lateinit var mockLevel: MazeLevel

    @Mock
    private lateinit var mockPaintSurface: PaintSurface

    @Mock
    private lateinit var mockBitmap: Bitmap

    private val red = Color.RED
    private val blue = Color.BLUE
    private val green = Color.GREEN
    private val transparent = Color.TRANSPARENT

    // Common viewport offset for tests, assuming no actual offset for simplicity unless specified
    private val testViewportOffset = Pair(0f, 0f)
    private val testBitmapWidth = 100
    private val testBitmapHeight = 100


    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockPaintSurface.getBitmap()).thenReturn(mockBitmap)
        `when`(mockBitmap.width).thenReturn(testBitmapWidth)
        `when`(mockBitmap.height).thenReturn(testBitmapHeight)
        `when`(mockLevel.getViewportOffset()).thenReturn(testViewportOffset)
    }

    private fun mockPixelData(zone: RectF, colorMap: Map<Pair<Int, Int>, Int>, isWallMap: Map<Pair<Int, Int>, Boolean> = emptyMap()) {
        val startX = (normalizedToScreenX(zone.left, testViewportOffset.first, testBitmapWidth.toFloat())).toInt()
        val startY = (normalizedToScreenY(zone.top, testViewportOffset.second, testBitmapHeight.toFloat())).toInt()
        val endX = (normalizedToScreenX(zone.right, testViewportOffset.first, testBitmapWidth.toFloat())).toInt()
        val endY = (normalizedToScreenY(zone.bottom, testViewportOffset.second, testBitmapHeight.toFloat())).toInt()

        for (x in startX until endX) {
            for (y in startY until endY) {
                val pixelColor = colorMap[Pair(x, y)] ?: transparent
                `when`(mockBitmap.getPixel(x, y)).thenReturn(pixelColor)
                val isWall = isWallMap[Pair(x,y)] ?: false
                `when`(mockLevel.checkCollision(x.toFloat(), y.toFloat())).thenReturn(isWall)
            }
        }
    }

    // Helper to convert normalized to screen coordinates for mocking (simplified)
    private fun normalizedToScreenX(normX: Float, viewportOffsetX: Float, mazeWidth: Float): Float {
        return viewportOffsetX + normX * (mazeWidth - 2 * viewportOffsetX)
    }
    private fun normalizedToScreenY(normY: Float, viewportOffsetY: Float, mazeHeight: Float): Float {
        return viewportOffsetY + normY * (mazeHeight - 2 * viewportOffsetY)
    }


    @Test
    fun `calculateZoneOwnership returns empty map when level has no zones`() {
        `when`(mockLevel.getZones()).thenReturn(emptyList())
        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface)
        assertTrue(ownership.isEmpty())
    }

    @Test
    fun `single zone fully red should be owned by red`() {
        val zone0 = RectF(0f, 0f, 0.5f, 0.5f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 0 until 50) { // Assuming 0.5f of 100 width is 50 pixels
            for (y in 0 until 50) { // Assuming 0.5f of 100 height is 50 pixels
                colorMap[Pair(x,y)] = red
            }
        }
        mockPixelData(zone0, colorMap)

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertEquals(red, ownership[0])
    }

    @Test
    fun `single zone 50-50 split red blue should have no owner`() {
        val zone0 = RectF(0f, 0f, 1f, 1f) // Full zone
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = if (x < 50) red else blue
            }
        }
        mockPixelData(zone0, colorMap)

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertNull(ownership[0]) // No owner if not > 50%
    }
    
    @Test
    fun `single zone more than 50 percent red, rest blue should be red`() {
        val zone0 = RectF(0f, 0f, 1f, 1f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = if (x < 60) red else blue // 60% red
            }
        }
        mockPixelData(zone0, colorMap)

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertEquals(red, ownership[0])
    }


    @Test
    fun `single zone with some wall pixels, red majority excluding walls`() {
        val zone0 = RectF(0f, 0f, 1f, 1f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        val wallMap = mutableMapOf<Pair<Int, Int>, Boolean>()
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                if (y < 10) { // First 10 rows are "wall"
                    wallMap[Pair(x,y)] = true
                    colorMap[Pair(x,y)] = green // Wall color, should be ignored
                } else {
                    wallMap[Pair(x,y)] = false
                    colorMap[Pair(x,y)] = red // Paint red on non-wall area
                }
            }
        }
        mockPixelData(zone0, colorMap, wallMap)

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertEquals(red, ownership[0]) // Red should own it as walls are excluded
    }

    @Test
    fun `single zone no paint should have no owner`() {
        val zone0 = RectF(0f, 0f, 1f, 1f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))
        mockPixelData(zone0, emptyMap()) // No painted pixels

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertNull(ownership[0])
    }
    
    @Test
    fun `single zone only transparent pixels should have no owner`() {
        val zone0 = RectF(0f, 0f, 1f, 1f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = transparent
            }
        }
        mockPixelData(zone0, colorMap)

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertNull(ownership[0])
    }

    @Test
    fun `multiple zones correct ownership`() {
        val zone0 = RectF(0f, 0f, 0.5f, 1f) // Left half
        val zone1 = RectF(0.5f, 0f, 1f, 1f) // Right half
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0, zone1))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        // Zone 0 (left) is all red
        for (x in 0 until 50) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = red
            }
        }
        // Zone 1 (right) is all blue
        for (x in 50 until 100) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = blue
            }
        }
        mockPixelData(zone0, colorMap) // Mock for zone0 area
        mockPixelData(zone1, colorMap) // Mock for zone1 area (uses the same combined map)


        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(2, ownership.size)
        assertEquals(red, ownership[0])
        assertEquals(blue, ownership[1])
    }
    
    @Test
    fun `sampleStep variation still gives correct owner`() {
        val zone0 = RectF(0f, 0f, 1f, 1f)
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                colorMap[Pair(x,y)] = if (x < 70) green else blue // 70% green
            }
        }
        mockPixelData(zone0, colorMap)

        val ownershipStep1 = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(green, ownershipStep1[0])

        val ownershipStep5 = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 5)
        assertEquals(green, ownershipStep5[0])
        
        val ownershipStep10 = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 10)
        assertEquals(green, ownershipStep10[0])
    }

    @Test
    fun `zone at bitmap edge with paint only at edge`() {
        // This test checks if sampling near boundaries works.
        // We'll define a small zone at the very top-left.
        val zoneEdge = RectF(0f, 0f, 0.1f, 0.1f) // Covers 0-9 in x and y
         `when`(mockLevel.getZones()).thenReturn(listOf(zoneEdge))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        // Paint only the very first pixel (0,0) red.
        colorMap[Pair(0,0)] = red
        // All other pixels in this small zone (up to 9,9) are transparent
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                if (x != 0 || y != 0) {
                     colorMap[Pair(x,y)] = transparent
                }
            }
        }
        mockPixelData(zoneEdge, colorMap)

        // With sampleStep = 1, pixel (0,0) should be sampled.
        // Since it's the *only* non-transparent pixel, it should be > 50% of paint.
        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertEquals(red, ownership[0])
    }
    
    @Test
    fun `zone with mixed content and wall, blue majority`() {
        val zone0 = RectF(0.2f, 0.2f, 0.8f, 0.8f) // A central zone
        `when`(mockLevel.getZones()).thenReturn(listOf(zone0))

        val colorMap = mutableMapOf<Pair<Int, Int>, Int>()
        val wallMap = mutableMapOf<Pair<Int, Int>, Boolean>()

        // Zone screen coordinates (approx for a 100x100 bitmap with no offset)
        // Left: 0.2*100=20, Right: 0.8*100=80 -> x from 20 to 79
        // Top: 0.2*100=20, Bottom: 0.8*100=80 -> y from 20 to 79
        for (x in 20 until 80) {
            for (y in 20 until 80) {
                // Make a strip of wall in the middle
                if (x >= 45 && x < 55) { // x from 45 to 54 is wall
                    wallMap[Pair(x,y)] = true
                    colorMap[Pair(x,y)] = green // Wall color, should be ignored
                } else {
                    wallMap[Pair(x,y)] = false
                    // Left part (x < 45) mostly blue, right part (x >= 55) some red
                    if (x < 45) { // 20 to 44 (25 columns)
                        colorMap[Pair(x,y)] = blue
                    } else { // 55 to 79 (25 columns)
                        colorMap[Pair(x,y)] = if (y < 50) blue else red // Top half blue, bottom half red
                    }
                }
            }
        }
        mockPixelData(zone0, colorMap, wallMap)
        // Analysis:
        // Total cells in zone (approx): 60x60 = 3600
        // Wall cells (approx): 10x60 = 600 (ignored)
        // Paintable cells (approx): 3000
        // Blue paint:
        //  - Left side: 25 cols * 60 rows = 1500
        //  - Right side, top half: 25 cols * 30 rows = 750
        //  Total Blue = 1500 + 750 = 2250
        // Red paint:
        //  - Right side, bottom half: 25 cols * 30 rows = 750
        // Total Red = 750
        // Blue is 2250 / 3000 = 75% -> Blue should own

        val ownership = ZoneOwnershipCalculator.calculateZoneOwnership(mockLevel, mockPaintSurface, sampleStep = 1)
        assertEquals(1, ownership.size)
        assertEquals(blue, ownership[0])
    }
} 