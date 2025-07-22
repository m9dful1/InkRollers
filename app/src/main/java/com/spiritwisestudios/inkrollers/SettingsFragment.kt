package com.spiritwisestudios.inkrollers

import android.app.Dialog
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * A DialogFragment that provides UI for adjusting audio settings.
 * It allows the user to enable/disable and control the volume for both
 * sound effects (SFX) and background music. Changes are applied in real-time.
 */
class SettingsFragment : DialogFragment() {

    private lateinit var audioManager: AudioManager

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        audioManager = AudioManager.getInstance(requireContext())

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_settings, null)

        val sfxSwitch = view.findViewById<SwitchMaterial>(R.id.switch_sfx)
        val sfxSeekBar = view.findViewById<SeekBar>(R.id.seekbar_sfx_volume)
        val musicSwitch = view.findViewById<SwitchMaterial>(R.id.switch_music)
        val musicSeekBar = view.findViewById<SeekBar>(R.id.seekbar_music_volume)

        sfxSwitch.isChecked = audioManager.isSfxEnabled()
        sfxSeekBar.progress = (audioManager.getMasterVolume() * 100).toInt()
        sfxSeekBar.isEnabled = sfxSwitch.isChecked

        musicSwitch.isChecked = audioManager.isMusicEnabled()
        musicSeekBar.progress = (audioManager.getMusicVolume() * 100).toInt()
        musicSeekBar.isEnabled = musicSwitch.isChecked

        sfxSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioManager.setSfxEnabled(isChecked)
            sfxSeekBar.isEnabled = isChecked
        }

        sfxSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setMasterVolume(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        musicSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioManager.setMusicEnabled(isChecked)
            musicSeekBar.isEnabled = isChecked
        }

        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setMusicVolume(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        builder.setView(view)
            .setPositiveButton("Done") { _, _ -> }

        return builder.create()
    }
} 