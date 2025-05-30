package com.spiritwisestudios.inkrollers

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VirtualJoystickTest {

    private lateinit var joystick: VirtualJoystick
    private val baseX = 100f
    private val baseY = 200f
    private val tolerance = 0.01f // Tolerance for floating point comparisons

    @Before
    fun setUp() {
        joystick = VirtualJoystick()
    }

    @Test
    fun initialState_isNotActive() {
        assertFalse("Joystick should not be active initially", joystick.isActive)
    }

    @Test
    fun initialState_hasZeroDirection() {
        assertEquals("Initial directionX should be 0", 0f, joystick.directionX, tolerance)
        assertEquals("Initial directionY should be 0", 0f, joystick.directionY, tolerance)
    }

    @Test
    fun initialState_hasZeroMagnitude() {
        assertEquals("Initial magnitude should be 0", 0f, joystick.magnitude, tolerance)
    }

    @Test
    fun onDown_activatesJoystick() {
        joystick.onDown(baseX, baseY)
        assertTrue("Joystick should be active after onDown", joystick.isActive)
    }

    @Test
    fun onDown_setsBasePosition() {
        joystick.onDown(baseX, baseY)
        
        // We can't directly test the private base position, but we can test the behavior
        // by checking that moving from this position calculates direction correctly
        joystick.onMove(baseX + 50f, baseY)
        
        assertTrue("Direction should be positive X after moving right from base", 
                  joystick.directionX > 0)
        assertEquals("Direction Y should be 0 for horizontal movement", 
                    0f, joystick.directionY, tolerance)
    }

    @Test
    fun onDown_resetsDirection() {
        // First activate with some movement
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        
        // Verify there's some direction
        assertTrue("Should have some direction after move", 
                  joystick.magnitude > 0)
        
        // Touch down at a new position
        joystick.onDown(baseX + 100f, baseY + 100f)
        
        // Direction should be reset (magnitude should be 0 at the exact touch point)
        assertEquals("Direction should be reset on new touch", 
                    0f, joystick.magnitude, tolerance)
    }

    @Test
    fun onUp_deactivatesJoystick() {
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        joystick.onUp()
        
        assertFalse("Joystick should not be active after onUp", joystick.isActive)
    }

    @Test
    fun onUp_resetsDirection() {
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        joystick.onUp()
        
        assertEquals("DirectionX should be reset to 0 after onUp", 
                    0f, joystick.directionX, tolerance)
        assertEquals("DirectionY should be reset to 0 after onUp", 
                    0f, joystick.directionY, tolerance)
    }

    @Test
    fun onUp_resetsMagnitude() {
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        joystick.onUp()
        
        assertEquals("Magnitude should be reset to 0 after onUp", 
                    0f, joystick.magnitude, tolerance)
    }

    @Test
    fun onMove_whenNotActive_doesNothing() {
        // Don't call onDown first
        joystick.onMove(baseX + 50f, baseY + 50f)
        
        assertEquals("DirectionX should remain 0 when not active", 
                    0f, joystick.directionX, tolerance)
        assertEquals("DirectionY should remain 0 when not active", 
                    0f, joystick.directionY, tolerance)
        assertEquals("Magnitude should remain 0 when not active", 
                    0f, joystick.magnitude, tolerance)
    }

    @Test
    fun horizontalMovement_calculatesCorrectDirection() {
        joystick.onDown(baseX, baseY)
        
        // Move right
        joystick.onMove(baseX + 50f, baseY)
        assertTrue("Right movement should have positive directionX", 
                  joystick.directionX > 0)
        assertEquals("Horizontal movement should have zero directionY", 
                    0f, joystick.directionY, tolerance)
        
        // Move left
        joystick.onMove(baseX - 50f, baseY)
        assertTrue("Left movement should have negative directionX", 
                  joystick.directionX < 0)
        assertEquals("Horizontal movement should have zero directionY", 
                    0f, joystick.directionY, tolerance)
    }

    @Test
    fun verticalMovement_calculatesCorrectDirection() {
        joystick.onDown(baseX, baseY)
        
        // Move down
        joystick.onMove(baseX, baseY + 50f)
        assertEquals("Vertical movement should have zero directionX", 
                    0f, joystick.directionX, tolerance)
        assertTrue("Down movement should have positive directionY", 
                  joystick.directionY > 0)
        
        // Move up
        joystick.onMove(baseX, baseY - 50f)
        assertEquals("Vertical movement should have zero directionX", 
                    0f, joystick.directionX, tolerance)
        assertTrue("Up movement should have negative directionY", 
                  joystick.directionY < 0)
    }

    @Test
    fun diagonalMovement_calculatesCorrectDirection() {
        joystick.onDown(baseX, baseY)
        
        // Move diagonally down-right
        joystick.onMove(baseX + 50f, baseY + 50f)
        
        assertTrue("Diagonal down-right should have positive directionX", 
                  joystick.directionX > 0)
        assertTrue("Diagonal down-right should have positive directionY", 
                  joystick.directionY > 0)
        
        // For 45-degree diagonal, X and Y components should be approximately equal
        val directionDifference = abs(abs(joystick.directionX) - abs(joystick.directionY))
        assertTrue("45-degree diagonal should have similar X and Y components", 
                  directionDifference < 0.1f)
    }

    @Test
    fun shortDistance_hasCorrectMagnitude() {
        joystick.onDown(baseX, baseY)
        
        // Move a short distance (within the joystick base circle)
        val shortDistance = 25f
        joystick.onMove(baseX + shortDistance, baseY)
        
        assertTrue("Short movement should have magnitude less than 1", 
                  joystick.magnitude < 1.0f)
        assertTrue("Short movement should have positive magnitude", 
                  joystick.magnitude > 0.0f)
    }

    @Test
    fun longDistance_clampsToMaxMagnitude() {
        joystick.onDown(baseX, baseY)
        
        // Move a very long distance (beyond the joystick base circle)
        val longDistance = 200f
        joystick.onMove(baseX + longDistance, baseY)
        
        assertEquals("Long movement should clamp magnitude to 1.0", 
                    1.0f, joystick.magnitude, tolerance)
    }

    @Test
    fun magnitudeCalculation_isAccurate() {
        joystick.onDown(baseX, baseY)
        
        // Move a known distance
        val moveDistance = 30f
        joystick.onMove(baseX + moveDistance, baseY)
        
        // Expected magnitude based on the assumption that maxDisplacement is 50f (baseRadius - handleRadius)
        // This is an approximation since we don't have direct access to maxDisplacement
        val expectedMagnitude = moveDistance / 50f // Assuming maxDisplacement = 50f
        
        assertTrue("Magnitude should be approximately correct", 
                  abs(joystick.magnitude - expectedMagnitude) < 0.1f)
    }

    @Test
    fun directionVector_isNormalized() {
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        
        // Direction vector length should not exceed 1.0 (approximately)
        val directionLength = sqrt(joystick.directionX * joystick.directionX + 
                                  joystick.directionY * joystick.directionY)
        
        assertTrue("Direction vector should not exceed length 1.0", 
                  directionLength <= 1.0f + tolerance)
    }

    @Test
    fun multipleMovements_updateDirectionCorrectly() {
        joystick.onDown(baseX, baseY)
        
        // First movement
        joystick.onMove(baseX + 30f, baseY)
        val firstDirectionX = joystick.directionX
        val firstMagnitude = joystick.magnitude
        
        // Second movement
        joystick.onMove(baseX + 60f, baseY)
        val secondDirectionX = joystick.directionX
        val secondMagnitude = joystick.magnitude
        
        assertTrue("Second movement should have larger magnitude", 
                  secondMagnitude > firstMagnitude)
        assertTrue("Both movements should have positive X direction", 
                  firstDirectionX > 0 && secondDirectionX > 0)
    }

    @Test
    fun zeroMovement_hasZeroMagnitude() {
        joystick.onDown(baseX, baseY)
        
        // Move to the exact same position
        joystick.onMove(baseX, baseY)
        
        assertEquals("Zero movement should have zero magnitude", 
                    0f, joystick.magnitude, tolerance)
        assertEquals("Zero movement should have zero directionX", 
                    0f, joystick.directionX, tolerance)
        assertEquals("Zero movement should have zero directionY", 
                    0f, joystick.directionY, tolerance)
    }

    @Test
    fun boundaryConditions_handleCorrectly() {
        joystick.onDown(baseX, baseY)
        
        // Test extreme coordinates
        joystick.onMove(Float.MAX_VALUE, Float.MAX_VALUE)
        
        // Should not crash and should clamp to max magnitude
        assertEquals("Extreme coordinates should clamp to max magnitude", 
                    1.0f, joystick.magnitude, tolerance)
        
        // Test negative coordinates
        joystick.onMove(-1000f, -1000f)
        
        // Should not crash and should still have max magnitude
        assertEquals("Negative extreme coordinates should clamp to max magnitude", 
                    1.0f, joystick.magnitude, tolerance)
    }

    @Test
    fun consecutiveDownEvents_resetProperly() {
        // First touch sequence
        joystick.onDown(baseX, baseY)
        joystick.onMove(baseX + 50f, baseY + 50f)
        
        val firstMagnitude = joystick.magnitude
        assertTrue("First sequence should have some magnitude", firstMagnitude > 0)
        
        // Second touch sequence at different location
        joystick.onDown(baseX + 100f, baseY + 100f)
        
        // Should reset to zero magnitude at the new base position
        assertEquals("New touch should reset magnitude to zero", 
                    0f, joystick.magnitude, tolerance)
        
        // Moving from new base should work correctly
        joystick.onMove(baseX + 150f, baseY + 100f)
        assertTrue("Movement from new base should generate magnitude", 
                  joystick.magnitude > 0)
    }

    @Test
    fun smallMovements_handlePrecisionCorrectly() {
        joystick.onDown(baseX, baseY)
        
        // Very small movement
        joystick.onMove(baseX + 0.1f, baseY + 0.1f)
        
        assertTrue("Very small movement should have very small but positive magnitude", 
                  joystick.magnitude > 0 && joystick.magnitude < 0.01f)
    }

    @Test
    fun circularMovement_maintainsMagnitude() {
        joystick.onDown(baseX, baseY)
        
        val radius = 40f // Within the joystick limits
        
        // Test several points around a circle
        val angles = listOf(0f, 90f, 180f, 270f)
        val magnitudes = mutableListOf<Float>()
        
        for (angle in angles) {
            val radians = Math.toRadians(angle.toDouble())
            val x = baseX + (radius * kotlin.math.cos(radians)).toFloat()
            val y = baseY + (radius * kotlin.math.sin(radians)).toFloat()
            
            joystick.onMove(x, y)
            magnitudes.add(joystick.magnitude)
        }
        
        // All magnitudes should be approximately the same
        val maxMagnitude = magnitudes.maxOrNull() ?: 0f
        val minMagnitude = magnitudes.minOrNull() ?: 0f
        
        assertTrue("Circular movement should maintain consistent magnitude", 
                  (maxMagnitude - minMagnitude) < 0.1f)
    }
} 