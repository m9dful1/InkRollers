package com.spiritwisestudios.inkrollers.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.spiritwisestudios.inkrollers.R

data class FriendDisplay(
    val uid: String,
    val name: String,
    val friendCode: String,
    val winCount: Int,
    val lossCount: Int,
    val isOnline: Boolean
)

class FriendAdapter(
    private val friends: List<FriendDisplay>,
    private val onRemove: (FriendDisplay) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(friends[position], onRemove)
    }

    override fun getItemCount(): Int = friends.size

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.text_friend_name)
        private val code: TextView = itemView.findViewById(R.id.text_friend_code)
        private val winLoss: TextView = itemView.findViewById(R.id.text_friend_win_loss)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btn_remove_friend)
        private val onlineStatusIndicator: ImageView = itemView.findViewById(R.id.image_online_status)

        fun bind(friend: FriendDisplay, onRemove: (FriendDisplay) -> Unit) {
            name.text = friend.name
            code.text = friend.friendCode
            winLoss.text = "${friend.winCount} / ${friend.lossCount}"
            btnRemove.setOnClickListener { onRemove(friend) }

            if (friend.isOnline) {
                onlineStatusIndicator.visibility = View.VISIBLE
            } else {
                onlineStatusIndicator.visibility = View.GONE
            }
        }
    }
} 