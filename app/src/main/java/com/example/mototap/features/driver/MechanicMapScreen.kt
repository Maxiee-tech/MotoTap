package com.example.mototap.features.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
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
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.util.formatDistanceMeters
import com.example.mototap.core.util.getMechanicServicePrice
import com.example.mototap.core.util.formatKsh
import com.example.mototap.ui.theme.MotoRed
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MechanicMapScreen(
    service: String,
    viewModel: DriverHomeViewModel,
    isAdmin: Boolean = false,
    onMechanicDetailsClick: (UserProfile) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val filteredMechanics = remember(uiState.availableMechanics, service) {
        val trimmedService = service.trim().lowercase()
        uiState.availableMechanics.filter { mechanic ->
            val hasService = mechanic.availableServices.any { it.trim().lowercase() == trimmedService } ||
                             mechanic.skills.any { it.trim().lowercase() == trimmedService }
            
            val containsService = mechanic.availableServices.any { it.trim().lowercase().contains(trimmedService) } ||
                                  mechanic.skills.any { it.trim().lowercase().contains(trimmedService) }

            (hasService || containsService) && mechanic.latitude != null && mechanic.longitude != null
        }
    }

    var selectedMechanic by remember { mutableStateOf<UserProfile?>(null) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Identify closest mechanic
    val closestMechanic = remember(filteredMechanics, uiState.userLocation) {
        val userLoc = uiState.userLocation ?: return@remember null
        filteredMechanics.minByOrNull { mechanic ->
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude, userLoc.longitude,
                mechanic.latitude!!, mechanic.longitude!!,
                results
            )
            results[0]
        }
    }

    // Auto-select closest mechanic on first load
    LaunchedEffect(closestMechanic) {
        if (selectedMechanic == null && closestMechanic != null) {
            selectedMechanic = closestMechanic
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            // Fetch location to find closest
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    viewModel.setUserLocation(LatLng(location.latitude, location.longitude))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Default center (Nairobi)
    val initialPos = LatLng(-1.286389, 36.817223)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 12f)
    }

    LaunchedEffect(filteredMechanics, uiState.userLocation) {
        if (uiState.userLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(uiState.userLocation!!, 13f)
            )
        } else if (filteredMechanics.isNotEmpty()) {
            val first = filteredMechanics.first()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude!!, first.longitude!!),
                    12f
                )
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MECHANICS: ${service.uppercase()}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = locationPermissionGranted,
                    mapType = MapType.NORMAL,
                    isTrafficEnabled = true
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = locationPermissionGranted
                )
            ) {
                filteredMechanics.forEach { mechanic ->
                    if (mechanic.latitude != null && mechanic.longitude != null) {
                        Marker(
                            state = MarkerState(position = LatLng(mechanic.latitude, mechanic.longitude)),
                            title = mechanic.name,
                            snippet = "Tap to view details",
                            onClick = {
                                selectedMechanic = mechanic
                                true
                            }
                        )
                    }
                }
            }

            selectedMechanic?.let { mechanic ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    MechanicInfoCard(
                        mechanic = mechanic,
                        service = service,
                        userLocation = uiState.userLocation,
                        isClosest = mechanic.id == closestMechanic?.id,
                        isAdmin = isAdmin,
                        vehicleMake = uiState.activeVehicleMake,
                        vehicleModel = uiState.activeVehicleModel,
                        onDismiss = { selectedMechanic = null },
                        onHeaderClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(mechanic.latitude!!, mechanic.longitude!!),
                                        15f
                                    )
                                )
                            }
                        },
                        onViewDetails = {
                            onMechanicDetailsClick(mechanic)
                        },
                        onCall = {
                            if (mechanic.phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${mechanic.phone}"))
                                context.startActivity(intent)
                            }
                        },
                        onMessage = {
                            if (mechanic.phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${mechanic.phone}"))
                                intent.putExtra("sms_body", "Hi, I'm interested in your services")
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            if (filteredMechanics.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "No mechanics found for this service on map",
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MechanicInfoCard(
    mechanic: UserProfile,
    service: String,
    userLocation: LatLng?,
    isClosest: Boolean = false,
    isAdmin: Boolean = false,
    vehicleMake: String = "",
    vehicleModel: String = "",
    onDismiss: () -> Unit,
    onHeaderClick: () -> Unit,
    onViewDetails: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit
) {
    val distanceMeters = remember(mechanic, userLocation) {
        val userLoc = userLocation
        val lat = mechanic.latitude
        val lng = mechanic.longitude
        if (userLoc == null || lat == null || lng == null) {
            null
        } else {
            val results = FloatArray(1)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, lat, lng, results)
            results[0]
        }
    }
    val servicePrice = getMechanicServicePrice(mechanic, service, vehicleMake, vehicleModel)
    val displayPhotoUrl = mechanic.profilePhotoUrl.takeIf { it.isNotBlank() }
    val areaLabel = mechanic.address.trim().takeIf { it.isNotEmpty() }
        ?.let { "Area: $it" }
        ?: "Location shared on map"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHeaderClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isClosest) {
                Surface(
                    color = MotoRed,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        "CLOSEST TO YOU",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!displayPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = displayPhotoUrl,
                        contentDescription = "Mechanic Photo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, MotoRed, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MotoRed,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mechanic.name,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    when {
                        distanceMeters != null -> {
                            Text(
                                text = formatDistanceMeters(distanceMeters),
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        userLocation == null -> {
                            Text(
                                text = "Calculating distance...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        mechanic.latitude == null || mechanic.longitude == null -> {
                            Text(
                                text = "Location not shared yet",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Text("X", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = areaLabel,
                color = Color.DarkGray,
                fontSize = 13.sp
            )
            val hoursStatus = com.example.mototap.core.util.getOpenClosedStatus(mechanic.workingHours)
            Text(
                text = hoursStatus.label,
                color = if (hoursStatus.isOpen) Color(0xFF0A7A32) else MotoRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            if (service.isNotBlank()) {
                Text(
                    text = "Service: $service",
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )
            }
            if (servicePrice != null && servicePrice > 0) {
                Text(
                    text = com.example.mototap.core.util.formatServicePriceAmount(servicePrice, service),
                    color = MotoRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MotoRed)
                ) {
                    Text("Details")
                }
                
                if (isAdmin) {
                    Button(
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call")
                    }
                    Button(
                        onClick = onMessage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SMS")
                    }
                }
            }
        }
    }
}
