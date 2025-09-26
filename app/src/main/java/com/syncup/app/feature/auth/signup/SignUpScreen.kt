package com.syncup.app.feature.auth.signup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.syncup.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SignUpScreen(navController: NavHostController) {
    val viewModel: SignUpViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSignUpComplete) {
        if (uiState.isSignUpComplete) {
            navController.navigate("main") {
                popUpTo("signup") { inclusive = true }
                popUpTo("login") { inclusive = true }
            }
        }
    }

    val screenBackgroundColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.background
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundColor)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(), // --- FIX: Keyboard ke liye padding add ki gayi ---
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // --- FIX: Column ko scrollable banaya gaya ---
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SignUpHeader(step = uiState.step, textColor = textColor)
                Spacer(modifier = Modifier.height(32.dp))

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedContent(
                    targetState = uiState.step,
                    label = "SignUpStepAnimation",
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            slideInHorizontally { fullWidth -> fullWidth } + fadeIn() togetherWith
                                    slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut()
                        } else {
                            slideInHorizontally { fullWidth -> -fullWidth } + fadeIn() togetherWith
                                    slideOutHorizontally { fullWidth -> fullWidth } + fadeOut()
                        }
                    }
                ) { targetStep ->
                    when (targetStep) {
                        SignUpStep.Email -> EmailStep(uiState, viewModel)
                        SignUpStep.Password -> PasswordStep(uiState, viewModel)
                        SignUpStep.Name -> NameStep(uiState, viewModel)
                        SignUpStep.Username -> UsernameStep(uiState, viewModel)
                        SignUpStep.ProfilePicture -> ProfilePictureStep(uiState, viewModel)
                    }
                }
            }

            if (uiState.step != SignUpStep.Email) {
                IconButton(
                    onClick = { viewModel.previousStep() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                }
            }

            if (uiState.isLoading && uiState.step == SignUpStep.ProfilePicture) {
                CircularProgressIndicator(color = textColor)
            }
        }
    }
}

@Composable
fun SignUpHeader(step: SignUpStep, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "SyncUp Logo",
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SignUpStep.values().forEach {
                val color = if (it.ordinal <= step.ordinal) textColor else Color.Gray
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(color, shape = CircleShape)
                )
            }
        }
    }
}

@Composable
fun StepContainer(
    title: String,
    isLoading: Boolean,
    onNext: () -> Unit,
    nextButtonText: String = "Next",
    isNextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val buttonBackgroundColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val buttonTextColor = if (isSystemInDarkTheme()) Color.Black else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(24.dp))

        content()

        Spacer(modifier = Modifier.height(24.dp))
        if (isLoading) {
            CircularProgressIndicator(color = textColor)
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                enabled = isNextEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = buttonBackgroundColor)
            ) {
                Text(text = nextButtonText, color = buttonTextColor)
            }
        }
    }
}

@Composable
fun DynamicTextFieldColors(): TextFieldColors {
    val isDarkTheme = isSystemInDarkTheme()
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
        focusedBorderColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedBorderColor = if (isDarkTheme) Color.Gray else Color.Gray,
        focusedLabelColor = if (isDarkTheme) Color.White else Color.Black,
        unfocusedLabelColor = Color.Gray,
        cursorColor = if (isDarkTheme) Color.White else Color.Black
    )
}


@Composable
fun EmailStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    val isNextEnabled = uiState.emailCheckStatus == ValidationStatus.AVAILABLE

    StepContainer(
        title = "What's your email?",
        isLoading = false,
        onNext = { viewModel.nextStep() },
        isNextEnabled = isNextEnabled
    ) {
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            isError = uiState.emailCheckStatus == ValidationStatus.TAKEN,
            supportingText = {
                when (uiState.emailCheckStatus) {
                    ValidationStatus.TAKEN -> Text("This email is already registered.", color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            },
            trailingIcon = {
                if (uiState.emailCheckStatus == ValidationStatus.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            colors = DynamicTextFieldColors()
        )
    }
}

@Composable
fun PasswordStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    StepContainer(title = "Create a password", isLoading = uiState.isLoading, onNext = { viewModel.nextStep() }) {
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            colors = DynamicTextFieldColors()
        )
    }
}

@Composable
fun NameStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    StepContainer(title = "What's your name?", isLoading = uiState.isLoading, onNext = { viewModel.nextStep() }) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = DynamicTextFieldColors()
        )
    }
}

@Composable
fun UsernameStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    val isNextEnabled = uiState.usernameCheckStatus == ValidationStatus.AVAILABLE
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

    StepContainer(
        title = "Choose a username",
        isLoading = false,
        onNext = { viewModel.nextStep() },
        isNextEnabled = isNextEnabled
    ) {
        OutlinedTextField(
            value = uiState.username,
            onValueChange = { newUsername ->
                viewModel.onUsernameChanged(newUsername.replace(" ", ""))
            },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("@", color = textColor) },
            singleLine = true,
            isError = uiState.usernameCheckStatus == ValidationStatus.TAKEN,
            trailingIcon = {
                when(uiState.usernameCheckStatus) {
                    ValidationStatus.CHECKING -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    ValidationStatus.AVAILABLE -> Icon(Icons.Default.CheckCircle, contentDescription = "Available", tint = Color.Green)
                    ValidationStatus.TAKEN -> Icon(Icons.Default.Close, contentDescription = "Taken", tint = MaterialTheme.colorScheme.error)
                    ValidationStatus.IDLE -> {}
                }
            },
            colors = DynamicTextFieldColors()
        )
        if (uiState.usernameSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Suggestions:", color = Color.Gray)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.usernameSuggestions) { suggestion ->
                    SuggestionChip(
                        suggestion = suggestion,
                        onClick = { viewModel.onUsernameChanged(suggestion) }
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(suggestion: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(suggestion) }
    )
}


@Composable
fun ProfilePictureStep(uiState: SignUpUiState, viewModel: SignUpViewModel) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            viewModel.onProfilePictureSelected(uri)
        }
    )
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val buttonBackgroundColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val buttonTextColor = if (isSystemInDarkTheme()) Color.Black else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add a profile picture?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .border(2.dp, textColor, CircleShape)
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.profilePictureUri != null) {
                AsyncImage(
                    model = uiState.profilePictureUri,
                    contentDescription = "Selected Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("Upload", color = textColor, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.createAccount() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = buttonBackgroundColor)
        ) {
            Text(text = "Finish", color = buttonTextColor)
        }
        TextButton(onClick = { viewModel.createAccount() }) {
            Text("Skip for now", color = Color.Gray)
        }
    }
}

