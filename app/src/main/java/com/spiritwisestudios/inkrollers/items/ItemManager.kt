package com.spiritwisestudios.inkrollers.items

import android.graphics.Canvas
import android.graphics.RectF
import android.util.Log
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.MazeLevel
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.PaintSurface
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Manages all items in the game - spawning, updating, rendering, and collection
 */
class ItemManager(
    private val itemConfig: ItemConfig,
    private val playerManager: InkRefillItem.PlayerManager? = null
) {
    
    companion object {
        private const val TAG = "ItemManager"
        private const val COLLISION_DISTANCE = 50f
        private const val MIN_SPAWN_DISTANCE_FROM_PLAYER = 100f
        private const val MIN_SPAWN_DISTANCE_FROM_ITEMS = 80f
        private const val MAX_ITEMS_PER_TYPE = 3
        private const val SPAWN_COOLDOWN = 5f // 5 seconds between spawns
    }
    
    private val activeItems = mutableListOf<Item>()
    private val itemCounts = mutableMapOf<ItemType, Int>()
    private val lastSpawnTimes = mutableMapOf<ItemType, Float>()
    private var gameTime = 0f
    
    /**
     * Update all active items and handle spawning
     */
    fun update(deltaTime: Float, level: Level?, players: List<Player>, paintSurface: PaintSurface?) {
        gameTime += deltaTime
        
        // Update all active items
        activeItems.forEach { item ->
            item.update(deltaTime)
        }
        
        // Check for item collection
        checkItemCollection(players)
        
        // Remove inactive items
        removeInactiveItems()
        
        // Spawn new items if needed
        handleItemSpawning(level, players, paintSurface)
    }
    
    /**
     * Draw all active items
     */
    fun draw(canvas: Canvas) {
        activeItems.forEach { item ->
            item.draw(canvas)
        }
    }
    
    /**
     * Spawn a specific item at a given location
     */
    fun spawnItem(itemType: ItemType, x: Float, y: Float): Item? {
        if (!itemConfig.isItemEnabled(itemType)) {
            Log.d(TAG, "Attempted to spawn disabled item type: $itemType")
            return null
        }
        
        val item = createItem(itemType, x, y)
        if (item != null) {
            activeItems.add(item)
            itemCounts[itemType] = (itemCounts[itemType] ?: 0) + 1
            Log.d(TAG, "Spawned $itemType at ($x, $y)")
        }
        return item
    }
    
    /**
     * Spawn a random item at a safe location
     */
    fun spawnRandomItem(level: Level?, players: List<Player>, paintSurface: PaintSurface?): Item? {
        val enabledItems = itemConfig.getEnabledItems()
        if (enabledItems.isEmpty()) return null
        
        val availableItems = enabledItems.filter { itemType ->
            val count = itemCounts[itemType] ?: 0
            count < MAX_ITEMS_PER_TYPE
        }
        
        if (availableItems.isEmpty()) return null
        
        val itemType = availableItems.random()
        val spawnLocation = findSafeSpawnLocation(level, players, paintSurface)
        
        return if (spawnLocation != null) {
            spawnItem(itemType, spawnLocation.first, spawnLocation.second)
        } else {
            Log.w(TAG, "Could not find safe spawn location for $itemType")
            null
        }
    }
    
    /**
     * Remove a specific item
     */
    fun removeItem(item: Item) {
        if (activeItems.remove(item)) {
            val count = itemCounts[item.type] ?: 0
            itemCounts[item.type] = (count - 1).coerceAtLeast(0)
            Log.d(TAG, "Removed ${item.type} item")
        }
    }
    
    /**
     * Remove all items of a specific type
     */
    fun removeAllItems(itemType: ItemType) {
        val itemsToRemove = activeItems.filter { it.type == itemType }
        itemsToRemove.forEach { item ->
            activeItems.remove(item)
        }
        itemCounts[itemType] = 0
        Log.d(TAG, "Removed all ${itemType} items (${itemsToRemove.size} items)")
    }
    
    /**
     * Clear all items
     */
    fun clearAllItems() {
        activeItems.clear()
        itemCounts.clear()
        lastSpawnTimes.clear()
        Log.d(TAG, "Cleared all items")
    }
    
    /**
     * Get all active items
     */
    fun getActiveItems(): List<Item> = activeItems.toList()
    
    /**
     * Get items of a specific type
     */
    fun getItemsOfType(itemType: ItemType): List<Item> {
        return activeItems.filter { it.type == itemType }
    }
    
    /**
     * Get count of items of a specific type
     */
    fun getItemCount(itemType: ItemType): Int {
        return itemCounts[itemType] ?: 0
    }
    
    /**
     * Check if an item can be spawned (considering cooldown and limits)
     */
    fun canSpawnItem(itemType: ItemType): Boolean {
        if (!itemConfig.isItemEnabled(itemType)) return false
        
        val count = itemCounts[itemType] ?: 0
        if (count >= MAX_ITEMS_PER_TYPE) return false
        
        val lastSpawnTime = lastSpawnTimes[itemType] ?: 0f
        return (gameTime - lastSpawnTime) >= SPAWN_COOLDOWN
    }
    
    /**
     * Force spawn an item (ignoring cooldowns and limits) - useful for testing
     */
    fun forceSpawnItem(itemType: ItemType, x: Float, y: Float): Item? {
        val item = createItem(itemType, x, y)
        if (item != null) {
            activeItems.add(item)
            itemCounts[itemType] = (itemCounts[itemType] ?: 0) + 1
            Log.d(TAG, "Force spawned $itemType at ($x, $y)")
        }
        return item
    }
    
    /**
     * Create an item of the specified type
     */
    private fun createItem(itemType: ItemType, x: Float, y: Float): Item? {
        return when (itemType) {
            ItemType.INK_REFILL -> InkRefillItem(x, y, playerManager = playerManager)
            ItemType.SPEED_BOOST -> null // TODO: Implement other item types
            ItemType.PAINT_MULTIPLIER -> null // TODO: Implement other item types
            ItemType.SHIELD -> null // TODO: Implement other item types
            ItemType.FREEZE -> null // TODO: Implement other item types
            ItemType.TELEPORT -> null // TODO: Implement other item types
        }
    }
    
    /**
     * Check for item collection by players
     */
    private fun checkItemCollection(players: List<Player>) {
        val itemsToRemove = mutableListOf<Item>()
        
        for (item in activeItems) {
            if (!item.isActive) continue
            
            for (player in players) {
                val distance = sqrt(
                    (player.x - item.position.first) * (player.x - item.position.first) +
                    (player.y - item.position.second) * (player.y - item.position.second)
                )
                
                if (distance <= COLLISION_DISTANCE) {
                    // Create a simple player ID for collection
                    val playerId = "player_${player.hashCode()}"
                    
                    if (item.onCollected(playerId)) {
                        itemsToRemove.add(item)
                        Log.d(TAG, "Player collected ${item.type} item")
                        break // Item collected, no need to check other players
                    }
                }
            }
        }
        
        // Remove collected items
        itemsToRemove.forEach { item ->
            removeItem(item)
        }
    }
    
    /**
     * Remove inactive items from the active list
     */
    private fun removeInactiveItems() {
        val inactiveItems = activeItems.filter { !it.isActive }
        inactiveItems.forEach { item ->
            removeItem(item)
        }
    }
    
    /**
     * Handle automatic item spawning based on game state
     */
    private fun handleItemSpawning(level: Level?, players: List<Player>, paintSurface: PaintSurface?) {
        // Simple spawning logic - spawn ink refills when players are low on ink
        val enabledItems = itemConfig.getEnabledItems()
        
        if (ItemType.INK_REFILL in enabledItems && canSpawnItem(ItemType.INK_REFILL)) {
            val lowInkPlayers = players.count { it.getInkPercent() < 0.3f }
            val inkRefillCount = getItemCount(ItemType.INK_REFILL)
            
            // Spawn ink refill if there are low ink players and not too many refills already
            if (lowInkPlayers > 0 && inkRefillCount < 2) {
                spawnRandomItem(level, players, paintSurface)
                lastSpawnTimes[ItemType.INK_REFILL] = gameTime
            }
        }
        
        // TODO: Add spawning logic for other item types
    }
    
    /**
     * Find a safe location to spawn an item
     */
    private fun findSafeSpawnLocation(level: Level?, players: List<Player>, paintSurface: PaintSurface?): Pair<Float, Float>? {
        if (level == null || paintSurface == null) return null
        
        val maxAttempts = 100
        var attempts = 0
        
        // Try maze-aware spawning if it's a MazeLevel
        if (level is MazeLevel) {
            // First try using walkable cell rectangles for more precise placement
            val walkableCells = level.getWalkableCellRects()
            if (walkableCells.isNotEmpty()) {
                while (attempts < maxAttempts * 2 / 3) { // Use 2/3 of attempts for precise method
                    val randomCell = walkableCells.random()
                    
                    // Generate random position within the selected cell
                    val x = randomCell.left + Random.nextFloat() * (randomCell.right - randomCell.left)
                    val y = randomCell.top + Random.nextFloat() * (randomCell.bottom - randomCell.top)
                    
                    // Check if location is valid
                    if (isValidSpawnLocation(x, y, level, players)) {
                        Log.d(TAG, "Successfully spawned item using walkable cell at ($x, $y)")
                        return Pair(x, y)
                    }
                    
                    attempts++
                }
            }
            
            // Fallback to maze coordinate system
            while (attempts < maxAttempts) {
                // Generate coordinates within the maze area using maze coordinate system
                val relX = 0.1f + Random.nextFloat() * 0.8f // Stay away from edges
                val relY = 0.1f + Random.nextFloat() * 0.8f
                
                val (x, y) = level.mazeToScreenCoord(relX, relY)
                
                // Check if location is valid
                if (isValidSpawnLocation(x, y, level, players)) {
                    Log.d(TAG, "Successfully spawned item using maze coordinates at ($x, $y)")
                    return Pair(x, y)
                }
                
                attempts++
            }
        }
        
        // Fallback to original method for non-maze levels or if maze method fails
        attempts = 0
        while (attempts < maxAttempts) {
            // Generate random coordinates within level bounds, but with better boundaries
            val margin = 100f // Keep away from screen edges
            val x = margin + Random.nextFloat() * (paintSurface.w - 2 * margin)
            val y = margin + Random.nextFloat() * (paintSurface.h - 2 * margin)
            
            // Check if location is valid
            if (isValidSpawnLocation(x, y, level, players)) {
                return Pair(x, y)
            }
            
            attempts++
        }
        
        return null // Could not find a safe location
    }
    
    /**
     * Check if a location is valid for spawning an item
     */
    private fun isValidSpawnLocation(x: Float, y: Float, level: Level, players: List<Player>): Boolean {
        val itemRadius = 30f // Approximate item size
        
        // Check for collision with walls - test multiple points around the item center
        val testPoints = listOf(
            Pair(x, y), // Center
            Pair(x - itemRadius, y), // Left
            Pair(x + itemRadius, y), // Right
            Pair(x, y - itemRadius), // Top
            Pair(x, y + itemRadius)  // Bottom
        )
        
        for ((testX, testY) in testPoints) {
            if (level.checkCollision(testX, testY)) return false
        }
        
        // Check distance from players
        for (player in players) {
            val distance = sqrt(
                (player.x - x) * (player.x - x) +
                (player.y - y) * (player.y - y)
            )
            if (distance < MIN_SPAWN_DISTANCE_FROM_PLAYER) return false
        }
        
        // Check distance from other items
        for (item in activeItems) {
            val distance = sqrt(
                (item.position.first - x) * (item.position.first - x) +
                (item.position.second - y) * (item.position.second - y)
            )
            if (distance < MIN_SPAWN_DISTANCE_FROM_ITEMS) return false
        }
        
        return true
    }
} 