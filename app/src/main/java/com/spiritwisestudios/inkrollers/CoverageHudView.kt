package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.AttributeSet
import android.view.View

/**
 * HUD component displaying paint coverage percentages for Coverage game mode.
 * 
 * Shows a horizontal bar across the top of the screen with left and right segments
 * representing each player's coverage percentage. Updated by GameView when coverage
 * statistics are recalculated.
 */
class CoverageHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var coverageData: Map<Int, Float> = emptyMap()
    private var leftPlayerColor: Int? = null
    private var rightPlayerColor: Int? = null

    private val paint = Paint().apply { style = Paint.Style.FILL }
    private val backgroundPaint = Paint().apply { color = Color.DKGRAY }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    /** Updates coverage data and triggers redraw. Called by GameView during Coverage mode. */
    fun updateCoverage(
        data: Map<Int, Float>,
        leftColor: Int? = this.leftPlayerColor,
        rightColor: Int? = this.rightPlayerColor
    ) {
        coverageData = data
        this.leftPlayerColor = leftColor
        this.rightPlayerColor = rightColor
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barHeight = height.toFloat()
        val centerY = (barHeight - (textPaint.ascent() + textPaint.descent())) / 2f

        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, backgroundPaint)

        val dataToUse = coverageData
        val currentLeftColor = leftPlayerColor
        val currentRightColor = rightPlayerColor

        val leftFraction = if (currentLeftColor != null) dataToUse[currentLeftColor] ?: 0f else 0f
        val rightFraction = if (currentRightColor != null) dataToUse[currentRightColor] ?: 0f else 0f

        val totalWidth = width.toFloat()

        // Left player coverage bar and percentage text
        if (currentLeftColor != null) {
            paint.color = currentLeftColor
            val barRight = totalWidth * leftFraction
            canvas.drawRect(0f, 0f, barRight, barHeight, paint)
            
            val pctTextLeft = "${(leftFraction * 100).toInt()}%"
            val leftTextX = if (barRight < textPaint.measureText(pctTextLeft) + 20f) {
                textPaint.measureText(pctTextLeft) / 2f + 10f
            } else {
                barRight / 2f
            }
            val effectiveLeftTextX = if (leftFraction == 0f) totalWidth / 4f else leftTextX
            canvas.drawText(pctTextLeft, effectiveLeftTextX, centerY, textPaint)
        }

        // Right player coverage bar and percentage text
        if (currentRightColor != null) {
            paint.color = currentRightColor
            val barLeft = totalWidth * (1 - rightFraction)
            canvas.drawRect(barLeft, 0f, totalWidth, barHeight, paint)

            val pctTextRight = "${(rightFraction * 100).toInt()}%"
            val rightTextX = if ((totalWidth - barLeft) < textPaint.measureText(pctTextRight) + 20f) {
                totalWidth - (textPaint.measureText(pctTextRight) / 2f + 10f)
            } else {
                barLeft + (totalWidth - barLeft) / 2f
            }
            val effectiveRightTextX = if (rightFraction == 0f) (totalWidth * 3f / 4f) else rightTextX
            canvas.drawText(pctTextRight, effectiveRightTextX, centerY, textPaint)
        }
    }
}
