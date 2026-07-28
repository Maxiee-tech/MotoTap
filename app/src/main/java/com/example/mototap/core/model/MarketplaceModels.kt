package com.example.mototap.core.model

enum class UserRole {
    DRIVER,
    MECHANIC,
    PARTS_DEALER,
    ADMIN,
}

enum class VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    SUSPENDED,
}

enum class JobStatus {
    INQUIRY,
    REQUESTED,
    MATCHING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    PAID,
    CLOSED,
}

data class VehicleProfile(
    val id: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val mileage: String = "",
    val lastServiceDate: Long? = null,
    val photoUrl: String = "",
)

data class RedeemedReward(
    val title: String = "",
    val points: Int = 0,
    val redeemedAtMillis: Long = 0L,
)

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.DRIVER,
    val profilePhotoUrl: String = "",
    val idNumber: String = "",
    val idPhotoUrl: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,

    // Garage org fields (mirrors web users.garageId / garageRole)
    val garageId: String = "",
    val garageRole: String = "",
    
    // Driver specific fields
    val vehicleType: String = "",
    val vehicleModel: String = "",
    val numberPlate: String = "",
    val vehiclePhotoUrl: String = "",
    val vehicles: List<VehicleProfile> = emptyList(),
    val loyaltyPoints: Int = 0,
    val redeemedRewards: List<RedeemedReward> = emptyList(),
    
    // Mechanic specific fields
    val certificateNumber: String = "",
    val certificatePhotoUrl: String = "",
    val institutionName: String = "",
    val experienceYears: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val garagePhotos: List<String> = emptyList(),
    
    val skills: List<String> = emptyList(),
    val availableServices: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val brandSpecializations: List<String> = emptyList(),
    val onboardingStep: Int? = null,
    val onboardingComplete: Boolean = false,

    // Garage-wide default prices mirrored for discovery. Outer key = service name,
    // inner key = "_default" or "Make:Model" -> KSh price.
    val garageServicePrices: Map<String, Map<String, Long>> = emptyMap(),

    // Parts dealer specific fields (institutionName doubles as shop name,
    // experienceYears as years in business)
    val parts: List<String> = emptyList(),
    val availableParts: List<String> = emptyList(),
    val partPrices: Map<String, Long> = emptyMap(),
    // service name -> { "_default" | "Make" | "Make:Model" -> KSh price }
    val servicePrices: Map<String, Map<String, Long>> = emptyMap(),

    // Weekly business hours (24h). Garage owners also mirror onto garages/{id}.
    val workingHours: com.example.mototap.core.util.WorkingHours? = null,
)

data class MechanicProfile(
    val id: String,
    val displayName: String,
    val skills: List<String>,
    val isAvailable: Boolean,
    val rating: Double,
    val approved: Boolean,
)

data class JobRequest(
    val id: String,
    val driverId: String,
    val mechanicId: String?,
    val issueType: String,
    val description: String = "",
    val locationLabel: String,
    val status: JobStatus,
    val price: Long,
    val createdAtMillis: Long,
    val garageId: String = "",
    val vehicleId: String = "",
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val serviceName: String = "",
    val serviceCategory: String = "",
    val driverName: String = "",
    val driverPhotoUrl: String = "",
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestampMillis: Long,
    val read: Boolean,
)

data class Review(
    val id: String = "",
    val mechanicId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val rating: Double = 0.0,
    val comment: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class PaymentRecord(
    val id: String,
    val jobId: String,
    val amount: Long,
    val status: String,
    val mpesaReceipt: String?,
)
