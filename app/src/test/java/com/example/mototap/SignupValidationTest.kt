package com.example.mototap

import com.example.mototap.core.util.SignupValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupValidationTest {
    @Test
    fun nameRequiresAtLeastTwoParts() {
        assertTrue(SignupValidation.isNameValid("John Doe"))
        assertTrue(!SignupValidation.isNameValid("John"))
        assertTrue(!SignupValidation.isNameValid(" "))
    }

    @Test
    fun driverStep3RequiresAllFields() {
        assertNull(
            SignupValidation.validateDriverStep3(
                vehicleMake = "Toyota",
                vehicleModel = "Axio",
                numberPlate = "KDA 123A",
                vehiclePhotoUrl = "https://example.com/car.jpg",
            )
        )
        assertEquals(
            "Please upload a vehicle photo.",
            SignupValidation.validateDriverStep3(
                vehicleMake = "Toyota",
                vehicleModel = "Axio",
                numberPlate = "KDA 123A",
                vehiclePhotoUrl = "",
            )
        )
    }

    @Test
    fun providerStep3RequiresLocationAndPhotos() {
        assertNull(
            SignupValidation.validateProviderStep3(
                institutionName = "Auto Parts Hub",
                experienceYears = "3-5",
                certificatePhotoUrl = "https://example.com/license.jpg",
                garagePhotos = listOf("https://example.com/shop.jpg"),
                latitude = -1.28,
                longitude = 36.82,
                address = "Nairobi",
                locationName = "Westlands",
                locationLabel = "shop",
            )
        )
        assertEquals(
            "Please pin your shop location on the map.",
            SignupValidation.validateProviderStep3(
                institutionName = "Auto Parts Hub",
                experienceYears = "3-5",
                certificatePhotoUrl = "https://example.com/license.jpg",
                garagePhotos = listOf("https://example.com/shop.jpg"),
                latitude = null,
                longitude = null,
                address = "Nairobi",
                locationName = "Westlands",
                locationLabel = "shop",
            )
        )
    }

    @Test
    fun roleHelpersRecognizeProviders() {
        assertTrue(SignupValidation.isProviderRole("mechanic"))
        assertTrue(SignupValidation.isProviderRole("parts_dealer"))
        assertTrue(SignupValidation.isPartsDealerRole("parts_dealer"))
        assertTrue(!SignupValidation.isPartsDealerRole("driver"))
    }
}
