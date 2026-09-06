package com.example.mototap

import com.example.mototap.core.util.garageServicesVehicle
import com.example.mototap.core.util.normalizeVehicleTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarageVehicleTypesTest {
    @Test
    fun normalizeDropsUnknownAndDuplicates() {
        assertEquals(
            listOf("suvs", "motorcycles"),
            normalizeVehicleTypes(listOf("suvs", "bogus", "suvs", "motorcycles")),
        )
    }

    @Test
    fun emptyTypesMatchEveryVehicle() {
        assertTrue(garageServicesVehicle(emptyList(), "Toyota", "Hilux"))
    }

    @Test
    fun selectedTypesFilterByCatalog() {
        assertTrue(garageServicesVehicle(listOf("pickup-trucks"), "Toyota", "Hilux"))
        assertFalse(garageServicesVehicle(listOf("motorcycles"), "Toyota", "Hilux"))
    }

    @Test
    fun missingMakeStaysCompatible() {
        assertTrue(garageServicesVehicle(listOf("suvs"), "", "Hilux"))
    }
}
