package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.example.data.AppPreferences
import com.example.telemetry.AppTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BillingUiState(val price: String? = null, val ready: Boolean = false,
    val busy: Boolean = false, val pending: Boolean = false, val error: String? = null,
    val message: String? = null)

class BillingManager(context: Context, private val preferences: AppPreferences,
                     private val telemetry: AppTelemetry, private val scope: CoroutineScope,
                     private val verifier: PurchaseVerifier) : AutoCloseable {
    private val mutableState = MutableStateFlow(BillingUiState())
    val state = mutableState.asStateFlow()
    private var productDetails: ProductDetails? = null
    private var selectedOffer: ProductDetails.OneTimePurchaseOfferDetails? = null
    private var connecting = false
    private var closed = false
    private var connectedOnce = false
    private var queryingPurchases = false
    private val verificationMutex = Mutex()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> process(purchases.orEmpty(), restored = false)
                BillingClient.BillingResponseCode.USER_CANCELED -> mutableState.value = mutableState.value.copy(busy = false)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                else -> error(billingErrorMessage(result))
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun initialize() {
        if (closed || connecting) return
        if (client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connectedOnce = true
                    queryExistingPurchases(); loadProductDetails()
                } else { error(billingErrorMessage(result)) }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false
                mutableState.value = mutableState.value.copy(busy = false)
                // PBL reconnects on the next API call; no competing retry timer.
            }
        })
    }
    fun refresh() {
        if (closed || connecting) return
        if (connectedOnce) { queryExistingPurchases(); loadProductDetails() } else initialize()
    }
    fun restorePurchases() {
        if (!verifier.canPurchase()) { error("Увійдіть у застосунок. Для відновлення потрібен акаунт Google Play, з якого придбано Premium."); return }
        mutableState.value = mutableState.value.copy(message = null, error = null)
        refresh()
    }

    fun launchPurchase(activity: Activity) {
        if (!verifier.canPurchase()) { error("Для покупки увійдіть через Google."); return }
        if (!verifier.configured()) { error("Покупки ще не налаштовано в цій збірці."); return }
        if (productDetails == null || selectedOffer == null) { refresh(); error("Дані покупки ще завантажуються. Спробуйте ще раз."); return }
        if (mutableState.value.busy || mutableState.value.pending) return
        mutableState.value = mutableState.value.copy(busy = true, error = null, message = null)
        telemetry.premiumOpened()
        val account = verifier.accountFingerprint() ?: run { error("Для покупки увійдіть через Google."); return }
        // ProductDetails/offer tokens may expire: query again before opening Play's confirmation.
        loadProductDetails { details, offer ->
            if (activity.isFinishing || activity.isDestroyed || !verifier.canPurchase() || verifier.accountFingerprint() != account) {
                error("Акаунт або екран змінився. Повторіть покупку.")
            } else {
                val params = BillingFlowParams.newBuilder().setObfuscatedAccountId(account).setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
                        .apply { offer.offerToken?.let { setOfferToken(it) } }.build())).build()
                val result = client.launchBillingFlow(activity, params)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) error(billingErrorMessage(result))
            }
        }
    }
    private fun queryExistingPurchases() {
        if (queryingPurchases || closed) return
        queryingPurchases = true
        val queryUid = verifier.currentUid()
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            queryingPurchases = false
            if (closed) return@queryPurchasesAsync
            if (verifier.currentUid() != queryUid) { refresh(); return@queryPurchasesAsync }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) { error("Не вдалося перевірити попередні покупки."); return@queryPurchasesAsync }
            val owned = purchases.filter { PREMIUM_PRODUCT_ID in it.products }
            if (owned.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                scope.launch { verificationMutex.withLock {
                    val pending = owned.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                    mutableState.value = mutableState.value.copy(busy = true, pending = pending)
                    try {
                        if (verifier.currentUid() == queryUid) {
                            preferences.setPremium(false, queryUid)
                            mutableState.value = mutableState.value.copy(error = null,
                                message = if (pending) "Очікуємо завершення оплати в Google Play."
                                    else "У поточного акаунта Google Play немає активної покупки Premium.")
                        }
                    } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
                        telemetry.recordException(failure)
                        error("Не вдалося перевірити статус Преміум. Спробуйте ще раз пізніше.")
                    } finally { mutableState.value = mutableState.value.copy(busy = false) }
                } }
            } else process(owned, restored = true)
        }
    }
    private fun process(purchases: List<Purchase>, restored: Boolean) {
        if (closed) return
        val uid = verifier.currentUid() ?: return
        val premium = purchases.filter { PREMIUM_PRODUCT_ID in it.products }
        mutableState.value = mutableState.value.copy(busy = false,
            pending = premium.any { it.purchaseState == Purchase.PurchaseState.PENDING })
        premium.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            scope.launch {
                verificationMutex.withLock {
                    mutableState.value = mutableState.value.copy(busy = true, error = null)
                    try {
                        val verified = verifier.verify(purchase, uid)
                        preferences.setPremium(verified.active, verified.uid)
                        if (!purchase.isAcknowledged) {
                            val result = acknowledge(purchase.purchaseToken)
                            check(result.responseCode == BillingClient.BillingResponseCode.OK) { "Play acknowledgement failed" }
                        }
                        check(verifier.currentUid() == uid) { "Акаунт змінився." }
                        telemetry.premiumVerified(restored, verified.active)
                        mutableState.value = mutableState.value.copy(busy = false, pending = false,
                            message = if (verified.active) "Преміум підтверджено. Рекламу вимкнено." else "Покупка більше не активна.")
                    } catch (cancelled: CancellationException) {
                        mutableState.value = mutableState.value.copy(busy = false)
                        throw cancelled
                    } catch (failure: Exception) {
                        telemetry.recordException(failure)
                        error("Не вдалося підтвердити покупку. Відкрийте налаштування після відновлення зв’язку.")
                    }
                }
            }
        }
    }
    private suspend fun acknowledge(token: String): BillingResult = suspendCancellableCoroutine { continuation ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
        }
    }
    private fun loadProductDetails(onReady: ((ProductDetails, ProductDetails.OneTimePurchaseOfferDetails) -> Unit)? = null) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryProductDetailsAsync(params) { result, query ->
            if (closed) return@queryProductDetailsAsync
            val details = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                query.productDetailsList.firstOrNull { it.productId == PREMIUM_PRODUCT_ID } else null
            // Premium is a lifetime purchase; never silently select a rental or preorder.
            val offer = details?.oneTimePurchaseOfferDetailsList.orEmpty()
                .filter { it.rentalDetails == null && it.preorderDetails == null }
                .minWithOrNull(compareBy<ProductDetails.OneTimePurchaseOfferDetails> { it.priceAmountMicros }.thenBy { it.offerToken })
            productDetails = details
            selectedOffer = offer
            mutableState.value = mutableState.value.copy(price = offer?.formattedPrice,
                ready = details != null && offer != null)
            if (details == null || offer == null) {
                error(if (result.responseCode == BillingClient.BillingResponseCode.OK) "Покупка зараз недоступна. Спробуйте пізніше." else billingErrorMessage(result))
            } else onReady?.invoke(details, offer)
        }
    }
    private fun error(message: String) { mutableState.value = mutableState.value.copy(error = message, busy = false) }
    override fun close() { closed = true; client.endConnection() }
    companion object { const val PREMIUM_PRODUCT_ID = "premium_remove_ads" }
}

fun billingErrorMessage(result: BillingResult): String = when (result.onPurchasesUpdatedSubResponseCode) {
    BillingClient.OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS ->
        "Недостатньо коштів. Перевірте спосіб оплати в Google Play."
    BillingClient.OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE ->
        "Ця пропозиція недоступна для вашого акаунта. Оновіть дані покупки."
    else -> when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Оплата Google Play недоступна або заблокована на цьому пристрої."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Немає зв’язку з Google Play. Перевірте інтернет і спробуйте ще раз."
        else -> "Не вдалося завершити покупку. Спробуйте ще раз."
    }
}
