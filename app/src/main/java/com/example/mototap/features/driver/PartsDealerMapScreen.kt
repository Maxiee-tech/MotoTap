package com.example.mototap.features.driver

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mototap.core.model.UserProfile
import com.example.mototap.core.util.formatPlaceAndDistance
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val PartsOrange = Color(0xFFFF8800)

private fun dealerStocksPart(dealer: UserProfile, part: String): Boolean {
    val trimmed = part.trim().lowercase()
    val inventory = (dealer.parts + dealer.availableParts)
    val exact = inventory.any { it.trim().lowercase() == trimmed }
    val contains = inventory.any { it.trim().lowercase().contains(trimmed) }
    return exact || contains
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsDealerMapScreen(
    part: String,
    viewModel: DriverHomeViewModel,
    onDealerDetailsClick: (UserProfile) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filteredDealers = remember(uiState.availablePartsDealers, part) {
        uiState.availablePartsDealers.filter { dealer ->
            dealerStocksPart(dealer, part) && dealer.latitude != null && dealer.longitude != null
        }
    }

    var selectedDealer by remember { mutableStateOf<UserProfile?>(null) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val closestDealer = remember(filteredDealers, uiState.userLocation) {
        val userLoc = uiState.userLocation ?: return@remember null
        filteredDealers.minByOrNull { dealer ->
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude, userLoc.longitude,
                dealer.latitude!!, dealer.longitude!!,
                results
            )
            results[0]
        }
    }

    LaunchedEffect(closestDealer) {
        if (selectedDealer == null && closestDealer != null) {
            selectedDealer = closestDealer
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

    val initialPos = LatLng(-1.286389, 36.817223)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, 12f)
    }

    LaunchedEffect(filteredDealers, uiState.userLocation) {
        if (uiState.userLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(uiState.userLocation!!, 13f)
            )
        } else if (filteredDealers.isNotEmpty()) {
            val first = filteredDealers.first()
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
                        text = "PARTS DEALERS: ${part.uppercase()}",
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
                    containerColor = PartsOrange
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
                filteredDealers.forEach { dealer ->
                    if (dealer.latitude != null && dealer.longitude != null) {
                        Marker(
                            state = MarkerState(position = LatLng(dealer.latitude, dealer.longitude)),
                            title = dealer.name,
                            snippet = "Tap to view parts",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                            onClick = {
                                selectedDealer = dealer
                                true
                            }
                        )
                    }
                }
            }

            selectedDealer?.let { dealer ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    PartsDealerInfoCard(
                        dealer = dealer,
                        part = part,
                        userLocation = uiState.userLocation,
                        isClosest = dealer.id == closestDealer?.id,
                        onDismiss = { selectedDealer = null },
                        onHeaderClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(dealer.latitude!!, dealer.longitude!!),
                                        15f
                                    )
                                )
                            }
                        },
                        onViewDetails = { onDealerDetailsClick(dealer) }
                    )
                }
            }

            if (filteredDealers.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "No parts dealers found stocking this part on map",
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
fun PartsDealerInfoCard(
    dealer: UserProfile,
    part: String,
    userLocation: LatLng? = null,
    isClosest: Boolean = false,
    onDismiss: () -> Unit,
    onHeaderClick: () -> Unit,
    onViewDetails: () -> Unit,
) {
    val price = dealer.partPrices[part]
        ?: dealer.partPrices.entries.firstOrNull { it.key.trim().lowercase() == part.trim().lowercase() }?.value
    val distanceMeters = remember(dealer, userLocation) {
        val userLoc = userLocation
        val lat = dealer.latitude
        val lng = dealer.longitude
        if (userLoc == null || lat == null || lng == null) {
            null
        } else {
            val results = FloatArray(1)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, lat, lng, results)
            results[0]
        }
    }

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
                    color = PartsOrange,
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
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = PartsOrange,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dealer.institutionName.ifBlank { dealer.name },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Spare Parts Dealer",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    val hoursStatus = com.example.mototap.core.util.getOpenClosedStatus(dealer.workingHours)
                    Text(
                        text = hoursStatus.label,
                        color = if (hoursStatus.isOpen) Color(0xFF0A7A32) else Color(0xFFC00000),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("X", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val placeLabel = formatPlaceAndDistance(dealer.locationName, distanceMeters)
            if (placeLabel.isNotBlank()) {
                Text(
                    text = placeLabel,
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )
            }
            Text(
                text = "Part: $part",
                color = Color.DarkGray,
                fontSize = 13.sp
            )
            if (price != null && price > 0) {
                Text(
                    text = "Price: KSh $price",
                    color = PartsOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PartsOrange)
            ) {
                Text("View Details", fontWeight = FontWeight.Bold)
            }
        }
    }
}
