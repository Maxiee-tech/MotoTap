package com.example.mototap.core.model

/**
 * Garage org model — field names aligned with web (js/models/Garage.js):
 * garages/{id}, garages/{id}/members/{uid}, garageInvites/{code},
 * users.garageId / garageRole, jobs.garageId, garages.skills / servicePrices / vehicleTypes.
 */

object GarageMemberRole {
    const val OWNER = "owner"
    const val MECHANIC = "mechanic"
}

object GarageMemberStatus {
    const val INVITED = "invited"
    const val PENDING = "pending"
    const val ACTIVE = "active"
    const val REMOVED = "removed"
}

object GarageStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
}

const val GARAGES_COLLECTION = "garages"
const val GARAGE_INVITES_COLLECTION = "garageInvites"

/** Alphabet for shareable invite codes (excludes ambiguous chars I/O/0/1). */
const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** Generate a short shareable invite code (e.g. MT7K2Q). */
fun generateInviteCode(length: Int = 6): String {
    val sb = StringBuilder(length)
    repeat(length) {
        sb.append(INVITE_ALPHABET[(INVITE_ALPHABET.indices).random()])
    }
    return sb.toString()
}

/** Normalize an invite code: trim + UPPER + strip non-alphanumeric. */
fun normalizeInviteCode(raw: String?): String =
    raw.orEmpty().trim().uppercase().replace(Regex("[^A-Z0-9]"), "")

data class Garage(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val garagePhotos: List<String> = emptyList(),
    val ownerId: String = "",
    val status: String = GarageStatus.PENDING,
    val inviteCode: String = "",
    val memberCount: Int = 0,
    val skills: List<String> = emptyList(),
    val servicePrices: Map<String, Map<String, Long>> = emptyMap(),
    val vehicleTypes: List<String> = emptyList(),
    val workingHours: com.example.mototap.core.util.WorkingHours? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

data class GarageMember(
    val uid: String = "",
    val displayName: String = "",
    val role: String = GarageMemberRole.MECHANIC,
    val status: String = GarageMemberStatus.ACTIVE,
    val joinedAtMillis: Long = 0L,
)
