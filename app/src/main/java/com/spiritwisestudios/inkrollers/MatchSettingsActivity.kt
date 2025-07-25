package com.spiritwisestudios.inkrollers

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.databinding.ActivityMatchSettingsBinding

class MatchSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchSettingsBinding
    private lateinit var audioManager: AudioManager

    // Settings values
    private var selectedTimeLimit = 3
    private var selectedComplexity = HomeActivity.COMPLEXITY_LOW
    private var selectedGameMode = HomeActivity.GAME_MODE_COVERAGE
    private var selectedRobotSpawners = 0
    private var isPrivateMatch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable full screen immersive mode
        enableFullScreenMode()

        // Initialize AudioManager
        audioManager = AudioManager.getInstance(this)

        setupResponsiveTextSizes()
        setupDropdowns()
        setupButtons()
        setupHelpButtons()
    }

    private fun setupResponsiveTextSizes() {
        val displayMetrics = resources.displayMetrics
        val densityDpi = displayMetrics.densityDpi
        
        // Calculate scaling factor based on device density
        // Target: make text appear same physical size regardless of screen density
        val scalingFactor = when {
            densityDpi <= 160 -> 1.4f  // MDPI and below
            densityDpi <= 240 -> 1.2f  // HDPI 
            densityDpi <= 320 -> 1.0f  // XHDPI (baseline)
            densityDpi <= 480 -> 0.85f // XXHDPI
            else -> 0.7f               // XXXHDPI and above
        }
        
        // Apply scaling to all text views
        scaleTextView(binding.infoTitle, 18f, scalingFactor)
        scaleTextView(binding.infoContent, 14f, scalingFactor)
        
        // Find and scale all TextViews in the settings card
        scaleTextViewsInCard(scalingFactor)
    }
    
    private fun scaleTextView(textView: TextView, baseSize: Float, scalingFactor: Float) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize * scalingFactor)
    }
    
    private fun scaleTextViewsInCard(scalingFactor: Float) {
        // Scale help buttons
        scaleTextView(findViewById<TextView>(R.id.help_time_limit), 14f, scalingFactor)
        scaleTextView(findViewById<TextView>(R.id.help_complexity), 14f, scalingFactor)
        scaleTextView(findViewById<TextView>(R.id.help_game_mode), 14f, scalingFactor)
        scaleTextView(findViewById<TextView>(R.id.help_spawners), 14f, scalingFactor)
        scaleTextView(findViewById<TextView>(R.id.help_private), 14f, scalingFactor)
        
        // Scale all other text views recursively
        scaleTextViewsRecursively(binding.root, scalingFactor)
    }
    
    private fun scaleTextViewsRecursively(view: View, scalingFactor: Float) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                scaleTextViewsRecursively(child, scalingFactor)
            }
        } else if (view is TextView) {
            val text = view.text?.toString() ?: ""
            when {
                text == "Match Settings" -> scaleTextView(view, 22f, scalingFactor)
                text.contains(":") || text == "Private" -> scaleTextView(view, 16f, scalingFactor)
                view.id in listOf(R.id.help_time_limit, R.id.help_complexity, R.id.help_game_mode, R.id.help_spawners, R.id.help_private) -> {
                    // Already handled above
                }
            }
        }
    }

    private fun setupDropdowns() {
        // Time Limit Dropdown
        val timeOptions = arrayOf("3 minutes", "5 minutes", "7 minutes")
        val timeValues = intArrayOf(3, 5, 7)
        val timeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeLimit.adapter = timeAdapter
        binding.spinnerTimeLimit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTimeLimit = timeValues[position]
                audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Maze Complexity Dropdown
        val complexityOptions = arrayOf("Low", "Medium", "High")
        val complexityValues = arrayOf(HomeActivity.COMPLEXITY_LOW, HomeActivity.COMPLEXITY_MEDIUM, HomeActivity.COMPLEXITY_HIGH)
        val complexityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, complexityOptions)
        complexityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerComplexity.adapter = complexityAdapter
        binding.spinnerComplexity.setSelection(0) // Default to Low
        binding.spinnerComplexity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedComplexity = complexityValues[position]
                audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Game Mode Dropdown
        val gameModeOptions = arrayOf("Coverage", "Zones")
        val gameModeValues = arrayOf(HomeActivity.GAME_MODE_COVERAGE, HomeActivity.GAME_MODE_ZONES)
        val gameModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gameModeOptions)
        gameModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGameMode.adapter = gameModeAdapter
        binding.spinnerGameMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGameMode = gameModeValues[position]
                audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Robot Spawners Dropdown
        val spawnerOptions = arrayOf("0", "1", "2", "3", "4", "5")
        val spawnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spawnerOptions)
        spawnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRobotSpawners.adapter = spawnerAdapter
        binding.spinnerRobotSpawners.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRobotSpawners = position
                audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Private Match Checkbox
        binding.checkboxPrivate.setOnCheckedChangeListener { _, isChecked ->
            isPrivateMatch = isChecked
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
        }
    }

    private fun setupButtons() {
        binding.buttonHostGame.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            startGameWithSettings()
        }

        binding.buttonCancel.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            finish()
        }
        
        // Scale button text as well
        val displayMetrics = resources.displayMetrics
        val densityDpi = displayMetrics.densityDpi
        val scalingFactor = when {
            densityDpi <= 160 -> 1.4f
            densityDpi <= 240 -> 1.2f  
            densityDpi <= 320 -> 1.0f
            densityDpi <= 480 -> 0.85f
            else -> 0.7f
        }
        
        scaleTextView(binding.buttonHostGame, 14f, scalingFactor)
        scaleTextView(binding.buttonCancel, 14f, scalingFactor)
    }

    private fun setupHelpButtons() {
        binding.helpTimeLimit.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            updateInfoCard("Time Limit", "Time Limit sets how long the match will last. Choose from:\n\n• 3 minutes - Quick match\n\n• 5 minutes - Standard match\n\n• 7 minutes - Extended match\n\nLonger matches allow for more strategic gameplay and territory expansion.")
        }

        binding.helpComplexity.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            updateInfoCard("Maze Complexity", "Maze Complexity affects how intricate the generated maze will be:\n\n• Low - Simple paths, fewer walls\n\n• Medium - Moderate complexity\n\n• High - Complex maze with many corridors\n\nHigher complexity creates more challenging navigation but offers more strategic options.")
        }

        binding.helpGameMode.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            updateInfoCard("Game Mode", "Game Mode determines the victory condition:\n\n• Coverage - Paint as much territory as possible. Winner has the most painted area.\n\n• Zones - Control specific zones on the map. Winner controls the most zones.\n\nEach mode requires different strategies and playstyles.")
        }

        binding.helpSpawners.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            updateInfoCard("Robot Spawners", "Robot Spawners add AI-controlled opponents to the match:\n\n• 0 - No AI robots (player vs player only)\n\n• 1-5 - Number of AI robots that will spawn\n\nAI robots will compete for territory and add unpredictability to matches. Higher numbers create more chaotic gameplay.")
        }

        binding.helpPrivate.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            updateInfoCard("Private Match", "Private Match setting controls who can join your game:\n\n• Unchecked - Public match that anyone can join\n\n• Checked - Private match requiring a Game ID\n\nPrivate matches are perfect for playing with specific friends, while public matches allow random opponents to join.")
        }
    }

    private fun updateInfoCard(title: String, text: String) {
        binding.infoTitle.text = title
        binding.infoContent.text = text
    }
    
    private fun resetInfoCard() {
        binding.infoTitle.text = "Game Info"
        binding.infoContent.text = "Configure your match settings:\n\n• Time Limit: How long the match will last\n\n• Maze Complexity: Affects maze generation difficulty\n\n• Game Mode: Coverage (paint territory) or Zones (control areas)\n\n• Robot Spawners: Add AI opponents to the match\n\n• Private: Requires Game ID to join"
    }

    private fun startGameWithSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_HOST)
            putExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, selectedTimeLimit)
            putExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY, selectedComplexity)
            putExtra(HomeActivity.EXTRA_GAME_MODE, selectedGameMode)
            putExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, isPrivateMatch)
            putExtra(HomeActivity.EXTRA_ROBOT_SPAWNERS_ENABLED, selectedRobotSpawners > 0)
            putExtra(HomeActivity.EXTRA_ROBOT_SPAWNER_COUNT, selectedRobotSpawners)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Reset info card when going back
        resetInfoCard()
    }

    /**
     * Enables full screen immersive mode by hiding status bar and navigation bar
     */
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
            // For Android 10 (API 29) and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        
        // Keep screen on during app usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-enable full screen mode when window regains focus
            enableFullScreenMode()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enable full screen mode when returning to the activity
        enableFullScreenMode()
    }
}