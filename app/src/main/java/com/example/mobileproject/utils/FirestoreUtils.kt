package com.example.mobileproject.utils

import com.example.mobileproject.models.Reward
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirestoreUtils {
    private val db by lazy { FirebaseFirestore.getInstance() }

    fun rewardsFlow() = callbackFlow<List<Reward>> {
        val ref = db.collection("rewards")
            .whereEqualTo("active", true)
            .orderBy("pointsRequired", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { doc ->
                val r = doc.toObject(Reward::class.java)?.copy(id = doc.id)
                r
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    // Handy helper you can call once from RewardsFragment (guarded) to insert demo docs
    suspend fun seedRewardsIfEmpty() {
        val col = db.collection("rewards")
        val existing = col.limit(1).get().await()
        if (!existing.isEmpty) return

        val demo = listOf(
            Reward(
                title = "Free Coffee",
                description = "Redeem for a small coffee at partner cafés.",
                pointsRequired = 100,
                gradientStart = "#FF8A65",
                gradientEnd = "#FF7043"
            ),
            Reward(
                title = "Adventure Sticker Pack",
                description = "Vinyl stickers for your water bottle.",
                pointsRequired = 150,
                gradientStart = "#66BB6A",
                gradientEnd = "#43A047"
            ),
            Reward(
                title = "10% Off Gear",
                description = "Save on your next adventure gear purchase.",
                pointsRequired = 300,
                gradientStart = "#5B8DEF",
                gradientEnd = "#3A6FE0"
            )
        )
        demo.forEach { col.add(it).await() }
    }
}
