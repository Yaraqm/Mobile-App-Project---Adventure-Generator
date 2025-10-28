package com.example.mobileproject.ui.rewards.store

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.R // Make sure this import is present
import com.example.mobileproject.databinding.ItemRewardStoreBinding
import com.example.mobileproject.ui.rewards.Badge

class RewardsStoreAdapter(
    private val onRedeemClicked: (Badge) -> Unit
) : ListAdapter<Badge, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private var userPoints: Int = 0

    // View types
    private val ITEM_VIEW_TYPE_BADGE = 0
    private val ITEM_VIEW_TYPE_FOOTER = 1

    fun updateUserPoints(points: Int) {
        userPoints = points
        notifyDataSetChanged()
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

    override fun getItemViewType(position: Int): Int {
        return if (position < super.getItemCount()) {
            ITEM_VIEW_TYPE_BADGE
        } else {
            ITEM_VIEW_TYPE_FOOTER
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ITEM_VIEW_TYPE_BADGE) {
            val binding = ItemRewardStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            RewardStoreViewHolder(binding)
        } else { // Footer
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recycler_footer, parent, false)
            FooterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is RewardStoreViewHolder) {
            holder.bind(getItem(position), userPoints, position)
        }
    }

    inner class RewardStoreViewHolder(private val binding: ItemRewardStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(badge: Badge, currentUserPoints: Int, position: Int) {
            binding.tvRewardTitle.text = badge.title
            binding.tvRewardPrice.text = "${badge.pointsRequired} Points"
            binding.imgReward.setImageResource(badge.iconResId)
            val canAfford = currentUserPoints >= badge.pointsRequired

            // Color range to align badges with rewards
            if (canAfford) {
                itemView.alpha = 1.0f
                binding.btnRedeem.isEnabled = true
                val unlockedColors = listOf("#FDEBD0", "#E8DAEF", "#D4E6F1", "#D1F2EB", "#FDEDEC", "#FAD7A0")
                binding.cardReward.setCardBackgroundColor(Color.parseColor(unlockedColors[position % unlockedColors.size]))
            } else {
                itemView.alpha = 0.6f
                binding.btnRedeem.isEnabled = false
                binding.cardReward.setCardBackgroundColor(Color.parseColor("#EAEDED"))
            }

            binding.btnRedeem.setOnClickListener { onRedeemClicked(badge) }
        }
    }

    // ViewHolder for the Footer
    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
