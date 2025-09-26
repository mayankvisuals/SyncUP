package com.syncup.app.feature.home // ✅ Galti theek kar di gayi hai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.syncup.app.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    // KTX ke bina database reference
    private val db = FirebaseDatabase.getInstance().getReference("channels")
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())

    // Search ke liye
    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    // Search ke hisaab se filter hui list
    val channels = searchText
        .combine(_channels) { text, channels ->
            if (text.isBlank()) {
                channels
            } else {
                channels.filter {
                    it.name.contains(text, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _channels.value
        )

    init {
        listenToChannels()
    }

    private fun listenToChannels() {
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Channel>()
                snapshot.children.forEach { data ->
                    try {
                        // Purane aur naye, dono format ke data ko handle karega
                        if (data.hasChildren() && data.child("name").exists()) {
                            val channel = data.getValue(Channel::class.java)
                            channel?.let { list.add(it.copy(id = data.key ?: "")) }
                        } else {
                            val channelName = data.getValue(String::class.java)
                            if (channelName != null) {
                                val channel = Channel(id = data.key ?: "", name = channelName, createdAt = System.currentTimeMillis())
                                list.add(channel)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error parsing channel: ${data.value}", e)
                    }
                }
                _channels.value = list.sortedByDescending { it.createdAt }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeViewModel", "Database error: ${error.message}")
            }
        })
    }

    fun onSearchTextChange(text: String) {
        _searchText.value = text
    }

    // Naye channel ko ab sahi format me save karenge
    fun addChannel(name: String) {
        val key = db.push().key ?: return
        val newChannel = Channel(id = key, name = name, createdAt = System.currentTimeMillis())
        db.child(key).setValue(newChannel)
    }
}

