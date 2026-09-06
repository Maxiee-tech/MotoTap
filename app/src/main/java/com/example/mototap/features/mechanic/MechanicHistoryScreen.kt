package com.example.mototap.features.mechanic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.model.JobRequest
import com.example.mototap.core.model.JobStatus
import com.example.mototap.core.util.JobAdditionalServices
import com.example.mototap.features.driver.formatJobStatus
import com.example.mototap.features.jobs.JobAdditionalServicesSection
import com.example.mototap.features.driver.jobTitle
import com.example.mototap.ui.theme.MotoRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechanicHistoryScreen(
    viewModel: MechanicDashboardViewModel,
    onBack: () -> Unit,
    onMessageDriver: (driverId: String, driverName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Same set as website MECHANIC_HISTORY_STATUSES / paintRequestHistory.
    val allJobs = remember(uiState.ongoingJobs, uiState.completedJobs) {
        (uiState.ongoingJobs + uiState.completedJobs)
            .filter { !it.issueType.equals("Inquiry", ignoreCase = true) }
            .sortedByDescending { it.createdAtMillis }
    }

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
        if (allJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No accepted jobs yet. Jobs you accept in the app or on the web will appear here.",
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
                items(allJobs, key = { it.id }) { job ->
                    MechanicRequestCard(
                        job = job,
                        currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
                        isGarageOwner = uiState.isGarageOwner,
                        isGarageMember = uiState.isGarageMember,
                        onAddAdditionalService = { text ->
                            viewModel.addAdditionalServiceNote(job.id, text)
                        },
                        onMessageDriver = {
                            onMessageDriver(
                                job.driverId,
                                job.driverName.ifBlank { "Driver" },
                            )
                        },
                        onStartJob = {
                            viewModel.updateStatus(job.id, JobStatus.IN_PROGRESS)
                        },
                        onCompleteJob = {
                            viewModel.updateStatus(job.id, JobStatus.COMPLETED)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MechanicRequestCard(
    job: JobRequest,
    currentUserId: String?,
    isGarageOwner: Boolean,
    isGarageMember: Boolean,
    onAddAdditionalService: (String) -> Unit,
    onMessageDriver: () -> Unit,
    onStartJob: () -> Unit,
    onCompleteJob: () -> Unit,
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = sdf.format(Date(job.createdAtMillis))
    val vehicleBits = listOf(job.vehicleMake, job.vehicleModel).filter { it.isNotBlank() }
    val vehicleSuffix = if (vehicleBits.isNotEmpty()) " (${vehicleBits.joinToString(" ")})" else ""
    val showMessage = job.status == JobStatus.ASSIGNED || job.status == JobStatus.IN_PROGRESS
    val progressLabel = when (job.status) {
        JobStatus.ASSIGNED -> "Start Job"
        JobStatus.IN_PROGRESS -> "Mark as Complete"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.DarkGray),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = Color.DarkGray,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (job.driverName.ifBlank { "D" }).first().uppercaseChar().toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = jobTitle(job),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    if (job.driverName.isNotBlank()) {
                        Text(
                            text = "Driver: ${job.driverName}",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Surface(
                    color = when (job.status.name) {
                        "COMPLETED", "PAID" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        "IN_PROGRESS" -> MotoRed.copy(alpha = 0.2f)
                        else -> Color.Blue.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = job.status.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Status: ${formatJobStatus(job.status)}", color = Color.LightGray, fontSize = 13.sp)
            Text("Submitted: $dateString", color = Color.LightGray, fontSize = 13.sp)
            Text(
                "Location: ${job.locationLabel.ifBlank { "Not provided" }}",
                color = Color.LightGray,
                fontSize = 13.sp,
            )
            Text(
                "Details: ${job.description.ifBlank { "No additional details" }}",
                color = Color.LightGray,
                fontSize = 13.sp,
            )
            Text(
                text = "Offered Price$vehicleSuffix: KSh ${job.price}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))
            JobAdditionalServicesSection(
                notes = job.additionalServices,
                canAdd = JobAdditionalServices.canAdd(
                    job,
                    currentUserId,
                    isGarageOwner = isGarageOwner,
                    isGarageMember = isGarageMember,
                ),
                onAdd = onAddAdditionalService,
            )

            if (showMessage || progressLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showMessage) {
                        OutlinedButton(
                            onClick = onMessageDriver,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Text("Message Driver", fontSize = 12.sp)
                        }
                    }
                    if (progressLabel != null) {
                        Button(
                            onClick = {
                                if (job.status == JobStatus.ASSIGNED) onStartJob() else onCompleteJob()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                        ) {
                            Text(progressLabel, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
