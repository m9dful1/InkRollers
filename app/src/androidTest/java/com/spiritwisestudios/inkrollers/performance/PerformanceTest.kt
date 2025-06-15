package com.spiritwisestudios.inkrollers.performance

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
/**
 * Performance testing suite for system stress testing and optimization.
 */
class PerformanceTest : BaseFirebaseTest() {

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
        System.gc()
        Thread.sleep(1000)
    }

    @Test
    fun testUIResponsiveness_rapidInteraction() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 120000L)
            putExtra("COMPLEXITY", "high")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            Thread.sleep(3000)

            val startTime = System.currentTimeMillis()
            var actionCount = 0

            while (System.currentTimeMillis() - startTime < 30000) {
                onView(withId(R.id.game_view))
                    .perform(click())
                    .perform(swipeLeft())
                    .perform(swipeRight())

                actionCount++

                if (actionCount % 20 == 0) {
                    onView(withId(R.id.game_view))
                        .check(matches(isDisplayed()))
                    Thread.sleep(100)
                }
            }

            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))

            assert(actionCount > 200) { "Performance test incomplete: $actionCount actions" }
        }
    }

    @Test
    fun testMemoryUsage_extendedGameplay() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("MODE", HomeActivity.MODE_HOST)
            putExtra("DURATION_MS", 300000L)
            putExtra("COMPLEXITY", "high")
            putExtra("GAME_MODE", "COVERAGE")
            putExtra("IS_PRIVATE", false)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            Thread.sleep(3000)

            val runtime = Runtime.getRuntime()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()
            
            repeat(200) { iteration ->
                onView(withId(R.id.game_view))
                    .perform(click())
                    .perform(swipeLeft())
                    .perform(swipeRight())

                if (iteration % 50 == 0) {
                    val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                    val memoryIncrease = currentMemory - initialMemory
                    
                    assert(memoryIncrease < 30 * 1024 * 1024) { 
                        "Memory increased too much: ${memoryIncrease/1024/1024}MB" 
                    }

                    onView(withId(R.id.game_view))
                        .check(matches(isDisplayed()))

                    Thread.sleep(200)
                }

                Thread.sleep(50)
            }

            onView(withId(R.id.game_view))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testStartupPerformance() {
        val startTime = System.currentTimeMillis()
        
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            onView(withId(R.id.button_host_game))
                .check(matches(isDisplayed()))

            val startupTime = System.currentTimeMillis() - startTime

            onView(withId(R.id.button_join_game))
                .check(matches(isDisplayed()))

            assert(startupTime < 5000) { "App startup too slow: ${startupTime}ms" }
        }
    }
} 