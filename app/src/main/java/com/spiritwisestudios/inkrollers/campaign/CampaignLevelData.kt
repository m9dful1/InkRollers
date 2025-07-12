package com.spiritwisestudios.inkrollers.campaign

import android.graphics.RectF

/**
 * Data class for campaign level configuration
 */
data class CampaignLevelData(
    val levelId: String,
    val levelName: String,
    val robotPositions: List<RobotData> = emptyList(),
    val securityDevices: List<SecurityDeviceData> = emptyList(),
    val hardenedPaintAreas: List<HardenedPaintData> = emptyList(),
    val secretAreas: List<SecretAreaData> = emptyList(),
    val requiredCoverage: Float = 1.0f,
    val timeLimit: Long? = null,
    val mazeComplexity: String = "MEDIUM"
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
 * Data class for secret area configuration
 */
data class SecretAreaData(
    val area: RectF,
    val secretType: SecretType,
    val description: String,
    val requiredFrequency: ColorFrequency? = null, // New: frequency requirement for discovery
    val unlockCondition: SecretUnlockCondition? = null // New: additional unlock requirements
)

/**
 * Enum for secret unlock conditions
 */
enum class SecretUnlockCondition {
    PROXIMITY_ONLY,      // Discovered just by being nearby
    FREQUENCY_MATCH,     // Requires correct frequency to be active
    PAINT_REQUIRED,      // Requires painting the area with correct color
    TIME_THRESHOLD,      // Only appears after certain time
    ROBOT_ASSISTED       // Requires converted robot nearby
}

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
 * Enum for secret types
 */
enum class SecretType {
    HIDDEN_PASSAGE,
    BONUS_POWERUP,
    STORY_FRAGMENT,
    ACHIEVEMENT
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
    
    // Level 1: The Awakening - Introduction level
    val LEVEL_1 = CampaignLevelData(
        levelId = "level_1",
        levelName = "The Awakening",
        robotPositions = listOf(
            RobotData(
                x = 400f,
                y = 300f,
                patrolPath = listOf(
                    400f to 300f,
                    600f to 300f,
                    600f to 500f,
                    400f to 500f
                )
            )
        ),
        securityDevices = emptyList(),
        hardenedPaintAreas = emptyList(),
        secretAreas = listOf(
            SecretAreaData(
                area = RectF(50f, 50f, 80f, 80f),
                secretType = SecretType.STORY_FRAGMENT,
                description = "Hidden Reclamation Corps message",
                requiredFrequency = ColorFrequency.RED,
                unlockCondition = SecretUnlockCondition.FREQUENCY_MATCH
            )
        ),
        requiredCoverage = 0.8f,
        timeLimit = null,
        mazeComplexity = "LOW"
    )
    
    // Level 2: First Contact - Introduces security devices
    val LEVEL_2 = CampaignLevelData(
        levelId = "level_2",
        levelName = "First Contact",
        robotPositions = listOf(
            RobotData(
                x = 150f,
                y = 150f,
                patrolPath = listOf(
                    150f to 150f,
                    300f to 150f
                )
            ),
            RobotData(
                x = 250f,
                y = 250f,
                patrolPath = listOf(
                    250f to 250f,
                    400f to 250f
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
        secretAreas = listOf(
            SecretAreaData(
                area = RectF(350f, 350f, 380f, 380f),
                secretType = SecretType.BONUS_POWERUP,
                description = "Enhanced paint capacity",
                requiredFrequency = ColorFrequency.BLUE,
                unlockCondition = SecretUnlockCondition.PAINT_REQUIRED
            )
        ),
        requiredCoverage = 0.9f,
        timeLimit = null,
        mazeComplexity = "MEDIUM"
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
        secretAreas = listOf(
            SecretAreaData(
                area = RectF(400f, 400f, 430f, 430f),
                secretType = SecretType.HIDDEN_PASSAGE,
                description = "Secret Ministry access tunnel",
                requiredFrequency = ColorFrequency.GREEN,
                unlockCondition = SecretUnlockCondition.FREQUENCY_MATCH
            ),
            SecretAreaData(
                area = RectF(60f, 60f, 90f, 90f),
                secretType = SecretType.STORY_FRAGMENT,
                description = "Classified Ministry document",
                requiredFrequency = ColorFrequency.YELLOW,
                unlockCondition = SecretUnlockCondition.TIME_THRESHOLD
            )
        ),
        requiredCoverage = 0.95f,
        timeLimit = 300000L, // 5 minutes
        mazeComplexity = "HIGH"
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
        secretAreas = listOf(
            SecretAreaData(
                area = RectF(450f, 450f, 480f, 480f),
                secretType = SecretType.ACHIEVEMENT,
                description = "Power Plant Master achievement",
                requiredFrequency = null,
                unlockCondition = SecretUnlockCondition.ROBOT_ASSISTED
            )
        ),
        requiredCoverage = 1.0f,
        timeLimit = 240000L, // 4 minutes
        mazeComplexity = "HIGH"
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
        secretAreas = listOf(
            SecretAreaData(
                area = RectF(400f, 400f, 430f, 430f),
                secretType = SecretType.STORY_FRAGMENT,
                description = "Data Hub access codes",
                requiredFrequency = ColorFrequency.BLUE,
                unlockCondition = SecretUnlockCondition.FREQUENCY_MATCH
            ),
            SecretAreaData(
                area = RectF(50f, 50f, 80f, 80f),
                secretType = SecretType.BONUS_POWERUP,
                description = "Temporary invincibility",
                requiredFrequency = ColorFrequency.RED,
                unlockCondition = SecretUnlockCondition.PAINT_REQUIRED
            )
        ),
        requiredCoverage = 1.0f,
        timeLimit = 300000L, // 5 minutes
        mazeComplexity = "HIGH"
    )
} 