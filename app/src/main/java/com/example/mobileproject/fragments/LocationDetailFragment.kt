package com.example.mobileproject.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager // <<< --- NEW IMPORT
import com.example.mobileproject.R
import com.example.mobileproject.adapters.ReviewAdapter // <<< --- NEW IMPORT
import com.example.mobileproject.databinding.FragmentLocationDetailBinding
import com.example.mobileproject.models.Review
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query // <<< --- NEW IMPORT

data class LocationDetails(
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val map_point: GeoPoint? = null
)

class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!
    private val args: LocationDetailFragmentArgs by navArgs()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var currentLocationId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentLocationId = args.locationId
        Log.d("LocationDetailFragment", "Received location ID: $currentLocationId")

        fetchLocationDetails(currentLocationId)
        checkIfFavorited()
        checkIfVisited()
        setupReviewsRecyclerView() // <<< --- NEW: SET UP THE REVIEWS LIST

        binding.btnAddFavorite.setOnClickListener {
            addOrRemoveFavorite()
        }
        binding.btnVisited.setOnClickListener {
            addOrRemoveVisited()
        }
        binding.btnLeaveReview.setOnClickListener {
            showLeaveReviewDialog()
        }
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // *** NEW: FUNCTION TO SET UP THE RECYCLERVIEW AND FETCH REVIEWS ***
    private fun setupReviewsRecyclerView() {
        binding.reviewsRecyclerView.layoutManager = LinearLayoutManager(context)

        firestore.collection("locations").document(currentLocationId)
            .collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING) // Show newest reviews first
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("LocationDetailFragment", "No reviews found.")
                    // Optionally, you could show a "No reviews yet" message here
                } else {
                    val reviews = documents.toObjects(Review::class.java)
                    val adapter = ReviewAdapter(reviews)
                    binding.reviewsRecyclerView.adapter = adapter
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LocationDetailFragment", "Error getting reviews: ", exception)
                Toast.makeText(context, "Failed to load reviews.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLeaveReviewDialog() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "You must be logged in to leave a review.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_leave_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rating_bar)
        val editTextReview = dialogView.findViewById<EditText>(R.id.edit_text_review)

        AlertDialog.Builder(requireContext())
            .setTitle("Leave a Review")
            .setView(dialogView)
            .setPositiveButton("Submit") { dialog, _ ->
                val rating = ratingBar.rating
                val reviewText = editTextReview.text.toString().trim()

                if (reviewText.isNotEmpty() && rating > 0) {
                    submitReview(user, rating, reviewText)
                } else {
                    Toast.makeText(context, "Please provide a rating and a comment.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun submitReview(user: FirebaseUser, rating: Float, reviewText: String) {
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc == null || !userDoc.exists()) {
                    Toast.makeText(context, "Could not find user data.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val userName = userDoc.getString("name") ?: "Anonymous"
                val userPhotoUrl = userDoc.getString("photoUrl")

                val review = Review(
                    userId = user.uid,
                    userName = userName,
                    userPhotoUrl = userPhotoUrl,
                    rating = rating,
                    text = reviewText
                )

                firestore.collection("locations").document(currentLocationId)
                    .collection("reviews")
                    .add(review)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Review submitted successfully!", Toast.LENGTH_SHORT).show()
                        // *** NEW: Refresh the reviews list after submitting a new one ***
                        setupReviewsRecyclerView()
                    }
                    .addOnFailureListener { e ->
                        Log.e("LocationDetailFragment", "Error submitting review", e)
                        Toast.makeText(context, "Failed to submit review.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("LocationDetailFragment", "Error fetching user details for review", e)
                Toast.makeText(context, "Could not fetch user details.", Toast.LENGTH_SHORT).show()
            }
    }

    // --- ALL THE OTHER FUNCTIONS (fetchLocationDetails, addOrRemoveFavorite, etc.) REMAIN UNCHANGED ---

    private fun fetchLocationDetails(locationId: String) {
        firestore.collection("locations").document(locationId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val location = document.toObject(LocationDetails::class.java)
                    if (location != null) {
                        updateUi(location)
                    } else {
                        Log.e("LocationDetailFragment", "Failed to parse document.")
                    }
                } else {
                    Log.e("LocationDetailFragment", "No such document with ID: $locationId")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("LocationDetailFragment", "Error getting location details", exception)
            }
    }

    private fun checkIfFavorited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val favoritedLocations = document.get("favorited_locations") as? List<*>
                    val isFavorited = favoritedLocations?.contains(currentLocationId) == true
                    updateFavoriteButtonUI(isFavorited)
                } else {
                    updateFavoriteButtonUI(false)
                }
            }
    }

    private fun addOrRemoveFavorite() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to manage favorites", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyFavorited = (binding.btnAddFavorite as MaterialButton).text == "Favorited"
        val firestoreUpdate = if (isCurrentlyFavorited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        val toastMessage = if (isCurrentlyFavorited) "Removed from favorites" else "Added to favorites!"
        val newFavoriteState = !isCurrentlyFavorited

        userDocRef.update("favorited_locations", firestoreUpdate)
            .addOnSuccessListener {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                updateFavoriteButtonUI(newFavoriteState)
            }
            .addOnFailureListener { Toast.makeText(context, "Error updating favorites", Toast.LENGTH_SHORT).show() }
    }

    private fun updateFavoriteButtonUI(isFavorited: Boolean) {
        val button = binding.btnAddFavorite as MaterialButton
        if (isFavorited) {
            button.text = "Favorited"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_filled)
        } else {
            button.text = "Add to Favorites"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border)
        }
    }

    private fun checkIfVisited() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val visitedLocations = document.get("visited_locations") as? List<*>
                    val isVisited = visitedLocations?.contains(currentLocationId) == true
                    updateVisitedButtonUI(isVisited)
                } else {
                    updateVisitedButtonUI(false)
                }
            }
    }

    private fun addOrRemoveVisited() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "You must be logged in to log visits", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(userId)
        val isCurrentlyVisited = (binding.btnVisited as MaterialButton).text == "Visited"
        val firestoreUpdate = if (isCurrentlyVisited) FieldValue.arrayRemove(currentLocationId) else FieldValue.arrayUnion(currentLocationId)
        val toastMessage = if (isCurrentlyVisited) "Removed from visited log" else "Added to visited!"
        val newVisitedState = !isCurrentlyVisited

        userDocRef.update("visited_locations", firestoreUpdate)
            .addOnSuccessListener {
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                updateVisitedButtonUI(newVisitedState)
            }
            .addOnFailureListener { Toast.makeText(context, "Error updating visited log", Toast.LENGTH_SHORT).show() }
    }

    private fun updateVisitedButtonUI(isVisited: Boolean) {
        val button = binding.btnVisited as MaterialButton
        if (isVisited) {
            button.text = "Visited"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
        } else {
            button.text = "I Visited!"
            button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_box_outline_blank)
        }
    }

    private fun updateUi(location: LocationDetails) {
        binding.textLocationName.text = location.name
        binding.textLocationCategory.text = location.category
        binding.textLocationDescription.text = location.description
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
