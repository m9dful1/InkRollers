package com.spiritwisestudios.inkrollers.campaign

/**
 * Interface for levels that support robot spawning.
 * This allows RobotSpawner to work with both CampaignLevel and multiplayer levels.
 */
interface SpawnableLevel {
    /**
     * Check if a point collides with any walls/obstacles in the level
     */
    fun checkCollision(x: Float, y: Float): Boolean
    
    /**
     * Add a spawned robot to the level's robot management system
     */
    fun addSpawnedRobot(robot: Robot)
}