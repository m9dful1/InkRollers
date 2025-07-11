package com.spiritwisestudios.inkrollers.campaign

import android.util.Log

/**
 * Grading system for campaign levels
 */
object LevelGrading {
    
    private const val TAG = "LevelGrading"
    
    /**
     * Calculate grade for a completed level
     */
    fun calculateGrade(
        timeTaken: Long,
        inkUsed: Float,
        robotsConverted: Int,
        totalRobots: Int,
        secretsFound: Int,
        totalSecrets: Int,
        levelData: CampaignLevelData
    ): CampaignManager.LevelGrade {
        
        var score = 0
        var timeBonus = 0
        var efficiencyBonus = 0
        var robotBonus = 0
        var secretsBonus = 0
        
        // Time bonus (if level has time limit)
        levelData.timeLimit?.let { timeLimit ->
            val timeRatio = timeTaken.toFloat() / timeLimit.toFloat()
            timeBonus = when {
                timeRatio <= 0.5f -> 100  // Completed in half time
                timeRatio <= 0.75f -> 75   // Completed in 3/4 time
                timeRatio <= 1.0f -> 50    // Completed within time limit
                else -> 0                  // Over time limit
            }
        }
        
        // Efficiency bonus (ink usage)
        val maxInk = 1000f // Assume max ink capacity
        val inkEfficiency = 1f - (inkUsed / maxInk)
        efficiencyBonus = (inkEfficiency * 100).toInt().coerceIn(0, 100)
        
        // Robot conversion bonus
        if (totalRobots > 0) {
            val conversionRatio = robotsConverted.toFloat() / totalRobots.toFloat()
            robotBonus = (conversionRatio * 100).toInt()
        }
        
        // Secrets bonus
        if (totalSecrets > 0) {
            val secretsRatio = secretsFound.toFloat() / totalSecrets.toFloat()
            secretsBonus = (secretsRatio * 100).toInt()
        }
        
        // Calculate total score
        score = timeBonus + efficiencyBonus + robotBonus + secretsBonus
        
        // Determine grade based on total score
        val grade = when {
            score >= 350 -> "A"
            score >= 300 -> "B"
            score >= 250 -> "C"
            score >= 200 -> "D"
            else -> "F"
        }
        
        Log.d(TAG, "Level grading - Score: $score, Grade: $grade, Time: $timeBonus, Efficiency: $efficiencyBonus, Robots: $robotBonus, Secrets: $secretsBonus")
        
        return CampaignManager.LevelGrade(
            grade = grade,
            score = score,
            timeBonus = timeBonus,
            efficiencyBonus = efficiencyBonus,
            robotBonus = robotBonus,
            secretsBonus = secretsBonus
        )
    }
    
    /**
     * Calculate a simple grade for basic completion
     */
    fun calculateBasicGrade(
        timeTaken: Long,
        levelData: CampaignLevelData
    ): CampaignManager.LevelGrade {
        
        var score = 100 // Base score for completion
        var timeBonus = 0
        
        // Time bonus (if level has time limit)
        levelData.timeLimit?.let { timeLimit ->
            val timeRatio = timeTaken.toFloat() / timeLimit.toFloat()
            timeBonus = when {
                timeRatio <= 0.5f -> 50   // Completed in half time
                timeRatio <= 0.75f -> 25   // Completed in 3/4 time
                timeRatio <= 1.0f -> 10    // Completed within time limit
                else -> 0                  // Over time limit
            }
        }
        
        score += timeBonus
        
        // Determine grade based on total score
        val grade = when {
            score >= 150 -> "A"
            score >= 125 -> "B"
            score >= 100 -> "C"
            score >= 75 -> "D"
            else -> "F"
        }
        
        return CampaignManager.LevelGrade(
            grade = grade,
            score = score,
            timeBonus = timeBonus,
            efficiencyBonus = 0,
            robotBonus = 0,
            secretsBonus = 0
        )
    }
} 