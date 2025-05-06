package com.spiritwisestudios.inkrollers
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log

class Player(
    private val surface: PaintSurface,
    startX: Float,
    startY: Float,
    playerColor: Int,
    private val multiplayerManager: MultiplayerManager? = null,
    private val level: Level? = null
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
  fun toggleMode(){ mode=1-mode }
  fun move(dirX: Float, dirY: Float, magnitude: Float, level: Level? = null, deltaTime: Float) {
    Log.d("Player", "move: Input: dirX=$dirX, dirY=$dirY, mag=$magnitude, deltaTime=$deltaTime")
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
        Log.d("Player", "move: Sliding X")
      } else if (!currentLevel.checkCollision(x, nextYOnly)) {
        nextX = x
        nextY = nextYOnly
        Log.d("Player", "move: Sliding Y")
      } else {
        Log.d("Player", "move: Blocked by collision")
        return
      }
    }

    if (nextX != x || nextY != y) {
        x = nextX
        y = nextY

        if (mode == 0) {
            if (ink > 0f) {
                ink -= PAINT_COST
                if (ink < 0f) ink = 0f
                
                // Paint locally
                surface.paintAt(x, y, paint.color)
                
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
            }
        } else {
            val ix = x.toInt()
            val iy = y.toInt()
            if (ix >= 0 && ix < surface.w && iy >= 0 && iy < surface.h) {
                 if (surface.getPixelColor(ix, iy) == paint.color && ink < MAX_INK) {
                    ink += REFILL_GAIN
                    if (ink > MAX_INK) ink = MAX_INK
                }
            }
        }
    }
    Log.d("Player", "move: Final position: ($x, $y)")
  }
  fun getInkPercent(): Float = ink / MAX_INK
  fun getModeText(): String = if (mode == 0) "PAINT" else "FILL"
  fun getColor(): Int = paint.color
  fun update(){}
  fun draw(c:Canvas){
    Log.d(TAG, "Player.draw() for player at ($x, $y) with color ${paint.color} and radius $PLAYER_RADIUS")
    // Scale player radius based on the level's scale factor if available
    var radius = PLAYER_RADIUS
    c.drawCircle(x, y, radius, paint)
    Log.d(TAG, "Player.draw() - Finished drawing player at ($x, $y)")
  }
}
