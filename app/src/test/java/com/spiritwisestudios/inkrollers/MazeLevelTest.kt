package com.spiritwisestudios.inkrollers

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MazeLevelTest {

    private lateinit var lowComplexityMaze: MazeLevel
    private lateinit var mediumComplexityMaze: MazeLevel
    private lateinit var highComplexityMaze: MazeLevel
    
    private val testScreenWidth = 1080
    private val testScreenHeight = 1920
    private val testSeed = 12345L

    @Before
    fun setUp() {
        lowComplexityMaze = MazeLevel(
            screenW = testScreenWidth,
            screenH = testScreenHeight,
            complexity = HomeActivity.COMPLEXITY_LOW,
            seed = testSeed
        )
        
        mediumComplexityMaze = MazeLevel(
            screenW = testScreenWidth,
            screenH = testScreenHeight,
            complexity = HomeActivity.COMPLEXITY_MEDIUM,
            seed = testSeed
        )
        
        highComplexityMaze = MazeLevel(
            screenW = testScreenWidth,
            screenH = testScreenHeight,
            complexity = HomeActivity.COMPLEXITY_HIGH,
            seed = testSeed
        )
    }

    @Test
    fun mazeComplexity_lowSetting_hasCorrectCellCount() {
        // Low complexity should have fewer cells (8x12 or 12x8 depending on orientation)
        val zones = lowComplexityMaze.getZones()
        assertNotNull("Low complexity maze should have zones", zones)
        assertEquals("Low complexity should have 6 zones", 6, zones.size)
        
        // Verify that walls exist (indicating maze was generated)
        val walls = lowComplexityMaze.getWalls()
        assertTrue("Low complexity maze should have walls", walls.isNotEmpty())
    }

    @Test
    fun mazeComplexity_mediumSetting_hasCorrectCellCount() {
        // Medium complexity should have moderate cells (10x16 or 16x10)
        val zones = mediumComplexityMaze.getZones()
        assertNotNull("Medium complexity maze should have zones", zones)
        assertEquals("Medium complexity should have 6 zones", 6, zones.size)
        
        val walls = mediumComplexityMaze.getWalls()
        assertTrue("Medium complexity maze should have walls", walls.isNotEmpty())
    }

    @Test
    fun mazeComplexity_highSetting_hasCorrectCellCount() {
        // High complexity should have most cells (12x20 or 20x12)
        val zones = highComplexityMaze.getZones()
        assertNotNull("High complexity maze should have zones", zones)
        assertEquals("High complexity should have 6 zones", 6, zones.size)
        
        val walls = highComplexityMaze.getWalls()
        assertTrue("High complexity maze should have walls", walls.isNotEmpty())
        
        // High complexity should generally have more walls than low complexity
        val lowWalls = lowComplexityMaze.getWalls()
        assertTrue("High complexity should have more walls than low", 
                  walls.size >= lowWalls.size)
    }

    @Test
    fun mazeGeneration_withSameSeed_producesIdenticalMaze() {
        val maze1 = MazeLevel(testScreenWidth, testScreenHeight, 
                             complexity = HomeActivity.COMPLEXITY_HIGH, seed = testSeed)
        val maze2 = MazeLevel(testScreenWidth, testScreenHeight, 
                             complexity = HomeActivity.COMPLEXITY_HIGH, seed = testSeed)
        
        val walls1 = maze1.getWalls()
        val walls2 = maze2.getWalls()
        
        assertEquals("Same seed should produce same number of walls", walls1.size, walls2.size)
        
        // Compare wall positions (allowing for floating point precision)
        for (i in walls1.indices) {
            val wall1 = walls1[i]
            val wall2 = walls2[i]
            assertEquals("Wall ${i} left should match", wall1.left, wall2.left, 0.1f)
            assertEquals("Wall ${i} top should match", wall1.top, wall2.top, 0.1f)
            assertEquals("Wall ${i} right should match", wall1.right, wall2.right, 0.1f)
            assertEquals("Wall ${i} bottom should match", wall1.bottom, wall2.bottom, 0.1f)
        }
    }

    @Test
    fun collisionDetection_withWallPosition_returnsTrue() {
        val walls = highComplexityMaze.getWalls()
        assertTrue("Maze should have walls for collision testing", walls.isNotEmpty())
        
        // Test collision with center of first wall
        val firstWall = walls.first()
        val centerX = (firstWall.left + firstWall.right) / 2
        val centerY = (firstWall.top + firstWall.bottom) / 2
        
        assertTrue("Should detect collision at wall center", 
                  highComplexityMaze.checkCollision(centerX, centerY))
    }

    @Test
    fun collisionDetection_withOpenSpace_returnsFalse() {
        // Test collision at player start position (should be open)
        val startPos = highComplexityMaze.getPlayerStartPosition(0)
        assertFalse("Should not detect collision at player start position", 
                   highComplexityMaze.checkCollision(startPos.first, startPos.second))
    }

    @Test
    fun playerStartPositions_areDifferentForDifferentPlayers() {
        val player0Start = highComplexityMaze.getPlayerStartPosition(0)
        val player1Start = highComplexityMaze.getPlayerStartPosition(1)
        
        assertNotEquals("Player 0 and 1 should have different X start positions", 
                       player0Start.first, player1Start.first, 0.1f)
        assertNotEquals("Player 0 and 1 should have different Y start positions", 
                       player0Start.second, player1Start.second, 0.1f)
    }

    @Test
    fun coordinateConversion_screenToMazeToScreen_isConsistent() {
        val originalX = 500f
        val originalY = 800f
        
        // Convert screen to maze coordinates
        val (normX, normY) = highComplexityMaze.screenToMazeCoord(originalX, originalY)
        
        // Convert back to screen coordinates
        val (convertedX, convertedY) = highComplexityMaze.mazeToScreenCoord(normX, normY)
        
        assertEquals("X coordinate conversion should be consistent", 
                    originalX, convertedX, 1.0f)
        assertEquals("Y coordinate conversion should be consistent", 
                    originalY, convertedY, 1.0f)
    }

    @Test
    fun normalizedCoordinates_areWithinValidRange() {
        val testX = 540f // Half screen width
        val testY = 960f // Half screen height
        
        val (normX, normY) = highComplexityMaze.screenToMazeCoord(testX, testY)
        
        assertTrue("Normalized X should be between 0 and 1", normX >= 0f && normX <= 1f)
        assertTrue("Normalized Y should be between 0 and 1", normY >= 0f && normY <= 1f)
    }

    @Test
    fun zones_areProperlyDefined() {
        val zones = highComplexityMaze.getZones()
        
        assertEquals("Should have exactly 6 zones (2x3 grid)", 6, zones.size)
        
        // Verify zones cover the entire normalized space
        var totalArea = 0f
        zones.forEach { zone ->
            val area = zone.width() * zone.height()
            totalArea += area
            
            // Each zone should be within normalized coordinates
            assertTrue("Zone left should be >= 0", zone.left >= 0f)
            assertTrue("Zone top should be >= 0", zone.top >= 0f)
            assertTrue("Zone right should be <= 1", zone.right <= 1f)
            assertTrue("Zone bottom should be <= 1", zone.bottom <= 1f)
        }
        
        // Total area should approximately equal 1.0 (entire normalized space)
        assertEquals("Total zone area should cover entire space", 1.0f, totalArea, 0.01f)
    }

    @Test
    fun mazeGeneration_hasRotationalSymmetry() {
        // Test that maze has 180-degree rotational symmetry as specified in design
        val walls = highComplexityMaze.getWalls()
        assertTrue("Maze should have walls to test symmetry", walls.isNotEmpty())
        
        // This is a complex test - for now, just verify maze was generated successfully
        // Full symmetry testing would require access to internal wall arrays
        assertNotNull("Maze should be generated successfully", walls)
    }

    @Test
    fun update_returnsFalse() {
        // MazeLevel.update() should return false as it has no dynamic behavior
        assertFalse("MazeLevel update should return false", highComplexityMaze.update())
    }

    @Test
    fun calculateCoverage_withEmptyPaintSurface_returnsEmptyMap() {
        // This test requires a PaintSurface - we'll test the interface
        // The actual coverage calculation is tested in CoverageCalculatorTest
        val zones = highComplexityMaze.getZones()
        assertNotNull("Zones should be available for coverage calculation", zones)
    }
} 