package com.spiritwisestudios.inkrollers

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.campaign.CampaignActivity
import com.spiritwisestudios.inkrollers.databinding.ActivityGameModeSelectionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameModeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameModeSelectionBinding
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameModeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableFullScreenMode()

        audioManager = AudioManager.getInstance(this)

        setupTooltips()
        setupButtons()
    }

    private fun setupTooltips() {
        binding.infoTitle.text = "Game Modes"
        binding.infoContent.text = "Choose your game experience:\n\n• Single Player Campaign: Play through story missions and challenges against AI opponents\n\n• Multi-Player Match: Join or host online matches against other players worldwide"
    }

    private fun setupButtons() {
        binding.buttonSinglePlayer.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            startCampaignActivity()
        }

        binding.buttonMultiplayer.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            val intent = Intent(this, MultiplayerModeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startCampaignActivity() {
        val intent = Intent(this, CampaignActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        enableFullScreenMode()
        audioManager.resumeAudio()
    }

    override fun onPause() {
        super.onPause()
        audioManager.pauseAudio()
    }

    private fun enableFullScreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
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
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullScreenMode()
        }
    }
}