package com.example.mobileproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobileproject.R
import com.example.mobileproject.models.Review
import de.hdodenhof.circleimageview.CircleImageView

class ReviewAdapter(private val reviews: List<Review>) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reviewerImage: CircleImageView = view.findViewById(R.id.reviewer_image)
        val reviewerName: TextView = view.findViewById(R.id.reviewer_name)
        val ratingBar: RatingBar = view.findViewById(R.id.review_rating_bar)
        val reviewText: TextView = view.findViewById(R.id.review_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        holder.reviewerName.text = review.userName
        holder.reviewText.text = review.text
        holder.ratingBar.rating = review.rating

        // Use Glide to load the user's profile image
        if (review.userPhotoUrl != null) {
            Glide.with(holder.itemView.context)
                .load(review.userPhotoUrl)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)  
                .into(holder.reviewerImage)
        } else {
            // Set a default image if the user has no photo
            holder.reviewerImage.setImageResource(R.drawable.ic_default_profile)
        }
    }

    override fun getItemCount() = reviews.size
}
