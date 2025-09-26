package com.syncup.app.feature.auth.signup

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.syncup.app.SuperbaseStorageUtils
import com.syncup.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.random.Random

// Represents the different steps in the sign-up process
enum class SignUpStep {
    Email, Password, Name, Username, ProfilePicture
}

// Enums for tracking async validation status
enum class ValidationStatus {
    IDLE, CHECKING, TAKEN, AVAILABLE
}

// Holds all the data and state for the sign-up flow
data class SignUpUiState(
    val step: SignUpStep = SignUpStep.Email,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val username: String = "",
    val profilePictureUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignUpComplete: Boolean = false,
    val emailCheckStatus: ValidationStatus = ValidationStatus.IDLE,
    val usernameCheckStatus: ValidationStatus = ValidationStatus.IDLE,
    val usernameSuggestions: List<String> = emptyList() // For suggestions
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val superbaseStorage = SuperbaseStorageUtils(application)

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState = _uiState.asStateFlow()

    private var validationJob: Job? = null

    // --- Email Handling ---
    fun onEmailChanged(email: String) {
        val trimmedEmail = email.trim()
        _uiState.update { it.copy(email = trimmedEmail, emailCheckStatus = ValidationStatus.IDLE, errorMessage = null) }
        validationJob?.cancel()
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            return
        }
        validationJob = viewModelScope.launch {
            delay(800)
            checkEmailAvailability(trimmedEmail)
        }
    }

    private fun checkEmailAvailability(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(emailCheckStatus = ValidationStatus.CHECKING) }
            try {
                val methods = auth.fetchSignInMethodsForEmail(email).await().signInMethods
                if (methods.isNullOrEmpty()) {
                    _uiState.update { it.copy(emailCheckStatus = ValidationStatus.AVAILABLE) }
                } else {
                    _uiState.update { it.copy(emailCheckStatus = ValidationStatus.TAKEN) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(emailCheckStatus = ValidationStatus.IDLE) }
            }
        }
    }

    // --- Username Handling ---
    fun onUsernameChanged(username: String) {
        val trimmedUsername = username.trim().lowercase()
        _uiState.update {
            it.copy(
                username = trimmedUsername,
                usernameCheckStatus = ValidationStatus.IDLE,
                errorMessage = null,
                usernameSuggestions = emptyList()
            )
        }
        validationJob?.cancel()
        if (trimmedUsername.length < 4) return

        validationJob = viewModelScope.launch {
            delay(800)
            checkUsernameAvailability(trimmedUsername)
        }
    }

    private fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(usernameCheckStatus = ValidationStatus.CHECKING) }
            try {
                val snapshot = db.child("usernames").child(username).get().await()
                if (snapshot.exists()) {
                    _uiState.update { it.copy(usernameCheckStatus = ValidationStatus.TAKEN) }
                    generateUsernameSuggestions(username)
                } else {
                    _uiState.update { it.copy(usernameCheckStatus = ValidationStatus.AVAILABLE, usernameSuggestions = emptyList()) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(usernameCheckStatus = ValidationStatus.IDLE) }
            }
        }
    }

    private fun generateUsernameSuggestions(baseUsername: String) {
        val suggestions = listOf(
            "$baseUsername${Random.nextInt(10, 99)}",
            "${baseUsername}_dev",
            "$baseUsername${Random.nextInt(100, 999)}"
        )
        _uiState.update { it.copy(usernameSuggestions = suggestions) }
    }


    fun onPasswordChanged(password: String) { _uiState.update { it.copy(password = password) } }
    fun onNameChanged(name: String) { _uiState.update { it.copy(name = name) } }
    fun onProfilePictureSelected(uri: Uri?) { _uiState.update { it.copy(profilePictureUri = uri) } }


    fun nextStep() {
        val currentState = _uiState.value
        _uiState.update { it.copy(errorMessage = null) }

        when (currentState.step) {
            SignUpStep.Email -> {
                if (currentState.emailCheckStatus == ValidationStatus.TAKEN) {
                    _uiState.update { it.copy(errorMessage = "This email is already registered.") }
                    return
                }
                if (currentState.emailCheckStatus != ValidationStatus.AVAILABLE) {
                    _uiState.update { it.copy(errorMessage = "Please enter a valid and available email.") }
                    return
                }
                _uiState.update { it.copy(step = SignUpStep.Password) }
            }
            SignUpStep.Password -> {
                if (currentState.password.length < 8) {
                    _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters.") }
                    return
                }
                _uiState.update { it.copy(step = SignUpStep.Name) }
            }
            SignUpStep.Name -> {
                if (currentState.name.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Please enter your name.") }
                    return
                }
                _uiState.update { it.copy(step = SignUpStep.Username) }
            }
            SignUpStep.Username -> {
                if (currentState.usernameCheckStatus == ValidationStatus.TAKEN) {
                    _uiState.update { it.copy(errorMessage = "This username is already taken.") }
                    return
                }
                if (currentState.usernameCheckStatus != ValidationStatus.AVAILABLE) {
                    _uiState.update { it.copy(errorMessage = "Please choose an available username.") }
                    return
                }
                _uiState.update { it.copy(step = SignUpStep.ProfilePicture) }
            }
            SignUpStep.ProfilePicture -> createAccount()
        }
    }

    fun previousStep() {
        val currentStep = _uiState.value.step
        val previousStep = SignUpStep.values().getOrNull(currentStep.ordinal - 1)
        previousStep?.let {
            _uiState.update { state -> state.copy(step = it, errorMessage = null) }
        }
    }

    fun createAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Final check for email before creating account to prevent race condition
                val methods = auth.fetchSignInMethodsForEmail(_uiState.value.email).await().signInMethods
                if (!methods.isNullOrEmpty()) {
                    throw FirebaseAuthUserCollisionException("EMAIL_EXISTS", "The email address is already in use by another account.")
                }

                val authResult = auth.createUserWithEmailAndPassword(_uiState.value.email, _uiState.value.password).await()
                val user = authResult.user ?: throw Exception("Failed to create user.")

                var photoUrl: String? = null
                if (_uiState.value.profilePictureUri != null) {
                    photoUrl = superbaseStorage.uploadProfilePicture(_uiState.value.profilePictureUri!!)
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(_uiState.value.name)
                    .setPhotoUri(photoUrl?.toUri())
                    .build()
                user.updateProfile(profileUpdates).await()

                // --- FIX: Create a complete User object with all fields ---
                val userProfile = User(
                    id = user.uid,
                    name = _uiState.value.name,
                    username = _uiState.value.username,
                    email = _uiState.value.email,
                    photoUrl = photoUrl,
                    bio = "Hey there! I am using SyncUp.",
                    role = "member",
                    verified = false,
                    followersCount = 0,
                    followingCount = 0
                )

                val updates = mapOf(
                    "/users/${user.uid}" to userProfile,
                    "/usernames/${_uiState.value.username}" to user.uid
                )
                db.updateChildren(updates).await()

                _uiState.update { it.copy(isLoading = false, isSignUpComplete = true) }

            } catch (e: Exception) {
                Log.e("SignUpViewModel", "Account creation failed", e)
                val friendlyMessage = when (e) {
                    is FirebaseAuthUserCollisionException -> "Error: This email is already registered."
                    else -> "Error: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = friendlyMessage, step = SignUpStep.Email) }
            }
        }
    }
}

