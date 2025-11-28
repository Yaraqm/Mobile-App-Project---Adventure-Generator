package com.example.mobileproject.models

data class Reward(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val pointsRequired: Int = 0,
    val imageUrl: String = "",   // optional can be blank
    val gradientStart: String = "#5B8DEF", // optional custom colors per reward
    val gradientEnd: String = "#3A6FE0",
    val active: Boolean = true
)
