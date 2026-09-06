package com.example.mototap.core.util

import com.example.mototap.core.data.VehicleCatalogData
import com.example.mototap.core.model.VehicleProfile

/** Keep only known catalog type IDs, in catalog order, without duplicates. */
fun normalizeVehicleTypes(raw: List<String>?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val known = VehicleCatalogData.knownVehicleTypeIds()
    val seen = linkedSetOf<String>()
    raw.forEach { value ->
        val id = value.trim()
        if (id.isNotEmpty() && id in known) seen.add(id)
    }
    return VehicleCatalogData.vehicleTypeOptions().map { it.id }.filter { it in seen }
}

/**
 * Whether a garage’s selected types cover this vehicle.
 * Empty `vehicleTypes` (unset) or missing vehicle details stay compatible with all garages.
 */
fun garageServicesVehicle(
    vehicleTypes: List<String>?,
    make: String?,
    model: String? = null,
): Boolean {
    val selected = normalizeVehicleTypes(vehicleTypes)
    if (selected.isEmpty()) return true
    if (make.isNullOrBlank()) return true

    val categoryIds = VehicleCatalogData.categoryIdsForVehicle(make, model)
    if (categoryIds.isEmpty()) return true

    val selectedSet = selected.toSet()
    return categoryIds.any { it in selectedSet }
}

fun garageServicesVehicle(vehicleTypes: List<String>?, vehicle: VehicleProfile?): Boolean =
    garageServicesVehicle(vehicleTypes, vehicle?.make, vehicle?.model)
