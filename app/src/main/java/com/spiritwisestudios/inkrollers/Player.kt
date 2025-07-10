package com.spiritwisestudios.inkrollers
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.graphics.Typeface
import com.spiritwisestudios.inkrollers.campaign.ColorFrequency

class Player(
    var surface: PaintSurface,
    startX: Float,
    startY: Float,
    playerColor: Int,
    private val multiplayerManager: MultiplayerManager? = null,
    private val level: Level? = null,
    var playerName: String = "",
    private val audioManager: AudioManager? = null,
    private val particleManager: com.spiritwisestudios.inkrollers.effects.ParticleManager? = null,
    private val isCampaignMode: Boolean = false
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
  var mode=0 //0 paint,1 fill
  
  // Audio state tracking
  private var isPaintSoundPlaying = false
  private var isRefillSoundPlaying = false
  
  // Color shift functionality for campaign mode
  private var currentFrequency: ColorFrequency = ColorFrequency.RED
  private val frequencyColors = mapOf(
      ColorFrequency.RED to Color.RED,
      ColorFrequency.BLUE to Color.BLUE,
      ColorFrequency.GREEN to Color.GREEN,
      ColorFrequency.YELLOW to Color.YELLOW
  )
  
  // Initialize paint color based on mode
  init {
      if (isCampaignMode) {
          // In campaign mode, start with red frequency
          paint.color = frequencyColors[ColorFrequency.RED] ?: Color.RED
      } else {
          // In multiplayer mode, use the provided player color
          paint.color = playerColor
      }
  }
  
  fun toggleMode(){ 
    // Stop all sounds when switching modes
    stopPaintSound()
    stopRefillSound()
    mode=1-mode 
    audioManager?.playSound(AudioManager.SoundType.MODE_TOGGLE)
  }
  
  /**
   * Toggle the color frequency for campaign mode
   */
  fun toggleColorShift() {
      if (!isCampaignMode) return
      
      currentFrequency = when (currentFrequency) {
          ColorFrequency.RED -> ColorFrequency.BLUE
          ColorFrequency.BLUE -> ColorFrequency.GREEN
          ColorFrequency.GREEN -> ColorFrequency.YELLOW
          ColorFrequency.YELLOW -> ColorFrequency.RED
      }
      
      // Update paint color to match new frequency
      paint.color = frequencyColors[currentFrequency] ?: Color.RED
      
      // Play color shift sound effect
      audioManager?.playSound(AudioManager.SoundType.COLOR_SHIFT)
      
      Log.d(TAG, "Color frequency shifted to: $currentFrequency")
  }
  
  /**
   * Get current color frequency (campaign mode)
   */
  fun getCurrentFrequency(): ColorFrequency = currentFrequency
  
  /**
   * Get the color corresponding to current frequency (campaign mode)
   */
  fun getFrequencyColor(): Int = frequencyColors[currentFrequency] ?: Color.RED
  
  /**
   * Check if player is in campaign mode
   */
  fun isCampaignMode(): Boolean = isCampaignMode
  fun move(dirX: Float, dirY: Float, magnitude: Float, level: Level? = null, deltaTime: Float) {
    // Log.d("Player", "move: Input: dirX=$dirX, dirY=$dirY, mag=$magnitude, deltaTime=$deltaTime")
    if (magnitude == 0f) return

    val moveAmount = MOVE_SPEED * magnitude * deltaTime
    var nextX = x + dirX * moveAmount
    var nextY = y + dirY * moveAmount

    nextX = nextX.coerceIn(0f, surface.w.toFloat() - 1)
    nextY = nextY.coerceIn(0f, surface.h.toFloat() - 1)

    // Use provided level parameter or class level field if available
    val currentLevel = level ?: this.level
    
    if (currentLevel != null && currentLevel.checkCollision(nextX, nextY)) {
      val nextXOnly = x + dirX * moveAmount
      val nextYOnly = y + dirY * moveAmount

      if (!currentLevel.checkCollision(nextXOnly, y)) {
        nextX = nextXOnly
        nextY = y
        // Log.d("Player", "move: Sliding X")
      } else if (!currentLevel.checkCollision(x, nextYOnly)) {
        nextX = x
        nextY = nextYOnly
        // Log.d("Player", "move: Sliding Y")
      } else {
        // Log.d("Player", "move: Blocked by collision")
        return
      }
    }

    if (nextX != x || nextY != y) {
        x = nextX
        y = nextY

        if (mode == 0) {
            if (ink > 0f) {
                // Start paint sound if not already playing
                startPaintSound()
                
                ink -= PAINT_COST
                if (ink < 0f) ink = 0f
                
                // Paint locally
                surface.paintAt(x, y, paint.color)
                
                // Create paint splat particle effect
                particleManager?.createPaintSplat(x, y, paint.color)
                
                try {
                    // Get the current level - either from parameter or member variable
                    val levelForCoords = level ?: this.level
                    
                    // Normalize coordinates for maze and send to network
                    if (levelForCoords is MazeLevel) {
                        val mazeRelativeCoords = levelForCoords.screenToMazeCoord(x, y)
                        // Send normalized coordinates
                        multiplayerManager?.sendPaintAction(
                            x.toInt(), 
                            y.toInt(), 
                            paint.color,
                            mazeRelativeCoords.first,  // Normalized X
                            mazeRelativeCoords.second  // Normalized Y
                        )
                    } else {
                        // Fallback to absolute coordinates if maze conversion not available
                        multiplayerManager?.sendPaintAction(x.toInt(), y.toInt(), paint.color)
                    }
                } catch (e: Exception) {
                    Log.e("Player", "Error sending paint action", e)
                    // Fallback to absolute coordinates
                    multiplayerManager?.sendPaintAction(x.toInt(), y.toInt(), paint.color)
                }
            } else {
                // Stop paint sound if out of ink
                stopPaintSound()
            }
        } else {
            val ix = x.toInt()
            val iy = y.toInt()
            if (ix >= 0 && ix < surface.w && iy >= 0 && iy < surface.h) {
                 if (surface.getPixelColor(ix, iy) == paint.color && ink < MAX_INK) {
                    // Start refill sound if not already playing
                    startRefillSound()
                    
                    ink += REFILL_GAIN
                    if (ink > MAX_INK) ink = MAX_INK
                } else {
                    // Stop refill sound if not on correct color
                    stopRefillSound()
                }
            }
        }
    } else {
        // Player not moving, stop all looping sounds
        stopPaintSound()
        stopRefillSound()
    }
    // Log.d("Player", "move: Final position: ($x, $y)")
  }
  fun getInkPercent(): Float = ink / MAX_INK
  fun getModeText(): String = if (mode == 0) "PAINT" else "FILL"
  fun getColor(): Int = paint.color
  fun update(){}
  fun draw(c:Canvas){
    // Draw main player circle
    var radius = PLAYER_RADIUS
    c.drawCircle(x, y, radius, paint)

    // Draw highlight (top-left)
    val highlightPaint = Paint().apply {
        color = Color.WHITE
        alpha = 150 // More opaque for visibility on all colors
        isAntiAlias = true
    }
    val highlightRadius = radius * 0.45f
    c.drawCircle(x - radius * 0.3f, y - radius * 0.3f, highlightRadius, highlightPaint)

    // Draw shadow (bottom-right, optional)
    val shadowPaint = Paint().apply {
        color = Color.BLACK
        alpha = 50 // very transparent
        isAntiAlias = true
    }
    val shadowRadius = radius * 0.9f
    c.drawCircle(x + radius * 0.2f, y + radius * 0.2f, shadowRadius, shadowPaint)

  }
  
  /** Starts the looping paint sound if not already playing. */
  private fun startPaintSound() {
    if (!isPaintSoundPlaying) {
      audioManager?.startLoopingSound(AudioManager.SoundType.PAINT, 0.3f)
      isPaintSoundPlaying = true
    }
  }

  /** Stops the looping paint sound if playing. */
  private fun stopPaintSound() {
    if (isPaintSoundPlaying) {
      audioManager?.stopLoopingSound(AudioManager.SoundType.PAINT)
      isPaintSoundPlaying = false
    }
  }

  /** Starts the looping refill sound if not already playing. */
  private fun startRefillSound() {
    if (!isRefillSoundPlaying) {
      audioManager?.startLoopingSound(AudioManager.SoundType.REFILL, 0.5f)
      isRefillSoundPlaying = true
    }
  }

  /** Stops the looping refill sound if playing. */
  private fun stopRefillSound() {
    if (isRefillSoundPlaying) {
      audioManager?.stopLoopingSound(AudioManager.SoundType.REFILL)
      isRefillSoundPlaying = false
    }
  }
}
