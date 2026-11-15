package com.example.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import org.junit.Assert.*
import org.junit.Test

class BillingMessagesTest {
    @Test fun networkAndUnavailableHaveDifferentRecoveryInstructions() {
        val network = billingErrorMessage(BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR).build())
        val unavailable = billingErrorMessage(BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE).build())
        assertTrue(network.contains("інтернет"))
        assertTrue(unavailable.contains("пристрої"))
        assertNotEquals(network, unavailable)
    }
}
