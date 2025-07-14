package com.spiritwisestudios.inkrollers.items

import android.util.Log
import com.spiritwisestudios.inkrollers.GameView

/**
 * Example demonstrating how to use the item system in campaigns
 */
object ItemSystemExample {
    
    private const val TAG = "ItemSystemExample"
    
    /**
     * Example: Configure items for a specific campaign level
     */
    fun configureCampaignItems(gameView: GameView, levelId: String) {
        val config = when (levelId) {
            "level_1" -> {
                // Level 1: Only ink refills enabled
                ItemConfig.createWithItems(listOf(ItemType.INK_REFILL))
            }
            "level_2" -> {
                // Level 2: Ink refills and speed boost
                ItemConfig.createWithItems(listOf(
                    ItemType.INK_REFILL,
                    ItemType.SPEED_BOOST
                ))
            }
            "level_3" -> {
                // Level 3: All items enabled except teleport
                val config = ItemConfig.createDefault()
                config.setItemEnabled(ItemType.TELEPORT, false)
                config
            }
            else -> {
                // Default: All items enabled
                ItemConfig.createDefault()
            }
        }
        
        gameView.setItemConfig(config)
        Log.d(TAG, "Configured items for $levelId: ${config.getEnabledItems()}")
    }
    
    /**
     * Example: Manually spawn items for testing
     */
    fun spawnTestItems(gameView: GameView) {
        // Spawn an ink refill at position (200, 300)
        val inkRefill = gameView.forceSpawnItem(ItemType.INK_REFILL, 200f, 300f)
        if (inkRefill != null) {
            Log.d(TAG, "Spawned ink refill item: ${inkRefill.id}")
        }
        
        // Try to spawn a speed boost (may fail if not enabled in config)
        val speedBoost = gameView.spawnItem(ItemType.SPEED_BOOST, 400f, 500f)
        if (speedBoost != null) {
            Log.d(TAG, "Spawned speed boost item: ${speedBoost.id}")
        } else {
            Log.d(TAG, "Speed boost spawn failed - probably disabled in config")
        }
    }
    
    /**
     * Example: Campaign-specific item configuration
     */
    fun createCampaignItemConfig(
        enableInkRefill: Boolean = true,
        enableSpeedBoost: Boolean = false,
        enablePaintMultiplier: Boolean = false,
        enableShield: Boolean = false,
        enableFreeze: Boolean = false,
        enableTeleport: Boolean = false
    ): ItemConfig {
        val config = ItemConfig()
        config.disableAllItems() // Start with everything disabled
        
        // Enable only the specified items
        if (enableInkRefill) config.setItemEnabled(ItemType.INK_REFILL, true)
        if (enableSpeedBoost) config.setItemEnabled(ItemType.SPEED_BOOST, true)
        if (enablePaintMultiplier) config.setItemEnabled(ItemType.PAINT_MULTIPLIER, true)
        if (enableShield) config.setItemEnabled(ItemType.SHIELD, true)
        if (enableFreeze) config.setItemEnabled(ItemType.FREEZE, true)
        if (enableTeleport) config.setItemEnabled(ItemType.TELEPORT, true)
        
        return config
    }
    
    /**
     * Example: Check what items are currently active
     */
    fun logActiveItems(gameView: GameView) {
        val activeItems = gameView.getActiveItems()
        Log.d(TAG, "Currently active items (${activeItems.size}):")
        
        activeItems.forEach { item ->
            Log.d(TAG, "  - ${item.type} at (${item.position.first}, ${item.position.second})")
        }
        
        if (activeItems.isEmpty()) {
            Log.d(TAG, "  No items currently active")
        }
    }
    
    /**
     * Example: Get item configuration summary
     */
    fun logItemConfiguration(gameView: GameView) {
        val config = gameView.getItemConfig()
        val enabledItems = config.getEnabledItems()
        val disabledItems = config.getDisabledItems()
        
        Log.d(TAG, "Item Configuration Summary:")
        Log.d(TAG, "  Enabled items (${enabledItems.size}): ${enabledItems.joinToString(", ")}")
        Log.d(TAG, "  Disabled items (${disabledItems.size}): ${disabledItems.joinToString(", ")}")
    }
} 