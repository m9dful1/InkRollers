package com.spiritwisestudios.inkrollers.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.spiritwisestudios.inkrollers.AudioManager
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class DialogManager @Inject constructor() {
    companion object {
        private const val TAG = "DialogManager"
    }
    
    private lateinit var audioManager: AudioManager
    
    fun initialize(context: Context) {
        audioManager = AudioManager.getInstance(context)
    }

    private var waitingDialog: AlertDialog? = null
    private var countdownDialog: AlertDialog? = null

    /**
     * Shows a waiting dialog for hosts waiting for other players to join
     */
    fun showWaitingForPlayersDialog(activity: AppCompatActivity) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
                return@runOnUiThread
            }
            dismissAllDialogs()
            Log.d(TAG, "Showing waiting for players dialog")
            waitingDialog = AlertDialog.Builder(activity)
                .setTitle("Waiting")
                .setMessage("Waiting for other players to join...")
                .setCancelable(false)
                .show()
            Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
        }
    }

    /**
     * Shows a waiting dialog for joiners waiting for the host to start
     */
    fun showWaitingForHostDialog(activity: AppCompatActivity) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
                return@runOnUiThread
            }
            dismissAllDialogs()
            Log.d(TAG, "Showing waiting for host dialog")
            waitingDialog = AlertDialog.Builder(activity)
                .setTitle("Waiting")
                .setMessage("Waiting for host to start...")
                .setCancelable(false)
                .show()
            Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
        }
    }

    /**
     * Shows a reconnecting dialog when attempting to rejoin a previous game
     */
    fun showReconnectingDialog(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        
        waitingDialog = AlertDialog.Builder(activity)
            .setTitle("Reconnecting")
            .setMessage("Attempting to reconnect to your previous game...")
            .setCancelable(false)
            .show()
    }

    /**
     * Shows the rematch dialog after a match ends
     */
    fun showRematchDialog(activity: AppCompatActivity, didWin: Boolean, onRematchDecision: (Boolean) -> Unit) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                Log.w(TAG, "showRematchDialog: Activity finishing or destroyed, not showing dialog.")
                return@runOnUiThread
            }
            dismissAllDialogs()
            Log.d(TAG, "showRematchDialog called. didWin=$didWin")
            val message = if (didWin) "You Won!" else "You Lost"
            AlertDialog.Builder(activity)
                .setTitle(message)
                .setMessage("Play Again?")
                .setPositiveButton("Yes") { _, _ -> 
                    if (::audioManager.isInitialized) {
                        audioManager.playSound(AudioManager.SoundType.UI_CLICK)
                    }
                    Log.d(TAG, "Rematch dialog: YES selected.")
                    onRematchDecision(true)
                }
                .setNegativeButton("No") { _, _ -> 
                    if (::audioManager.isInitialized) {
                        audioManager.playSound(AudioManager.SoundType.UI_CLICK)
                    }
                    Log.d(TAG, "Rematch dialog: NO selected.")
                    onRematchDecision(false)
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * Shows the rematch declined dialog when the other player says no
     */
    fun showRematchDeclinedDialog(activity: AppCompatActivity, onDismiss: () -> Unit) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                onDismiss()
                return@runOnUiThread
            }
            dismissAllDialogs()
            AlertDialog.Builder(activity)
                .setTitle("Rematch Declined")
                .setMessage("The other player chose not to rematch. Returning to home screen.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ -> 
                    if (::audioManager.isInitialized) {
                        audioManager.playSound(AudioManager.SoundType.UI_CLICK)
                    }
                    onDismiss() 
                }
                .show()
        }
    }

    /**
     * Shows a general error dialog with Firebase-related errors
     */
    fun showFirebaseErrorDialog(activity: AppCompatActivity, message: String, onDismiss: () -> Unit) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            AlertDialog.Builder(activity)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> onDismiss() }
                .setCancelable(false)
                .show()
        } else {
            Log.w(TAG, "Activity is finishing, cannot show error dialog: $message")
        }
    }

    /**
     * Shows the pre-match countdown dialog (3-2-1-GO)
     */
    fun showCountdownDialog(
        activity: AppCompatActivity,
        isHost: Boolean,
        onCountdownFinished: () -> Unit,
        onSendMatchStart: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing, cannot show countdown dialog.")
            return 
        }
        try {
            Log.d(TAG, "Starting pre-match countdown, isHost=$isHost")
            dismissAllDialogs()
            countdownDialog = AlertDialog.Builder(activity)
                .setCancelable(false)
                .setMessage("3")
                .show()
            // Play initial countdown sound for "3"
            if (::audioManager.isInitialized) {
                audioManager.playSound(AudioManager.SoundType.COUNTDOWN_TICK)
            }
            Log.d(TAG, "Countdown dialog shown with initial '3'")
            
            val messages = listOf("2", "1", "GO")
            val handler = Handler(Looper.getMainLooper())
            var index = 0
            val runnable = object : Runnable {
                override fun run() {
                    try {
                        if (index < messages.size) {
                            countdownDialog?.setMessage(messages[index])
                            // Play appropriate sound for countdown
                            if (::audioManager.isInitialized) {
                                if (messages[index] == "GO") {
                                    audioManager.playSound(AudioManager.SoundType.COUNTDOWN_GO)
                                } else {
                                    audioManager.playSound(AudioManager.SoundType.COUNTDOWN_TICK)
                                }
                            }
                            Log.d(TAG, "Countdown updated to: ${messages[index]}")
                            index++
                            handler.postDelayed(this, 1000)
                        } else {
                            Log.d(TAG, "Countdown finished, starting match")
                            countdownDialog?.dismiss()
                            onCountdownFinished()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in countdown runnable", e)
                        countdownDialog?.dismiss()
                        onCountdownFinished()
                    }
                }
            }
            handler.postDelayed(runnable, 1000)
            
            if (isHost) {
                Log.d(TAG, "Host is sending match start signal")
                onSendMatchStart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showCountdownDialog", e)
            onCountdownFinished()
        }
    }

    /**
     * Dismisses the waiting dialog
     */
    fun dismissWaitingDialog() {
        try { 
            waitingDialog?.dismiss() 
        } catch (_: Exception) {}
        waitingDialog = null
    }

    /**
     * Dismisses all dialogs to prevent leaks/crashes
     */
    fun dismissAllDialogs() {
        try { 
            waitingDialog?.dismiss() 
        } catch (_: Exception) {}
        waitingDialog = null
        
        try { 
            countdownDialog?.dismiss() 
        } catch (_: Exception) {}
        countdownDialog = null
    }
} 