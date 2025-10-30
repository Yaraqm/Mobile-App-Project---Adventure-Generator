package com.example.mobileproject.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobileproject.R
import com.example.mobileproject.adapters.FriendsAdapter
import com.example.mobileproject.databinding.FragmentFriendsBinding
import com.example.mobileproject.models.Friend
import com.example.mobileproject.models.FriendshipStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var requestsAdapter: FriendsAdapter

    private val friendsList = mutableListOf<Friend>()
    private val requestsList = mutableListOf<Friend>()
    private val outgoingRequests = mutableListOf<Friend>()

    private var friendsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerViews()
        setupSearch()
        setupBackButton()
        loadFriendsAndRequests()
    }

    /* ░░░ BACK BUTTON HANDLER ░░░ */
    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    /* ░░░ RECYCLERS ░░░ */
    private fun setupRecyclerViews() {
        friendsAdapter = FriendsAdapter(
            friendsList,
            onAddFriend = ::sendFriendRequest,
            onAcceptRequest = ::acceptFriendRequest,
            onRejectRequest = ::rejectFriendRequest,
            onRemoveFriend = ::removeFriend
        )
        binding.friendsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.friendsRecyclerView.adapter = friendsAdapter

        requestsAdapter = FriendsAdapter(
            requestsList,
            onAddFriend = ::sendFriendRequest,
            onAcceptRequest = ::acceptFriendRequest,
            onRejectRequest = ::rejectFriendRequest,
            onRemoveFriend = ::removeFriend
        )
        binding.requestsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.requestsRecyclerView.adapter = requestsAdapter
    }

    /* ░░░ LOAD FRIENDS ░░░ */
    private fun loadFriendsAndRequests() {
        val currentUserUid = auth.currentUser?.uid ?: return
        friendsListener?.remove()

        friendsListener = db.collection("users").document(currentUserUid)
            .collection("friends")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                requestsList.clear()
                friendsList.clear()
                outgoingRequests.clear()

                snapshot.documents.forEach { doc ->
                    val friendId = doc.id
                    val statusString = doc.getString("status") ?: return@forEach

                    // Prevent self references or bad entries
                    if (friendId == currentUserUid) return@forEach

                    val status = when (statusString) {
                        "accepted" -> FriendshipStatus.FRIENDS
                        "pending_incoming" -> FriendshipStatus.PENDING_INCOMING
                        "pending_outgoing" -> FriendshipStatus.PENDING_OUTGOING
                        else -> return@forEach
                    }

                    db.collection("users").document(friendId).get()
                        .addOnSuccessListener { userDoc ->
                            val friend = userDoc.toObject(Friend::class.java)
                                ?.copy(uid = friendId, status = status)
                            if (friend != null) {
                                when (status) {
                                    FriendshipStatus.FRIENDS -> friendsList.add(friend)
                                    FriendshipStatus.PENDING_INCOMING -> requestsList.add(friend)
                                    FriendshipStatus.PENDING_OUTGOING -> outgoingRequests.add(friend)
                                    else -> {}
                                }
                                if (_binding != null) updateAdapters()
                            }
                        }
                }
            }
    }

    /* ░░░ SEARCH USERS ░░░ */
    private fun setupSearch() {
        binding.friendsSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchUsers(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchUsers(newText)
                return true
            }
        })
    }

    private fun searchUsers(query: String?) {
        if (query.isNullOrBlank()) {
            friendsAdapter.updateUsers(friendsList + outgoingRequests)
            return
        }

        val q = query.trim().lowercase(Locale.getDefault())
        val currentUserUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .orderBy("name")
            .limit(75)
            .get()
            .addOnSuccessListener { documents ->
                val searchResults = mutableListOf<Friend>()

                for (doc in documents) {
                    val user = doc.toObject(Friend::class.java).copy(uid = doc.id)
                    if (user.uid == currentUserUid) continue

                    val nameLc = (user.name ?: "").lowercase(Locale.getDefault())
                    if (nameLc.startsWith(q)) {

                        // ✅ Safer Firestore check for stale "requested" states
                        val userStatus = when {
                            friendsList.any { it.uid == user.uid } -> FriendshipStatus.FRIENDS
                            requestsList.any { it.uid == user.uid } -> FriendshipStatus.PENDING_INCOMING
                            outgoingRequests.any { it.uid == user.uid } -> FriendshipStatus.PENDING_OUTGOING
                            else -> FriendshipStatus.NOT_FRIENDS
                        }

                        user.status = userStatus
                        searchResults.add(user)
                    }
                }

                // Clean pass to verify no false "Requested"
                val checkedResults = mutableListOf<Friend>()
                for (user in searchResults) {
                    val docRef = db.collection("users")
                        .document(currentUserUid)
                        .collection("friends")
                        .document(user.uid)
                    docRef.get().addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val status = doc.getString("status")
                            when (status) {
                                "pending_outgoing" -> user.status = FriendshipStatus.PENDING_OUTGOING
                                "pending_incoming" -> user.status = FriendshipStatus.PENDING_INCOMING
                                "accepted" -> user.status = FriendshipStatus.FRIENDS
                                else -> user.status = FriendshipStatus.NOT_FRIENDS
                            }
                        } else {
                            user.status = FriendshipStatus.NOT_FRIENDS
                        }
                        friendsAdapter.notifyDataSetChanged()
                    }
                    checkedResults.add(user)
                }

                friendsAdapter.updateUsers(checkedResults)
            }
    }

    /* ░░░ FRIEND REQUEST HANDLERS ░░░ */
    private fun sendFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
            .set(mapOf("status" to "pending_outgoing"))

        db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)
            .set(mapOf("status" to "pending_incoming"))

        user.status = FriendshipStatus.PENDING_OUTGOING
        outgoingRequests.add(user)
        updateAdapters()
    }

    private fun acceptFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
            .update("status", "accepted")

        db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)
            .update("status", "accepted")
    }

    private fun rejectFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid).delete()

        db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid).delete()
    }

    private fun removeFriend(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid).delete()

        db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid).delete()
    }

    private fun updateAdapters() {
        val combined = friendsList + outgoingRequests
        friendsAdapter.updateUsers(combined)
        requestsAdapter.updateUsers(requestsList)

        binding.requestsTitle.visibility =
            if (requestsList.isNotEmpty()) View.VISIBLE else View.GONE
        binding.requestsRecyclerView.visibility =
            if (requestsList.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        friendsListener?.remove()
        _binding = null
    }
}
