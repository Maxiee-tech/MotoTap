package com.example.mototap.features.driver

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mototap.features.auth.AuthUiState
import com.example.mototap.features.auth.AuthViewModel
import com.example.mototap.features.auth.LocationNamePicker
import com.example.mototap.R
import com.example.mototap.core.model.GarageMemberRole
import com.example.mototap.core.model.UserRole
import com.example.mototap.core.util.computeLoyalty
import com.example.mototap.ui.theme.MotoRed
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    driverViewModel: DriverHomeViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onDeleteSuccess: () -> Unit,
    onNavigateToRequests: () -> Unit = {},
    onBookNow: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val driverUiState by driverViewModel.uiState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deletePasswordVisible by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var hasNavigatedAfterDelete by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Service History", "Reminders", "Loyalty Rewards")
    
    var showVehicleDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<com.example.mototap.core.model.VehicleProfile?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val garagePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.updateGarageProfilePhoto(context, it) }
    }

    val loyaltyPoints = remember(userProfile, driverUiState.jobs) {
        computeLoyalty(userProfile, driverUiState.jobs).available
    }

    val accountOwnerName = remember(userProfile, currentUserId) {
        userProfile?.name
            ?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.displayName
                ?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.email
                ?.substringBefore("@")
                ?.takeIf { it.isNotBlank() }
            ?: "Account Owner"
    }

    LaunchedEffect(Unit) {
        viewModel.fetchUserProfile()
    }

    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener {
            currentUserId = it.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            viewModel.fetchUserProfile()
        }
    }

    LaunchedEffect(uiState, isDeleting) {
        if (isDeleting && uiState is AuthUiState.Error) {
            isDeleting = false
        }
    }

    LaunchedEffect(isDeleting, currentUserId, hasNavigatedAfterDelete) {
        if (isDeleting && currentUserId == null && !hasNavigatedAfterDelete) {
            hasNavigatedAfterDelete = true
            isDeleting = false
            viewModel.resetState()
            onDeleteSuccess()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MY PROFILE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
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
        val isDriver = userProfile?.role?.name?.lowercase() == "driver"

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Header Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = MotoRed.copy(alpha = 0.1f)
                        ) {
                            val roleName = userProfile?.role?.name
                            val displayPhotoUrl = userProfile?.profilePhotoUrl?.takeIf { it.isNotBlank() }
                                ?: when (roleName) {
                                    "DRIVER" -> userProfile?.vehiclePhotoUrl?.takeIf { it.isNotBlank() }
                                    "MECHANIC" -> userProfile?.certificatePhotoUrl?.takeIf { it.isNotBlank() } ?: userProfile?.garagePhotos?.firstOrNull()?.takeIf { it.isNotBlank() }
                                    else -> null
                                }
                                ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()

                            if (!displayPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = displayPhotoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    onError = { error ->
                                        android.util.Log.e("ProfileScreen", "Coil load failed for: $displayPhotoUrl", error.result.throwable)
                                    }
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MotoRed,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = accountOwnerName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = userProfile?.role?.name ?: "",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        
                        if (isDriver) {
                            Text(
                                text = "Total Points: $loyaltyPoints",
                                color = MotoRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Info Cards (Email & Phone)
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        ProfileInfoCard(
                            icon = Icons.Default.Email,
                            label = "Email Address",
                            value = FirebaseAuth.getInstance().currentUser?.email ?: "Not available"
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        ProfileInfoCard(
                            icon = Icons.Default.Phone,
                            label = "Phone Number",
                            value = userProfile?.phone?.takeIf { it.isNotBlank() } ?: "Not provided"
                        )
                    }
                }

                if (userProfile?.role == UserRole.MECHANIC || userProfile?.role == UserRole.PARTS_DEALER) {
                    item {
                        val profile = userProfile ?: return@item
                        val canEditGaragePhoto = !profile.garageRole.equals(
                            GarageMemberRole.MECHANIC,
                            ignoreCase = true,
                        )
                        var draftLocationName by remember(profile.locationName) {
                            mutableStateOf(profile.locationName)
                        }
                        val photoUrl = profile.garagePhotos.firstOrNull().orEmpty()
                        val shopLabel = if (profile.role == UserRole.PARTS_DEALER) {
                            "SHOP PROFILE PHOTO"
                        } else {
                            "GARAGE PROFILE PHOTO"
                        }
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ProfileInfoCard(
                                icon = Icons.Default.Home,
                                label = shopLabel.lowercase().replaceFirstChar { it.uppercase() },
                                value = profile.institutionName.takeIf { it.isNotBlank() }
                                    ?: if (profile.role == UserRole.PARTS_DEALER) "Shop" else "Garage",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (canEditGaragePhoto) Modifier.clickable {
                                            garagePhotoLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        } else Modifier
                                    ),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.DarkGray.copy(alpha = 0.5f),
                                    ) {
                                        if (photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = shopLabel,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = null,
                                                tint = Color.LightGray,
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shopLabel,
                                            color = MotoRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            text = when {
                                                uiState is AuthUiState.Loading -> "Uploading…"
                                                photoUrl.isNotBlank() -> "Clients see this on your listing"
                                                else -> "Add a clear shop photo"
                                            },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                        )
                                        if (canEditGaragePhoto) {
                                            Text(
                                                text = if (photoUrl.isBlank()) "ADD PHOTO" else "CHANGE PHOTO",
                                                color = MotoRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (uiState is AuthUiState.Error) {
                                Text(
                                    text = (uiState as AuthUiState.Error).message,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (canEditGaragePhoto) {
                                LocationNamePicker(
                                    locationName = draftLocationName,
                                    onLocationNameChange = { draftLocationName = it },
                                    latitude = profile.latitude,
                                    longitude = profile.longitude,
                                    onPlacePicked = { place ->
                                        draftLocationName = place.name
                                        viewModel.updateGarageLocationName(place.name)
                                    },
                                    showSave = true,
                                    onSave = { viewModel.updateGarageLocationName(draftLocationName) },
                                    isSaving = uiState is AuthUiState.Loading,
                                )
                            } else {
                                ProfileInfoCard(
                                    icon = Icons.Default.LocationOn,
                                    label = "Location name",
                                    value = profile.locationName.ifBlank { "Not provided" },
                                )
                            }
                        }
                    }
                }

                // Driver-Specific Vehicle Hub Integration
                if (isDriver) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        MyVehiclesSection(
                            vehicles = userProfile?.vehicles ?: emptyList(),
                            onVehicleClick = { vehicle ->
                                editingVehicle = vehicle
                                showVehicleDialog = true
                            },
                            onAddVehicle = {
                                editingVehicle = null
                                showVehicleDialog = true
                            }
                        )
                    }

                    item {
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Black,
                            contentColor = MotoRed,
                            edgePadding = 16.dp,
                            divider = {}
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            when (selectedTab) {
                                0 -> OverviewTabContent(
                                    userProfile = userProfile,
                                    jobs = driverUiState.jobs,
                                    loyaltyPoints = loyaltyPoints,
                                    onSeeMore = { selectedTab = 1 },
                                    onBookNow = onBookNow
                                )
                                1 -> ServiceHistoryTabContent(
                                    jobs = driverUiState.jobs,
                                    onViewAllRequests = onNavigateToRequests
                                )
                                2 -> RemindersTabContent(
                                    userProfile = userProfile,
                                    jobs = driverUiState.jobs,
                                    onBookNow = onBookNow
                                )
                                3 -> LoyaltyTabContent(loyaltyPoints = loyaltyPoints)
                            }
                        }
                    }
                }

                // Actions Section
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Button(
                            onClick = { viewModel.logout { onLogout() } },
                            enabled = !isDeleting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("LOG OUT", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showDeleteDialog = true },
                            enabled = !isDeleting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Delete Account", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deletePassword = ""
                },
                title = { Text("Delete Account?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("This action is permanent and cannot be undone.")
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("Current Password") },
                            singleLine = true,
                            visualTransformation = if (deletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { deletePasswordVisible = !deletePasswordVisible }) {
                                    Icon(if (deletePasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                                }
                            },
                            enabled = !isDeleting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            isDeleting = true
                            viewModel.deleteAccount(deletePassword) {
                                isDeleting = false
                                onDeleteSuccess()
                            }
                        },
                        enabled = deletePassword.isNotBlank() && !isDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                    ) {
                        Text("DELETE", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; deletePassword = "" }, enabled = !isDeleting) {
                        Text("CANCEL")
                    }
                }
            )
        }

        if (isDeleting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MotoRed)
            }
        }

        if (showVehicleDialog) {
            VehicleEditDialog(
                vehicle = editingVehicle,
                onDismiss = { showVehicleDialog = false },
                onSave = { updatedVehicle ->
                    viewModel.updateVehicle(updatedVehicle)
                    showVehicleDialog = false
                },
                onDelete = { vehicleId ->
                    viewModel.deleteVehicle(vehicleId)
                    showVehicleDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditDialog(
    vehicle: com.example.mototap.core.model.VehicleProfile?,
    onDismiss: () -> Unit,
    onSave: (com.example.mototap.core.model.VehicleProfile) -> Unit,
    onDelete: (String) -> Unit
) {
    var make by remember { mutableStateOf(vehicle?.make ?: "") }
    var model by remember { mutableStateOf(vehicle?.model ?: "") }
    var plate by remember { mutableStateOf(vehicle?.licensePlate ?: "") }
    var mileage by remember { mutableStateOf(vehicle?.mileage ?: "") }
    var year by remember { mutableStateOf(vehicle?.year ?: "") }

    val makeOptions = remember { com.example.mototap.core.data.VehicleCatalogData.makeNames() }
    val modelOptions = remember(make) { com.example.mototap.core.data.VehicleCatalogData.modelsForMake(make) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vehicle == null) "Add Vehicle" else "Edit Vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VehicleCatalogDropdown(
                    label = "Make",
                    value = make,
                    options = makeOptions,
                    onSelected = { make = it; model = "" },
                )
                VehicleCatalogDropdown(
                    label = "Model",
                    value = model,
                    options = modelOptions,
                    enabled = make.isNotBlank(),
                    onSelected = { model = it },
                )
                OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("License Plate") })
                OutlinedTextField(value = mileage, onValueChange = { mileage = it }, label = { Text("Current Mileage") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        com.example.mototap.core.model.VehicleProfile(
                            id = vehicle?.id ?: "",
                            make = make,
                            model = model,
                            year = year,
                            licensePlate = plate,
                            mileage = mileage
                        )
                    )
                },
                enabled = make.isNotBlank() && model.isNotBlank() && plate.isNotBlank()
            ) {
                Text("SAVE")
            }
        },
        dismissButton = {
            Row {
                if (vehicle != null) {
                    TextButton(onClick = { onDelete(vehicle.id) }) {
                        Text("DELETE", color = Color.Red)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CANCEL")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCatalogDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun ProfileInfoCard(icon: ImageVector, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = label, color = Color.Gray, fontSize = 12.sp)
                Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MyVehiclesSection(
    vehicles: List<com.example.mototap.core.model.VehicleProfile>,
    onVehicleClick: (com.example.mototap.core.model.VehicleProfile) -> Unit,
    onAddVehicle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Vehicles",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            TextButton(onClick = onAddVehicle) {
                Text("ADD", color = MotoRed, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (vehicles.isEmpty()) {
            Text("No vehicles registered", color = Color.Gray, fontSize = 14.sp)
        } else {
            vehicles.forEach { vehicle ->
                VehicleItemRow(
                    name = "${vehicle.make} ${vehicle.model}",
                    plate = vehicle.licensePlate,
                    onClick = { onVehicleClick(vehicle) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun VehicleItemRow(name: String, plate: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(4.dp),
                color = MotoRed.copy(alpha = 0.1f)
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = MotoRed, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = name, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text(text = plate, fontSize = 14.sp, color = Color.Gray)
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
        }
    }
}

@Composable
fun OverviewTabContent(
    userProfile: com.example.mototap.core.model.UserProfile?,
    jobs: List<com.example.mototap.core.model.JobRequest>,
    loyaltyPoints: Int,
    onSeeMore: () -> Unit,
    onBookNow: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeVehicle = userProfile?.vehicles?.firstOrNull()
    val carName = activeVehicle?.let { "${it.make} ${it.model}" } ?: "Your vehicle"
    
    val completedJobs = jobs.filter { it.status == com.example.mototap.core.model.JobStatus.COMPLETED }
        .sortedByDescending { it.createdAtMillis }
    val latestJob = completedJobs.firstOrNull()

    val nextServiceDateStr = remember(latestJob) {
        val cal = Calendar.getInstance()
        if (latestJob != null) {
            cal.timeInMillis = latestJob.createdAtMillis
            cal.add(Calendar.MONTH, 4)
        } else {
            cal.add(Calendar.MONTH, 1) // Default to 1 month from now if no previous service
        }
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(cal.time)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NotificationBubble(
            title = "Automated Service Reminders",
            message = "Reminder: $carName is due for service on $nextServiceDateStr.",
            actionText = "Book Now!",
            onActionClick = { onBookNow(context.getString(R.string.preventive_maintenance)) },
            icon = Icons.Default.DateRange
        )
        
        HubSectionHeader("Latest Service")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (latestJob != null) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateString = sdf.format(Date(latestJob.createdAtMillis))
                    Text(latestJob.issueType, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text("$dateString at professional service", color = Color.Gray, fontSize = 13.sp)
                } else {
                    Text("No services yet", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        HubSectionHeader("Service History")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (completedJobs.isEmpty()) {
                    Text("No history found", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                } else {
                    completedJobs.take(3).forEachIndexed { index, job ->
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        HubServiceItem(job.issueType, sdf.format(Date(job.createdAtMillis)), "MotoTap Service")
                        if (index < completedJobs.take(3).size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = Color.DarkGray)
                        }
                    }
                    
                    if (completedJobs.size > 3) {
                        TextButton(
                            onClick = onSeeMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("MORE...", color = MotoRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MotoRed),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Loyalty & Rewards", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Points Balance: $loyaltyPoints", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Earn 10 points for every completed service!", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                if (loyaltyPoints >= 50) {
                    Text("\u2713 Reward Available: Free Standard Car Wash", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ReminderItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MotoRed, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
fun HubSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Color.White,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

@Composable
fun HubServiceItem(service: String, date: String, garage: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = service, fontSize = 14.sp, color = Color.White)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = date, fontSize = 12.sp, color = Color.Gray)
            Text(text = garage, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun NotificationBubble(
    title: String, 
    message: String, 
    icon: ImageVector,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MotoRed.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MotoRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MotoRed)
                Text(text = message, fontSize = 12.sp, color = Color.LightGray)
                if (actionText != null) {
                    Text(
                        text = actionText,
                        color = MotoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onActionClick() }
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceHistoryTabContent(
    jobs: List<com.example.mototap.core.model.JobRequest>,
    onViewAllRequests: () -> Unit
) {
    val completedJobs = jobs.filter { it.status == com.example.mototap.core.model.JobStatus.COMPLETED }
        .sortedByDescending { it.createdAtMillis }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (completedJobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No service history found", color = Color.Gray)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    completedJobs.take(3).forEachIndexed { index, job ->
                        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        HubServiceItem(job.issueType, sdf.format(Date(job.createdAtMillis)), "Professional Service")
                        if (index < completedJobs.take(3).size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = Color.DarkGray)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onViewAllRequests,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MotoRed)
            ) {
                Text("VIEW ALL REQUESTS (MORE...)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RemindersTabContent(
    userProfile: com.example.mototap.core.model.UserProfile?,
    jobs: List<com.example.mototap.core.model.JobRequest>,
    onBookNow: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeVehicle = userProfile?.vehicles?.firstOrNull()
    val carName = activeVehicle?.let { "${it.make} ${it.model}" } ?: "Your vehicle"
    
    val completedJobs = jobs.filter { it.status == com.example.mototap.core.model.JobStatus.COMPLETED }
        .sortedByDescending { it.createdAtMillis }
    val latestJob = completedJobs.firstOrNull()

    val nextServiceDateStr = remember(latestJob) {
        val cal = Calendar.getInstance()
        if (latestJob != null) {
            cal.timeInMillis = latestJob.createdAtMillis
            cal.add(Calendar.MONTH, 4)
        } else {
            cal.add(Calendar.MONTH, 1)
        }
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(cal.time)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HubSectionHeader("Active Reminders")
        
        NotificationBubble(
            title = "Maintenance Due",
            message = "Reminder: $carName is due for service on $nextServiceDateStr.",
            actionText = "Book Now!",
            onActionClick = { onBookNow(context.getString(R.string.preventive_maintenance)) },
            icon = Icons.Default.DateRange
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ReminderItem("Insurance Renewal: Pending")
                Spacer(modifier = Modifier.height(8.dp))
                ReminderItem("Tire Rotation: Recommended")
            }
        }
    }
}

@Composable
fun LoyaltyTabContent(loyaltyPoints: Int) {
    val points = loyaltyPoints

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MotoRed)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURRENT BALANCE", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text("$points", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Text("MOTOTAP POINTS", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        
        HubSectionHeader("Available Rewards")
        
        RewardRedeemItem(
            title = "Free Standard Car Wash",
            pointsRequired = 50,
            isAvailable = points >= 50
        )
        
        RewardRedeemItem(
            title = "10% Discount on Major Service",
            pointsRequired = 150,
            isAvailable = points >= 150
        )
        
        RewardRedeemItem(
            title = "Free Vehicle Diagnostic Scan",
            pointsRequired = 300,
            isAvailable = points >= 300
        )
    }
}

@Composable
fun RewardRedeemItem(title: String, pointsRequired: Int, isAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isAvailable) Color(0xFF1F2C34) else Color(0xFF1A1A1A)),
        border = if (isAvailable) androidx.compose.foundation.BorderStroke(1.dp, MotoRed) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = if (isAvailable) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                Text(text = "$pointsRequired pts", color = MotoRed, fontSize = 12.sp)
            }
            Button(
                onClick = { /* Handle Redeem */ },
                enabled = isAvailable,
                colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("REDEEM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
