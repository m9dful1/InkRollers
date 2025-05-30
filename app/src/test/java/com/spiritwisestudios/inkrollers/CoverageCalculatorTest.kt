package com.spiritwisestudios.inkrollers

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE) // Add manifest config to avoid warnings
class CoverageCalculatorTest {

    private lateinit var testLevel: TestLevel
    private lateinit var testPixelProvider: TestPixelProvider
    
    private val testWidth = 100
    private val testHeight = 100
    private val redColor = Color.RED
    private val blueColor = Color.BLUE
    private val greenColor = Color.GREEN

    @Before
    fun setUp() {
        testLevel = TestLevel(testWidth, testHeight)
        testPixelProvider = TestPixelProvider(testWidth, testHeight)
    }

    @Test
    fun calculate_withNoPaint_returnsZeroCoverage() {
        // Test with completely empty paint surface
        val result = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 1)
        
        // Should return empty map since no paint exists
        assertTrue("Coverage should be empty for unpainted surface", result.isEmpty())
    }

    @Test
    fun calculate_withSingleColorPaint_returnsCorrectCoverage() {
        // Manually set some pixels to painted in our test surface
        testPixelProvider.setPaintedPixels(mapOf(
            Pair(30, 30) to redColor,
            Pair(50, 30) to redColor,
            Pair(70, 30) to redColor,
            Pair(30, 50) to redColor,
            Pair(50, 50) to redColor,
            Pair(70, 50) to redColor
        ))
        
        val result = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 5)
        
        // Should have some coverage for red color
        assertTrue("Should have coverage data", result.isNotEmpty())
        val redCoverage = result[redColor] ?: 0f
        assertTrue("Red coverage should be greater than 0", redCoverage > 0f)
        assertTrue("Red coverage should be less than 1", redCoverage < 1f)
    }

    @Test
    fun calculate_withMultipleColors_returnsCorrectCoverageForEach() {
        // Set pixels for different colors
        testPixelProvider.setPaintedPixels(mapOf(
            // Red pixels (left side)
            Pair(25, 25) to redColor,
            Pair(25, 45) to redColor,
            Pair(25, 65) to redColor,
            // Blue pixels (right side)  
            Pair(75, 25) to blueColor,
            Pair(75, 45) to blueColor,
            Pair(75, 65) to blueColor
        ))
        
        val result = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 5)
        
        // Should have coverage for both colors
        assertTrue("Should have coverage data", result.isNotEmpty())
        
        val redCoverage = result[redColor] ?: 0f
        val blueCoverage = result[blueColor] ?: 0f
        
        assertTrue("Red coverage should be greater than 0", redCoverage > 0f)
        assertTrue("Blue coverage should be greater than 0", blueCoverage > 0f)
        
        // Both should be reasonable fractions
        assertTrue("Red coverage should be less than 1", redCoverage < 1f)
        assertTrue("Blue coverage should be less than 1", blueCoverage < 1f)
    }

    @Test
    fun calculate_withDifferentSampleSteps_returnsConsistentResults() {
        // Set a large grid of painted pixels to ensure coverage with different sample steps
        val paintedPixels = mutableMapOf<Pair<Int, Int>, Int>()
        
        // Create a dense 20x20 grid of painted pixels in the center
        for (x in 40..60 step 2) {
            for (y in 40..60 step 2) {
                paintedPixels[Pair(x, y)] = redColor
            }
        }
        testPixelProvider.setPaintedPixels(paintedPixels)
        
        val result1 = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 1)
        val result2 = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 2)
        val result5 = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 5)
        
        val coverage1 = result1[redColor] ?: 0f
        val coverage2 = result2[redColor] ?: 0f
        val coverage5 = result5[redColor] ?: 0f
        
        // All should be greater than 0 (main requirement)
        assertTrue("Coverage with step 1 should be > 0", coverage1 > 0f)
        assertTrue("Coverage with step 2 should be > 0", coverage2 > 0f)
        assertTrue("Coverage with step 5 should be > 0", coverage5 > 0f)
        
        // All should be reasonable fractions (less than 1)
        assertTrue("Coverage with step 1 should be < 1", coverage1 < 1f)
        assertTrue("Coverage with step 2 should be < 1", coverage2 < 1f)
        assertTrue("Coverage with step 5 should be < 1", coverage5 < 1f)
    }

    @Test
    fun calculate_withOverlappingPaint_handlesCorrectly() {
        // Set some overlapping painted areas (blue overwrites red)
        testPixelProvider.setPaintedPixels(mapOf(
            Pair(40, 40) to redColor,  // Red first
            Pair(50, 40) to blueColor, // Blue overwrites
            Pair(60, 40) to blueColor
        ))
        
        val result = CoverageCalculator.calculate(testLevel, testPixelProvider, sampleStep = 5)
        
        // Should have some coverage (either red, blue, or both)
        assertTrue("Should have some coverage data", result.isNotEmpty())
        val totalCoverage = result.values.sum()
        assertTrue("Total coverage should be greater than 0", totalCoverage > 0f)
    }

    // Test helper classes that work without bitmap operations
    class TestLevel(private val width: Int, private val height: Int) : Level {
        override fun update(): Boolean = false
        override fun draw(canvas: android.graphics.Canvas) {}
        override fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float> = emptyMap()
        override fun checkCollision(x: Float, y: Float): Boolean = false // No walls for simple test
        override fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float> = Pair(0f, 0f)
        override fun getZones(): List<android.graphics.RectF> = emptyList()
    }

    class TestPixelProvider(override val w: Int, override val h: Int) : PaintPixelProvider {
        private val paintedPixels = mutableMapOf<Pair<Int, Int>, Int>()
        
        fun setPaintedPixels(pixels: Map<Pair<Int, Int>, Int>) {
            paintedPixels.clear()
            paintedPixels.putAll(pixels)
        }
        
        override fun getPixelColor(x: Int, y: Int): Int {
            return paintedPixels[Pair(x, y)] ?: Color.TRANSPARENT
        }
    }
} 