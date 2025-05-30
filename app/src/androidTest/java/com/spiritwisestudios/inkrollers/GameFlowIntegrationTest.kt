package com.spiritwisestudios.inkrollers

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GameFlowIntegrationTest {

    private val database = FirebaseDatabase.getInstance("http://127.0.0.1:9001?ns=inkrollers-13595-default-rtdb")

    @get:Rule
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Before
    fun setup() {
        // Ensure emulator is running and clear database before each test for a clean slate
        try {
            database.useEmulator("127.0.0.1", 9001)
            // Clear the entire database (or specific nodes if preferred)
            database.reference.setValue(null)
                .addOnSuccessListener { println("Firebase Realtime Database emulator data cleared.") }
                .addOnFailureListener { e -> println("Failed to clear Firebase Realtime Database emulator data: ${'$'}{e.message}") }
        } catch (e: IllegalStateException) {
            // Emulator might have already been set, which is fine.
            println("Firebase Database emulator already configured or error during setup: ${'$'}{e.message}")
        }
        // It's good practice to also sign out any existing Firebase Auth user if tests involve auth
        // com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
    }
    
    @After
    fun cleanup() {
        // Optional: Clear database after each test if not done in @Before,
        // or if specific data needs to persist only within a test.
        // database.reference.setValue(null)
    }

    private fun createGameInFirebaseDirectly(
        gameId: String,
        hostPlayerState: PlayerState,
        durationMs: Long = 180000L, // 3 minutes
        complexity: String = HomeActivity.COMPLEXITY_HIGH,
        gameMode: String = HomeActivity.GAME_MODE_COVERAGE,
        isPrivate: Boolean = false
    ) {
        val gameRef = database.reference.child(MultiplayerManager.GAMES_NODE).child(gameId)
        val mazeSeed = System.currentTimeMillis()

        val initialGameData = mapOf(
            "players" to mapOf(
                "player0" to hostPlayerState.copy(mazeSeed = mazeSeed) // Ensure host has the seed
            ),
            "mazeSeed" to mazeSeed,
            "matchDurationMs" to durationMs,
            "mazeComplexity" to complexity,
            "gameMode" to gameMode,
            "isPrivate" to isPrivate,
            MultiplayerManager.CREATED_AT_NODE to ServerValue.TIMESTAMP,
            MultiplayerManager.LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP,
            "started" to false,
            "playerCount" to 1L
        )
        var success = false
        val latch = java.util.concurrent.CountDownLatch(1)
        gameRef.setValue(initialGameData)
            .addOnSuccessListener {
                println("Successfully created game $gameId directly in Firebase for testing.")
                success = true
                latch.countDown()
            }
            .addOnFailureListener {
                println("Failed to create game $gameId directly in Firebase: ${it.message}")
                latch.countDown()
            }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS) // Wait for Firebase operation
        assert(success) { "Firebase game creation failed for $gameId" }
    }

    // Placeholder test - will be expanded
    @Test
    fun hostGameAndSeeWaitingDialog() {
        // 1. Click Play button
        onView(withId(R.id.button_play)).perform(click())
        Thread.sleep(500) // Wait for submenu animation

        // 2. Click Host Game button
        onView(withId(R.id.button_host_game)).perform(click())
        Thread.sleep(500) // Wait for dialogs

        // 3. In the first dialog (time limit), click the first option ("3 minutes")
        // Dialogs are harder to interact with directly via ID. We'll use text.
        onView(withText("Set Time Limit")).check(matches(isDisplayed()))
        onView(withText("3 minutes")).perform(click())
        Thread.sleep(500)

        // 4. In the second dialog (complexity), click "High"
        onView(withText("Set Maze Complexity")).check(matches(isDisplayed()))
        onView(withText("High")).perform(click())
        Thread.sleep(500)

        // 5. In the third dialog (game mode), click "Coverage"
        onView(withText("Select Game Mode")).check(matches(isDisplayed()))
        onView(withText("Coverage")).perform(click())
        Thread.sleep(500)
        
        // 6. In the fourth dialog (match type), click "Public" (default selected) then "Host Game"
        onView(withText("Select Match Type")).check(matches(isDisplayed()))
        onView(withText("Public (Joinable by random)")).check(matches(isChecked())) // Verify default
        onView(withText("Host Game")).perform(click())

        // MainActivity should now be active and showing a "Waiting for other players..." dialog
        // This requires checking for a dialog.
        // Espresso's default view hierarchy search doesn't always find dialogs well.
        // We might need a more robust way to check for dialogs or their content.
        // For now, we'll check if the "Waiting" title is displayed on a dialog.
        // This relies on the dialog being an AlertDialog.
        Thread.sleep(2000) // Wait for MainActivity to load and Firebase to connect
        onView(withText("Waiting")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Waiting for other players to join...")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun joinGameByIdAndSeeWaitingDialog() {
        val testGameId = "TEST01"
        val hostUid = "testHostUid"
        val hostPlayerState = PlayerState(
            normX = 0.5f, normY = 0.5f, color = android.graphics.Color.GREEN,
            playerName = "TestHost", uid = hostUid, active = true
        )

        createGameInFirebaseDirectly(testGameId, hostPlayerState)

        // 1. Click Play button
        onView(withId(R.id.button_play)).perform(click())
        Thread.sleep(500) // Wait for submenu animation

        // 2. Type the Game ID
        onView(withId(R.id.editText_game_id)).perform(typeText(testGameId), closeSoftKeyboard())
        Thread.sleep(500)

        // 3. Click Join Game button
        onView(withId(R.id.button_join_game)).perform(click())

        // MainActivity should now be active and showing a "Waiting for host to start..." dialog
        Thread.sleep(2000) // Wait for MainActivity to load and Firebase to connect
        onView(withText("Waiting")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Waiting for host to start...")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun joinRandomGameAndSeeWaitingDialog() {
        val randomGameId = "RANDJOINTEST"
        val hostUid = "randomHostUid"
        val hostPlayerState = PlayerState(
            normX = 0.5f, normY = 0.5f, color = android.graphics.Color.BLUE,
            playerName = "RandomHost", uid = hostUid, active = true
        )

        // Create a public game that can be joined randomly
        createGameInFirebaseDirectly(randomGameId, hostPlayerState, isPrivate = false)

        // 1. Click Play button
        onView(withId(R.id.button_play)).perform(click())
        Thread.sleep(500) // Wait for submenu animation

        // 2. Ensure Game ID field is empty (it should be by default)
        onView(withId(R.id.editText_game_id)).check(matches(withText("")))

        // 3. Click Join Game button
        onView(withId(R.id.button_join_game)).perform(click())

        // MainActivity should now be active. 
        // It will briefly show a "Searching..." toast, then connect and show "Waiting for host to start..."
        // We will wait a bit longer here to allow for the random join logic and Firebase updates.
        Thread.sleep(3000) // Increased wait time for random join
        onView(withText("Waiting")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Waiting for host to start...")).inRoot(isDialog()).check(matches(isDisplayed()))
    }
} 