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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        loadBadges() // Load static badges immediately

        binding.btnRedeem.setOnClickListener {
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
        loadChallenges()
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
                    val photosAdded = snapshot.getLong("photosAdded") ?: 0L
                    val reviewsWritten = snapshot.getLong("reviewsWritten") ?: 0L
                    val newSpotsAdded = snapshot.getLong("newSpotsAdded") ?: 0L

                    binding.tvTotalPoints.text = NumberFormat.getNumberInstance(Locale.US).format(points)
                    badgeAdapter.updateUserPoints(points.toInt())

                    val contributorScore = photosAdded * 10 + reviewsWritten * 5 + newSpotsAdded * 20
                    binding.tvContributorScore.text = contributorScore.toString()
                    binding.tvPhotosAdded.text = photosAdded.toString()
                    binding.tvReviewsWritten.text = reviewsWritten.toString()
                    binding.tvNewSpotsAdded.text = newSpotsAdded.toString()

                    val level = (points / 1000).toInt() + 1
                    val pointsForCurrentLevel = (level - 1) * 1000
                    val pointsForNextLevel = level * 1000
                    val progressInLevel = points - pointsForCurrentLevel
                    val levelRange = pointsForNextLevel - pointsForCurrentLevel
                    val progressPercentage = if (levelRange > 0) (progressInLevel * 100 / levelRange).toInt() else 0

                    binding.tvCurrentLevel.text = "Level $level"
                    binding.levelProgressBar.progress = progressPercentage
                    binding.tvPointsToNextLevel.text = "${pointsForNextLevel - points} points to Level ${level + 1}"

                    // No longer need to pass userStats to loadChallenges
                }
            }
    }

    private fun loadChallenges() {
        challengesListener = firestore.collection("challenges")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RewardsFragment", "Error fetching challenges", error)
                    return@addSnapshotListener
                }
                val challenges = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Challenge::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                challengeAdapter.submitList(challenges)
            }
    }

    private fun loadBadges() {
        // Static list of badges as requested
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