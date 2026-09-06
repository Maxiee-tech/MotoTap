package com.example.mototap.features.mechanic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mototap.core.model.GarageMember
import com.example.mototap.core.model.GarageMemberRole
import com.example.mototap.core.model.GarageMemberStatus
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus
import com.example.mototap.core.repository.AuthRepository
import com.example.mototap.core.repository.GarageRepository
import com.example.mototap.core.repository.JobRepository
import com.example.mototap.core.util.DEFAULT_PRICE_KEY
import com.example.mototap.core.util.buildServicePricesPayload
import com.example.mototap.core.util.buildVehiclePriceKey
import com.example.mototap.core.util.getDefaultServicePrice
import com.example.mototap.core.util.isTowingService
import com.example.mototap.core.util.toFlatServicePrices
import com.example.mototap.core.util.vehicleOnlyPriceMap
import com.example.mototap.core.util.JobAdditionalServices
import com.example.mototap.core.data.VehicleCatalogData
import com.example.mototap.core.util.garageServicesVehicle
import com.example.mototap.core.util.normalizeVehicleTypes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class MechanicUiState(
    val openJobs: List<JobRequest> = emptyList(),
    val ongoingJobs: List<JobRequest> = emptyList(),
    val completedJobs: List<JobRequest> = emptyList(),
    val garageJobs: List<JobRequest> = emptyList(),
    val selectedSkills: List<String> = emptyList(),
    val servicePrices: Map<String, Long> = emptyMap(),
    /** Towing (and any) make:model -> KSh/km rates for personal catalog. */
    val serviceVehiclePrices: Map<String, Map<String, Long>> = emptyMap(),
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
    val isSavingServices: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Garage org state
    val garageId: String = "",
    val garageRole: String = "",
    val inviteCode: String = "",
    val pendingJoinRequests: List<GarageMember> = emptyList(),
    val garageMemberStatus: String = "",
    val garageSelectedSkills: List<String> = emptyList(),
    // Flat (default) garage prices for editing: service -> KSh (non-towing).
    val garageServicePrices: Map<String, Long> = emptyMap(),
    /** Towing make:model -> KSh/km rates for the garage catalog. */
    val garageVehiclePrices: Map<String, Map<String, Long>> = emptyMap(),
    val garageVehicleTypes: List<String> = emptyList(),
    val garagePhotos: List<String> = emptyList(),
    val locationName: String = "",
    val isSavingGarageCatalog: Boolean = false,
    val isSavingGaragePhoto: Boolean = false,
    val isSavingLocationName: Boolean = false,
) {
    val isGarageMember: Boolean get() = garageId.isNotBlank()
    val isGarageOwner: Boolean get() = garageRole == GarageMemberRole.OWNER
}

class MechanicDashboardViewModel(
    private val authRepository: AuthRepository,
    private val jobRepository: JobRepository,
    private val garageRepository: GarageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MechanicUiState())
    val uiState: StateFlow<MechanicUiState> = _uiState.asStateFlow()

    private val _selectedSkills = MutableStateFlow<List<String>>(emptyList())
    private val _servicePrices = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _serviceVehiclePrices = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    private val _garageVehicleTypes = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            authRepository.currentUserId.collectLatest { userId ->
                if (userId != null) {
                    val profile = authRepository.getUserProfile(userId)
                    val combinedSkills = ((profile?.skills ?: emptyList()) + (profile?.availableServices ?: emptyList()))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                    val nested = profile?.servicePrices ?: emptyMap()
                    val prices = toFlatServicePrices(nested)
                    val vehiclePrices = nested.mapValues { (_, entry) -> vehicleOnlyPriceMap(entry) }
                        .filterValues { it.isNotEmpty() }
                    _selectedSkills.value = combinedSkills
                    _servicePrices.value = prices
                    _serviceVehiclePrices.value = vehiclePrices
                    _uiState.value = _uiState.value.copy(
                        selectedSkills = combinedSkills,
                        servicePrices = prices,
                        serviceVehiclePrices = vehiclePrices,
                        latitude = profile?.latitude,
                        longitude = profile?.longitude,
                        locationName = profile?.locationName.orEmpty(),
                        garageId = profile?.garageId ?: "",
                        garageRole = profile?.garageRole ?: "",
                    )

                    // Lazy migration: a completed solo mechanic without a garage gets one.
                    val role = profile?.role?.name?.lowercase()
                    if (profile != null && role == "mechanic" &&
                        profile.onboardingComplete && profile.garageId.isBlank()
                    ) {
                        val result = garageRepository.ensureOwnerGarage(userId, profile)
                        result.getOrNull()?.let { garage ->
                            _uiState.value = _uiState.value.copy(
                                garageId = garage.id,
                                garageRole = GarageMemberRole.OWNER,
                            )
                        }
                    }

                    loadGarageState(_uiState.value.garageId)

                    coroutineScope {
                        val garageIdForJobs = _uiState.value.garageId
                        launch {
                            if (garageIdForJobs.isBlank()) {
                                _uiState.value = _uiState.value.copy(garageJobs = emptyList())
                                return@launch
                            }
                            jobRepository.observeGarageJobs(garageIdForJobs).collect { garageJobs ->
                                _uiState.value = _uiState.value.copy(garageJobs = garageJobs)
                            }
                        }

                        combine(
                            jobRepository.observeOpenJobs(),
                            jobRepository.observeMechanicJobs(userId),
                            _selectedSkills,
                            _garageVehicleTypes,
                        ) { openJobs, myJobs, skills, vehicleTypes ->
                            val newRequests = openJobs.filter { job ->
                                job.status == JobStatus.REQUESTED &&
                                    garageServicesVehicle(
                                        vehicleTypes,
                                        job.vehicleMake,
                                        job.vehicleModel,
                                    ) &&
                                    (skills.isEmpty() || skills.any {
                                        it.equals(job.issueType, ignoreCase = true)
                                    })
                            }

                            val ongoing = myJobs.filter {
                                it.status == JobStatus.ASSIGNED || it.status == JobStatus.IN_PROGRESS
                            }

                            val completed = myJobs.filter {
                                it.status == JobStatus.COMPLETED || it.status == JobStatus.PAID || it.status == JobStatus.CLOSED
                            }

                            Triple(newRequests, ongoing, completed)
                        }.collect { (newRequests, ongoing, completed) ->
                            _uiState.value = _uiState.value.copy(
                                openJobs = newRequests,
                                ongoingJobs = ongoing,
                                completedJobs = completed,
                                selectedSkills = _selectedSkills.value,
                                servicePrices = _servicePrices.value,
                                serviceVehiclePrices = _serviceVehiclePrices.value,
                            )
                        }
                    }
                } else {
                    _uiState.value = MechanicUiState()
                    _selectedSkills.value = emptyList()
                    _servicePrices.value = emptyMap()
                    _serviceVehiclePrices.value = emptyMap()
                    _garageVehicleTypes.value = emptyList()
                }
            }
        }
    }

    private suspend fun loadGarageState(garageId: String) {
        if (garageId.isBlank()) return
        val garage = garageRepository.getGarage(garageId) ?: return
        val members = garageRepository.listMembers(garageId)
        val pending = members.filter { it.status == GarageMemberStatus.PENDING }
        val self = members.firstOrNull { it.uid == FirebaseAuth.getInstance().currentUser?.uid }
        if (self?.status == GarageMemberStatus.ACTIVE &&
            _uiState.value.garageRole == GarageMemberRole.MECHANIC
        ) {
            // Joiner self-upgrades to APPROVED once owner activates membership.
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                runCatching {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .update(
                            mapOf(
                                "status" to "APPROVED",
                                "garageMemberStatus" to GarageMemberStatus.ACTIVE,
                            )
                        ).await()
                }
            }
        }
        val flatPrices = linkedMapOf<String, Long>()
        val vehiclePrices = linkedMapOf<String, Map<String, Long>>()
        garage.servicePrices.forEach { (service, inner) ->
            getDefaultServicePrice(inner)?.let { flatPrices[service] = it }
            val vehicles = vehicleOnlyPriceMap(inner)
            if (vehicles.isNotEmpty()) vehiclePrices[service] = vehicles
        }
        _uiState.value = _uiState.value.copy(
            inviteCode = garage.inviteCode,
            pendingJoinRequests = pending,
            garageMemberStatus = self?.status.orEmpty(),
            garageSelectedSkills = garage.skills,
            garageServicePrices = flatPrices,
            garageVehiclePrices = vehiclePrices,
            garageVehicleTypes = garage.vehicleTypes,
            garagePhotos = garage.garagePhotos,
            locationName = garage.locationName.ifBlank { _uiState.value.locationName },
        )
        _garageVehicleTypes.value = garage.vehicleTypes
    }

    fun approveJoinRequest(memberId: String) {
        val garageId = _uiState.value.garageId
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (garageId.isBlank() || memberId.isBlank()) return
        viewModelScope.launch {
            val result = garageRepository.approveMember(garageId, ownerId, memberId)
            if (result.isSuccess) {
                loadGarageState(garageId)
                _uiState.value = _uiState.value.copy(infoMessage = "Mechanic approved.")
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = result.exceptionOrNull()?.message ?: "Could not approve request."
                )
            }
        }
    }

    fun rejectJoinRequest(memberId: String) {
        val garageId = _uiState.value.garageId
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (garageId.isBlank() || memberId.isBlank()) return
        viewModelScope.launch {
            val result = garageRepository.rejectMember(garageId, ownerId, memberId)
            if (result.isSuccess) {
                loadGarageState(garageId)
                _uiState.value = _uiState.value.copy(infoMessage = "Join request rejected.")
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = result.exceptionOrNull()?.message ?: "Could not reject request."
                )
            }
        }
    }

    fun toggleSkill(skill: String) {
        val currentSkills = _selectedSkills.value.toMutableList()
        val currentPrices = _servicePrices.value.toMutableMap()
        val currentVehicle = _serviceVehiclePrices.value.toMutableMap()
        val existing = currentSkills.firstOrNull { it.equals(skill, ignoreCase = true) }
        if (existing != null) {
            currentSkills.removeAll { it.equals(skill, ignoreCase = true) }
            currentPrices.keys.filter { it.equals(skill, ignoreCase = true) }.forEach { currentPrices.remove(it) }
            currentVehicle.keys.filter { it.equals(skill, ignoreCase = true) }.forEach { currentVehicle.remove(it) }
        } else {
            currentSkills.add(skill)
        }

        _selectedSkills.value = currentSkills
        _servicePrices.value = currentPrices
        _serviceVehiclePrices.value = currentVehicle
        _uiState.value = _uiState.value.copy(
            selectedSkills = currentSkills,
            servicePrices = currentPrices,
            serviceVehiclePrices = currentVehicle,
        )
    }

    fun setServicePrice(serviceName: String, price: Long?) {
        val currentPrices = _servicePrices.value.toMutableMap()
        if (price != null && price > 0) {
            currentPrices[serviceName] = price
        } else {
            currentPrices.remove(serviceName)
        }
        _servicePrices.value = currentPrices
        _uiState.value = _uiState.value.copy(servicePrices = currentPrices)
    }

    fun setServiceVehicleRate(serviceName: String, make: String, model: String, price: Long?) {
        val key = buildVehiclePriceKey(make, model)
        if (key.isBlank()) return
        val current = _serviceVehiclePrices.value.toMutableMap()
        val entry = current[serviceName].orEmpty().toMutableMap()
        if (price != null && price > 0) entry[key] = price else entry.remove(key)
        if (entry.isEmpty()) current.remove(serviceName) else current[serviceName] = entry
        _serviceVehiclePrices.value = current
        _uiState.value = _uiState.value.copy(serviceVehiclePrices = current)
    }

    fun removeServiceVehicleRate(serviceName: String, make: String, model: String) {
        setServiceVehicleRate(serviceName, make, model, null)
    }

    fun saveMechanicServices(onSuccess: () -> Unit = {}) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val skills = _selectedSkills.value
        val prices = _servicePrices.value
        val vehiclePrices = _serviceVehiclePrices.value
        val garageMember = _uiState.value.isGarageMember

        if (skills.isEmpty()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Select at least one service to continue")
            return
        }

        _uiState.value = _uiState.value.copy(isSavingServices = true)
        viewModelScope.launch {
            val payload = if (garageMember) {
                emptyMap()
            } else {
                buildServicePricesPayload(skills, prices, vehiclePrices)
            }
            val result = authRepository.updateMechanicSkills(currentUserId, skills, payload, replacePrices = true)
            _uiState.value = _uiState.value.copy(isSavingServices = false)
            if (result.isSuccess) {
                val savedFlat = if (garageMember) emptyMap() else prices
                val savedVehicle = if (garageMember) emptyMap() else vehiclePrices
                _servicePrices.value = savedFlat
                _serviceVehiclePrices.value = savedVehicle
                _uiState.value = _uiState.value.copy(
                    servicePrices = savedFlat,
                    serviceVehiclePrices = savedVehicle,
                    infoMessage = if (garageMember)
                        "Saved ${skills.size} offered service(s). Prices come from your garage."
                    else
                        "Saved ${skills.size} offered service(s).",
                )
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(infoMessage = "Failed to save services")
            }
        }
    }

    // ---- Garage catalog (owner only) ----

    fun toggleGarageSkill(skill: String) {
        val skills = _uiState.value.garageSelectedSkills.toMutableList()
        val prices = _uiState.value.garageServicePrices.toMutableMap()
        val vehicle = _uiState.value.garageVehiclePrices.toMutableMap()
        if (skills.any { it.equals(skill, ignoreCase = true) }) {
            skills.removeAll { it.equals(skill, ignoreCase = true) }
            prices.keys.filter { it.equals(skill, ignoreCase = true) }.forEach { prices.remove(it) }
            vehicle.keys.filter { it.equals(skill, ignoreCase = true) }.forEach { vehicle.remove(it) }
        } else {
            skills.add(skill)
        }
        _uiState.value = _uiState.value.copy(
            garageSelectedSkills = skills,
            garageServicePrices = prices,
            garageVehiclePrices = vehicle,
        )
    }

    fun setGaragePrice(serviceName: String, price: Long?) {
        val prices = _uiState.value.garageServicePrices.toMutableMap()
        if (price != null && price > 0) prices[serviceName] = price else prices.remove(serviceName)
        _uiState.value = _uiState.value.copy(garageServicePrices = prices)
    }

    fun setGarageVehicleRate(serviceName: String, make: String, model: String, price: Long?) {
        val key = buildVehiclePriceKey(make, model)
        if (key.isBlank()) return
        val current = _uiState.value.garageVehiclePrices.toMutableMap()
        val entry = current[serviceName].orEmpty().toMutableMap()
        if (price != null && price > 0) entry[key] = price else entry.remove(key)
        if (entry.isEmpty()) current.remove(serviceName) else current[serviceName] = entry
        _uiState.value = _uiState.value.copy(garageVehiclePrices = current)
    }

    fun removeGarageVehicleRate(serviceName: String, make: String, model: String) {
        setGarageVehicleRate(serviceName, make, model, null)
    }

    fun toggleGarageVehicleType(typeId: String) {
        val current = _uiState.value.garageVehicleTypes.toMutableList()
        val existing = current.firstOrNull { it.equals(typeId, ignoreCase = true) }
        if (existing != null) {
            current.removeAll { it.equals(typeId, ignoreCase = true) }
        } else {
            current.add(typeId)
        }
        val next = normalizeVehicleTypes(current)
        _garageVehicleTypes.value = next
        _uiState.value = _uiState.value.copy(garageVehicleTypes = next)
    }

    fun setAllGarageVehicleTypes(selected: Boolean) {
        val next = if (selected) {
            VehicleCatalogData.vehicleTypeOptions().map { it.id }
        } else {
            emptyList()
        }
        _garageVehicleTypes.value = next
        _uiState.value = _uiState.value.copy(garageVehicleTypes = next)
    }

    fun saveGarageCatalog(onSuccess: () -> Unit = {}) {
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val garageId = _uiState.value.garageId
        if (garageId.isBlank()) {
            _uiState.value = _uiState.value.copy(infoMessage = "No garage found for your account.")
            return
        }
        val skills = _uiState.value.garageSelectedSkills
        val flatPrices = _uiState.value.garageServicePrices
        val vehiclePrices = _uiState.value.garageVehiclePrices
        if (skills.isEmpty()) {
            _uiState.value = _uiState.value.copy(infoMessage = "Select at least one garage service.")
            return
        }
        val nested = buildServicePricesPayload(skills, flatPrices, vehiclePrices)
        val vehicleTypes = _uiState.value.garageVehicleTypes

        _uiState.value = _uiState.value.copy(isSavingGarageCatalog = true)
        viewModelScope.launch {
            val result = garageRepository.updateGarageCatalog(
                garageId,
                ownerId,
                skills,
                nested,
                vehicleTypes,
            )
            _uiState.value = _uiState.value.copy(isSavingGarageCatalog = false)
            if (result.isSuccess) {
                val typeCount = vehicleTypes.size
                val typeLabel = if (typeCount == 0) {
                    "all vehicle types"
                } else {
                    "$typeCount vehicle type${if (typeCount == 1) "" else "s"}"
                }
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Saved ${skills.size} garage service(s) and $typeLabel.",
                )
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = result.exceptionOrNull()?.message ?: "Failed to save garage prices",
                )
            }
        }
    }

    fun updateGarageProfilePhoto(context: android.content.Context, uri: android.net.Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!_uiState.value.isGarageOwner) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "Only the garage owner can update the garage photo.",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingGaragePhoto = true)
            val upload = authRepository.uploadSignupImage(userId, "garage", uri, context)
            if (upload.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isSavingGaragePhoto = false,
                    infoMessage = upload.exceptionOrNull()?.message ?: "Could not upload the garage photo.",
                )
                return@launch
            }
            val url = upload.getOrNull().orEmpty()
            val result = authRepository.updateGarageProfilePhotos(userId, listOf(url))
            _uiState.value = _uiState.value.copy(
                isSavingGaragePhoto = false,
                garagePhotos = result.getOrNull() ?: _uiState.value.garagePhotos,
                infoMessage = if (result.isSuccess) {
                    "Garage photo updated."
                } else {
                    result.exceptionOrNull()?.message ?: "Could not update the garage photo."
                },
            )
        }
    }

    fun updateGarageLocationName(locationName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!_uiState.value.isGarageOwner && _uiState.value.garageId.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "Only the garage owner can update the location name.",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingLocationName = true)
            val result = authRepository.updateGarageLocationName(userId, locationName)
            _uiState.value = _uiState.value.copy(
                isSavingLocationName = false,
                locationName = result.getOrNull() ?: _uiState.value.locationName,
                infoMessage = if (result.isSuccess) {
                    "Location name updated."
                } else {
                    result.exceptionOrNull()?.message ?: "Could not update the location name."
                },
            )
        }
    }

    fun regenerateInviteCode() {
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val garageId = _uiState.value.garageId
        if (garageId.isBlank()) return
        viewModelScope.launch {
            val result = garageRepository.regenerateInviteCode(garageId, ownerId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    inviteCode = result.getOrNull() ?: _uiState.value.inviteCode,
                    infoMessage = "New invite code generated.",
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = result.exceptionOrNull()?.message ?: "Failed to refresh invite code",
                )
            }
        }
    }

    fun addAdditionalServiceNote(jobId: String, text: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val job =
            _uiState.value.ongoingJobs.find { it.id == jobId }
                ?: _uiState.value.completedJobs.find { it.id == jobId }
                ?: _uiState.value.garageJobs.find { it.id == jobId }
                ?: return
        viewModelScope.launch {
            val profile = authRepository.getUserProfile(userId)
            val note = JobAdditionalServices.createNote(
                userId = userId,
                authorRole = JobAdditionalServices.authorRole(
                    job,
                    userId,
                    isGarageOwner = _uiState.value.isGarageOwner,
                    isGarageMember = _uiState.value.isGarageMember,
                ),
                authorName = profile?.name.orEmpty(),
                text = text,
            )
            val result = jobRepository.addAdditionalServiceNote(jobId, note)
            _uiState.value = _uiState.value.copy(
                infoMessage = if (result.isSuccess) {
                    "Additional service added to the job card."
                } else {
                    result.exceptionOrNull()?.message ?: "Failed to add additional service"
                },
            )
        }
    }

    fun acceptJob(jobId: String, mechanicId: String) {
        viewModelScope.launch {
            val garageId = _uiState.value.garageId.takeIf { it.isNotBlank() }
            val result = jobRepository.acceptJob(jobId, mechanicId, garageId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(infoMessage = "Job accepted successfully")
            } else {
                _uiState.value = _uiState.value.copy(infoMessage = "Failed to accept job")
            }
        }
    }

    fun updateStatus(jobId: String, status: JobStatus) {
        viewModelScope.launch {
            val result = jobRepository.updateJobStatus(jobId, status)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(infoMessage = "Status updated to ${status.name}")

                if (status == JobStatus.COMPLETED) {
                    val job = _uiState.value.ongoingJobs.find { it.id == jobId }
                    job?.driverId?.let { driverId ->
                        authRepository.awardLoyaltyPoints(driverId, 10)
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(infoMessage = "Failed to update status")
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double, locationName: String = "") {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val name = locationName.trim().take(120)
        _uiState.value = _uiState.value.copy(
            latitude = lat,
            longitude = lon,
            locationName = name.ifBlank { _uiState.value.locationName },
        )

        viewModelScope.launch {
            val profile = authRepository.getUserProfile(currentUserId)
            if (profile != null) {
                val updatedProfile = profile.copy(
                    latitude = lat,
                    longitude = lon,
                    locationName = name.ifBlank { profile.locationName },
                )
                val result = authRepository.updateUserProfile(updatedProfile)
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(infoMessage = "Failed to update location")
                    return@launch
                }
                if (name.isNotBlank()) {
                    authRepository.updateGarageLocationName(currentUserId, name)
                }
                _uiState.value = _uiState.value.copy(infoMessage = "Location updated successfully")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }
}

class MechanicDashboardViewModelFactory(
    private val authRepository: AuthRepository,
    private val jobRepository: JobRepository,
    private val garageRepository: GarageRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MechanicDashboardViewModel(authRepository, jobRepository, garageRepository) as T
    }
}
