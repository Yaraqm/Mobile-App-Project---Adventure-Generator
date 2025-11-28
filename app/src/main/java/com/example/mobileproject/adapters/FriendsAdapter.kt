package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.example.mobileproject.models.Friend
import com.example.mobileproject.models.FriendshipStatus

class FriendsAdapter(
    private var users: List<Friend>,
    private val onAddFriend: (Friend) -> Unit,
    private val onAcceptRequest: (Friend) -> Unit,
    private val onRejectRequest: (Friend) -> Unit,
    private val onRemoveFriend: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, onAddFriend, onAcceptRequest, onRejectRequest, onRemoveFriend)
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<Friend>) {
        this.users = newUsers.distinctBy { it.uid }
        notifyDataSetChanged()
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.friend_avatar)
        private val name: TextView = itemView.findViewById(R.id.friend_name)
        private val email: TextView = itemView.findViewById(R.id.friend_email)
        private val requestButtons: LinearLayout = itemView.findViewById(R.id.request_buttons)
        private val btnAddFriend: Button = itemView.findViewById(R.id.btn_add_friend)
        private val btnAccept: Button = itemView.findViewById(R.id.btn_accept)
        private val btnReject: Button = itemView.findViewById(R.id.btn_reject)
        private val friendStatusTag: TextView = itemView.findViewById(R.id.friend_status_tag)

        fun bind(
            user: Friend,
            onAddFriend: (Friend) -> Unit,
            onAcceptRequest: (Friend) -> Unit,
            onRejectRequest: (Friend) -> Unit,
            onRemoveFriend: (Friend) -> Unit
        ) {
            name.text = user.name
            email.text = user.email
            Glide.with(itemView.context)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .into(avatar)

            // 🔁 FULL RESET for recycled views
            requestButtons.visibility = View.GONE
            btnAddFriend.visibility = View.GONE
            friendStatusTag.visibility = View.GONE

            itemView.setOnClickListener(null)       
            itemView.setOnLongClickListener(null)

            btnAddFriend.isEnabled = true    
            btnAddFriend.text = "Add"
            btnAddFriend.setBackgroundResource(R.drawable.bg_friend_add)
            btnAddFriend.setTextColor(
                itemView.resources.getColor(android.R.color.white, null)
            )

            when (user.status) {
                FriendshipStatus.FRIENDS -> {
                    friendStatusTag.visibility = View.VISIBLE
                    friendStatusTag.text = "Friend"
                    friendStatusTag.setBackgroundResource(R.drawable.bg_friend_tag_solid)

                    itemView.setOnLongClickListener {
                        // Simple UX: hold to remove a friend
                        onRemoveFriend(user)
                        Toast.makeText(
                            itemView.context,
                            "Friend removed",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                }
                FriendshipStatus.PENDING_INCOMING -> {
                    requestButtons.visibility = View.VISIBLE
                    btnAccept.setOnClickListener {
                        onAcceptRequest(user)
                        Toast.makeText(
                            itemView.context,
                            "Friend request accepted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    btnReject.setOnClickListener {
                        onRejectRequest(user)
                        Toast.makeText(
                            itemView.context,
                            "Friend request rejected",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                FriendshipStatus.PENDING_OUTGOING -> {
                    friendStatusTag.visibility = View.VISIBLE
                    friendStatusTag.text = "Requested"
                    friendStatusTag.setBackgroundResource(R.drawable.bg_friend_requested)
                }
                FriendshipStatus.NOT_FRIENDS -> {
                    btnAddFriend.visibility = View.VISIBLE
                    btnAddFriend.setOnClickListener {
                        onAddFriend(user)
                        // temporary UI feedback; Firestore listener will update the real state
                        btnAddFriend.isEnabled = false
                        btnAddFriend.text = "Requested"
                        btnAddFriend.setBackgroundResource(R.drawable.bg_friend_requested)
                        Toast.makeText(
                            itemView.context,
                            "Friend request sent",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
