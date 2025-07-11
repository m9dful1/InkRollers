package com.spiritwisestudios.inkrollers.campaign

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.spiritwisestudios.inkrollers.R

/**
 * RecyclerView adapter for displaying campaign missions
 */
class MissionAdapter(
    var missions: List<MissionItem>,
    private val onMissionClick: (String) -> Unit
) : RecyclerView.Adapter<MissionAdapter.MissionViewHolder>() {

    /**
     * Data class representing a mission item in the list
     */
    data class MissionItem(
        val levelId: String,
        val levelName: String,
        val isAvailable: Boolean,
        val isCompleted: Boolean,
        val grade: String? = null
    )

    class MissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val missionIcon: ImageView = itemView.findViewById(R.id.mission_icon)
        val missionName: TextView = itemView.findViewById(R.id.mission_name)
        val missionStatus: TextView = itemView.findViewById(R.id.mission_status)
        val missionGrade: TextView = itemView.findViewById(R.id.mission_grade)
        val playButton: Button = itemView.findViewById(R.id.button_play_mission)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mission, parent, false)
        return MissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: MissionViewHolder, position: Int) {
        val mission = missions[position]
        
        holder.missionName.text = mission.levelName
        
        // Set mission status and icon
        when {
            mission.isCompleted -> {
                holder.missionStatus.text = "Completed"
                holder.missionStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
                holder.missionIcon.setImageResource(android.R.drawable.ic_dialog_info)
                
                // Show grade if available
                if (mission.grade != null) {
                    holder.missionGrade.text = "Grade: ${mission.grade}"
                    holder.missionGrade.visibility = View.VISIBLE
                    
                    // Set grade color based on performance
                    val gradeColor = when (mission.grade) {
                        "A" -> android.graphics.Color.rgb(0, 200, 0) // Green
                        "B" -> android.graphics.Color.rgb(0, 150, 255) // Blue
                        "C" -> android.graphics.Color.rgb(255, 200, 0) // Yellow
                        "D" -> android.graphics.Color.rgb(255, 150, 0) // Orange
                        "F" -> android.graphics.Color.rgb(255, 0, 0) // Red
                        else -> android.graphics.Color.GRAY
                    }
                    holder.missionGrade.setTextColor(gradeColor)
                } else {
                    holder.missionGrade.visibility = View.GONE
                }
            }
            mission.isAvailable -> {
                holder.missionStatus.text = "Available"
                holder.missionStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
                holder.missionIcon.setImageResource(android.R.drawable.ic_menu_compass)
                holder.missionGrade.visibility = View.GONE
            }
            else -> {
                holder.missionStatus.text = "Locked"
                holder.missionStatus.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
                holder.missionIcon.setImageResource(android.R.drawable.ic_lock_lock)
                holder.missionGrade.visibility = View.GONE
            }
        }
        
        // Set play button state
        if (mission.isAvailable) {
            holder.playButton.isEnabled = true
            holder.playButton.alpha = 1.0f
            holder.playButton.text = if (mission.isCompleted) "Replay" else "Play"
        } else {
            holder.playButton.isEnabled = false
            holder.playButton.alpha = 0.5f
            holder.playButton.text = "Locked"
        }
        
        // Set click listener
        holder.playButton.setOnClickListener {
            if (mission.isAvailable) {
                onMissionClick(mission.levelId)
            }
        }
    }

    override fun getItemCount(): Int = missions.size
} 