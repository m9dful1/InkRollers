package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * HUD overlay to display zone ownership for Zones game mode.
 * Shows a mini-map grid where each cell represents a zone.
 */
class ZoneHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var zoneOwnership: Map<Int, Int?> = emptyMap()
    private var leftPlayerColor: Int? = null
    private var rightPlayerColor: Int? = null

    private val zonePaint = Paint().apply { 
        style = Paint.Style.FILL 
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply { 
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val backgroundPaint = Paint().apply { 
        color = Color.DKGRAY 
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Zone grid layout (2 rows, 3 columns as defined in MazeLevel)
    private val zoneRows = 2
    private val zoneCols = 3
    private val totalZones = zoneRows * zoneCols

    /**
     * Update zone ownership and redraw.
     * @param ownership Map of zone index to owner color (null if uncontrolled)
     * @param leftColor Color value for the player on the left side
     * @param rightColor Color value for the player on the right side
     */
    fun updateZones(
        ownership: Map<Int, Int?>,
        leftColor: Int? = this.leftPlayerColor,
        rightColor: Int? = this.rightPlayerColor
    ) {
        zoneOwnership = ownership
        this.leftPlayerColor = leftColor
        this.rightPlayerColor = rightColor
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (zoneOwnership.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        // Draw background
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, backgroundPaint)
        
        // Calculate zone cell dimensions
        val cellWidth = viewWidth / zoneCols
        val cellHeight = viewHeight / zoneRows
        
        // Draw each zone
        for (zoneIndex in 0 until totalZones) {
            val row = zoneIndex / zoneCols
            val col = zoneIndex % zoneCols
            
            val left = col * cellWidth
            val top = row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            
            val zoneRect = RectF(left + 1, top + 1, right - 1, bottom - 1)
            
            // Get zone owner color
            val ownerColor = zoneOwnership[zoneIndex]
            
            if (ownerColor != null) {
                zonePaint.color = ownerColor
                canvas.drawRect(zoneRect, zonePaint)
            }
            
            // Draw zone border
            canvas.drawRect(zoneRect, borderPaint)
            
            // Draw zone number
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2 + textPaint.textSize / 3
            canvas.drawText((zoneIndex + 1).toString(), centerX, centerY, textPaint)
        }
        
        // Draw player score summary at the bottom
        drawScoreSummary(canvas, viewWidth, viewHeight)
    }
    
    private fun drawScoreSummary(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val leftColor = leftPlayerColor
        val rightColor = rightPlayerColor
        
        if (leftColor == null || rightColor == null) return
        
        // Count zones controlled by each player
        var leftZones = 0
        var rightZones = 0
        var neutralZones = 0
        
        for (ownerColor in zoneOwnership.values) {
            when (ownerColor) {
                leftColor -> leftZones++
                rightColor -> rightZones++
                else -> neutralZones++
            }
        }
        
        // Draw score text at the bottom
        val scoreY = viewHeight - 8f
        val leftText = "P1: $leftZones"
        val rightText = "P2: $rightZones"
        
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = leftColor
        canvas.drawText(leftText, 8f, scoreY, textPaint)
        
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = rightColor
        canvas.drawText(rightText, viewWidth - 8f, scoreY, textPaint)
        
        // Reset text paint
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
    }
} 