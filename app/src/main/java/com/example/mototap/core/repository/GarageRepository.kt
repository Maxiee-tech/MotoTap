package com.example.mototap.core.repository

import com.example.mototap.core.model.Garage
import com.example.mototap.core.model.GarageMember
import com.example.mototap.core.model.UserProfile

/** Result of resolving an invite code to a joinable garage. */
data class InviteLookup(
    val inviteCode: String,
    val garage: Garage,
)

interface GarageRepository {
    suspend fun getGarage(garageId: String): Garage?

    suspend fun listMembers(garageId: String): List<GarageMember>

    suspend fun lookupInvite(code: String): InviteLookup?

    /** Create a garage owned by the mechanic (garage-of-one). */
    suspend fun createGarageForOwner(ownerId: String, profile: UserProfile): Result<Garage>

    /** Join via invite — creates a pending membership until the owner approves. */
    suspend fun joinGarageWithInvite(
        userId: String,
        inviteCode: String,
        profile: UserProfile,
    ): Result<Garage>

    suspend fun getMember(garageId: String, memberId: String): GarageMember?

    suspend fun approveMember(garageId: String, ownerId: String, memberId: String): Result<Unit>

    suspend fun rejectMember(garageId: String, ownerId: String, memberId: String): Result<Unit>

    suspend fun regenerateInviteCode(garageId: String, ownerId: String): Result<String>

    suspend fun updateGarageCatalog(
        garageId: String,
        ownerId: String,
        skills: List<String>,
        servicePrices: Map<String, Map<String, Long>>,
        vehicleTypes: List<String> = emptyList(),
    ): Result<Garage>

    /** Ensure a solo mechanic has a garage document (lazy migration). */
    suspend fun ensureOwnerGarage(userId: String, profile: UserProfile): Result<Garage>

    suspend fun allocateInviteCode(maxAttempts: Int = 8): String
}
