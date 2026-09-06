package com.example.mototap.features.driver

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.util.JobAdditionalServices
import com.example.mototap.core.repository.AuthRepository
import com.example.mototap.core.repository.ChatRepository
import com.example.mototap.core.repository.JobRepository
import com.example.mototap.core.util.ChatIds
import com.example.mototap.core.util.estimateTowingTotal
import com.example.mototap.core.util.formatKsh
import com.example.mototap.core.util.getMechanicServicePrice
import com.example.mototap.core.util.isTowingService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

enum class DriverMarketplaceMode {
    MECHANICS,
    PARTS_DEALERS,
}

data class DriverUiState(
    val currentUserId: String? = null,
    val jobs: List<JobRequest> = emptyList(),
    val issueTypeInput: String = "",
    val descriptionInput: String = "",
    val locationInput: String = "",
    val priceInput: String = "1000",
    val infoMessage: String? = null,
    val mechanicPhoneNumber: String? = null,
    val availableMechanics: List<UserProfile> = emptyList(),
    val availablePartsDealers: List<UserProfile> = emptyList(),
    val marketplaceMode: DriverMarketplaceMode = DriverMarketplaceMode.MECHANICS,
    val isLocating: Boolean = false,
    val selectedMechanic: UserProfile? = null,
    val bookingSuccess: Boolean = false,
    val userLocation: LatLng? = null,
    val activeVehicleMake: String = "",
    val activeVehicleModel: String = "",
    val activeVehicleId: String = "",
)

class DriverHomeViewModel(
    private val authRepository: AuthRepository,
    private val jobRepository: JobRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverUiState())
    val uiState: StateFlow<DriverUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserId.collectLatest { userId ->
                _uiState.update { it.copy(currentUserId = userId) }
                
                if (userId == null) {
                    _uiState.update { it.copy(
                        availableMechanics = emptyList(),
                        availablePartsDealers = emptyList(),
                        jobs = emptyList(),
                        mechanicPhoneNumber = null
                    ) }
                    return@collectLatest
                }

                launch {
                    val profile = authRepository.getUserProfile(userId)
                    if (profile != null) {
                        val activeVehicle = profile.vehicles.firstOrNull()
                        val make = activeVehicle?.make?.takeIf { it.isNotBlank() } ?: profile.vehicleType
                        val model = activeVehicle?.model?.takeIf { it.isNotBlank() } ?: profile.vehicleModel
                        _uiState.update {
                            it.copy(
                                activeVehicleMake = make,
                                activeVehicleModel = model,
                                activeVehicleId = activeVehicle?.id ?: "",
                            )
                        }
                    }
                }

                launch {
                    authRepository.observeAllMechanics().collect { mechanics ->
                        _uiState.update { it.copy(availableMechanics = mechanics) }
                    }
                }

                launch {
                    authRepository.observeAllPartsDealers().collect { dealers ->
                        _uiState.update { it.copy(availablePartsDealers = dealers) }
                    }
                }

                launch {
                    jobRepository.observeDriverJobs(userId).collect { jobs ->
                        _uiState.update { it.copy(jobs = jobs) }
                        val activeJob = jobs.firstOrNull { it.mechanicId != null }
                        if (activeJob != null) {
                            val profile = authRepository.getUserProfile(activeJob.mechanicId!!)
                            _uiState.update { it.copy(mechanicPhoneNumber = profile?.phone) }
                        }
                    }
                }
            }
        }
    }

    fun onIssueChanged(value: String) = _uiState.update { it.copy(issueTypeInput = value) }
    fun onDescriptionChanged(value: String) = _uiState.update { it.copy(descriptionInput = value) }
    fun onLocationChanged(value: String) = _uiState.update { it.copy(locationInput = value) }
    fun onPriceChanged(value: String) = _uiState.update { it.copy(priceInput = value) }

    fun deleteRequest(jobId: String) = viewModelScope.launch { jobRepository.deleteJob(jobId) }

    fun addAdditionalServiceNote(jobId: String, text: String) {
        val userId = _uiState.value.currentUserId ?: return
        val job = _uiState.value.jobs.find { it.id == jobId } ?: return
        viewModelScope.launch {
            val profile = authRepository.getUserProfile(userId)
            val note = JobAdditionalServices.createNote(
                userId = userId,
                authorRole = JobAdditionalServices.authorRole(job, userId),
                authorName = profile?.name.orEmpty(),
                text = text,
            )
            val result = jobRepository.addAdditionalServiceNote(jobId, note)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(infoMessage = result.exceptionOrNull()?.message ?: "Failed to add additional service")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun quickRequest(context: Context, serviceName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, infoMessage = "Getting location...") }
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location: Location? = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                
                val locationLabel = if (location != null) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.getAddressLine(0) ?: "${location.latitude}, ${location.longitude}"
                } else "Nairobi, Kenya"

                val userId = _uiState.value.currentUserId ?: return@launch
                jobRepository.createJob(userId, serviceName, "Quick request", locationLabel, 1000L, null, null)
                _uiState.update { it.copy(isLocating = false, infoMessage = "Request sent!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLocating = false, infoMessage = "Location error") }
            }
        }
    }

    fun submitJob() {
        val state = _uiState.value
        val userId = state.currentUserId ?: return
        if (state.issueTypeInput.isBlank() || state.locationInput.isBlank()) {
            _uiState.update { it.copy(infoMessage = "Fill all fields") }
            return
        }

        viewModelScope.launch {
            jobRepository.createJob(
                userId, state.issueTypeInput, state.descriptionInput, state.locationInput,
                state.priceInput.toLongOrNull() ?: 0L, null, null
            )
            _uiState.update { it.copy(issueTypeInput = "", descriptionInput = "", locationInput = "", infoMessage = "Request sent") }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(infoMessage = null) }
    fun setSelectedMechanic(mechanic: UserProfile) = _uiState.update { it.copy(selectedMechanic = mechanic) }
    fun setUserLocation(location: LatLng) = _uiState.update { it.copy(userLocation = location) }

    fun setMarketplaceMode(mode: DriverMarketplaceMode) =
        _uiState.update { it.copy(marketplaceMode = mode) }

    @SuppressLint("MissingPermission")
    fun bookMechanic(
        context: Context,
        mechanic: UserProfile,
        serviceName: String,
        estimatedKm: Double? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, infoMessage = "Booking $serviceName...") }
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location: Location? = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                val locationLabel = location?.let {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    geocoder.getFromLocation(it.latitude, it.longitude, 1)?.firstOrNull()?.getAddressLine(0)
                } ?: "Location shared"

                val userId = _uiState.value.currentUserId ?: return@launch
                val state = _uiState.value

                // Listed price is optional (same as web). Unpriced services book at 0.
                // Towing still needs a per-km rate to estimate a total.
                val rateOrPrice = getMechanicServicePrice(
                    mechanic,
                    serviceName,
                    state.activeVehicleMake,
                    state.activeVehicleModel,
                )

                val towing = isTowingService(serviceName)
                var resolvedPrice = rateOrPrice ?: 0L
                var description = "Direct booking"
                if (towing) {
                    if (rateOrPrice == null || rateOrPrice <= 0L) {
                        _uiState.update {
                            it.copy(
                                isLocating = false,
                                infoMessage = "Unable to estimate towing total. Check the rate and distance.",
                            )
                        }
                        return@launch
                    }
                    val km = estimatedKm
                    val total = km?.let { estimateTowingTotal(rateOrPrice, it) }
                    if (total == null) {
                        _uiState.update {
                            it.copy(
                                isLocating = false,
                                infoMessage = "Enter estimated towing distance (km).",
                            )
                        }
                        return@launch
                    }
                    resolvedPrice = total
                    description = "Towing ~$km km @ KSh ${formatKsh(rateOrPrice)}/km"
                }

                // Canonical sorted room id (same as website) so chat history stays shared.
                val inquiryId = ChatIds.roomId(userId, mechanic.id)
                val result = jobRepository.createJob(
                    driverId = userId, 
                    issueType = serviceName, 
                    description = description, 
                    locationLabel = locationLabel, 
                    suggestedPrice = resolvedPrice, 
                    mechanicId = mechanic.id, 
                    jobId = inquiryId,
                    garageId = mechanic.garageId,
                    vehicleMake = state.activeVehicleMake,
                    vehicleModel = state.activeVehicleModel,
                    vehicleId = state.activeVehicleId,
                )

                if (result.isSuccess) {
                    _uiState.update { it.copy(isLocating = false, infoMessage = "Booked!", bookingSuccess = true) }
                    _navigationEvent.emit("job_tracking")
                } else {
                    _uiState.update { it.copy(isLocating = false, infoMessage = "Failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLocating = false, infoMessage = "Error") }
            }
        }
    }

    // Parts dealers reuse the same chat-room mechanism as mechanics; the
    // "mechanicId" field on the inquiry job simply holds the provider's id.
    fun openChatWithProvider(providerId: String, onJobIdFound: (String) -> Unit) =
        openChatWithMechanic(providerId, onJobIdFound)

    fun openChatWithMechanic(mechanicId: String, onJobIdFound: (String) -> Unit) {
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            val roomId = ChatIds.roomId(userId, mechanicId)
            val myProfile = authRepository.getUserProfile(userId)
            val partnerProfile = authRepository.getUserProfile(mechanicId)
            val names = buildMap {
                myProfile?.name?.takeIf { it.isNotBlank() }?.let { put(userId, it) }
                partnerProfile?.name?.takeIf { it.isNotBlank() }?.let { put(mechanicId, it) }
            }
            chatRepository.ensureConversation(userId, mechanicId, names)
            Log.d("DriverVM", "Opening chat room: $roomId")
            onJobIdFound(roomId)
        }
    }
}

class DriverHomeViewModelFactory(
    private val authRepository: AuthRepository,
    private val jobRepository: JobRepository,
    private val chatRepository: ChatRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DriverHomeViewModel(authRepository, jobRepository, chatRepository) as T
}
