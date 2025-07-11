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

    companion object {
        private const val TAG = "CampaignLevelActivity"
        const val EXTRA_LEVEL_ID = "com.spiritwisestudios.inklers.CAMPAIGN_LEVEL_ID"
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

        // Setup color shift button
        binding.buttonColorShift.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.COLOR_SHIFT)
            localPlayer?.toggleColorShift()
            updateFrequencyDisplay()
            
            // Trigger color shift effect
            campaignLevel?.getCampaignEffects()?.triggerColorShiftEffect(localPlayer!!)
        }
        
        // Setup mode toggle button
        binding.buttonModeToggle.setOnClickListener {
            localPlayer?.toggleMode()
            updateModeDisplay()
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
            Log.d(TAG, "About to call startGameLoop")
            gameView.startGameLoop()
            Log.d(TAG, "startGameLoop completed")
        }, 50) // Small delay to ensure thread is ready
        
        // Start progress updates
        startProgressUpdates()
        
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
    
    private fun startProgressUpdates() {
        // Create a handler to update progress periodically
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                updateProgressDisplay()
                handler.postDelayed(this, progressUpdateInterval)
            }
        }
        handler.post(progressRunnable)
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
        binding.textLevelName.text = "Mission: ${levelData.levelName}"
        
        // Update coverage objective
        val coveragePercent = (levelData.requiredCoverage * 100).toInt()
        binding.textCoverageObjective.text = "• Paint $coveragePercent% of the area"
        
        // Update robots objective
        val robotCount = levelData.robotPositions.size
        if (robotCount > 0) {
            binding.textRobotsObjective.text = "• Convert $robotCount robot${if (robotCount != 1) "s" else ""}"
            binding.textRobotsObjective.visibility = android.view.View.VISIBLE
        } else {
            binding.textRobotsObjective.visibility = android.view.View.GONE
        }
        
        // Update secrets objective
        val secretCount = levelData.secretAreas.size
        if (secretCount > 0) {
            binding.textSecretsObjective.text = "• Find $secretCount secret area${if (secretCount != 1) "s" else ""}"
            binding.textSecretsObjective.visibility = android.view.View.VISIBLE
        } else {
            binding.textSecretsObjective.visibility = android.view.View.GONE
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
                
                // Check for level completion
                if (!levelCompleted && totalCoverage >= level.getRequiredCoverage()) {
                    handleLevelCompletion()
                }
            }
        }
    }
    
    private fun updateObjectiveCompletion(level: CampaignLevel, totalCoverage: Float) {
        val levelData = level.getLevelData()
        val requiredCoverage = levelData.requiredCoverage
        val gradingStats = level.getGradingStats()
        
        // Update coverage objective
        val coveragePercent = (requiredCoverage * 100).toInt()
        val currentPercent = (totalCoverage * 100).toInt()
        val coverageComplete = totalCoverage >= requiredCoverage
        val coverageCheckmark = if (coverageComplete) "✓" else "•"
        val coverageColor = if (coverageComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
        binding.textCoverageObjective.text = "$coverageCheckmark Paint $coveragePercent% of the area ($currentPercent%)"
        binding.textCoverageObjective.setTextColor(coverageColor)
        
        // Update robots objective
        val robotsConverted = gradingStats["robotsConverted"] as? Int ?: 0
        val totalRobots = gradingStats["totalRobots"] as? Int ?: 0
        if (totalRobots > 0) {
            val robotsComplete = robotsConverted >= totalRobots
            val robotsCheckmark = if (robotsComplete) "✓" else "•"
            val robotsColor = if (robotsComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            binding.textRobotsObjective.text = "$robotsCheckmark Convert $totalRobots robot${if (totalRobots != 1) "s" else ""} ($robotsConverted/$totalRobots)"
            binding.textRobotsObjective.setTextColor(robotsColor)
        }
        
        // Update secrets objective
        val secretsFound = gradingStats["secretsFound"] as? Int ?: 0
        val totalSecrets = gradingStats["totalSecrets"] as? Int ?: 0
        if (totalSecrets > 0) {
            val secretsComplete = secretsFound >= totalSecrets
            val secretsCheckmark = if (secretsComplete) "✓" else "•"
            val secretsColor = if (secretsComplete) android.graphics.Color.GREEN else android.graphics.Color.WHITE
            binding.textSecretsObjective.text = "$secretsCheckmark Find $totalSecrets secret area${if (totalSecrets != 1) "s" else ""} ($secretsFound/$totalSecrets)"
            binding.textSecretsObjective.setTextColor(secretsColor)
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
        val secretsFound = gradingStats["secretsFound"] as? Int ?: 0
        val totalSecrets = gradingStats["totalSecrets"] as? Int ?: 0
        
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
                secretsFound = secretsFound,
                totalSecrets = totalSecrets,
                levelData = levelData
            )
        } else {
            LevelGrading.calculateBasicGrade(timeTaken, levelData ?: CampaignLevels.LEVEL_1)
        }
        
        // Save level completion
        campaignManager.completeLevel(currentLevelId!!, grade)
        
        // Show completion dialog
        showLevelCompletionDialog(grade, timeTaken, robotsConverted, totalRobots, secretsFound, totalSecrets)
        
        Log.d(TAG, "Level completed with grade: ${grade.grade}")
    }
    
    private fun showLevelCompletionDialog(
        grade: CampaignManager.LevelGrade,
        timeTaken: Long,
        robotsConverted: Int,
        totalRobots: Int,
        secretsFound: Int,
        totalSecrets: Int
    ) {
        val timeString = String.format("%02d:%02d", timeTaken / 60000, (timeTaken % 60000) / 1000)
        
        val message = """
            Level Complete!
            
            Grade: ${grade.grade}
            Score: ${grade.score}
            
            Time: $timeString
            Robots Converted: $robotsConverted/$totalRobots
            Secrets Found: $secretsFound/$totalSecrets
            
            Time Bonus: ${grade.timeBonus}
            Efficiency Bonus: ${grade.efficiencyBonus}
            Robot Bonus: ${grade.robotBonus}
            Secrets Bonus: ${grade.secretsBonus}
        """.trimIndent()
        
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

    private fun enableFullScreenMode() {
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
            // Note: Don't call audioManager.release() here as it's a singleton
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy", e)
        }
    }
} 