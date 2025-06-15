package com.spiritwisestudios.inkrollers
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.graphics.Typeface

/**
 * Represents a paint roller player in the game.
 * 
 * Handles player movement, painting mechanics, ink management, and collision detection.
 * Supports two modes: PAINT (consumes ink to paint) and FILL (refills ink from same-color paint).
 * Coordinates with MultiplayerManager to synchronize paint actions across clients.
 */
class Player(
    var surface: PaintSurface,
    startX: Float,
    startY: Float,
    playerColor: Int,
    private val multiplayerManager: MultiplayerManager? = null,
    private val level: Level? = null,
    var playerName: String = "",
    private val audioManager: com.spiritwisestudios.inkrollers.AudioManager? = null
) {
  companion object {
    const val MAX_INK = 100f
    const val PAINT_COST = 0.1f
    const val REFILL_GAIN = 0.5f
    const val MOVE_SPEED = 200f
    const val PLAYER_RADIUS = 40f
    private const val TAG = "Player"
  }
  
  var ink = MAX_INK
  var x = startX
  var y = startY
  private val paint=Paint().apply{ color = playerColor }
  /** Player mode: 0 for PAINT, 1 for FILL. */
  var mode=0
  
  // Track painting state to control audio
  private var isPaintSoundPlaying = false
  private var isRefillSoundPlaying = false
  
  /** Switches between PAINT and FILL modes. */
  fun toggleMode(){ 
    mode=1-mode
    // Stop all sounds when switching modes
    stopPaintSound()
    stopRefillSound()
    audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.MODE_TOGGLE)
  }
  
  /** Starts the looping paint sound if not already playing. */
  private fun startPaintSound() {
    if (!isPaintSoundPlaying) {
      audioManager?.startLoopingSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.PAINT, 0.3f)
      isPaintSoundPlaying = true
    }
  }
  
  /** Stops the looping paint sound if playing. */
  private fun stopPaintSound() {
    if (isPaintSoundPlaying) {
      audioManager?.stopLoopingSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.PAINT)
      isPaintSoundPlaying = false
    }
  }
  
  /** Starts the looping refill sound if not already playing. */
  private fun startRefillSound() {
    if (!isRefillSoundPlaying) {
      audioManager?.startLoopingSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.REFILL, 0.5f)
      isRefillSoundPlaying = true
    }
  }
  
  /** Stops the looping refill sound if playing. */
  private fun stopRefillSound() {
    if (isRefillSoundPlaying) {
      audioManager?.stopLoopingSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.REFILL)
      isRefillSoundPlaying = false
    }
  }
  
  /**
   * Moves player based on input direction and handles painting/refilling.
   * 
   * Performs collision detection with level geometry and applies sliding movement
   * when blocked. In PAINT mode, paints at current position and syncs to network.
   * In FILL mode, refills ink when over same-color paint.
   */
  fun move(dirX: Float, dirY: Float, magnitude: Float, deltaTime: Float, level: Level? = null) {
    if (magnitude == 0f) {
        // Player stopped moving, stop all sounds
        stopPaintSound()
        stopRefillSound()
        return
    }

    val moveAmount = MOVE_SPEED * magnitude * deltaTime
    var nextX = x + dirX * moveAmount
    var nextY = y + dirY * moveAmount

    nextX = nextX.coerceIn(0f, surface.w.toFloat() - 1)
    nextY = nextY.coerceIn(0f, surface.h.toFloat() - 1)

    val currentLevel = level ?: this.level
    
    if (currentLevel != null && currentLevel.checkCollision(nextX, nextY)) {
      val nextXOnly = x + dirX * moveAmount
      val nextYOnly = y + dirY * moveAmount

      if (!currentLevel.checkCollision(nextXOnly, y)) {
        nextX = nextXOnly
        nextY = y
      } else if (!currentLevel.checkCollision(x, nextYOnly)) {
        nextX = x
        nextY = nextYOnly
      } else {
        // Player is blocked, stop all sounds
        stopPaintSound()
        stopRefillSound()
        return
      }
    }

    if (nextX != x || nextY != y) {
        x = nextX
        y = nextY

        if (mode == 0) { // PAINT mode
            // Stop refill sound when switching to paint mode
            stopRefillSound()
            if (ink > 0f) {
                ink -= PAINT_COST
                if (ink < 0f) ink = 0f
                
                surface.paintAt(x, y, paint.color)
                
                // Start looping paint sound while moving and painting
                startPaintSound()
                
                try {
                    val levelForCoords = level ?: this.level
                    
                    if (levelForCoords is MazeLevel) {
                        val (normX, normY) = levelForCoords.screenToMazeCoord(x, y)
                        multiplayerManager?.sendPaintAction(
                            x.toInt(), 
                            y.toInt(), 
                            paint.color,
                            normX,
                            normY
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Player", "Error sending paint action", e)
                }
            } else {
                // Out of ink, stop painting sound
                stopPaintSound()
            }
        } else { // Fill mode
            // Not in paint mode, stop painting sound
            stopPaintSound()
            
            val ix = x.toInt()
            val iy = y.toInt()
            if (ix >= 0 && ix < surface.w && iy >= 0 && iy < surface.h) {
                if (surface.getPixelColor(ix, iy) == paint.color && ink < MAX_INK) {
                    ink += REFILL_GAIN
                    if (ink > MAX_INK) ink = MAX_INK
                    // Start the sound only if we are actively refilling
                    startRefillSound()
                } else {
                    // Stop the sound if we move off our color
                    stopRefillSound()
                }
            } else {
                // Outside the surface, definitely stop
                stopRefillSound()
            }
        }
    } else {
        // Player didn't actually move, stop all sounds
        stopPaintSound()
        stopRefillSound()
    }
  }
  
  /** Returns ink level as percentage (0.0-1.0) for HUD display. */
  fun getInkPercent(): Float = ink / MAX_INK
  
  /** Returns current mode as display text for HUD. */
  fun getModeText(): String = if (mode == 0) "PAINT" else "FILL"
  
  /** Returns player's paint color for coverage calculations and display. */
  fun getColor(): Int = paint.color
  
  /** Cleanup method to stop any playing sounds. Should be called when player is removed. */
  fun cleanup() {
    stopPaintSound()
    stopRefillSound()
  }
  
  /** Renders player as a colored circle with highlight and shadow effects. */
  fun draw(c:Canvas){
    var radius = PLAYER_RADIUS
    c.drawCircle(x, y, radius, paint)

    val highlightPaint = Paint().apply {
        color = Color.WHITE
        alpha = 150
        isAntiAlias = true
    }
    val highlightRadius = radius * 0.45f
    c.drawCircle(x - radius * 0.3f, y - radius * 0.3f, highlightRadius, highlightPaint)

    val shadowPaint = Paint().apply {
        color = Color.BLACK
        alpha = 50
        isAntiAlias = true
    }
    val shadowRadius = radius * 0.9f
    c.drawCircle(x + radius * 0.2f, y + radius * 0.2f, shadowRadius, shadowPaint)
  }
}
