package com.example.mototap.core.data.firebase

import android.util.Log
import com.example.mototap.core.model.Garage
import com.example.mototap.core.model.GarageMember
import com.example.mototap.core.model.GarageMemberRole
import com.example.mototap.core.model.GarageMemberStatus
import com.example.mototap.core.model.GarageStatus
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.model.generateInviteCode
import com.example.mototap.core.model.normalizeInviteCode
import com.example.mototap.core.model.GARAGES_COLLECTION
import com.example.mototap.core.model.GARAGE_INVITES_COLLECTION
import com.example.mototap.core.repository.GarageRepository
import com.example.mototap.core.repository.InviteLookup
import com.example.mototap.core.util.normalizeServicePrices
import com.example.mototap.core.util.normalizeVehicleTypes
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseGarageRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : GarageRepository {

    private fun garageRef(garageId: String): DocumentReference =
        firestore.collection(GARAGES_COLLECTION).document(garageId)

    private fun memberRef(garageId: String, uid: String): DocumentReference =
        firestore.collection(GARAGES_COLLECTION).document(garageId)
            .collection("members").document(uid)

    private fun inviteRef(code: String): DocumentReference =
        firestore.collection(GARAGE_INVITES_COLLECTION).document(normalizeInviteCode(code))

    private fun userRef(uid: String): DocumentReference =
        firestore.collection("users").document(uid)

    private fun mapGarage(id: String, doc: DocumentSnapshot): Garage {
        val lat = doc.getDouble("latitude")
        val lng = doc.getDouble("longitude")
        val photos = (doc.get("garagePhotos") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val skills = (doc.get("skills") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return Garage(
            id = id,
            name = doc.getString("name")?.trim().orEmpty(),
            address = doc.getString("address")?.trim().orEmpty(),
            locationName = doc.getString("locationName")?.trim().orEmpty(),
            latitude = if (lat != null && lat.isFinite()) lat else null,
            longitude = if (lng != null && lng.isFinite()) lng else null,
            garagePhotos = photos,
            ownerId = doc.getString("ownerId")?.trim().orEmpty(),
            status = doc.getString("status")?.trim()?.ifEmpty { GarageStatus.PENDING } ?: GarageStatus.PENDING,
            inviteCode = normalizeInviteCode(doc.getString("inviteCode")),
            memberCount = (doc.getLong("memberCount") ?: 0L).toInt().coerceAtLeast(0),
            skills = skills,
            servicePrices = normalizeServicePrices(doc.get("servicePrices")),
            vehicleTypes = normalizeVehicleTypes(
                (doc.get("vehicleTypes") as? List<*>)?.mapNotNull { it?.toString() }
            ),
            workingHours = com.example.mototap.core.util.normalizeWorkingHours(doc.get("workingHours")),
            createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
            updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
        )
    }

    private fun mapMember(uid: String, doc: DocumentSnapshot): GarageMember {
        val role = doc.getString("role")?.trim()
        return GarageMember(
            uid = uid,
            displayName = doc.getString("displayName")?.trim().orEmpty(),
            role = if (role == GarageMemberRole.OWNER) GarageMemberRole.OWNER else GarageMemberRole.MECHANIC,
            status = doc.getString("status")?.trim()?.ifEmpty { GarageMemberStatus.ACTIVE }
                ?: GarageMemberStatus.ACTIVE,
            joinedAtMillis = doc.getLong("joinedAtMillis") ?: 0L,
        )
    }

    override suspend fun getGarage(garageId: String): Garage? {
        if (garageId.isBlank()) return null
        return try {
            val snap = garageRef(garageId).get().await()
            if (!snap.exists()) null else mapGarage(snap.id, snap)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "getGarage error: ${e.message}")
            null
        }
    }

    override suspend fun listMembers(garageId: String): List<GarageMember> {
        if (garageId.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection(GARAGES_COLLECTION).document(garageId)
                .collection("members").get().await()
            snap.documents
                .map { mapMember(it.id, it) }
                .filter { it.status != GarageMemberStatus.REMOVED }
                .sortedWith(compareByDescending<GarageMember> { it.role == GarageMemberRole.OWNER }
                    .thenBy { it.displayName.lowercase() })
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "listMembers error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun lookupInvite(code: String): InviteLookup? {
        val inviteCode = normalizeInviteCode(code)
        if (inviteCode.isEmpty()) return null
        return try {
            val inviteSnap = inviteRef(inviteCode).get().await()
            if (!inviteSnap.exists()) return null
            if (inviteSnap.getBoolean("active") == false) return null

            val garageId = inviteSnap.getString("garageId")?.trim().orEmpty()
            if (garageId.isEmpty()) return null

            val garage = getGarage(garageId) ?: return null
            if (garage.status == GarageStatus.REJECTED) return null

            InviteLookup(inviteCode = inviteCode, garage = garage)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "lookupInvite error: ${e.message}")
            null
        }
    }

    override suspend fun createGarageForOwner(ownerId: String, profile: UserProfile): Result<Garage> {
        val uid = ownerId.trim()
        if (uid.isEmpty()) return Result.failure(Exception("Missing owner id."))

        return try {
            val existingUser = userRef(uid).get().await()
            val existingGarageId = existingUser.getString("garageId")?.trim().orEmpty()
            if (existingUser.exists() && existingGarageId.isNotEmpty()) {
                val garage = getGarage(existingGarageId)
                if (garage != null) return Result.success(garage)
            }

            val inviteCode = allocateInviteCode()
            val newGarageRef = firestore.collection(GARAGES_COLLECTION).document()
            val now = System.currentTimeMillis()
            val name = (profile.institutionName.trim().ifEmpty { profile.name.trim() })
                .take(120).ifEmpty { "Garage" }

            val garageData = hashMapOf<String, Any?>(
                "name" to name,
                "address" to profile.address.trim().take(300),
                "locationName" to profile.locationName.trim().take(120),
                "latitude" to (profile.latitude?.takeIf { it.isFinite() }),
                "longitude" to (profile.longitude?.takeIf { it.isFinite() }),
                "garagePhotos" to profile.garagePhotos.take(5),
                "ownerId" to uid,
                "status" to GarageStatus.PENDING,
                "inviteCode" to inviteCode,
                "memberCount" to 1L,
                "skills" to emptyList<String>(),
                "servicePrices" to emptyMap<String, Any>(),
                "vehicleTypes" to emptyList<String>(),
                "workingHours" to (
                    profile.workingHours?.let {
                        com.example.mototap.core.util.workingHoursToFirestoreMap(it)
                    } ?: emptyMap<String, Any>()
                ),
                "createdAtMillis" to now,
                "updatedAtMillis" to now,
            )

            // Garage must exist before invite/member rules can verify ownership.
            newGarageRef.set(garageData).await()

            val batch = firestore.batch()
            batch.set(
                memberRef(newGarageRef.id, uid),
                mapOf(
                    "uid" to uid,
                    "displayName" to profile.name.trim().take(120),
                    "role" to GarageMemberRole.OWNER,
                    "status" to GarageMemberStatus.ACTIVE,
                    "joinedAtMillis" to now,
                )
            )
            batch.set(
                inviteRef(inviteCode),
                mapOf(
                    "garageId" to newGarageRef.id,
                    "ownerId" to uid,
                    "active" to true,
                    "createdAtMillis" to now,
                )
            )
            batch.update(
                userRef(uid),
                mapOf(
                    "garageId" to newGarageRef.id,
                    "garageRole" to GarageMemberRole.OWNER,
                    "institutionName" to name,
                )
            )
            batch.commit().await()

            Result.success(mapGarage(newGarageRef.id, newGarageRef.get().await()))
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "createGarageForOwner error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun joinGarageWithInvite(
        userId: String,
        inviteCode: String,
        profile: UserProfile,
    ): Result<Garage> {
        val uid = userId.trim()
        if (uid.isEmpty()) return Result.failure(Exception("Missing user id."))

        return try {
            val lookup = lookupInvite(inviteCode)
                ?: return Result.failure(Exception("Invalid or expired garage invite code."))
            val garage = lookup.garage

            val userSnap = userRef(uid).get().await()
            if (!userSnap.exists()) {
                return Result.failure(Exception("Complete account setup before joining a garage."))
            }

            var existingGarageId = userSnap.getString("garageId")?.trim().orEmpty()
            if (existingGarageId.isNotEmpty() && existingGarageId != garage.id) {
                val existingGarage = getGarage(existingGarageId)
                val ownsSoloGarage = existingGarage != null &&
                    existingGarage.ownerId == uid &&
                    existingGarage.memberCount.coerceAtLeast(1) <= 1

                if (!ownsSoloGarage) {
                    return Result.failure(Exception("You already belong to another garage."))
                }

                // Abandon the accidental solo garage so invite join can proceed.
                try {
                    memberRef(existingGarageId, uid).set(
                        mapOf(
                            "uid" to uid,
                            "displayName" to (userSnap.getString("name")?.trim()?.take(120) ?: ""),
                            "role" to GarageMemberRole.OWNER,
                            "status" to GarageMemberStatus.REMOVED,
                            "joinedAtMillis" to System.currentTimeMillis(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                } catch (e: Exception) {
                    Log.w("FirebaseGarageRepo", "Could not mark solo garage membership removed: ${e.message}")
                }

                userRef(uid).update(mapOf("garageId" to "", "garageRole" to "")).await()
                existingGarageId = ""
            }

            if (existingGarageId == garage.id) {
                val existing = getMember(garage.id, uid)
                if (existing?.status == GarageMemberStatus.ACTIVE ||
                    existing?.status == GarageMemberStatus.PENDING
                ) {
                    return Result.success(garage)
                }
            }
            if (garage.ownerId == uid) return Result.success(garage)

            val now = System.currentTimeMillis()
            memberRef(garage.id, uid).set(
                mapOf(
                    "uid" to uid,
                    "displayName" to (profile.name.trim().ifEmpty {
                        userSnap.getString("name")?.trim().orEmpty()
                    }).take(120),
                    "role" to GarageMemberRole.MECHANIC,
                    "status" to GarageMemberStatus.PENDING,
                    "joinedAtMillis" to now,
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            userRef(uid).update(
                mapOf(
                    "garageId" to garage.id,
                    "garageRole" to GarageMemberRole.MECHANIC,
                    "garageMemberStatus" to GarageMemberStatus.PENDING,
                    "institutionName" to garage.name,
                    "address" to (garage.address),
                    "locationName" to garage.locationName,
                    "latitude" to garage.latitude,
                    "longitude" to garage.longitude,
                    "garagePhotos" to garage.garagePhotos,
                )
            ).await()

            Result.success(garage)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "joinGarageWithInvite error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getMember(garageId: String, memberId: String): GarageMember? {
        if (garageId.isBlank() || memberId.isBlank()) return null
        return try {
            val snap = memberRef(garageId, memberId).get().await()
            if (!snap.exists()) null else mapMember(memberId, snap)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "getMember error: ${e.message}")
            null
        }
    }

    override suspend fun approveMember(garageId: String, ownerId: String, memberId: String): Result<Unit> {
        return try {
            val garage = getGarage(garageId)
                ?: return Result.failure(Exception("Garage not found."))
            if (garage.ownerId != ownerId) {
                return Result.failure(Exception("Only the garage owner can approve join requests."))
            }
            val member = getMember(garageId, memberId)
                ?: return Result.failure(Exception("Join request not found."))
            if (member.status == GarageMemberStatus.ACTIVE) return Result.success(Unit)

            memberRef(garageId, memberId).set(
                mapOf(
                    "uid" to memberId,
                    "displayName" to member.displayName,
                    "role" to GarageMemberRole.MECHANIC,
                    "status" to GarageMemberStatus.ACTIVE,
                    "joinedAtMillis" to (member.joinedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()),
                    "approvedAtMillis" to System.currentTimeMillis(),
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            try {
                garageRef(garageId).update(
                    mapOf(
                        "memberCount" to (garage.memberCount.coerceAtLeast(1) + 1),
                        "updatedAtMillis" to System.currentTimeMillis(),
                    )
                ).await()
            } catch (e: Exception) {
                Log.w("FirebaseGarageRepo", "Could not bump memberCount: ${e.message}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "approveMember error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rejectMember(garageId: String, ownerId: String, memberId: String): Result<Unit> {
        return try {
            val garage = getGarage(garageId)
                ?: return Result.failure(Exception("Garage not found."))
            if (garage.ownerId != ownerId) {
                return Result.failure(Exception("Only the garage owner can reject join requests."))
            }
            val member = getMember(garageId, memberId)
                ?: return Result.failure(Exception("Join request not found."))

            memberRef(garageId, memberId).set(
                mapOf(
                    "uid" to memberId,
                    "displayName" to member.displayName,
                    "role" to (member.role.ifBlank { GarageMemberRole.MECHANIC }),
                    "status" to GarageMemberStatus.REMOVED,
                    "joinedAtMillis" to (member.joinedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis()),
                    "rejectedAtMillis" to System.currentTimeMillis(),
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "rejectMember error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun regenerateInviteCode(garageId: String, ownerId: String): Result<String> {
        return try {
            val garage = getGarage(garageId)
                ?: return Result.failure(Exception("Garage not found."))
            if (garage.ownerId != ownerId) {
                return Result.failure(Exception("Only the garage owner can refresh the invite code."))
            }

            val newCode = allocateInviteCode()
            val now = System.currentTimeMillis()
            val batch = firestore.batch()

            if (garage.inviteCode.isNotEmpty()) {
                batch.set(
                    inviteRef(garage.inviteCode),
                    mapOf(
                        "active" to false,
                        "garageId" to garage.id,
                        "ownerId" to ownerId,
                        "updatedAtMillis" to now,
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
            batch.set(
                inviteRef(newCode),
                mapOf(
                    "garageId" to garage.id,
                    "ownerId" to ownerId,
                    "active" to true,
                    "createdAtMillis" to now,
                )
            )
            batch.update(
                garageRef(garage.id),
                mapOf("inviteCode" to newCode, "updatedAtMillis" to now)
            )
            batch.commit().await()
            Result.success(newCode)
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "regenerateInviteCode error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateGarageCatalog(
        garageId: String,
        ownerId: String,
        skills: List<String>,
        servicePrices: Map<String, Map<String, Long>>,
        vehicleTypes: List<String>,
    ): Result<Garage> {
        return try {
            val garage = getGarage(garageId)
                ?: return Result.failure(Exception("Garage not found."))
            if (garage.ownerId != ownerId) {
                return Result.failure(Exception("Only the garage owner can update garage prices."))
            }

            val nextSkills = skills.map { it.trim() }.filter { it.isNotEmpty() }.take(50)
            val nextPrices = normalizeServicePrices(servicePrices)
            val nextVehicleTypes = normalizeVehicleTypes(vehicleTypes)

            garageRef(garageId).update(
                mapOf(
                    "skills" to nextSkills,
                    "servicePrices" to nextPrices,
                    "vehicleTypes" to nextVehicleTypes,
                    "updatedAtMillis" to System.currentTimeMillis(),
                )
            ).await()

            Result.success(
                garage.copy(
                    skills = nextSkills,
                    servicePrices = nextPrices,
                    vehicleTypes = nextVehicleTypes,
                )
            )
        } catch (e: Exception) {
            Log.e("FirebaseGarageRepo", "updateGarageCatalog error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun ensureOwnerGarage(userId: String, profile: UserProfile): Result<Garage> {
        val uid = userId.trim()
        if (uid.isEmpty()) return Result.failure(Exception("Missing user id."))

        if (profile.garageId.isNotEmpty()) {
            val garage = getGarage(profile.garageId)
            if (garage != null) return Result.success(garage)
        }
        return createGarageForOwner(uid, profile)
    }

    override suspend fun allocateInviteCode(maxAttempts: Int): String {
        repeat(maxAttempts) {
            val code = generateInviteCode()
            val snap = inviteRef(code).get().await()
            if (!snap.exists()) return code
        }
        throw Exception("Could not allocate a unique invite code. Please try again.")
    }
}
