package com.example.mobileproject.models

enum class FriendshipStatus {
    FRIENDS,
    PENDING_INCOMING,
    PENDING_OUTGOING,
    NOT_FRIENDS
}

data class Friend(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    var status: FriendshipStatus = FriendshipStatus.NOT_FRIENDS
)
