package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
    private val paint = Paint().apply { style = Paint.Style.FILL }
    private val textPaint = Paint().apply {
        color = 0xFF000000.toInt()
        textSize = 36f
        isAntiAlias = true
    }

    /**
     * Update coverage percentages and redraw.
     * @param data Map of color (Int) to fraction (0-1)
     */
    fun updateCoverage(data: Map<Int, Float>) {
        coverageData = data
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = coverageData
        if (data.isEmpty()) return
        
        // Configuration: gutter and min bar width in pixels
        val density = resources.displayMetrics.density
        val baseGutter = 8f * density
        val minBarWidth = 16f * density
        val textMargin = 4f * density
        
        // Text metrics to reserve bottom space
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        // Baseline for drawing text: above bottom by descent and margin
        val baselineY = height.toFloat() - fm.descent - textMargin
        // Bottom of bars should be above the text by margin
        val barBottom = baselineY - textMargin
        
        // Compute maximum text width for gutter
        val pctStrings = data.values.map { "${(it * 100).toInt()}%" }
        val maxTextWidth = pctStrings.map { textPaint.measureText(it) }.maxOrNull() ?: 0f
        val gutter = maxOf(baseGutter, maxTextWidth / 2f + textMargin)
        
        val n = data.size
        val totalGutter = (n + 1) * gutter
        val availableWidth = width - totalGutter
        val barWidth = (availableWidth / n).coerceAtLeast(minBarWidth)
        
        data.entries.forEachIndexed { index, (color, fraction) ->
            paint.color = color
            // Compute left edge for this bar
            val left = gutter + index * (barWidth + gutter)
            // Compute bar top based on fraction and reserved barBottom
            val top = barBottom - (barBottom * fraction)
            val right = left + barWidth
            // Draw bar within reserved area
            canvas.drawRect(left, top, right, barBottom, paint)
            
            // Draw text at baseline
            val pctText = "${(fraction * 100).toInt()}%"
            val textX = left + barWidth / 2f
            canvas.drawText(pctText, textX, baselineY, textPaint)
        }
    }
} 