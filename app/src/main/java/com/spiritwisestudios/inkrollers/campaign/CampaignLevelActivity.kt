package com.spiritwisestudios.inkrollers.campaign

import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.*
import com.spiritwisestudios.inkrollers.databinding.ActivityCampaignLevelBinding
import com.spiritwisestudios.inkrollers.R
import com.spiritwisestudios.inkrollers.items.ItemConfig
import com.spiritwisestudios.inkrollers.items.ItemType

class CampaignLevelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCampaignLevelBinding
    private lateinit var gameView: GameView
    private lateinit var inkHudView: InkHudView
    private lateinit var coverageHudView: CoverageHudView
    private lateinit var timerHudView: TimerHudView
    private lateinit var audioManager: AudioManager
    private lateinit var campaignManager: CampaignManager
    
    private var currentLevelId: String? = null
    private var campaignLevel: CampaignLevel? = null
    private var localPlayer: Player? = null
    private var gameModeManager: GameModeManager? = null
    
    // Progress tracking
    private var lastProgressUpdate = 0L
    private val progressUpdateInterval = 500L // Update every 500ms
    
    // Level completion tracking
    private var levelStartTime = 0L
    private var levelCompleted = false
    
    // Performance monitoring
    private val performanceMonitor = PerformanceMonitor()

    private var timerUpdateHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null

    companion object {
        private const val TAG = "CampaignLevelActivity"
        const val EXTRA_LEVEL_ID = "com.spiritwisestudios.inkrollers.CAMPAIGN_LEVEL_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCampaignLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable full screen immersive mode
        enableFullScreenMode()
        
        // Override activity transitions
        overridePendingTransition(R.anim.campaign_enter, R.anim.campaign_exit)

        try {
            // Initialize managers
            audioManager = AudioManager.getInstance(this)
            audioManager.initialize()
            campaignManager = CampaignManager.getInstance(this)

            // Get level ID from intent
            currentLevelId = intent.getStringExtra(EXTRA_LEVEL_ID)
            if (currentLevelId == null) {
                Log.e(TAG, "No level ID provided in intent")
                Toast.makeText(this, "Error: No level ID provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            setupUI()
            initializeCampaignLevel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during campaign level activity creation", e)
            Toast.makeText(this, "Error initializing campaign level: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupUI() {
        gameView = binding.gameView
        inkHudView = binding.inkHudView
        coverageHudView = binding.coverageHudView
        timerHudView = binding.timerHudView

        // Hide coverage HUD in campaign mode (not needed)
        coverageHudView.visibility = android.view.View.GONE

        // Connect HUDs to GameView
        gameView.setHudView(inkHudView)
        gameView.setTimerHudView(timerHudView)

        // Setup color shift button
        binding.buttonColorShift.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.COLOR_SHIFT)
            localPlayer?.toggleColorShift()
            updateFrequencyDisplay()
            
            // Trigger color shift effect
            campaignLevel?.getCampaignEffects()?.triggerColorShiftEffect(localPlayer!!)
        }
        
        // Setup mode toggle button
        // Initialize button appearance for default PAINT mode (hold to refill)
        binding.buttonModeToggle.setTextColor(android.graphics.Color.WHITE)
        binding.buttonModeToggle.setBackgroundColor(android.graphics.Color.parseColor("#2196F3")) // Blue indicates paint mode active
        binding.buttonModeToggle.text = "REFILL"
        
        binding.buttonModeToggle.setOnTouchListener { view, event ->
            val localPlayer = gameView.getLocalPlayer()
            Log.d(TAG, "Campaign button touch event: ${event.action} / ${event.actionMasked}")
            Log.d(TAG, "Local player available: ${localPlayer != null}")
            if (localPlayer != null) {
                Log.d(TAG, "Current player mode: ${localPlayer.mode}")
            }
            
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "REFILL button pressed - switching to FILL mode")
                    if (localPlayer != null) {
                        Log.d(TAG, "Calling changeModeIfNeeded(1) on local player")
                        localPlayer.changeModeIfNeeded(1)
                        binding.buttonModeToggle.setBackgroundColor(android.graphics.Color.parseColor("#FF9800"))
                        binding.buttonModeToggle.text = "REFILLING"
                        updateModeDisplay()
                        // Add haptic feedback
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    } else {
                        Log.w(TAG, "Cannot switch to FILL mode - local player is null")
                    }
                    return@setOnTouchListener true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "REFILL button released - switching to PAINT mode")
                    if (localPlayer != null) {
                        Log.d(TAG, "Calling changeModeIfNeeded(0) on local player")
                        localPlayer.changeModeIfNeeded(0)
                        binding.buttonModeToggle.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                        binding.buttonModeToggle.text = "REFILL"
                        updateModeDisplay()
                    } else {
                        Log.w(TAG, "Cannot switch to PAINT mode - local player is null")
                    }
                    return@setOnTouchListener true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "REFILL button cancelled - switching to PAINT mode")
                    if (localPlayer != null) {
                        Log.d(TAG, "Calling changeModeIfNeeded(0) on local player (cancel)")
                        localPlayer.changeModeIfNeeded(0)
                        binding.buttonModeToggle.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                        binding.buttonModeToggle.text = "REFILL"
                        updateModeDisplay()
                    } else {
                        Log.w(TAG, "Cannot switch to PAINT mode - local player is null (cancel)")
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Setup back button
        binding.buttonBack.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            finish()
            // Use custom transition
            overridePendingTransition(R.anim.campaign_enter, R.anim.campaign_exit)
        }
    }

    private fun initializeCampaignLevel() {
        val levelData = campaignManager.getLevelData(currentLevelId!!)
        if (levelData == null) {
            Toast.makeText(this, "Error: Level data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Wait for the game view to be laid out before initializing the campaign level
        gameView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove the listener to avoid multiple calls
                gameView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Check if we have valid dimensions
                if (gameView.width <= 0 || gameView.height <= 0) {
                    Log.w(TAG, "GameView has invalid dimensions: ${gameView.width}x${gameView.height}")
                    Toast.makeText(this@CampaignLevelActivity, "Error: Invalid view dimensions", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                Log.d(TAG, "GameView dimensions: ${gameView.width}x${gameView.height}")
                
                // Create campaign level with proper dimensions
                campaignLevel = CampaignLevel(
                    screenW = gameView.width,
                    screenH = gameView.height,
                    levelData = levelData,
                    topMargin = coverageHudView.height,
                    audioManager = audioManager
                )

                        // Initialize game view for campaign mode
        gameView.setCampaignMode(true)
        gameView.setCampaignLevel(campaignLevel!!)
        gameView.setCampaignPerformanceMonitor(performanceMonitor)
        
        // Set up item configuration for campaign mode
        setupCampaignItemConfiguration(levelData)

                // Wait for surface to be ready before proceeding
                initializeWithSurface(levelData)
            }
        })
    }
    
    private fun initializeWithSurface(levelData: CampaignLevelData) {
        // Check if surface is ready, if not, wait a bit and try again
        val paintSurface = gameView.getPaintSurface()
        if (paintSurface != null) {
            proceedWithCampaignInitialization(levelData, paintSurface)
        } else {
            Log.d(TAG, "Paint surface not ready yet, waiting...")
            // Wait for surface to be created, then try again
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val retryPaintSurface = gameView.getPaintSurface()
                if (retryPaintSurface != null) {
                    proceedWithCampaignInitialization(levelData, retryPaintSurface)
                } else {
                    Log.e(TAG, "Paint surface still not available after waiting")
                    Toast.makeText(this, "Error: Could not initialize paint surface", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, 100) // Wait 100ms for surface creation
        }
    }
    
    private fun proceedWithCampaignInitialization(levelData: CampaignLevelData, paintSurface: PaintSurface) {
        Log.d(TAG, "Proceeding with campaign initialization with available paint surface")
        
        // Create local player for campaign
        val startPosition = campaignLevel!!.getPlayerStartPosition(0)
        localPlayer = Player(
            surface = paintSurface,
            startX = startPosition.first,
            startY = startPosition.second,
            playerColor = android.graphics.Color.RED, // Will be overridden by frequency system
            level = campaignLevel,
            playerName = "Player",
            audioManager = audioManager,
            particleManager = gameView.getParticleManager(),
            isCampaignMode = true
        )
        
        // Initialize the campaign game
        Log.d(TAG, "About to call initCampaignGame for level: $currentLevelId")
        gameView.initCampaignGame(currentLevelId!!)
        Log.d(TAG, "initCampaignGame completed")
        
        // Set up the campaign player in GameView so it gets rendered and controlled
        gameView.setCampaignPlayer(localPlayer!!)
        Log.d(TAG, "Campaign player set up in GameView")
        
        // Connect the paint surface to the campaign level
        campaignLevel!!.setPaintSurface(paintSurface)
        Log.d(TAG, "Paint surface connected to campaign level")
        
        // Set up game mode manager
        levelData.timeLimit?.let { timeLimit ->
            gameModeManager = GameModeManager(GameMode.COVERAGE, timeLimit)
            gameModeManager!!.start()
            gameView.setGameModeManager(gameModeManager!!)
        }

        // Start campaign music (now with better error handling)
        audioManager.startCampaignMusic()

        // Wait a brief moment for thread initialization before starting game loop
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            gameView.startGameLoop()
        }, 50)

        // Start progress updates
        startProgressUpdates()
        // Start timer updates
        startTimerUpdates(levelData)
        
        // Record level start time
        levelStartTime = System.currentTimeMillis()
        
        // Mark level as started in campaign manager
        campaignManager.startLevel(currentLevelId!!)

        // Update objectives display
        updateObjectivesDisplay(levelData)
        
        // Update initial UI displays
        updateFrequencyDisplay()
        updateModeDisplay()

        Log.d(TAG, "Campaign level initialized: ${levelData.levelName}")
    }

    private fun startTimerUpdates(levelData: CampaignLevelData) {
        timerUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                updateTimerDisplay(levelData)
                timerUpdateHandler?.postDelayed(this, 250)
            }
        }
        timerUpdateHandler?.post(timerRunnable!!)
    }

    private fun updateTimerDisplay(levelData: CampaignLevelData) {
        if (levelData.timeLimit == null) {
            // No time limit: show ∞
            timerHudView.updateTime(-1L)
        } else {
            // Use gameModeManager's time remaining if available
            val ms = gameModeManager?.timeRemainingMs() ?: levelData.timeLimit
            timerHudView.updateTime(ms)
        }
    }
    
    private fun startProgressUpdates() {
        // Create a handler to update progress periodically
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                updateProgressDisplay()
                updateInkHudDisplay()
                handler.postDelayed(this, progressUpdateInterval)
            }
        }
        handler.post(progressRunnable)
    }

    private fun updateInkHudDisplay() {
        localPlayer?.let { player ->
            inkHudView.updateHud(player.getInkPercent(), player.getModeText())
        }
    }

    private fun updateFrequencyDisplay() {
        localPlayer?.let { player ->
            val frequency = player.getCurrentFrequency()
            val frequencyColor = when (frequency) {
                ColorFrequency.RED -> "#FF4444"
                ColorFrequency.BLUE -> "#4444FF"
                ColorFrequency.GREEN -> "#44FF44"
                ColorFrequency.YELLOW -> "#FFFF44"
            }
            binding.textFrequency.text = "Frequency: $frequency"
            binding.textFrequency.setTextColor(android.graphics.Color.parseColor(frequencyColor))
        }
    }
    
    private fun updateModeDisplay() {
        localPlayer?.let { player ->
            binding.buttonModeToggle.text = player.getModeText()
        }
    }
    
    private fun updateObjectivesDisplay(levelData: CampaignLevelData) {
        // Update level name
        binding.textLevelName.text = levelData.levelName
        
        // Special objectives for tutorial level (level_1)
        if (levelData.levelId == "level_1") {
            // Tutorial objectives: Ink door activator and reach exit
            binding.textCoverageObjective.text = "• Ink door activator"
            binding.textCoverageObjective.visibility = android.view.View.VISIBLE
            
            binding.textRobotsObjective.text = "• Make it to the end of maze"
            binding.textRobotsObjective.visibility = android.view.View.VISIBLE
            
            // Hide secrets objective for tutorial
            binding.textSecretsObjective.visibility = android.view.View.GONE
        } else {
            // Standard objectives for other levels
            
            // Update coverage objective
            val coveragePercent = (levelData.requiredCoverage * 100).toInt()
            binding.textCoverageObjective.text = "• Paint $coveragePercent% of the area"
            binding.textCoverageObjective.visibility = android.view.View.VISIBLE
            
            // Update robots objective
            val robotCount = levelData.robotPositions.size
            if (robotCount > 0) {
                binding.textRobotsObjective.text = "• Convert $robotCount robot${if (robotCount != 1) "s" else ""}"
                binding.textRobotsObjective.visibility = android.view.View.VISIBLE
            } else {
                binding.textRobotsObjective.visibility = android.view.View.GONE
            }
            
            // Update door activators objective (replaces secrets)
            val doorCount = levelData.doorActivators.size
            if (doorCount > 0) {
                binding.textSecretsObjective.text = "• Activate $doorCount door${if (doorCount != 1) "s" else ""}"
                binding.textSecretsObjective.visibility = android.view.View.VISIBLE
            } else {
                binding.textSecretsObjective.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun updateProgressDisplay() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressUpdate < progressUpdateInterval) return
        
        lastProgressUpdate = currentTime
        
        campaignLevel?.let { level ->
            val paintSurface = gameView.getPaintSurface()
            if (paintSurface != null) {
                val coverage = level.calculateCoverage(paintSurface)
                val totalCoverage = coverage.values.sum()
                val progress = (totalCoverage * 100).toInt().coerceIn(0, 100)
                
                binding.textLevelProgress.text = "Progress: $progress%"
                
                // Update progress color based on completion
                val progressColor = when {
                    progress >= 90 -> android.graphics.Color.GREEN
                    progress >= 70 -> android.graphics.Color.YELLOW
                    progress >= 50 -> android.graphics.Color.rgb(255, 165, 0) // Orange
                    else -> android.graphics.Color.RED
                }
                binding.textLevelProgress.setTextColor(progressColor)
                
                // Update objectives with completion status
                updateObjectiveCompletion(level, totalCoverage)
            }
        }
    }
    
    private fun updateObjectiveCompletion(level: CampaignLevel, totalCoverage: Float) {
        val levelData = level.getLevelData()
        val gradingStats = level.getGradingStats()
        
        // Special completion logic for tutorial level (level_1)
        if (levelData.levelId == "level_1") {
            val doorsActivated = gradingStats["doorsActivated"] as? Int ?: 0
            val totalDoors = gradingStats["totalDoors"] as? Int ?: 0
            val reachedExit = gradingStats["reachedExit"] as? Boolean ?: false
            
            // Update door activator objective
            val doorComplete = doorsActivated >= totalDoors
            val doorCheckmark = if (doorComplete) "✓" else "•"
            val doorColor = if (doorComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            binding.textCoverageObjective.text = "$doorCheckmark Ink door activator"
            binding.textCoverageObjective.setTextColor(doorColor)
            
            // Update exit objective
            val exitCheckmark = if (reachedExit) "✓" else "•"
            val exitColor = if (reachedExit) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            binding.textRobotsObjective.text = "$exitCheckmark Make it to the end of maze"
            binding.textRobotsObjective.setTextColor(exitColor)
            
            // Check for level completion (tutorial only needs exit)
            if (!levelCompleted && reachedExit) {
                handleLevelCompletion()
            }
        } else {
            // Standard completion logic for other levels
            val requiredCoverage = levelData.requiredCoverage
            val robotsConverted = gradingStats["robotsConverted"] as? Int ?: 0
            val totalRobots = gradingStats["totalRobots"] as? Int ?: 0
            val doorsActivated = gradingStats["doorsActivated"] as? Int ?: 0
            val totalDoors = gradingStats["totalDoors"] as? Int ?: 0
            val reachedExit = gradingStats["reachedExit"] as? Boolean ?: false
            
            // Update coverage objective
            val coveragePercent = (requiredCoverage * 100).toInt()
            val currentPercent = (totalCoverage * 100).toInt()
            val coverageComplete = totalCoverage >= requiredCoverage
            val coverageCheckmark = if (coverageComplete) "✓" else "•"
            val coverageColor = if (coverageComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            binding.textCoverageObjective.text = "$coverageCheckmark Paint $coveragePercent% of the area ($currentPercent%)"
            binding.textCoverageObjective.setTextColor(coverageColor)
            
            // Update robots objective
            if (totalRobots > 0) {
                val robotsComplete = robotsConverted >= totalRobots
                val robotsCheckmark = if (robotsComplete) "✓" else "•"
                val robotsColor = if (robotsComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
                binding.textRobotsObjective.text = "$robotsCheckmark Convert $totalRobots robot${if (totalRobots != 1) "s" else ""} ($robotsConverted/$totalRobots)"
                binding.textRobotsObjective.setTextColor(robotsColor)
            }
            
            // Update doors objective (replaces secrets)
            if (totalDoors > 0) {
                val doorsComplete = doorsActivated >= totalDoors
                val doorsCheckmark = if (doorsComplete) "✓" else "•"
                val doorsColor = if (doorsComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
                binding.textSecretsObjective.text = "$doorsCheckmark Activate $totalDoors door${if (totalDoors != 1) "s" else ""} ($doorsActivated/$totalDoors)"
                binding.textSecretsObjective.setTextColor(doorsColor)
            }
            
            // Check for level completion (coverage + exit for other levels)
            if (!levelCompleted && totalCoverage >= requiredCoverage && reachedExit) {
                handleLevelCompletion()
            }
        }
    }
    
    private fun handleLevelCompletion() {
        if (levelCompleted) return
        
        levelCompleted = true
        val timeTaken = System.currentTimeMillis() - levelStartTime
        
        // Get grading statistics
        val gradingStats = campaignLevel?.getGradingStats() ?: emptyMap()
        val robotsConverted = gradingStats["robotsConverted"] as? Int ?: 0
        val totalRobots = gradingStats["totalRobots"] as? Int ?: 0
        val doorsActivated = gradingStats["doorsActivated"] as? Int ?: 0
        val totalDoors = gradingStats["totalDoors"] as? Int ?: 0
        
        // Calculate ink used (simplified - could be tracked more accurately)
        val inkUsed = 500f // Placeholder value
        
        // Calculate grade
        val levelData = campaignLevel?.getLevelData()
        val grade = if (levelData != null) {
            LevelGrading.calculateGrade(
                timeTaken = timeTaken,
                inkUsed = inkUsed,
                robotsConverted = robotsConverted,
                totalRobots = totalRobots,
                secretsFound = doorsActivated, // Use doors activated instead of secrets
                totalSecrets = totalDoors, // Use total doors instead of total secrets
                levelData = levelData
            )
        } else {
            LevelGrading.calculateBasicGrade(timeTaken, levelData ?: CampaignLevels.LEVEL_1)
        }
        
        // Save level completion
        campaignManager.completeLevel(currentLevelId!!, grade)
        
        // Show completion dialog
        showLevelCompletionDialog(grade, timeTaken, robotsConverted, totalRobots, doorsActivated, totalDoors)
        
        Log.d(TAG, "Level completed with grade: ${grade.grade}")
    }
    
    private fun showLevelCompletionDialog(
        grade: CampaignManager.LevelGrade,
        timeTaken: Long,
        robotsConverted: Int,
        totalRobots: Int,
        doorsActivated: Int,
        totalDoors: Int
    ) {
        val timeString = String.format("%02d:%02d", timeTaken / 60000, (timeTaken % 60000) / 1000)
        
        val message = if (currentLevelId == "level_1") {
            // Tutorial completion message
            """
            Tutorial Complete!
            
            Grade: ${grade.grade}
            Score: ${grade.score}
            
            Time: $timeString
            Doors Activated: $doorsActivated/$totalDoors
            
            You've learned the basics!
            Try the next level for more challenges.
            """.trimIndent()
        } else {
            // Standard completion message
            """
            Level Complete!
            
            Grade: ${grade.grade}
            Score: ${grade.score}
            
            Time: $timeString
            Robots Converted: $robotsConverted/$totalRobots
            Doors Activated: $doorsActivated/$totalDoors
            
            Time Bonus: ${grade.timeBonus}
            Efficiency Bonus: ${grade.efficiencyBonus}
            Robot Bonus: ${grade.robotBonus}
            Door Bonus: ${grade.secretsBonus}
            """.trimIndent()
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Mission Accomplished!")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                finish()
                overridePendingTransition(R.anim.campaign_enter, R.anim.campaign_exit)
            }
            .setCancelable(false)
            .show()
    }

    private fun setupCampaignItemConfiguration(levelData: CampaignLevelData) {
        // Create item configuration based on campaign level
        val itemConfig = when (levelData.levelId) {
            "level_1" -> {
                // Level 1: Basic ink refills only
                ItemConfig.createWithItems(listOf(ItemType.INK_REFILL))
            }
            "level_2" -> {
                // Level 2: Ink refills + speed boost
                ItemConfig.createWithItems(listOf(
                    ItemType.INK_REFILL,
                    ItemType.SPEED_BOOST
                ))
            }
            "level_3", "level_4a", "level_4b" -> {
                // Later levels: More item types
                ItemConfig.createWithItems(listOf(
                    ItemType.INK_REFILL,
                    ItemType.SPEED_BOOST,
                    ItemType.PAINT_MULTIPLIER,
                    ItemType.SHIELD,
                    ItemType.FREEZE
                    // Note: Teleport excluded for campaign balance
                ))
            }
            else -> {
                // Default: Ink refills only
                ItemConfig.createWithItems(listOf(ItemType.INK_REFILL))
            }
        }
        
        // Apply configuration to game view
        gameView.setItemConfig(itemConfig)
        Log.d(TAG, "Set up item configuration for ${levelData.levelId}: ${itemConfig.getEnabledItems()}")
    }

    private fun enableFullScreenMode() {
        // Allow content to extend into display cutout areas on Android P and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            audioManager.pauseAudio()
            gameView.stopThread()
        } catch (e: Exception) {
            Log.e(TAG, "Error during onPause", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            audioManager.resumeAudio()
            // Only restart game loop if campaign was properly initialized
            if (levelCompleted || localPlayer == null) {
                Log.d(TAG, "Skipping game loop restart - level completed or not initialized")
            } else {
                gameView.startGameLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during onResume", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioManager.stopCampaignMusic()
            gameView.stopThread()
            timerUpdateHandler?.removeCallbacksAndMessages(null)
            // Note: Don't call audioManager.release() here as it's a singleton
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy", e)
        }
    }
} 