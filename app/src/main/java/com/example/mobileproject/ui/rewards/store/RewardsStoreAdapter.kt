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
        holder.bind(getItem(position))
    }

    inner class RewardStoreViewHolder(private val binding: ItemRewardStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(badge: Badge) {
            binding.tvRewardTitle.text = badge.title
            binding.tvRewardPrice.text = "${badge.pointsRequired} Points"
            binding.imgReward.setImageResource(badge.iconResId)

            binding.btnRedeem.setOnClickListener {
                onRedeemClicked(badge)
            }
        }
    }
}