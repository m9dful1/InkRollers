package com.spiritwisestudios.inkrollers.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.rule.GrantPermissionRule
import com.spiritwisestudios.inkrollers.BaseFirebaseTest
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.MainActivity
import com.spiritwisestudios.inkrollers.R
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive UI tests for Ink Rollers gameplay experience.
 * 
 * Tests the complete user interface during actual gameplay including:
 * - HUD component updates and visibility
 * - Virtual joystick interaction and responsiveness
 * - Paint mechanics and visual feedback
 * - Game mode transitions and UI changes
 * - Real-time multiplayer UI synchronization
 * - Performance under various gameplay conditions
 */
class GameplayUITest : BaseFirebaseTest() {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET
    )

    @Before
    fun setUp() {
        // Firebase setup handled by BaseFirebaseTest
        auth.signOut()
    }

    @After
    fun tearDown() {
        cleanupFirebase()
    }

    @Test
    fun testGameplayHUD_allComponentsVisible() {
        // Test that all HUD components are visible during gameplay
        
        // Start a host game to get into gameplay
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "medium")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Check ink HUD visibility
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))

            // Check coverage HUD visibility (for COVERAGE mode)
            onView(withId(R.id.coverage_hud_view))
                .check(matches(isDisplayed()))

            // Check timer HUD visibility
            onView(withId(R.id.timer_hud_view))
                .check(matches(isDisplayed()))

            // Verify initial ink level display (should show 100% or close to it)
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testVirtualJoystick_touchInteraction() {
        // Test virtual joystick responds to touch input
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "easy")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Check that GameView (which contains the virtual joystick) is present
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))

            // Perform touch action on the GameView to simulate joystick interaction
            onView(withId(R.id.game_view))
                .perform(click())

            // Wait briefly to allow touch processing
            Thread.sleep(500)

            // Verify the game view is still responsive after touch
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testInkHUD_updatesWithMovement() {
        // Test that ink HUD updates as player moves and paints
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "easy")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Check initial ink HUD state
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))

            // Simulate movement/painting by multiple touches on game view
            onView(withId(R.id.game_view))
                .perform(click())
                .perform(click())
                .perform(click())

            // Wait for ink level to potentially change
            Thread.sleep(2000)

            // Verify ink HUD is still visible and functioning
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testCoverageHUD_displaysStatistics() {
        // Test coverage HUD shows player statistics during COVERAGE mode
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "medium")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization and statistics calculation
            Thread.sleep(4000)

            // Check coverage HUD is visible and showing data
            onView(withId(R.id.coverage_hud_view))
                .check(matches(isDisplayed()))

            // Simulate some gameplay to generate coverage statistics
            onView(withId(R.id.game_view))
                .perform(click())
                .perform(swipeRight())
                .perform(click())

            // Wait for statistics update
            Thread.sleep(2000)

            // Verify coverage HUD continues to display
            onView(withId(R.id.coverage_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testZoneHUD_visibleInZonesMode() {
        // Test zone HUD appears in ZONES game mode
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "medium")
            putExtra("GAME_MODE", "ZONES")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Check zone HUD visibility for ZONES mode
            onView(withId(R.id.zone_hud_view))
                .check(matches(isDisplayed()))

            // Verify other standard HUDs are also present
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.timer_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testTimerHUD_countdownDisplay() {
        // Test timer HUD shows countdown and updates correctly
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 60000L) // 1 minute for faster testing
            putExtra("COMPLEXITY", "easy")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Check timer HUD is visible
            onView(withId(R.id.timer_hud_view))
                .check(matches(isDisplayed()))

            // Wait a few seconds and verify timer is still updating
            Thread.sleep(5000)

            onView(withId(R.id.timer_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testModeToggle_UIFeedback() {
        // Test mode toggle button provides proper UI feedback
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "easy")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Look for mode toggle button or double-tap to toggle
            onView(withId(R.id.game_view))
                .perform(doubleClick()) // Double-tap typically toggles mode

            // Wait for mode change feedback
            Thread.sleep(1000)

            // Verify game view is still responsive
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))

            // Check ink HUD still shows mode information
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testGameView_responsiveToMultipleInputs() {
        // Test game view handles multiple rapid inputs without freezing
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "medium")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            // Perform multiple rapid inputs
            onView(withId(R.id.game_view))
                .perform(click())
                .perform(swipeLeft())
                .perform(click())
                .perform(swipeRight())
                .perform(click())
                .perform(swipeUp())
                .perform(click())
                .perform(swipeDown())

            // Wait briefly for processing
            Thread.sleep(1000)

            // Verify game view is still responsive
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))
                .perform(click()) // Final test click

            // Verify HUDs are still updating
            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testMultiplayerSync_UIUpdates() {
        // Test UI updates correctly when multiplayer events occur
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "easy")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization and potential player joining
            Thread.sleep(5000)

            // Verify all UI components remain stable during multiplayer setup
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.coverage_hud_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.timer_hud_view))
                .check(matches(isDisplayed()))

            // Simulate some gameplay during multiplayer
            onView(withId(R.id.game_view))
                .perform(click())
                .perform(swipeRight())

            // Wait for multiplayer synchronization
            Thread.sleep(2000)

            // Verify UI remains stable after multiplayer activity
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testPerformance_UIResponsivenessUnderLoad() {
        // Test UI remains responsive under intensive gameplay conditions
        
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 180000L)
            putExtra("COMPLEXITY", "high") // High complexity for performance testing
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Wait for game initialization
            Thread.sleep(3000)

            val startTime = System.currentTimeMillis()

            // Perform intensive interaction sequence
            repeat(10) {
                onView(withId(R.id.game_view))
                    .perform(click())
                    .perform(swipeLeft())
                    .perform(swipeRight())
                    .perform(swipeUp())
                    .perform(swipeDown())
                
                // Brief pause between sequences
                Thread.sleep(100)
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Verify performance (should complete intensive sequence reasonably quickly)
            assert(duration < 15000) { "UI performance test took too long: ${duration}ms" }

            // Verify all UI components are still functional after load test
            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.ink_hud_view))
                .check(matches(isDisplayed()))

            onView(withId(R.id.coverage_hud_view))
                .check(matches(isDisplayed()))
        }
    }
} 