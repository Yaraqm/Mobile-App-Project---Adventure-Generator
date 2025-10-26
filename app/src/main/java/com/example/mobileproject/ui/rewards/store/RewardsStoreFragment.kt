package com.example.mobileproject.ui.rewards.store

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.R
import com.example.mobileproject.databinding.FragmentRewardsStoreBinding
import com.example.mobileproject.ui.rewards.Badge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RewardsStoreFragment : Fragment() {

    private var _binding: FragmentRewardsStoreBinding? = null
    private val binding get() = _binding!!

    private lateinit var rewardsStoreAdapter: RewardsStoreAdapter
    private var badgeToDownload: Badge? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            badgeToDownload?.let {
                // Once permission is granted, call the download logic again.
                // It will now succeed.
                downloadBadgeImage(it)
                badgeToDownload = null
            }
        } else {
            Toast.makeText(requireContext(), "Permission denied. Cannot save file.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardsStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadRewards()
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        rewardsStoreAdapter = RewardsStoreAdapter { badge ->
            showRedeemConfirmationDialog(badge)
        }
        binding.recyclerRewardsStore.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rewardsStoreAdapter
        }
    }

    private fun loadRewards() {
        val badges = listOf(
            Badge("The Extrovert", 500, R.drawable.ic_extrovert),
            Badge("The Tourist", 1500, R.drawable.ic_tourist),
            Badge("The Explorer", 3000, R.drawable.ic_explorer),
            Badge("Thrill-Seeker", 5000, R.drawable.ic_thrill_seeker),
            Badge("Daredevil", 7500, R.drawable.ic_daredevil),
            Badge("Adventurer", 10000, R.drawable.ic_adventurer)
        )
        rewardsStoreAdapter.submitList(badges)
    }

    private fun showRedeemConfirmationDialog(badge: Badge) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redeem Badge")
            .setMessage("Do you want to redeem the '${badge.title}' for ${badge.pointsRequired} points and download the image?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Redeem") { _, _ ->
                if (getRawResourceIdForBadge(badge.title) != null) {
                    badgeToDownload = badge
                    downloadBadgeImage(badge) // Directly call the download function
                } else {
                    Toast.makeText(requireContext(), "No downloadable image for this badge.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun getRawResourceIdForBadge(badgeTitle: String): Int? {
        return when (badgeTitle) {
            "Thrill-Seeker" -> R.raw.badge4
            "Daredevil" -> R.raw.badge5
            "Adventurer" -> R.raw.badge6
            else -> null
        }
    }

    private fun downloadBadgeImage(badge: Badge) {
        // This is the crucial version check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (Q) and above, use the modern MediaStore API
            saveImageWithMediaStore(badge)
        } else {
            // For older versions, use the legacy file path method, which requires permission.
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveImageWithLegacyStorage(badge)
            } else {
                // If permission is not granted, request it.
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // Modern method for API 29+
    private fun saveImageWithMediaStore(badge: Badge) {
        val rawResourceId = getRawResourceIdForBadge(badge.title) ?: return
        val resolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${badge.title.replace(" ", "_")}_Badge.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        var imageUri: Uri? = null // FIX 1: Make Uri nullable
        try {
            // FIX 2: Removed redundant 'if' statement
            imageUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                resources.openRawResource(rawResourceId).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(requireContext(), "${badge.title} badge saved to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            imageUri?.let { resolver.delete(it, null, null) }
            Toast.makeText(requireContext(), "Failed to save badge image.", Toast.LENGTH_SHORT).show()
        }
    }

    // Legacy method for API < 29
    private fun saveImageWithLegacyStorage(badge: Badge) {
        val rawResourceId = getRawResourceIdForBadge(badge.title) ?: return
        try {
            val inputStream = resources.openRawResource(rawResourceId)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // Ensure the directory exists
            downloadsDir.mkdirs()

            val file = File(downloadsDir, "${badge.title.replace(" ", "_")}_Badge.png")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            // Notify the gallery of the new file
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(file)
            requireContext().sendBroadcast(mediaScanIntent)

            Toast.makeText(requireContext(), "${badge.title} badge saved to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to save badge image.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
