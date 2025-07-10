package com.spiritwisestudios.inkrollers.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.spiritwisestudios.inkrollers.R
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.PaintSurface
import com.spiritwisestudios.inkrollers.VirtualJoystick
import com.spiritwisestudios.inkrollers.effects.ParticleManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles all rendering logic for the game, providing clean separation
 * between game logic and drawing operations.
 * 
 * This class encapsulates all drawing-related code that was previously
 * scattered throughout GameView, improving maintainability and testability.
 */
class GameRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "GameRenderer"
    }
    
    // Background image for the maze (center-cropped)
    private val bgBitmap: Bitmap by lazy { 
        BitmapFactory.decodeResource(context.resources, R.drawable.space_bg) 
    }
    
    // Reusable drawing objects to avoid allocation during rendering
    private val cornerNamePaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    // Rectangle for background scaling calculations
    private val backgroundDestRect = RectF()
    
    // Viewport dimensions for calculations
    private var viewWidth = 0
    private var viewHeight = 0
    
    /**
     * Initialize the renderer with viewport dimensions.
     * Should be called when the surface size is known.
     */
    fun initialize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        calculateBackgroundRect()
        Log.d(TAG, "GameRenderer initialized with size: ${width}x${height}")
    }
    
    /**
     * Calculate the background scaling rectangle for center-crop effect
     */
    private fun calculateBackgroundRect() {
        if (viewWidth == 0 || viewHeight == 0) return
        
        val viewW = viewWidth.toFloat()
        val viewH = viewHeight.toFloat()
        val bmpW = bgBitmap.width.toFloat()
        val bmpH = bgBitmap.height.toFloat()
        val scale = maxOf(viewW / bmpW, viewH / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val left = (viewW - scaledW) / 2
        val top = (viewH - scaledH) / 2
        backgroundDestRect.set(left, top, left + scaledW, top + scaledH)
    }
    
    /**
     * Render the complete game scene to the canvas.
     * 
     * @param canvas Canvas to draw on
     * @param surface Paint surface containing painted areas
     * @param currentLevel Current game level (maze)
     * @param players Map of all players
     * @param joysticks Map of joysticks
     * @param localPlayerId ID of the local player (for joystick rendering)
     * @param particleManager Particle manager for visual effects
     */
    fun render(
        canvas: Canvas,
        surface: PaintSurface?,
        currentLevel: Level?,
        players: ConcurrentHashMap<String, Player>,
        joysticks: ConcurrentHashMap<String, VirtualJoystick>,
        localPlayerId: String?,
        particleManager: ParticleManager? = null
    ) {
        try {
            // 1. Draw background
            drawBackground(canvas)
            
            // 2. Draw painted surface
            surface?.let { drawPaintSurface(canvas, it) }
            
            // 3. Draw level (maze walls)
            currentLevel?.let { drawLevel(canvas, it) }
            
            // 4. Draw all players
            drawPlayers(canvas, players)
            
            // 5. Draw particles (on top of players for better visibility)
            particleManager?.let { drawParticles(canvas, it) }
            
            // 6. Draw local joystick
            drawLocalJoystick(canvas, joysticks, localPlayerId)
            
            // 7. Draw corner names
            drawCornerNames(canvas, players)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during rendering", e)
        }
    }
    
    /**
     * Draw the center-cropped background image
     */
    private fun drawBackground(canvas: Canvas) {
        canvas.drawBitmap(bgBitmap, null, backgroundDestRect, null)
    }
    
    /**
     * Draw the paint surface (painted areas)
     */
    private fun drawPaintSurface(canvas: Canvas, surface: PaintSurface) {
        surface.drawTo(canvas)
    }
    
    /**
     * Draw the current level (maze walls)
     */
    private fun drawLevel(canvas: Canvas, level: Level) {
        level.draw(canvas)
    }
    
    /**
     * Draw particle effects
     */
    private fun drawParticles(canvas: Canvas, particleManager: ParticleManager) {
        try {
            particleManager.draw(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing particles", e)
        }
    }
    
    /**
     * Draw all players (local and remote)
     */
    private fun drawPlayers(canvas: Canvas, players: ConcurrentHashMap<String, Player>) {
        for ((id, player) in players) {
            try {
                player.draw(canvas)
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing player $id", e)
            }
        }
    }
    
    /**
     * Draw the local player's joystick
     */
    private fun drawLocalJoystick(
        canvas: Canvas, 
        joysticks: ConcurrentHashMap<String, VirtualJoystick>, 
        localPlayerId: String?
    ) {
        localPlayerId?.let { playerId ->
            joysticks[playerId]?.let { joystick ->
                try {
                    joystick.draw(canvas)
                } catch (e: Exception) {
                    Log.e(TAG, "Error drawing joystick for player $playerId", e)
                }
            }
        }
    }
    
    /**
     * Draw player names in the screen corners
     */
    private fun drawCornerNames(canvas: Canvas, players: ConcurrentHashMap<String, Player>) {
        val margin = 16f
        
        // Draw player0 name in top-left corner
        players["player0"]?.playerName?.takeIf { it.isNotEmpty() }?.let { name ->
            cornerNamePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(name, margin, margin + cornerNamePaint.textSize, cornerNamePaint)
        }
        
        // Draw player1 name in bottom-right corner
        players["player1"]?.playerName?.takeIf { it.isNotEmpty() }?.let { name ->
            cornerNamePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(name, viewWidth - margin, viewHeight - margin, cornerNamePaint)
        }
    }
    
    /**
     * Update renderer when viewport size changes
     */
    fun onSizeChanged(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        calculateBackgroundRect()
        Log.d(TAG, "GameRenderer size changed to: ${width}x${height}")
    }
    
    /**
     * Get the current background bitmap (for debugging or effects)
     */
    fun getBackgroundBitmap(): Bitmap = bgBitmap
    
    /**
     * Get the current background destination rectangle
     */
    fun getBackgroundRect(): RectF = RectF(backgroundDestRect)
} 