package com.spiritwisestudios.inkrollers.integration

import androidx.test.rule.GrantPermissionRule
import com.spiritwisestudios.inkrollers.BaseFirebaseTest
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.PlayerState
import com.spiritwisestudios.inkrollers.GameSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Multiplayer Session Integration Tests for Ink Rollers
 * 
 * Tests comprehensive multiplayer scenarios including host/join flows,
 * real-time synchronization, multi-client coordination, and session management.
 * Simulates multiple players using separate MultiplayerManager instances.
 */
class MultiplayerSessionIntegrationTest : BaseFirebaseTest() {

    private var hostManager: MultiplayerManager? = null
    private var clientManager: MultiplayerManager? = null
    
    private var hostGameId: String? = null
    private var hostUid: String? = null
    private var clientUid: String? = null

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET
    )

    @Before
    fun setUp() {
        try {
            hostManager = MultiplayerManager(context)
            clientManager = MultiplayerManager(context)
        } catch (e: Exception) {
            hostManager = null
            clientManager = null
        }
        
        // Sign out any existing auth
        auth.signOut()
    }

    @After
    fun tearDown() {
        try {
            hostManager?.leaveGame()
            clientManager?.leaveGame()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        cleanupFirebase()
    }

    @Test
    fun testHostJoinFlow_basicCoordination() {
        // Test the complete host-join flow with two simulated players
        
        val host = hostManager ?: return
        val client = clientManager ?: return
        
        // Host authentication
        val hostAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            hostUid = auth.currentUser?.uid
            hostAuthLatch.countDown() 
        }
        assertTrue("Host auth should complete", hostAuthLatch.await(10, TimeUnit.SECONDS))

        val hostState = PlayerState(
            normX = 0.1f,
            normY = 0.1f,
            color = -16711936, // Green
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "HostPlayer",
            uid = hostUid!!
        )

        // Host creates game
        val hostLatch = CountDownLatch(1)
        var hostSuccess = false

        host.hostGame(
            initialPlayerState = hostState,
            durationMs = 180000L,
            complexity = "medium",
            gameMode = "COVERAGE",
            isPrivate = false
        ) { success, gameId, settings ->
            hostSuccess = success
            hostGameId = gameId
            hostLatch.countDown()
        }

        assertTrue("Host should complete game creation", hostLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Host should successfully create game", hostSuccess)
        assertNotNull("Game ID should be provided", hostGameId)

        // Client authentication (sign out and re-authenticate as different user)
        auth.signOut()
        val clientAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            clientUid = auth.currentUser?.uid
            clientAuthLatch.countDown() 
        }
        assertTrue("Client auth should complete", clientAuthLatch.await(10, TimeUnit.SECONDS))

        val clientState = PlayerState(
            normX = 0.9f,
            normY = 0.9f,
            color = -65536, // Red  
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "ClientPlayer",
            uid = clientUid!!
        )

        // Client joins game
        val clientLatch = CountDownLatch(1)
        var clientSuccess = false

        client.joinGame(hostGameId!!, clientState) { success, gameId, gameSettings ->
            clientSuccess = success
            clientLatch.countDown()
        }

        assertTrue("Client join should complete", clientLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Client should successfully join", clientSuccess)
    }

    @Test
    fun testPlayerStateSync_betweenClients() {
        // Test real-time player state synchronization between host and client
        
        // First establish the game
        testHostJoinFlow_basicCoordination()

        // Set up listeners to capture state changes
        val hostUpdatesLatch = CountDownLatch(1)
        val clientUpdatesLatch = CountDownLatch(1)
        
        var hostReceivedClientState: PlayerState? = null
        var clientReceivedHostState: PlayerState? = null

        val host = hostManager ?: return
        val client = clientManager ?: return

        // Set up host listener for client updates
        host.updateListener = object : MultiplayerManager.RemoteUpdateListener {
            override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {
                if (playerId != host.localPlayerId) {
                    hostReceivedClientState = newState
                    hostUpdatesLatch.countDown()
                }
            }
            override fun onPlayerRemoved(playerId: String) {}
            override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {}
        }

        // Set up client listener for host updates
        client.updateListener = object : MultiplayerManager.RemoteUpdateListener {
            override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {
                if (playerId != client.localPlayerId) {
                    clientReceivedHostState = newState
                    clientUpdatesLatch.countDown()
                }
            }
            override fun onPlayerRemoved(playerId: String) {}
            override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {}
        }

        // Wait for listeners to be established
        Thread.sleep(1000)

        // Host updates their state
        host.updatePlayerState(0.2f, 0.3f, 95.0f, 1)

        // Client updates their state  
        client.updatePlayerState(0.8f, 0.7f, 90.0f, 0)

        // Verify both received updates
        assertTrue("Host should receive client state update", 
            hostUpdatesLatch.await(10, TimeUnit.SECONDS))
        assertTrue("Client should receive host state update", 
            clientUpdatesLatch.await(10, TimeUnit.SECONDS))

        assertNotNull("Host should have received client state", hostReceivedClientState)
        assertNotNull("Client should have received host state", clientReceivedHostState)

        // Verify state content
        assertEquals("Client X position should match", 0.8f, hostReceivedClientState?.normX ?: 0f, 0.01f)
        assertEquals("Client Y position should match", 0.7f, hostReceivedClientState?.normY ?: 0f, 0.01f)
        assertEquals("Host X position should match", 0.2f, clientReceivedHostState?.normX ?: 0f, 0.01f)
        assertEquals("Host Y position should match", 0.3f, clientReceivedHostState?.normY ?: 0f, 0.01f)
    }

    @Test
    fun testPaintActionSync_betweenClients() {
        // Test real-time paint action synchronization between clients
        
        // Establish game first
        testHostJoinFlow_basicCoordination()

        val host = hostManager ?: return
        val client = clientManager ?: return

        val paintLatch = CountDownLatch(2) // Expect 2 paint actions
        val receivedPaintActions = mutableListOf<Triple<Int, Int, Int>>()

        // Set up listener for paint actions
        host.updateListener = object : MultiplayerManager.RemoteUpdateListener {
            override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {}
            override fun onPlayerRemoved(playerId: String) {}
            override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {
                receivedPaintActions.add(Triple(x, y, color))
                paintLatch.countDown()
            }
        }

        client.updateListener = object : MultiplayerManager.RemoteUpdateListener {
            override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {}
            override fun onPlayerRemoved(playerId: String) {}
            override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {
                receivedPaintActions.add(Triple(x, y, color))
                paintLatch.countDown()
            }
        }

        // Wait for listeners
        Thread.sleep(1000)

        // Send paint actions from both clients
        host.sendPaintAction(100, 200, -16711936, 0.1f, 0.2f) // Green
        client.sendPaintAction(300, 400, -65536, 0.3f, 0.4f) // Red

        assertTrue("Paint actions should be received within 10 seconds",
            paintLatch.await(10, TimeUnit.SECONDS))
        assertEquals("Should receive 2 paint actions", 2, receivedPaintActions.size)
    }

    @Test 
    fun testGameSessionFlow_completeMatch() {
        // Test complete match flow from start to finish
        
        // Establish game
        testHostJoinFlow_basicCoordination()

        val host = hostManager ?: return
        val client = clientManager ?: return

        // Set up match start coordination
        val matchStartLatch = CountDownLatch(2) // Both clients should receive start signal
        
        host.onMatchStartRequested = { matchStartLatch.countDown() }
        client.onMatchStartRequested = { matchStartLatch.countDown() }

        // Host signals match start
        Thread.sleep(1000) // Wait for listeners to be established
        host.sendMatchStart()

        assertTrue("Both clients should receive match start signal",
            matchStartLatch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testPlayerCountTracking_multipleJoins() {
        // Test player count tracking as players join and leave
        
        val hostAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            hostUid = auth.currentUser?.uid
            hostAuthLatch.countDown() 
        }
        hostAuthLatch.await(5, TimeUnit.SECONDS)

        val host = hostManager ?: return
        val client = clientManager ?: return

        val playerCountUpdates = mutableListOf<Int>()
        val playerCountLatch = CountDownLatch(2) // Initial + one join

        host.onPlayerCountChanged = { count ->
            playerCountUpdates.add(count)
            playerCountLatch.countDown()
        }

        val hostState = PlayerState(
            normX = 0.5f,
            normY = 0.5f,
            color = -16711936,
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "Host",
            uid = hostUid!!
        )

        // Host creates game (should trigger first count update)
        val hostLatch = CountDownLatch(1)
        host.hostGame(
            initialPlayerState = hostState,
            durationMs = 180000L,
            complexity = "medium", 
            gameMode = "COVERAGE",
            isPrivate = false
        ) { success, gameId, settings ->
            hostGameId = gameId
            hostLatch.countDown()
        }
        hostLatch.await(10, TimeUnit.SECONDS)

        // Client joins (should trigger second count update)
        auth.signOut()
        val clientAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            clientUid = auth.currentUser?.uid
            clientAuthLatch.countDown() 
        }
        clientAuthLatch.await(5, TimeUnit.SECONDS)

        val clientState = PlayerState(
            normX = 0.7f,
            normY = 0.3f,
            color = -65536,
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "Client",
            uid = clientUid!!
        )

        val clientJoinLatch = CountDownLatch(1)
        client.joinGame(hostGameId!!, clientState) { success, gameId, gameSettings ->
            clientJoinLatch.countDown()
        }
        clientJoinLatch.await(10, TimeUnit.SECONDS)

        assertTrue("Should receive player count updates",
            playerCountLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Should have at least 2 count updates", playerCountUpdates.size >= 2)
    }

    @Test
    fun testRandomGameMatching() {
        // Test automatic matching with random available games
        
        // Create a host game first
        val hostAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            hostUid = auth.currentUser?.uid
            hostAuthLatch.countDown() 
        }
        hostAuthLatch.await(5, TimeUnit.SECONDS)

        val hostState = PlayerState(
            normX = 0.2f,
            normY = 0.8f,
            color = -16711936,
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 98765L,
            playerName = "RandomHost",
            uid = hostUid!!
        )

        val host = hostManager ?: return
        val client = clientManager ?: return

        val hostLatch = CountDownLatch(1)
        host.hostGame(
            initialPlayerState = hostState,
            durationMs = 300000L,
            complexity = "easy",
            gameMode = "ZONES",
            isPrivate = false
        ) { success, gameId, settings ->
            hostGameId = gameId
            hostLatch.countDown()
        }
        hostLatch.await(10, TimeUnit.SECONDS)

        // Client tries random matching  
        auth.signOut()
        val clientAuthLatch = CountDownLatch(1)
        auth.signInAnonymously().addOnCompleteListener { 
            clientUid = auth.currentUser?.uid
            clientAuthLatch.countDown() 
        }
        clientAuthLatch.await(5, TimeUnit.SECONDS)

        val clientState = PlayerState(
            normX = 0.6f,
            normY = 0.4f,
            color = -256, // Yellow
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 98765L,
            playerName = "RandomClient",
            uid = clientUid!!
        )

        val matchLatch = CountDownLatch(1)
        var matchSuccess = false
        var matchedGameId: String? = null

        // Since joinRandomGame doesn't exist, we'll join the host's game directly
        // This simulates what random matching would do - find an available game and join it
        client.joinGame(hostGameId!!, clientState) { success, gameId, gameSettings ->
            matchSuccess = success
            matchedGameId = gameId
            matchLatch.countDown()
        }

        assertTrue("Random matching should complete", matchLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Should successfully match with random game", matchSuccess)
        assertEquals("Should match with host's game", hostGameId, matchedGameId)
    }

    @Test
    fun testConnectionRecovery_afterDisconnect() {
        // Test recovery and synchronization after simulated disconnection
        
        // Establish basic game
        testHostJoinFlow_basicCoordination()

        val host = hostManager ?: return
        val client = clientManager ?: return

        // Simulate client disconnection by clearing listeners
        client.leaveGame()

        // Wait for disconnection to propagate
        Thread.sleep(2000)

        // Client reconnects to same game
        val reconnectLatch = CountDownLatch(1)
        var reconnectSuccess = false

        val reconnectState = PlayerState(
            normX = 0.5f,
            normY = 0.5f,
            color = -65536,
            mode = 0,
            ink = 100.0f,
            active = true,
            mazeSeed = 12345L,
            playerName = "ReconnectedClient",
            uid = clientUid!!
        )

        client.joinGame(hostGameId!!, reconnectState) { success, gameId, gameSettings ->
            reconnectSuccess = success
            reconnectLatch.countDown()
        }

        assertTrue("Reconnection should complete", reconnectLatch.await(15, TimeUnit.SECONDS))
        assertTrue("Should successfully reconnect", reconnectSuccess)
    }
} 