package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileproject.R
import com.example.mobileproject.models.FavoriteSpot

class FavoriteSpotAdapter(private val favoriteSpots: List<FavoriteSpot>) :
    RecyclerView.Adapter<FavoriteSpotAdapter.FavoriteSpotViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteSpotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_spot, parent, false)
        return FavoriteSpotViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteSpotViewHolder, position: Int) {
        val spot = favoriteSpots[position]
        holder.bind(spot)
    }

    override fun getItemCount() = favoriteSpots.size

    class FavoriteSpotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.fav_spot_name)
        private val cityTextView: TextView = itemView.findViewById(R.id.fav_spot_city)

        fun bind(spot: FavoriteSpot) {
            nameTextView.text = spot.name
            cityTextView.text = spot.city
        }
    }
}
