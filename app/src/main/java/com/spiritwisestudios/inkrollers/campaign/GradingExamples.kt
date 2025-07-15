package com.spiritwisestudios.inkrollers.campaign

/**
 * Examples of how to customize grading for campaign levels.
 * Copy these configurations to CampaignLevelData.kt to customize specific levels.
 */
object GradingExamples {
    
    /**
     * Example 1: Easy Tutorial Grading
     * Use this for tutorial levels where you want players to easily get good grades
     */
    val EASY_TUTORIAL_GRADING = LevelGradingConfig(
        useBasicGrading = true,
        baseCompletionScore = 125, // Higher base score
        timeBonusConfig = TimeBonusConfig(
            basicHalfTimeBonus = 25,
            basicThreeQuarterTimeBonus = 15,
            basicWithinTimeBonus = 10
        )
    )
    
    /**
     * Example 2: Lenient Grading
     * Use this for levels where you want lower thresholds for better grades
     */
    val LENIENT_GRADING = LevelGradingConfig(
        gradeThresholds = GradeThresholds(
            gradeA = 250, // Much lower than default 350
            gradeB = 200, // Much lower than default 300
            gradeC = 150, // Much lower than default 250
            gradeD = 100  // Much lower than default 200
        )
    )
    
    /**
     * Example 3: Strict Grading
     * Use this for challenging levels where you want higher standards
     */
    val STRICT_GRADING = LevelGradingConfig(
        gradeThresholds = GradeThresholds(
            gradeA = 450, // Higher than default 350
            gradeB = 400, // Higher than default 300
            gradeC = 350, // Higher than default 250
            gradeD = 300  // Higher than default 200
        )
    )
    
    /**
     * Example 4: High Time Bonus
     * Use this for timed levels where speed is very important
     */
    val HIGH_TIME_BONUS_GRADING = LevelGradingConfig(
        timeBonusConfig = TimeBonusConfig(
            halfTimeBonus = 200,        // Much higher than default 100
            threeQuarterTimeBonus = 150, // Much higher than default 75
            withinTimeBonus = 100       // Much higher than default 50
        )
    )
    
    /**
     * Example 5: High Efficiency Focus
     * Use this for levels where ink conservation is important
     */
    val EFFICIENCY_FOCUSED_GRADING = LevelGradingConfig(
        efficiencyBonusConfig = EfficiencyBonusConfig(
            maxInkCapacity = 500f,      // Lower capacity makes efficiency more important
            maxEfficiencyBonus = 200    // Higher bonus for efficiency
        )
    )
    
    /**
     * Example 6: Robot-Heavy Level
     * Use this for levels with many robots where conversion is key
     */
    val ROBOT_FOCUSED_GRADING = LevelGradingConfig(
        robotBonusConfig = RobotBonusConfig(
            maxRobotBonus = 200 // Higher bonus for robot conversion
        )
    )
    
    /**
     * Example 7: Puzzle/Secrets Level
     * Use this for levels where finding secrets/doors is the main challenge
     */
    val PUZZLE_FOCUSED_GRADING = LevelGradingConfig(
        secretsBonusConfig = SecretsBonusConfig(
            maxSecretsBonus = 200 // Higher bonus for finding secrets/doors
        )
    )
    
    /**
     * Example 8: Balanced but Generous
     * Use this for levels where you want all bonuses to be slightly higher
     */
    val GENEROUS_GRADING = LevelGradingConfig(
        gradeThresholds = GradeThresholds(
            gradeA = 300, // Slightly lower than default
            gradeB = 250,
            gradeC = 200,
            gradeD = 150
        ),
        timeBonusConfig = TimeBonusConfig(
            halfTimeBonus = 120,
            threeQuarterTimeBonus = 90,
            withinTimeBonus = 60
        ),
        efficiencyBonusConfig = EfficiencyBonusConfig(
            maxEfficiencyBonus = 120
        ),
        robotBonusConfig = RobotBonusConfig(
            maxRobotBonus = 120
        ),
        secretsBonusConfig = SecretsBonusConfig(
            maxSecretsBonus = 120
        )
    )
}

/**
 * Quick reference for copying configurations:
 * 
 * To use any of these configurations, copy the desired config to your level definition:
 * 
 * val MY_LEVEL = CampaignLevelData(
 *     // ... other level properties ...
 *     gradingConfig = GradingExamples.EASY_TUTORIAL_GRADING
 * )
 * 
 * Or create a custom configuration:
 * 
 * val MY_LEVEL = CampaignLevelData(
 *     // ... other level properties ...
 *     gradingConfig = LevelGradingConfig(
 *         gradeThresholds = GradeThresholds(
 *             gradeA = 400, // Custom values
 *             gradeB = 350,
 *             gradeC = 300,
 *             gradeD = 250
 *         ),
 *         timeBonusConfig = TimeBonusConfig(
 *             halfTimeBonus = 150,
 *             threeQuarterTimeBonus = 100,
 *             withinTimeBonus = 75
 *         )
 *         // ... other custom configurations ...
 *     )
 * )
 */ 