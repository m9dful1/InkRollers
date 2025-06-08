# Audio Resources for Ink Rollers

This directory contains sound effect files used throughout the game. Currently, placeholder files are present that need to be replaced with actual audio content.

## Required Sound Effects

### **UI Sounds**
- `ui_click.wav` - Button click sound effect
- `countdown_tick.wav` - Countdown number sounds (3, 2, 1)
- `countdown_go.wav` - "GO!" sound for match start

### **Gameplay Sounds**
- `paint_splash.wav` - Sound when player paints (loops while painting/moving, should be subtle/quiet)
- `mode_toggle.wav` - Sound when switching between PAINT/FILL modes
- `ink_refill.wav` - Sound when refilling ink in FILL mode
- `player_join.wav` - Sound when another player joins the game

### **Match Events**
- `match_start.wav` - Sound when the actual match begins
- `match_end_win.wav` - Victory sound
- `match_end_lose.wav` - Defeat sound

## Audio Specifications

- **Format**: WAV files preferred for best compatibility
- **Sample Rate**: 44.1 kHz
- **Bit Depth**: 16-bit
- **Duration**: Keep sounds short (0.1-2 seconds for most effects)
- **Volume**: Normalized but not overly loud (will be controlled by game volume settings)

## Free Sound Resources

### Recommended Sources:
1. **Freesound.org** - High-quality Creative Commons sounds
2. **Zapsplat.com** - Free with registration
3. **Adobe Audition** - Built-in sound effects library
4. **GarageBand** (Mac) - Sound effects library
5. **OpenGameArt.org** - Game-specific sound effects

### Sound Design Tips:
- **Paint sounds**: Light splash, brush stroke, or liquid drop sounds (designed for seamless looping)
- **UI sounds**: Clean, crisp clicks or beeps
- **Victory/defeat**: Triumphant chords vs. descending tones
- **Countdown**: Digital beeps or voice countdown
- **Mode toggle**: Switch or click sound
- **Player join**: Notification chime or brief fanfare

## Implementation Notes

The `AudioManager` class handles all sound playback using Android's `SoundPool` for optimal performance. Sounds are loaded at app start and cached for low-latency playback during gameplay.

### Audio Features:
- **Looping Sounds**: Paint sounds loop seamlessly while the player is moving and painting
- **Dynamic Control**: Sounds start/stop based on player actions (movement, mode changes, ink levels)
- **Volume Control**: Individual volume levels per sound type
- **Audio Settings**: Entire audio system can be disabled via user preferences (to be implemented in M-14)

Volume levels can be adjusted per sound type, and the entire audio system can be disabled via user preferences (to be implemented in M-14). 