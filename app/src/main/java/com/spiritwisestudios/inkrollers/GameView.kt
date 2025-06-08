package com.spiritwisestudios.inkrollers
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.graphics.PixelFormat
import android.util.Log
import java.util.concurrent.ConcurrentHashMap // Use thread-safe map
import com.spiritwisestudios.inkrollers.GameModeManager
import com.spiritwisestudios.inkrollers.GameMode
import com.spiritwisestudios.inkrollers.CoverageHudView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.spiritwisestudios.inkrollers.TimerHudView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Main game rendering surface and engine for Ink Rollers.
 * 
 * Manages the complete game loop including player input, movement, painting mechanics,
 * multiplayer synchronization, HUD updates, and match progression. Coordinates between
 * local player controls (touch/joystick) and remote player state via MultiplayerManager.
 * 
 * Handles both Coverage and Zones game modes with real-time statistics calculation and
 * UI updates. Provides thread-safe game state management and seamless surface recreation
 * for Android lifecycle events.
 */
class GameView @JvmOverloads constructor(ctx:Context,attrs:AttributeSet?=null):
    SurfaceView(ctx,attrs),SurfaceHolder.Callback, MultiplayerManager.RemoteUpdateListener { // Implement listener
  private lateinit var surface: PaintSurface
  // Use a Map to store players, keyed by their Firebase ID (player0, player1, etc.)
  val players = ConcurrentHashMap<String, Player>()
  private val joysticks = ConcurrentHashMap<String, VirtualJoystick>() // Also map joysticks by player ID
  private val pendingPlayerStates = ConcurrentHashMap<String, PlayerState>() // Cache for early arrivals
  private var inkHudView: InkHudView? = null
  private var coverageHudView: CoverageHudView? = null
  private var zoneHudView: ZoneHudView? = null
  private var gameModeManager: GameModeManager? = null
  private var timerHudView: TimerHudView? = null
  // Use var and lateinit for the thread to allow recreation
  private lateinit var thread: GameThread 
  
  private var currentLevel: Level? = null 
  private var coverageStats: Map<Int, Float> = emptyMap()
  private var coverageUpdateFrames = 30 
  private var frameCount = 0
  
  // Multiplayer specific fields
  private var multiplayerManager: MultiplayerManager? = null
  private var localPlayerId: String? = null
  
  // Audio manager for game sound effects
  private val audioManager: com.spiritwisestudios.inkrollers.AudioManager by lazy { 
    com.spiritwisestudios.inkrollers.AudioManager.getInstance(context)
  }
  
  // New state flag to prevent premature end-check
  private var isMatchReady: Boolean = false
  
  // New field for match end listener
  var onMatchEnd: ((Boolean) -> Unit)? = null
  // Flag to prevent multiple match end notifications
  private var endNotified: Boolean = false
  
  // Background image for the maze (center-cropped)
  private val bgBitmap: Bitmap by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.space_bg) }
  
  companion object {
      private const val TAG = "GameView"
      var savedPaintBitmap: android.graphics.Bitmap? = null // For paint persistence
  }
  
  init {
    holder.addCallback(this)
    isFocusable = true
    holder.setFormat(PixelFormat.TRANSLUCENT)
    setZOrderMediaOverlay(true)
  }
  
  /** Initializes paint surface and restores previous state if available. */
  override fun surfaceCreated(h:SurfaceHolder){
    Log.i(TAG, "surfaceCreated called. SurfaceView size: ${width}x${height}")
    surface = if (savedPaintBitmap != null) {
        PaintSurface(width, height, savedPaintBitmap!!)
    } else {
        PaintSurface(width, height)
    }
    savedPaintBitmap = null
    
    for (player in players.values) {
        player.surface = surface
    }
  }
  
  /** Saves paint state and ensures clean thread termination. */
  override fun surfaceDestroyed(h:SurfaceHolder){ 
    Log.i(TAG, "surfaceDestroyed called. Stopping game thread if running.")
    if (::surface.isInitialized) {
        savedPaintBitmap = surface.getBitmapCopy()
    }
    stopThread()
    Log.d(TAG, "Surface destroyed, game thread stopped.")
  }
  
  override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,h2:Int){}
  
  /** Safely stops the game thread and waits for completion with timeout handling. */
  fun stopThread() {
      synchronized(this) {
          // Clean up any looping sounds from players
          players.values.forEach { it.cleanup() }
          
          if (::thread.isInitialized && thread.isAlive) {
              Log.d(TAG, "stopThread: Attempting to stop game thread. State: ${thread.state}")
              var retry = true
              thread.running = false
              while (retry) {
                  try {
                      thread.join(500)
                      if (!thread.isAlive) {
                          retry = false
                          Log.d(TAG, "Game thread stopped and joined.")
                      } else {
                          Log.w(TAG, "Game thread still alive after join timeout. Retrying...")
                      }
                  } catch (e: InterruptedException) {
                      Log.w(TAG, "Interrupted while joining game thread", e)
                  }
              }
          } else {
              Log.d(TAG, "stopThread called but thread not initialized or not alive. State: ${if (::thread.isInitialized) thread.state else "not initialized"}")
          }
      }
  }

  /** Starts the game loop thread with proper state validation and recovery. */
  fun startGameLoop() {
      synchronized(this) {
          Log.d(TAG, "startGameLoop: Called. Thread initialized: ${if (::thread.isInitialized) "yes" else "no"}, Thread state: ${if (::thread.isInitialized) thread.state else "not initialized"}")
          if (!::thread.isInitialized) {
              Log.e(TAG, "Cannot start game loop: thread not initialized.")
              return
          }
          Log.d(TAG, "startGameLoop: Thread state before start: ${thread.state}")
          if (!thread.isAlive) {
              try {
                 if (thread.state == Thread.State.NEW) {
                    thread.running = true
                    Log.d(TAG, "startGameLoop: Starting thread (NEW state)")
                    thread.start()
                    Log.d(TAG, "Game thread started.")
                 } else {
                     Log.w(TAG, "Attempting to start thread in unexpected state: ${thread.state}. Re-initializing.")
                     thread = GameThread(holder, this)
                     thread.running = true
                     Log.d(TAG, "startGameLoop: Re-initialized thread, now starting.")
                     thread.start()
                     Log.d(TAG, "Game thread re-initialized and started.")
                 }
              } catch(e: IllegalThreadStateException) {
                  Log.e(TAG, "Failed to start game thread", e)
              }
          } else {
              Log.d(TAG, "startGameLoop called but thread already running.")
          }
      }
  }

  /** 
   * Main game loop update method called each frame.
   * Handles local player movement, Firebase state synchronization, HUD updates,
   * and game mode progression (coverage/zones calculations, timer management).
   */
  fun update(deltaTime: Float){
      val localPlayer = getLocalPlayer()
      val localJoystick = if (localPlayerId != null) joysticks[localPlayerId] else null

      if (localPlayer != null && localJoystick != null && currentLevel is MazeLevel) {
          localPlayer.move(localJoystick.directionX, localJoystick.directionY, localJoystick.magnitude, deltaTime, currentLevel)
          
          val (nx, ny) = (currentLevel as MazeLevel).screenToMazeCoord(localPlayer.x, localPlayer.y)
          
          multiplayerManager?.updatePlayerState(nx, ny, localPlayer.ink, localPlayer.mode)
      }
      
      currentLevel?.update()
      
      // Update coverage statistics periodically for performance
      frameCount++
      if (frameCount >= coverageUpdateFrames) {
          frameCount = 0
          currentLevel?.let { level ->
              if (::surface.isInitialized) {
                  coverageStats = level.calculateCoverage(surface)
              } else {
                  Log.w(TAG, "Surface not initialized, skipping coverage calculation")
              }
          }
      }
      
      // Update HUD elements
      localPlayer?.let { 
          inkHudView?.updateHud(it.getInkPercent(), it.getModeText()) 
      }
      
      // Process game mode state and end conditions
      if (isMatchReady) {
          gameModeManager?.let { mgr ->
            timerHudView?.updateTime(mgr.timeRemainingMs())
            mgr.update()

            if (mgr.isFinished()) {
              if (!endNotified) {
                endNotified = true
                thread.running = false
                finishMatch("timer_expired")
              }
            } else {
              when (mgr.mode) {
                  GameMode.COVERAGE -> {
                      if (currentLevel is MazeLevel) {
                          try {
                              val allStats = (currentLevel as MazeLevel).calculateCoverage(surface)
                              val activeColors = players.values.map { it.getColor() }.toSet()
                              val activeStats = allStats.filterKeys { it in activeColors }

                              val leftColor = players["player0"]?.getColor()
                              val rightColor = players["player1"]?.getColor()

                              Handler(Looper.getMainLooper()).post {
                                  try {
                                      coverageHudView?.updateCoverage(activeStats, leftColor, rightColor)
                                      zoneHudView?.visibility = View.GONE
                                      coverageHudView?.visibility = View.VISIBLE
                                  } catch (e: Exception) {
                                      Log.e(TAG, "Error updating coverage HUD on main thread", e)
                                  }
                              }
                          } catch (e: Exception) {
                              Log.e(TAG, "Error calculating coverage", e)
                          }
                      }
                  }
                  GameMode.ZONES -> {
                      if (currentLevel is MazeLevel) {
                          try {
                              val zoneOwnership = ZoneOwnershipCalculator.calculateZoneOwnership(
                                  currentLevel as MazeLevel, 
                                  surface,
                                  sampleStep = 10
                              )

                              val leftColor = players["player0"]?.getColor()
                              val rightColor = players["player1"]?.getColor()

                              Handler(Looper.getMainLooper()).post {
                                  try {
                                      zoneHudView?.updateZones(zoneOwnership, leftColor, rightColor)
                                      coverageHudView?.visibility = View.GONE
                                      zoneHudView?.visibility = View.VISIBLE
                                  } catch (e: Exception) {
                                      Log.e(TAG, "Error updating zone HUD on main thread", e)
                                  }
                              }
                          } catch (e: Exception) {
                              Log.e(TAG, "Error calculating zone ownership", e)
                          }
                      }
                  }
              }
            }
          }
      }
  }
  
  /** 
   * Main rendering method called each frame.
   * Draws background, paint surface, level geometry, players, local joystick, and player names.
   */
  override fun draw(c:Canvas){
    // Draw center-cropped background image
    run {
      val bmp = bgBitmap
      val viewW = width.toFloat()
      val viewH = height.toFloat()
      val bmpW = bmp.width.toFloat()
      val bmpH = bmp.height.toFloat()
      val scale = maxOf(viewW / bmpW, viewH / bmpH)
      val scaledW = bmpW * scale
      val scaledH = bmpH * scale
      val left = (viewW - scaledW) / 2
      val top = (viewH - scaledH) / 2
      val dest = RectF(left, top, left + scaledW, top + scaledH)
      c.drawBitmap(bmp, null, dest, null)
    }
    
    if(::surface.isInitialized) {
        surface.drawTo(c)
    }
    
    currentLevel?.draw(c)

    for (player in players.values) {
        player.draw(c)
    }

    localPlayerId?.let { joysticks[it]?.draw(c) }

    drawCornerNames(c)
  }

  /** Draw the names of the players in the screen corners. */
  private fun drawCornerNames(canvas: Canvas) {
      val textPaint = Paint().apply {
          color = Color.BLACK // Changed to black for better visibility
          textSize = 24f * resources.displayMetrics.density // Adjusted size, made it density-dependent
          isAntiAlias = true
          typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) // Explicitly bold
          setShadowLayer(4f, 1f, 1f, Color.argb(160, 255, 255, 255)) // Subtle white shadow for pop
      }
      val horizontalMargin = 16f * resources.displayMetrics.density // Density-dependent margin
      val verticalMarginFromHud = 8f * resources.displayMetrics.density // Reduced margin below HUD for tighter spacing
      val topScreenMargin = 16f * resources.displayMetrics.density // Margin from top if HUD not visible

      // Determine Y position based on coverageHudView visibility and position
      val player0NameYBaseline: Float
      val coverageHud = coverageHudView
      // Use ascent for more precise top alignment (ascent is negative)
      if (coverageHud != null && coverageHud.visibility == View.VISIBLE && coverageHud.isLaidOut) {
          // Position top of text verticalMarginFromHud below the coverage HUD
          val player0TextTop = coverageHud.bottom.toFloat() + verticalMarginFromHud
          player0NameYBaseline = player0TextTop - textPaint.fontMetrics.ascent 
      } else {
          // Position top of text at topScreenMargin
          player0NameYBaseline = topScreenMargin - textPaint.fontMetrics.ascent
      }

      // Player 0 (Associated with top-left start)
      val player0Name = players["player0"]?.playerName?.takeIf { it.isNotEmpty() } ?: "Player 1"
      textPaint.textAlign = Paint.Align.LEFT
      // Set color for Player 0
      val player0Color = players["player0"]?.getColor() ?: Color.WHITE // Default to white if no player/color
      textPaint.color = player0Color
      // Adjust shadow color based on text color for better contrast
      if (isColorDark(player0Color)) {
          textPaint.setShadowLayer(4f, 1f, 1f, Color.argb(180, 255, 255, 255)) // Brighter shadow for dark text
      } else {
          textPaint.setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0)) // Darker shadow for light text
      }
      canvas.drawText(player0Name, horizontalMargin, player0NameYBaseline, textPaint)

      // Player 1 (Associated with bottom-right start, but name displayed top-right)
      val player1Name = players["player1"]?.playerName?.takeIf { it.isNotEmpty() } ?: "Player 2"
      textPaint.textAlign = Paint.Align.RIGHT
      // Set color for Player 1
      val player1Color = players["player1"]?.getColor() ?: Color.WHITE // Default to white
      textPaint.color = player1Color
      // Adjust shadow color
      if (isColorDark(player1Color)) {
          textPaint.setShadowLayer(4f, 1f, 1f, Color.argb(180, 255, 255, 255))
      } else {
          textPaint.setShadowLayer(4f, 1f, 1f, Color.argb(180, 0, 0, 0))
      }

      // Calculate Y position for Player 1's name
      // Start by aiming to align Player 1 with Player 0
      var player1NameYBaseline = player0NameYBaseline 

      val timerHud = timerHudView
      val minSpaceAboveTimer = 4f * resources.displayMetrics.density // Reduced minimum space needed above timer

      if (timerHud != null && timerHud.visibility == View.VISIBLE && timerHud.isLaidOut) {
          // Calculate where Player 1's text bottom would be if aligned with Player 0
          val p1TextBottomAtP0Level = player0NameYBaseline + textPaint.fontMetrics.descent
          
          // Calculate the safe boundary (Player 1's text bottom must be above this line)
          val maxSafeYForP1TextBottom = timerHud.top.toFloat() - minSpaceAboveTimer
          
          // Only move Player 1 up if it would actually overlap the timer with insufficient space
          if (p1TextBottomAtP0Level > maxSafeYForP1TextBottom) {
              // Calculate a safer baseline position that avoids the timer
              val timerSafeBaseline = maxSafeYForP1TextBottom - textPaint.fontMetrics.descent
              player1NameYBaseline = timerSafeBaseline
          }
          // If no conflict, Player 1 stays aligned with Player 0
      }
      
      canvas.drawText(player1Name, width - horizontalMargin, player1NameYBaseline, textPaint)
  }

  // Helper function to determine if a color is dark (for shadow adjustment)
  private fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
    return darkness >= 0.5 // Threshold for considering a color dark
  }
  
  /** 
   * Handles touch input for local player joystick control.
   * Processes only the primary pointer (finger 0) and routes events to the local player's joystick.
   */
  override fun onTouchEvent(e:MotionEvent):Boolean{
      if (localPlayerId == null) return false
      
      val pointerIndex = e.actionIndex
      val pointerId = e.getPointerId(pointerIndex)
      val action = e.actionMasked
      
      if (pointerId == 0) { 
          val joystick = joysticks[localPlayerId] ?: return false
          val x = e.getX(pointerIndex)
          val y = e.getY(pointerIndex)
          
          when (action) {
              MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> joystick.onDown(x, y)
              MotionEvent.ACTION_MOVE -> {
                  val moveX = e.getX(0)
                  val moveY = e.getY(0)
                  joystick.onMove(moveX, moveY)
              }
              MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> joystick.onUp()
              MotionEvent.ACTION_CANCEL -> joystick.onUp()
          }
          return true
      }
      
      return false
  }
  
  // --- Multiplayer Integration Methods --- 

  /** Registers the MultiplayerManager for Firebase state synchronization. */
  fun setMultiplayerManager(manager: MultiplayerManager) {
      this.multiplayerManager = manager
      this.multiplayerManager?.updateListener = this 
      Log.d(TAG, "MultiplayerManager set.")
  }

  /** 
   * Sets the local player ID and initializes their joystick and Player object.
   * Creates the player at the appropriate starting position based on player index.
   */
  fun setLocalPlayerId(id: String?, newPlayerColor: Int? = null, playerName: String = "") {
      Log.d(TAG, "setLocalPlayerId called with id: $id, color: $newPlayerColor, name: $playerName")
      this.localPlayerId = id
      if (id == null) return
      Log.d(TAG, "Local player ID set: $id")
      
      try {
          if (!joysticks.containsKey(id)) {
              joysticks[id] = VirtualJoystick()
          }
          
          if (currentLevel != null) {
               val playerIndex = try {
                   id.replace("player", "").toInt()
               } catch (e: Exception) {
                   Log.e(TAG, "Failed to parse player index from $id, using 0", e)
                   0
               }
               
               val startPos = currentLevel!!.getPlayerStartPosition(playerIndex)
               val defaultColor = if (playerIndex == 0) Color.parseColor("#39FF14") else Color.parseColor("#1F51FF")
               val finalPlayerColor = newPlayerColor ?: defaultColor
               
               var localPlayer = players[id]
               if (localPlayer == null) {
                   Log.d(TAG, "Creating local player $id at definite start position (${startPos.first}, ${startPos.second})")
                   localPlayer = Player(
                       surface,
                       startPos.first,
                       startPos.second,
                       finalPlayerColor,
                       multiplayerManager,
                       currentLevel,
                       playerName,
                       audioManager
                   )
                   players[id] = localPlayer
               } else {
                   Log.d(TAG, "Updating existing local player $id object to definite start position (${startPos.first}, ${startPos.second})")
                   localPlayer.x = startPos.first
                   localPlayer.y = startPos.second
                   localPlayer.playerName = playerName
               }
               
               if (currentLevel is MazeLevel) {
                   val (normX, normY) = (currentLevel as MazeLevel).screenToMazeCoord(localPlayer.x, localPlayer.y)
                   val initialPositionUpdate = mapOf(
                       "normX" to normX,
                       "normY" to normY
                   )
                   Log.d(TAG, "Updating Firebase with initial normalized position for $id: ($normX, $normY)")
                   multiplayerManager?.updateLocalPlayerPartialState(initialPositionUpdate)
               } else {
                   Log.w(TAG, "Could not update initial Firebase position for $id - currentLevel is not MazeLevel.")
               }
          } else {
              Log.w(TAG, "setLocalPlayerId called for $id but currentLevel is still null!")
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error in setLocalPlayerId for $id", e)
      }
  }

  /** Returns the local player instance if available. */
  fun getLocalPlayer(): Player? {
      return if (localPlayerId != null) players[localPlayerId] else null
  }

  // --- MultiplayerManager.RemoteUpdateListener Implementation --- 

  /** 
   * Processes a player state update from Firebase.
   * Handles coordinate conversion from normalized to screen space and manages player creation/updates.
   */
  private fun actuallyProcessPlayerState(playerId: String, newState: PlayerState) {
      if (currentLevel == null) {
          Log.e(TAG, "actuallyProcessPlayerState called with null currentLevel for $playerId")
          return
      }
      val mazeLevel = currentLevel as? MazeLevel
      if (mazeLevel == null) {
          Log.e(TAG, "MazeLevel not available for coordinate conversion in actuallyProcessPlayerState for $playerId")
          return
      }

      var player = players[playerId]
      if (player == null) {
          Log.i(TAG, "Adding new player via actuallyProcessPlayerState: $playerId")
          val (sx, sy) = mazeLevel.mazeToScreenCoord(newState.normX, newState.normY)
          player = Player(
              surface,
              sx,
              sy,
              newState.color,
              multiplayerManager,
              currentLevel,
              newState.playerName,
              audioManager
          )
          players[playerId] = player
      } else {
          if (playerId != localPlayerId) {
              val (sx, sy) = mazeLevel.mazeToScreenCoord(newState.normX, newState.normY)
              Log.d(TAG, "Updating remote player $playerId screen pos to ($sx, $sy) from norm (${newState.normX}, ${newState.normY}) via actuallyProcessPlayerState")
              player.x = sx
              player.y = sy
          }
      }
      
      player.mode = newState.mode
      player.ink = newState.ink
      if (playerId != localPlayerId) {
          player.playerName = newState.playerName
      }
  }

  /** Handles incoming player state changes from Firebase, managing pending states for early arrivals. */
  override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {
      if (!newState.active) {
          Log.i(TAG, "Player $playerId reported as inactive. Removing.")
          players[playerId]?.cleanup() // Stop any playing sounds
          players.remove(playerId)
          pendingPlayerStates.remove(playerId)
          joysticks.remove(playerId)
          return
      }

      if (currentLevel == null) {
          Log.w(TAG, "Caching pending player state for $playerId as currentLevel is null.")
          pendingPlayerStates[playerId] = newState
          return
      }

      Log.d(TAG, "Processing player state for $playerId as currentLevel is available.")
      pendingPlayerStates.remove(playerId)
      try {
          actuallyProcessPlayerState(playerId, newState)
      } catch (e: Exception) {
          Log.e(TAG, "Error in onPlayerStateChanged (direct processing) for $playerId", e)
      }
  }

  /** Handles player removal notifications from Firebase. */
  override fun onPlayerRemoved(playerId: String) {
      Log.i(TAG, "onPlayerRemoved: $playerId")
      players[playerId]?.cleanup() // Stop any playing sounds
      players.remove(playerId)
      joysticks.remove(playerId)
  }

  /** 
   * Handles remote paint actions from other players.
   * Converts normalized coordinates to local screen coordinates when available.
   */
  override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {
      try {
          if (currentLevel == null) {
              Log.w(TAG, "onPaintAction called but currentLevel is null. Paint action dropped.")
              return
          }
          if (normalizedX != null && normalizedY != null && currentLevel is MazeLevel) {
              val screenCoords = (currentLevel as MazeLevel).mazeToScreenCoord(normalizedX, normalizedY)
              Log.d(TAG, "Applying remote paint at normalized($normalizedX,$normalizedY) -> screen(${screenCoords.first},${screenCoords.second})")
              
              if (screenCoords.first >= 0 && screenCoords.first < surface.w.toFloat() &&
                  screenCoords.second >= 0 && screenCoords.second < surface.h.toFloat()) {
                  surface.paintAt(screenCoords.first, screenCoords.second, color)
              } else {
                  Log.w(TAG, "Remote paint coordinates out of bounds: (${screenCoords.first}, ${screenCoords.second})")
              }
          } else {
              if (x >= 0 && x < surface.w && y >= 0 && y < surface.h) {
                  Log.d(TAG, "Applying remote paint at ($x,$y) with color #${color.toString(16)}")
                  surface.paintAt(x.toFloat(), y.toFloat(), color)
              }
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error in onPaintAction", e)
      }
  }

  /** Returns the current level's scale factor for player size adjustments. */
  private fun getPlayerScaleFactor(): Float {
      return (currentLevel as? MazeLevel)?.getScale() ?: 1.0f
  }

  // --- Game Lifecycle Methods --- 
  
  /** Pauses the game thread without destroying state. */
  fun pause(){ 
    if (::thread.isInitialized) {
        thread.running=false 
    } else {
        Log.w(TAG, "pause() called but thread is not initialized.")
    }
  }
  
  /** Resumes the game thread, recreating if necessary. */
  fun resume(){ 
      if (::thread.isInitialized) {
      if (thread.state == Thread.State.TERMINATED) {
              Log.w(TAG, "Game thread terminated. Recreating for resume().")

              thread = GameThread(holder, this)
              thread.running = true
              try {
                  thread.start()
              } catch (e: Exception) {
                  Log.e(TAG, "Failed to restart game thread", e)
              }

          } else if (!thread.running) {
              Log.d(TAG, "Resuming game thread.")
              thread.running = true 
              if (!thread.isAlive) {
                  try {
                      thread.start()
                  } catch (e: IllegalThreadStateException) {
                       Log.e(TAG, "Failed to restart game thread on resume", e)
                  }
              }
          }
      } else {
          Log.w(TAG, "resume() called but thread is not initialized.")
      }
  }

  // --- HUD Management Methods ---

  fun setHudView(hudView: InkHudView) {
    this.inkHudView = hudView
  }
  
  fun setCoverageHudView(view: CoverageHudView) {
    this.coverageHudView = view
  }

  fun setZoneHudView(view: ZoneHudView) {
    this.zoneHudView = view
  }

  fun setTimerHudView(view: TimerHudView) {
    this.timerHudView = view
  }
  
  // --- Game State Query Methods ---
  
  fun getPlayerCoverage(): Float {
    return 0f
  }

  fun getCurrentLevel(): Level? {
      Log.v(TAG, "getCurrentLevel called. currentLevel is null: ${currentLevel == null}")
      return currentLevel
  }

  fun getActivePlayerIds(): Set<String> {
      Log.v(TAG, "getActivePlayerIds called. Player keys: ${players.keys}")
      return players.keys
  }

  // --- Game Initialization and Management ---

  /**
   * Initializes a new game instance with the specified maze complexity.
   * Creates the level, processes pending player states, and prepares a new game thread.
   * Should be called after multiplayer setup is complete and maze seed is available.
   */
  fun initGame(mazeComplexity: String = HomeActivity.COMPLEXITY_HIGH) {
    Log.d(TAG, "initGame called with mazeComplexity: $mazeComplexity")
    stopThread()
    
    if (!::surface.isInitialized) {
        Log.d(TAG, "Surface not initialized, creating new surface")
        surface = PaintSurface(width, height)
    }
    
    clearPaintSurface()
    
    val seed = multiplayerManager?.mazeSeed ?: System.currentTimeMillis()

    val hudHeight = coverageHudView?.height
        ?: coverageHudView?.layoutParams?.height
        ?: 0

    currentLevel = MazeLevel(width, height, 12, 20, 12f, seed, mazeComplexity, hudHeight)
    Log.d(TAG, "Created maze with viewport offset: ${(currentLevel as MazeLevel).getViewportOffset()}")
    
    players.clear()
    joysticks.clear()

    if (pendingPlayerStates.isNotEmpty()) {
        Log.d(TAG, "Processing ${pendingPlayerStates.size} pending player states in initGame.")
        for ((pId, state) in pendingPlayerStates.toMap()) {
            if (pId == this.localPlayerId) {
                Log.d(TAG, "Skipping pending state processing for local player $pId in initGame. Will be handled by setLocalPlayerId.")
                continue
            }
            if (state.active) {
                Log.d(TAG, "Attempting to process pending state for active player: $pId")
                try {
                    actuallyProcessPlayerState(pId, state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pending state for $pId in initGame", e)
                }
            } else {
                Log.w(TAG, "Skipping inactive pending player state for $pId in initGame.")
            }
        }
        pendingPlayerStates.clear()
    }
    
    isMatchReady = false
    thread = GameThread(holder, this)
    Log.d(TAG, "New GameThread instance created.")
    Log.d(TAG, "initGame: Created maze with seed: $seed, complexity: $mazeComplexity")
    Log.d(TAG, "initGame: Cleared previous game objects. Players: ${players.size}, Joysticks: ${joysticks.size}")
    Log.d(TAG, "initGame: Processed pending player states. Remaining: ${pendingPlayerStates.size}")
    Log.d(TAG, "initGame: New GameThread instance created. Thread state: ${thread.state}")
  }

  /** 
   * Starts the specified game mode with timer and initializes HUD states.
   * Sets up initial coverage/zone displays and marks the match as ready for updates.
   */
  fun startGameMode(mode: GameMode, durationMs: Long, startTime: Long? = null) {
    Log.d(TAG, "startGameMode called with mode: $mode, durationMs: $durationMs, startTime: $startTime")
    gameModeManager = if (startTime != null) {
        GameModeManager(mode, durationMs, startTime)
    } else {
        GameModeManager(mode, durationMs)
    }
    gameModeManager?.start()
    isMatchReady = true

    Handler(Looper.getMainLooper()).post {
        try {
            when (mode) {
                GameMode.COVERAGE -> {
                    coverageHudView?.visibility = View.VISIBLE
                    zoneHudView?.visibility = View.GONE
                    val initialStats = players.values.map { it.getColor() to 0f }.toMap()
                    val leftColor = players["player0"]?.getColor()
                    val rightColor = players["player1"]?.getColor()
                    coverageHudView?.updateCoverage(initialStats, leftColor, rightColor)
                }
                GameMode.ZONES -> {
                    zoneHudView?.visibility = View.VISIBLE
                    coverageHudView?.visibility = View.GONE
                    val leftColor = players["player0"]?.getColor()
                    val rightColor = players["player1"]?.getColor()
                    zoneHudView?.updateZones(emptyMap(), leftColor, rightColor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting initial HUD state in startGameMode", e)
        }
    }
  }

  /** Clears all painted areas and resets match end notification state. */
  fun clearPaintSurface() {
    if (::surface.isInitialized) surface.clear()
    endNotified = false
  }

  /** 
   * Calculates match winner and triggers completion callback.
   * Handles both Coverage and Zones game mode victory conditions.
   */
  private fun finishMatch(reason: String = "unknown") {
      Log.i(TAG, "finishMatch called. Reason: $reason")
      var didWin = false
      try {
          if (gameModeManager?.mode == GameMode.COVERAGE && currentLevel is MazeLevel) {
              val allStats = (currentLevel as MazeLevel).calculateCoverage(surface)
              val localColor = getLocalPlayer()?.getColor()
              val activeColors = players.values.map { it.getColor() }.toSet()
              val activeStats = allStats.filterKeys { it in activeColors }
              val localFrac = if (localColor != null) activeStats[localColor] ?: 0f else 0f
              val maxOther = activeStats.filterKeys { it != localColor }.values.maxOrNull() ?: 0f
              didWin = localFrac >= maxOther
          } else if (gameModeManager?.mode == GameMode.ZONES && currentLevel is MazeLevel) {
              val zoneOwnership = ZoneOwnershipCalculator.calculateZoneOwnership(
                  currentLevel as MazeLevel,
                  surface
              )
              val localColor = getLocalPlayer()?.getColor()
              
              var localZones = 0
              var otherZones = 0
              
              for (ownerColor in zoneOwnership.values) {
                  when (ownerColor) {
                      localColor -> localZones++
                      null -> {}
                      else -> otherZones++
                  }
              }
              
              didWin = localZones >= otherZones
              Log.i(TAG, "finishMatch: Zones mode - Local zones: $localZones, Other zones: $otherZones")
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error calculating winner in finishMatch", e)
      }
      Log.i(TAG, "finishMatch: didWin=$didWin, calling onMatchEnd")
      Handler(Looper.getMainLooper()).post {
          Log.i(TAG, "onMatchEnd invoked from finishMatch. didWin=$didWin")
          onMatchEnd?.invoke(didWin)
      }
  }
}

/**
 * Dedicated rendering thread for smooth game loop execution.
 * Handles frame timing, surface validation, and safe canvas operations
 * with delta time calculation for consistent frame-rate independent updates.
 */
class GameThread(private val sh:SurfaceHolder,private val gv:GameView):Thread(){
  var running=false
  private val TAG = "GameThread"
  private var lastTimeNanos: Long = System.nanoTime()

  override fun run(){ 
      Log.i(TAG, "run() started. Initial running state: $running")
      lastTimeNanos = System.nanoTime()
      try {
          while(running){ 
              if (!sh.surface.isValid) {
                  Log.w(TAG, "Surface is not valid, skipping frame.")
                  try { Thread.sleep(8) } catch (e: InterruptedException) { /* ignore */ }
                  continue
              }
              val currentTimeNanos = System.nanoTime()
              val deltaTimeSeconds = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
              lastTimeNanos = currentTimeNanos
              var c: Canvas? = null
              try {
                  c = sh.lockCanvas()
                  if(c!=null){ 
                      try {
                          gv.update(deltaTimeSeconds)
                      } catch (e: Exception) {
                          Log.e(TAG, "Exception in GameView.update()", e)
                      }
                      try {
                          gv.draw(c)
                      } catch (e: Exception) {
                          Log.e(TAG, "Exception in GameView.draw()", e)
                      }
                  } else {
                      Log.w(TAG, "lockCanvas() returned null. Surface may not be ready.")
                      try { Thread.sleep(16) } catch (e: InterruptedException) { /* ignore */ }
                  }
              } catch (e: Exception) {
                  Log.e(TAG, "Exception in GameThread run loop (lockCanvas or unlock)", e)
              } finally {
                  if (c != null) {
                      if (sh.surface.isValid) {
                          try {
                              sh.unlockCanvasAndPost(c)
                          } catch (e: Exception) {
                              Log.e(TAG, "Exception in unlockCanvasAndPost", e)
                          }
                      } else {
                          Log.w(TAG, "Surface became invalid before unlockCanvasAndPost. Skipping post.")
                      }
                  }
              } 
          } 
      } catch (e: Exception) {
          Log.e(TAG, "Exception in GameThread main loop", e)
      }
      Log.i(TAG, "run() finished. Final running state: $running")
  }
}
