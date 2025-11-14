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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import java.io.File

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val photos = mutableListOf<Photo>()
    private lateinit var photoAdapter: PhotoAdapter

    private val favoriteSpots = mutableListOf<FavoriteSpot>()
    private lateinit var favoriteSpotAdapter: FavoriteSpotAdapter

    // Requests
    private val PICK_IMAGE_REQUEST = 1001
    private val CAPTURE_IMAGE_REQUEST = 1002
    private val RECORD_VIDEO_REQUEST = 1003

    private val PERMISSION_REQUEST_GALLERY = 2001
    private val PERMISSION_REQUEST_CAMERA_PHOTO = 2002
    private val PERMISSION_REQUEST_CAMERA_VIDEO = 2003

    private var tempPhotoUri: Uri? = null

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

        // RecyclerView setup for Photos (with + tile)
        photoAdapter = PhotoAdapter(
            photos,
            onAddClicked = { showAddMediaDialog() },
            onMediaClicked = { selectedPhoto ->
                val dialog = ImagePreviewDialogFragment.newInstance(
                    selectedPhoto.mediaUrl,
                    selectedPhoto.type
                )
                dialog.setOnDeleteClicked { deletePhoto(selectedPhoto) }
                dialog.show(parentFragmentManager, "MediaPreviewDialog")
            }
        )

        // RecyclerView setup for Favorites
        favoriteSpotAdapter = FavoriteSpotAdapter(favoriteSpots)

        // Default to photos tab
        binding.profileRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.profileRecyclerView.adapter = photoAdapter

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
    }

    /* ░░░ TABS ░░░ */
    private fun setupTabs() {
        binding.profileTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // 📸 Pictures tab selected
                        binding.profileRecyclerView.layoutManager =
                            GridLayoutManager(requireContext(), 3)
                        binding.profileRecyclerView.adapter = photoAdapter
                        loadUserPhotos()
                    }
                    1 -> {
                        // ⭐ Favorites tab selected
                        binding.profileRecyclerView.layoutManager =
                            LinearLayoutManager(requireContext())
                        binding.profileRecyclerView.adapter = favoriteSpotAdapter
                        loadFavoriteSpots()
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /* ░░░ FAVORITES ░░░ */
    private fun loadFavoriteSpots() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc != null && userDoc.exists()) {
                    val favoritedLocationIds =
                        userDoc.get("favorited_locations") as? List<String> ?: listOf()
                    favoriteSpots.clear()

                    if (favoritedLocationIds.isEmpty()) {
                        favoriteSpotAdapter.notifyDataSetChanged()
                        return@addOnSuccessListener
                    }

                    for (locationId in favoritedLocationIds) {
                        db.collection("locations").document(locationId).get()
                            .addOnSuccessListener { locationDoc ->
                                if (locationDoc != null && locationDoc.exists()) {
                                    val name = locationDoc.getString("name")
                                        ?.trim()?.replace("\n", "") ?: "Unknown Spot"
                                    val city =
                                        locationDoc.getString("city")?.trim() ?: "Unknown City"
                                    favoriteSpots.add(FavoriteSpot(locationId, name, city))
                                    favoriteSpotAdapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
            }
    }

    /* ░░░ FRIEND COUNT ░░░ */
    private var friendCountListener: ListenerRegistration? = null

    private fun loadUserFriendCount() {
        val userId = auth.currentUser?.uid ?: return

        friendCountListener?.remove()

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
                if (_binding != null) {
                    binding.userFriendsCount.text = count.toString()
                }
            }
    }

    /* ░░░ PERMISSIONS RESULT ░░░ */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            return
        }

        when (requestCode) {
            PERMISSION_REQUEST_GALLERY -> openMediaPicker()
            PERMISSION_REQUEST_CAMERA_PHOTO -> openCameraForPhoto()
            PERMISSION_REQUEST_CAMERA_VIDEO -> openVideoRecorder()
        }
    }

    /* ░░░ PROFILE INFO ░░░ */
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
                    binding.userBio.text =
                        if (bio.isNotBlank()) bio else "Tap to add a short bio..."

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
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT)
                    .show()
            }

        binding.userBio.setOnClickListener { showEditBioDialog() }
    }

    /* ░░░ REVIEW COUNT ░░░ */
    private fun loadUserReviewCount() {
        val userId = auth.currentUser?.uid ?: return

        db.collectionGroup("reviews").whereEqualTo("userId", userId).get()
            .addOnSuccessListener { documents ->
                Log.d(
                    "ProfileFragment",
                    "Review count query successful, found ${documents.size()} reviews."
                )
                binding.userReviewsCount.text = documents.size().toString()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Error getting review count", e)
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Toast.makeText(
                        requireContext(),
                        "Query requires an index. Check Logcat for a link to create it.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    /* ░░░ ADD MEDIA DIALOG ░░░ */
    private fun showAddMediaDialog() {
        val options = arrayOf("Take Photo", "Choose From Gallery", "Record Video")
        AlertDialog.Builder(requireContext())
            .setTitle("Add media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionForPhoto()
                    1 -> checkGalleryPermission()
                    2 -> checkCameraPermissionForVideo()
                }
            }
            .show()
    }

    private fun checkGalleryPermission() {
        if (requireContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openMediaPicker()
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                PERMISSION_REQUEST_GALLERY
            )
        }
    }

    private fun checkCameraPermissionForPhoto() {
        if (requireContext().checkSelfPermission(android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openCameraForPhoto()
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CAMERA_PHOTO
            )
        }
    }

    private fun checkCameraPermissionForVideo() {
        if (requireContext().checkSelfPermission(android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openVideoRecorder()
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CAMERA_VIDEO
            )
        }
    }

    /* ░░░ PICKERS & CAMERA ░░░ */
    private fun openMediaPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }


    private fun openCameraForPhoto() {
        val photoFile = File.createTempFile(
            "IMG_${System.currentTimeMillis()}",
            ".jpg",
            requireContext().cacheDir
        )
        val authority = "${requireContext().packageName}.provider"
        tempPhotoUri = FileProvider.getUriForFile(requireContext(), authority, photoFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
        }

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivityForResult(intent, CAPTURE_IMAGE_REQUEST)
        } else {
            Toast.makeText(requireContext(), "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideoRecorder() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivityForResult(intent, RECORD_VIDEO_REQUEST)
        } else {
            Toast.makeText(requireContext(), "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    /* ░░░ onActivityResult ░░░ */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            PICK_IMAGE_REQUEST -> {
                val uri: Uri? = data?.data
                if (uri != null) {
                    val mime = requireContext().contentResolver.getType(uri) ?: ""

                    if (mime.startsWith("video")) {
                        uploadVideoToSupabase(uri)
                    } else if (mime.startsWith("image")) {
                        uploadImageToSupabase(uri)
                    } else {
                        Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            CAPTURE_IMAGE_REQUEST -> {
                val uri = tempPhotoUri
                if (uri != null) {
                    uploadImageToSupabase(uri)
                } else {
                    Toast.makeText(requireContext(), "Failed to capture photo", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            RECORD_VIDEO_REQUEST -> {
                val videoUri: Uri? = data?.data
                if (videoUri != null) {
                    uploadVideoToSupabase(videoUri)
                } else {
                    Toast.makeText(requireContext(), "Failed to capture video", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    /* ░░░ UPLOAD MEDIA TO SUPABASE ░░░ */
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
                            "uploadedAt" to System.currentTimeMillis(),
                            "type" to "photo"
                        )

                        db.collection("users").document(userId)
                            .collection("photos")
                            .add(photoData)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Photo uploaded!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                photos.add(
                                    Photo(
                                        storagePath = savedPath,
                                        mediaUrl = signed,
                                        type = "photo",
                                        uploadedAt = System.currentTimeMillis()
                                    )
                                )
                                photoAdapter.notifyItemInserted(photos.size) // +1 because of add tile
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to save photo data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to sign URL: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onFailure = {
                Toast.makeText(
                    requireContext(),
                    "Upload failed: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun uploadVideoToSupabase(videoUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val filePath = "${userId}_${UUID.randomUUID()}.mp4"

        Toast.makeText(requireContext(), "Uploading video...", Toast.LENGTH_SHORT).show()

        SupabaseHelper.uploadFileReturnPath(
            bucket = "user_photos",
            filePath = filePath,
            fileUri = videoUri,
            context = requireContext(),
            onSuccessPath = { savedPath ->
                lifecycleScope.launch {
                    try {
                        val signed = SupabaseHelper.getSignedUrl(
                            bucket = "user_photos",
                            path = savedPath,
                            expiresInSeconds = 3600
                        )

                        val videoData = mapOf(
                            "path" to savedPath,
                            "uploadedAt" to System.currentTimeMillis(),
                            "type" to "video"
                        )

                        db.collection("users").document(userId)
                            .collection("photos")
                            .add(videoData)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Video uploaded!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                photos.add(
                                    Photo(
                                        storagePath = savedPath,
                                        mediaUrl = signed,
                                        type = "video",
                                        uploadedAt = System.currentTimeMillis()
                                    )
                                )
                                photoAdapter.notifyItemInserted(photos.size)
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to save video data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to sign URL: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onFailure = {
                Toast.makeText(
                    requireContext(),
                    "Upload failed: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    /* ░░░ LOAD MEDIA FROM FIRESTORE ░░░ */
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
                            val type = document.getString("type") ?: "photo"

                            if (!path.isNullOrBlank()) {
                                val signed = SupabaseHelper.getSignedUrl(
                                    bucket = "user_photos",
                                    path = path,
                                    expiresInSeconds = 3600
                                )
                                photos.add(
                                    Photo(
                                        storagePath = path,
                                        mediaUrl = signed,
                                        type = type,
                                        uploadedAt = uploadedAt
                                    )
                                )
                            } else {
                                // Legacy support: imageUrl field
                                val imageUrl = document.getString("imageUrl")
                                if (!imageUrl.isNullOrBlank()) {
                                    photos.add(
                                        Photo(
                                            storagePath = "",
                                            mediaUrl = imageUrl,
                                            type = "photo",
                                            uploadedAt = uploadedAt
                                        )
                                    )
                                }
                            }
                        }
                        photoAdapter.notifyDataSetChanged()
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load media: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load media", Toast.LENGTH_SHORT).show()
            }
    }

    /* ░░░ EDIT BIO ░░░ */
    private fun showEditBioDialog() {
        val userId = auth.currentUser?.uid ?: return
        val currentBio =
            binding.userBio.text.toString()
                .takeIf { it != "Tap to add a short bio..." } ?: ""

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
                        binding.userBio.text =
                            if (newBio.isNotEmpty()) newBio else "Tap to add a short bio..."
                        Toast.makeText(requireContext(), "Bio updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "Failed to update bio: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /* ░░░ DELETE MEDIA ░░░ */
    private fun deletePhoto(photo: Photo) {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val path = photo.storagePath.takeIf { it.isNotBlank() }
                    ?: photo.mediaUrl.substringBeforeLast("?").substringAfterLast("/")

                SupabaseHelper.deleteFile("user_photos", path)

                db.collection("users").document(userId)
                    .collection("photos")
                    .whereEqualTo("path", path)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) doc.reference.delete()
                        Toast.makeText(
                            requireContext(),
                            "Media deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            requireContext(),
                            "Firestore delete failed: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                val index = photos.indexOf(photo)
                if (index != -1) {
                    photos.removeAt(index)
                    photoAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
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
