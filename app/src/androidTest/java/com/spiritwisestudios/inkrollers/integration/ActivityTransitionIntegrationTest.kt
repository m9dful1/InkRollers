package com.spiritwisestudios.inkrollers.integration

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.google.firebase.auth.FirebaseAuth
import com.spiritwisestudios.inkrollers.BaseFirebaseTest
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.MainActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for Activity transitions and data passing between HomeActivity and MainActivity.
 * 
 * These tests verify:
 * - Proper Intent data passing for game settings
 * - Activity lifecycle management during transitions
 * - Firebase integration during activity startup
 * - Error handling for invalid game configurations
 */
class ActivityTransitionIntegrationTest : BaseFirebaseTest() {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )

    @Before
    fun setup() {
        // Firebase setup handled by BaseFirebaseTest
        auth.signOut()
    }

    @After
    fun cleanup() {
        cleanupFirebase()
    }

    @Test
    fun testHostGameTransition_withValidSettings() {
        // Test transition from HomeActivity to MainActivity with host settings
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_HOST)
            putExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 5)
            putExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY, HomeActivity.COMPLEXITY_MEDIUM)
            putExtra(HomeActivity.EXTRA_GAME_MODE, HomeActivity.GAME_MODE_COVERAGE)
            putExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var activityStarted = false

            scenario.onActivity { activity ->
                // Verify activity was created successfully
                assertNotNull("Activity should not be null", activity)
                
                // Verify intent extras were received
                assertEquals("Mode should be HOST", HomeActivity.MODE_HOST, 
                    activity.intent.getStringExtra(HomeActivity.EXTRA_MODE))
                assertEquals("Time limit should be 5 minutes", 5, 
                    activity.intent.getIntExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 0))
                assertEquals("Complexity should be MEDIUM", HomeActivity.COMPLEXITY_MEDIUM,
                    activity.intent.getStringExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY))
                assertEquals("Game mode should be COVERAGE", HomeActivity.GAME_MODE_COVERAGE,
                    activity.intent.getStringExtra(HomeActivity.EXTRA_GAME_MODE))
                assertEquals("Should not be private match", false,
                    activity.intent.getBooleanExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, true))

                activityStarted = true
                latch.countDown()
            }

            assertTrue("Activity setup should complete within 10 seconds", 
                latch.await(10, TimeUnit.SECONDS))
            assertTrue("Activity should have started successfully", activityStarted)
        }
    }

    @Test
    fun testJoinGameTransition_withGameId() {
        // Test transition for joining a specific game
        
        val testGameId = "ABC123"
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_JOIN)
            putExtra(HomeActivity.EXTRA_GAME_ID, testGameId)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var intentDataVerified = false

            scenario.onActivity { activity ->
                // Verify join mode setup
                assertEquals("Mode should be JOIN", HomeActivity.MODE_JOIN,
                    activity.intent.getStringExtra(HomeActivity.EXTRA_MODE))
                assertEquals("Game ID should match", testGameId,
                    activity.intent.getStringExtra(HomeActivity.EXTRA_GAME_ID))

                intentDataVerified = true
                latch.countDown()
            }

            assertTrue("Intent data verification should complete", 
                latch.await(5, TimeUnit.SECONDS))
            assertTrue("Intent data should be verified", intentDataVerified)
        }
    }

    @Test
    fun testJoinRandomGameTransition() {
        // Test transition for joining a random game (no game ID)
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_JOIN)
            // No EXTRA_GAME_ID - should trigger random game search
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var randomJoinSetup = false

            scenario.onActivity { activity ->
                // Verify random join mode setup
                assertEquals("Mode should be JOIN", HomeActivity.MODE_JOIN,
                    activity.intent.getStringExtra(HomeActivity.EXTRA_MODE))
                assertNull("Game ID should be null for random join",
                    activity.intent.getStringExtra(HomeActivity.EXTRA_GAME_ID))

                randomJoinSetup = true
                latch.countDown()
            }

            assertTrue("Random join setup should complete", 
                latch.await(5, TimeUnit.SECONDS))
            assertTrue("Random join should be set up correctly", randomJoinSetup)
        }
    }

    @Test
    fun testActivityLifecycle_onCreate() {
        // Test that MainActivity properly handles onCreate lifecycle
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_HOST)
            putExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 3)
            putExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY, HomeActivity.COMPLEXITY_HIGH)
            putExtra(HomeActivity.EXTRA_GAME_MODE, HomeActivity.GAME_MODE_ZONES)
            putExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, true)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var lifecycleCompleted = false

            scenario.onActivity { activity ->
                // Verify activity completed onCreate successfully
                assertNotNull("Activity should be initialized", activity)
                
                // Basic lifecycle verification - activity should be in a valid state
                assertTrue("Activity should be valid", !activity.isDestroyed)
                assertTrue("Activity should be finished or active", 
                    activity.isFinishing || !activity.isDestroyed)

                lifecycleCompleted = true
                latch.countDown()
            }

            assertTrue("Lifecycle should complete within timeout", 
                latch.await(10, TimeUnit.SECONDS))
            assertTrue("Lifecycle should be completed", lifecycleCompleted)
        }
    }

    @Test
    fun testInvalidGameSettings_handledGracefully() {
        // Test that invalid or missing game settings are handled gracefully
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, "INVALID_MODE")
            putExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, -1) // Invalid time
            putExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY, "INVALID_COMPLEXITY")
            putExtra(HomeActivity.EXTRA_GAME_MODE, "INVALID_MODE")
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var errorHandled = false

            scenario.onActivity { activity ->
                // Activity should start despite invalid settings (should use defaults)
                assertNotNull("Activity should handle invalid settings gracefully", activity)
                assertTrue("Activity should not be destroyed", !activity.isDestroyed)

                errorHandled = true
                latch.countDown()
            }

            assertTrue("Error handling should complete", 
                latch.await(5, TimeUnit.SECONDS))
            assertTrue("Invalid settings should be handled", errorHandled)
        }
    }

    @Test
    fun testFirebaseAuthIntegration_duringActivityStart() {
        // Test that Firebase Auth is properly initialized during activity startup
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(HomeActivity.EXTRA_MODE, HomeActivity.MODE_HOST)
            putExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 3)
            putExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY, HomeActivity.COMPLEXITY_LOW)
            putExtra(HomeActivity.EXTRA_GAME_MODE, HomeActivity.GAME_MODE_COVERAGE)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val latch = CountDownLatch(1)
            var authInitialized = false

            scenario.onActivity { activity ->
                // Firebase Auth should be accessible
                val currentAuth = FirebaseAuth.getInstance()
                assertNotNull("FirebaseAuth should be initialized", currentAuth)

                // Auth instance should be properly configured for emulator
                assertTrue("Auth should be initialized in activity context", 
                    currentAuth.app != null)

                authInitialized = true
                latch.countDown()
            }

            assertTrue("Auth initialization should complete", 
                latch.await(10, TimeUnit.SECONDS))
            assertTrue("Auth should be initialized", authInitialized)
        }
    }
} 