package com.example.mototap.core.util

import com.example.mototap.core.model.UserProfile
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Default price key inside a per-service vehicle price map. */
const val DEFAULT_PRICE_KEY = "_default"

/** Build a vehicle price map key from make and optional model. */
fun buildVehiclePriceKey(make: String?, model: String?): String {
    val makeStr = make?.trim().orEmpty()
    val modelStr = model?.trim().orEmpty()
    return if (makeStr.isNotEmpty() && modelStr.isNotEmpty()) "$makeStr:$modelStr" else makeStr
}

/** Parse a price field value; empty input is null (not zero). Strips commas (e.g. 5,000). */
fun parsePriceInput(raw: String?): Long? {
    val digits = raw.orEmpty().filter { it.isDigit() }
    if (digits.isEmpty()) return null
    return digits.toLongOrNull()?.takeIf { it > 0 }
}

/** Coerce an arbitrary Firestore value into a non-negative KSh amount. */
private fun coercePriceValue(value: Any?): Long? {
    return when (value) {
        is Number -> value.toDouble().let { if (it.isFinite() && it >= 0) it.roundToLong() else null }
        is String -> {
            val cleaned = value.trim().replace(",", "")
            if (cleaned.isEmpty()) return null
            val parsed = cleaned.toDoubleOrNull() ?: return null
            if (parsed.isFinite() && parsed >= 0) parsed.roundToLong() else null
        }
        else -> null
    }
}

private fun lookupCaseInsensitive(map: Map<String, Long>, target: String?): Long? {
    val key = target?.trim().orEmpty()
    if (key.isEmpty()) return null
    map[key]?.let { return it }
    val keyLower = key.lowercase()
    return map.entries.firstOrNull { it.key.trim().lowercase() == keyLower }?.value
}

private fun normalizeVehiclePriceMap(raw: Map<*, *>): Map<String, Long> {
    val out = linkedMapOf<String, Long>()
    for ((rawKey, rawValue) in raw) {
        val name = rawKey?.toString()?.trim().orEmpty()
        val price = coercePriceValue(rawValue)
        if (name.isNotEmpty() && price != null) out[name] = price
    }
    return out
}

/**
 * Normalize Firestore `servicePrices`.
 * Values may be a flat KSh amount or a vehicle-keyed map with `_default`.
 * Flat values are normalized to `mapOf("_default" to amount)`.
 */
fun normalizeServicePrices(raw: Any?): Map<String, Map<String, Long>> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    val out = linkedMapOf<String, Map<String, Long>>()
    for ((rawKey, rawValue) in map) {
        val name = rawKey?.toString()?.trim().orEmpty()
        if (name.isEmpty()) continue
        when (rawValue) {
            is Number, is String -> {
                val price = coercePriceValue(rawValue)
                if (price != null && price > 0) out[name] = mapOf(DEFAULT_PRICE_KEY to price)
            }
            is Map<*, *> -> {
                val normalized = normalizeVehiclePriceMap(rawValue)
                if (normalized.isNotEmpty()) out[name] = normalized
            }
        }
    }
    return out
}

/** Default (flat) price for a stored service price entry. */
fun getDefaultServicePrice(entry: Map<String, Long>?): Long? {
    if (entry == null) return null
    entry[DEFAULT_PRICE_KEY]?.let { return it }
    entry["default"]?.let { return it }
    return null
}

/**
 * Resolve the price for a service entry given an optional vehicle.
 * Prefers make+model, then make, then `_default` (legacy flat).
 */
fun resolveVehiclePrice(entry: Map<String, Long>?, make: String? = null, model: String? = null): Long? {
    if (entry == null) return null
    val makeStr = make?.trim().orEmpty()
    val modelStr = model?.trim().orEmpty()

    if (makeStr.isNotEmpty() && modelStr.isNotEmpty()) {
        lookupCaseInsensitive(entry, buildVehiclePriceKey(makeStr, modelStr))?.let { return it }
    }
    if (makeStr.isNotEmpty()) {
        lookupCaseInsensitive(entry, makeStr)?.let { return it }
    }
    return getDefaultServicePrice(entry)
}

private fun lookupServiceEntry(
    prices: Map<String, Map<String, Long>>,
    serviceName: String,
): Map<String, Long>? {
    val key = serviceName.trim()
    if (key.isEmpty()) return null
    prices[key]?.let { return it }
    val keyLower = key.lowercase()
    return prices.entries.firstOrNull { it.key.trim().lowercase() == keyLower }?.value
}

/**
 * Resolve a mechanic's listed price for a service.
 * Prefers the mechanic's own price; falls back to garage prices on `garageServicePrices`.
 * When make/model are provided, prefers make+model, then make, then `_default`.
 */
fun getMechanicServicePrice(
    mechanic: UserProfile,
    serviceName: String,
    make: String? = null,
    model: String? = null,
): Long? {
    lookupServiceEntry(mechanic.servicePrices, serviceName)?.let {
        return resolveVehiclePrice(it, make, model)
    }
    lookupServiceEntry(mechanic.garageServicePrices, serviceName)?.let {
        return resolveVehiclePrice(it, make, model)
    }
    return null
}

/** Flat-price lookup for mechanic dashboard UI editing state (case-insensitive). */
fun getMechanicServicePrice(servicePrices: Map<String, Long>, serviceName: String): Long? =
    lookupCaseInsensitive(servicePrices, serviceName)

/** Convert a flat name->amount map to normalized name->{_default:amount}. */
fun flatPriceMap(prices: Map<String, Long>): Map<String, Map<String, Long>> {
    val out = linkedMapOf<String, Map<String, Long>>()
    prices.forEach { (name, amount) ->
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && amount > 0) out[trimmed] = mapOf(DEFAULT_PRICE_KEY to amount)
    }
    return out
}

/** Flatten normalized service prices to flat name->default amount for UI editing. */
fun toFlatServicePrices(prices: Map<String, Map<String, Long>>): Map<String, Long> {
    val out = linkedMapOf<String, Long>()
    prices.forEach { (name, entry) ->
        getDefaultServicePrice(entry)?.let { out[name] = it }
    }
    return out
}

/**
 * Keep only prices for selected skills; omit unset or invalid values.
 * Accepts flat UI prices and vehicle make:model rates.
 * Returns normalized Firestore payload.
 */
fun buildServicePricesPayload(
    selectedSkills: List<String>,
    flatPrices: Map<String, Long>,
    vehiclePrices: Map<String, Map<String, Long>>,
): Map<String, Map<String, Long>> {
    val out = linkedMapOf<String, Map<String, Long>>()
    selectedSkills.forEach { skill ->
        val name = skill.trim()
        if (name.isEmpty()) return@forEach
        val merged = linkedMapOf<String, Long>()

        // Vehicle make:model rates
        vehicleOnlyPriceMap(vehiclePrices[name]).forEach { (key, amount) ->
            if (amount > 0) merged[key] = amount
        }

        // Flat default price (except for towing which is vehicle-only)
        if (!isTowingService(name)) {
            flatPrices[name]?.takeIf { it > 0 }?.let { merged[DEFAULT_PRICE_KEY] = it }
        }

        if (merged.isNotEmpty()) out[name] = merged
    }
    return out
}

fun allSelectedServicesPriced(
    selectedSkills: List<String>,
    pricesByName: Map<String, Long>,
): Boolean {
    if (selectedSkills.isEmpty()) return false
    return selectedSkills.all { skill ->
        getMechanicServicePrice(pricesByName, skill)?.let { it > 0 } == true
    }
}

/**
 * Display helper: whether every selected skill currently has a listed amount.
 * Pricing is optional — save and booking do not require this to be true.
 */
fun allSelectedServicesPriced(
    selectedSkills: List<String>,
    flatPrices: Map<String, Long>,
    vehiclePrices: Map<String, Map<String, Long>>,
): Boolean {
    if (selectedSkills.isEmpty()) return false
    return selectedSkills.all { skill ->
        val hasFlat = lookupFlatPrice(flatPrices, skill)?.let { it > 0 } == true
        val hasVehicle = lookupVehiclePrices(vehiclePrices, skill).any { (key, amount) ->
            key != DEFAULT_PRICE_KEY &&
                !key.equals("default", ignoreCase = true) &&
                amount > 0
        }
        hasFlat || hasVehicle
    }
}

/** Vehicle make:model rates only (excludes `_default`). */
fun vehicleOnlyPriceMap(entry: Map<String, Long>?): Map<String, Long> {
    if (entry.isNullOrEmpty()) return emptyMap()
    return entry.filterKeys { key ->
        key != DEFAULT_PRICE_KEY && !key.equals("default", ignoreCase = true)
    }
}

fun lookupFlatPrice(prices: Map<String, Long>, serviceName: String): Long? =
    getMechanicServicePrice(prices, serviceName)

fun lookupVehiclePrices(
    prices: Map<String, Map<String, Long>>,
    serviceName: String,
): Map<String, Long> {
    val key = serviceName.trim()
    if (key.isEmpty()) return emptyMap()
    prices[key]?.let { return it }
    val keyLower = key.lowercase()
    return prices.entries.firstOrNull { it.key.trim().lowercase() == keyLower }?.value
        ?: emptyMap()
}

fun formatKsh(amount: Long): String =
    String.format(Locale("en", "KE"), "%,d", amount)

/** Towing catalog services — billed per kilometre (must match web SERVICE_CATEGORIES). */
fun isTowingService(serviceName: String?): Boolean =
    com.example.mototap.core.data.isCatalogTowingService(serviceName)

/** Short price amount label, e.g. "KSh 1,500" or "KSh 150/km". */
fun formatServicePriceAmount(amount: Long, serviceName: String? = null): String {
    val perKm = isTowingService(serviceName)
    return "KSh ${formatKsh(amount)}${if (perKm) "/km" else ""}"
}

/** Format a listed service price; towing shows KSh/km. */
fun formatServicePriceLabel(
    amount: Long,
    serviceName: String? = null,
    vehicleLabel: String = "",
): String {
    val perKm = isTowingService(serviceName)
    val base = formatServicePriceAmount(amount, serviceName)
    if (vehicleLabel.isBlank()) {
        return if (perKm) "Rate: $base" else "Price: $base"
    }
    return if (perKm) "Rate for $vehicleLabel: $base" else "Price for $vehicleLabel: $base"
}

/** Estimate a towing total from rate (KSh/km) and distance. */
fun estimateTowingTotal(ratePerKm: Long, kilometres: Double): Long? {
    if (ratePerKm <= 0) return null
    if (!kilometres.isFinite() || kilometres <= 0.0) return null
    return (ratePerKm * kilometres).roundToLong()
}

fun formatDistanceMeters(meters: Float): String {
    if (!meters.isFinite()) return ""
    return if (meters < 1000f) "${meters.roundToInt()} m away"
    else String.format(Locale.US, "%.1f km away", meters / 1000f)
}

fun mechanicServiceInventory(mechanic: com.example.mototap.core.model.UserProfile): List<String> =
    (mechanic.availableServices + mechanic.skills)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
