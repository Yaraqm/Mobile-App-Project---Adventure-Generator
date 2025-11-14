package com.example.mobileproject.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
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

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
    }

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
                if (e != null || snapshot == null || _binding == null) return@addSnapshotListener

                if (snapshot.isEmpty) {
                    friendsList.clear()
                    requestsList.clear()
                    outgoingRequests.clear()
                    updateAdapters()
                    return@addSnapshotListener
                }

                val newFriends = mutableListOf<Friend>()
                val newRequests = mutableListOf<Friend>()
                val newOutgoing = mutableListOf<Friend>()

                var remaining = snapshot.documents.size

                for (doc in snapshot.documents) {
                    val friendId = doc.id
                    if (friendId == currentUserUid) {
                        remaining--
                        if (remaining == 0) applyNewLists(newFriends, newRequests, newOutgoing)
                        continue
                    }

                    val statusString = doc.getString("status")
                    val status = when (statusString) {
                        "accepted" -> FriendshipStatus.FRIENDS
                        "pending_incoming" -> FriendshipStatus.PENDING_INCOMING
                        "pending_outgoing" -> FriendshipStatus.PENDING_OUTGOING
                        else -> null
                    }

                    if (status == null) {
                        remaining--
                        if (remaining == 0) applyNewLists(newFriends, newRequests, newOutgoing)
                        continue
                    }

                    db.collection("users").document(friendId).get()
                        .addOnSuccessListener { userDoc ->
                            val friend = userDoc.toObject(Friend::class.java)
                                ?.copy(uid = friendId, status = status)

                            if (friend != null) {
                                when (status) {
                                    FriendshipStatus.FRIENDS -> newFriends.add(friend)
                                    FriendshipStatus.PENDING_INCOMING -> newRequests.add(friend)
                                    FriendshipStatus.PENDING_OUTGOING -> newOutgoing.add(friend)
                                    else -> {}
                                }
                            }
                        }
                        .addOnCompleteListener {
                            remaining--
                            if (remaining == 0 && _binding != null) {
                                applyNewLists(newFriends, newRequests, newOutgoing)
                            }
                        }
                }
            }
    }

    private fun applyNewLists(
        newFriends: List<Friend>,
        newRequests: List<Friend>,
        newOutgoing: List<Friend>
    ) {
        friendsList.clear()
        friendsList.addAll(newFriends.distinctBy { it.uid })

        requestsList.clear()
        requestsList.addAll(newRequests.distinctBy { it.uid })

        outgoingRequests.clear()
        outgoingRequests.addAll(newOutgoing.distinctBy { it.uid })

        updateAdapters()
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
            // show default friends + outgoing requests
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
                        val status = when {
                            friendsList.any { it.uid == user.uid } -> FriendshipStatus.FRIENDS
                            requestsList.any { it.uid == user.uid } -> FriendshipStatus.PENDING_INCOMING
                            outgoingRequests.any { it.uid == user.uid } -> FriendshipStatus.PENDING_OUTGOING
                            else -> FriendshipStatus.NOT_FRIENDS
                        }
                        user.status = status
                        searchResults.add(user)
                    }
                }
                friendsAdapter.updateUsers(searchResults)
            }
    }

    /* ░░░ FRIEND REQUEST HANDLERS ░░░ */

    private fun sendFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return

        val batch = db.batch()
        val senderRef = db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
        val receiverRef = db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)

        batch.set(senderRef, mapOf("status" to "pending_outgoing"))
        batch.set(receiverRef, mapOf("status" to "pending_incoming"))

        // 🔑 Let the snapshot listener update lists; no manual list changes here
        batch.commit()
    }

    private fun acceptFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return

        val batch = db.batch()
        val userRef = db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
        val otherRef = db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)

        batch.update(userRef, "status", "accepted")
        batch.update(otherRef, "status", "accepted")
        batch.commit()
    }

    private fun rejectFriendRequest(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        val batch = db.batch()
        val ref1 = db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
        val ref2 = db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)

        batch.delete(ref1)
        batch.delete(ref2)
        batch.commit()
    }

    private fun removeFriend(user: Friend) {
        val currentUserUid = auth.currentUser?.uid ?: return
        val batch = db.batch()
        val ref1 = db.collection("users").document(currentUserUid)
            .collection("friends").document(user.uid)
        val ref2 = db.collection("users").document(user.uid)
            .collection("friends").document(currentUserUid)

        batch.delete(ref1)
        batch.delete(ref2)
        batch.commit()
    }

    private fun updateAdapters() {
        val combined = (friendsList + outgoingRequests).distinctBy { it.uid }
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
        friendsListener = null
        _binding = null
    }
}
