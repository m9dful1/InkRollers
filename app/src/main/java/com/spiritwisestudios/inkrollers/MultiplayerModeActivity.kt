package com.spiritwisestudios.inkrollers

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.databinding.ActivityMultiplayerModeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MultiplayerModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultiplayerModeBinding
    private lateinit var audioManager: AudioManager
    private var isJoinDialogVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiplayerModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableFullScreenMode()

        audioManager = AudioManager.getInstance(this)

        setupTooltips()
        setupButtons()
    }

    private fun setupTooltips() {
        updateTooltipContent(false)
    }

    private fun updateTooltipContent(joinDialogVisible: Boolean) {
        binding.infoTitle.text = "Multiplayer"
        if (joinDialogVisible) {
            binding.infoContent.text = "Join a match:\n\n• Enter a 6-character Game ID to join a specific private match\n\n• Leave the Game ID blank to join any available public match\n\n• Game IDs are case-sensitive and must be exactly 6 characters"
        } else {
            binding.infoContent.text = "Choose multiplayer mode:\n\n• Host Match: Create a new game and configure settings. Other players can join your match\n\n• Join Match: Enter an existing game by Game ID or join a random available match"
        }
    }

    private fun setupButtons() {
        binding.buttonHostMatch.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            val intent = Intent(this, MatchSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonJoinMatch.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            showJoinDialog()
        }

        binding.buttonPlay.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            val gameId = binding.editTextGameId.text.toString().trim()
            if (gameId.isEmpty()) {
                startGameActivity(HomeActivity.MODE_JOIN, null)
            } else if (gameId.length == 6) {
                startGameActivity(HomeActivity.MODE_JOIN, gameId)
            } else {
                Toast.makeText(this, "Please enter a valid 6-character Game ID or leave blank to join random game", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonCancel.setOnClickListener {
            audioManager.playSound(AudioManager.SoundType.UI_CLICK)
            hideJoinDialog()
        }
    }

    private fun showJoinDialog() {
        isJoinDialogVisible = true
        binding.cardJoinDialog.visibility = View.VISIBLE
        binding.buttonHostMatch.visibility = View.GONE
        binding.buttonJoinMatch.visibility = View.GONE
        updateTooltipContent(true)
        binding.editTextGameId.text.clear()
    }

    private fun hideJoinDialog() {
        isJoinDialogVisible = false
        binding.cardJoinDialog.visibility = View.GONE
        binding.buttonHostMatch.visibility = View.VISIBLE
        binding.buttonJoinMatch.visibility = View.VISIBLE
        updateTooltipContent(false)
    }

    private fun startGameActivity(mode: String, gameId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, mode)
            if (mode == HomeActivity.MODE_JOIN && gameId != null) {
                putExtra(HomeActivity.EXTRA_GAME_ID, gameId)
            }
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (isJoinDialogVisible) {
            hideJoinDialog()
        } else {
            super.onBackPressed()
        }
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