package com.example.mobileproject.ui.rewards

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.R
import com.example.mobileproject.databinding.FragmentRewardsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.Locale

class RewardsFragment : Fragment() {

    private var _binding: FragmentRewardsBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var challengeAdapter: ChallengeAdapter
    private lateinit var badgeAdapter: BadgeAdapter

    private var userListener: ListenerRegistration? = null
    private var challengesListener: ListenerRegistration? = null
    private var photosListener: ListenerRegistration? = null
    private var reviewsListener: ListenerRegistration? = null // Added for future use
    private var spotsVisitedListener: ListenerRegistration? = null // Added for future use


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        loadBadges()

        binding.btnViewStore.setOnClickListener {
            findNavController().navigate(R.id.action_rewardsFragment_to_rewardsStoreFragment)
        }
    }

    override fun onStart() {
        super.onStart()
        loadAllData()
    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
        challengesListener?.remove()
        photosListener?.remove()
        reviewsListener?.remove()
        spotsVisitedListener?.remove()
    }

    private fun setupRecyclerViews() {
        challengeAdapter = ChallengeAdapter()
        binding.recyclerChallenges.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChallenges.adapter = challengeAdapter

        badgeAdapter = BadgeAdapter()
        binding.recyclerBadges.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerBadges.adapter = badgeAdapter
    }

    private fun loadAllData() {
        if (userId == null) {
            Log.e("RewardsFragment", "User not logged in.")
            return
        }
        loadUserData(userId)
        loadChallenges(userId)
    }

    private fun loadUserData(userId: String) {
        userListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RewardsFragment", "Error fetching user data", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val points = snapshot.getLong("points") ?: 0L
                    // Static values from the document
                    val reviewsWritten = snapshot.getLong("reviewsWritten") ?: 0L
                    val spotsVisited = snapshot.getLong("spotsVisited") ?: 0L

                    binding.tvTotalPoints.text = NumberFormat.getNumberInstance(Locale.US).format(points)
                    badgeAdapter.updateUserPoints(points.toInt())

                    // Set static text views
                    binding.tvReviewsWritten.text = reviewsWritten.toString()
                    binding.tvSpotsVisited.text = spotsVisited.toString()
                    updateContributorScore() // Calculate score

                    val level = (points / 1000).toInt() + 1
                    val pointsForCurrentLevel = (level - 1) * 1000
                    val pointsForNextLevel = level * 1000
                    val progressInLevel = points - pointsForCurrentLevel
                    val levelRange = pointsForNextLevel - pointsForCurrentLevel
                    val progressPercentage = if (levelRange > 0) (progressInLevel * 100 / levelRange).toInt() else 0

                    binding.tvCurrentLevel.text = "Level $level"
                    binding.levelProgressBar.progress = progressPercentage
                    binding.tvPointsToNextLevel.text = "${pointsForNextLevel - points} points to Level ${level + 1}"

                    val completions = snapshot.get("photoChallengeCompletions") as? Long ?: 0L
                    listenToPhotoChallengeProgress(userId, completions.toInt())
                }
            }
    }

    private fun updateContributorScore() {
        val photosAdded = binding.tvPhotosAdded.text.toString().toLongOrNull() ?: 0L
        val reviewsWritten = binding.tvReviewsWritten.text.toString().toLongOrNull() ?: 0L
        val spotsVisited = binding.tvSpotsVisited.text.toString().toLongOrNull() ?: 0L

        val contributorScore = photosAdded + reviewsWritten + spotsVisited
        binding.tvContributorScore.text = contributorScore.toString()
    }


    private fun loadChallenges(userId: String) {
        challengesListener = firestore.collection("challenges")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RewardsFragment", "Error fetching challenges", error)
                    return@addSnapshotListener
                }
                val challenges = snapshot?.documents?.mapNotNull {
                    it.toObject(Challenge::class.java)?.copy(id = it.id)
                } ?: emptyList()
                challengeAdapter.submitList(challenges)
            }
    }

    private fun listenToPhotoChallengeProgress(userId: String, completions: Int) {
        photosListener?.remove()
        photosListener = firestore.collection("users").document(userId).collection("photos")
            .addSnapshotListener { photosSnapshot, error ->
                if (error != null) {
                    Log.w("RewardsFragment", "Listen failed for photos.", error)
                    return@addSnapshotListener
                }

                val photoCount = photosSnapshot?.size() ?: 0
                challengeAdapter.updatePhotoChallengeProgress("photos", photoCount, completions)
                binding.tvPhotosAdded.text = photoCount.toString()

                updateContributorScore()

                val photoChallenge = challengeAdapter.getChallengeByType("photos") ?: return@addSnapshotListener

                val newCompletions = photoCount / photoChallenge.goal

                if (newCompletions > completions) {
                    val completionsToAward = newCompletions - completions
                    awardPointsForPhotoChallenge(userId, photoChallenge, completionsToAward, newCompletions)
                }
            }
    }


    private fun awardPointsForPhotoChallenge(userId: String, challenge: Challenge, completionsToAward: Int, totalCompletions: Int) {
        val userRef = firestore.collection("users").document(userId)
        val pointsToAward = challenge.points * completionsToAward

        firestore.runTransaction { transaction ->
            transaction.update(userRef, "points", FieldValue.increment(pointsToAward.toLong()))
            transaction.update(userRef, "photoChallengeCompletions", totalCompletions)
            null
        }.addOnSuccessListener {
            Log.d("RewardsFragment", "Awarded $pointsToAward points for $completionsToAward photo challenge completions.")
        }.addOnFailureListener {
            Log.e("RewardsFragment", "Failed to award points for photo challenge", it)
        }
    }

    private fun loadBadges() {
        val badges = listOf(
            Badge("The Extrovert", 500, R.drawable.ic_extrovert),
            Badge("The Tourist", 1500, R.drawable.ic_tourist),
            Badge("The Explorer", 3000, R.drawable.ic_explorer),
            Badge("Thrill-Seeker", 5000, R.drawable.ic_thrill_seeker),
            Badge("Daredevil", 7500, R.drawable.ic_daredevil),
            Badge("Adventurer", 10000, R.drawable.ic_adventurer)
        )
        badgeAdapter.submitList(badges)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
