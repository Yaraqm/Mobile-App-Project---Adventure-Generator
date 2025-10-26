package com.example.mobileproject.ui.rewards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.R // Import R
import com.example.mobileproject.databinding.ItemChallengeBinding

data class Challenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val goal: Int = 0,
    val fieldType: String = "",
    val icon: String = "", // Add this for the icon name (e.g., "ic_new_spot")
    var progress: Int = 0 // Add this to hold current progress
)

class ChallengeAdapter : RecyclerView.Adapter<ChallengeAdapter.ChallengeViewHolder>() {

    private val items = mutableListOf<Challenge>()

    fun submitList(list: List<Challenge>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ChallengeViewHolder(val binding: ItemChallengeBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
        val binding = ItemChallengeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChallengeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvChallengeTitle.text = item.title
        holder.binding.tvChallengeReward.text = "Reward\n+${item.points}"

        // Set progress to a static 0%
        val progressPercentage = 0
        holder.binding.progressChallenge.progress = progressPercentage
        holder.binding.tvChallengeProgressText.text = "$progressPercentage% Complete"

        // Dynamically set icon based on `fieldType` or a dedicated `icon` field
        val iconResId = when (item.fieldType) {
            "spots" -> R.drawable.ic_new_spot // Replace with your actual icons
            "photos" -> R.drawable.ic_share_photo
            "reviews" -> R.drawable.ic_review_place
            else -> R.drawable.ic_challenge_placeholder
        }
        holder.binding.ivChallengeIcon.setImageResource(iconResId)
    }

    override fun getItemCount() = items.size
}
