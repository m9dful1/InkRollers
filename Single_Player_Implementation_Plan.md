# InkRollers Single-Player Campaign Implementation Plan

**Project:** InkRollers - "The Reclamation Corps" Campaign  
**Version:** 1.0  
**Created:** December 19, 2025  
**Status:** Planning Phase  

---

## Overview

This document outlines the implementation plan for "The Reclamation Corps" single-player campaign, transforming the existing multiplayer painting game into a strategic, puzzle-driven experience. The campaign emphasizes cleverness, efficiency, and tactical decision-making over brute force.

### Core Concept
Players embody a "Master Tactician" within the Reclamation Corps, dedicated to restoring color to a world desaturated by the oppressive Monochromatic Ministry. Each mission involves navigating degraded environments, solving environmental puzzles, and strategically converting Ministry Color Suppressor robots into autonomous painting allies.

### Key Features
- **Color Shift Module:** Toggleable ability to change ink "frequency" for environmental interactions
- **Color Suppressor Robots:** Convertible enemies that become autonomous painting allies
- **Environmental Puzzles:** Security devices, hardened paint areas, and control panels
- **Branching Campaign:** Strategic choices leading to different mission paths
- **Grading System:** Performance-based evaluation with replayability incentives

---

## Implementation Phases

### Phase 1: Core Single-Player Framework (Foundation) - 2-3 weeks
**Status:** ✅ In Progress  
**Priority:** High  

#### 1.1 Project Structure & Entry Point
**Files to Create/Modify:**
- [x] `app/src/main/java/com/spiritwisestudios/inkrollers/campaign/` (new directory)
- [x] `app/src/main/java/com/spiritwisestudios/inkrollers/campaign/CampaignActivity.kt` (new)
- [ ] `app/src/main/java/com/spiritwisestudios/inkrollers/campaign/CampaignLevel.kt` (new)
- [x] `app/src/main/java/com/spiritwisestudios/inkrollers/campaign/CampaignManager.kt` (new)
- [x] `app/src/main/res/layout/activity_campaign.xml` (new)
- [ ] `app/src/main/res/layout/activity_campaign_level.xml` (new)

**Implementation Steps:**
1. **Update HomeActivity UI:**
   - [x] Add "Single Player Campaign" button to submenu in `activity_home.xml`
   - [x] Add campaign mode constants to `HomeActivity.kt`
   - [x] Implement campaign button click handler

2. **Create CampaignActivity:**
   - [x] Campaign map UI with mission selection
   - [x] Progress tracking and save/load functionality
   - [x] Branching path logic

3. **Create CampaignManager:**
   - [x] Singleton for campaign state management
   - [x] Level progression tracking
   - [x] Save/load campaign progress

#### 1.2 Campaign Level System
**New Classes:**
- [ ] `CampaignLevel.kt` - Extends Level interface for campaign-specific levels
- [x] `CampaignLevelData.kt` - Data class for level configuration
- [x] `CampaignProgress.kt` - Data class for campaign save data
- [x] `MissionAdapter.kt` - RecyclerView adapter for mission list
- [x] `item_mission.xml` - Layout for mission list items

**Implementation:**
```kotlin
// CampaignLevel.kt - Extends Level interface
class CampaignLevel(
    screenW: Int,
    screenH: Int,
    private val levelData: CampaignLevelData,
    private val topMargin: Int = 0
) : Level {
    // Campaign-specific level implementation
    // Includes robot placement, environmental puzzles, etc.
}

// CampaignLevelData.kt - Data class for level configuration
data class CampaignLevelData(
    val levelId: String,
    val levelName: String,
    val robotPositions: List<RobotData>,
    val securityDevices: List<SecurityDeviceData>,
    val hardenedPaintAreas: List<HardenedPaintData>,
    val requiredCoverage: Float = 1.0f,
    val timeLimit: Long? = null
)
```

#### 1.3 Campaign Manager
```kotlin
// CampaignManager.kt - Singleton for campaign state management
object CampaignManager {
    private var currentLevel: String? = null
    private var completedLevels: Set<String> = emptySet()
    private var campaignProgress: CampaignProgress? = null
    
    fun startLevel(levelId: String): CampaignLevelData
    fun completeLevel(levelId: String, grade: LevelGrade)
    fun getAvailableLevels(): List<String>
    fun saveProgress()
    fun loadProgress()
}
```

**Deliverables:**
- [ ] Campaign entry point from home screen
- [ ] Basic campaign level loading system
- [ ] Campaign progress save/load functionality
- [ ] Simple campaign map UI

---

### Phase 2: Core Single-Player Mechanics (3-4 weeks)
**Status:** ❌ Not Started  
**Priority:** High  

#### 2.1 Color Shift Module
**Files to Modify:**
- [ ] `Player.kt` - Add color shift functionality
- [ ] `GameView.kt` - Add color shift input handling
- [ ] `CampaignLevel.kt` - Add frequency-based interactions

**Implementation:**
```kotlin
// In Player.kt
enum class ColorFrequency {
    RED, BLUE, GREEN, YELLOW
}

class Player {
    private var currentFrequency: ColorFrequency = ColorFrequency.RED
    private val frequencyColors = mapOf(
        ColorFrequency.RED to Color.RED,
        ColorFrequency.BLUE to Color.BLUE,
        ColorFrequency.GREEN to Color.GREEN,
        ColorFrequency.YELLOW to Color.YELLOW
    )
    
    fun toggleColorShift() {
        currentFrequency = when (currentFrequency) {
            ColorFrequency.RED -> ColorFrequency.BLUE
            ColorFrequency.BLUE -> ColorFrequency.GREEN
            ColorFrequency.GREEN -> ColorFrequency.YELLOW
            ColorFrequency.YELLOW -> ColorFrequency.RED
        }
        audioManager?.playSound(AudioManager.SoundType.COLOR_SHIFT)
    }
    
    fun getCurrentFrequency(): ColorFrequency = currentFrequency
    fun getFrequencyColor(): Int = frequencyColors[currentFrequency] ?: Color.RED
}
```

#### 2.2 Color Suppressor Robots
**New Classes:**
- [ ] `campaign/Robot.kt` - Robot AI and conversion logic
- [ ] `campaign/RobotData.kt` - Robot configuration data

**Implementation:**
```kotlin
// campaign/Robot.kt
class Robot(
    x: Float, y: Float,
    private val patrolPath: List<Pair<Float, Float>>,
    private val unpaintRadius: Float = 50f
) {
    private var isConverted: Boolean = false
    private var conversionProgress: Float = 0f
    private var currentPatrolIndex: Int = 0
    private var position: Pair<Float, Float> = x to y
    
    fun update(deltaTime: Float, paintSurface: PaintSurface)
    fun paintRobot(playerColor: Int, paintSurface: PaintSurface): Boolean
    fun isFullyConverted(): Boolean = isConverted
    fun getPosition(): Pair<Float, Float> = position
    fun shouldUnpaintArea(x: Float, y: Float): Boolean
}
```

#### 2.3 Environmental Puzzles
**New Classes:**
- [ ] `campaign/SecurityDevice.kt` - Security device logic
- [ ] `campaign/HardenedPaint.kt` - Hardened paint area logic
- [ ] `campaign/ControlPanel.kt` - Control panel interaction logic

**Implementation:**
```kotlin
// campaign/SecurityDevice.kt
class SecurityDevice(
    x: Float, y: Float,
    private val deviceType: DeviceType,
    private val controlPanelPosition: Pair<Float, Float>,
    private val requiredFrequency: ColorFrequency
) {
    private var isDisabled: Boolean = false
    
    fun interactWithControlPanel(frequency: ColorFrequency): Boolean
    fun isBlockingPath(x: Float, y: Float): Boolean
    fun getControlPanelPosition(): Pair<Float, Float> = controlPanelPosition
}

// campaign/HardenedPaint.kt
class HardenedPaint(
    private val area: RectF,
    private val requiredFrequency: ColorFrequency
) {
    fun canBeDissolved(frequency: ColorFrequency): Boolean
    fun dissolve(frequency: ColorFrequency): Boolean
    fun getArea(): RectF = area
}
```

**Deliverables:**
- [ ] Color shift functionality with visual feedback
- [ ] Robot AI with patrol and conversion mechanics
- [ ] Environmental puzzle interactions
- [ ] Basic campaign level with working mechanics

---

### Phase 3: Campaign Level Design & Integration (2-3 weeks)
**Status:** ❌ Not Started  
**Priority:** Medium  

#### 3.1 Level Data Structure
**New Files:**
- [ ] `campaign/levels/LevelData.kt` - Campaign level definitions
- [ ] `campaign/levels/Level1.kt` - First campaign level
- [ ] `campaign/levels/Level2.kt` - Second campaign level

**Implementation:**
```kotlin
// campaign/levels/LevelData.kt
object CampaignLevels {
    val LEVEL_1 = CampaignLevelData(
        levelId = "level_1",
        levelName = "The Awakening",
        robotPositions = listOf(
            RobotData(100f, 100f, listOf(100f to 100f, 200f to 100f, 200f to 200f, 100f to 200f))
        ),
        securityDevices = emptyList(),
        hardenedPaintAreas = emptyList(),
        requiredCoverage = 0.8f
    )
    
    val LEVEL_2 = CampaignLevelData(
        levelId = "level_2", 
        levelName = "First Contact",
        robotPositions = listOf(
            RobotData(150f, 150f, listOf(150f to 150f, 300f to 150f)),
            RobotData(250f, 250f, listOf(250f to 250f, 400f to 250f))
        ),
        securityDevices = listOf(
            SecurityDeviceData(200f, 200f, DeviceType.LASER_GRID, 180f to 180f, ColorFrequency.BLUE)
        ),
        hardenedPaintAreas = listOf(
            HardenedPaintData(RectF(100f, 100f, 150f, 150f), ColorFrequency.RED)
        ),
        requiredCoverage = 0.9f
    )
}
```

#### 3.2 Campaign Level Implementation
**Modify CampaignLevel.kt:**
```kotlin
class CampaignLevel(
    screenW: Int,
    screenH: Int,
    private val levelData: CampaignLevelData,
    private val topMargin: Int = 0
) : Level {
    private val robots = mutableListOf<Robot>()
    private val securityDevices = mutableListOf<SecurityDevice>()
    private val hardenedPaintAreas = mutableListOf<HardenedPaint>()
    private val mazeLevel: MazeLevel
    
    init {
        // Initialize maze as base
        mazeLevel = MazeLevel(screenW, screenH, 12, 20, 12f, levelData.levelId.hashCode().toLong())
        
        // Add campaign elements
        setupRobots()
        setupSecurityDevices()
        setupHardenedPaint()
    }
    
    override fun update(): Boolean {
        // Update robots
        robots.forEach { it.update(deltaTime, paintSurface) }
        
        // Check win condition
        return calculateCoverage(paintSurface).values.maxOrNull() ?: 0f >= levelData.requiredCoverage
    }
    
    override fun draw(canvas: Canvas) {
        // Draw base maze
        mazeLevel.draw(canvas)
        
        // Draw campaign elements
        drawRobots(canvas)
        drawSecurityDevices(canvas)
        drawHardenedPaint(canvas)
    }
}
```

**Deliverables:**
- [ ] Complete level data structure
- [ ] First 2-3 campaign levels implemented
- [ ] Level progression system
- [ ] Campaign branching logic

---

### Phase 4: UI & Visual Feedback (2 weeks)
**Status:** ❌ Not Started  
**Priority:** Medium  

#### 4.1 Campaign UI
**New Layout Files:**
- [ ] `activity_campaign.xml` - Campaign map screen
- [ ] `activity_campaign_level.xml` - Campaign level gameplay screen
- [ ] `fragment_campaign_map.xml` - Campaign map fragment
- [ ] `item_mission.xml` - Mission list item layout

**Implementation:**
```xml
<!-- activity_campaign.xml -->
<androidx.constraintlayout.widget.ConstraintLayout>
    <ImageView android:id="@+id/campaign_map" />
    <TextView android:id="@+id/campaign_title" />
    <RecyclerView android:id="@+id/mission_list" />
    <Button android:id="@+id/button_back" />
</androidx.constraintlayout.widget.ConstraintLayout>

<!-- activity_campaign_level.xml -->
<androidx.constraintlayout.widget.ConstraintLayout>
    <com.spiritwisestudios.inkrollers.GameView android:id="@+id/game_view" />
    <Button android:id="@+id/button_color_shift" />
    <TextView android:id="@+id/text_frequency" />
    <TextView android:id="@+id/text_coverage" />
    <TextView android:id="@+id/text_grade" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

#### 4.2 Visual Effects
**New Classes:**
- [ ] `campaign/effects/CampaignEffects.kt` - Visual effects manager
- [ ] `campaign/effects/ColorShiftEffect.kt` - Color shift visual feedback
- [ ] `campaign/effects/RobotConversionEffect.kt` - Robot conversion effects

**Implementation:**
```kotlin
// campaign/effects/CampaignEffects.kt
class CampaignEffects {
    fun drawColorShiftEffect(canvas: Canvas, player: Player)
    fun drawRobotConversionEffect(canvas: Canvas, robot: Robot)
    fun drawAreaCompletionEffect(canvas: Canvas, area: RectF)
    fun drawBloomEffect(canvas: Canvas, center: Pair<Float, Float>)
}
```

**Deliverables:**
- [ ] Complete campaign UI flow
- [ ] Visual effects for all campaign mechanics
- [ ] Smooth transitions between screens
- [ ] Responsive UI design

---

### Phase 5: Grading & Progression System (1-2 weeks)
**Status:** ❌ Not Started  
**Priority:** Low  

#### 5.1 Level Grading
**New Classes:**
- [ ] `campaign/grading/LevelGrading.kt` - Grading system
- [ ] `campaign/grading/LevelGrade.kt` - Grade data class
- [ ] `campaign/grading/GradeCalculator.kt` - Grade calculation logic

**Implementation:**
```kotlin
// campaign/grading/LevelGrading.kt
data class LevelGrade(
    val grade: String, // "A", "B", "C", "D", "F"
    val score: Int,
    val timeBonus: Int,
    val efficiencyBonus: Int,
    val robotBonus: Int,
    val secretsBonus: Int
)

class LevelGrading {
    fun calculateGrade(
        timeTaken: Long,
        inkUsed: Float,
        robotsConverted: Int,
        totalRobots: Int,
        secretsFound: Int,
        totalSecrets: Int
    ): LevelGrade
}
```

#### 5.2 Campaign Progression
**Modify CampaignManager.kt:**
```kotlin
object CampaignManager {
    private val levelDependencies = mapOf(
        "level_2" to setOf("level_1"),
        "level_3" to setOf("level_2"),
        "level_4a" to setOf("level_3"), // Branch A
        "level_4b" to setOf("level_3")  // Branch B
    )
    
    fun getAvailableLevels(): List<String> {
        return levelDependencies.entries
            .filter { (levelId, dependencies) ->
                dependencies.all { it in completedLevels }
            }
            .map { it.key }
    }
}
```

**Deliverables:**
- [ ] Complete grading system
- [ ] Campaign progression tracking
- [ ] Branching path implementation
- [ ] Grade-based unlock system

---

### Phase 6: Audio & Polish (1 week)
**Status:** ❌ Not Started  
**Priority:** Low  

#### 6.1 Campaign Audio
**Modify AudioManager.kt:**
- [ ] Add campaign-specific sound types
- [ ] Implement campaign music system
- [ ] Add sound effects for all campaign mechanics

**Implementation:**
```kotlin
enum class SoundType {
    // Existing sounds...
    COLOR_SHIFT,
    ROBOT_CONVERSION,
    SECURITY_DEVICE_ACTIVATE,
    SECURITY_DEVICE_DEACTIVATE,
    HARDENED_PAINT_DISSOLVE,
    LEVEL_COMPLETE,
    CAMPAIGN_MUSIC
}
```

#### 6.2 Performance Optimization
- [ ] Implement object pooling for effects
- [ ] Optimize robot AI pathfinding
- [ ] Add frame rate monitoring for campaign levels
- [ ] Memory optimization for campaign assets

**Deliverables:**
- [ ] Complete audio integration
- [ ] Performance optimizations
- [ ] Final polish and bug fixes
- [ ] Campaign ready for testing

---

## Implementation Timeline

| Phase | Duration | Key Deliverables | Status |
|-------|----------|------------------|--------|
| Phase 1 | 2-3 weeks | Campaign entry point, basic level system, save/load | ❌ Not Started |
| Phase 2 | 3-4 weeks | Color shift, robots, environmental puzzles | ❌ Not Started |
| Phase 3 | 2-3 weeks | Level design, campaign integration | ❌ Not Started |
| Phase 4 | 2 weeks | UI, visual effects, feedback | ❌ Not Started |
| Phase 5 | 1-2 weeks | Grading system, progression | ❌ Not Started |
| Phase 6 | 1 week | Audio, polish, optimization | ❌ Not Started |

**Total Estimated Time: 11-15 weeks**

---

## Technical Considerations

### 1. Architecture Integration
- [ ] Leverage existing `Level` interface for campaign levels
- [ ] Reuse `GameView` and `GameThread` for campaign gameplay
- [ ] Extend `AudioManager` for campaign-specific sounds
- [ ] Use existing `PaintSurface` for robot conversion tracking

### 2. Data Persistence
- [ ] Use SharedPreferences for campaign progress
- [ ] JSON serialization for level data
- [ ] Firebase integration for cloud saves (optional)

### 3. Performance
- [ ] Robot AI should be efficient (simple pathfinding)
- [ ] Visual effects should be optimized for mobile
- [ ] Level loading should be fast and smooth

### 4. Testing Strategy
- [ ] Unit tests for robot AI and puzzle logic
- [ ] Integration tests for level completion
- [ ] UI tests for campaign flow
- [ ] Performance testing on target devices

---

## Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Scope creep | High | Medium | Strict adherence to phase deliverables |
| Performance issues | Medium | Low | Early performance testing and optimization |
| UI complexity | Medium | Medium | Incremental UI development with user testing |
| Integration challenges | High | Low | Leverage existing architecture patterns |

---

## Success Criteria

### Phase 1 Success Criteria
- [ ] User can access campaign mode from home screen
- [ ] Campaign progress saves and loads correctly
- [ ] Basic campaign level loads and runs without crashes
- [ ] Campaign map UI displays available missions

### Phase 2 Success Criteria
- [ ] Color shift mechanic works with visual feedback
- [ ] Robots patrol and can be converted
- [ ] Environmental puzzles respond to correct frequencies
- [ ] All mechanics work together in a single level

### Phase 3 Success Criteria
- [ ] Multiple campaign levels are playable
- [ ] Level progression works correctly
- [ ] Branching paths function as designed
- [ ] Campaign flow is smooth and intuitive

### Phase 4 Success Criteria
- [ ] Campaign UI is polished and responsive
- [ ] Visual effects enhance gameplay without performance impact
- [ ] All campaign screens have consistent design
- [ ] UI provides clear feedback for all actions

### Phase 5 Success Criteria
- [ ] Grading system provides meaningful feedback
- [ ] Campaign progression unlocks correctly
- [ ] Branching paths work as intended
- [ ] Save/load system maintains all progress data

### Phase 6 Success Criteria
- [ ] Audio enhances campaign experience
- [ ] Performance meets target frame rates
- [ ] No critical bugs remain
- [ ] Campaign is ready for user testing

---

## Notes & Updates

### Development Notes
- This implementation plan assumes the existing codebase architecture remains stable
- Campaign mode should be developed in a separate branch to avoid disrupting multiplayer functionality
- Regular integration testing with the main codebase is recommended

### Future Enhancements (Post-Release)
- Additional campaign levels and story content
- Advanced robot AI behaviors
- More complex environmental puzzles
- Multiplayer campaign mode
- Achievement system integration

---

**Document Version:** 1.0  
**Last Updated:** December 19, 2025  
**Next Review:** After Phase 1 completion 