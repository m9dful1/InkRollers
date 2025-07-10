# Particle Effects System

This directory contains the particle effects system for Ink Rollers, providing visual feedback for paint actions.

## Overview

The particle system creates realistic paint splat effects when players paint in the game. It's designed to be lightweight and performance-conscious while providing satisfying visual feedback.

## Components

### Particle.kt
- **Purpose**: Represents individual particles in the paint splat effect
- **Features**:
  - Random direction and velocity for natural splat appearance
  - Gravity effect for realistic movement
  - Fade-out and shrink effects as particles age
  - Configurable lifetime, size, and velocity

### ParticleManager.kt
- **Purpose**: Manages all particles in the game
- **Features**:
  - Creates paint splat effects with multiple particles
  - Updates and removes dead particles
  - Performance monitoring and limits
  - Rendering coordination

## How It Works

1. **Particle Creation**: When a player paints, `ParticleManager.createPaintSplat()` is called
2. **Splat Generation**: Creates 8 particles with random properties around the paint point
3. **Particle Lifecycle**: Each particle moves, fades, and shrinks over its lifetime
4. **Cleanup**: Dead particles are automatically removed to maintain performance

## Performance Considerations

- **Maximum Particles**: 200 active particles (configurable)
- **Performance Warning**: Logs warnings when approaching limits
- **Automatic Cleanup**: Dead particles are removed immediately
- **Throttled Logging**: Performance stats logged every 5 seconds

## Integration

The particle system is integrated into:
- **GameView**: Manages the ParticleManager instance
- **Player**: Creates particles when local player paints
- **GameRenderer**: Renders particles on top of game elements
- **GameUpdateManager**: Updates particles each frame

## Configuration

Key parameters can be adjusted in `ParticleManager.kt`:
- `MAX_PARTICLES`: Maximum number of active particles
- `PARTICLES_PER_SPLAT`: Number of particles per paint action
- `PERFORMANCE_WARNING_THRESHOLD`: When to log performance warnings

## Future Enhancements

Potential improvements for the particle system:
- Different particle types (splash, spray, etc.)
- Particle color blending with background
- Wind effects for more dynamic movement
- Particle trails for continuous painting
- Performance-based particle count adjustment 