package com.example.mototap.core.data.firebase

import android.util.Log
import com.example.mototap.core.model.JobAdditionalService
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus
import com.example.mototap.core.repository.JobRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreJobRepository(
    private val firestore: FirebaseFirestore,
) : JobRepository {

    private val jobs = firestore.collection("jobs")

    override suspend fun createJob(
        driverId: String,
        issueType: String,
        description: String,
        locationLabel: String,
        suggestedPrice: Long,
        mechanicId: String?,
        jobId: String?,
        garageId: String?,
        vehicleMake: String?,
        vehicleModel: String?,
        vehicleId: String?,
    ): Result<String> = runCatching {
        val doc = if (jobId != null) jobs.document(jobId) else jobs.document()
        doc.set(
            mapOf(
                "driverId" to driverId,
                "mechanicId" to mechanicId,
                "issueType" to issueType,
                "serviceName" to issueType,
                "description" to description,
                "locationLabel" to locationLabel,
                "status" to (if (mechanicId != null) JobStatus.ASSIGNED.name else JobStatus.REQUESTED.name),
                "price" to suggestedPrice,
                "createdAtMillis" to System.currentTimeMillis(),
                "garageId" to (garageId ?: ""),
                "vehicleMake" to (vehicleMake ?: ""),
                "vehicleModel" to (vehicleModel ?: ""),
                "vehicleId" to (vehicleId ?: ""),
            )
        ).await()
        doc.id
    }

    override fun observeGarageJobs(garageId: String): Flow<List<JobRequest>> = callbackFlow {
        if (garageId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val subscription = jobs
            .whereEqualTo("garageId", garageId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreJobRepo", "Error observing garage jobs: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot
                    ?.documents
                    ?.mapNotNull { it.toJobRequestOrNull() }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()
                trySend(list)
            }
        awaitClose {
            Log.d("FirestoreJobRepo", "Removing garage jobs observer")
            subscription.remove()
        }
    }

    override fun observeDriverJobs(driverId: String): Flow<List<JobRequest>> = callbackFlow {
        if (driverId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val subscription = jobs
            .whereEqualTo("driverId", driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreJobRepo", "Error observing driver jobs: ${error.message}")
                    trySend(emptyList()) 
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { it.toJobRequestOrNull() }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()

                trySend(list)
            }

        awaitClose { 
            Log.d("FirestoreJobRepo", "Removing driver jobs observer")
            subscription.remove() 
        }
    }

    override fun observeMechanicJobs(mechanicId: String): Flow<List<JobRequest>> = callbackFlow {
        if (mechanicId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val subscription = jobs
            .whereEqualTo("mechanicId", mechanicId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreJobRepo", "Error observing mechanic jobs: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { it.toJobRequestOrNull() }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()

                trySend(list)
            }

        awaitClose {
            Log.d("FirestoreJobRepo", "Removing mechanic jobs observer")
            subscription.remove()
        }
    }

    override fun observeOpenJobs(): Flow<List<JobRequest>> = callbackFlow {
        val subscription = jobs
            .whereIn(
                "status",
                listOf(
                    JobStatus.REQUESTED.name,
                    JobStatus.MATCHING.name,
                    JobStatus.ASSIGNED.name,
                )
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Critical: Just log the error and send empty list. 
                    // Do NOT use close(error) or throw any exception.
                    Log.e("FirestoreJobRepo", "Error observing open jobs: ${error.message}")
                    trySend(emptyList()) 
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { it.toJobRequestOrNull() }
                    ?.sortedByDescending { it.createdAtMillis }
                    .orEmpty()

                trySend(list)
            }

        awaitClose { 
            Log.d("FirestoreJobRepo", "Removing open jobs observer")
            subscription.remove() 
        }
    }

    override suspend fun addAdditionalServiceNote(
        jobId: String,
        note: JobAdditionalService,
    ): Result<Unit> = runCatching {
        val text = note.text.trim()
        require(jobId.isNotBlank() && text.isNotEmpty()) { "Missing job or additional service details." }
        jobs.document(jobId).update(
            "additionalServices",
            FieldValue.arrayUnion(
                mapOf(
                    "id" to note.id,
                    "authorId" to note.authorId,
                    "authorRole" to note.authorRole,
                    "authorName" to note.authorName.trim().take(120),
                    "text" to text.take(500),
                    "createdAtMillis" to note.createdAtMillis,
                )
            )
        ).await()
    }

    override suspend fun updateJobStatus(jobId: String, status: JobStatus): Result<Unit> = runCatching {
        jobs.document(jobId)
            .update("status", status.name)
            .await()
    }

    override suspend fun acceptJob(
        jobId: String,
        mechanicId: String,
        garageId: String?,
    ): Result<Unit> = runCatching {
        val payload = mutableMapOf<String, Any>(
            "mechanicId" to mechanicId,
            "status" to JobStatus.ASSIGNED.name,
        )
        garageId?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["garageId"] = it }
        jobs.document(jobId).update(payload).await()
    }

    override suspend fun deleteJob(jobId: String): Result<Unit> = runCatching {
        jobs.document(jobId).delete().await()
    }

    private fun DocumentSnapshot.toJobRequestOrNull(): JobRequest? {
        val driverId = getString("driverId") ?: return null
        val issueType = getString("issueType") ?: return null
        val locationLabel = getString("locationLabel") ?: return null
        val status = getString("status")
            ?.let { runCatching { JobStatus.valueOf(it) }.getOrNull() }
            ?: JobStatus.REQUESTED

        return JobRequest(
            id = id,
            driverId = driverId,
            mechanicId = getString("mechanicId"),
            issueType = issueType,
            description = getString("description") ?: "",
            locationLabel = locationLabel,
            status = status,
            price = getLong("price") ?: 0L,
            createdAtMillis = getLong("createdAtMillis") ?: 0L,
            garageId = getString("garageId") ?: "",
            vehicleId = getString("vehicleId") ?: "",
            vehicleMake = getString("vehicleMake") ?: "",
            vehicleModel = getString("vehicleModel") ?: "",
            serviceName = getString("serviceName") ?: "",
            serviceCategory = getString("serviceCategory") ?: "",
            driverName = getString("driverName") ?: "",
            driverPhotoUrl = getString("driverPhotoUrl") ?: "",
            additionalServices = parseAdditionalServices(get("additionalServices")),
        )
    }

    private fun parseAdditionalServices(raw: Any?): List<JobAdditionalService> {
        val items = raw as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val text = map["text"]?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            JobAdditionalService(
                id = map["id"]?.toString().orEmpty(),
                authorId = map["authorId"]?.toString().orEmpty(),
                authorRole = map["authorRole"]?.toString().orEmpty(),
                authorName = map["authorName"]?.toString().orEmpty(),
                text = text.take(500),
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: 0L,
            )
        }.take(30)
    }
}
