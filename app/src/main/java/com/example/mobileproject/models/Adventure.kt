package com.example.mobileproject.models

data class Adventure(
    var id: String? = null,
    var title: String? = null,
    var description: String? = null,
    var location: String? = null,
    var imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val difficulty: String? = null,
    val likes: Int? = null
)
