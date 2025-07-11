# Campaign Audio Requirements - Phase 6

## Missing Audio Files

The following campaign-specific audio files need to be created and added to `/res/raw/`:

### Campaign Sound Effects
1. **`color_shift.wav`** - Sound when player changes color frequency
   - Duration: 0.3-0.5 seconds
   - Style: Electronic frequency shift, digital tone change
   - Volume: Medium (similar to mode_toggle.wav)

2. **`robot_conversion.wav`** - Sound when robot is successfully converted
   - Duration: 1.0-1.5 seconds
   - Style: Mechanical whirr transitioning to friendly beep
   - Volume: Medium-high (achievement sound)

3. **`security_device_activate.wav`** - Sound when security device activates
   - Duration: 0.5-0.8 seconds
   - Style: Warning alarm, electronic alert
   - Volume: Medium (warning sound)

4. **`security_device_deactivate.wav`** - Sound when security device is disabled
   - Duration: 0.5-0.8 seconds
   - Style: Power down, electronic shutdown
   - Volume: Medium (success sound)

5. **`hardened_paint_dissolve.wav`** - Sound when hardened paint dissolves
   - Duration: 0.8-1.2 seconds
   - Style: Chemical reaction, dissolving sound
   - Volume: Medium (satisfying effect)

6. **`level_complete.wav`** - Sound when campaign level is completed
   - Duration: 2.0-3.0 seconds
   - Style: Triumphant fanfare, achievement music
   - Volume: High (celebration sound)

### Campaign Background Music
7. **`campaign_bg.wav`** - Background music for campaign mode
   - Duration: 2-3 minutes (looping)
   - Style: Ambient, strategic, slightly tense
   - Volume: Low-medium (background level)
   - Format: Should loop seamlessly

## Audio Integration Status

### ✅ Already Implemented
- `AudioManager` has all campaign sound types defined
- Campaign components are set up to use audio
- Audio lifecycle management is in place

### 🔄 Needs Enhancement
- Audio files need to be created and added
- Campaign music system needs to be implemented
- Audio integration in some campaign components needs refinement

## Implementation Steps

### Step 1: Create Audio Files
1. Source or create the 7 missing audio files
2. Ensure they meet the specifications above
3. Add them to `/res/raw/` directory
4. Test audio loading in `AudioManager`

### Step 2: Enhance Campaign Music System
1. Implement campaign-specific background music
2. Add music transitions between campaign screens
3. Ensure proper audio lifecycle management

### Step 3: Performance Optimization
1. Implement object pooling for visual effects
2. Optimize robot AI pathfinding
3. Add frame rate monitoring
4. Memory optimization for campaign assets

### Step 4: Final Polish
1. Test all audio integration
2. Performance testing on target devices
3. Bug fixes and final adjustments

## Audio File Sources

### Recommended Sources for Campaign Audio:
1. **Freesound.org** - Search for "electronic", "robot", "security", "achievement"
2. **Zapsplat.com** - Sci-fi and electronic sound effects
3. **Adobe Audition** - Built-in electronic and sci-fi libraries
4. **GarageBand** (Mac) - Electronic and sci-fi sound libraries
5. **OpenGameArt.org** - Game-specific electronic sound effects

### Sound Design Guidelines:
- **Color Shift**: Electronic frequency sweep or digital tone change
- **Robot Conversion**: Mechanical to friendly transition
- **Security Devices**: Warning and success electronic sounds
- **Hardened Paint**: Chemical or dissolving sound effects
- **Level Complete**: Triumphant electronic fanfare
- **Campaign Music**: Ambient, strategic, slightly tense background music

## Testing Checklist

- [ ] All campaign audio files load correctly
- [ ] Audio plays at appropriate times during gameplay
- [ ] Volume levels are appropriate
- [ ] Audio doesn't interfere with performance
- [ ] Campaign music loops seamlessly
- [ ] Audio lifecycle management works correctly
- [ ] No audio memory leaks
- [ ] Performance meets target frame rates

## Success Criteria

Phase 6 will be complete when:
1. All campaign audio files are created and integrated
2. Campaign music system is fully functional
3. Performance optimizations are implemented
4. No critical bugs remain
5. Campaign is ready for user testing 