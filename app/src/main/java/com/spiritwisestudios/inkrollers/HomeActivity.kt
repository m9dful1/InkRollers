package com.spiritwisestudios.inkrollers

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.ui.ProfileFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.spiritwisestudios.inkrollers.repository.ProfileRepository

class HomeActivity : AppCompatActivity() {

    private lateinit var playButton: Button
    private lateinit var subMenuLayout: LinearLayout
    private lateinit var hostButton: Button
    private lateinit var joinButton: Button
    private lateinit var gameIdEditText: EditText

    companion object {
        const val EXTRA_MODE = "com.spiritwisestudios.inkrollers.MODE"
        const val EXTRA_GAME_ID = "com.spiritwisestudios.inkrollers.GAME_ID"
        const val EXTRA_TIME_LIMIT_MINUTES = "com.spiritwisestudios.inkrollers.TIME_LIMIT_MINUTES"
        const val EXTRA_MAZE_COMPLEXITY = "com.spiritwisestudios.inkrollers.MAZE_COMPLEXITY"
        const val MODE_HOST = "HOST"
        const val MODE_JOIN = "JOIN"

        // Maze Complexity Levels
        const val COMPLEXITY_LOW = "LOW"
        const val COMPLEXITY_MEDIUM = "MEDIUM"
        const val COMPLEXITY_HIGH = "HIGH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        playButton = findViewById(R.id.button_play)
        subMenuLayout = findViewById(R.id.layout_submenu)
        hostButton = findViewById(R.id.button_host_game)
        joinButton = findViewById(R.id.button_join_game)
        gameIdEditText = findViewById(R.id.editText_game_id)

        playButton.setOnClickListener {
            // Apply the press animation
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press)
            playButton.startAnimation(animation)
            
            // Hide Play button and show submenu after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                playButton.visibility = View.GONE
                subMenuLayout.visibility = View.VISIBLE
            }, 100)
        }

        hostButton.setOnClickListener {
            showMatchSettingsDialog()
        }

        joinButton.setOnClickListener {
            val gameId = gameIdEditText.text.toString().trim().uppercase()
            if (gameId.isEmpty()) {
                // Join a random available game
                startGameActivity(MODE_JOIN, null)
            } else if (gameId.length == 6) { // Specific game ID entered
                startGameActivity(MODE_JOIN, gameId)
            } else {
                Toast.makeText(this, "Please enter a valid 6-character Game ID or leave blank to join random game", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.button_profile).setOnClickListener {
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
        val timeOptions = arrayOf("3 Minutes", "5 Minutes", "7 Minutes")
        val timeValues = intArrayOf(3, 5, 7)
        var selectedTimeIndex = 0 // Default to 3 minutes

        val complexityOptions = arrayOf("Low", "Medium", "High")
        val complexityValues = arrayOf(COMPLEXITY_LOW, COMPLEXITY_MEDIUM, COMPLEXITY_HIGH)
        var selectedComplexityIndex = 2 // Default to High

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Match Settings")

        // Layout for dialog
        val dialogLayout = LinearLayout(this)
        dialogLayout.orientation = LinearLayout.VERTICAL
        dialogLayout.setPadding(50, 50, 50, 50)

        // Time Limit Picker
        builder.setSingleChoiceItems(timeOptions, selectedTimeIndex) { _, which ->
            selectedTimeIndex = which
        }

        // Separator or some spacing might be good here if using multiple pickers directly in builder.
        // For simplicity, we'll just have two pickers.
        // If more complex layout is needed, we'd inflate an XML.

        // Maze Complexity Picker
        // AlertDialog.Builder doesn't easily support multiple setSingleChoiceItems.
        // We'll show complexity in a subsequent dialog or use a custom layout.
        // For now, let's simplify and do it sequentially or use a custom view.
        // For this step, we'll just pick time and then complexity in a chained manner.

        val timeDialogBuilder = AlertDialog.Builder(this)
        timeDialogBuilder.setTitle("Select Time Limit")
        timeDialogBuilder.setSingleChoiceItems(timeOptions, selectedTimeIndex) { _, which ->
            selectedTimeIndex = which
        }
        timeDialogBuilder.setPositiveButton("Next") { timeDialog, _ ->
            timeDialog.dismiss()
            val selectedTime = timeValues[selectedTimeIndex]

            val complexityDialogBuilder = AlertDialog.Builder(this)
            complexityDialogBuilder.setTitle("Select Maze Complexity")
            complexityDialogBuilder.setSingleChoiceItems(complexityOptions, selectedComplexityIndex) { _, which ->
                selectedComplexityIndex = which
            }
            complexityDialogBuilder.setPositiveButton("Host Game") { complexityDialog, _ ->
                complexityDialog.dismiss()
                val selectedComplexity = complexityValues[selectedComplexityIndex]
                startGameActivity(MODE_HOST, null, selectedTime, selectedComplexity)
            }
            complexityDialogBuilder.setNegativeButton("Cancel") { complexityDialog, _ ->
                complexityDialog.dismiss()
            }
            complexityDialogBuilder.show()
        }
        timeDialogBuilder.setNegativeButton("Cancel") { timeDialog, _ ->
            timeDialog.dismiss()
        }
        timeDialogBuilder.show()
    }

    private fun startGameActivity(mode: String, gameId: String? = null, timeLimit: Int? = null, mazeComplexity: String? = null) {
        android.util.Log.d("HomeActivity", "Starting MainActivity with mode: $mode, gameId: $gameId, timeLimit: $timeLimit, mazeComplexity: $mazeComplexity")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            if (mode == MODE_JOIN && gameId != null) {
                putExtra(EXTRA_GAME_ID, gameId)
            }
            if (mode == MODE_HOST) {
                putExtra(EXTRA_TIME_LIMIT_MINUTES, timeLimit ?: 3) // Default 3 min
                putExtra(EXTRA_MAZE_COMPLEXITY, mazeComplexity ?: COMPLEXITY_HIGH) // Default High
            }
        }
        startActivity(intent)
    }
} 