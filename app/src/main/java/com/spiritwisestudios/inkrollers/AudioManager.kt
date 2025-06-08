package com.spiritwisestudios.inkrollers

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

/**
 * Centralized audio manager for all game sound effects and background music.
 *
 * Uses SoundPool for low-latency audio playback suitable for interactive games.
 * Uses MediaPlayer for background music and longer audio tracks.
 * Handles sound loading, playing, and resource management with automatic cleanup.
 * Provides volume controls and audio preference management.
 */
class AudioManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("audio_settings", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AudioManager"
        private const val MAX_STREAMS = 8
        private const val PREF_MUSIC_ENABLED = "music_enabled"
        private const val PREF_MUSIC_VOLUME = "music_volume"
        private const val PREF_SFX_ENABLED = "sfx_enabled"
        private const val PREF_MASTER_VOLUME = "master_volume"

        @Volatile
        private var INSTANCE: AudioManager? = null

        fun getInstance(context: Context): AudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var soundPool: SoundPool? = null
    private var backgroundMusicPlayer: MediaPlayer? = null
    private val soundIds = mutableMapOf<SoundType, Int>()
    private var isInitialized = false
    private var masterVolume = 0.7f
    private var sfxEnabled = true
    private var musicEnabled = true
    private var musicVolume = 0.5f
    private var isMusicSupposedToPlay = false

    // For tracking looping sounds
    private val loopingStreams = mutableMapOf<SoundType, Int>()

    // Sound effect types
    enum class SoundType(val fileName: String) {
        PAINT("paint_splash.wav"),
        MODE_TOGGLE("mode_toggle.wav"),
        REFILL("ink_refill.wav"),
        UI_CLICK("ui_click.wav"),
        MATCH_START("match_start.wav"),
        MATCH_END_WIN("match_end_win.wav"),
        MATCH_END_LOSE("match_end_lose.wav"),
        PLAYER_JOIN("player_join.wav"),
        COUNTDOWN_TICK("countdown_tick.wav"),
        COUNTDOWN_GO("countdown_go.wav")
    }

    /**
     * Initializes the SoundPool and loads all sound effects.
     * Should be called during app initialization or when audio is needed.
     */
    fun initialize() {
        if (isInitialized) return

        loadSettings()

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(audioAttributes)
                .build()

            loadSounds()
            initializeBackgroundMusic()
            isInitialized = true
            Log.d(TAG, "AudioManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioManager", e)
        }
    }

    /**
     * Initializes the MediaPlayer for background music.
     */
    private fun initializeBackgroundMusic() {
        try {
            val resourceId = context.resources.getIdentifier("bg", "raw", context.packageName)
            if (resourceId != 0) {
                backgroundMusicPlayer = MediaPlayer.create(context, resourceId)
                backgroundMusicPlayer?.apply {
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setVolume(musicVolume, musicVolume)
                }
                Log.d(TAG, "Background music initialized")
            } else {
                Log.w(TAG, "Background music file (bg.wav) not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize background music", e)
        }
    }

    /**
     * Starts playing background music during matches.
     */
    fun startBackgroundMusic() {
        if (!musicEnabled || !isInitialized) return
        isMusicSupposedToPlay = true
        try {
            backgroundMusicPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    Log.d(TAG, "Background music started")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background music", e)
        }
    }

    /**
     * Stops background music.
     */
    fun stopBackgroundMusic() {
        isMusicSupposedToPlay = false
        try {
            backgroundMusicPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0) // Reset to beginning
                    Log.d(TAG, "Background music stopped")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop background music", e)
        }
    }

    /**
     * Pauses background music (maintains current position).
     */
    private fun pauseBackgroundMusic() {
        try {
            backgroundMusicPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    Log.d(TAG, "Background music paused")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause background music", e)
        }
    }

    /**
     * Resumes background music from current position.
     */
    private fun resumeBackgroundMusic() {
        if (!musicEnabled || !isInitialized || !isMusicSupposedToPlay) return

        try {
            backgroundMusicPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    Log.d(TAG, "Background music resumed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume background music", e)
        }
    }

    /**
     * Sets the music volume (0.0 - 1.0).
     */
    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        backgroundMusicPlayer?.setVolume(musicVolume, musicVolume)
        Log.d(TAG, "Music volume set to: $musicVolume")
        saveSetting(PREF_MUSIC_VOLUME, musicVolume)
    }

    /**
     * Enables or disables background music.
     */
    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        if (!enabled && isMusicSupposedToPlay) {
            stopBackgroundMusic()
        }
        Log.d(TAG, "Music enabled: $musicEnabled")
        saveSetting(PREF_MUSIC_ENABLED, enabled)
    }

    /**
     * Gets current music enabled state.
     */
    fun isMusicEnabled(): Boolean = musicEnabled

    /**
     * Gets current music volume.
     */
    fun getMusicVolume(): Float = musicVolume

    /**
     * Loads all sound effects from res/raw into the SoundPool.
     */
    private fun loadSounds() {
        soundPool?.let { pool ->
            SoundType.values().forEach { soundType ->
                try {
                    val resourceId = context.resources.getIdentifier(
                        soundType.fileName.substringBeforeLast('.'),
                        "raw",
                        context.packageName
                    )

                    if (resourceId != 0) {
                        val soundId = pool.load(context, resourceId, 1)
                        soundIds[soundType] = soundId
                        Log.d(TAG, "Loaded sound: ${soundType.name} -> $soundId")
                    } else {
                        Log.w(TAG, "Sound file not found: ${soundType.fileName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load sound: ${soundType.fileName}", e)
                }
            }
        }
    }

    /**
     * Plays a sound effect with optional volume override.
     */
    fun playSound(soundType: SoundType, volume: Float = masterVolume) {
        if (!sfxEnabled || !isInitialized) return

        soundPool?.let { pool ->
            soundIds[soundType]?.let { soundId ->
                try {
                    val actualVolume = (volume * masterVolume).coerceIn(0f, 1f)
                    pool.play(soundId, actualVolume, actualVolume, 1, 0, 1f)
                    Log.v(TAG, "Played sound: ${soundType.name} at volume $actualVolume")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to play sound: ${soundType.name}", e)
                }
            } ?: Log.w(TAG, "Sound not loaded: ${soundType.name}")
        }
    }

    /**
     * Starts playing a looping sound effect. Stops any existing loop of the same type.
     */
    fun startLoopingSound(soundType: SoundType, volume: Float = masterVolume): Int? {
        if (!sfxEnabled || !isInitialized) return null

        // Stop any existing loop of this sound type
        stopLoopingSound(soundType)

        val pool = soundPool ?: return null
        val soundId = soundIds[soundType] ?: run {
            Log.w(TAG, "Sound not loaded: ${soundType.name}")
            return null
        }

        try {
            val actualVolume = (volume * masterVolume).coerceIn(0f, 1f)
            val streamId = pool.play(soundId, actualVolume, actualVolume, 1, -1, 1f)
            if (streamId != 0) {
                loopingStreams[soundType] = streamId
                Log.v(
                    TAG,
                    "Started looping sound: ${soundType.name} with stream ID $streamId at volume $actualVolume"
                )
                return streamId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start looping sound: ${soundType.name}", e)
        }

        return null
    }

    /**
     * Stops a looping sound effect.
     */
    fun stopLoopingSound(soundType: SoundType) {
        loopingStreams[soundType]?.let { streamId ->
            try {
                soundPool?.stop(streamId)
                loopingStreams.remove(soundType)
                Log.v(TAG, "Stopped looping sound: ${soundType.name} with stream ID $streamId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop looping sound: ${soundType.name}", e)
            }
        }
    }

    /**
     * Stops all currently looping sounds.
     */
    fun stopAllLoopingSounds() {
        loopingStreams.keys.toList().forEach { soundType ->
            stopLoopingSound(soundType)
        }
    }

    /**
     * Checks if a sound is currently looping.
     */
    fun isLooping(soundType: SoundType): Boolean {
        return loopingStreams.containsKey(soundType)
    }

    /**
     * Sets the master volume for all sound effects (0.0 - 1.0).
     */
    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        Log.d(TAG, "Master volume set to: $masterVolume")
        saveSetting(PREF_MASTER_VOLUME, masterVolume)
    }

    /**
     * Enables or disables all sound effects.
     */
    fun setSfxEnabled(enabled: Boolean) {
        sfxEnabled = enabled
        if (!enabled) {
            stopAllLoopingSounds()
        }
        Log.d(TAG, "SFX enabled: $sfxEnabled")
        saveSetting(PREF_SFX_ENABLED, enabled)
    }

    /**
     * Gets current SFX enabled state.
     */
    fun isSfxEnabled(): Boolean = sfxEnabled

    /**
     * Gets current master volume.
     */
    fun getMasterVolume(): Float = masterVolume

    private fun saveSetting(key: String, value: Any) {
        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
            }
            apply()
        }
    }

    private fun loadSettings() {
        sfxEnabled = prefs.getBoolean(PREF_SFX_ENABLED, true)
        masterVolume = prefs.getFloat(PREF_MASTER_VOLUME, 0.7f)
        musicEnabled = prefs.getBoolean(PREF_MUSIC_ENABLED, true)
        musicVolume = prefs.getFloat(PREF_MUSIC_VOLUME, 0.5f)
        Log.d(TAG, "Loaded settings: SFX=$sfxEnabled($masterVolume), Music=$musicEnabled($musicVolume)")
    }

    /**
     * Releases all audio resources. Should be called when audio is no longer needed.
     */
    fun release() {
        try {
            stopAllLoopingSounds()
            stopBackgroundMusic()
            soundPool?.release()
            backgroundMusicPlayer?.release()
            soundPool = null
            backgroundMusicPlayer = null
            soundIds.clear()
            loopingStreams.clear()
            isInitialized = false
            isMusicSupposedToPlay = false
            Log.d(TAG, "AudioManager released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioManager", e)
        }
    }

    /**
     * Pauses audio (for when app goes to background).
     */
    fun pauseAudio() {
        soundPool?.autoPause()
        pauseBackgroundMusic()
    }

    /**
     * Resumes audio (for when app comes to foreground).
     */
    fun resumeAudio() {
        soundPool?.autoResume()
        resumeBackgroundMusic()
    }
}