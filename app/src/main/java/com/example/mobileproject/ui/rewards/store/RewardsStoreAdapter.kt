package com.example.mobileproject.ui.rewards.store

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.databinding.ItemRewardStoreBinding
import com.example.mobileproject.ui.rewards.Badge

class RewardsStoreAdapter(
    private val onRedeemClicked: (Badge) -> Unit
) : ListAdapter<Badge, RewardsStoreAdapter.RewardStoreViewHolder>(DIFF_CALLBACK) {

    private var userPoints: Int = 0

    fun updateUserPoints(points: Int) {
        userPoints = points
        notifyDataSetChanged() // Redraw the entire list to update button states
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Badge>() {
            override fun areItemsTheSame(oldItem: Badge, newItem: Badge): Boolean {
                return oldItem.title == newItem.title
            }

            override fun areContentsTheSame(oldItem: Badge, newItem: Badge): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardStoreViewHolder {
        val binding = ItemRewardStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RewardStoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RewardStoreViewHolder, position: Int) {
        // Pass the user points to the bind function
        holder.bind(getItem(position), userPoints)
    }

    inner class RewardStoreViewHolder(private val binding: ItemRewardStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // The bind function now accepts the current user's points
        fun bind(badge: Badge, currentUserPoints: Int) {
            binding.tvRewardTitle.text = badge.title
            binding.tvRewardPrice.text = "${badge.pointsRequired} Points"
            binding.imgReward.setImageResource(badge.iconResId)

            val canAfford = currentUserPoints >= badge.pointsRequired

            // Enable/disable the button and change opacity based on affordability
            binding.btnRedeem.isEnabled = canAfford
            itemView.alpha = if (canAfford) 1.0f else 0.6f

            binding.btnRedeem.setOnClickListener {
                onRedeemClicked(badge)
            }
        }
    }
}
