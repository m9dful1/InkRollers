package com.spiritwisestudios.inkrollers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-screen virtual joystick for player movement control.
 * 
 * Provides normalized direction and magnitude values from touch input,
 * which are used by Player objects for movement calculations. The joystick
 * appears dynamically where the user first touches and constrains movement
 * within a circular boundary.
 */
class VirtualJoystick {
    private val basePaint = Paint().apply { color = Color.argb(100, 128, 128, 128); style = Paint.Style.FILL }
    private val handlePaint = Paint().apply { color = Color.argb(150, 80, 80, 80); style = Paint.Style.FILL }

    private var baseRadius = 100f
    private var handleRadius = 50f
    private var maxDisplacement = baseRadius - handleRadius

    var isActive = false
        private set

    private var baseX = 0f
    private var baseY = 0f
    private var handleX = 0f
    private var handleY = 0f

    // Normalized direction vector (-1.0 to 1.0 for each axis)
    var directionX = 0f
        private set
    var directionY = 0f
        private set
    var magnitude = 0f // 0.0 to 1.0
        private set

    /** Initializes joystick at touch position and activates it. */
    fun onDown(x: Float, y: Float) {
        baseX = x
        baseY = y
        handleX = x
        handleY = y
        isActive = true
        updateDirection(x, y)
    }

    /** Updates joystick handle position and direction based on touch movement. */
    fun onMove(x: Float, y: Float) {
        if (!isActive) return
        updateDirection(x, y)
    }

    /** Deactivates joystick and resets all direction values to zero. */
    fun onUp() {
        isActive = false
        directionX = 0f
        directionY = 0f
        magnitude = 0f
    }

    /** Calculates normalized direction and magnitude from current touch position. */
    private fun updateDirection(touchX: Float, touchY: Float) {
        val dx = touchX - baseX
        val dy = touchY - baseY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance == 0f) {
            handleX = baseX
            handleY = baseY
            directionX = 0f
            directionY = 0f
            magnitude = 0f
            return
        }

        if (distance > maxDisplacement) {
            // Clamp handle position to edge of base circle
            val angle = atan2(dy, dx)
            handleX = baseX + cos(angle) * maxDisplacement
            handleY = baseY + sin(angle) * maxDisplacement
            magnitude = 1.0f
        } else {
            handleX = touchX
            handleY = touchY
            magnitude = distance / maxDisplacement
        }

        // Calculate normalized direction vector
        val actualDx = handleX - baseX
        val actualDy = handleY - baseY
        val actualDistance = sqrt(actualDx * actualDx + actualDy * actualDy)

        if (actualDistance > 0) {
             directionX = actualDx / maxDisplacement
             directionY = actualDy / maxDisplacement
        } else {
             directionX = 0f
             directionY = 0f
        }
    }

    /** Renders the joystick base and handle when active. Called by GameView. */
    fun draw(canvas: Canvas) {
        if (!isActive) return
        canvas.drawCircle(baseX, baseY, baseRadius, basePaint)
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint)
    }
} 