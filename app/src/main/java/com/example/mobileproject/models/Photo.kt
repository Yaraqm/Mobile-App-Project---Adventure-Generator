package com.example.mobileproject.models

data class Photo(
    val imageUrl: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)
