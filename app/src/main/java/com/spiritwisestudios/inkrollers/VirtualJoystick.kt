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


    fun onDown(x: Float, y: Float) {
        baseX = x
        baseY = y
        handleX = x
        handleY = y
        isActive = true
        updateDirection(x, y) // Reset direction on new touch
    }

    fun onMove(x: Float, y: Float) {
        if (!isActive) return
        updateDirection(x, y)
    }

    fun onUp() {
        isActive = false
        directionX = 0f
        directionY = 0f
        magnitude = 0f
    }

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
            magnitude = 1.0f // Max magnitude when clamped
        } else {
            handleX = touchX
            handleY = touchY
            magnitude = distance / maxDisplacement // Scale magnitude
        }

        // Update normalized direction vector based on actual displacement
        val actualDx = handleX - baseX
        val actualDy = handleY - baseY
        val actualDistance = sqrt(actualDx * actualDx + actualDy * actualDy) // Could be slightly different due to clamping

        if (actualDistance > 0) {
             directionX = actualDx / maxDisplacement // Normalize based on max possible displacement
             directionY = actualDy / maxDisplacement // Normalize based on max possible displacement
        } else {
             directionX = 0f
             directionY = 0f
        }

        // Added Log
        Log.d("VirtualJoystick", "updateDirection: dirX=$directionX, dirY=$directionY, mag=$magnitude") 

    }


    fun draw(canvas: Canvas) {
        if (!isActive) return
        // Draw base circle
        canvas.drawCircle(baseX, baseY, baseRadius, basePaint)
        // Draw handle circle
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint)
    }
} 