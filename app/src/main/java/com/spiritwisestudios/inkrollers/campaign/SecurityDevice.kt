package com.spiritwisestudios.inkrollers.campaign

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.*

/**
 * SecurityDevice class for environmental puzzles in campaign mode.
 * Devices can be disabled by interacting with control panels using correct color frequency.
 */
class SecurityDevice(
    private val deviceData: SecurityDeviceData
) {
    companion object {
        private const val TAG = "SecurityDevice"
        private const val DEVICE_SIZE = 60f
        private const val CONTROL_PANEL_SIZE = 40f
        private const val INTERACTION_RADIUS = 50f
        private const val LASER_WIDTH = 8f
        private const val FORCE_FIELD_ALPHA = 100
    }
    
    // Device state
    private var isDisabled: Boolean = false
    private var animationPhase: Float = 0f
    
    // Visual elements
    private val devicePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val controlPanelPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val laserPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.RED
    }
    
    private val forceFieldPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        alpha = FORCE_FIELD_ALPHA
        color = Color.CYAN
    }
    
    /**
     * Update device state and animations
     */
    fun update(deltaTime: Float) {
        // Update animation phase for visual effects
        animationPhase += deltaTime * 2f
        if (animationPhase > 2 * PI) {
            animationPhase -= (2 * PI).toFloat()
        }
    }
    
    /**
     * Interact with the control panel using a color frequency
     */
    fun interactWithControlPanel(frequency: ColorFrequency, playerX: Float, playerY: Float): Boolean {
        if (isDisabled) return false
        
        // Check if player is close enough to control panel
        val controlPanelX = deviceData.controlPanelPosition.first
        val controlPanelY = deviceData.controlPanelPosition.second
        val distance = sqrt((playerX - controlPanelX).pow(2) + (playerY - controlPanelY).pow(2))
        
        if (distance > INTERACTION_RADIUS) return false
        
        // Check if correct frequency is used
        if (frequency == deviceData.requiredFrequency) {
            isDisabled = true
            Log.d(TAG, "Security device disabled with frequency: $frequency")
            return true
        }
        
        Log.d(TAG, "Wrong frequency used: $frequency, required: ${deviceData.requiredFrequency}")
        return false
    }
    
    /**
     * Check if device is blocking a path/area
     */
    fun isBlockingPath(x: Float, y: Float): Boolean {
        if (isDisabled) return false
        
        return when (deviceData.deviceType) {
            DeviceType.LASER_GRID -> isInLaserPath(x, y)
            DeviceType.AUTO_TURRET -> isInTurretRange(x, y)
            DeviceType.FORCE_FIELD -> isInForceField(x, y)
        }
    }
    
    /**
     * Get device bounds for collision detection
     */
    fun getDeviceBounds(): RectF {
        return RectF(
            deviceData.x - DEVICE_SIZE / 2,
            deviceData.y - DEVICE_SIZE / 2,
            deviceData.x + DEVICE_SIZE / 2,
            deviceData.y + DEVICE_SIZE / 2
        )
    }
    
    /**
     * Get control panel position
     */
    fun getControlPanelPosition(): Pair<Float, Float> = deviceData.controlPanelPosition
    
    /**
     * Check if device is disabled
     */
    fun isDisabled(): Boolean = this.isDisabled
    
    /**
     * Get required frequency for this device
     */
    fun getRequiredFrequency(): ColorFrequency = deviceData.requiredFrequency
    
    /**
     * Draw the security device and control panel
     */
    fun draw(canvas: Canvas) {
        drawDevice(canvas)
        drawControlPanel(canvas)
    }
    
    /**
     * Draw the main security device
     */
    private fun drawDevice(canvas: Canvas) {
        val alpha = if (isDisabled) 50 else 255
        
        when (deviceData.deviceType) {
            DeviceType.LASER_GRID -> drawLaserGrid(canvas, alpha)
            DeviceType.AUTO_TURRET -> drawAutoTurret(canvas, alpha)
            DeviceType.FORCE_FIELD -> drawForceField(canvas, alpha)
        }
    }
    
    /**
     * Draw laser grid device
     */
    private fun drawLaserGrid(canvas: Canvas, alpha: Int) {
        // Draw device base
        devicePaint.color = Color.GRAY
        devicePaint.alpha = alpha
        canvas.drawRect(
            deviceData.x - DEVICE_SIZE / 2,
            deviceData.y - DEVICE_SIZE / 2,
            deviceData.x + DEVICE_SIZE / 2,
            deviceData.y + DEVICE_SIZE / 2,
            devicePaint
        )
        
        // Draw laser beams if active
        if (!isDisabled) {
            laserPaint.alpha = (sin(animationPhase) * 50 + 200).toInt()
            
            // Horizontal laser beam
            canvas.drawRect(
                deviceData.x - 100f,
                deviceData.y - LASER_WIDTH / 2,
                deviceData.x + 100f,
                deviceData.y + LASER_WIDTH / 2,
                laserPaint
            )
            
            // Vertical laser beam
            canvas.drawRect(
                deviceData.x - LASER_WIDTH / 2,
                deviceData.y - 100f,
                deviceData.x + LASER_WIDTH / 2,
                deviceData.y + 100f,
                laserPaint
            )
        }
    }
    
    /**
     * Draw auto turret device
     */
    private fun drawAutoTurret(canvas: Canvas, alpha: Int) {
        // Draw base
        devicePaint.color = Color.DKGRAY
        devicePaint.alpha = alpha
        canvas.drawCircle(deviceData.x, deviceData.y, DEVICE_SIZE / 2, devicePaint)
        
        // Draw turret barrel
        if (!isDisabled) {
            val barrelLength = DEVICE_SIZE / 2 + 20f
            val barrelAngle = animationPhase
            val barrelEndX = deviceData.x + cos(barrelAngle) * barrelLength
            val barrelEndY = deviceData.y + sin(barrelAngle) * barrelLength
            
            val barrelPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 8f
                this.alpha = alpha
            }
            
            canvas.drawLine(deviceData.x, deviceData.y, barrelEndX, barrelEndY, barrelPaint)
        }
        
        // Draw status light
        val lightPaint = Paint().apply {
            color = if (isDisabled) Color.GREEN else Color.RED
            this.alpha = alpha
        }
        canvas.drawCircle(deviceData.x, deviceData.y - 15f, 5f, lightPaint)
    }
    
    /**
     * Draw force field device
     */
    private fun drawForceField(canvas: Canvas, alpha: Int) {
        // Draw generator
        devicePaint.color = Color.BLUE
        devicePaint.alpha = alpha
        canvas.drawCircle(deviceData.x, deviceData.y, DEVICE_SIZE / 2, devicePaint)
        
        // Draw force field effect if active
        if (!isDisabled) {
            forceFieldPaint.alpha = (sin(animationPhase) * 30 + 70).toInt()
            
            // Draw expanding circles for force field effect
            val radius1 = 80f + sin(animationPhase) * 10f
            val radius2 = 100f + cos(animationPhase * 1.3f) * 15f
            
            canvas.drawCircle(deviceData.x, deviceData.y, radius1, forceFieldPaint)
            canvas.drawCircle(deviceData.x, deviceData.y, radius2, forceFieldPaint)
        }
    }
    
    /**
     * Draw control panel
     */
    private fun drawControlPanel(canvas: Canvas) {
        val controlX = deviceData.controlPanelPosition.first
        val controlY = deviceData.controlPanelPosition.second
        
        // Panel base
        controlPanelPaint.color = Color.LTGRAY
        canvas.drawRect(
            controlX - CONTROL_PANEL_SIZE / 2,
            controlY - CONTROL_PANEL_SIZE / 2,
            controlX + CONTROL_PANEL_SIZE / 2,
            controlY + CONTROL_PANEL_SIZE / 2,
            controlPanelPaint
        )
        
        // Required frequency indicator
        val frequencyColor = when (deviceData.requiredFrequency) {
            ColorFrequency.RED -> Color.RED
            ColorFrequency.BLUE -> Color.BLUE
            ColorFrequency.GREEN -> Color.GREEN
            ColorFrequency.YELLOW -> Color.YELLOW
        }
        
        controlPanelPaint.color = frequencyColor
        canvas.drawCircle(controlX, controlY, CONTROL_PANEL_SIZE / 3, controlPanelPaint)
        
        // Status indicator
        val statusColor = if (isDisabled) Color.GREEN else Color.RED
        controlPanelPaint.color = statusColor
        canvas.drawCircle(controlX, controlY - 15f, 5f, controlPanelPaint)
    }
    
    /**
     * Check if position is in laser path
     */
    private fun isInLaserPath(x: Float, y: Float): Boolean {
        // Check horizontal laser
        if (abs(y - deviceData.y) <= LASER_WIDTH / 2 && 
            x >= deviceData.x - 100f && x <= deviceData.x + 100f) {
            return true
        }
        
        // Check vertical laser
        if (abs(x - deviceData.x) <= LASER_WIDTH / 2 && 
            y >= deviceData.y - 100f && y <= deviceData.y + 100f) {
            return true
        }
        
        return false
    }
    
    /**
     * Check if position is in turret range
     */
    private fun isInTurretRange(x: Float, y: Float): Boolean {
        val distance = sqrt((x - deviceData.x).pow(2) + (y - deviceData.y).pow(2))
        return distance <= 120f // Turret range
    }
    
    /**
     * Check if position is in force field
     */
    private fun isInForceField(x: Float, y: Float): Boolean {
        val distance = sqrt((x - deviceData.x).pow(2) + (y - deviceData.y).pow(2))
        return distance <= 100f // Force field range
    }
} 