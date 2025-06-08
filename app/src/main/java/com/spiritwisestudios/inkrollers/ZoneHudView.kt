package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * HUD component displaying zone control status for Zones game mode.
 * 
 * Shows a 2x3 grid mini-map where each cell represents a zone in the maze.
 * Zones are colored by their controlling player, with numbered labels and
 * a score summary showing zone counts for each player.
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

    /** Updates zone ownership data and triggers redraw. Called by GameView during Zones mode. */
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
        
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, backgroundPaint)
        
        val cellWidth = viewWidth / zoneCols
        val cellHeight = viewHeight / zoneRows
        
        for (zoneIndex in 0 until totalZones) {
            val row = zoneIndex / zoneCols
            val col = zoneIndex % zoneCols
            
            val left = col * cellWidth
            val top = row * cellHeight
            val right = left + cellWidth
            val bottom = top + cellHeight
            
            val zoneRect = RectF(left + 1, top + 1, right - 1, bottom - 1)
            
            val ownerColor = zoneOwnership[zoneIndex]
            
            if (ownerColor != null) {
                zonePaint.color = ownerColor
                canvas.drawRect(zoneRect, zonePaint)
            }
            
            canvas.drawRect(zoneRect, borderPaint)
            
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2 + textPaint.textSize / 3
            canvas.drawText((zoneIndex + 1).toString(), centerX, centerY, textPaint)
        }
        
        drawScoreSummary(canvas, viewWidth, viewHeight)
    }
    
    /** Renders player zone counts at the bottom of the HUD. */
    private fun drawScoreSummary(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val leftColor = leftPlayerColor
        val rightColor = rightPlayerColor
        
        if (leftColor == null || rightColor == null) return
        
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
        
        val scoreY = viewHeight - 8f
        val leftText = "P1: $leftZones"
        val rightText = "P2: $rightZones"
        
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = leftColor
        canvas.drawText(leftText, 8f, scoreY, textPaint)
        
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = rightColor
        canvas.drawText(rightText, viewWidth - 8f, scoreY, textPaint)
        
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
    }
} 