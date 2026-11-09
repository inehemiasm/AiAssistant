package com.neo.chevere.domain

import android.app.Activity

/** Test Store package as displayed in the current offering. */
data class SandboxPlan(
    val id: String,
    val productId: String,
    val title: String,
    val description: String,
    val price: String
)

/** Active products and access reported by the Test Store. */
data class SandboxAccess(val activeProducts: Set<String>, val activeEntitlements: List<String>)

/** Current offering and the customer's Test Store access. */
data class SandboxCatalog(val plans: List<SandboxPlan>, val access: SandboxAccess)

/** Outcomes reported by the billing provider after a simulated purchase. */
sealed interface SandboxPurchaseResult {
    data class Completed(val access: SandboxAccess) : SandboxPurchaseResult
    data object Cancelled : SandboxPurchaseResult
    data object Failed : SandboxPurchaseResult
}

/** Reads the test catalog and launches simulated purchases from an Android activity. */
interface PurchaseSandboxRepository {
    suspend fun load(): SandboxCatalog?
    suspend fun purchase(activity: Activity, planId: String): SandboxPurchaseResult
}
