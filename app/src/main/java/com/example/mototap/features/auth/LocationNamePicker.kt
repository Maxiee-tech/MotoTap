package com.example.mototap.features.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mototap.core.util.PlacesLocationHelper

@Composable
fun LocationNamePicker(
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    latitude: Double?,
    longitude: Double?,
    onPlacePicked: (PlacesLocationHelper.PickedPlace) -> Unit,
    modifier: Modifier = Modifier,
    showSave: Boolean = false,
    onSave: (() -> Unit)? = null,
    isSaving: Boolean = false,
    textColor: Color = Color.White,
    labelColor: Color = Color.Gray,
) {
    val context = LocalContext.current
    var landmarks by remember { mutableStateOf<List<String>>(emptyList()) }
    val autocompleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val place = runCatching { PlacesLocationHelper.placeFromResult(result.data) }.getOrNull()
            ?: return@rememberLauncherForActivityResult
        onPlacePicked(place)
    }

    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            landmarks = PlacesLocationHelper.nearbyLandmarks(context, latitude, longitude)
        } else {
            landmarks = emptyList()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = locationName,
            onValueChange = { onLocationNameChange(it.take(120)) },
            label = { Text("Location name (drivers see this)", color = labelColor) },
            placeholder = { Text("Search or pick a popular place near you") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = {
                        autocompleteLauncher.launch(PlacesLocationHelper.autocompleteIntent(context))
                    }
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search places", tint = labelColor)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedPlaceholderColor = labelColor,
                unfocusedPlaceholderColor = labelColor,
            ),
        )
        if (landmarks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nearby landmarks", color = labelColor, fontSize = 12.sp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                landmarks.forEach { name ->
                    FilterChip(
                        selected = locationName == name,
                        onClick = {
                            onPlacePicked(PlacesLocationHelper.PickedPlace(name = name))
                        },
                        label = { Text(name) },
                    )
                }
            }
        }
        if (showSave && onSave != null) {
            TextButton(onClick = onSave, enabled = !isSaving && locationName.isNotBlank()) {
                Text(if (isSaving) "Saving…" else "Save location name")
            }
        }
    }
}
