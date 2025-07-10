package com.spiritwisestudios.inkrollers.campaign

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Singleton manager for campaign state and progression.
 * Handles saving/loading campaign progress and managing level availability.
 */
class CampaignManager private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "CampaignManager"
        private const val PREFS_NAME = "inkrollers_campaign_progress"
        private const val KEY_CAMPAIGN_PROGRESS = "campaign_progress"
        
        @Volatile
        private var INSTANCE: CampaignManager? = null
        
        fun getInstance(context: Context): CampaignManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CampaignManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Data class to hold campaign progress information
     */
    data class CampaignProgress(
        val completedLevels: Set<String> = emptySet(),
        val levelGrades: Map<String, LevelGrade> = emptyMap(),
        val currentLevel: String? = null,
        val timestampSaved: Long = System.currentTimeMillis()
    )
    
    /**
     * Data class for level grades
     */
    data class LevelGrade(
        val grade: String, // "A", "B", "C", "D", "F"
        val score: Int,
        val timeBonus: Int,
        val efficiencyBonus: Int,
        val robotBonus: Int,
        val secretsBonus: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private var campaignProgress: CampaignProgress = CampaignProgress()
    
    /**
     * Level dependencies - defines which levels must be completed before others are available
     */
    private val levelDependencies = mapOf(
        "level_2" to setOf("level_1"),
        "level_3" to setOf("level_2"),
        "level_4a" to setOf("level_3"), // Branch A
        "level_4b" to setOf("level_3")  // Branch B
    )
    
    /**
     * Get all available levels based on completed levels
     */
    fun getAvailableLevels(): List<String> {
        val available = mutableListOf<String>()
        
        // Level 1 is always available
        available.add("level_1")
        
        // Check other levels based on dependencies
        levelDependencies.forEach { (levelId, dependencies) ->
            if (dependencies.all { it in campaignProgress.completedLevels }) {
                available.add(levelId)
            }
        }
        
        return available
    }
    
    /**
     * Get completed levels
     */
    fun getCompletedLevels(): Set<String> {
        return campaignProgress.completedLevels
    }
    
    /**
     * Get grade for a specific level
     */
    fun getLevelGrade(levelId: String): LevelGrade? {
        return campaignProgress.levelGrades[levelId]
    }
    
    /**
     * Complete a level with a grade
     */
    fun completeLevel(levelId: String, grade: LevelGrade) {
        val newCompletedLevels = campaignProgress.completedLevels + levelId
        val newLevelGrades = campaignProgress.levelGrades + (levelId to grade)
        
        campaignProgress = campaignProgress.copy(
            completedLevels = newCompletedLevels,
            levelGrades = newLevelGrades
        )
        
        saveProgress()
        Log.d(TAG, "Completed level $levelId with grade ${grade.grade}")
    }
    
    /**
     * Start a level
     */
    fun startLevel(levelId: String) {
        campaignProgress = campaignProgress.copy(currentLevel = levelId)
        saveProgress()
        Log.d(TAG, "Started level $levelId")
    }
    
    /**
     * Get level data for a specific level
     */
    fun getLevelData(levelId: String): CampaignLevelData? {
        return CampaignLevels.getLevelData(levelId)
    }
    
    /**
     * Save campaign progress to SharedPreferences
     */
    fun saveProgress() {
        try {
            val jsonString = gson.toJson(campaignProgress)
            prefs.edit()
                .putString(KEY_CAMPAIGN_PROGRESS, jsonString)
                .apply()
            
            Log.d(TAG, "Saved campaign progress: ${campaignProgress.completedLevels.size} levels completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save campaign progress", e)
        }
    }
    
    /**
     * Load campaign progress from SharedPreferences
     */
    fun loadProgress() {
        try {
            val jsonString = prefs.getString(KEY_CAMPAIGN_PROGRESS, null)
            if (jsonString != null) {
                campaignProgress = gson.fromJson(jsonString, CampaignProgress::class.java)
                Log.d(TAG, "Loaded campaign progress: ${campaignProgress.completedLevels.size} levels completed")
            } else {
                Log.d(TAG, "No campaign progress found, starting fresh")
                campaignProgress = CampaignProgress()
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse saved campaign progress", e)
            clearProgress()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load campaign progress", e)
            clearProgress()
        }
    }
    
    /**
     * Clear all campaign progress (for testing or reset)
     */
    fun clearProgress() {
        campaignProgress = CampaignProgress()
        prefs.edit()
            .remove(KEY_CAMPAIGN_PROGRESS)
            .apply()
        Log.d(TAG, "Cleared campaign progress")
    }
    
    /**
     * Check if a level is available to play
     */
    fun isLevelAvailable(levelId: String): Boolean {
        return levelId in getAvailableLevels()
    }
    
    /**
     * Check if a level is completed
     */
    fun isLevelCompleted(levelId: String): Boolean {
        return levelId in campaignProgress.completedLevels
    }
} 