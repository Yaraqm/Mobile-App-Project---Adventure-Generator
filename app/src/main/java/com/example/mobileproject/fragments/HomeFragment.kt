package com.example.mobileproject.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.adapters.AdventureAdapter
import com.example.mobileproject.databinding.FragmentHomeBinding
import com.example.mobileproject.models.Adventure
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val adventureList = mutableListOf<Adventure>()
    private lateinit var adapter: AdventureAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdventureAdapter(adventureList)
        binding.adventureRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.adventureRecycler.adapter = adapter

        loadAdventures()
    }

    private fun loadAdventures() {
        db.collection("adventures")
            .get()
            .addOnSuccessListener { result ->
                adventureList.clear()
                for (doc in result) {
                    val adventure = doc.toObject(Adventure::class.java)
                    adventureList.add(adventure)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load adventures", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
