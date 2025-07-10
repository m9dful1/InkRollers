package com.spiritwisestudios.inkrollers.campaign

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.AudioManager
import com.spiritwisestudios.inkrollers.MainActivity
import com.spiritwisestudios.inkrollers.databinding.ActivityCampaignBinding
import androidx.recyclerview.widget.LinearLayoutManager

class CampaignActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCampaignBinding
    private lateinit var audioManager: AudioManager
    private lateinit var campaignManager: CampaignManager

    companion object {
        private const val TAG = "CampaignActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCampaignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable full screen immersive mode
        enableFullScreenMode()

        // Initialize AudioManager
        audioManager = AudioManager.getInstance(this)
        audioManager.initialize()

        // Initialize CampaignManager
        campaignManager = CampaignManager.getInstance(this)

        setupUI()
        loadCampaignProgress()
    }

    private fun setupUI() {
        binding.buttonBack.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            finish()
        }

        // Setup mission list
        binding.missionList.layoutManager = LinearLayoutManager(this)
        binding.missionList.adapter = MissionAdapter(emptyList()) { levelId ->
            startCampaignLevel(levelId)
        }
    }

    private fun loadCampaignProgress() {
        campaignManager.loadProgress()
        updateMissionList()
    }

    private fun updateMissionList() {
        val availableLevels = campaignManager.getAvailableLevels()
        val completedLevels = campaignManager.getCompletedLevels()
        
        val missions = CampaignLevels.getAllLevelIds().map { levelId ->
            val levelData = CampaignLevels.getLevelData(levelId)
            val isAvailable = levelId in availableLevels
            val isCompleted = levelId in completedLevels
            val grade = campaignManager.getLevelGrade(levelId)?.grade
            
            MissionAdapter.MissionItem(
                levelId = levelId,
                levelName = levelData?.levelName ?: "Unknown Mission",
                isAvailable = isAvailable,
                isCompleted = isCompleted,
                grade = grade
            )
        }
        
        (binding.missionList.adapter as? MissionAdapter)?.let { adapter ->
            // Update the adapter's data
            adapter.missions = missions
            adapter.notifyDataSetChanged()
        } ?: run {
            // Create new adapter if none exists
            binding.missionList.adapter = MissionAdapter(missions) { levelId ->
                startCampaignLevel(levelId)
            }
        }
        
        Log.d(TAG, "Updated mission list: ${missions.size} missions")
    }

    private fun startCampaignLevel(levelId: String) {
        Log.d(TAG, "Starting campaign level: $levelId")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(com.spiritwisestudios.inkrollers.HomeActivity.EXTRA_MODE, com.spiritwisestudios.inkrollers.HomeActivity.MODE_CAMPAIGN)
            putExtra(com.spiritwisestudios.inkrollers.MainActivity.EXTRA_CAMPAIGN_LEVEL, levelId)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-enable full screen mode when returning to the activity
        enableFullScreenMode()
        // Resume audio when activity comes back to foreground
        audioManager.resumeAudio()
        // Refresh mission list in case progress was updated
        updateMissionList()
    }

    override fun onPause() {
        super.onPause()
        // Pause audio when activity goes to background
        audioManager.pauseAudio()
    }

    /**
     * Enables full screen immersive mode by hiding status bar and navigation bar
     */
    private fun enableFullScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 (API 30) and above
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
} 