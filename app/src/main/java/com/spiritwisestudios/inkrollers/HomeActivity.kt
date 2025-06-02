package com.spiritwisestudios.inkrollers

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.BuildConfig
import com.spiritwisestudios.inkrollers.ui.ProfileFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.spiritwisestudios.inkrollers.databinding.ActivityHomeBinding
//import com.spiritwisestudios.inkrollers.BuildConfig

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    companion object {
        const val EXTRA_MODE = "com.spiritwisestudios.inkrollers.MODE"
        const val EXTRA_GAME_ID = "com.spiritwisestudios.inkrollers.GAME_ID"
        const val EXTRA_TIME_LIMIT_MINUTES = "com.spiritwisestudios.inkrollers.TIME_LIMIT_MINUTES"
        const val EXTRA_MAZE_COMPLEXITY = "com.spiritwisestudios.inkrollers.MAZE_COMPLEXITY"
        const val EXTRA_GAME_MODE = "com.spiritwisestudios.inkrollers.GAME_MODE"
        const val EXTRA_IS_PRIVATE_MATCH = "com.spiritwisestudios.inkrollers.EXTRA_IS_PRIVATE_MATCH"
        const val MODE_HOST = "HOST"
        const val MODE_JOIN = "JOIN"

        // Maze Complexity Levels
        const val COMPLEXITY_LOW = "LOW"
        const val COMPLEXITY_MEDIUM = "MEDIUM"
        const val COMPLEXITY_HIGH = "HIGH"
        
        // Game Modes
        const val GAME_MODE_COVERAGE = "COVERAGE"
        const val GAME_MODE_ZONES = "ZONES"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase App Check
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        binding.buttonPlay.setOnClickListener {
            // Apply the press animation
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press)
            binding.buttonPlay.startAnimation(animation)
            
            // Hide Play button and show submenu after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                binding.layoutSubmenu.visibility = View.VISIBLE
            }, 100)
        }

        binding.buttonHostGame.setOnClickListener {
            showMatchSettingsDialog()
        }

        binding.buttonJoinGame.setOnClickListener {
            val gameId = binding.editTextGameId.text.toString().trim()
            if (gameId.isEmpty()) {
                // Join a random available game
                startGameActivity(MODE_JOIN, null)
            } else if (gameId.length == 6) { // Specific game ID entered
                startGameActivity(MODE_JOIN, gameId)
            } else {
                Toast.makeText(this, "Please enter a valid 6-character Game ID or leave blank to join random game", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonProfile.setOnClickListener {
            val currentUser = Firebase.auth.currentUser
            if (currentUser != null) {
                // User is signed in, proceed to profile
                showProfileFragment(currentUser.uid)
            } else {
                // User is not signed in, attempt anonymous sign-in then show profile
                signInAndShowProfile()
            }
        }
    }

    private fun showProfileFragment(uid: String) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, ProfileFragment.newInstance(uid))
            .addToBackStack(null)
            .commit()
    }

    private fun signInAndShowProfile() {
        Firebase.auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, get user and show profile
                    val user = Firebase.auth.currentUser
                    user?.uid?.let {
                        Log.d("HomeActivity", "Anonymous sign-in successful for profile view. UID: $it")
                        // It might be good to set online status here IF this is the main point of user interaction
                        // ProfileRepository.setUserOnlineStatus(it) 
                        showProfileFragment(it)
                    } ?: run {
                        Log.e("HomeActivity", "Anonymous sign-in task successful but user or UID is null.")
                        Toast.makeText(baseContext, "Error: Could not retrieve user ID after sign-in.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("HomeActivity", "Anonymous sign-in failed for profile view.", task.exception)
                    Toast.makeText(baseContext, "Sign-in failed. Cannot view profile.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        // REMOVE: Set user online when activity resumes
        // Firebase.auth.currentUser?.uid?.let {
        // ProfileRepository.setUserOnlineStatus(it)
        // }
    }

    private fun showMatchSettingsDialog() {
        val timeOptions = arrayOf("3 minutes", "5 minutes", "7 minutes")
        val timeValues = intArrayOf(3, 5, 7)
        var selectedTimeLimit = timeValues[0] // Default to 3 minutes

        AlertDialog.Builder(this)
            .setTitle("Set Time Limit")
            .setItems(timeOptions) { _, which ->
                selectedTimeLimit = timeValues[which]
                showComplexityDialog(selectedTimeLimit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showComplexityDialog(timeLimit: Int) {
        val complexityOptions = arrayOf("Low", "Medium", "High")
        val complexityValues = arrayOf(COMPLEXITY_LOW, COMPLEXITY_MEDIUM, COMPLEXITY_HIGH)
        var selectedComplexity = complexityValues[2] // Default to High

        AlertDialog.Builder(this)
            .setTitle("Set Maze Complexity")
            .setItems(complexityOptions) { _, which ->
                selectedComplexity = complexityValues[which]
                showGameModeDialog(timeLimit, selectedComplexity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGameModeDialog(timeLimit: Int, complexity: String) {
        val gameModeOptions = arrayOf("Coverage", "Zones")
        val gameModeValues = arrayOf(GAME_MODE_COVERAGE, GAME_MODE_ZONES)
        var selectedGameMode = gameModeValues[0] // Default to Coverage

        AlertDialog.Builder(this)
            .setTitle("Select Game Mode")
            .setItems(gameModeOptions) { _, which ->
                selectedGameMode = gameModeValues[which]
                showMatchTypeDialog(timeLimit, complexity, selectedGameMode) // Proceed to match type
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showMatchTypeDialog(timeLimit: Int, complexity: String, gameMode: String) {
        val matchTypeOptions = arrayOf("Public (Joinable by random)", "Private (Requires Game ID)")
        var isPrivate = false // Default to public

        AlertDialog.Builder(this)
            .setTitle("Select Match Type")
            .setSingleChoiceItems(matchTypeOptions, 0) { _, which -> // Default to Public (index 0)
                isPrivate = (which == 1) // Private is index 1
            }
            .setPositiveButton("Host Game") { _, _ ->
                startGameActivity(MODE_HOST, null, timeLimit, complexity, gameMode, isPrivate)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startGameActivity(mode: String, gameId: String? = null, timeLimit: Int = 3, complexity: String = COMPLEXITY_HIGH, gameMode: String = GAME_MODE_COVERAGE, isPrivate: Boolean = false) {
        android.util.Log.d("HomeActivity", "Starting MainActivity with mode: $mode, gameId: $gameId, timeLimit: $timeLimit, mazeComplexity: $complexity, gameMode: $gameMode, isPrivate: $isPrivate")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            if (mode == MODE_JOIN && gameId != null) {
                putExtra(EXTRA_GAME_ID, gameId)
            }
            if (mode == MODE_HOST) {
                putExtra(EXTRA_TIME_LIMIT_MINUTES, timeLimit)
                putExtra(EXTRA_MAZE_COMPLEXITY, complexity)
                putExtra(EXTRA_GAME_MODE, gameMode)
                putExtra(EXTRA_IS_PRIVATE_MATCH, isPrivate)
            }
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (binding.layoutSubmenu.visibility == View.VISIBLE) {
            binding.layoutSubmenu.visibility = View.GONE
            binding.buttonPlay.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }
} 