package com.example.mototap.features.driver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.model.Review
import com.example.mototap.core.util.formatKsh
import com.example.mototap.core.util.getMechanicServicePrice
import com.example.mototap.core.util.mechanicServiceInventory
import com.example.mototap.ui.theme.MotoRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechanicDetailsPage(
    mechanic: UserProfile,
    selectedService: String = "",
    reviews: List<Review> = emptyList(),
    isAdmin: Boolean = false,
    vehicleMake: String = "",
    vehicleModel: String = "",
    onBack: () -> Unit,
    onChat: () -> Unit,
    onBookService: (serviceName: String, estimatedKm: Double?) -> Unit,
) {
    val context = LocalContext.current
    val services = mechanicServiceInventory(mechanic)
    val highlightedService = selectedService.trim().takeIf { it.isNotEmpty() }
    val highlightedPrice = highlightedService?.let {
        getMechanicServicePrice(mechanic, it, vehicleMake, vehicleModel)
    }
    var showTowingKmDialog by remember { mutableStateOf(false) }
    var towingKmText by remember { mutableStateOf("") }
    var towingKmError by remember { mutableStateOf<String?>(null) }

    fun requestBook(serviceName: String) {
        if (com.example.mototap.core.util.isTowingService(serviceName)) {
            towingKmText = ""
            towingKmError = null
            showTowingKmDialog = true
        } else {
            onBookService(serviceName, null)
        }
    }

    if (showTowingKmDialog) {
        val rate = highlightedPrice ?: 0L
        val km = towingKmText.toDoubleOrNull()
        val estimate = km?.let { com.example.mototap.core.util.estimateTowingTotal(rate, it) }
        AlertDialog(
            onDismissRequest = { showTowingKmDialog = false },
            title = { Text("Towing distance", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = if (rate > 0) {
                            "Rate: KSh ${formatKsh(rate)}/km — enter estimated kilometres."
                        } else {
                            "Enter estimated kilometres for this tow."
                        },
                        color = Color.LightGray,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = towingKmText,
                        onValueChange = {
                            towingKmText = it.filter { ch -> ch.isDigit() || ch == '.' }
                            towingKmError = null
                        },
                        label = { Text("Kilometres") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MotoRed,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = MotoRed,
                            focusedLabelColor = Color.Gray,
                            unfocusedLabelColor = Color.Gray,
                        ),
                    )
                    if (estimate != null) {
                        Text(
                            text = "Estimated total: KSh ${formatKsh(estimate)}",
                            color = MotoRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    towingKmError?.let {
                        Text(text = it, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = towingKmText.toDoubleOrNull()
                        if (parsed == null || parsed <= 0.0) {
                            towingKmError = "Enter a valid distance in km."
                            return@TextButton
                        }
                        if (rate <= 0L) {
                            towingKmError =
                                "Unable to estimate towing total. Check the rate and distance."
                            return@TextButton
                        }
                        showTowingKmDialog = false
                        onBookService(highlightedService ?: "General Maintenance", parsed)
                    }
                ) {
                    Text("BOOK", color = MotoRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTowingKmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1A1A),
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Mechanic Details",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MotoRed
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Photo & Mechanic Name - Fallback to certificate if profile photo is missing
            val displayPhotoUrl = mechanic.profilePhotoUrl.takeIf { it.isNotBlank() }
                ?: mechanic.certificatePhotoUrl.takeIf { it.isNotBlank() }

            if (!displayPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = displayPhotoUrl,
                    contentDescription = "Mechanic Photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MotoRed, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MotoRed.copy(alpha = 0.1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MotoRed,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = mechanic.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Rating Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${mechanic.rating} (${mechanic.reviewCount} reviews)",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            // Institution/Garage Name
            Text(
                text = mechanic.institutionName.ifBlank { "Professional Mechanic" },
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (highlightedService != null) {
                Text(
                    text = "Service: $highlightedService",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                if (highlightedPrice != null && highlightedPrice > 0) {
                    Text(
                        text = com.example.mototap.core.util.formatServicePriceLabel(
                            highlightedPrice,
                            highlightedService,
                        ),
                        color = MotoRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Location Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Location",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = mechanic.address.ifBlank { "Location not specified" },
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }

            // Experience Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Experience & Expertise",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Experience: ${mechanic.experienceYears.ifBlank { "Not specified" }}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (mechanic.brandSpecializations.isNotEmpty()) {
                        Text(
                            text = "Specializes in: ${mechanic.brandSpecializations.joinToString(", ")}",
                            color = MotoRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Available Services Card
            if (services.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Available Services",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            services.forEach { service ->
                                val price = getMechanicServicePrice(mechanic, service, vehicleMake, vehicleModel)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "• ",
                                            color = MotoRed,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = service,
                                            color = Color.LightGray,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    if (price != null && price > 0) {
                                        Text(
                                            text = com.example.mototap.core.util.formatServicePriceAmount(price, service),
                                            color = MotoRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                if (services.last() != service) {
                                    HorizontalDivider(color = Color.Black.copy(alpha = 0.3f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Phone Number Card
            if (isAdmin) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Contact",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = mechanic.phone.ifBlank { "Phone not available" },
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Call Button
                    Button(
                        onClick = {
                            if (mechanic.phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${mechanic.phone}"))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call", fontWeight = FontWeight.Bold)
                    }

                    // Message Button
                    Button(
                        onClick = {
                            if (mechanic.phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${mechanic.phone}"))
                                intent.putExtra("sms_body", "Hi, I'm interested in your services")
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Message",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SMS", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Chat Button
            Button(
                onClick = onChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Chat with Mechanic", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            // Booking Button
            Button(
                onClick = {
                    requestBook(highlightedService ?: "General Maintenance")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("BOOK NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Reviews Section
            if (reviews.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Customer Reviews",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    reviews.forEach { review ->
                        ReviewItem(review)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewItem(review: Review) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateString = sdf.format(Date(review.timestampMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = review.driverName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = dateString,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                for (i in 1..5) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (i <= review.rating) Color(0xFFFFD700) else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            if (review.comment.isNotBlank()) {
                Text(
                    text = review.comment,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

