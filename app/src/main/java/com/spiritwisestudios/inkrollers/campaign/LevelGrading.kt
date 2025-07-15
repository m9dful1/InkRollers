package com.spiritwisestudios.inkrollers.campaign

import android.util.Log

/**
 * Grading system for campaign levels
 */
object LevelGrading {
    
    private const val TAG = "LevelGrading"
    
    /**
     * Calculate grade for a completed level using configurable parameters
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
        
        val config = levelData.gradingConfig
        
        // Use basic grading if configured
        if (config.useBasicGrading) {
            return calculateBasicGrade(timeTaken, secretsFound, totalSecrets, levelData)
        }
        
        var score: Int
        var timeBonus = 0
        var efficiencyBonus: Int
        var robotBonus = 0
        var secretsBonus = 0
        
        // Time bonus (if level has time limit)
        levelData.timeLimit?.let { timeLimit ->
            val timeRatio = timeTaken.toFloat() / timeLimit.toFloat()
            timeBonus = when {
                timeRatio <= 0.5f -> config.timeBonusConfig.halfTimeBonus
                timeRatio <= 0.75f -> config.timeBonusConfig.threeQuarterTimeBonus
                timeRatio <= 1.0f -> config.timeBonusConfig.withinTimeBonus
                else -> config.timeBonusConfig.overtimeBonus
            }
        }
        
        // Efficiency bonus (ink usage)
        val maxInk = config.efficiencyBonusConfig.maxInkCapacity
        val maxEfficiencyBonus = config.efficiencyBonusConfig.maxEfficiencyBonus
        val inkEfficiency = 1f - (inkUsed / maxInk)
        efficiencyBonus = (inkEfficiency * maxEfficiencyBonus).toInt().coerceIn(0, maxEfficiencyBonus)
        
        // Robot conversion bonus
        if (totalRobots > 0) {
            val conversionRatio = robotsConverted.toFloat() / totalRobots.toFloat()
            val maxRobotBonus = config.robotBonusConfig.maxRobotBonus
            robotBonus = (conversionRatio * maxRobotBonus).toInt()
        }
        
        // Secrets bonus
        if (totalSecrets > 0) {
            val secretsRatio = secretsFound.toFloat() / totalSecrets.toFloat()
            val maxSecretsBonus = config.secretsBonusConfig.maxSecretsBonus
            secretsBonus = (secretsRatio * maxSecretsBonus).toInt()
        }
        
        // Calculate total score
        score = timeBonus + efficiencyBonus + robotBonus + secretsBonus
        
        // Determine grade based on configurable thresholds
        val thresholds = config.gradeThresholds
        val grade = when {
            score >= thresholds.gradeA -> "A"
            score >= thresholds.gradeB -> "B"
            score >= thresholds.gradeC -> "C"
            score >= thresholds.gradeD -> "D"
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
     * Calculate a simple grade for basic completion using configurable parameters
     */
    fun calculateBasicGrade(
        timeTaken: Long,
        secretsFound: Int,
        totalSecrets: Int,
        levelData: CampaignLevelData
    ): CampaignManager.LevelGrade {
        
        val config = levelData.gradingConfig
        var score = config.baseCompletionScore // Use configurable base score
        var timeBonus = 0
        var secretsBonus = 0
        
        // Time bonus (if level has time limit)
        levelData.timeLimit?.let { timeLimit ->
            val timeRatio = timeTaken.toFloat() / timeLimit.toFloat()
            timeBonus = when {
                timeRatio <= 0.5f -> config.timeBonusConfig.basicHalfTimeBonus
                timeRatio <= 0.75f -> config.timeBonusConfig.basicThreeQuarterTimeBonus
                timeRatio <= 1.0f -> config.timeBonusConfig.basicWithinTimeBonus
                else -> config.timeBonusConfig.basicOvertimeBonus
            }
        }
        
        // Secrets bonus (doors activated)
        if (totalSecrets > 0) {
            val secretsRatio = secretsFound.toFloat() / totalSecrets.toFloat()
            val maxSecretsBonus = config.secretsBonusConfig.maxSecretsBonus
            secretsBonus = (secretsRatio * maxSecretsBonus).toInt()
        }
        
        score += timeBonus + secretsBonus
        
        // Determine grade based on basic grading thresholds
        // For basic grading, we use simpler thresholds
        val grade = when {
            score >= 150 -> "A"
            score >= 125 -> "B"
            score >= 100 -> "C"
            score >= 75 -> "D"
            else -> "F"
        }
        
        Log.d(TAG, "Basic level grading - Score: $score, Grade: $grade, Time: $timeBonus, Secrets: $secretsBonus")
        
        return CampaignManager.LevelGrade(
            grade = grade,
            score = score,
            timeBonus = timeBonus,
            efficiencyBonus = 0,
            robotBonus = 0,
            secretsBonus = secretsBonus
        )
    }
} 