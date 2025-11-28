package com.example.mobileproject.ui.rewards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.R
import com.example.mobileproject.databinding.ItemChallengeBinding

data class Challenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val points: Int = 0,
    val goal: Int = 0,
    val fieldType: String = "",
    val icon: String = "",
    var progress: Int = 0,
    var completions: Int = 0
)

class ChallengeAdapter : RecyclerView.Adapter<ChallengeAdapter.ChallengeViewHolder>() {

    private val items = mutableListOf<Challenge>()

    fun submitList(list: List<Challenge>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getChallengeByType(type: String): Challenge? {
        return items.find { it.fieldType == type }
    }

    fun updateChallengeProgress(fieldType: String, currentProgress: Int, completions: Int) {
        val challengeIndex = items.indexOfFirst { it.fieldType == fieldType }
        if (challengeIndex != -1) {
            val challenge = items[challengeIndex]
            challenge.progress = currentProgress
            challenge.completions = completions
            notifyItemChanged(challengeIndex)
        }
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

        var displayProgress = item.progress
        var displayGoal = item.goal

        if (item.goal > 0) {
            displayGoal = item.goal * (item.completions + 1)
        }

        val progressPercentage = if (displayGoal > 0) (displayProgress * 100 / displayGoal).coerceAtMost(100) else 0
        holder.binding.progressChallenge.progress = progressPercentage

        val unit = when(item.fieldType) {
            "photos" -> "Photos"
            "spots" -> "Spots"
            "reviews" -> "Reviews"
            else -> ""
        }

        val progressText = if (unit.isNotEmpty()) "$displayProgress/$displayGoal $unit" else "$displayProgress/$displayGoal"
        holder.binding.tvChallengeProgressText.text = progressText

        val iconResId = when (item.fieldType) {
            "spots" -> R.drawable.ic_new_spot
            "photos" -> R.drawable.ic_share_photo
            "reviews" -> R.drawable.ic_review_place
            else -> R.drawable.ic_challenge_placeholder
        }
        holder.binding.ivChallengeIcon.setImageResource(iconResId)
    }

    override fun getItemCount() = items.size
}
