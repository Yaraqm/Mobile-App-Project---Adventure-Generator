package com.example.mobileproject.models

data class Photo(
    val storagePath: String = "",          // Supabase path, e.g. userId_uuid.jpg / .mp4
    val mediaUrl: String = "",             // Signed URL or direct URL
    val type: String = "photo",            // "photo" or "video"
    val uploadedAt: Long = System.currentTimeMillis()
)
