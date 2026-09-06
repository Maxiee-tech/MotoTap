package com.example.mototap.features.partsdealer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.model.UserProfile
import com.example.mototap.ui.theme.MotoRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsDealerHomeScreen(
    profile: UserProfile?,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MOTO TAP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MotoRed),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Spare Parts Dealer",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Text(
                text = "Your shop profile is under review. You will appear to drivers once approved.",
                color = Color.LightGray,
                fontSize = 14.sp,
            )

            InfoCard(
                title = "SHOP STATUS",
                value = profile?.status?.name ?: "PENDING",
                icon = { Icon(Icons.Default.Store, contentDescription = null, tint = MotoRed) },
            )
            InfoCard(
                title = "SHOP NAME",
                value = profile?.institutionName?.takeIf { it.isNotBlank() } ?: "Not set",
                icon = { Icon(Icons.Default.Store, contentDescription = null, tint = MotoRed) },
            )
            InfoCard(
                title = "LOCATION",
                value = when {
                    profile?.locationName?.isNotBlank() == true -> profile.locationName
                    profile?.latitude != null && profile.longitude != null -> "Location pinned"
                    else -> "Not set"
                },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MotoRed) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNavigateToProfile,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MotoRed),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("VIEW PROFILE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, color = MotoRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(text = value, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
