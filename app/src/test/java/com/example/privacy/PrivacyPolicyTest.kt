package com.example.privacy

import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyTest {
    @Test fun adsRequireConsentAndConfirmedNonPremiumState() {
        for (premium in listOf(true, false, null)) assertFalse(canRequestAdvertisements(false, premium))
        assertFalse(canRequestAdvertisements(true, null))
        assertFalse(canRequestAdvertisements(true, true))
        assertTrue(canRequestAdvertisements(true, false))
    }

    @Test fun externalPagesRejectNonHttpsAndPlaceholderAddresses() {
        listOf("", "http://wrocki.web.app/privacy", "javascript:alert(1)", "intent://page",
            "https://example.com/privacy", "https://localhost", "https://user:password@wrocki.web.app")
            .forEach { assertFalse(it, isSafePublicHttpsUrl(it)) }
        assertTrue(isSafePublicHttpsUrl("https://wrocki.web.app/privacy"))
    }
}
