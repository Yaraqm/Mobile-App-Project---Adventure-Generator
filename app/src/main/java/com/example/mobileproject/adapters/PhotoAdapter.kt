package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.example.mobileproject.models.Photo

class PhotoAdapter(
    private val photos: List<Photo>,
    private val onAddClicked: () -> Unit,
    private val onMediaClicked: (Photo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ADD = 0
        private const val VIEW_TYPE_MEDIA = 1
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener { onAddClicked() }
        }
    }

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imagePhoto)
        val iconVideo: ImageView = view.findViewById(R.id.iconVideo)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_ADD else VIEW_TYPE_MEDIA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ADD -> {
                val view = inflater.inflate(R.layout.item_add_tile, parent, false)
                AddViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_photo, parent, false)
                MediaViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ADD) {
            // Nothing else to bind; click is set in ViewHolder init
            return
        }

        val photo = photos[position - 1]  // because 0 is the add tile
        val mediaHolder = holder as MediaViewHolder

        // Thumbnail (photo or video frame)
        Glide.with(mediaHolder.itemView.context)
            .load(photo.mediaUrl)
            .centerCrop()
            .into(mediaHolder.imageView)


        // Show play icon only for videos
        if (photo.type == "video") {
            mediaHolder.iconVideo.visibility = View.VISIBLE
        } else {
            mediaHolder.iconVideo.visibility = View.GONE
        }

        mediaHolder.imageView.setOnClickListener {
            onMediaClicked(photo)
        }
    }

    override fun getItemCount(): Int = photos.size + 1 // +1 for the add tile
}
