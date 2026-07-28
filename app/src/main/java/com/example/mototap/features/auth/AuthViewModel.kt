package com.example.mototap.features.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.model.UserRole
import com.example.mototap.core.repository.AuthRepository
import com.example.mototap.core.repository.GarageRepository
import com.example.mototap.core.util.SignupValidation
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val role: String?, val resumeSignUp: Boolean = false) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

enum class SignUpStep {
    BASIC_INFO,
    IDENTITY_VERIFICATION,
    ADDITIONAL_INFO,
    WORKING_HOURS,
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val garageRepository: GarageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _signUpStep = MutableStateFlow(SignUpStep.BASIC_INFO)
    val signUpStep: StateFlow<SignUpStep> = _signUpStep.asStateFlow()

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val name = MutableStateFlow("")
    val phoneNumber = MutableStateFlow("")
    val role = MutableStateFlow("driver") // default to driver

    // Step 2 & 3 Fields
    val profilePhotoUrl = MutableStateFlow("")
    val idNumber = MutableStateFlow("")
    val idPhotoUrl = MutableStateFlow("")
    
    // Driver specific
    val vehicleType = MutableStateFlow("") // kept synced to make for compatibility
    val vehicleMake = MutableStateFlow("")
    val vehicleModel = MutableStateFlow("")
    val numberPlate = MutableStateFlow("")
    val vehiclePhotoUrl = MutableStateFlow("")

     // Mechanic specific
     val certificateNumber = MutableStateFlow("")
     val certificatePhotoUrl = MutableStateFlow("")
     val institutionName = MutableStateFlow("")
     val experienceYears = MutableStateFlow("")
     val latitude = MutableStateFlow<Double?>(null)
     val longitude = MutableStateFlow<Double?>(null)
     val address = MutableStateFlow("")
     val garagePhotos = MutableStateFlow<List<String>>(emptyList())
     val availableServices = MutableStateFlow<List<String>>(emptyList())

     // Garage onboarding: "own" (manage a garage) vs "join" (staff via invite)
     val garageMode = MutableStateFlow("own")
     val garageInviteCode = MutableStateFlow("")
     val inviteVerified = MutableStateFlow(false)
     val verifiedGarageName = MutableStateFlow("")
     val workingHours = MutableStateFlow(com.example.mototap.core.util.defaultWorkingHours())
     private val _needsWorkingHoursOnly = MutableStateFlow(false)
     val needsWorkingHoursOnly: StateFlow<Boolean> = _needsWorkingHoursOnly.asStateFlow()

    fun clearVerifiedInvite() {
        inviteVerified.value = false
        verifiedGarageName.value = ""
    }

    fun verifyGarageInvite(onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val code = garageInviteCode.value
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val lookup = runCatching {
                garageRepository.lookupInvite(code)
            }.getOrElse {
                _uiState.value = AuthUiState.Error(it.message ?: "Could not verify invite code.")
                onDone(false, it.message ?: "Could not verify invite code.")
                return@launch
            }
            if (lookup == null) {
                clearVerifiedInvite()
                _uiState.value = AuthUiState.Error("Invalid or expired garage invite code.")
                onDone(false, "Invalid or expired garage invite code.")
                return@launch
            }
            garageInviteCode.value = lookup.inviteCode
            inviteVerified.value = true
            verifiedGarageName.value = lookup.garage.name.ifBlank { "Garage" }
            _uiState.value = AuthUiState.Idle
            onDone(true, verifiedGarageName.value)
        }
    }

    fun setVehicleMake(make: String) {
        vehicleMake.value = make
        vehicleType.value = make // keep legacy field in sync
    }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    fun isNameValid(name: String): Boolean = SignupValidation.isNameValid(name)

    fun getSignupResumeStep(profile: UserProfile?): SignUpStep? {
        if (profile == null) return null
        if (!profile.onboardingComplete) {
            val completedStep = profile.onboardingStep ?: 0
            return when {
                completedStep >= 2 -> SignUpStep.ADDITIONAL_INFO
                completedStep >= 1 -> SignUpStep.IDENTITY_VERIFICATION
                else -> SignUpStep.BASIC_INFO
            }
        }
        if (com.example.mototap.core.util.needsWorkingHoursUpdate(
                profile.role.name,
                profile.garageRole,
                profile.workingHours,
            )
        ) {
            return SignUpStep.WORKING_HOURS
        }
        return null
    }

    fun loadProfileIntoSignupState(profile: UserProfile) {
        name.value = profile.name
        email.value = profile.email
        phoneNumber.value = profile.phone
        role.value = when (profile.role) {
            UserRole.MECHANIC -> "mechanic"
            UserRole.PARTS_DEALER -> "parts_dealer"
            else -> "driver"
        }
        profilePhotoUrl.value = profile.profilePhotoUrl
        idNumber.value = profile.idNumber
        idPhotoUrl.value = profile.idPhotoUrl
        vehicleType.value = profile.vehicleType
        vehicleMake.value = profile.vehicleType // vehicleType stores the make
        vehicleModel.value = profile.vehicleModel
        numberPlate.value = profile.numberPlate
        vehiclePhotoUrl.value = profile.vehiclePhotoUrl
        certificateNumber.value = profile.certificateNumber
        certificatePhotoUrl.value = profile.certificatePhotoUrl
        institutionName.value = profile.institutionName
        experienceYears.value = profile.experienceYears
        latitude.value = profile.latitude
        longitude.value = profile.longitude
        address.value = profile.address
        garagePhotos.value = profile.garagePhotos
        availableServices.value = profile.availableServices
        workingHours.value = profile.workingHours
            ?: com.example.mototap.core.util.defaultWorkingHours()
        _needsWorkingHoursOnly.value = profile.onboardingComplete &&
            com.example.mototap.core.util.needsWorkingHoursUpdate(
                profile.role.name,
                profile.garageRole,
                profile.workingHours,
            )
        _userProfile.value = profile
    }

    fun resumeIncompleteSignupIfNeeded(onResume: (SignUpStep) -> Unit): Boolean {
        val profile = _userProfile.value ?: return false
        val step = getSignupResumeStep(profile) ?: return false
        loadProfileIntoSignupState(profile)
        _signUpStep.value = step
        onResume(step)
        return true
    }

    fun signIn() {
        viewModelScope.launch {
            Log.d("AuthViewModel", "signIn started for ${email.value}")
            _uiState.value = AuthUiState.Loading
            
            val result = authRepository.signIn(email.value, password.value)
            if (result.isSuccess) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                Log.d("AuthViewModel", "signIn success, fetching role for $userId with timeout")
                
                // Increased timeout to 15s to be more robust on slower networks
                val userRole = withTimeoutOrNull(15000) {
                    userId?.let { authRepository.getUserRole(it) }
                }
                val profile = userId?.let { authRepository.getUserProfile(it) }
                _userProfile.value = profile

                val resumeStep = getSignupResumeStep(profile)
                if (resumeStep != null && profile != null) {
                    loadProfileIntoSignupState(profile)
                    _signUpStep.value = resumeStep
                }

                Log.d("AuthViewModel", "role result: $userRole, resumeStep: $resumeStep")
                _uiState.value = AuthUiState.Success(userRole, resumeStep != null)
            } else {
                val rawError = result.exceptionOrNull()?.message ?: ""
                val errorMsg = if (rawError.contains("credential", ignoreCase = true) || 
                                 rawError.contains("password", ignoreCase = true) ||
                                 rawError.contains("user", ignoreCase = true)) {
                    "Wrong Email or Password."
                } else {
                    "Login failed. Please check your connection."
                }
                Log.e("AuthViewModel", "signIn failed: $rawError")
                _uiState.value = AuthUiState.Error(errorMsg)
            }
        }
    }

    fun sendPasswordReset(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.sendPasswordReset(email.value)
            if (result.isSuccess) {
                _uiState.value = AuthUiState.Idle
                onSuccess()
            } else {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Unable to send reset email."
                )
            }
        }
    }

    fun signUp() {
        if (name.value.trim().split("\\s+".toRegex()).size < 2) {
            _uiState.value = AuthUiState.Error("Please provide at least two names.")
            return
        }

        viewModelScope.launch {
            Log.d("AuthViewModel", "signUp started for ${email.value}")
            _uiState.value = AuthUiState.Loading
            
            try {
                // Increased timeout to 30 seconds to accommodate double Firebase operations (Auth + Firestore)
                val result = withTimeoutOrNull(30000) {
                    authRepository.signUp(
                        email.value, 
                        password.value, 
                        name.value, 
                        role.value,
                        phoneNumber.value // Now always passing phoneNumber for both roles
                    )
                }

                if (result == null) {
                    val errorMsg = "Sign up timed out. Please check your connection and try again."
                    Log.e("AuthViewModel", errorMsg)
                    _uiState.value = AuthUiState.Error(errorMsg)
                } else if (result.isSuccess) {
                    Log.d("AuthViewModel", "signUp success, navigating with role ${role.value}")
                    if (role.value == "mechanic" && garageMode.value.trim() == "join") {
                        val lookup = garageRepository.lookupInvite(garageInviteCode.value)
                        if (lookup == null) {
                            clearVerifiedInvite()
                            _uiState.value = AuthUiState.Error("Invalid or expired garage invite code.")
                            return@launch
                        }
                        garageInviteCode.value = lookup.inviteCode
                        inviteVerified.value = true
                        verifiedGarageName.value = lookup.garage.name.ifBlank { "Garage" }
                    }
                    _uiState.value = AuthUiState.Success(role.value)
                } else {
                    val rawError = result.exceptionOrNull()?.message ?: ""
                    val errorMsg = when {
                        rawError.contains("email", ignoreCase = true) && rawError.contains("already", ignoreCase = true) -> 
                            "This email is already registered."
                        rawError.contains("network", ignoreCase = true) -> 
                            "Network error. Please check your connection."
                        else -> "Sign up failed: $rawError"
                    }
                    Log.e("AuthViewModel", "signUp failed: $rawError")
                    _uiState.value = AuthUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "signUp exception: ${e.message}")
                _uiState.value = AuthUiState.Error("An unexpected error occurred during sign up.")
            }
        }
    }

    fun checkExistingSession(onRoleFetched: (String?, Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                Log.d("AuthViewModel", "Existing session found for $userId, fetching role...")
                val profile = withTimeoutOrNull(15000) {
                    authRepository.getUserProfile(userId)
                }
                _userProfile.value = profile
                val userRole = profile?.role?.name?.lowercase()
                    ?: withTimeoutOrNull(15000) { authRepository.getUserRole(userId) }

                val resumeStep = getSignupResumeStep(profile)
                if (resumeStep != null && profile != null) {
                    loadProfileIntoSignupState(profile)
                    _signUpStep.value = resumeStep
                }

                Log.d("AuthViewModel", "Session role: $userRole, resume: ${resumeStep != null}")
                onRoleFetched(userRole, resumeStep != null)
            }
        }
    }
    
    fun fetchUserProfile() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                // Increased timeout to 15s
                val profile = withTimeoutOrNull(15000) {
                    authRepository.getUserProfile(userId)
                }
                _userProfile.value = profile
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Reset state first to clear any role-specific data in ViewModels
                _userProfile.value = null
                _uiState.value = AuthUiState.Idle
                
                authRepository.signOut()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during logout: ${e.message}")
            }
            onSuccess()
        }
    }

    fun deleteAccount(currentPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (currentPassword.isBlank()) {
                _uiState.value = AuthUiState.Error("Enter your current password to delete your account.")
                return@launch
            }

            Log.d("AuthViewModel", "deleteAccount started")
            _uiState.value = AuthUiState.Loading
            
            val result = withTimeoutOrNull(20000) {
                authRepository.deleteAccount(currentPassword)
            }

            if (result != null && result.isSuccess) {
                Log.d("AuthViewModel", "deleteAccount success")
                _userProfile.value = null
                _uiState.value = AuthUiState.Idle
                onSuccess()
            } else {
                val errorMsg = result?.exceptionOrNull()?.message ?: "Account deletion timed out. Please try again."
                Log.e("AuthViewModel", "deleteAccount failed: $errorMsg")
                _uiState.value = AuthUiState.Error(errorMsg)
            }
        }
    }

    fun nextStep() {
        val next = when (_signUpStep.value) {
            SignUpStep.BASIC_INFO -> SignUpStep.IDENTITY_VERIFICATION
            SignUpStep.IDENTITY_VERIFICATION -> SignUpStep.ADDITIONAL_INFO
            SignUpStep.ADDITIONAL_INFO -> SignUpStep.ADDITIONAL_INFO
            SignUpStep.WORKING_HOURS -> SignUpStep.WORKING_HOURS
        }
        _signUpStep.value = next
    }

    fun previousStep() {
        val prev = when (_signUpStep.value) {
            SignUpStep.BASIC_INFO -> SignUpStep.BASIC_INFO
            SignUpStep.IDENTITY_VERIFICATION -> SignUpStep.BASIC_INFO
            SignUpStep.ADDITIONAL_INFO -> SignUpStep.IDENTITY_VERIFICATION
            SignUpStep.WORKING_HOURS -> SignUpStep.WORKING_HOURS
        }
        _signUpStep.value = prev
    }

    fun uploadImage(context: Context, uri: android.net.Uri, type: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.uploadSignupImage(userId, type, uri, context)
            if (result.isSuccess) {
                val url = result.getOrNull() ?: ""
                when (type) {
                    "profile" -> profilePhotoUrl.value = url
                    "id_front" -> idPhotoUrl.value = url
                    "vehicle" -> vehiclePhotoUrl.value = url
                    "certificate" -> certificatePhotoUrl.value = url
                    "garage" -> {
                        val current = garagePhotos.value.toMutableList()
                        current.add(url)
                        garagePhotos.value = current
                    }
                }
                _uiState.value = AuthUiState.Idle
            } else {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Upload failed")
            }
        }
    }

    fun saveIdentityStep(onSuccess: () -> Unit) {
        if (profilePhotoUrl.value.isBlank()) {
            _uiState.value = AuthUiState.Error("Please upload a profile photo.")
            return
        }
        val joinMode = role.value == "mechanic" && garageMode.value.trim() == "join"
        if (!joinMode) {
            if (idPhotoUrl.value.isBlank()) {
                val docLabel = when (role.value) {
                    "mechanic" -> "Certificate of Corporation"
                    "parts_dealer" -> "business license photo"
                    else -> "ID document photo"
                }
                _uiState.value = AuthUiState.Error("Please upload your $docLabel.")
                return
            }
            if (idNumber.value.isBlank()) {
                val numberLabel = when (role.value) {
                    "mechanic" -> "Certificate of Corporation number"
                    "parts_dealer" -> "business license number"
                    else -> "ID number"
                }
                _uiState.value = AuthUiState.Error("Please enter your $numberLabel.")
                return
            }
        } else if (!inviteVerified.value) {
            _uiState.value = AuthUiState.Error("Verify your garage invite code before continuing.")
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.completeSignupStep2(
                userId = userId,
                profilePhotoUrl = profilePhotoUrl.value,
                idPhotoUrl = if (joinMode) "" else idPhotoUrl.value,
                idNumber = if (joinMode) "" else idNumber.value,
                role = role.value,
            )
            if (result.isSuccess) {
                _uiState.value = AuthUiState.Idle
                onSuccess()
            } else {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to save identity details"
                )
            }
        }
    }

    fun completeProfile(onSuccess: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val validationError = when (role.value) {
            "mechanic" -> SignupValidation.validateMechanicStep3(
                garageMode = garageMode.value,
                inviteCode = garageInviteCode.value,
                institutionName = institutionName.value,
                experienceYears = experienceYears.value,
                garagePhotos = garagePhotos.value,
                latitude = latitude.value,
                longitude = longitude.value,
                address = address.value,
                inviteVerified = inviteVerified.value,
                workingHours = if (garageMode.value.trim() == "join") null else workingHours.value,
            )
            "parts_dealer" -> SignupValidation.validateProviderStep3(
                institutionName = institutionName.value,
                experienceYears = experienceYears.value,
                certificatePhotoUrl = certificatePhotoUrl.value,
                garagePhotos = garagePhotos.value,
                latitude = latitude.value,
                longitude = longitude.value,
                address = address.value,
                locationLabel = "shop",
                workingHours = workingHours.value,
            )
            else -> SignupValidation.validateDriverStep3(
                vehicleMake = vehicleMake.value,
                vehicleModel = vehicleModel.value,
                numberPlate = numberPlate.value,
                vehiclePhotoUrl = vehiclePhotoUrl.value,
            )
        }

        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = if (role.value == "mechanic") {
                val joinMode = garageMode.value.trim() == "join"
                authRepository.completeSignupStep3Mechanic(
                    userId = userId,
                    institutionName = institutionName.value,
                    experienceYears = experienceYears.value,
                    certificatePhotoUrl = "",
                    garagePhotos = garagePhotos.value,
                    latitude = if (joinMode) 0.0 else latitude.value!!,
                    longitude = if (joinMode) 0.0 else longitude.value!!,
                    address = address.value,
                    garageMode = garageMode.value,
                    inviteCode = garageInviteCode.value,
                    workingHours = if (joinMode) null else workingHours.value,
                )
            } else if (role.value == "parts_dealer") {
                authRepository.completeSignupStep3PartsDealer(
                    userId = userId,
                    institutionName = institutionName.value,
                    experienceYears = experienceYears.value,
                    certificatePhotoUrl = certificatePhotoUrl.value,
                    garagePhotos = garagePhotos.value,
                    latitude = latitude.value!!,
                    longitude = longitude.value!!,
                    address = address.value,
                    workingHours = workingHours.value,
                )
            } else {
                authRepository.completeSignupStep3Driver(
                    userId = userId,
                    vehicleMake = vehicleMake.value,
                    vehicleModel = vehicleModel.value,
                    numberPlate = numberPlate.value,
                    vehiclePhotoUrl = vehiclePhotoUrl.value,
                )
            }

            if (result.isSuccess) {
                fetchUserProfile()
                _uiState.value = AuthUiState.Success(role.value)
                onSuccess()
            } else {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to complete sign up"
                )
            }
        }
    }

    fun saveWorkingHours(onSuccess: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val hoursError = com.example.mototap.core.util.validateWorkingHours(workingHours.value)
        if (hoursError != null) {
            _uiState.value = AuthUiState.Error(hoursError)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.saveWorkingHours(userId, workingHours.value)
            if (result.isSuccess) {
                _needsWorkingHoursOnly.value = false
                fetchUserProfile()
                _uiState.value = AuthUiState.Success(role.value)
                onSuccess()
            } else {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to save working hours"
                )
            }
        }
    }

    fun updateVehicle(updatedVehicle: com.example.mototap.core.model.VehicleProfile) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentProfile = _userProfile.value ?: return

        viewModelScope.launch {
            val updatedVehicles = currentProfile.vehicles.toMutableList()
            val index = updatedVehicles.indexOfFirst { it.id == updatedVehicle.id }
            
            if (index != -1) {
                updatedVehicles[index] = updatedVehicle
            } else {
                updatedVehicles.add(updatedVehicle.copy(id = UUID.randomUUID().toString()))
            }

            val newProfile = currentProfile.copy(vehicles = updatedVehicles)
            val result = authRepository.updateUserProfile(newProfile)
            
            if (result.isSuccess) {
                _userProfile.value = newProfile
            }
        }
    }

    fun deleteVehicle(vehicleId: String) {
        val currentProfile = _userProfile.value ?: return
        viewModelScope.launch {
            val updatedVehicles = currentProfile.vehicles.filter { it.id != vehicleId }
            val newProfile = currentProfile.copy(vehicles = updatedVehicles)
            val result = authRepository.updateUserProfile(newProfile)
            if (result.isSuccess) {
                _userProfile.value = newProfile
            }
        }
    }

    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        Log.d("AuthViewModel", "resetState called")
        _uiState.value = AuthUiState.Idle
        _signUpStep.value = SignUpStep.BASIC_INFO
    }
}
