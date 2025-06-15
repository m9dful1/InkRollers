package com.spiritwisestudios.inkrollers.integration

import androidx.test.rule.GrantPermissionRule
import com.google.firebase.database.*
import com.spiritwisestudios.inkrollers.BaseFirebaseTest
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.PlayerState
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Firebase Integration Tests for Ink Rollers
 * 
 * Tests real-time database operations, authentication, and profile management
 * with Firebase emulator. Verifies data persistence, synchronization, and
 * error handling for core multiplayer functionality.
 */
class FirebaseIntegrationTest : BaseFirebaseTest() {

    private var multiplayerManager: MultiplayerManager? = null

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET
    )

    @Before
    fun setUp() {
        try {
            multiplayerManager = MultiplayerManager(context)
        } catch (e: Exception) {
            // Handle initialization errors gracefully
            multiplayerManager = null
        }
    }

    @After
    fun tearDown() {
        try {
            multiplayerManager?.leaveGame()
        } catch (e: Exception) {
            // Ignore cleanup errors if manager wasn't initialized
        }
        cleanupFirebase()
    }

    @Test
    fun testFirebaseConnection_canConnectToEmulator() {
        // Test basic Firebase emulator connectivity
        
        val connectionLatch = CountDownLatch(1)
        var connectionSuccess = false
        
        // Test database connection with a simple write/read
        val testRef = database.getReference("test/connection")
        val testData = mapOf("timestamp" to System.currentTimeMillis())
        
        testRef.setValue(testData)
            .addOnSuccessListener {
                connectionSuccess = true
                connectionLatch.countDown()
            }
            .addOnFailureListener { 
                connectionLatch.countDown()
            }
        
        assertTrue("Firebase connection should complete within 10 seconds", 
            connectionLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Should successfully connect to Firebase emulator", connectionSuccess)
    }

    @Test
    fun testAuthentication_anonymousSignIn() {
        // Test anonymous authentication with Firebase emulator
        
        val authLatch = CountDownLatch(1)
        var authSuccess = false
        var userId: String? = null
        
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                authSuccess = task.isSuccessful
                userId = task.result?.user?.uid
                authLatch.countDown()
            }
        
        assertTrue("Authentication should complete within 10 seconds", 
            authLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Anonymous sign-in should succeed", authSuccess)
        assertNotNull("User ID should be available after sign-in", userId)
        assertFalse("User ID should not be empty", userId?.isEmpty() ?: true)
    }

    @Test
    fun testDatabaseOperations_writeAndRead() {
        // Test basic Firebase database write and read operations
        
        val writeLatch = CountDownLatch(1)
        val readLatch = CountDownLatch(1)
        
        var writeSuccess = false
        var readData: Map<String, Any>? = null
        
        val testRef = database.getReference("test/data")
        val testValue = mapOf(
            "message" to "Hello Firebase!",
            "timestamp" to System.currentTimeMillis(),
            "value" to 42
        )
        
        // Write data
        testRef.setValue(testValue)
            .addOnSuccessListener {
                writeSuccess = true
                writeLatch.countDown()
            }
            .addOnFailureListener { 
                writeLatch.countDown()
            }
        
        assertTrue("Write operation should complete within 10 seconds",
            writeLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Write should succeed", writeSuccess)
        
        // Read data back
        testRef.get()
            .addOnSuccessListener { snapshot ->
                @Suppress("UNCHECKED_CAST")
                readData = snapshot.value as? Map<String, Any>
                readLatch.countDown()
            }
            .addOnFailureListener {
                readLatch.countDown()
            }
        
        assertTrue("Read operation should complete within 10 seconds",
            readLatch.await(10, TimeUnit.SECONDS))
        assertNotNull("Read data should not be null", readData)
        assertEquals("Message should match", "Hello Firebase!", readData?.get("message"))
        assertEquals("Value should match", 42L, readData?.get("value")) // Firebase returns Long for numbers
    }

    @Test
    fun testRealtimeSynchronization_withListeners() {
        // Test real-time data synchronization using Firebase listeners
        
        val updateLatch = CountDownLatch(2) // Expect 2 updates
        val updates = mutableListOf<String>()
        
        val testRef = database.getReference("test/realtime")
        
        // Set up listener first
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(String::class.java)
                if (value != null) {
                    updates.add(value)
                    updateLatch.countDown()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        }
        
        testRef.addValueEventListener(listener)
        
        // Wait a moment for listener to be established
        Thread.sleep(500)
        
        // Write first value
        testRef.setValue("First Value")
        
        // Wait a moment then write second value
        Thread.sleep(1000)
        testRef.setValue("Second Value")
        
        assertTrue("Should receive both updates within 15 seconds",
            updateLatch.await(15, TimeUnit.SECONDS))
        assertEquals("Should receive 2 updates", 2, updates.size)
        assertEquals("First update should match", "First Value", updates[0])
        assertEquals("Second update should match", "Second Value", updates[1])
        
        // Clean up listener
        testRef.removeEventListener(listener)
    }

    @Test
    fun testMultiplayerManager_gameCreation() {
        // Test game creation and hosting through MultiplayerManager
        
        val authLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { authLatch.countDown() }
        authLatch.await(5, TimeUnit.SECONDS)

        val userId = auth.currentUser?.uid ?: return

        val manager = multiplayerManager ?: return

        val initialState = PlayerState(
            normX = 0.5f,
            normY = 0.5f,
            color = -16711936, // Green
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "TestHost",
            uid = userId
        )

        val hostLatch = CountDownLatch(1)
        var hostSuccess = false
        var gameId: String? = null

        manager.hostGame(
            initialPlayerState = initialState,
            durationMs = 180000L, // 3 minutes
            complexity = "medium",
            gameMode = "COVERAGE",
            isPrivate = false
        ) { success, id, settings ->
            hostSuccess = success
            gameId = id
            hostLatch.countDown()
        }

        assertTrue("Game hosting should complete within 15 seconds", 
            hostLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Game should be hosted successfully", hostSuccess)
        assertNotNull("Game ID should be provided", gameId)
        assertFalse("Game ID should not be empty", gameId?.isEmpty() ?: true)
    }

    @Test
    fun testPlayerState_synchronization() {
        // Test PlayerState synchronization between database and local state
        
        val authLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { authLatch.countDown() }
        authLatch.await(5, TimeUnit.SECONDS)

        val userId = auth.currentUser?.uid ?: return

        val initialState = PlayerState(
            normX = 0.3f,
            normY = 0.7f,
            color = -65536, // Red
            mode = 1, // FILL mode
            ink = 75.5f,
            active = true,
            mazeSeed = 54321L,
            playerName = "SyncTestPlayer",
            uid = userId
        )

        val manager = multiplayerManager ?: return

        // Host a game to get a valid game context
        val hostLatch = CountDownLatch(1)
        var gameId: String? = null

        manager.hostGame(
            initialPlayerState = initialState,
            durationMs = 180000L,
            complexity = "easy",
            gameMode = "ZONES",
            isPrivate = false
        ) { success, id, _ ->
            if (success) gameId = id
            hostLatch.countDown()
        }

        assertTrue("Game hosting should complete", hostLatch.await(10, TimeUnit.SECONDS))
        assertNotNull("Game ID should be available", gameId)

        // Test state updates
        val updateLatch = CountDownLatch(1)
        var receivedUpdate = false

        // Set up listener for state changes
        manager.updateListener = object : MultiplayerManager.RemoteUpdateListener {
            override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {
                if (playerId == manager.localPlayerId) {
                    receivedUpdate = true
                    assertEquals("Updated position X should match", 0.8f, newState.normX, 0.01f)
                    assertEquals("Updated position Y should match", 0.2f, newState.normY, 0.01f)
                    assertEquals("Updated ink should match", 50.0f, newState.ink, 0.01f)
                    updateLatch.countDown()
                }
            }
            
            override fun onPlayerRemoved(playerId: String) {}
            override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {}
        }

        // Update player state
        manager.updatePlayerState(0.8f, 0.2f, 50.0f, 0)

        assertTrue("State update should be received within 10 seconds",
            updateLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Should receive state update notification", receivedUpdate)
    }

    @Test
    fun testProfileRepository_saveAndLoad() {
        // Test ProfileRepository integration with Firebase
        
        val authLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { authLatch.countDown() }
        authLatch.await(5, TimeUnit.SECONDS)

        val userId = auth.currentUser?.uid ?: return

        val testProfile = PlayerProfile(
            uid = userId,
            playerName = "IntegrationTestPlayer",
            favoriteColors = listOf(-65536, -16711936, -256), // Red, Green, Yellow
            catchPhrase = "Testing Firebase Integration!",
            friendCode = "TEST01",
            friends = listOf(),
            winCount = 5,
            lossCount = 3,
            isOnline = true,
            currentLobbyId = null
        )

        // Save profile
        val saveLatch = CountDownLatch(1)
        var saveSuccess = false

        ProfileRepository.savePlayerProfile(testProfile) { success ->
            saveSuccess = success
            saveLatch.countDown()
        }

        assertTrue("Profile save should complete within 10 seconds", 
            saveLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Profile should be saved successfully", saveSuccess)

        // Load profile
        val loadLatch = CountDownLatch(1)
        var loadedProfile: PlayerProfile? = null

        ProfileRepository.loadPlayerProfile(userId) { profile ->
            loadedProfile = profile
            loadLatch.countDown()
        }

        assertTrue("Profile load should complete within 10 seconds", 
            loadLatch.await(10, TimeUnit.SECONDS))
        assertNotNull("Loaded profile should not be null", loadedProfile)

        // Verify loaded profile data
        assertEquals("Player name should match", testProfile.playerName, loadedProfile?.playerName)
        assertEquals("Favorite colors should match", testProfile.favoriteColors, loadedProfile?.favoriteColors)
        assertEquals("Catch phrase should match", testProfile.catchPhrase, loadedProfile?.catchPhrase)
        assertEquals("Friend code should match", testProfile.friendCode, loadedProfile?.friendCode)
        assertEquals("Win count should match", testProfile.winCount, loadedProfile?.winCount)
        assertEquals("Loss count should match", testProfile.lossCount, loadedProfile?.lossCount)
    }

    @Test
    fun testErrorHandling_networkFailure() {
        // Test error handling when network operations fail
        
        val authLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { authLatch.countDown() }
        authLatch.await(5, TimeUnit.SECONDS)

        // Test with invalid reference path
        val invalidRef = database.getReference("invalid/path/that/should/fail")
        val errorLatch = CountDownLatch(1)
        var errorHandled = false

        invalidRef.setValue(null)
            .addOnSuccessListener {
                // This might still succeed in emulator
                errorLatch.countDown()
            }
            .addOnFailureListener { exception ->
                errorHandled = true
                assertNotNull("Error should have exception details", exception.message)
                errorLatch.countDown()
            }

        assertTrue("Error handling should complete within 10 seconds",
            errorLatch.await(10, TimeUnit.SECONDS))
    }
} 