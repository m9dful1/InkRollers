package com.spiritwisestudios.inkrollers.items

import com.spiritwisestudios.inkrollers.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of PlayerManager that works with GameView's player management
 */
class GameViewPlayerManager(
    private val players: ConcurrentHashMap<String, Player>
) : InkRefillItem.PlayerManager {
    
    override fun getPlayer(playerId: String): Player? {
        return players[playerId]
    }
    
    override fun getAllPlayers(): List<Player> {
        return players.values.toList()
    }
    
    /**
     * Get a player by their hash code (used for simple collision detection)
     */
    fun getPlayerByHashCode(hashCode: Int): Player? {
        return players.values.find { it.hashCode() == hashCode }
    }
    
    /**
     * Find a player by their position (for collision detection)
     */
    fun getPlayerByPosition(x: Float, y: Float, tolerance: Float = 50f): Player? {
        return players.values.find { player ->
            val dx = player.x - x
            val dy = player.y - y
            (dx * dx + dy * dy) <= (tolerance * tolerance)
        }
    }
    
    /**
     * Get the local player if available
     */
    fun getLocalPlayer(): Player? {
        // In the current implementation, there's usually only one local player
        // This is a simple heuristic - in a full implementation you'd track the local player ID
        return players.values.firstOrNull()
    }
} 