package com.syncup.app.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncup.app.model.MusicTrack
import com.syncup.app.service.YoutubeApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicSearchViewModel @Inject constructor() : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MusicTrack>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // --- FIX: YouTube Data API key provided here ---
    // Note: For a production app, this key should be stored securely (e.g., in local.properties)
    // and not be hardcoded directly in the source code.
    private val apiService = YoutubeApiService(apiKey = "AIzaSyAcrPqo6eHA2BN9qmyLSgeN2ZyYZp-Y5n4")

    private var searchJob: Job? = null

    fun onSearchTextChanged(text: String) {
        _searchText.value = text
        searchJob?.cancel() // Cancel previous job
        if (text.length > 2) {
            searchJob = viewModelScope.launch {
                delay(500) // Debounce to avoid too many API calls
                performSearch(text)
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _searchResults.value = apiService.searchMusic(query)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

