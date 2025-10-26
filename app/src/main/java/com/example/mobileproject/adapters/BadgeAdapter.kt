package com.example.mobileproject.ui.rewards

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.databinding.ItemBadgeBinding

data class Badge(
    val title: String = "",
    val pointsRequired: Int = 0,
    @DrawableRes val iconResId: Int = 0 // Use a drawable resource ID for the icon
)

class BadgeAdapter : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    private val items = mutableListOf<Badge>()
    private var userPoints: Int = 0

    fun submitList(list: List<Badge>) {
        items.clear()
        items.addAll(list.sortedBy { it.pointsRequired }) // Sort badges by points required
        notifyDataSetChanged()
    }

    fun updateUserPoints(points: Int) {
        userPoints = points
        notifyDataSetChanged()
    }

    inner class BadgeViewHolder(val binding: ItemBadgeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val binding = ItemBadgeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BadgeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = items[position]
        val context = holder.itemView.context
        val isUnlocked = userPoints >= badge.pointsRequired

        holder.binding.tvBadgeTitle.text = badge.title
        holder.binding.imgBadgeIcon.setImageResource(badge.iconResId)

        if (isUnlocked) {
            // Full color and vibrant background for unlocked badges
            holder.binding.badgeContainer.alpha = 1.0f
            val unlockedColors = listOf("#FDEBD0", "#E8DAEF", "#D4E6F1", "#D1F2EB", "#FDEDEC", "#FAD7A0")
            holder.binding.cardBadge.setCardBackgroundColor(Color.parseColor(unlockedColors[position % unlockedColors.size]))
        } else {
            // Slightly transparent for locked badges
            holder.binding.badgeContainer.alpha = 0.5f
            holder.binding.cardBadge.setCardBackgroundColor(Color.parseColor("#EAEDED"))
        }

        holder.itemView.setOnClickListener {
            val message = if (isUnlocked) {
                "You've unlocked: ${badge.title}!"
            } else {
                "Locked: Earn ${badge.pointsRequired - userPoints} more points to unlock '${badge.title}'."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = items.size
}
