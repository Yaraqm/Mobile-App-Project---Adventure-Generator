package com.example.mobileproject.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.adapters.AdventureAdapter
import com.example.mobileproject.databinding.FragmentHomeBinding
import com.example.mobileproject.models.Adventure
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.util.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AdventureAdapter
    private val db = FirebaseFirestore.getInstance()
    private val adventures = mutableListOf<Adventure>()
    private val random = Random()
    private var isSpinning = false
    private var currentAngle = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdventureAdapter { adv ->
            Toast.makeText(requireContext(), "Tapped: ${adv.title}", Toast.LENGTH_SHORT).show()
        }

        binding.adventureRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.adventureRecycler.adapter = adapter

        loadAdventures()

        binding.randomAdventureButton.setOnClickListener {
            if (!isSpinning && adventures.isNotEmpty()) {
                spinWheel()
            }
        }
    }

    private fun loadAdventures() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snapshot = db.collection("adventures").get().await()
                val items = snapshot.documents.mapNotNull { it.toObject(Adventure::class.java) }
                adventures.clear()
                adventures.addAll(items)
                adapter.submitList(adventures)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load adventures", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun spinWheel() {
        binding.resultText.visibility = View.GONE
        isSpinning = true
        val degrees = random.nextFloat() * 3600 + 720 // Spin at least 2 times

        val rotateAnimation = RotateAnimation(
            currentAngle,
            currentAngle + degrees,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 4000
            interpolator = DecelerateInterpolator()
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    isSpinning = false
                    val selectedAdventure = adventures[getAdventureIndexFromAngle(currentAngle + degrees)]
                    binding.resultText.text = "You got: ${selectedAdventure.title}"
                    binding.resultText.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }

        currentAngle = (currentAngle + degrees) % 360
        binding.wheel.startAnimation(rotateAnimation)
    }

    private fun getAdventureIndexFromAngle(angle: Float): Int {
        val partitionSize = 360f / adventures.size
        val normalizedAngle = (360 - angle % 360) % 360
        return (normalizedAngle / partitionSize).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
