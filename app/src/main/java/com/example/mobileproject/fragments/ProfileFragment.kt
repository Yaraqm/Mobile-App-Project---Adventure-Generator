package com.example.mobileproject.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.mobileproject.LoginActivity
import com.example.mobileproject.R
import com.example.mobileproject.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

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

        loadUserProfile()

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // Handle user not logged in
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    val email = document.getString("email")
                    val photoUrl = document.getString("photoUrl")
                    val points = document.getLong("points") ?: 0
                    val joinedAt = document.getTimestamp("joinedAt")

                    binding.userName.text = name
                    binding.userEmail.text = email
                    binding.userPoints.text = "Points: $points"

                    joinedAt?.let {
                        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                        binding.joinedAt.text = "Joined: ${sdf.format(it.toDate())}"
                    }

                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_launcher_background) // A default image
                        .into(binding.profileImage)

                }
            }
            .addOnFailureListener {
                // Handle failure
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
