package com.neo.chevere.ui.purchases

import android.app.Activity
import com.neo.chevere.domain.SandboxAccess
import com.neo.chevere.domain.PurchaseSandboxRepository
import com.neo.chevere.domain.SandboxCatalog
import com.neo.chevere.domain.SandboxPlan
import com.neo.chevere.domain.SandboxPurchaseResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseSandboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onlyDisplayedPackageCanStartOnePurchaseAndCompletionUpdatesEntitlements() = runTest(dispatcher) {
        val outcome = CompletableDeferred<SandboxPurchaseResult>()
        val purchasedIds = mutableListOf<String>()
        val repo = object : PurchaseSandboxRepository {
            override suspend fun load() = SandboxCatalog(
                listOf(SandboxPlan("monthly", "monthly_product", "Monthly", "One month", "$2.99")),
                SandboxAccess(emptySet(), emptyList())
            )

            override suspend fun purchase(activity: Activity, planId: String): SandboxPurchaseResult {
                purchasedIds += planId
                return outcome.await()
            }
        }
        val activity = mock<Activity>()
        val viewModel = PurchaseSandboxViewModel(repo)
        advanceUntilIdle()

        viewModel.onIntent(PurchaseSandboxIntent.Buy(activity, "unknown"))
        viewModel.onIntent(PurchaseSandboxIntent.Buy(activity, "monthly"))
        viewModel.onIntent(PurchaseSandboxIntent.Buy(activity, "monthly"))
        advanceUntilIdle()

        assertEquals(listOf("monthly"), purchasedIds)
        assertEquals(PurchaseStatus.Pending, (viewModel.state.value as PurchaseSandboxState.Ready).purchase)
        outcome.complete(SandboxPurchaseResult.Completed(SandboxAccess(setOf("monthly_product"), listOf("premium"))))
        advanceUntilIdle()

        val completed = viewModel.state.value as PurchaseSandboxState.Ready
        assertEquals(PurchaseStatus.Completed, completed.purchase)
        assertEquals(listOf("premium"), completed.activeEntitlements)
        assertEquals("Monthly", completed.completedPlan)
        assertEquals(true, completed.plans.single().isActive)
        verifyNoInteractions(activity)
    }
}
