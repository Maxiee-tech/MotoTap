package com.example.mototap.features.auth

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.example.mototap.R
import com.example.mototap.core.util.SignupValidation
import com.example.mototap.ui.theme.MotoRed
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onSignUpSuccess: (String?) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val signUpStep by viewModel.signUpStep.collectAsState()
    val appContext = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.fetchUserProfile()
        viewModel.resumeIncompleteSignupIfNeeded { }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success && signUpStep == SignUpStep.BASIC_INFO) {
            viewModel.nextStep()
            viewModel.resetUiState()
        }
    }

    Scaffold(
        topBar = {
            if (signUpStep != SignUpStep.BASIC_INFO) {
                TopAppBar(
                    title = {
                        Text(
                            when (signUpStep) {
                                SignUpStep.IDENTITY_VERIFICATION -> "Step 2: Identity"
                                SignUpStep.ADDITIONAL_INFO -> "Step 3: Details"
                                SignUpStep.WORKING_HOURS -> "Working hours"
                                else -> "Sign up"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (signUpStep != SignUpStep.WORKING_HOURS) {
                            IconButton(onClick = { viewModel.previousStep() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoRed)
                )
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (signUpStep) {
                SignUpStep.BASIC_INFO -> BasicInfoStep(viewModel, onNavigateToLogin)
                SignUpStep.IDENTITY_VERIFICATION -> IdentityVerificationStep(viewModel, appContext)
                SignUpStep.ADDITIONAL_INFO -> AdditionalInfoStep(viewModel, appContext, onSignUpSuccess)
                SignUpStep.WORKING_HOURS -> WorkingHoursStep(viewModel, onSignUpSuccess)
            }
        }
    }
}

@Composable
fun WorkingHoursStep(viewModel: AuthViewModel, onSignUpSuccess: (String?) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val hours by viewModel.workingHours.collectAsState()
    val role by viewModel.role.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Update your working hours so drivers can see when you are open.",
            color = Color.LightGray,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        WorkingHoursEditor(
            hours = hours,
            onHoursChange = { viewModel.workingHours.value = it },
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (uiState is AuthUiState.Error) {
            Text((uiState as AuthUiState.Error).message, color = Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = {
                viewModel.saveWorkingHours {
                    onSignUpSuccess(role)
                }
            },
            enabled = uiState !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text("Save working hours")
            }
        }
    }
}

@Composable
fun BasicInfoStep(viewModel: AuthViewModel, onNavigateToLogin: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val name by viewModel.name.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val role by viewModel.role.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordStrengthLabel = remember(password) {
        val s = password.trim()
        val hasLower = s.any { it.isLowerCase() }
        val hasUpper = s.any { it.isUpperCase() }
        val hasDigit = s.any { it.isDigit() }
        val score = listOf(hasLower, hasUpper, hasDigit, s.length >= 8).count { it }
        when {
            s.isBlank() -> ""
            score <= 1 -> "Weak"
            score == 2 -> "Fair"
            score == 3 -> "Good"
            else -> "Strong"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.signup_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = stringResource(id = R.string.signup_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.name.value = it },
            label = { Text(stringResource(id = R.string.signup_name_label), color = Color.Gray) },
            singleLine = true,
            isError = name.isNotBlank() && !viewModel.isNameValid(name),
            supportingText = {
                if (name.isNotBlank() && !viewModel.isNameValid(name)) {
                    Text("Please provide at least two names (e.g. John Doe)", color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MotoRed,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { viewModel.email.value = it },
            label = { Text("Email", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MotoRed,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.password.value = it },
            label = { Text("Password", color = Color.Gray) },
            supportingText = {
                if (passwordStrengthLabel.isNotBlank()) {
                    Text(
                        text = "Password strength: $passwordStrengthLabel",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description, tint = Color.Gray)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MotoRed,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { viewModel.phoneNumber.value = it },
            label = { Text("Phone Number", color = Color.Gray) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MotoRed,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Select Role:",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.Start)
        )

        val garageMode by viewModel.garageMode.collectAsState()
        val garageInviteCode by viewModel.garageInviteCode.collectAsState()
        val inviteVerified by viewModel.inviteVerified.collectAsState()
        val verifiedGarageName by viewModel.verifiedGarageName.collectAsState()

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = role == "driver",
                    onClick = {
                        viewModel.role.value = "driver"
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = MotoRed, unselectedColor = Color.Gray)
                )
                Text(
                    text = "Driver",
                    color = Color.White,
                    modifier = Modifier.clickable {
                        viewModel.role.value = "driver"
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = role == "mechanic",
                    onClick = { viewModel.role.value = "mechanic" },
                    colors = RadioButtonDefaults.colors(selectedColor = MotoRed, unselectedColor = Color.Gray)
                )
                Text(
                    text = "Mechanic / Garage",
                    color = Color.White,
                    modifier = Modifier.clickable { viewModel.role.value = "mechanic" }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = role == "parts_dealer",
                    onClick = {
                        viewModel.role.value = "parts_dealer"
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = MotoRed, unselectedColor = Color.Gray)
                )
                Text(
                    text = "Parts Dealer",
                    color = Color.White,
                    modifier = Modifier.clickable {
                        viewModel.role.value = "parts_dealer"
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    }
                )
            }
        }

        if (role == "mechanic") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("How are you joining MotoTap?", color = Color.White, modifier = Modifier.align(Alignment.Start))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = garageMode != "join",
                    onClick = {
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    },
                    colors = RadioButtonDefaults.colors(selectedColor = MotoRed, unselectedColor = Color.Gray)
                )
                Text(
                    "I own / manage this garage",
                    color = Color.White,
                    modifier = Modifier.clickable {
                        viewModel.garageMode.value = "own"
                        viewModel.clearVerifiedInvite()
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = garageMode == "join",
                    onClick = { viewModel.garageMode.value = "join" },
                    colors = RadioButtonDefaults.colors(selectedColor = MotoRed, unselectedColor = Color.Gray)
                )
                Text(
                    "I'm joining an existing garage",
                    color = Color.White,
                    modifier = Modifier.clickable { viewModel.garageMode.value = "join" }
                )
            }
            if (garageMode == "join") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = garageInviteCode,
                    onValueChange = {
                        viewModel.garageInviteCode.value = it
                        viewModel.clearVerifiedInvite()
                    },
                    label = { Text("Garage invite code", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MotoRed,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your invite code will be verified when you continue.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                if (inviteVerified && verifiedGarageName.isNotBlank()) {
                    Text(
                        text = "Verified: $verifiedGarageName",
                        color = Color(0xFF7DDEA5),
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.Start)
                    )
                }
            }
        }

        Button(
            onClick = {
                if (role == "mechanic" && garageMode == "join") {
                    val code = garageInviteCode.trim().uppercase().filter { it.isLetterOrDigit() }
                    if (code.length < 4) {
                        // keep on this step; validation surfaces via enabled state
                        return@Button
                    }
                }
                viewModel.signUp()
            },
            enabled = uiState !is AuthUiState.Loading &&
                    viewModel.isNameValid(name) &&
                    email.isNotBlank() &&
                    password.isNotBlank() &&
                    phoneNumber.isNotBlank() &&
                    role.isNotBlank() &&
                    (role != "mechanic" || garageMode != "join" ||
                        garageInviteCode.trim().uppercase().filter { it.isLetterOrDigit() }.length >= 4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = "NEXT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        if (uiState is AuthUiState.Error) {
            Text(
                text = (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        TextButton(
            onClick = {
                viewModel.resetState()
                onNavigateToLogin()
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.signup_have_account),
                color = MotoRed
            )
        }
    }
}

@Composable
fun IdentityVerificationStep(viewModel: AuthViewModel, context: android.content.Context) {
    val role by viewModel.role.collectAsState()
    val garageMode by viewModel.garageMode.collectAsState()
    val verifiedGarageName by viewModel.verifiedGarageName.collectAsState()
    val idNumber by viewModel.idNumber.collectAsState()
    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsState()
    val idPhotoUrl by viewModel.idPhotoUrl.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val joinMode = role == "mechanic" && garageMode.trim() == "join"

    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "profile") }
    }

    val idLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "id_front") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 2: Identity Verification",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        VerificationItem(
            label = "Selfie / Profile Photo",
            description = "Choose a photo from your device",
            previewUrl = profilePhotoUrl,
            onPickImage = {
                profileLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        if (joinMode && verifiedGarageName.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Joining $verifiedGarageName. Business documents are already on file for this garage.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!joinMode) {
            Spacer(modifier = Modifier.height(24.dp))

            val idLabel = when (role) {
                "driver" -> "Driving License"
                "parts_dealer" -> "Business License"
                else -> "Certificate of Corporation"
            }
            val idPhotoLabel = when (role) {
                "driver" -> "Front Photo of Driving License"
                "parts_dealer" -> "Front Photo of Business License"
                else -> "Certificate of Corporation"
            }

            OutlinedTextField(
                value = idNumber,
                onValueChange = { viewModel.idNumber.value = it },
                label = { Text("$idLabel Number", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MotoRed,
                    unfocusedBorderColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            VerificationItem(
                label = idPhotoLabel,
                description = "Choose a photo or PDF from your device",
                previewUrl = idPhotoUrl,
                onPickImage = { idLauncher.launch(arrayOf("image/*", "application/pdf")) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.saveIdentityStep { viewModel.nextStep() } },
            enabled = uiState !is AuthUiState.Loading &&
                    profilePhotoUrl.isNotBlank() &&
                    (joinMode || (idPhotoUrl.isNotBlank() && idNumber.isNotBlank())),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("NEXT", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState is AuthUiState.Error) {
            Text(
                text = (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
fun VerificationItem(label: String, description: String? = null, previewUrl: String? = null, onPickImage: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
        if (description != null) {
            Text(text = description, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.DarkGray, shape = MaterialTheme.shapes.medium)
                .clickable { onPickImage() },
            contentAlignment = Alignment.Center
        ) {
            if (previewUrl.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Text("Tap to choose from device", color = Color.White)
                }
            } else {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AdditionalInfoStep(
    viewModel: AuthViewModel,
    context: android.content.Context,
    onSignUpSuccess: (String?) -> Unit,
) {
    val role by viewModel.role.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val latitude by viewModel.latitude.collectAsState()
    val longitude by viewModel.longitude.collectAsState()
    val vehicleMake by viewModel.vehicleMake.collectAsState()
    val vehicleModel by viewModel.vehicleModel.collectAsState()
    val numberPlate by viewModel.numberPlate.collectAsState()
    val vehiclePhotoUrl by viewModel.vehiclePhotoUrl.collectAsState()
    val institutionName by viewModel.institutionName.collectAsState()
    val experienceYears by viewModel.experienceYears.collectAsState()
    val certificatePhotoUrl by viewModel.certificatePhotoUrl.collectAsState()
    val garagePhotos by viewModel.garagePhotos.collectAsState()
    val address by viewModel.address.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val garageMode by viewModel.garageMode.collectAsState()
    val garageInviteCode by viewModel.garageInviteCode.collectAsState()
    val inviteVerified by viewModel.inviteVerified.collectAsState()

    val canComplete = when (role) {
        "driver" -> SignupValidation.validateDriverStep3(
            vehicleMake = vehicleMake,
            vehicleModel = vehicleModel,
            numberPlate = numberPlate,
            vehiclePhotoUrl = vehiclePhotoUrl,
        ) == null
        "mechanic" -> SignupValidation.validateMechanicStep3(
            garageMode = garageMode,
            inviteCode = garageInviteCode,
            institutionName = institutionName,
            experienceYears = experienceYears,
            garagePhotos = garagePhotos,
            latitude = latitude,
            longitude = longitude,
            address = address,
            locationName = locationName,
            inviteVerified = inviteVerified,
        ) == null
        else -> SignupValidation.validateProviderStep3(
            institutionName = institutionName,
            experienceYears = experienceYears,
            certificatePhotoUrl = certificatePhotoUrl,
            garagePhotos = garagePhotos,
            latitude = latitude,
            longitude = longitude,
            address = address,
            locationName = locationName,
            locationLabel = "shop",
        ) == null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 3: Additional Information",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (role) {
            "driver" -> DriverAdditionalInfo(viewModel, context)
            "mechanic" -> MechanicAdditionalInfo(viewModel, context)
            else -> PartsDealerAdditionalInfo(viewModel, context)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.completeProfile { onSignUpSuccess(role) } },
            enabled = uiState !is AuthUiState.Loading && canComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("COMPLETE SIGN UP", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState is AuthUiState.Error) {
            Text(
                text = (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverAdditionalInfo(viewModel: AuthViewModel, context: android.content.Context) {
    val vehicleMake by viewModel.vehicleMake.collectAsState()
    val vehicleModel by viewModel.vehicleModel.collectAsState()
    val numberPlate by viewModel.numberPlate.collectAsState()
    val vehiclePhotoUrl by viewModel.vehiclePhotoUrl.collectAsState()

    val makeOptions = remember { com.example.mototap.core.data.VehicleCatalogData.makeNames() }
    val modelOptions = remember(vehicleMake) {
        com.example.mototap.core.data.VehicleCatalogData.modelsForMake(vehicleMake)
    }

    val vehicleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "vehicle") }
    }

    SignupDropdownField(
        label = "Vehicle Make",
        placeholder = "Select make",
        value = vehicleMake,
        options = makeOptions,
        onSelected = { selected ->
            viewModel.setVehicleMake(selected)
            viewModel.vehicleModel.value = "" // reset model when make changes
        },
    )

    Spacer(modifier = Modifier.height(16.dp))

    SignupDropdownField(
        label = "Vehicle Model",
        placeholder = if (vehicleMake.isBlank()) "Select make first" else "Select model",
        value = vehicleModel,
        options = modelOptions,
        enabled = vehicleMake.isNotBlank(),
        onSelected = { viewModel.vehicleModel.value = it },
    )

    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = numberPlate,
        onValueChange = { viewModel.numberPlate.value = it },
        label = { Text("Number Plate", color = Color.Gray) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    )
    Spacer(modifier = Modifier.height(16.dp))
    VerificationItem(
        label = "Vehicle Photo",
        description = "Choose a photo from your device",
        previewUrl = vehiclePhotoUrl,
        onPickImage = {
            vehicleLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupDropdownField(
    label: String,
    placeholder: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label, color = Color.Gray) },
            placeholder = { Text(placeholder, color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.Gray,
                focusedBorderColor = MotoRed,
                unfocusedBorderColor = Color.Gray,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechanicAdditionalInfo(viewModel: AuthViewModel, context: android.content.Context) {
    val institutionName by viewModel.institutionName.collectAsState()
    val experienceYears by viewModel.experienceYears.collectAsState()
    val address by viewModel.address.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val garagePhotos by viewModel.garagePhotos.collectAsState()
    val latitude by viewModel.latitude.collectAsState()
    val longitude by viewModel.longitude.collectAsState()
    val garageMode by viewModel.garageMode.collectAsState()
    val joinMode = garageMode.trim() == "join"

    val garageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "garage") }
    }

    val localContext = LocalContext.current
    var showMap by remember { mutableStateOf(false) }

    // Automatic Address Reflection when Location changes
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
                val geocoder = Geocoder(localContext, Locale.getDefault())
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude!!, longitude!!, 1)
                val addressLine = addresses?.firstOrNull()?.getAddressLine(0)
                if (addressLine != null) {
                    viewModel.address.value = addressLine
                }
            } catch (_: Exception) {
                // Keep address empty so drivers never see raw coordinates.
            }
        }
    }
    
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(localContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationPermissionGranted) {
            showMap = true
        }
    }

    if (showMap && !joinMode) {
        val nairobi = LatLng(-1.286389, 36.817223)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else nairobi,
                15f
            )
        }

        AlertDialog(
            onDismissRequest = { showMap = false },
            confirmButton = {
                Button(onClick = {
                    viewModel.latitude.value = cameraPositionState.position.target.latitude
                    viewModel.longitude.value = cameraPositionState.position.target.longitude
                    showMap = false
                }) { Text("Confirm Location") }
            },
            title = { Text("Pin Garage Location") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = locationPermissionGranted,
                            mapType = MapType.NORMAL,
                            isTrafficEnabled = true
                        ),
                        uiSettings = MapUiSettings(
                            myLocationButtonEnabled = locationPermissionGranted,
                            zoomControlsEnabled = true,
                            compassEnabled = true
                        ),
                        onMapClick = { latLng ->
                            viewModel.latitude.value = latLng.latitude
                            viewModel.longitude.value = latLng.longitude
                        }
                    ) {
                        latitude?.let { lat ->
                            longitude?.let { lon ->
                                Marker(state = MarkerState(position = LatLng(lat, lon)))
                            }
                        }
                    }
                }
            }
        )
    }

    val verifiedGarageName by viewModel.verifiedGarageName.collectAsState()

    if (joinMode) {
        Text(
            text = if (verifiedGarageName.isNotBlank()) {
                "Joining $verifiedGarageName. The owner will approve your request."
            } else {
                "Joining an existing garage. The owner will approve your request."
            },
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        OutlinedTextField(
            value = institutionName,
            onValueChange = { viewModel.institutionName.value = it },
            label = { Text("Institution Name", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text("Experience", color = Color.White)
    val experienceOptions = listOf(
        "0-1" to "0-1 years",
        "1-3" to "1-3 years",
        "3-5" to "3-5 years",
        "5-10" to "5-10 years",
        "10+" to "10+ years",
    )
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        experienceOptions.forEach { (value, label) ->
            FilterChip(
                selected = experienceYears == value,
                onClick = { viewModel.experienceYears.value = value },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }

    // Garage-owner-only fields (address, map pin, front photo). Joining
    // mechanics inherit these from the garage they join, like the web flow.
    if (!joinMode) {
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (locationPermissionGranted) {
                    showMap = true
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (latitude != null) "Location Pinned" else "Pin Garage Location on Map")
        }

        if (latitude != null && longitude != null) {
            Text(
                text = "Pinned coordinates: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LocationNamePicker(
            locationName = locationName,
            onLocationNameChange = { viewModel.locationName.value = it },
            latitude = latitude,
            longitude = longitude,
            onPlacePicked = { place ->
                viewModel.locationName.value = place.name
                if (place.lat != null && place.lng != null) {
                    viewModel.latitude.value = place.lat
                    viewModel.longitude.value = place.lng
                }
                if (place.address.isNotBlank()) {
                    viewModel.address.value = place.address
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        VerificationItem(
            label = "Garage Front Photo",
            description = "Choose a photo from your device",
            previewUrl = garagePhotos.lastOrNull(),
            onPickImage = {
                garageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        val hours by viewModel.workingHours.collectAsState()
        WorkingHoursEditor(
            hours = hours,
            onHoursChange = { viewModel.workingHours.value = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsDealerAdditionalInfo(viewModel: AuthViewModel, context: android.content.Context) {
    val institutionName by viewModel.institutionName.collectAsState()
    val experienceYears by viewModel.experienceYears.collectAsState()
    val address by viewModel.address.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val garagePhotos by viewModel.garagePhotos.collectAsState()
    val certificatePhotoUrl by viewModel.certificatePhotoUrl.collectAsState()
    val latitude by viewModel.latitude.collectAsState()
    val longitude by viewModel.longitude.collectAsState()

    val licenseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "certificate") }
    }

    val shopLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadImage(context, it, "garage") }
    }

    val localContext = LocalContext.current
    var showMap by remember { mutableStateOf(false) }

    // Automatic Address Reflection when Location changes
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val geocoder = Geocoder(localContext, Locale.getDefault())
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude!!, longitude!!, 1)
                val addressLine = addresses?.firstOrNull()?.getAddressLine(0)
                if (addressLine != null) {
                    viewModel.address.value = addressLine
                }
            } catch (_: Exception) {
                // Keep address empty so drivers never see raw coordinates.
            }
        }
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(localContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationPermissionGranted) {
            showMap = true
        }
    }

    if (showMap) {
        val nairobi = LatLng(-1.286389, 36.817223)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else nairobi,
                15f
            )
        }

        AlertDialog(
            onDismissRequest = { showMap = false },
            confirmButton = {
                Button(onClick = {
                    viewModel.latitude.value = cameraPositionState.position.target.latitude
                    viewModel.longitude.value = cameraPositionState.position.target.longitude
                    showMap = false
                }) { Text("Confirm Location") }
            },
            title = { Text("Pin Shop Location") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            isMyLocationEnabled = locationPermissionGranted,
                            mapType = MapType.NORMAL,
                            isTrafficEnabled = true
                        ),
                        uiSettings = MapUiSettings(
                            myLocationButtonEnabled = locationPermissionGranted,
                            zoomControlsEnabled = true,
                            compassEnabled = true
                        ),
                        onMapClick = { latLng ->
                            viewModel.latitude.value = latLng.latitude
                            viewModel.longitude.value = latLng.longitude
                        }
                    ) {
                        latitude?.let { lat ->
                            longitude?.let { lon ->
                                Marker(state = MarkerState(position = LatLng(lat, lon)))
                            }
                        }
                    }
                }
            }
        )
    }

    OutlinedTextField(
        value = institutionName,
        onValueChange = { viewModel.institutionName.value = it },
        label = { Text("Shop / Business Name", color = Color.Gray) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    )

    Spacer(modifier = Modifier.height(16.dp))
    VerificationItem(
        label = "Business License Photo",
        description = "Choose a photo or PDF from your device",
        previewUrl = certificatePhotoUrl,
        onPickImage = { licenseLauncher.launch(arrayOf("image/*", "application/pdf")) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text("Years in Business", color = Color.White)
    val experienceOptions = listOf(
        "0-1" to "0-1 years",
        "1-3" to "1-3 years",
        "3-5" to "3-5 years",
        "5-10" to "5-10 years",
        "10+" to "10+ years",
    )
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        experienceOptions.forEach { (value, label) ->
            FilterChip(
                selected = experienceYears == value,
                onClick = { viewModel.experienceYears.value = value },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            if (locationPermissionGranted) {
                showMap = true
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (latitude != null) "Location Pinned" else "Pin Shop Location on Map")
    }

    if (latitude != null && longitude != null) {
        Text(
            text = "Pinned coordinates: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    LocationNamePicker(
        locationName = locationName,
        onLocationNameChange = { viewModel.locationName.value = it },
        latitude = latitude,
        longitude = longitude,
        onPlacePicked = { place ->
            viewModel.locationName.value = place.name
            if (place.lat != null && place.lng != null) {
                viewModel.latitude.value = place.lat
                viewModel.longitude.value = place.lng
            }
            if (place.address.isNotBlank()) {
                viewModel.address.value = place.address
            }
        },
    )

    Spacer(modifier = Modifier.height(16.dp))
    VerificationItem(
        label = "Shop Front Photo",
        description = "Choose a photo from your device",
        previewUrl = garagePhotos.lastOrNull(),
        onPickImage = {
            shopLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    val hours by viewModel.workingHours.collectAsState()
    WorkingHoursEditor(
        hours = hours,
        onHoursChange = { viewModel.workingHours.value = it },
    )
}
