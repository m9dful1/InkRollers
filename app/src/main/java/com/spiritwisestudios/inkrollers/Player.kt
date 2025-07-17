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
    val oldMode = mode
    mode=1-mode 
    Log.d(TAG, "toggleMode: Changed from mode $oldMode to mode $mode")
    audioManager?.playSound(AudioManager.SoundType.MODE_TOGGLE)
  }
  
  /**
   * Toggle the color frequency for campaign mode
   * Thread-safe implementation with comprehensive error handling
   */
  @Synchronized
  fun toggleColorShift(): Boolean {
      try {
          // Validate campaign mode
          if (!isCampaignMode) {
              Log.w(TAG, "toggleColorShift called but player is not in campaign mode")
              return false
          }
          
          val oldFrequency = currentFrequency
          
          // Cycle through frequencies - Kotlin ensures currentFrequency is never null
          currentFrequency = when (currentFrequency) {
              ColorFrequency.RED -> ColorFrequency.BLUE
              ColorFrequency.BLUE -> ColorFrequency.GREEN
              ColorFrequency.GREEN -> ColorFrequency.YELLOW
              ColorFrequency.YELLOW -> ColorFrequency.RED
          }
          
          // Update paint color with validation
          val newColor = frequencyColors[currentFrequency]
          if (newColor == null) {
              Log.e(TAG, "toggleColorShift: No color found for frequency $currentFrequency, using fallback")
              paint.color = Color.RED
              currentFrequency = ColorFrequency.RED
              return false
          } else {
              paint.color = newColor
          }
          
          // Play color shift sound effect (safe call)
          try {
              audioManager?.playSound(AudioManager.SoundType.COLOR_SHIFT)
          } catch (e: Exception) {
              Log.w(TAG, "toggleColorShift: Failed to play sound effect", e)
              // Continue execution - sound failure shouldn't break color shift
          }
          
          Log.d(TAG, "Color frequency shifted from $oldFrequency to $currentFrequency (color: ${String.format("#%06X", 0xFFFFFF and newColor)})")
          return true
          
      } catch (e: Exception) {
          Log.e(TAG, "toggleColorShift: Unexpected error during color shift", e)
          // Reset to safe state
          try {
              currentFrequency = ColorFrequency.RED
              paint.color = Color.RED
          } catch (resetError: Exception) {
              Log.e(TAG, "toggleColorShift: Failed to reset to safe state", resetError)
          }
          return false
      }
  }
  
  /**
   * Get current color frequency (campaign mode)
   * Thread-safe with validation
   */
  @Synchronized
  fun getCurrentFrequency(): ColorFrequency {
      // Validate campaign mode
      if (!isCampaignMode) {
          Log.w(TAG, "getCurrentFrequency called but player is not in campaign mode")
          return ColorFrequency.RED // Safe fallback
      }
      
      return currentFrequency // Kotlin ensures this is never null
  }
  
  /**
   * Get the color corresponding to current frequency (campaign mode)
   * Thread-safe with validation
   */
  @Synchronized
  fun getFrequencyColor(): Int {
      if (!isCampaignMode) {
          Log.w(TAG, "getFrequencyColor called but player is not in campaign mode")
          return getColor() // Fall back to regular player color
      }
      
      val frequency = getCurrentFrequency() // This includes validation
      return frequencyColors[frequency] ?: run {
          Log.e(TAG, "getFrequencyColor: No color found for frequency $frequency, using RED fallback")
          Color.RED
      }
  }
  
  /**
   * Validate and fix frequency state if corrupted
   */
  @Synchronized
  fun validateFrequencyState(): Boolean {
      if (!isCampaignMode) return true
      
      try {
          // Check if current frequency has a valid color mapping
          val isValidFrequency = frequencyColors.containsKey(currentFrequency)
          
          if (!isValidFrequency) {
              Log.w(TAG, "validateFrequencyState: Invalid frequency state detected, resetting to RED")
              currentFrequency = ColorFrequency.RED
              paint.color = Color.RED
              return false
          }
          
          // Check if paint color matches frequency
          val expectedColor = frequencyColors[currentFrequency]
          if (expectedColor != null && paint.color != expectedColor) {
              Log.w(TAG, "validateFrequencyState: Paint color mismatch, fixing")
              paint.color = expectedColor
              return false
          }
          
          return true
      } catch (e: Exception) {
          Log.e(TAG, "validateFrequencyState: Error during validation", e)
          currentFrequency = ColorFrequency.RED
          paint.color = Color.RED
          return false
      }
  }
  
  /**
   * Check if player is in campaign mode
   */
  fun isCampaignMode(): Boolean = isCampaignMode
  fun move(dirX: Float, dirY: Float, magnitude: Float, level: Level? = null, deltaTime: Float) {
    // 1. Calculate final position based on input and collision.
    var finalX = x
    var finalY = y

    if (magnitude > 0f) {
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
                finalX = nextXOnly
                finalY = y
            } else if (!currentLevel.checkCollision(x, nextYOnly)) {
                finalX = x
                finalY = nextYOnly
            }
            // else, finalX and finalY remain x and y (no movement)
        } else {
            finalX = nextX
            finalY = nextY
        }
    }

    // 2. Update player's position state.
    val moved = (x != finalX || y != finalY)
    x = finalX
    y = finalY

    // 3. Handle actions (paint or refill) based on the current mode.
    if (mode == 0) { // PAINT mode
        stopRefillSound() // Ensure refill sound is off
        if (moved && ink > 0f) {
            startPaintSound()
            ink -= PAINT_COST
            if (ink < 0f) ink = 0f
            
            surface.paintAt(x, y, paint.color)
            particleManager?.createPaintSplat(x, y, paint.color)
            
            // Network paint action
            try {
                val levelForCoords = level ?: this.level
                if (levelForCoords is MazeLevel) {
                    val mazeRelativeCoords = levelForCoords.screenToMazeCoord(x, y)
                    multiplayerManager?.sendPaintAction(x.toInt(), y.toInt(), paint.color, mazeRelativeCoords.first, mazeRelativeCoords.second)
                } else {
                    multiplayerManager?.sendPaintAction(x.toInt(), y.toInt(), paint.color)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending paint action", e)
                multiplayerManager?.sendPaintAction(x.toInt(), y.toInt(), paint.color)
            }
        } else {
            stopPaintSound() // Stop if not moving or out of ink
        }
    } else { // FILL mode
        stopPaintSound() // Ensure paint sound is off
        refillCurrentSpot() // Refill regardless of movement
    }
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

  fun changeModeIfNeeded(newMode: Int) {
    Log.d(TAG, "changeModeIfNeeded called: current mode = $mode, requested mode = $newMode")
    if (newMode != mode) {
      toggleMode()
    }
  }

  /**
   * Attempt to refill ink at the player's current position if conditions are met.
   */
  private fun refillCurrentSpot() {
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
      } else {
          stopRefillSound()
      }
  }
}
