package com.neo.chevere.data.purchases

import android.app.Activity
import com.neo.chevere.BuildConfig
import com.neo.chevere.domain.PurchaseSandboxRepository
import com.neo.chevere.domain.SandboxAccess
import com.neo.chevere.domain.SandboxCatalog
import com.neo.chevere.domain.SandboxPlan
import com.neo.chevere.domain.SandboxPurchaseResult
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import javax.inject.Inject

/** Maps RevenueCat's Test Store offering and purchase outcomes to domain models. */
class RevenueCatSandboxRepository @Inject constructor() : PurchaseSandboxRepository {
    private var packages: Map<String, Package> = emptyMap()

    override suspend fun load(): SandboxCatalog? {
        packages = emptyMap()
        val purchases = sandboxPurchases() ?: return null
        return try {
            val available = purchases.awaitOfferings().current?.availablePackages.orEmpty()
            purchases.invalidateCustomerInfoCache()
            val access = purchases.awaitCustomerInfo().toSandboxAccess()
            packages = available.associateBy { it.identifier }
            SandboxCatalog(
                plans = available.map {
                    SandboxPlan(
                        it.identifier, it.product.id, it.product.title, it.product.description, it.product.price.formatted
                    )
                },
                access = access
            )
        } catch (_: PurchasesException) {
            null
        }
    }

    override suspend fun purchase(activity: Activity, planId: String): SandboxPurchaseResult {
        val purchases = sandboxPurchases() ?: return SandboxPurchaseResult.Failed
        val selected = packages[planId] ?: return SandboxPurchaseResult.Failed
        return try {
            val result = purchases.awaitPurchase(PurchaseParams.Builder(activity, selected).build())
            SandboxPurchaseResult.Completed(result.customerInfo.toSandboxAccess())
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) SandboxPurchaseResult.Cancelled else SandboxPurchaseResult.Failed
        } catch (_: PurchasesException) {
            SandboxPurchaseResult.Failed
        }
    }

    private fun CustomerInfo.toSandboxAccess() = SandboxAccess(
        activeSubscriptions,
        entitlements.active.filterValues { it.isSandbox }.keys.sorted()
    )

    private fun sandboxPurchases(): Purchases? =
        if (BuildConfig.DEBUG && BuildConfig.REVENUECAT_SDK_KEY.startsWith("test_") && Purchases.isConfigured) {
            Purchases.sharedInstance
        } else {
            null
        }
}
