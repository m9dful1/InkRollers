package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class InkHudView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var inkPercent = 1f // 0.0 to 1.0
    private var modeText = "PAINT"
    private var inkColor = Color.BLUE // Default ink color

    private val barPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private val barBackgroundPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val dividerPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val textPaint = Paint().apply { color = Color.BLACK; textSize = 40f; textAlign = Paint.Align.CENTER }

    private val pillRect = RectF()
    private val inkRect = RectF()

    fun updateHud(inkPercent: Float, modeText: String, inkColor: Int = Color.BLUE) {
        this.inkPercent = inkPercent.coerceIn(0f, 1f)
        this.modeText = modeText
        this.inkColor = inkColor
        this.barPaint.color = inkColor
        invalidate() // Request a redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width * 0.6f
        val barHeight = height * 0.7f
        val barLeft = (width - barWidth) / 2f
        val barTop = height * 0.1f
        val barBottom = barTop + barHeight
        val cornerRadius = barWidth / 2f // Makes it pill-shaped

        // Set up pill rectangle
        pillRect.set(barLeft, barTop, barLeft + barWidth, barBottom)

        // Draw pill background
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, barBackgroundPaint)

        // Draw ink level
        val inkHeight = barHeight * inkPercent
        val inkTop = barBottom - inkHeight
        
        // Create clipped ink rectangle that follows pill shape
        inkRect.set(barLeft, inkTop, barLeft + barWidth, barBottom)
        
        // Save canvas state for clipping
        val saveCount = canvas.save()
        
        // Clip to pill shape
        canvas.clipRect(pillRect)
        canvas.drawRoundRect(inkRect, cornerRadius, cornerRadius, barPaint)
        
        // Restore canvas state
        canvas.restoreToCount(saveCount)

        // Draw 3 white divider lines to create 4 sections
        val sectionHeight = barHeight / 4f
        for (i in 1..3) {
            val dividerY = barTop + (i * sectionHeight)
            canvas.drawLine(barLeft + 4f, dividerY, barLeft + barWidth - 4f, dividerY, dividerPaint)
        }

        // Draw pill border
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, borderPaint)

        // Draw mode text
        val textX = width / 2f
        val textY = height * 0.9f // Below the bar
        canvas.drawText(modeText, textX, textY, textPaint)
    }
} 