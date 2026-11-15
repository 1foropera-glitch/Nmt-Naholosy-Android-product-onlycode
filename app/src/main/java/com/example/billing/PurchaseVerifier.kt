package com.example.billing

import android.util.Base64
import com.android.billingclient.api.Purchase
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class VerifiedPurchase(val active: Boolean, val uid: String)

/** Client-only verification. No private keys or trusted cloud entitlement are stored in the APK. */
class PurchaseVerifier(private val app: FirebaseApp?, private val allowed: (String) -> Boolean = { true }) {
    fun currentUid(): String? = app?.let { FirebaseAuth.getInstance(it).currentUser?.uid }?.takeIf(allowed)
    fun canPurchase(): Boolean = currentUid() != null
    fun configured(): Boolean = BuildConfig.PLAY_LICENSING_PUBLIC_KEY.isNotBlank()
    suspend fun verify(purchase: Purchase, expectedUid: String): VerifiedPurchase {
        check(currentUid() == expectedUid) { "Акаунт змінився під час перевірки покупки." }
        val valid = withContext(Dispatchers.Default) {
            isAuthenticPremiumPurchase(purchase, BuildConfig.PLAY_LICENSING_PUBLIC_KEY, BuildConfig.APPLICATION_ID)
        }
        check(valid) { "Не вдалося перевірити підпис покупки Google Play." }
        check(currentUid() == expectedUid) { "Акаунт змінився під час перевірки покупки." }
        return VerifiedPurchase(true, expectedUid)
    }
    fun accountFingerprint(): String? = currentUid()?.let { uid ->
        java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

fun isAuthenticPremiumPurchase(purchase: Purchase, publicKey: String, packageName: String): Boolean =
    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
        BillingManager.PREMIUM_PRODUCT_ID in purchase.products && purchase.purchaseToken.isNotBlank() &&
        purchase.packageName == packageName && verifyPlaySignature(publicKey, purchase.originalJson, purchase.signature)

/** Google Play's signed purchase payload uses RSA/SHA-1; this is not the APK signing key. */
fun verifyPlaySignature(publicKey: String, payload: String, signature: String): Boolean = runCatching {
    if (publicKey.isBlank() || payload.isBlank() || signature.isBlank()) return false
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
    Signature.getInstance("SHA1withRSA").run {
        initVerify(key); update(payload.toByteArray(Charsets.UTF_8))
        verify(Base64.decode(signature, Base64.DEFAULT))
    }
}.getOrDefault(false)
