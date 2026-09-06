package com.example.mototap.core.util

private val COORDINATE_PAIR = Regex("""^-?\d{1,3}(?:\.\d+)?\s*,\s*-?\d{1,3}(?:\.\d+)?$""")

fun looksLikeCoordinates(value: String?): Boolean =
    COORDINATE_PAIR.matches(value?.trim().orEmpty())

/** Popular place drivers should see — never street address or lat/lng. */
fun formatShopAreaLabel(
    locationName: String? = "",
    @Suppress("UNUSED_PARAMETER") address: String? = "",
): String = locationName?.trim().orEmpty()

fun formatPlaceAndDistance(
    locationName: String? = "",
    meters: Float?,
): String {
    val place = formatShopAreaLabel(locationName)
    val distance = meters?.let { formatDistanceMeters(it) }.orEmpty()
    return when {
        place.isNotEmpty() && distance.isNotEmpty() -> "$place · $distance"
        place.isNotEmpty() -> place
        else -> distance
    }
}
