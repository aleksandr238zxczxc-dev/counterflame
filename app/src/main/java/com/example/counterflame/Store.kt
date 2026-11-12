package com.example.counterflame

import android.app.Activity
import android.content.SharedPreferences
import com.android.billingclient.api.*

class Store(private val activity: Activity, private val prefs: SharedPreferences) {

    private val owned: MutableSet<String> =
        prefs.getString("owned", "default")!!.split(",").toMutableSet()

    private var billing: BillingClient? = null

    fun isOwned(skinId: String) = skinId in owned || skinId == "default"

    fun selectedSkin(): String = prefs.getString("selected", "default") ?: "default"

    fun selectSkin(id: String): Boolean {
        if (!isOwned(id)) return false
        prefs.edit().putString("selected", id).apply()
        return true
    }

    fun devUnlock(id: String) {
        owned.add(id)
        prefs.edit().putString("owned", owned.joinToString(",")).apply()
    }

    fun initBilling(onReady: () -> Unit) {
        val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (p in purchases) grantPurchase(p)
            }
        }
        billing = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdated)
            .enablePendingPurchases()
            .build()
        billing?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun launchPurchase(skin: Skin) {
        val b = billing ?: return
        val q = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("skin_${skin.id}")
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()
        b.queryProductDetailsAsync(q) { r, list ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK || list.isEmpty())
                return@queryProductDetailsAsync
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(list[0]).build()
                )).build()
            b.launchBillingFlow(activity, params)
        }
    }

    private fun grantPurchase(p: Purchase) {
        for (product in p.products) {
            val id = product.removePrefix("skin_")
            devUnlock(id)
        }
    }

    fun destroy() { billing?.endConnection() }
}
