package com.syncup.app.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun onSearchTextChange(text: String) {
        _searchText.value = text
        // Search tabhi shuru hoga jab user kam se kam 3 character type karega
        if (text.trim().length >= 3) {
            performSearch(text.trim().lowercase())
        } else {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // --- SEARCH LOGIC FIX & DEBUGGING ---
                // IMPORTANT: Is search ke kaam karne ke liye, aapke Firebase Realtime Database
                // ke rules '/users' node ko read karne ki permission dene chahiye.
                // Example Rule:
                // {
                //   "rules": {
                //     "users": {
                //       ".read": "auth != null", // Logged-in users ko read access deta hai
                //       "$uid": {
                //         ".write": "$uid === auth.uid"
                //       }
                //     },
                //     // ... baaki rules
                //   }
                // }

                val usersRef = db.child("users")
                val searchQuery = usersRef.orderByChild("username")
                    .startAt(query)
                    .endAt(query + "\uf8ff") // Yeh prefix search ke liye zaroori hai

                val snapshot = searchQuery.get().await()

                // Hum check karne ke liye result count ko Logcat me print karenge
                Log.d("SearchViewModel", "Search for '$query' found ${snapshot.childrenCount} results from Firebase.")

                val userList = snapshot.children.mapNotNull { it.getValue(User::class.java) }

                // Search result me se current user ko hata denge
                val currentUserId = auth.currentUser?.uid
                _searchResults.value = userList.filter { it.id != currentUserId }

            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error performing search", e)
                _searchResults.value = emptyList() // Error aane par list khali kar do
            } finally {
                _isLoading.value = false
            }
        }
    }
}
