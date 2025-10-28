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
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.mobileproject.LoginActivity
import com.example.mobileproject.R
import com.example.mobileproject.adapters.PhotoAdapter
import com.example.mobileproject.databinding.FragmentProfileBinding
import com.example.mobileproject.models.Photo
import com.example.mobileproject.utils.SupabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import android.content.pm.PackageManager
import io.github.jan.supabase.storage.storage

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val photos = mutableListOf<Photo>()
    private lateinit var photoAdapter: PhotoAdapter

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

        // RecyclerView setup
        photoAdapter = PhotoAdapter(photos) { selectedPhoto ->
            val dialog = ImagePreviewDialogFragment.newInstance(selectedPhoto.imageUrl)
            dialog.setOnDeleteClicked {
                deletePhoto(selectedPhoto)
            }
            dialog.show(parentFragmentManager, "ImagePreviewDialog")
        }

        binding.profileRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.profileRecyclerView.adapter = photoAdapter

        loadUserProfile()
        loadUserPhotos()

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }


        // ░░░ UPLOAD PHOTO FAB ░░░
        binding.btnUploadPhoto.setOnClickListener {
            // ✅ Request runtime permission before picker
            if (requireContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 2000)
            } else {
                openImagePicker()
            }
        }
    }

    // ✅ Handle permission result
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

                    binding.userName.text = name
                    binding.userEmail.text = email
                    binding.userPoints.text = "$points"

                    // Bio text
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

        // ✅ Set up tap-to-edit behavior
        binding.userBio.setOnClickListener {
            showEditBioDialog()
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

                // Update Firestore dynamically
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
                // 1️⃣ Extract the correct path (before any query params)
                val fullUrl = photo.imageUrl
                val baseFileName = fullUrl.substringBeforeLast("?").substringAfterLast("/") // gives userId_uuid.jpg

                // 2️⃣ Delete from Supabase
                SupabaseHelper.deleteFile("user_photos", baseFileName)

                // 3️⃣ Delete from Firestore
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

                // 4️⃣ Update locally
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
        _binding = null
    }
}



