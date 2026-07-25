package com.example.mototap.core.data.firebase

import android.util.Log
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.model.UserRole
import com.example.mototap.core.model.VerificationStatus
import com.example.mototap.core.model.Review
import com.example.mototap.core.model.GarageMemberRole
import com.example.mototap.core.model.GarageMemberStatus
import com.example.mototap.core.repository.AuthRepository
import com.example.mototap.core.repository.GarageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mototap.core.data.cloudinary.CloudinaryUploadService
import com.example.mototap.core.util.normalizeServicePrices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val garageRepository: GarageRepository = FirebaseGarageRepository(firestore),
) : AuthRepository {

    companion object {
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private val MECHANIC_ROLE_QUERY = listOf("mechanic", "MECHANIC")
        private val PARTS_DEALER_ROLE_QUERY = listOf("parts_dealer", "PARTS_DEALER")
    }

    private fun parseUserRole(roleStr: String?): UserRole {
        return when (roleStr?.lowercase()?.trim()) {
            "mechanic" -> UserRole.MECHANIC
            "parts_dealer", "parts dealer" -> UserRole.PARTS_DEALER
            else -> UserRole.DRIVER
        }
    }

    private fun parseVerificationStatus(statusStr: String?): VerificationStatus {
        return when (statusStr?.uppercase()) {
            "APPROVED", "VERIFIED" -> VerificationStatus.VERIFIED
            "REJECTED" -> VerificationStatus.REJECTED
            else -> VerificationStatus.PENDING
        }
    }

    private fun toFirestoreRole(role: String): String =
        when (role.lowercase().trim()) {
            "mechanic" -> "MECHANIC"
            "parts_dealer", "parts dealer" -> "PARTS_DEALER"
            else -> "DRIVER"
        }

    /** Drivers are auto-approved; business accounts start pending (matches web). */
    private fun defaultProfileStatusForRole(role: String): String =
        when (toFirestoreRole(role)) {
            "MECHANIC", "PARTS_DEALER" -> "PENDING"
            else -> "APPROVED"
        }

    private fun normalizeUrl(url: String?): String {
        val s = url?.trim().orEmpty()
        return if (s.startsWith("//")) "https:$s" else s
    }

    private fun parseRedeemedRewards(
        doc: com.google.firebase.firestore.DocumentSnapshot,
    ): List<com.example.mototap.core.model.RedeemedReward> {
        return (doc.get("redeemedRewards") as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            com.example.mototap.core.model.RedeemedReward(
                title = map["title"]?.toString() ?: "",
                points = (map["points"] as? Number)?.toInt() ?: 0,
                redeemedAtMillis = (map["redeemedAtMillis"] as? Number)?.toLong() ?: 0L,
            )
        } ?: emptyList()
    }

    /** Case-insensitive lookup over normalized (nested) service prices. */
    private fun lookupNestedEntry(
        prices: Map<String, Map<String, Long>>,
        serviceName: String,
    ): Map<String, Long>? {
        prices[serviceName]?.let { return it }
        val lower = serviceName.lowercase()
        return prices.entries.firstOrNull { it.key.lowercase() == lower }?.value
    }

    private suspend fun syncMechanicPublicProfile(
        userId: String,
        skills: List<String>,
        servicePrices: Map<String, Map<String, Long>>,
    ) {
        val userDoc = firestore.collection("users").document(userId).get().await()
        if (!userDoc.exists()) return

        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "skills" to skills,
            "availableServices" to skills,
            "servicePrices" to servicePrices,
            "updatedAtMillis" to System.currentTimeMillis(),
        )
        userDoc.getString("name")?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["name"] = it }
        userDoc.getString("role")?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["role"] = it }
        userDoc.getString("profilePhotoUrl")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            payload["profilePhotoUrl"] = normalizeUrl(it)
        }
        userDoc.getDouble("latitude")?.let { payload["latitude"] = it }
        userDoc.getDouble("longitude")?.let { payload["longitude"] = it }
        userDoc.getString("address")?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["address"] = it }
        userDoc.getString("status")?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["status"] = it }
        userDoc.getString("institutionName")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            payload["institutionName"] = it
        }
        userDoc.getString("garageId")?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["garageId"] = it }
        userDoc.getDouble("rating")?.let { payload["rating"] = it }
        userDoc.getLong("reviewCount")?.let { payload["reviewCount"] = it }

        firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .document(userId)
            .set(payload, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /** Fetch garage servicePrices for each mechanic's garageId (batched by unique id). */
    private suspend fun hydrateGarageServicePrices(mechanics: List<UserProfile>): List<UserProfile> {
        val garageIds = mechanics.map { it.garageId.trim() }.filter { it.isNotEmpty() }.distinct()
        if (garageIds.isEmpty()) return mechanics

        val priceByGarageId = HashMap<String, Map<String, Map<String, Long>>>()
        garageIds.forEach { garageId ->
            val garage = garageRepository.getGarage(garageId)
            priceByGarageId[garageId] = garage?.servicePrices ?: emptyMap()
        }

        return mechanics.map { mechanic ->
            val garageId = mechanic.garageId.trim()
            if (garageId.isEmpty()) mechanic
            else mechanic.copy(garageServicePrices = priceByGarageId[garageId] ?: emptyMap())
        }
    }

    override val currentUserId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            trySend(it.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun ensureSignedIn() {
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            Log.d("FirebaseAuthRepo", "Attempting signIn for $email")
            auth.signInWithEmailAndPassword(email, password).await()
            Log.d("FirebaseAuthRepo", "signIn successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "signIn error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(Exception("Please enter your email address."))
        }

        return try {
            auth.sendPasswordResetEmail(trimmed).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Match web: don't reveal whether the account exists.
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "sendPasswordReset error: ${e.message}")
            Result.failure(Exception("Unable to send reset email. Please try again."))
        }
    }

    override suspend fun signUp(email: String, password: String, name: String, role: String, phoneNumber: String?): Result<Unit> {
        return try {
            Log.d("FirebaseAuthRepo", "Attempting signUp for $email with role: $role")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User creation failed")
            Log.d("FirebaseAuthRepo", "Auth user created: $userId. Saving to Firestore...")

            val phone = phoneNumber?.trim().orEmpty()
            val firestoreRole = toFirestoreRole(role)
            val userMap = mapOf(
                "uid" to userId,
                "id" to userId,
                "name" to name.trim(),
                "email" to email.trim(),
                "phone" to phone,
                "phoneNumber" to phone,
                "role" to firestoreRole,
                "status" to defaultProfileStatusForRole(role),
                "onboardingStep" to 1,
                "onboardingComplete" to false,
                "rating" to 0,
                "reviewCount" to 0,
                "skills" to emptyList<String>(),
                "parts" to emptyList<String>(),
                "availableParts" to emptyList<String>(),
                "partPrices" to emptyMap<String, Long>(),
            )

            firestore.collection("users").document(userId).set(userMap).await()
            Log.d("FirebaseAuthRepo", "Firestore document created. signUp complete.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "signUp error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "signOut error: ${e.message}")
        }
    }

    override suspend fun getUserRole(userId: String): String? {
        return try {
            Log.d("FirebaseAuthRepo", "Fetching role for $userId")
            val document = firestore.collection("users").document(userId).get().await()
            if (!document.exists()) {
                Log.w("FirebaseAuthRepo", "User document does not exist for $userId")
                return null
            }
            val role = document.getString("role")?.lowercase()?.trim()
            Log.d("FirebaseAuthRepo", "Role fetched and normalized: $role")
            role
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "getUserRole error: ${e.message}")
            null
        }
    }

    override suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            if (!document.exists()) {
                Log.w("FirebaseAuthRepo", "Profile not found for $userId")
                return null
            }

            val roleStr = document.getString("role")?.lowercase()?.trim() ?: "driver"
            val role = parseUserRole(roleStr)
            val statusStr = document.getString("status")?.uppercase() ?: "PENDING"
            val status = parseVerificationStatus(statusStr)

            val garagePhotos = (document.get("garagePhotos") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            val skills = (document.get("skills") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            val availableServices = (document.get("availableServices") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
            val brandSpecializations = (document.get("brandSpecializations") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()

            val vehicles = (document.get("vehicles") as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                com.example.mototap.core.model.VehicleProfile(
                    id = map["id"]?.toString() ?: "",
                    make = map["make"]?.toString() ?: "",
                    model = map["model"]?.toString() ?: "",
                    year = map["year"]?.toString() ?: "",
                    licensePlate = map["licensePlate"]?.toString() ?: "",
                    mileage = map["mileage"]?.toString() ?: "",
                    lastServiceDate = map["lastServiceDate"] as? Long,
                    photoUrl = map["photoUrl"]?.toString() ?: ""
                )
            } ?: emptyList()

            val profilePhotoUrl = document.getString("profilePhotoUrl")
                ?: document.getString("photoUrl")
                ?: document.getString("photoURL")
                ?: document.getString("profile_photo_url")
                ?: document.getString("profileImage")
                ?: document.getString("profile_image")
                ?: document.getString("image")
                ?: document.getString("imageUrl")
                ?: document.getString("avatar")
                ?: auth.currentUser?.photoUrl?.toString()
                ?: ""

            val garageId = document.getString("garageId") ?: ""
            val garageServicePrices = if (garageId.isNotBlank()) {
                garageRepository.getGarage(garageId)?.servicePrices ?: emptyMap()
            } else emptyMap()

            UserProfile(
                id = userId,
                name = document.getString("name") ?: "",
                email = document.getString("email") ?: "",
                phone = document.getString("phone")
                    ?: document.getString("phoneNumber")
                    ?: "",
                role = role,
                profilePhotoUrl = normalizeUrl(profilePhotoUrl),
                idNumber = document.getString("idNumber") ?: "",
                idPhotoUrl = normalizeUrl(document.getString("idPhotoUrl")),
                status = status,
                garageId = garageId,
                garageRole = document.getString("garageRole") ?: "",
                vehicleType = document.getString("vehicleType") ?: "",
                vehicleModel = document.getString("vehicleModel") ?: "",
                numberPlate = document.getString("numberPlate") ?: "",
                vehiclePhotoUrl = normalizeUrl(
                    document.getString("vehiclePhotoUrl")
                    ?: document.getString("vehicle_photo_url")
                    ?: document.getString("vehiclePhoto")
                    ?: document.getString("carPhoto")
                ),
                vehicles = vehicles,
                loyaltyPoints = document.getLong("loyaltyPoints")?.toInt() ?: 0,
                redeemedRewards = parseRedeemedRewards(document),
                certificateNumber = document.getString("certificateNumber") ?: "",
                certificatePhotoUrl = normalizeUrl(
                    document.getString("certificatePhotoUrl")
                    ?: document.getString("certificate_photo_url")
                    ?: document.getString("certificatePhoto")
                    ?: document.getString("certPhoto")
                ),
                institutionName = document.getString("institutionName") ?: "",
                experienceYears = document.getString("experienceYears") ?: "",
                latitude = document.getDouble("latitude"),
                longitude = document.getDouble("longitude"),
                address = document.getString("address") ?: "",
                garagePhotos = garagePhotos.map { normalizeUrl(it) },
                skills = skills,
                availableServices = availableServices,
                rating = document.getDouble("rating") ?: 0.0,
                reviewCount = document.getLong("reviewCount")?.toInt() ?: 0,
                brandSpecializations = brandSpecializations,
                onboardingStep = document.getLong("onboardingStep")?.toInt(),
                onboardingComplete = document.getBoolean("onboardingComplete") == true,
                servicePrices = normalizeServicePrices(document.get("servicePrices")),
                garageServicePrices = garageServicePrices,
            )
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "getUserProfile error: ${e.message}")
            null
        }
    }

    override suspend fun completeSignupStep2(
        userId: String,
        profilePhotoUrl: String,
        idPhotoUrl: String,
        idNumber: String,
        role: String,
    ): Result<Unit> {
        return try {
            val trimmedId = idNumber.trim()
            val payload = mutableMapOf<String, Any>(
                "profilePhotoUrl" to profilePhotoUrl,
                "idPhotoUrl" to idPhotoUrl,
                "idNumber" to trimmedId,
                "onboardingStep" to 2,
            )
            if (toFirestoreRole(role) in listOf("MECHANIC", "PARTS_DEALER")) {
                payload["certificateNumber"] = trimmedId
            }
            firestore.collection("users").document(userId).update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "completeSignupStep2 error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun completeSignupStep3Driver(
        userId: String,
        vehicleMake: String,
        vehicleModel: String,
        numberPlate: String,
        vehiclePhotoUrl: String,
    ): Result<Unit> {
        return try {
            val make = vehicleMake.trim()
            val model = vehicleModel.trim()
            val plate = numberPlate.trim()

            val vehicle = mapOf(
                "id" to UUID.randomUUID().toString(),
                "make" to make,
                "model" to model,
                "year" to "",
                "licensePlate" to plate,
                "mileage" to "",
                "lastServiceDate" to null,
                "photoUrl" to vehiclePhotoUrl,
            )

            val payload = mapOf(
                // vehicleType retained as make for legacy discovery/filtering.
                "vehicleType" to make,
                "vehicleMake" to make,
                "vehicleModel" to model,
                "numberPlate" to plate,
                "vehiclePhotoUrl" to vehiclePhotoUrl,
                "vehicles" to listOf(vehicle),
                "onboardingStep" to 3,
                "onboardingComplete" to true,
                "status" to "APPROVED",
            )
            firestore.collection("users").document(userId).update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "completeSignupStep3Driver error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun completeSignupStep3Mechanic(
        userId: String,
        institutionName: String,
        experienceYears: String,
        certificatePhotoUrl: String,
        garagePhotos: List<String>,
        latitude: Double,
        longitude: Double,
        address: String,
        garageMode: String,
        inviteCode: String,
    ): Result<Unit> {
        val joinMode = garageMode.trim() == "join"
        return try {
            val profileName = firestore.collection("users").document(userId)
                .get().await().getString("name")?.trim().orEmpty()

            if (joinMode) {
                val lookup = garageRepository.lookupInvite(inviteCode)
                    ?: return Result.failure(Exception("Invalid or expired garage invite code."))
                val garage = lookup.garage

                // Join before marking onboarding complete so dashboard auto-setup cannot
                // create a competing solo garage during this request.
                val joinResult = garageRepository.joinGarageWithInvite(
                    userId = userId,
                    inviteCode = inviteCode,
                    profile = UserProfile(id = userId, name = profileName),
                )
                if (joinResult.isFailure) {
                    return Result.failure(joinResult.exceptionOrNull() ?: Exception("Unable to join garage."))
                }

                val payload = mutableMapOf<String, Any?>(
                    "institutionName" to garage.name,
                    "experienceYears" to experienceYears.trim(),
                    "certificatePhotoUrl" to certificatePhotoUrl,
                    "garagePhotos" to garage.garagePhotos,
                    "latitude" to garage.latitude,
                    "longitude" to garage.longitude,
                    "address" to garage.address,
                    "garageId" to garage.id,
                    "garageRole" to GarageMemberRole.MECHANIC,
                    "garageMemberStatus" to GarageMemberStatus.PENDING,
                    "onboardingStep" to 3,
                    "onboardingComplete" to true,
                    "status" to "PENDING",
                )
                firestore.collection("users").document(userId).update(payload).await()

                val refreshedSkills = firestore.collection("users").document(userId)
                    .get().await().let { snap ->
                        (snap.get("skills") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    }
                syncMechanicPublicProfile(userId, refreshedSkills, emptyMap())
                return Result.success(Unit)
            }

            val payload = mapOf(
                "institutionName" to institutionName.trim(),
                "experienceYears" to experienceYears.trim(),
                "certificatePhotoUrl" to certificatePhotoUrl,
                "garagePhotos" to garagePhotos,
                "latitude" to latitude,
                "longitude" to longitude,
                "address" to address.trim(),
                "onboardingStep" to 3,
                "onboardingComplete" to true,
                "status" to "PENDING",
            )
            firestore.collection("users").document(userId).update(payload).await()

            // Create a garage-of-one so the owner can invite staff and set shop prices.
            garageRepository.createGarageForOwner(
                ownerId = userId,
                profile = UserProfile(
                    id = userId,
                    name = profileName,
                    institutionName = institutionName.trim(),
                    address = address.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    garagePhotos = garagePhotos,
                ),
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "completeSignupStep3Mechanic error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun completeSignupStep3PartsDealer(
        userId: String,
        institutionName: String,
        experienceYears: String,
        certificatePhotoUrl: String,
        garagePhotos: List<String>,
        latitude: Double,
        longitude: Double,
        address: String,
    ): Result<Unit> {
        // Parts dealers reuse the provider fields but never create a garage org.
        return try {
            val payload = mapOf(
                "institutionName" to institutionName.trim(),
                "experienceYears" to experienceYears.trim(),
                "certificatePhotoUrl" to certificatePhotoUrl,
                "garagePhotos" to garagePhotos,
                "latitude" to latitude,
                "longitude" to longitude,
                "address" to address.trim(),
                "onboardingStep" to 3,
                "onboardingComplete" to true,
                "status" to "PENDING",
            )
            firestore.collection("users").document(userId).update(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "completeSignupStep3PartsDealer error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val vehiclesMap = profile.vehicles.map { vehicle ->
                mapOf(
                    "id" to vehicle.id,
                    "make" to vehicle.make,
                    "model" to vehicle.model,
                    "year" to vehicle.year,
                    "licensePlate" to vehicle.licensePlate,
                    "mileage" to vehicle.mileage,
                    "lastServiceDate" to vehicle.lastServiceDate,
                    "photoUrl" to vehicle.photoUrl
                )
            }

            val userMap = mutableMapOf(
                "name" to profile.name,
                "phone" to profile.phone,
                "phoneNumber" to profile.phone,
                "profilePhotoUrl" to profile.profilePhotoUrl,
                "idNumber" to profile.idNumber,
                "idPhotoUrl" to profile.idPhotoUrl,
                "status" to profile.status.name,
                "vehicleType" to profile.vehicleType,
                "vehicleModel" to profile.vehicleModel,
                "numberPlate" to profile.numberPlate,
                "vehiclePhotoUrl" to profile.vehiclePhotoUrl,
                "vehicles" to vehiclesMap,
                "loyaltyPoints" to profile.loyaltyPoints,
                "certificateNumber" to profile.certificateNumber,
                "certificatePhotoUrl" to profile.certificatePhotoUrl,
                "institutionName" to profile.institutionName,
                "experienceYears" to profile.experienceYears,
                "latitude" to profile.latitude,
                "longitude" to profile.longitude,
                "address" to profile.address,
                "garagePhotos" to profile.garagePhotos,
                "skills" to profile.skills,
                "availableServices" to profile.availableServices,
                "rating" to profile.rating,
                "reviewCount" to profile.reviewCount,
                "brandSpecializations" to profile.brandSpecializations
            )
            firestore.collection("users").document(profile.id).update(userMap as Map<String, Any>).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "updateUserProfile error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun uploadSignupImage(
        userId: String,
        folder: String,
        uri: android.net.Uri,
        context: android.content.Context,
    ): Result<String> {
        return CloudinaryUploadService.uploadSignupImage(context, userId, folder, uri)
    }

    private fun approvedPublicProfilesQuery(roles: List<String>) =
        firestore.collection(PUBLIC_PROFILES_COLLECTION)
            .whereIn("role", roles)
            .whereEqualTo("status", "APPROVED")

    private fun mapApprovedPublicProfiles(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        rolePredicate: (String?) -> Boolean,
    ): List<UserProfile> = docs.mapNotNull { doc ->
        val role = doc.getString("role")?.lowercase()?.trim()
        if (rolePredicate(role)) {
            mapDocumentToUserProfile(doc.id, doc)
        } else null
    }

    override suspend fun getAllMechanics(): List<UserProfile> {
        return try {
            val snapshot = approvedPublicProfilesQuery(MECHANIC_ROLE_QUERY).get().await()
            val mechanics = mapApprovedPublicProfiles(snapshot.documents) { role -> role == "mechanic" }
            hydrateGarageServicePrices(mechanics)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "getAllMechanics error: ${e.message}")
            emptyList()
        }
    }

    override fun observeAllMechanics(): Flow<List<UserProfile>> = callbackFlow {
        val registration = approvedPublicProfilesQuery(MECHANIC_ROLE_QUERY)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseAuthRepo", "Error observing mechanics: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val mechanics = mapApprovedPublicProfiles(snapshot.documents) { role ->
                        role == "mechanic"
                    }
                    trySend(mechanics)
                    launch {
                        runCatching { hydrateGarageServicePrices(mechanics) }
                            .onSuccess { trySend(it) }
                    }
                }
            }
        awaitClose {
            Log.d("FirebaseAuthRepo", "Removing mechanics observer")
            registration.remove()
        }
    }

    override suspend fun getAllPartsDealers(): List<UserProfile> {
        return try {
            val snapshot = approvedPublicProfilesQuery(PARTS_DEALER_ROLE_QUERY).get().await()
            mapApprovedPublicProfiles(snapshot.documents) { role ->
                role == "parts_dealer" || role == "parts dealer"
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "getAllPartsDealers error: ${e.message}")
            emptyList()
        }
    }

    override fun observeAllPartsDealers(): Flow<List<UserProfile>> = callbackFlow {
        val registration = approvedPublicProfilesQuery(PARTS_DEALER_ROLE_QUERY)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseAuthRepo", "Error observing parts dealers: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val dealers = mapApprovedPublicProfiles(snapshot.documents) { role ->
                        role == "parts_dealer" || role == "parts dealer"
                    }
                    trySend(dealers)
                }
            }
        awaitClose {
            Log.d("FirebaseAuthRepo", "Removing parts dealers observer")
            registration.remove()
        }
    }

    private fun mapDocumentToUserProfile(id: String, doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile {
        val name = doc.getString("name") ?: ""
        val email = doc.getString("email") ?: ""
        val phone = doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""
        val roleStr = doc.getString("role")?.lowercase()?.trim() ?: "driver"
        val role = parseUserRole(roleStr)
        val status = parseVerificationStatus(doc.getString("status"))

        val garagePhotos = (doc.get("garagePhotos") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val skills = (doc.get("skills") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val availableServices = (doc.get("availableServices") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val brandSpecializations = (doc.get("brandSpecializations") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()

        val parts = (doc.get("parts") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val availableParts = (doc.get("availableParts") as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
        val partPrices = (doc.get("partPrices") as? Map<*, *>)?.entries?.mapNotNull { entry ->
            val key = entry.key?.toString() ?: return@mapNotNull null
            val value = (entry.value as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }?.toMap() ?: emptyMap()
        val servicePrices = normalizeServicePrices(doc.get("servicePrices"))

        val vehicles = (doc.get("vehicles") as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            com.example.mototap.core.model.VehicleProfile(
                id = map["id"]?.toString() ?: "",
                make = map["make"]?.toString() ?: "",
                model = map["model"]?.toString() ?: "",
                year = map["year"]?.toString() ?: "",
                licensePlate = map["licensePlate"]?.toString() ?: "",
                mileage = map["mileage"]?.toString() ?: "",
                lastServiceDate = map["lastServiceDate"] as? Long,
                photoUrl = map["photoUrl"]?.toString() ?: ""
            )
        } ?: emptyList()

        val profilePhotoUrl = doc.getString("profilePhotoUrl")
            ?: doc.getString("photoURL")
            ?: doc.getString("profile_photo_url")
            ?: doc.getString("profileImage")
            ?: ""

        return UserProfile(
            id = id,
            name = name,
            email = email,
            phone = phone,
            role = role,
            profilePhotoUrl = normalizeUrl(profilePhotoUrl),
            idNumber = doc.getString("idNumber") ?: "",
            idPhotoUrl = normalizeUrl(doc.getString("idPhotoUrl")),
            status = status,
            garageId = doc.getString("garageId") ?: "",
            garageRole = doc.getString("garageRole") ?: "",
            vehicleType = doc.getString("vehicleType") ?: "",
            vehicleModel = doc.getString("vehicleModel") ?: "",
            numberPlate = doc.getString("numberPlate") ?: "",
            vehiclePhotoUrl = normalizeUrl(doc.getString("vehiclePhotoUrl")),
            vehicles = vehicles,
            loyaltyPoints = doc.getLong("loyaltyPoints")?.toInt() ?: 0,
            redeemedRewards = parseRedeemedRewards(doc),
            certificateNumber = doc.getString("certificateNumber") ?: "",
            certificatePhotoUrl = normalizeUrl(doc.getString("certificatePhotoUrl")),
            institutionName = doc.getString("institutionName") ?: "",
            experienceYears = doc.getString("experienceYears") ?: "",
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            address = doc.getString("address") ?: "",
            garagePhotos = garagePhotos.map { normalizeUrl(it) },
            skills = skills,
            availableServices = availableServices,
            rating = doc.getDouble("rating") ?: 0.0,
            reviewCount = doc.getLong("reviewCount")?.toInt() ?: 0,
            brandSpecializations = brandSpecializations,
            onboardingStep = doc.getLong("onboardingStep")?.toInt(),
            onboardingComplete = doc.getBoolean("onboardingComplete") == true,
            parts = parts,
            availableParts = availableParts,
            partPrices = partPrices,
            servicePrices = servicePrices,
        )
    }

    override suspend fun deleteAccount(currentPassword: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
        val userId = user.uid
        val email = user.email ?: return Result.failure(Exception("Missing email for re-authentication"))

        return try {
            Log.d("FirebaseAuthRepo", "Starting account deletion for $userId")

            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            Log.d("FirebaseAuthRepo", "Re-authenticating user before deletion...")
            user.reauthenticate(credential).await()
            Log.d("FirebaseAuthRepo", "Re-authentication successful.")

            try {
                Log.d("FirebaseAuthRepo", "Deleting Firestore user document...")
                withTimeout(5000) {
                    firestore.collection("users").document(userId).delete().await()
                }
                Log.d("FirebaseAuthRepo", "Firestore document deleted.")
            } catch (e: Exception) {
                Log.e("FirebaseAuthRepo", "Firestore deletion failed or timed out: ${e.message}")
            }

            Log.d("FirebaseAuthRepo", "Deleting Auth account...")
            user.delete().await()
            Log.d("FirebaseAuthRepo", "Auth account deleted.")

            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("FirebaseAuthRepo", "Deletion failed: Invalid password: ${e.message}")
            Result.failure(Exception("The password you entered is incorrect."))
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Log.e("FirebaseAuthRepo", "Deletion failed: Re-authentication required: ${e.message}")
            Result.failure(Exception("Please re-enter your current password and try again."))
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "deleteAccount error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateMechanicSkills(
        userId: String,
        skills: List<String>,
        servicePrices: Map<String, Map<String, Long>>,
        replacePrices: Boolean,
    ): Result<Unit> {
        return try {
            val existingDoc = firestore.collection("users").document(userId).get().await()
            val garageId = existingDoc.getString("garageId")?.trim().orEmpty()

            // Garage members inherit garage-wide prices; keep personal prices empty.
            if (garageId.isNotEmpty()) {
                val updates = mapOf(
                    "skills" to skills,
                    "availableServices" to skills,
                    "servicePrices" to emptyMap<String, Any>(),
                )
                firestore.collection("users").document(userId).update(updates).await()
                syncMechanicPublicProfile(userId, skills, emptyMap())
                return Result.success(Unit)
            }

            val incomingPrices = normalizeServicePrices(servicePrices)
            val mergedPrices: Map<String, Map<String, Long>> = if (replacePrices) {
                incomingPrices
            } else {
                val existingPrices = normalizeServicePrices(existingDoc.get("servicePrices"))
                val merged = linkedMapOf<String, Map<String, Long>>()
                skills.forEach { skill ->
                    val name = skill.trim()
                    if (name.isEmpty()) return@forEach
                    val entry = lookupNestedEntry(incomingPrices, name)
                        ?: lookupNestedEntry(existingPrices, name)
                    if (entry != null) merged[name] = entry
                }
                merged
            }

            val updates = mapOf(
                "skills" to skills,
                "availableServices" to skills,
                "servicePrices" to mergedPrices,
            )
            firestore.collection("users").document(userId).update(updates).await()
            syncMechanicPublicProfile(userId, skills, mergedPrices)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "updateMechanicSkills error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun awardLoyaltyPoints(userId: String, points: Int): Result<Unit> = runCatching {
        firestore.runTransaction { transaction ->
            val userRef = firestore.collection("users").document(userId)
            val snapshot = transaction.get(userRef)
            val currentPoints = snapshot.getLong("loyaltyPoints") ?: 0L
            transaction.update(userRef, "loyaltyPoints", currentPoints + points)
        }.await()
    }

    override suspend fun addReview(review: Review): Result<Unit> = runCatching {
        val reviewRef = firestore.collection("reviews").document()
        val reviewData = mapOf(
            "mechanicId" to review.mechanicId,
            "driverId" to review.driverId,
            "driverName" to review.driverName,
            "rating" to review.rating,
            "comment" to review.comment,
            "timestampMillis" to review.timestampMillis
        )

        firestore.runTransaction { transaction ->
            val mechanicRef = firestore.collection("users").document(review.mechanicId)
            val mechanicDoc = transaction.get(mechanicRef)

            val currentRating = mechanicDoc.getDouble("rating") ?: 0.0
            val currentCount = mechanicDoc.getLong("reviewCount") ?: 0L

            val newCount = currentCount + 1
            val newRating = ((currentRating * currentCount) + review.rating) / newCount

            transaction.set(reviewRef, reviewData)
            transaction.update(mechanicRef, mapOf(
                "rating" to newRating,
                "reviewCount" to newCount
            ))
        }.await()
    }

    override fun observeMechanicReviews(mechanicId: String): Flow<List<Review>> = callbackFlow {
        val subscription = firestore.collection("reviews")
            .whereEqualTo("mechanicId", mechanicId)
            .orderBy("timestampMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val reviews = snapshot?.documents?.mapNotNull { doc ->
                    Review(
                        id = doc.id,
                        mechanicId = doc.getString("mechanicId") ?: "",
                        driverId = doc.getString("driverId") ?: "",
                        driverName = doc.getString("driverName") ?: "",
                        rating = doc.getDouble("rating") ?: 0.0,
                        comment = doc.getString("comment") ?: "",
                        timestampMillis = doc.getLong("timestampMillis") ?: 0L
                    )
                } ?: emptyList()
                trySend(reviews)
            }
        awaitClose { subscription.remove() }
    }
}
