package com.example.mototap.core.util

import com.example.mototap.core.model.JobAdditionalService
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus

object JobAdditionalServices {
    const val MAX_NOTES = 30
    const val MAX_TEXT = 500

    private val writableStatuses = setOf(
        JobStatus.ASSIGNED,
        JobStatus.IN_PROGRESS,
        JobStatus.COMPLETED,
        JobStatus.PAID,
    )

    fun canAdd(
        job: JobRequest,
        userId: String?,
        isGarageOwner: Boolean = false,
        isGarageMember: Boolean = false,
    ): Boolean {
        val uid = userId?.trim().orEmpty()
        if (uid.isEmpty() || job.id.isBlank()) return false
        if (job.status !in writableStatuses) return false
        if (job.additionalServices.size >= MAX_NOTES) return false
        if (job.driverId == uid) return true
        if (job.mechanicId == uid) return true
        return job.garageId.isNotBlank() && (isGarageOwner || isGarageMember)
    }

    fun authorRole(
        job: JobRequest,
        userId: String?,
        isGarageOwner: Boolean = false,
        isGarageMember: Boolean = false,
    ): String {
        val uid = userId?.trim().orEmpty()
        return when {
            job.driverId == uid -> "driver"
            isGarageOwner -> "garage"
            job.mechanicId == uid -> "mechanic"
            isGarageMember -> "garage"
            else -> "mechanic"
        }
    }

    fun authorLabel(note: JobAdditionalService): String {
        val name = note.authorName.trim()
        return when (note.authorRole) {
            "driver" -> if (name.isNotEmpty()) "$name (client)" else "Client"
            "garage" -> if (name.isNotEmpty()) "$name (garage)" else "Garage"
            else -> if (name.isNotEmpty()) "$name (mechanic)" else "Mechanic"
        }
    }

    fun createNote(
        userId: String,
        authorRole: String,
        authorName: String,
        text: String,
    ): JobAdditionalService {
        val cleaned = text.trim().take(MAX_TEXT)
        val role = when (authorRole) {
            "driver", "garage", "mechanic" -> authorRole
            else -> "mechanic"
        }
        return JobAdditionalService(
            id = "asn_${System.currentTimeMillis()}_${(1000..9999).random()}",
            authorId = userId.trim(),
            authorRole = role,
            authorName = authorName.trim().take(120),
            text = cleaned,
            createdAtMillis = System.currentTimeMillis(),
        )
    }
}
