package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.example.mobileproject.models.Adventure

class AdventureAdapter(private val adventures: List<Adventure>) :
    RecyclerView.Adapter<AdventureAdapter.AdventureViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdventureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_adventure, parent, false)
        return AdventureViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdventureViewHolder, position: Int) {
        val adventure = adventures[position]
        holder.title.text = adventure.title
        holder.description.text = adventure.description
        holder.location.text = adventure.location

        // Load image with Glide if available
        Glide.with(holder.itemView.context)
            .load(adventure.imageUrl)
            .placeholder(R.drawable.ic_placeholder) // add a placeholder image to drawable
            .into(holder.image)
    }

    override fun getItemCount(): Int = adventures.size

    class AdventureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.adventureTitle)
        val description: TextView = itemView.findViewById(R.id.adventureDescription)
        val location: TextView = itemView.findViewById(R.id.adventureLocation)
        val image: ImageView = itemView.findViewById(R.id.adventureImage)
    }
}
