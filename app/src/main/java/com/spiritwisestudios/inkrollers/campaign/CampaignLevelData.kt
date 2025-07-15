package com.spiritwisestudios.inkrollers.campaign

import android.graphics.RectF

/**
 * Data class for level grading configuration
 */
data class LevelGradingConfig(
    // Grade thresholds (score required for each grade)
    val gradeThresholds: GradeThresholds = GradeThresholds(),
    
    // Time bonus configuration
    val timeBonusConfig: TimeBonusConfig = TimeBonusConfig(),
    
    // Efficiency bonus configuration
    val efficiencyBonusConfig: EfficiencyBonusConfig = EfficiencyBonusConfig(),
    
    // Robot bonus configuration
    val robotBonusConfig: RobotBonusConfig = RobotBonusConfig(),
    
    // Secrets/doors bonus configuration
    val secretsBonusConfig: SecretsBonusConfig = SecretsBonusConfig(),
    
    // Base score for completion (used in basic grading)
    val baseCompletionScore: Int = 100,
    
    // Whether to use basic grading (simpler scoring system)
    val useBasicGrading: Boolean = false
)

/**
 * Grade thresholds configuration
 */
data class GradeThresholds(
    val gradeA: Int = 350,
    val gradeB: Int = 300,
    val gradeC: Int = 250,
    val gradeD: Int = 200
    // F is anything below gradeD
)

/**
 * Basic grade thresholds (for basic grading system)
 */
data class BasicGradeThresholds(
    val gradeA: Int = 150,
    val gradeB: Int = 125,
    val gradeC: Int = 100,
    val gradeD: Int = 75
    // F is anything below gradeD
)

/**
 * Time bonus configuration
 */
data class TimeBonusConfig(
    val halfTimeBonus: Int = 100,    // Bonus for completing in ≤50% of time limit
    val threeQuarterTimeBonus: Int = 75,  // Bonus for completing in ≤75% of time limit
    val withinTimeBonus: Int = 50,   // Bonus for completing within time limit
    val overtimeBonus: Int = 0,      // Bonus for completing over time limit
    
    // Basic grading time bonuses (smaller values)
    val basicHalfTimeBonus: Int = 50,
    val basicThreeQuarterTimeBonus: Int = 25,
    val basicWithinTimeBonus: Int = 10,
    val basicOvertimeBonus: Int = 0
)

/**
 * Efficiency bonus configuration
 */
data class EfficiencyBonusConfig(
    val maxInkCapacity: Float = 1000f,  // Maximum ink capacity for efficiency calculation
    val maxEfficiencyBonus: Int = 100   // Maximum bonus points for perfect efficiency
)

/**
 * Robot bonus configuration
 */
data class RobotBonusConfig(
    val maxRobotBonus: Int = 100  // Maximum bonus points for converting all robots
)

/**
 * Secrets/doors bonus configuration
 */
data class SecretsBonusConfig(
    val maxSecretsBonus: Int = 100  // Maximum bonus points for finding all secrets/doors
)

/**
 * Data class for campaign level configuration
 */
data class CampaignLevelData(
    val levelId: String,
    val levelName: String,
    val robotPositions: List<RobotData> = emptyList(),
    val securityDevices: List<SecurityDeviceData> = emptyList(),
    val hardenedPaintAreas: List<HardenedPaintData> = emptyList(),
    val doorActivators: List<DoorActivatorData> = emptyList(), // New: replaces secretAreas
    val exitZone: ExitZoneData? = null, // New: level exit area
    val requiredCoverage: Float = 1.0f,
    val timeLimit: Long? = null,
    val mazeComplexity: String = "MEDIUM",
    val requiresSinglePath: Boolean = false, // New: true for puzzle levels that need linear progression
    val gradingConfig: LevelGradingConfig = LevelGradingConfig() // New: per-level grading configuration
)

/**
 * Data class for robot configuration
 */
data class RobotData(
    val x: Float,
    val y: Float,
    val patrolPath: List<Pair<Float, Float>> = emptyList(),
    val unpaintRadius: Float = 50f
)

/**
 * Data class for security device configuration
 */
data class SecurityDeviceData(
    val x: Float,
    val y: Float,
    val deviceType: DeviceType,
    val controlPanelPosition: Pair<Float, Float>,
    val requiredFrequency: ColorFrequency
)

/**
 * Data class for hardened paint area configuration
 */
data class HardenedPaintData(
    val area: RectF,
    val requiredFrequency: ColorFrequency
)

/**
 * Data class for door activator configuration
 * Replaces SecretAreaData with interactive puzzle doors
 */
data class DoorActivatorData(
    val activatorArea: RectF,     // The colored square that needs to be painted
    val wallArea: RectF,          // The wall that gets removed when activated
    val requiredFrequency: ColorFrequency, // Color needed to activate
    val description: String       // Description of what this door opens
)

/**
 * Data class for exit zone configuration
 * Defines the area where players need to go to complete the level
 */
data class ExitZoneData(
    val area: RectF,              // The exit area
    val description: String = "Level Exit" // Description for the exit
)

/**
 * Enum for device types
 */
enum class DeviceType {
    LASER_GRID,
    AUTO_TURRET,
    FORCE_FIELD
}

/**
 * Enum for color frequencies
 */
enum class ColorFrequency {
    RED, BLUE, GREEN, YELLOW
}

/**
 * Object containing all campaign level definitions
 */
object CampaignLevels {
    
    /**
     * Get level data for a specific level ID
     */
    fun getLevelData(levelId: String): CampaignLevelData? {
        return when (levelId) {
            "level_1" -> LEVEL_1
            "level_2" -> LEVEL_2
            "level_3" -> LEVEL_3
            "level_4a" -> LEVEL_4A
            "level_4b" -> LEVEL_4B
            else -> null
        }
    }
    
    /**
     * Get all available level IDs
     */
    fun getAllLevelIds(): List<String> {
        return listOf("level_1", "level_2", "level_3", "level_4a", "level_4b")
    }
    
    // Level 1: Tutorial - Simple linear maze with door puzzle
    val LEVEL_1 = CampaignLevelData(
        levelId = "level_1",
        levelName = "First Steps",
        robotPositions = emptyList(), // No robots in tutorial
        securityDevices = emptyList(), // No security devices in tutorial
        hardenedPaintAreas = emptyList(), // No hardened paint in tutorial
        doorActivators = listOf(
            DoorActivatorData(
                /*
                // (1)left  (2)top  (3)right  (4)bottom
                // Movement Rules:
                // Move RIGHT: Increase (1)left and (3)right by same amount
                // Move DOWN: Increase (2)top and (4)bottom by same amount
                // Move LEFT: Decrease (1)left and (3)right by same amount
                // Move UP: Decrease (2)top and (4)bottom by same amount
                */
                activatorArea = RectF(370f, 515f, 420f, 610f), // 50x50 activator square positioned near the path
                wallArea = RectF(430f, 510f, 455f, 610f), // 30x90 door wall blocking the main path corridor
                requiredFrequency = ColorFrequency.RED,
                description = "Main pathway door"
            )
        ),
        exitZone = null, // Will be positioned at maze exit automatically for single-path levels
        requiredCoverage = 0.1f, // Very low coverage requirement for tutorial
        timeLimit = null, // No time limit for tutorial
        mazeComplexity = "LOW", // Simple maze for tutorial
        requiresSinglePath = true, // Tutorial is a puzzle level requiring door unlock
        gradingConfig = LevelGradingConfig(
            useBasicGrading = true, // Use simpler grading for tutorial
            baseCompletionScore = 100, // Base score for completing tutorial
            secretsBonusConfig = SecretsBonusConfig(maxSecretsBonus = 50) // Lower bonus for tutorial
        )
    )
    
    // Level 2: First Contact - Introduces security devices and more doors
    val LEVEL_2 = CampaignLevelData(
        levelId = "level_2",
        levelName = "First Contact",
        robotPositions = listOf(
            RobotData(
                x = 150f,
                y = 150f,
                patrolPath = listOf(
                    250f to 250f,
                    600f to 250f
                )
            ),
            RobotData(
                x = 650f,
                y = 650f,
                patrolPath = listOf(
                    650f to 650f,
                    400f to 650f
                )
            )
        ),
        securityDevices = listOf(
            SecurityDeviceData(
                x = 200f,
                y = 200f,
                deviceType = DeviceType.LASER_GRID,
                controlPanelPosition = 180f to 180f,
                requiredFrequency = ColorFrequency.BLUE
            )
        ),
        hardenedPaintAreas = listOf(
            HardenedPaintData(
                area = RectF(100f, 100f, 150f, 150f),
                requiredFrequency = ColorFrequency.RED
            )
        ),
        doorActivators = listOf(
            DoorActivatorData(
                activatorArea = RectF(440f, 820f, 480f, 870f),
                wallArea = RectF(490f, 800f, 510f, 890f),
                requiredFrequency = ColorFrequency.BLUE,
                description = "Secondary access door"
            )
        ),
        exitZone = null, // Will be positioned at maze exit automatically
        requiredCoverage = 0.6f,
        timeLimit = null,
        mazeComplexity = "MEDIUM",
        requiresSinglePath = false, // Linear maze
        gradingConfig = LevelGradingConfig(
            // Slightly easier grading for level 2
            gradeThresholds = GradeThresholds(
                gradeA = 300, // Reduced from 350
                gradeB = 250, // Reduced from 300
                gradeC = 200, // Reduced from 250
                gradeD = 150  // Reduced from 200
            )
        )
    )
    
    // Level 3: Deep Infiltration - More complex puzzles
    val LEVEL_3 = CampaignLevelData(
        levelId = "level_3",
        levelName = "Deep Infiltration",
        robotPositions = listOf(
            RobotData(
                x = 120f,
                y = 120f,
                patrolPath = listOf(
                    120f to 120f,
                    280f to 120f,
                    280f to 280f,
                    120f to 280f
                )
            ),
            RobotData(
                x = 200f,
                y = 200f,
                patrolPath = listOf(
                    200f to 200f,
                    350f to 200f
                )
            )
        ),
        securityDevices = listOf(
            SecurityDeviceData(
                x = 180f,
                y = 180f,
                deviceType = DeviceType.AUTO_TURRET,
                controlPanelPosition = 160f to 160f,
                requiredFrequency = ColorFrequency.GREEN
            ),
            SecurityDeviceData(
                x = 320f,
                y = 320f,
                deviceType = DeviceType.FORCE_FIELD,
                controlPanelPosition = 300f to 300f,
                requiredFrequency = ColorFrequency.YELLOW
            )
        ),
        hardenedPaintAreas = listOf(
            HardenedPaintData(
                area = RectF(80f, 80f, 140f, 140f),
                requiredFrequency = ColorFrequency.RED
            ),
            HardenedPaintData(
                area = RectF(260f, 260f, 320f, 320f),
                requiredFrequency = ColorFrequency.BLUE
            )
        ),
        doorActivators = listOf(
            DoorActivatorData(
                activatorArea = RectF(410f, 330f, 430f, 410f), //LBRT
                wallArea = RectF(390f, 330f, 410f, 410f),
                requiredFrequency = ColorFrequency.GREEN,
                description = "Access tunnel door"
            ),
            DoorActivatorData(
                activatorArea = RectF(820f, 670f, 840f, 750f),
                wallArea = RectF(840f, 670f, 860f, 750f),
                requiredFrequency = ColorFrequency.YELLOW,
                description = "Document archive door"
            ),
            DoorActivatorData(
                activatorArea = RectF(415f, 991f, 435f, 921f),
                wallArea = RectF(435f, 991f, 455f, 921f),
                requiredFrequency = ColorFrequency.YELLOW,
                description = "Document archive door"
            )
        ),
        exitZone = null, // Will be positioned at maze exit automatically
        requiredCoverage = 0.8f,
        timeLimit = 300000L, // 5 minutes
        mazeComplexity = "HIGH",
        requiresSinglePath = false, // Linear maze
        gradingConfig = LevelGradingConfig(
            // Higher bonuses for more complex level
            timeBonusConfig = TimeBonusConfig(
                halfTimeBonus = 150,        // Increased from 100
                threeQuarterTimeBonus = 100, // Increased from 75
                withinTimeBonus = 75        // Increased from 50
            ),
            robotBonusConfig = RobotBonusConfig(
                maxRobotBonus = 150 // Increased from 100 for multiple robots
            )
        )
    )
    
    // Level 4A: Branch A - Power Plant
    val LEVEL_4A = CampaignLevelData(
        levelId = "level_4a",
        levelName = "Power Plant Reclamation",
        robotPositions = listOf(
            RobotData(
                x = 100f,
                y = 100f,
                patrolPath = listOf(
                    100f to 100f,
                    400f to 100f,
                    400f to 400f,
                    100f to 400f
                )
            ),
            RobotData(
                x = 250f,
                y = 250f,
                patrolPath = listOf(
                    250f to 250f,
                    350f to 250f
                )
            ),
            RobotData(
                x = 300f,
                y = 300f,
                patrolPath = listOf(
                    300f to 300f,
                    400f to 300f
                )
            )
        ),
        securityDevices = listOf(
            SecurityDeviceData(
                x = 150f,
                y = 150f,
                deviceType = DeviceType.LASER_GRID,
                controlPanelPosition = 130f to 130f,
                requiredFrequency = ColorFrequency.BLUE
            ),
            SecurityDeviceData(
                x = 350f,
                y = 350f,
                deviceType = DeviceType.AUTO_TURRET,
                controlPanelPosition = 330f to 330f,
                requiredFrequency = ColorFrequency.GREEN
            )
        ),
        hardenedPaintAreas = listOf(
            HardenedPaintData(
                area = RectF(60f, 60f, 120f, 120f),
                requiredFrequency = ColorFrequency.RED
            ),
            HardenedPaintData(
                area = RectF(380f, 380f, 440f, 440f),
                requiredFrequency = ColorFrequency.YELLOW
            )
        ),
        doorActivators = listOf(
            DoorActivatorData(
                activatorArea = RectF(430f, 430f, 470f, 470f),
                wallArea = RectF(480f, 410f, 500f, 490f),
                requiredFrequency = ColorFrequency.RED,
                description = "Power core access door"
            )
        ),
        exitZone = null, // Will be positioned at maze exit automatically
        requiredCoverage = 0.9f,
        timeLimit = 240000L, // 4 minutes
        mazeComplexity = "HIGH",
        requiresSinglePath = false, // Linear maze
        gradingConfig = LevelGradingConfig() // Default grading configuration
    )
    
    // Level 4B: Branch B - Data Hub
    val LEVEL_4B = CampaignLevelData(
        levelId = "level_4b",
        levelName = "Data Hub Infiltration",
        robotPositions = listOf(
            RobotData(
                x = 120f,
                y = 120f,
                patrolPath = listOf(
                    120f to 120f,
                    380f to 120f,
                    380f to 380f,
                    120f to 380f
                )
            ),
            RobotData(
                x = 200f,
                y = 200f,
                patrolPath = listOf(
                    200f to 200f,
                    300f to 200f
                )
            )
        ),
        securityDevices = listOf(
            SecurityDeviceData(
                x = 180f,
                y = 180f,
                deviceType = DeviceType.FORCE_FIELD,
                controlPanelPosition = 160f to 160f,
                requiredFrequency = ColorFrequency.YELLOW
            ),
            SecurityDeviceData(
                x = 320f,
                y = 320f,
                deviceType = DeviceType.LASER_GRID,
                controlPanelPosition = 300f to 300f,
                requiredFrequency = ColorFrequency.BLUE
            )
        ),
        hardenedPaintAreas = listOf(
            HardenedPaintData(
                area = RectF(80f, 80f, 160f, 160f),
                requiredFrequency = ColorFrequency.RED
            ),
            HardenedPaintData(
                area = RectF(240f, 240f, 320f, 320f),
                requiredFrequency = ColorFrequency.GREEN
            )
        ),
        doorActivators = listOf(
            DoorActivatorData(
                activatorArea = RectF(380f, 380f, 420f, 420f),
                wallArea = RectF(430f, 360f, 450f, 440f),
                requiredFrequency = ColorFrequency.BLUE,
                description = "Data core access door"
            ),
            DoorActivatorData(
                activatorArea = RectF(30f, 30f, 70f, 70f),
                wallArea = RectF(80f, 10f, 100f, 90f),
                requiredFrequency = ColorFrequency.RED,
                description = "Backup system door"
            )
        ),
        exitZone = null, // Will be positioned at maze exit automatically
        requiredCoverage = 0.9f,
        timeLimit = 300000L, // 5 minutes
        mazeComplexity = "HIGH",
        requiresSinglePath = false, // Linear maze
        gradingConfig = LevelGradingConfig() // Default grading configuration
    )
} 