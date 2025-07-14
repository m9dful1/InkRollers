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
            Log.d(TAG, "🎯 Successfully spawned $itemType at ($x, $y) - Total active items: ${activeItems.size}")
        } else {
            Log.w(TAG, "❌ Failed to create $itemType item at ($x, $y)")
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
                    // Use the player manager to find the correct player ID by object reference
                    val playerId = (playerManager as? GameViewPlayerManager)?.getPlayerIdByObject(player)
                    
                    Log.d(TAG, "Player collision detected! Distance: $distance, Player ID: $playerId, Item: ${item.type}")
                    
                    if (playerId != null && item.onCollected(playerId)) {
                        itemsToRemove.add(item)
                        Log.d(TAG, "✅ Player $playerId successfully collected ${item.type} item")
                        break // Item collected, no need to check other players
                    } else {
                        Log.w(TAG, "❌ Failed to collect item - player ID: $playerId, item active: ${item.isActive}")
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
     * Find a safe location to spawn an item using the same logic as CoverageCalculator
     */
    private fun findSafeSpawnLocation(level: Level?, players: List<Player>, paintSurface: PaintSurface?): Pair<Float, Float>? {
        if (level == null || paintSurface == null) return null
        
        return when (level) {
            is MazeLevel -> findSafeSpawnLocationInMaze(level, players)
            is com.spiritwisestudios.inkrollers.campaign.CampaignLevel -> findSafeSpawnLocationInCampaign(level, players)
            else -> findSafeSpawnLocationGeneral(level, players, paintSurface)
        }
    }

    /**
     * Find safe spawn location within a CampaignLevel by leveraging its underlying maze coordinate system.
     */
    private fun findSafeSpawnLocationInCampaign(level: com.spiritwisestudios.inkrollers.campaign.CampaignLevel, players: List<Player>): Pair<Float, Float>? {
        val maxAttempts = 200
        var attempts = 0

        // Compute the maze bounding box in screen space using the helper conversion
        val (topLeftX, topLeftY) = level.mazeToScreenCoord(0f, 0f)
        val (bottomRightX, bottomRightY) = level.mazeToScreenCoord(1f, 1f)

        val minX = minOf(topLeftX, bottomRightX)
        val maxX = maxOf(topLeftX, bottomRightX)
        val minY = minOf(topLeftY, bottomRightY)
        val maxY = maxOf(topLeftY, bottomRightY)

        val margin = 10f // Keep away from maze borders

        while (attempts < maxAttempts) {
            // Generate random normalized coordinates (0.0-1.0) inside the maze
            val relX = Random.nextFloat()
            val relY = Random.nextFloat()
            val (x, y) = level.mazeToScreenCoord(relX, relY)

            // Ensure we stay inside the maze bounds with margin
            if (x < minX + margin || x > maxX - margin || y < minY + margin || y > maxY - margin) {
                attempts++
                continue
            }

            // Validate against walls and distances
            if (!level.checkCollision(x, y) &&
                isValidDistanceFromPlayers(x, y, players) &&
                isValidDistanceFromOtherItems(x, y)) {
                Log.d(TAG, "✅ Successfully found spawn location in CampaignLevel maze at ($x, $y)")
                return Pair(x, y)
            }

            attempts++
        }

        Log.w(TAG, "❌ Could not find safe spawn location in CampaignLevel after $maxAttempts attempts")
        return null
    }
    
    /**
     * Find safe spawn location in MazeLevel using walkable cell rectangles
     * Uses the same approach as CoverageCalculator.calculateWithCellRects()
     */
    private fun findSafeSpawnLocationInMaze(level: MazeLevel, players: List<Player>): Pair<Float, Float>? {
        val maxAttempts = 200
        var attempts = 0
        
        // Get walkable cell rectangles from the maze level (same as CoverageCalculator)
        val cellRects = level.getWalkableCellRects()
        
        if (cellRects.isEmpty()) {
            Log.w(TAG, "❌ No walkable cell rectangles found in maze")
            return null
        }
        
        Log.d(TAG, "🎯 Found ${cellRects.size} walkable cell rectangles in maze for item spawning")
        
        while (attempts < maxAttempts) {
            // Pick a random walkable cell rectangle
            val randomCell = cellRects.random()
            
            // Generate random position within the selected cell with some margin from edges
            val margin = 10f // Small margin from cell edges
            val cellWidth = randomCell.right - randomCell.left
            val cellHeight = randomCell.bottom - randomCell.top
            
            if (cellWidth > margin * 2 && cellHeight > margin * 2) {
                val x = randomCell.left + margin + Random.nextFloat() * (cellWidth - margin * 2)
                val y = randomCell.top + margin + Random.nextFloat() * (cellHeight - margin * 2)
                
                // Validate the spawn location
                if (isValidSpawnLocationInCell(x, y, players)) {
                    Log.d(TAG, "✅ Successfully found spawn location in walkable cell at ($x, $y)")
                    return Pair(x, y)
                }
            }
            
            attempts++
        }
        
        Log.w(TAG, "❌ Could not find safe spawn location in maze after $maxAttempts attempts")
        return null
    }
    
    /**
     * Find safe spawn location for non-MazeLevel types using collision checking
     * Uses the same approach as CoverageCalculator.calculateOriginal()
     */
    private fun findSafeSpawnLocationGeneral(level: Level, players: List<Player>, paintSurface: PaintSurface): Pair<Float, Float>? {
        val maxAttempts = 200
        var attempts = 0
        
        while (attempts < maxAttempts) {
            val margin = 50f // Keep away from screen edges
            val x = margin + Random.nextFloat() * (paintSurface.w - 2 * margin)
            val y = margin + Random.nextFloat() * (paintSurface.h - 2 * margin)
            
            // Skip locations that are on walls (same as CoverageCalculator)
            if (!level.checkCollision(x, y)) {
                // Additional validation for player distance
                if (isValidDistanceFromPlayers(x, y, players)) {
                    Log.d(TAG, "✅ Successfully found spawn location for general level at ($x, $y)")
                    return Pair(x, y)
                }
            }
            
            attempts++
        }
        
        Log.w(TAG, "❌ Could not find safe spawn location in general level after $maxAttempts attempts")
        return null
    }
    
    /**
     * Check if a location within a walkable cell is valid for spawning an item
     * Assumes the location is already within a valid walkable area
     */
    private fun isValidSpawnLocationInCell(x: Float, y: Float, players: List<Player>): Boolean {
        // Since we're already in a walkable cell, just check distances
        return isValidDistanceFromPlayers(x, y, players) && isValidDistanceFromOtherItems(x, y)
    }
    
    /**
     * Check if location is far enough from players
     */
    private fun isValidDistanceFromPlayers(x: Float, y: Float, players: List<Player>): Boolean {
        for (player in players) {
            val distance = sqrt(
                (player.x - x) * (player.x - x) +
                (player.y - y) * (player.y - y)
            )
            if (distance < MIN_SPAWN_DISTANCE_FROM_PLAYER) return false
        }
        return true
    }
    
    /**
     * Check if location is far enough from other items
     */
    private fun isValidDistanceFromOtherItems(x: Float, y: Float): Boolean {
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