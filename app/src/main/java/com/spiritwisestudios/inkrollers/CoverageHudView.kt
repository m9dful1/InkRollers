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
    private val backgroundPaint = Paint().apply { color = Color.DKGRAY }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
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
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = coverageData
        if (data.isEmpty()) return

        val halfWidth = width / 2f
        val barHeight = height.toFloat()
        val centerY = (barHeight - (textPaint.ascent() + textPaint.descent())) / 2f

        // Draw background bar
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, backgroundPaint)

        val leftColor = leftPlayerColor
        val rightColor = rightPlayerColor

        val leftFraction = if (leftColor != null) data[leftColor] ?: 0f else 0f
        val rightFraction = if (rightColor != null) data[rightColor] ?: 0f else 0f

        // Left bar grows from the left edge toward the center
        if (leftColor != null) {
            paint.color = leftColor
            val right = (halfWidth * leftFraction).coerceAtMost(halfWidth)
            canvas.drawRect(0f, 0f, right, barHeight, paint)
            val pctText = "${(leftFraction * 100).toInt()}%"
            canvas.drawText(pctText, right / 2f, centerY, textPaint)
        }

        // Right bar grows from the right edge toward the center
        if (rightColor != null) {
            paint.color = rightColor
            val left = width - (halfWidth * rightFraction).coerceAtMost(halfWidth)
            canvas.drawRect(left, 0f, width.toFloat(), barHeight, paint)
            val pctText = "${(rightFraction * 100).toInt()}%"
            canvas.drawText(pctText, left + (width - left) / 2f, centerY, textPaint)
        }
    }
}
