package com.example.mototap.features.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus
import com.example.mototap.core.util.JobAdditionalServices
import com.example.mototap.features.jobs.JobAdditionalServicesSection
import com.example.mototap.ui.theme.MotoRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestHistoryScreen(
    viewModel: DriverHomeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Match website driver REQUESTS: real bookings only (no chat-inquiry stubs).
    val sortedJobs = uiState.jobs
        .filter { !it.issueType.equals("Inquiry", ignoreCase = true) }
        .sortedByDescending { it.createdAtMillis }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "REQUESTS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MotoRed,
                ),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        if (sortedJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No past requests yet. Your submitted requests will show here.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sortedJobs, key = { it.id }) { job ->
                    DriverRequestCard(
                        job = job,
                        currentUserId = uiState.currentUserId,
                        onDelete = { viewModel.deleteRequest(job.id) },
                        onAddAdditionalService = { text ->
                            viewModel.addAdditionalServiceNote(job.id, text)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverRequestCard(
    job: JobRequest,
    currentUserId: String?,
    onDelete: () -> Unit,
    onAddAdditionalService: (String) -> Unit,
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = sdf.format(Date(job.createdAtMillis))
    val title = jobTitle(job)
    val vehicleBits = listOf(job.vehicleMake, job.vehicleModel).filter { it.isNotBlank() }
    val vehicleSuffix = if (vehicleBits.isNotEmpty()) " (${vehicleBits.joinToString(" ")})" else ""
    val canDelete = job.status == JobStatus.REQUESTED ||
        job.status == JobStatus.MATCHING ||
        job.status == JobStatus.CLOSED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.DarkGray),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = job.status.name)
            }

            Spacer(modifier = Modifier.height(10.dp))
            MetaLine("Status: ${formatJobStatus(job.status)}")
            MetaLine("Submitted: $dateString")
            MetaLine("Location: ${job.locationLabel.ifBlank { "Not provided" }}")
            MetaLine("Details: ${job.description.ifBlank { "No additional details" }}")
            Text(
                text = "Offered Price$vehicleSuffix: KSh ${job.price}",
                color = MotoRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))
            JobAdditionalServicesSection(
                notes = job.additionalServices,
                canAdd = JobAdditionalServices.canAdd(job, currentUserId),
                onAdd = onAddAdditionalService,
            )

            if (canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    Surface(
        color = when (status) {
            "COMPLETED", "PAID" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
            "IN_PROGRESS" -> MotoRed.copy(alpha = 0.2f)
            "REQUESTED", "MATCHING" -> Color(0xFF2196F3).copy(alpha = 0.2f)
            else -> Color.Gray.copy(alpha = 0.2f)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = when (status) {
                "COMPLETED", "PAID" -> Color(0xFF4CAF50)
                "IN_PROGRESS" -> MotoRed
                "REQUESTED", "MATCHING" -> Color(0xFF2196F3)
                else -> Color.White
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(
        text = text,
        color = Color.LightGray,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

internal fun jobTitle(job: JobRequest): String {
    val issue = job.issueType.ifBlank { job.serviceName }.ifBlank { "Service" }
    return if (job.serviceCategory.isNotBlank()) {
        "$issue - ${job.serviceCategory}"
    } else {
        issue
    }
}

internal fun formatJobStatus(status: JobStatus): String = when (status) {
    JobStatus.REQUESTED -> "Requested"
    JobStatus.MATCHING -> "Matching"
    JobStatus.ASSIGNED -> "Assigned"
    JobStatus.IN_PROGRESS -> "In progress"
    JobStatus.COMPLETED -> "Completed"
    JobStatus.PAID -> "Paid"
    JobStatus.CLOSED -> "Closed"
    JobStatus.INQUIRY -> "Inquiry"
}
