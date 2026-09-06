package com.example.mototap.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PlacesLocationHelper {
    data class PickedPlace(
        val name: String,
        val address: String = "",
        val lat: Double? = null,
        val lng: Double? = null,
    )

    fun mapsApiKey(context: Context): String {
        return try {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            info.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun ensureInitialized(context: Context) {
        val key = mapsApiKey(context)
        if (key.isBlank() || Places.isInitialized()) return
        runCatching { Places.initialize(context.applicationContext, key) }
    }

    fun autocompleteIntent(context: Context): Intent {
        ensureInitialized(context)
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
        )
        return Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("KE"))
            .build(context)
    }

    fun placeFromResult(data: Intent?): PickedPlace? {
        val place = Autocomplete.getPlaceFromIntent(data ?: return null)
        val name = place.name?.trim().orEmpty()
            .ifBlank { place.address.orEmpty().trim() }
            .take(120)
        if (name.isBlank()) return null
        return PickedPlace(
            name = name,
            address = place.address.orEmpty(),
            lat = place.latLng?.latitude,
            lng = place.latLng?.longitude,
        )
    }

    suspend fun nearbyLandmarks(
        context: Context,
        lat: Double,
        lng: Double,
        limit: Int = 3,
    ): List<String> = withContext(Dispatchers.IO) {
        val key = mapsApiKey(context)
        if (key.isBlank()) return@withContext emptyList()
        val url = URL(
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=$lat,$lng&radius=900&type=point_of_interest&key=$key"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            val names = linkedSetOf<String>()
            for (index in 0 until results.length()) {
                val name = results.optJSONObject(index)?.optString("name").orEmpty().trim()
                if (name.isNotEmpty()) names += name
                if (names.size >= limit) break
            }
            names.toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }
}
