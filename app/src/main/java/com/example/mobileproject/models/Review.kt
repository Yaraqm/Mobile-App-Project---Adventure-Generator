package com.example.mobileproject.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a single review left by a user for a location.
 */
data class Review(
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val rating: Float = 0.0f,
    val text: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)
