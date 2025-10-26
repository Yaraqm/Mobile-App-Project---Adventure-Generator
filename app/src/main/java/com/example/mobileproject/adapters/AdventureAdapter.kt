package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.databinding.ItemAdventureBinding
import com.example.mobileproject.databinding.ItemAdventureDayBinding
import com.example.mobileproject.models.Adventure

// The sealed class is correct
sealed class AdventureListItem {
    data class SingleLocation(val adventure: Adventure) : AdventureListItem()
    data class AdventureDay(val food: Adventure, val activity: Adventure) : AdventureListItem()
}

class AdventureAdapter(
    private val onItemClicked: (Adventure) -> Unit
) : ListAdapter<AdventureListItem, RecyclerView.ViewHolder>(AdventureDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SINGLE = 1
        private const val VIEW_TYPE_DAY = 2
    }

    // ViewHolder for the combined "Adventure Day"
    inner class AdventureDayViewHolder(
        private val binding: ItemAdventureDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun bindNestedAdventure(container: ViewGroup, adventure: Adventure) {
            val inflater = LayoutInflater.from(container.context)
            val itemBinding = ItemAdventureBinding.inflate(inflater, container, true)

            // --- FIX #1: Add back the missing text fields here ---
            itemBinding.adventureName.text = adventure.name
            itemBinding.adventureCategory.text = adventure.category
            itemBinding.adventureDescription.text = adventure.description
            itemBinding.adventureCity.text = adventure.city

            itemBinding.root.setOnClickListener { onItemClicked(adventure) }
        }

        fun bind(item: AdventureListItem.AdventureDay) {
            binding.foodLocationContainer.removeAllViews()
            binding.activityLocationContainer.removeAllViews()
            bindNestedAdventure(binding.foodLocationContainer, item.food)
            bindNestedAdventure(binding.activityLocationContainer, item.activity)
        }
    }

    // ViewHolder for a standard single location
    inner class SingleLocationViewHolder(
        private val binding: ItemAdventureBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Adventure) {
            // --- FIX #2: Add back the missing text fields here as well ---
            binding.adventureName.text = item.name
            binding.adventureCategory.text = item.category
            binding.adventureDescription.text = item.description
            binding.adventureCity.text = item.city

            binding.root.setOnClickListener { onItemClicked(item) }
        }
    }

    // The rest of the adapter logic is correct
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AdventureListItem.SingleLocation -> VIEW_TYPE_SINGLE
            is AdventureListItem.AdventureDay -> VIEW_TYPE_DAY
            null -> throw IllegalStateException("Null item at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DAY -> {
                val binding = ItemAdventureDayBinding.inflate(inflater, parent, false)
                AdventureDayViewHolder(binding)
            }
            else -> {
                val binding = ItemAdventureBinding.inflate(inflater, parent, false)
                SingleLocationViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AdventureListItem.SingleLocation -> (holder as SingleLocationViewHolder).bind(item.adventure)
            is AdventureListItem.AdventureDay -> (holder as AdventureDayViewHolder).bind(item)
            null -> { /* Do nothing */ }
        }
    }
}

// DiffUtil is correct
class AdventureDiffCallback : DiffUtil.ItemCallback<AdventureListItem>() {
    override fun areItemsTheSame(oldItem: AdventureListItem, newItem: AdventureListItem): Boolean {
        return when {
            oldItem is AdventureListItem.SingleLocation && newItem is AdventureListItem.SingleLocation ->
                oldItem.adventure.id == newItem.adventure.id
            oldItem is AdventureListItem.AdventureDay && newItem is AdventureListItem.AdventureDay ->
                oldItem.food.id == newItem.food.id && oldItem.activity.id == newItem.activity.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: AdventureListItem, newItem: AdventureListItem): Boolean {
        return oldItem == newItem
    }
}




