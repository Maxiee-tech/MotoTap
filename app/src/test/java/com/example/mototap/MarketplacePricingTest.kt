package com.example.mototap

import com.example.mototap.core.util.DEFAULT_PRICE_KEY
import com.example.mototap.core.util.buildServicePricesPayload
import com.example.mototap.core.util.normalizeServicePrices
import com.example.mototap.core.util.resolveVehiclePrice
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketplacePricingTest {
    @Test
    fun unpricedSelectedServiceIsOmittedFromPayload() {
        assertEquals(
            emptyMap<String, Map<String, Long>>(),
            buildServicePricesPayload(listOf("Oil Change"), emptyMap(), emptyMap()),
        )
    }

    @Test
    fun nullPricesResolveAsAbsent() {
        val entry = mapOf(DEFAULT_PRICE_KEY to 1000L)
        assertEquals(null, resolveVehiclePrice(null, "Toyota", "Axio"))
        assertEquals(1000L, resolveVehiclePrice(entry, "Toyota", "Axio"))
        assertEquals(null, resolveVehiclePrice(emptyMap(), "Toyota", "Axio"))
    }

    @Test
    fun towingRequiresVehicleRatesAndHasNoDefault() {
        // Towing is "Towing & Recovery" or similar in catalog
        val service = "Towing" 
        val prices = mapOf(service to 1000L)
        val payload = buildServicePricesPayload(listOf(service), prices, emptyMap())
        
        // Should be empty because towing has no flat default
        assertEquals(emptyMap<String, Map<String, Long>>(), payload)
        
        val vehiclePrices = mapOf(service to mapOf("Toyota" to 150L))
        val payloadWithVehicle = buildServicePricesPayload(listOf(service), prices, vehiclePrices)
        assertEquals(150L, payloadWithVehicle[service]?.get("Toyota"))
        assertEquals(null, payloadWithVehicle[service]?.get(DEFAULT_PRICE_KEY))
    }

    @Test
    fun vehiclePriceFallsBackFromModelToMakeToDefault() {
        val entry = mapOf(
            "Toyota" to 1_500L,
            "Toyota:Axio" to 2_000L,
            DEFAULT_PRICE_KEY to 1_000L,
        )

        assertEquals(2_000L, resolveVehiclePrice(entry, "Toyota", "Axio"))
        assertEquals(1_500L, resolveVehiclePrice(entry, "Toyota", "Corolla"))
        assertEquals(1_000L, resolveVehiclePrice(entry, "Honda", "Fit"))
    }

    @Test
    fun legacyFlatPriceNormalizesToOptionalDefault() {
        val prices = normalizeServicePrices(mapOf("Oil Change" to "1,250"))

        assertEquals(mapOf(DEFAULT_PRICE_KEY to 1_250L), prices["Oil Change"])
        assertEquals(1_250L, resolveVehiclePrice(prices["Oil Change"], "Honda", "Fit"))
    }
}