package com.example.mototap.core.repository

import com.example.mototap.core.model.JobAdditionalService
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus
import kotlinx.coroutines.flow.Flow

interface JobRepository {
    suspend fun createJob(
        driverId: String,
        issueType: String,
        description: String,
        locationLabel: String,
        suggestedPrice: Long,
        mechanicId: String? = null,
        jobId: String? = null,
        garageId: String? = null,
        vehicleMake: String? = null,
        vehicleModel: String? = null,
        vehicleId: String? = null,
    ): Result<String>

    fun observeGarageJobs(garageId: String): Flow<List<JobRequest>>

    fun observeDriverJobs(driverId: String): Flow<List<JobRequest>>

    fun observeMechanicJobs(mechanicId: String): Flow<List<JobRequest>>

    fun observeOpenJobs(): Flow<List<JobRequest>>

    suspend fun addAdditionalServiceNote(jobId: String, note: JobAdditionalService): Result<Unit>

    suspend fun updateJobStatus(jobId: String, status: JobStatus): Result<Unit>
    
    suspend fun acceptJob(jobId: String, mechanicId: String, garageId: String? = null): Result<Unit>

    suspend fun deleteJob(jobId: String): Result<Unit>
}
