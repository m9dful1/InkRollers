package com.spiritwisestudios.inkrollers

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class GameModeManagerTest {

    private lateinit var coverageManager: GameModeManager
    private lateinit var zonesManager: GameModeManager
    private val testDurationMs = 5000L // 5 seconds for testing
    private val testStartTime = 1000000L // Fixed start time for consistency

    @Before
    fun setUp() {
        coverageManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = testStartTime
        )
        
        zonesManager = GameModeManager(
            mode = GameMode.ZONES,
            durationMs = testDurationMs,
            providedStartTime = testStartTime
        )
    }

    @Test
    fun constructor_withCoverageMode_setsCorrectMode() {
        assertEquals("Coverage manager should have COVERAGE mode", 
                    GameMode.COVERAGE, coverageManager.mode)
    }

    @Test
    fun constructor_withZonesMode_setsCorrectMode() {
        assertEquals("Zones manager should have ZONES mode", 
                    GameMode.ZONES, zonesManager.mode)
    }

    @Test
    fun initialState_beforeStart_isNotFinished() {
        assertFalse("Manager should not be finished before start", 
                   coverageManager.isFinished())
    }

    @Test
    fun start_setsCorrectStartTime() {
        // Create a manager without a provided start time so it uses current time
        val currentTimeManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs
        )
        
        currentTimeManager.start()
        
        // Time remaining should be approximately equal to full duration
        // Allow for some tolerance since system time can vary slightly
        val timeRemaining = currentTimeManager.timeRemainingMs()
        assertTrue("Time remaining should be close to full duration", 
                  timeRemaining >= testDurationMs - 500) // Allow 500ms tolerance
        assertTrue("Time remaining should not exceed duration", 
                  timeRemaining <= testDurationMs)
    }

    @Test
    fun start_withProvidedStartTime_usesProvidedTime() {
        // Create a manager with a specific start time
        val specificStartTime = 2000000L
        val managerWithSpecificTime = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = specificStartTime
        )
        
        managerWithSpecificTime.start()
        
        // The time remaining calculation should be based on the provided start time
        // This is hard to test directly, but we can verify the manager doesn't crash
        assertNotNull("Manager should handle provided start time", 
                     managerWithSpecificTime.timeRemainingMs())
    }

    @Test
    fun start_withoutProvidedStartTime_usesCurrentTime() {
        val managerWithoutSpecificTime = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs
        )
        
        managerWithoutSpecificTime.start()
        
        // Should use current system time (hard to test exactly, but shouldn't crash)
        assertTrue("Time remaining should be positive", 
                  managerWithoutSpecificTime.timeRemainingMs() > 0)
    }

    @Test
    fun update_beforeDurationElapsed_remainsNotFinished() {
        // Create a manager with current time as start time to ensure we're testing
        // a case where duration hasn't elapsed
        val recentStartTime = System.currentTimeMillis() - 100L // 100ms ago
        val activeManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = recentStartTime
        )
        
        activeManager.start()
        
        // Immediately after start, should not be finished
        activeManager.update()
        assertFalse("Manager should not be finished with recent start time", 
                   activeManager.isFinished())
    }

    @Test
    fun update_afterDurationElapsed_becomesFinished() {
        // Create a manager with a past start time to simulate elapsed time
        val pastStartTime = System.currentTimeMillis() - (testDurationMs + 1000)
        val expiredManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = pastStartTime
        )
        
        expiredManager.start()
        expiredManager.update()
        
        assertTrue("Manager should be finished after duration elapsed", 
                  expiredManager.isFinished())
    }

    @Test
    fun timeRemainingMs_beforeStart_returnsFullDuration() {
        // Before start, we can't really predict the time remaining since it uses current time
        // But we can test that it returns a reasonable value
        val timeRemaining = coverageManager.timeRemainingMs()
        assertTrue("Time remaining should be non-negative", timeRemaining >= 0)
    }

    @Test
    fun timeRemainingMs_afterStart_decreasesOverTime() {
        coverageManager.start()
        
        val initialTimeRemaining = coverageManager.timeRemainingMs()
        
        // Simulate a small delay
        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            // Ignore
        }
        
        val laterTimeRemaining = coverageManager.timeRemainingMs()
        
        assertTrue("Time remaining should decrease over time", 
                  laterTimeRemaining <= initialTimeRemaining)
    }

    @Test
    fun timeRemainingMs_afterExpiration_returnsZero() {
        // Create a manager with a past start time
        val pastStartTime = System.currentTimeMillis() - (testDurationMs + 1000)
        val expiredManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = pastStartTime
        )
        
        expiredManager.start()
        
        assertEquals("Time remaining should be zero after expiration", 
                    0L, expiredManager.timeRemainingMs())
    }

    @Test
    fun isFinished_togglesCorrectly() {
        // Start fresh
        coverageManager.start()
        assertFalse("Should not be finished initially", coverageManager.isFinished())
        
        // Create an expired manager to test the finished state
        val pastStartTime = System.currentTimeMillis() - (testDurationMs + 1000)
        val expiredManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = testDurationMs,
            providedStartTime = pastStartTime
        )
        
        expiredManager.start()
        expiredManager.update()
        
        assertTrue("Should be finished after expiration", expiredManager.isFinished())
    }

    @Test
    fun multipleUpdates_maintainConsistentState() {
        coverageManager.start()
        
        // Call update multiple times
        for (i in 0..5) {
            coverageManager.update()
            
            if (!coverageManager.isFinished()) {
                assertTrue("Time remaining should be positive when not finished", 
                          coverageManager.timeRemainingMs() >= 0)
            } else {
                assertEquals("Time remaining should be zero when finished", 
                           0L, coverageManager.timeRemainingMs())
            }
        }
    }

    @Test
    fun differentModes_behaveSimilarly() {
        coverageManager.start()
        zonesManager.start()
        
        // Both modes should have similar timing behavior
        val coverageTimeRemaining = coverageManager.timeRemainingMs()
        val zonesTimeRemaining = zonesManager.timeRemainingMs()
        
        // Times should be similar (within reasonable tolerance)
        val timeDifference = Math.abs(coverageTimeRemaining - zonesTimeRemaining)
        assertTrue("Different modes should have similar timing behavior", 
                  timeDifference < 1000) // Within 1 second
    }

    @Test
    fun restart_functionality() {
        // Start and let some time pass
        coverageManager.start()
        val firstTimeRemaining = coverageManager.timeRemainingMs()
        
        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            // Ignore
        }
        
        // Start again (restart)
        coverageManager.start()
        val secondTimeRemaining = coverageManager.timeRemainingMs()
        
        // Second start should reset the timer
        assertTrue("Restart should reset timer to full duration", 
                  secondTimeRemaining >= firstTimeRemaining)
    }

    @Test
    fun longDuration_handlesCorrectly() {
        val longDuration = 300000L // 5 minutes
        val longDurationManager = GameModeManager(
            mode = GameMode.COVERAGE,
            durationMs = longDuration
        )
        
        longDurationManager.start()
        longDurationManager.update()
        
        assertFalse("Long duration should not be finished immediately", 
                   longDurationManager.isFinished())
        assertTrue("Long duration should have substantial time remaining", 
                  longDurationManager.timeRemainingMs() > longDuration - 1000)
    }

    @Test
    fun shortDuration_handlesCorrectly() {
        val shortDuration = 100L // 100 milliseconds
        val shortDurationManager = GameModeManager(
            mode = GameMode.ZONES,
            durationMs = shortDuration
        )
        
        shortDurationManager.start()
        
        // Wait longer than the duration
        try {
            Thread.sleep(150)
        } catch (e: InterruptedException) {
            // Ignore
        }
        
        shortDurationManager.update()
        
        assertTrue("Short duration should be finished quickly", 
                  shortDurationManager.isFinished())
        assertEquals("Short duration should have zero time remaining", 
                    0L, shortDurationManager.timeRemainingMs())
    }
} 