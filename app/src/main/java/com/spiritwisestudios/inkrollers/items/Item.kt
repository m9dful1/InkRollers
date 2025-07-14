package com.spiritwisestudios.inkrollers.items

import android.graphics.Canvas
import android.graphics.RectF

/**
 * Base interface for all collectible items in the game
 */
interface Item {
    /**
     * The type of this item
     */
    val type: ItemType
    
    /**
     * Current position of the item
     */
    val position: Pair<Float, Float>
    
    /**
     * Whether this item is currently active/collectible
     */
    val isActive: Boolean
    
    /**
     * Collision bounds for this item
     */
    val bounds: RectF
    
    /**
     * Unique identifier for this item instance
     */
    val id: String
    
    /**
     * Update the item's state (animations, effects, etc.)
     */
    fun update(deltaTime: Float)
    
    /**
     * Draw the item on the canvas
     */
    fun draw(canvas: Canvas)
    
    /**
     * Called when the item is collected by a player
     * @param playerId The ID of the player who collected this item
     * @return true if the item was successfully collected and should be removed
     */
    fun onCollected(playerId: String): Boolean
    
    /**
     * Called when the item should be activated/used
     * @param playerId The ID of the player using this item
     * @return true if the item was successfully used
     */
    fun onUsed(playerId: String): Boolean
    
    /**
     * Deactivate this item (remove from game)
     */
    fun deactivate()
    
    /**
     * Check if this item can be collected by the given player
     */
    fun canBeCollectedBy(playerId: String): Boolean
    
    /**
     * Get the effect description for UI display
     */
    fun getEffectDescription(): String
} 