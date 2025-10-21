package com.example.mobileproject.ui.rewards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.adapters.RewardAdapter
import com.example.mobileproject.databinding.FragmentRewardsBinding
import com.example.mobileproject.utils.FirestoreUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RewardsFragment : Fragment() {

    private var _binding: FragmentRewardsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RewardAdapter
    private var flowJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecycler()
        observeRewards()

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            reloadRewards()
        }
    }

    private fun setupRecycler() {
        adapter = RewardAdapter { reward ->
            // TODO: handle click (detail or redeem)
        }
        binding.rewardsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.rewardsRecycler.adapter = adapter
    }

    private fun observeRewards() {
        // show progress while first loading
        binding.progress.isVisible = true
        binding.emptyView.isVisible = false

        flowJob?.cancel()
        flowJob = viewLifecycleOwner.lifecycleScope.launch {
            FirestoreUtils.rewardsFlow().collect { items ->
                adapter.submitList(items)
                binding.emptyView.isVisible = items.isEmpty()
                binding.progress.isVisible = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun reloadRewards() {
        // If your Firestore flow is a snapshot listener, simply re-subscribing is fine.
        // This restarts the collector and turns the spinner off when data arrives.
        observeRewards()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        flowJob?.cancel()
        _binding = null
    }
}
