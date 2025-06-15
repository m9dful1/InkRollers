package com.spiritwisestudios.inkrollers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Base test class for Firebase-related integration and system tests.
 * 
 * Handles Firebase emulator initialization in a safe way that prevents
 * the "Cannot call useEmulator() after instance has already been initialized" error.
 * All Firebase-related tests should extend this class instead of initializing
 * Firebase directly in their setUp methods.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseFirebaseTest {

    protected lateinit var context: Context
    protected lateinit var auth: FirebaseAuth
    protected lateinit var database: FirebaseDatabase

    companion object {
        private var isFirebaseInitialized = false
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val AUTH_PORT = 9099
        private const val DATABASE_PORT = 9000
        
        /**
         * Initializes Firebase emulator configuration once per test run.
         * This prevents multiple initialization attempts that cause test failures.
         */
        @JvmStatic
        private fun initializeFirebaseEmulator(context: Context) {
            if (isFirebaseInitialized) return
            
            // Initialize Firebase app if not already done
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            
            val auth = FirebaseAuth.getInstance()
            val database = FirebaseDatabase.getInstance()
            
            try {
                // Configure emulator settings
                auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
                database.useEmulator(EMULATOR_HOST, DATABASE_PORT)
                
                isFirebaseInitialized = true
            } catch (e: IllegalStateException) {
                // Emulator already configured, which is fine
                if (e.message?.contains("useEmulator") != true) {
                    throw e
                }
                isFirebaseInitialized = true
            }
        }
    }

    @Before
    fun setUpFirebase() {
        context = ApplicationProvider.getApplicationContext()
        initializeFirebaseEmulator(context)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
    }
    
    /**
     * Common cleanup for Firebase tests.
     * Subclasses should call this in their tearDown methods.
     */
    protected fun cleanupFirebase() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
} 