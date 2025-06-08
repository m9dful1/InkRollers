package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * HUD component displaying local player's ink level and current mode.
 * 
 * Shows a vertical ink meter (blue bar) and mode text ("PAINT" or "FILL")
 * positioned on the left side of the screen. Updated by GameView based
 * on local player state.
 */
class InkHudView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var inkPercent = 1f // 0.0 to 1.0
    private var modeText = "PAINT"

    private val barPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
    private val barBackgroundPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val textPaint = Paint().apply { color = Color.BLACK; textSize = 40f; textAlign = Paint.Align.CENTER }

    /** Updates ink level and mode display. Called by GameView when local player state changes. */
    fun updateHud(inkPercent: Float, modeText: String) {
        this.inkPercent = inkPercent.coerceIn(0f, 1f)
        this.modeText = modeText
        invalidate() // Request a redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width * 0.6f
        val barHeight = height * 0.7f
        val barLeft = (width - barWidth) / 2f
        val barTop = height * 0.1f
        val barBottom = barTop + barHeight

        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barBottom, barBackgroundPaint)
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barBottom, borderPaint)

        val inkHeight = barHeight * inkPercent
        val inkTop = barBottom - inkHeight
        canvas.drawRect(barLeft, inkTop, barLeft + barWidth, barBottom, barPaint)

        val textX = width / 2f
        val textY = height * 0.9f
        canvas.drawText(modeText, textX, textY, textPaint)
    }
} 