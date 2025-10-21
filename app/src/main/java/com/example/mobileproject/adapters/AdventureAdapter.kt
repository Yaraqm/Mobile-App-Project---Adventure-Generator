package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.example.mobileproject.models.Adventure

class AdventureAdapter(
    private val onClick: (Adventure) -> Unit
) : ListAdapter<Adventure, AdventureAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Adventure>() {
        override fun areItemsTheSame(old: Adventure, new: Adventure) = old.id == new.id
        override fun areContentsTheSame(old: Adventure, new: Adventure) = old == new
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_adventure, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onClick: (Adventure) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val image: ImageView = itemView.findViewById(R.id.image)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val location: TextView = itemView.findViewById(R.id.location)

        fun bind(item: Adventure) {
            title.text = item.title
            subtitle.text = item.description
            location.text = item.location

            Glide.with(image).load(item.imageUrl).placeholder(R.drawable.ic_placeholder).into(image)

            itemView.setOnClickListener { onClick(item) }
        }
    }
}
