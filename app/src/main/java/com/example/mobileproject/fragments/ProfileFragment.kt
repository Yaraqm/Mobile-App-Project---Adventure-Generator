package com.example.mobileproject.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mobileproject.LoginActivity
import com.example.mobileproject.R
import com.example.mobileproject.adapters.FavoriteSpotAdapter
import com.example.mobileproject.adapters.PhotoAdapter
import com.example.mobileproject.databinding.FragmentProfileBinding
import com.example.mobileproject.models.FavoriteSpot
import com.example.mobileproject.models.Photo
import com.example.mobileproject.utils.SupabaseHelper
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import android.content.pm.PackageManager
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import io.github.jan.supabase.storage.storage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val photos = mutableListOf<Photo>()
    private lateinit var photoAdapter: PhotoAdapter

    private val favoriteSpots = mutableListOf<FavoriteSpot>()
    private lateinit var favoriteSpotAdapter: FavoriteSpotAdapter

    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        SupabaseHelper.logConnection()

        // RecyclerView setup for Photos
        photoAdapter = PhotoAdapter(photos) { selectedPhoto ->
            val dialog = ImagePreviewDialogFragment.newInstance(selectedPhoto.imageUrl)
            dialog.setOnDeleteClicked { deletePhoto(selectedPhoto) }
            dialog.show(parentFragmentManager, "ImagePreviewDialog")
        }

        // RecyclerView setup for Favorites
        favoriteSpotAdapter = FavoriteSpotAdapter(favoriteSpots)

        // Default to photos tab
        binding.profileRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.profileRecyclerView.adapter = photoAdapter
        binding.btnUploadPhoto.visibility = View.VISIBLE

        loadUserProfile()
        loadUserPhotos()
        loadUserReviewCount()
        loadUserFriendCount()

        setupTabs()

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        binding.friendsStatLayout.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_friendsFragment)
        }

        // ░░░ UPLOAD PHOTO FAB ░░░
        binding.btnUploadPhoto.setOnClickListener {
            if (requireContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 2000)
            } else {
                openImagePicker()
            }
        }
    }

    private fun setupTabs() {
        binding.profileTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // 📸 Pictures tab selected
                        binding.profileRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
                        binding.profileRecyclerView.adapter = photoAdapter
                        loadUserPhotos()
                        binding.btnUploadPhoto.visibility = View.VISIBLE // show FAB
                    }
                    1 -> {
                        // ⭐ Favorites tab selected
                        binding.profileRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                        binding.profileRecyclerView.adapter = favoriteSpotAdapter
                        loadFavoriteSpots()
                        binding.btnUploadPhoto.visibility = View.GONE // hide FAB
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadFavoriteSpots() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc != null && userDoc.exists()) {
                    val favoritedLocationIds = userDoc.get("favorited_locations") as? List<String> ?: listOf()
                    favoriteSpots.clear()

                    if (favoritedLocationIds.isEmpty()) {
                        favoriteSpotAdapter.notifyDataSetChanged()
                        return@addOnSuccessListener
                    }

                    for (locationId in favoritedLocationIds) {
                        db.collection("locations").document(locationId).get()
                            .addOnSuccessListener { locationDoc ->
                                if (locationDoc != null && locationDoc.exists()) {
                                    val name = locationDoc.getString("name")?.trim()?.replace("\n", "") ?: "Unknown Spot"
                                    val city = locationDoc.getString("city")?.trim() ?: "Unknown City"
                                    favoriteSpots.add(FavoriteSpot(locationId, name, city))
                                    favoriteSpotAdapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
            }
    }

    private var friendCountListener: ListenerRegistration? = null

    private fun loadUserFriendCount() {
        val userId = auth.currentUser?.uid ?: return

        // Remove any old listener
        friendCountListener?.remove()

        // Attach Firestore listener
        friendCountListener = db.collection("users")
            .document(userId)
            .collection("friends")
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ProfileFragment", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val count = snapshots?.documents?.size ?: 0
                if (_binding != null) { // ensure view still exists
                    binding.userFriendsCount.text = count.toString()
                }
            }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2000 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openImagePicker()
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    val email = document.getString("email")
                    val photoUrl = document.getString("photoUrl")
                    val points = document.getLong("points") ?: 0
                    val joinedAt = document.getTimestamp("joinedAt")
                    val bio = document.getString("bio") ?: ""
                    val visitedLocations = document.get("visited_locations") as? List<*>
                    val visitedCount = visitedLocations?.size ?: 0

                    binding.userSpotsCount.text = visitedCount.toString()
                    binding.userName.text = name
                    binding.userEmail.text = email
                    binding.userPoints.text = "$points"
                    binding.userBio.text = if (bio.isNotBlank()) bio else "Tap to add a short bio..."

                    joinedAt?.let {
                        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                        binding.joinedAt.text = "Joined: ${sdf.format(it.toDate())}"
                    }

                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(binding.profileImage)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }

        binding.userBio.setOnClickListener { showEditBioDialog() }
    }

    private fun loadUserReviewCount() {
        val userId = auth.currentUser?.uid ?: return

        db.collectionGroup("reviews").whereEqualTo("userId", userId).get()
            .addOnSuccessListener { documents ->
                Log.d("ProfileFragment", "Review count query successful, found ${documents.size()} reviews.")
                binding.userReviewsCount.text = documents.size().toString()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Error getting review count", e)
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Toast.makeText(requireContext(), "Query requires an index. Check Logcat for a link to create it.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                uploadImageToSupabase(imageUri)
            } else {
                Log.e("Supabase", "⚠️ No image URI returned from picker")
            }
        }
    }

    private fun uploadImageToSupabase(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val filePath = "${userId}_${UUID.randomUUID()}.jpg"

        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        SupabaseHelper.uploadFileReturnPath(
            bucket = "user_photos",
            filePath = filePath,
            fileUri = imageUri,
            context = requireContext(),
            onSuccessPath = { savedPath ->
                lifecycleScope.launch {
                    try {
                        val signed = SupabaseHelper.getSignedUrl(
                            bucket = "user_photos",
                            path = savedPath,
                            expiresInSeconds = 3600
                        )

                        val photoData = mapOf(
                            "path" to savedPath,
                            "uploadedAt" to System.currentTimeMillis()
                        )

                        db.collection("users").document(userId)
                            .collection("photos")
                            .add(photoData)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Photo uploaded!", Toast.LENGTH_SHORT).show()
                                photos.add(Photo(signed, System.currentTimeMillis()))
                                photoAdapter.notifyItemInserted(photos.size - 1)
                            }
                            .addOnFailureListener {
                                Toast.makeText(requireContext(), "Failed to save photo data", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to sign URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onFailure = {
                Toast.makeText(requireContext(), "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadUserPhotos() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .collection("photos")
            .get()
            .addOnSuccessListener { result ->
                photos.clear()
                lifecycleScope.launch {
                    try {
                        for (document in result) {
                            val path = document.getString("path")
                            val uploadedAt = document.getLong("uploadedAt") ?: 0L
                            if (!path.isNullOrBlank()) {
                                val signed = SupabaseHelper.getSignedUrl(
                                    bucket = "user_photos",
                                    path = path,
                                    expiresInSeconds = 3600
                                )
                                photos.add(Photo(signed, uploadedAt))
                            } else {
                                val imageUrl = document.getString("imageUrl")
                                if (!imageUrl.isNullOrBlank()) {
                                    photos.add(Photo(imageUrl, uploadedAt))
                                }
                            }
                        }
                        photoAdapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to load photos: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load photos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditBioDialog() {
        val userId = auth.currentUser?.uid ?: return
        val currentBio = binding.userBio.text.toString().takeIf { it != "Tap to add a short bio..." } ?: ""

        val input = android.widget.EditText(requireContext()).apply {
            setText(currentBio)
            hint = "Enter your bio"
            setPadding(40, 40, 40, 40)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Bio")
            .setView(input)
            .setPositiveButton("Save") { dialog, _ ->
                val newBio = input.text.toString().trim()

                db.collection("users").document(userId)
                    .update("bio", newBio)
                    .addOnSuccessListener {
                        binding.userBio.text = if (newBio.isNotEmpty()) newBio else "Tap to add a short bio..."
                        Toast.makeText(requireContext(), "Bio updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Failed to update bio: ${e.message}", Toast.LENGTH_SHORT).show()
                    }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deletePhoto(photo: Photo) {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val fullUrl = photo.imageUrl
                val baseFileName = fullUrl.substringBeforeLast("?").substringAfterLast("/")

                SupabaseHelper.deleteFile("user_photos", baseFileName)

                db.collection("users").document(userId)
                    .collection("photos")
                    .whereEqualTo("path", baseFileName)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(requireContext(), "Photo deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Firestore delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }

                val index = photos.indexOf(photo)
                if (index != -1) {
                    photos.removeAt(index)
                    photoAdapter.notifyItemRemoved(index)
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        friendCountListener?.remove()
        friendCountListener = null
        _binding = null
    }

}
