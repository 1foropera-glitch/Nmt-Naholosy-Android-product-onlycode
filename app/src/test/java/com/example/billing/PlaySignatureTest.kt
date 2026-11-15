package com.example.billing

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaySignatureTest {
    @Test fun pendingWrongPackageAndWrongProductNeverGrantPremiumEvenWithValidSignature() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        fun purchase(state: Int = 0, pkg: String = "app.test", product: String = "premium_remove_ads"): com.android.billingclient.api.Purchase {
            val payload = """{"packageName":"$pkg","productId":"$product","purchaseToken":"fixture-token","purchaseState":$state}"""
            val signature = Signature.getInstance("SHA1withRSA").run {
                initSign(pair.private); update(payload.toByteArray(Charsets.UTF_8)); sign()
            }
            return com.android.billingclient.api.Purchase(payload, Base64.encodeToString(signature, Base64.NO_WRAP))
        }
        assertTrue(isAuthenticPremiumPurchase(purchase(), key, "app.test"))
        assertFalse(isAuthenticPremiumPurchase(purchase(state = 4), key, "app.test"))
        assertFalse(isAuthenticPremiumPurchase(purchase(pkg = "other.app"), key, "app.test"))
        assertFalse(isAuthenticPremiumPurchase(purchase(product = "other_product"), key, "app.test"))
    }
    @Test fun validSignaturePassesAndTamperingOrAnotherKeyFails() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val payload = "{\"productId\":\"premium_remove_ads\",\"purchaseState\":0}"
        val signed = Signature.getInstance("SHA1withRSA").run {
            initSign(pair.private); update(payload.toByteArray(Charsets.UTF_8)); sign()
        }
        val signature = Base64.encodeToString(signed, Base64.NO_WRAP)
        assertTrue(verifyPlaySignature(key, payload, signature))
        assertFalse(verifyPlaySignature(key, payload.replace(":0", ":4"), signature))
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(verifyPlaySignature(Base64.encodeToString(other.public.encoded, Base64.NO_WRAP), payload, signature))
        assertFalse(verifyPlaySignature("", payload, signature))
        assertFalse(verifyPlaySignature(key, payload, "invalid"))
    }
}
