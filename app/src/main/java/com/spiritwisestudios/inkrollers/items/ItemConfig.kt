package com.spiritwisestudios.inkrollers.items

/**
 * Configuration class for managing item availability in campaigns
 */
data class ItemConfig(
    /**
     * Map of item types to their enabled/disabled state
     */
    private val itemSettings: MutableMap<ItemType, Boolean> = mutableMapOf()
) {
    
    init {
        // Initialize all items as enabled by default
        ItemType.getAllTypes().forEach { itemType ->
            itemSettings[itemType] = true
        }
    }
    
    /**
     * Check if an item type is enabled
     */
    fun isItemEnabled(itemType: ItemType): Boolean {
        return itemSettings[itemType] ?: false
    }
    
    /**
     * Enable or disable an item type
     */
    fun setItemEnabled(itemType: ItemType, enabled: Boolean) {
        itemSettings[itemType] = enabled
    }
    
    /**
     * Get all enabled item types
     */
    fun getEnabledItems(): List<ItemType> {
        return itemSettings.filter { it.value }.keys.toList()
    }
    
    /**
     * Get all disabled item types
     */
    fun getDisabledItems(): List<ItemType> {
        return itemSettings.filter { !it.value }.keys.toList()
    }
    
    /**
     * Enable all items
     */
    fun enableAllItems() {
        ItemType.getAllTypes().forEach { itemType ->
            itemSettings[itemType] = true
        }
    }
    
    /**
     * Disable all items
     */
    fun disableAllItems() {
        ItemType.getAllTypes().forEach { itemType ->
            itemSettings[itemType] = false
        }
    }
    
    /**
     * Get the current settings as a map
     */
    fun getSettings(): Map<ItemType, Boolean> {
        return itemSettings.toMap()
    }
    
    /**
     * Load settings from a map
     */
    fun loadSettings(settings: Map<ItemType, Boolean>) {
        itemSettings.clear()
        // Ensure all item types are present
        ItemType.getAllTypes().forEach { itemType ->
            itemSettings[itemType] = settings[itemType] ?: true
        }
    }
    
    /**
     * Create a copy of this configuration
     */
    fun copy(): ItemConfig {
        val newConfig = ItemConfig()
        newConfig.itemSettings.putAll(this.itemSettings)
        return newConfig
    }
    
    companion object {
        /**
         * Create a default configuration with all items enabled
         */
        fun createDefault(): ItemConfig {
            return ItemConfig()
        }
        
        /**
         * Create a configuration with only specific items enabled
         */
        fun createWithItems(enabledItems: List<ItemType>): ItemConfig {
            val config = ItemConfig()
            config.disableAllItems()
            enabledItems.forEach { itemType ->
                config.setItemEnabled(itemType, true)
            }
            return config
        }
    }
} 