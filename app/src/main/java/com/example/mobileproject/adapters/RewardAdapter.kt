package com.example.mobileproject.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.databinding.ItemRewardBinding
import com.example.mobileproject.models.Reward

class RewardAdapter(
    private val onClick: (Reward) -> Unit
) : ListAdapter<Reward, RewardAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<Reward>() {
        override fun areItemsTheSame(oldItem: Reward, newItem: Reward) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Reward, newItem: Reward) = oldItem == newItem
    }

    inner class VH(val binding: ItemRewardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Reward) = with(binding) {
            rewardTitle.text = item.title
            rewardDesc.text = item.description
            rewardPoints.text = item.pointsRequired.toString()

            // dynamic gradient per item
            val gd = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(item.gradientStart.toColorInt(), item.gradientEnd.toColorInt())
            )
            gd.cornerRadius = root.resources.getDimensionPixelSize(com.example.mobileproject.R.dimen.card_radius).toFloat()
            cardBackground.background = gd

            root.setOnClickListener { onClick(item) }

            // playful pop-in
            root.scaleX = 0.96f
            root.scaleY = 0.96f
            root.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRewardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
