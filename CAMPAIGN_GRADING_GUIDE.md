# Campaign Grading System Guide

## Overview

The campaign grading system now supports **per-level configuration** of grade thresholds and bonus values. This allows you to customize the difficulty and scoring for each campaign level individually, similar to how you can set different seeds for maze generation.

## Key Features

- **Configurable grade thresholds** (A, B, C, D, F cutoffs)
- **Adjustable bonus values** for time, efficiency, robot conversion, and secrets/doors
- **Two grading modes**: Standard and Basic
- **Per-level customization** with sensible defaults
- **Easy-to-use examples** for common scenarios

## How It Works

### Default Values
If you don't specify a `gradingConfig`, the system uses these defaults:
- **Grade Thresholds**: A=350, B=300, C=250, D=200, F=<200
- **Time Bonuses**: 100 (≤50% time), 75 (≤75% time), 50 (within limit), 0 (overtime)
- **Efficiency Bonus**: 0-100 points based on ink usage (max 1000 ink)
- **Robot Bonus**: 0-100 points based on conversion percentage
- **Secrets/Doors Bonus**: 0-100 points based on discovery percentage

### Score Calculation
**Standard Grading**: `Score = Time Bonus + Efficiency Bonus + Robot Bonus + Secrets Bonus`
**Basic Grading**: `Score = Base Score + Time Bonus` (simpler, more lenient)

## Configuration Examples

### 1. Tutorial Level (Easy Grading)
```kotlin
val TUTORIAL_LEVEL = CampaignLevelData(
    // ... other properties ...
    gradingConfig = LevelGradingConfig(
        useBasicGrading = true,           // Simpler scoring
        baseCompletionScore = 100,        // Base score for completion
        secretsBonusConfig = SecretsBonusConfig(
            maxSecretsBonus = 50          // Lower bonus for tutorial
        )
    )
)
```

### 2. Lenient Level (Lower Thresholds)
```kotlin
val EASY_LEVEL = CampaignLevelData(
    // ... other properties ...
    gradingConfig = LevelGradingConfig(
        gradeThresholds = GradeThresholds(
            gradeA = 250,  // Much easier to get A
            gradeB = 200,
            gradeC = 150,
            gradeD = 100
        )
    )
)
```

### 3. Time-Focused Level (High Time Bonuses)
```kotlin
val SPEED_LEVEL = CampaignLevelData(
    // ... other properties ...
    gradingConfig = LevelGradingConfig(
        timeBonusConfig = TimeBonusConfig(
            halfTimeBonus = 200,        // Big reward for speed
            threeQuarterTimeBonus = 150,
            withinTimeBonus = 100
        )
    )
)
```

### 4. Robot-Heavy Level (High Robot Bonuses)
```kotlin
val ROBOT_LEVEL = CampaignLevelData(
    // ... other properties ...
    gradingConfig = LevelGradingConfig(
        robotBonusConfig = RobotBonusConfig(
            maxRobotBonus = 200  // Double the normal robot bonus
        )
    )
)
```

### 5. Challenging Level (Strict Grading)
```kotlin
val HARD_LEVEL = CampaignLevelData(
    // ... other properties ...
    gradingConfig = LevelGradingConfig(
        gradeThresholds = GradeThresholds(
            gradeA = 450,  // Much harder to get A
            gradeB = 400,
            gradeC = 350,
            gradeD = 300
        )
    )
)
```

## Current Level Configurations

### Level 1 (Tutorial)
- Uses **basic grading** for simplicity
- Base completion score: 100
- Lower secrets bonus: 50 (instead of 100)

### Level 2 (First Contact)
- **Easier thresholds**: A=300, B=250, C=200, D=150
- Standard bonuses for all categories

### Level 3 (Deep Infiltration)
- **Higher time bonuses**: 150/100/75 (instead of 100/75/50)
- **Higher robot bonus**: 150 (instead of 100)
- Standard grade thresholds

### Level 4A & 4B (Final Levels)
- **Default configuration** (most challenging)
- Standard thresholds: A=350, B=300, C=250, D=200

## Quick Reference

### All Configuration Options
```kotlin
LevelGradingConfig(
    // Basic vs Standard grading
    useBasicGrading = false,
    baseCompletionScore = 100,
    
    // Grade thresholds
    gradeThresholds = GradeThresholds(
        gradeA = 350,
        gradeB = 300,
        gradeC = 250,
        gradeD = 200
    ),
    
    // Time bonuses
    timeBonusConfig = TimeBonusConfig(
        halfTimeBonus = 100,
        threeQuarterTimeBonus = 75,
        withinTimeBonus = 50,
        overtimeBonus = 0,
        // Basic grading time bonuses
        basicHalfTimeBonus = 50,
        basicThreeQuarterTimeBonus = 25,
        basicWithinTimeBonus = 10,
        basicOvertimeBonus = 0
    ),
    
    // Efficiency bonuses
    efficiencyBonusConfig = EfficiencyBonusConfig(
        maxInkCapacity = 1000f,
        maxEfficiencyBonus = 100
    ),
    
    // Robot bonuses
    robotBonusConfig = RobotBonusConfig(
        maxRobotBonus = 100
    ),
    
    // Secrets/doors bonuses
    secretsBonusConfig = SecretsBonusConfig(
        maxSecretsBonus = 100
    )
)
```

### Pre-made Examples
You can also use pre-made configurations from `GradingExamples.kt`:
```kotlin
gradingConfig = GradingExamples.EASY_TUTORIAL_GRADING
gradingConfig = GradingExamples.LENIENT_GRADING
gradingConfig = GradingExamples.STRICT_GRADING
gradingConfig = GradingExamples.HIGH_TIME_BONUS_GRADING
gradingConfig = GradingExamples.EFFICIENCY_FOCUSED_GRADING
gradingConfig = GradingExamples.ROBOT_FOCUSED_GRADING
gradingConfig = GradingExamples.PUZZLE_FOCUSED_GRADING
gradingConfig = GradingExamples.GENEROUS_GRADING
```

## How to Adjust Grading

1. **Open** `CampaignLevelData.kt`
2. **Find** the level you want to modify (e.g., `LEVEL_1`, `LEVEL_2`, etc.)
3. **Update** the `gradingConfig` parameter with your desired values
4. **Test** the level to ensure the grading feels right

## Tips for Good Grading

- **Tutorial levels**: Use basic grading or very lenient thresholds
- **Early levels**: Make grades easier to achieve to encourage players
- **Timed levels**: Increase time bonuses to reward speed
- **Robot-heavy levels**: Increase robot bonuses to emphasize conversion
- **Puzzle levels**: Increase secrets bonuses to reward exploration
- **Final levels**: Use default or strict grading for challenge

## Troubleshooting

### "Players getting F grades too easily"
- Lower the grade thresholds
- Increase bonus values
- Consider using basic grading for simpler levels

### "Grades are too easy to achieve"
- Raise the grade thresholds
- Decrease bonus values
- Add more challenging objectives

### "Time bonus feels unbalanced"
- Adjust `timeBonusConfig` values
- Consider the level's time limit when setting bonuses
- Test with different completion times

Remember: The goal is to make grading feel fair and rewarding while matching the difficulty and focus of each level! 