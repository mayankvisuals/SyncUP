package com.syncup.app.feature.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.database
import com.syncup.app.SuperbaseStorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val auth = Firebase.auth
    private val db = Firebase.database.reference
    private val currentUser = auth.currentUser
    private val superbaseStorage = SuperbaseStorageUtils(application.applicationContext)

    private val _userName = MutableStateFlow(currentUser?.displayName ?: "")
    val userName = _userName.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _userBio = MutableStateFlow("Hey there! I am using SyncUp.")
    val userBio = _userBio.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow(currentUser?.photoUrl?.toString())
    val userPhotoUrl = _userPhotoUrl.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus = _saveStatus.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private var originalUsername: String = ""

    init {
        // ViewModel shuru hote hi profile fetch karega
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        currentUser?.let { user ->
            // UI ko update karne ke liye snapshot listener ka istemal
            db.child("users").child(user.uid).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (snapshot.exists()) {
                        _userName.value = snapshot.child("name").getValue(String::class.java) ?: currentUser.displayName ?: ""
                        _username.value = snapshot.child("username").getValue(String::class.java) ?: ""
                        originalUsername = _username.value
                        _userBio.value = snapshot.child("bio").getValue(String::class.java) ?: "Hey there! I am using SyncUp."
                        _userPhotoUrl.value = snapshot.child("photoUrl").getValue(String::class.java)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("EditProfileViewModel", "Failed to fetch user profile", error.toException())
                }
            })
        }
    }


    fun onNameChange(newName: String) { _userName.value = newName }
    fun onUsernameChange(newUsername: String) { _username.value = newUsername.trim().lowercase() }
    fun onBioChange(newBio: String) { _userBio.value = newBio }

    fun changeProfilePicture(uri: Uri) {
        if (currentUser == null) return
        val oldPhotoUrl = _userPhotoUrl.value // Purani photo ka URL store karein

        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Step 1: Agar purani photo hai, to use delete karein
                if (!oldPhotoUrl.isNullOrBlank()) {
                    superbaseStorage.deleteImage(oldPhotoUrl)
                }

                // Step 2: Nayi photo upload karein
                val imageUrl = superbaseStorage.uploadProfilePicture(uri)
                if (imageUrl != null) {
                    val profileUpdates = userProfileChangeRequest {
                        photoUri = imageUrl.toUri()
                    }
                    currentUser.updateProfile(profileUpdates).await()
                    _userPhotoUrl.value = imageUrl
                    db.child("users").child(currentUser.uid).child("photoUrl").setValue(imageUrl).await()
                    _saveStatus.value = "Profile picture updated!"
                } else {
                    throw Exception("Image upload returned null URL.")
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Failed to change profile picture", e)
                _saveStatus.value = "Failed to update picture: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveProfile() {
        if (currentUser == null || _isSaving.value) return
        val newUsername = _username.value

        if (newUsername.isEmpty()) {
            _saveStatus.value = "Username cannot be empty."
            return
        }
        if(newUsername.length < 4){
            _saveStatus.value = "Username must be at least 4 characters long."
            return
        }


        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (newUsername != originalUsername) {
                    val snapshot = db.child("usernames").child(newUsername).get().await()
                    if (snapshot.exists()) {
                        throw Exception("Username '$newUsername' is already taken.")
                    }
                }

                val newName = _userName.value
                val newBio = _userBio.value

                val profileUpdates = userProfileChangeRequest { displayName = newName }
                currentUser.updateProfile(profileUpdates).await()

                val userProfileData = mapOf(
                    "id" to currentUser.uid,
                    "name" to newName,
                    "username" to newUsername,
                    "bio" to newBio,
                    "photoUrl" to (_userPhotoUrl.value ?: "")
                )

                val updates = mutableMapOf<String, Any?>()
                updates["/users/${currentUser.uid}"] = userProfileData
                if (newUsername != originalUsername) {
                    if(originalUsername.isNotEmpty()) updates["/usernames/$originalUsername"] = null
                    updates["/usernames/$newUsername"] = currentUser.uid
                }

                db.updateChildren(updates).await()

                originalUsername = newUsername
                _saveStatus.value = "Profile saved successfully!"

            } catch (e: Exception) {
                _saveStatus.value = "Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveStatus() { _saveStatus.value = null }
}
