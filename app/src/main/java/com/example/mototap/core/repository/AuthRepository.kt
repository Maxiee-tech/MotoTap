package com.example.mototap.core.repository

import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.model.Review
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: Flow<String?>

    suspend fun ensureSignedIn()
    
    suspend fun signIn(email: String, password: String): Result<Unit>
    
    suspend fun sendPasswordReset(email: String): Result<Unit>
    
    suspend fun signUp(email: String, password: String, name: String, role: String, phoneNumber: String? = null): Result<Unit>

    suspend fun signOut()
    
    suspend fun getUserRole(userId: String): String?

    suspend fun getUserProfile(userId: String): UserProfile?

    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>

    suspend fun uploadSignupImage(
        userId: String,
        folder: String,
        uri: android.net.Uri,
        context: android.content.Context,
    ): Result<String>

    suspend fun completeSignupStep2(
        userId: String,
        profilePhotoUrl: String,
        idPhotoUrl: String,
        idNumber: String,
        role: String,
    ): Result<Unit>

    suspend fun completeSignupStep3Driver(
        userId: String,
        vehicleMake: String,
        vehicleModel: String,
        numberPlate: String,
        vehiclePhotoUrl: String,
    ): Result<Unit>

    suspend fun completeSignupStep3Mechanic(
        userId: String,
        institutionName: String,
        experienceYears: String,
        certificatePhotoUrl: String,
        garagePhotos: List<String>,
        latitude: Double,
        longitude: Double,
        address: String,
        garageMode: String = "own",
        inviteCode: String = "",
        workingHours: com.example.mototap.core.util.WorkingHours? = null,
    ): Result<Unit>

    suspend fun completeSignupStep3PartsDealer(
        userId: String,
        institutionName: String,
        experienceYears: String,
        certificatePhotoUrl: String,
        garagePhotos: List<String>,
        latitude: Double,
        longitude: Double,
        address: String,
        workingHours: com.example.mototap.core.util.WorkingHours? = null,
    ): Result<Unit>

    suspend fun saveWorkingHours(
        userId: String,
        workingHours: com.example.mototap.core.util.WorkingHours,
    ): Result<Unit>

    suspend fun getAllMechanics(): List<UserProfile>
    fun observeAllMechanics(): Flow<List<UserProfile>>

    suspend fun getAllPartsDealers(): List<UserProfile>
    fun observeAllPartsDealers(): Flow<List<UserProfile>>

    suspend fun deleteAccount(currentPassword: String): Result<Unit>

    suspend fun updateMechanicSkills(
        userId: String,
        skills: List<String>,
        servicePrices: Map<String, Map<String, Long>> = emptyMap(),
        replacePrices: Boolean = false,
    ): Result<Unit>

    suspend fun awardLoyaltyPoints(userId: String, points: Int): Result<Unit>

    suspend fun addReview(review: Review): Result<Unit>
    fun observeMechanicReviews(mechanicId: String): Flow<List<Review>>
}
