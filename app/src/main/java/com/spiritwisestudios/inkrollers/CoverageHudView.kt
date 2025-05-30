package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.AttributeSet
import android.view.View

/**
 * HUD overlay to display coverage percentages for each color.
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
    private val backgroundPaint = Paint().apply { color = Color.DKGRAY } // Default Dark Gray
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f // Slightly smaller to fit "0%" better if bars are tiny
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    init {
        // Example: Set background from XML attribute or default
        // val a = context.theme.obtainStyledAttributes(attrs, R.styleable.CoverageHudView, 0, 0)
        // try {
        //     backgroundPaint.color = a.getColor(R.styleable.CoverageHudView_hudBackgroundColor, Color.DKGRAY)
        // } finally {
        //     a.recycle()
        // }
    }

    /**
     * Update coverage percentages and redraw.
     * @param data Map of color (Int) to fraction (0-1)
     * @param leftColor Color value for the player on the left side of the bar
     * @param rightColor Color value for the player on the right side of the bar
     */
    fun updateCoverage(
        data: Map<Int, Float>,
        leftColor: Int? = this.leftPlayerColor,
        rightColor: Int? = this.rightPlayerColor
    ) {
        coverageData = data
        this.leftPlayerColor = leftColor
        this.rightPlayerColor = rightColor
        postInvalidate() // Request a redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // No early return if data is empty, always draw background and potentially "0%"

        val barHeight = height.toFloat()
        val centerY = (barHeight - (textPaint.ascent() + textPaint.descent())) / 2f

        // Draw background bar - always drawn
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, backgroundPaint)

        val dataToUse = coverageData // Use the stored data
        val currentLeftColor = leftPlayerColor
        val currentRightColor = rightPlayerColor

        // Get fractions, default to 0f. This allows "0%" text to be shown.
        val leftFraction = if (currentLeftColor != null) dataToUse[currentLeftColor] ?: 0f else 0f
        val rightFraction = if (currentRightColor != null) dataToUse[currentRightColor] ?: 0f else 0f

        val totalWidth = width.toFloat()

        // Left bar and text
        if (currentLeftColor != null) {
            paint.color = currentLeftColor
            val barRight = totalWidth * leftFraction
            // Draw bar even if fraction is 0 (it will be a 0-width line, effectively invisible but logic is there)
            canvas.drawRect(0f, 0f, barRight, barHeight, paint)
            
            val pctTextLeft = "${(leftFraction * 100).toInt()}%"
            // Position text: if bar is very small, anchor near left; otherwise, center in its potential space.
            val leftTextX = if (barRight < textPaint.measureText(pctTextLeft) + 20f) {
                textPaint.measureText(pctTextLeft) / 2f + 10f // Near left edge
            } else {
                barRight / 2f // Center in actual bar
            }
            // If no bar is drawn due to 0 fraction, attempt to draw 0% near the center of the left half
            val effectiveLeftTextX = if (leftFraction == 0f) totalWidth / 4f else leftTextX
            canvas.drawText(pctTextLeft, effectiveLeftTextX, centerY, textPaint)
        }

        // Right bar and text
        if (currentRightColor != null) {
            paint.color = currentRightColor
            val barLeft = totalWidth * (1 - rightFraction)
            // Draw bar even if fraction is 0
            canvas.drawRect(barLeft, 0f, totalWidth, barHeight, paint)

            val pctTextRight = "${(rightFraction * 100).toInt()}%"
            // Position text: if bar is very small, anchor near right; otherwise, center in its potential space.
            val rightTextX = if ((totalWidth - barLeft) < textPaint.measureText(pctTextRight) + 20f) {
                totalWidth - (textPaint.measureText(pctTextRight) / 2f + 10f) // Near right edge
            } else {
                barLeft + (totalWidth - barLeft) / 2f // Center in actual bar
            }
            // If no bar is drawn due to 0 fraction, attempt to draw 0% near the center of the right half
            val effectiveRightTextX = if (rightFraction == 0f) (totalWidth * 3f / 4f) else rightTextX
            canvas.drawText(pctTextRight, effectiveRightTextX, centerY, textPaint)
        }
    }
}
