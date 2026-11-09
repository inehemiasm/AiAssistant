package com.neo.chevere.ui.purchases

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.chevere.R
import com.neo.chevere.domain.PurchaseSandboxRepository
import com.neo.chevere.domain.SandboxPlan
import com.neo.chevere.domain.SandboxPurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Renderable states for the purchase sandbox. */
sealed interface PurchaseSandboxState {
    data object Loading : PurchaseSandboxState
    data object LoadFailed : PurchaseSandboxState
    data class Ready(
        val plans: List<PlanCard>,
        val activeEntitlements: List<String>,
        val purchase: PurchaseStatus = PurchaseStatus.Idle,
        val completedPlan: String? = null
    ) : PurchaseSandboxState
}

/** Display data for a selectable package in the current offering. */
data class PlanCard(val plan: SandboxPlan, val isActive: Boolean)

/** Progress and feedback for a simulated checkout. */
enum class PurchaseStatus(@param:StringRes @get:StringRes val message: Int?) {
    Idle(null),
    Pending(R.string.sandbox_purchasing),
    Completed(R.string.sandbox_completed),
    Cancelled(R.string.sandbox_cancelled),
    Failed(R.string.sandbox_purchase_failed)
}

/** User actions supported by the Test Store screen. */
sealed interface PurchaseSandboxIntent {
    data object Refresh : PurchaseSandboxIntent
    data class Buy(val activity: Activity, val planId: String) : PurchaseSandboxIntent
}

/** Serializes catalog refresh and checkout, and maps purchase results to screen state. */
@HiltViewModel
class PurchaseSandboxViewModel @Inject constructor(
    private val repository: PurchaseSandboxRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<PurchaseSandboxState>(PurchaseSandboxState.Loading)
    val state = mutableState.asStateFlow()

    init {
        load()
    }

    fun onIntent(intent: PurchaseSandboxIntent) {
        val current = state.value
        if (current == PurchaseSandboxState.Loading) return
        if (current is PurchaseSandboxState.Ready && current.purchase == PurchaseStatus.Pending) return
        when (intent) {
            PurchaseSandboxIntent.Refresh -> load()
            is PurchaseSandboxIntent.Buy -> {
                if (current is PurchaseSandboxState.Ready) {
                    val selected = current.plans.firstOrNull { it.plan.id == intent.planId } ?: return
                    buy(current, intent.activity, selected.plan)
                }
            }
        }
    }

    private fun load() {
        mutableState.value = PurchaseSandboxState.Loading
        viewModelScope.launch {
            val catalog = repository.load()
            mutableState.value = catalog?.let {
                PurchaseSandboxState.Ready(
                    it.plans.map { plan -> PlanCard(plan, plan.productId in it.access.activeProducts) },
                    it.access.activeEntitlements
                )
            } ?: PurchaseSandboxState.LoadFailed
        }
    }

    private fun buy(current: PurchaseSandboxState.Ready, activity: Activity, selected: SandboxPlan) {
        val pending = current.copy(purchase = PurchaseStatus.Pending, completedPlan = null)
        mutableState.value = pending
        viewModelScope.launch {
            mutableState.value = when (val result = repository.purchase(activity, selected.id)) {
                is SandboxPurchaseResult.Completed -> pending.copy(
                    plans = pending.plans.map {
                        it.copy(isActive = it.plan.productId in result.access.activeProducts)
                    },
                    activeEntitlements = result.access.activeEntitlements,
                    purchase = PurchaseStatus.Completed,
                    completedPlan = selected.title
                )
                SandboxPurchaseResult.Cancelled -> pending.copy(purchase = PurchaseStatus.Cancelled)
                SandboxPurchaseResult.Failed -> pending.copy(purchase = PurchaseStatus.Failed)
            }
        }
    }
}
