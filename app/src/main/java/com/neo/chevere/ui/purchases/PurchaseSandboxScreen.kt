package com.neo.chevere.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.chevere.R

/** Chevere's native purchase UI for the RevenueCat Test Store. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseSandboxScreen(onBack: () -> Unit, viewModel: PurchaseSandboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending = (state as? PurchaseSandboxState.Ready)?.purchase == PurchaseStatus.Pending
    val activity = LocalActivity.current
    BackHandler(enabled = pending) {}
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sandbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !pending) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sandbox_badge), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.sandbox_intro), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            when (val current = state) {
                PurchaseSandboxState.Loading -> item { CircularProgressIndicator() }
                PurchaseSandboxState.LoadFailed -> item { Text(stringResource(R.string.sandbox_load_failed)) }
                is PurchaseSandboxState.Ready -> {
                    item {
                        Column(
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(stringResource(R.string.sandbox_entitlements), style = MaterialTheme.typography.titleMedium)
                            if (current.activeEntitlements.isEmpty()) {
                                Text(stringResource(R.string.sandbox_no_entitlements))
                            }
                            current.activeEntitlements.forEach { Text(it) }
                            current.purchase.message?.let { Text(stringResource(it)) }
                            current.completedPlan?.let { Text(stringResource(R.string.sandbox_purchased_plan, it)) }
                        }
                    }
                    if (current.plans.isEmpty()) {
                        item { Text(stringResource(R.string.sandbox_no_products)) }
                    }
                    items(current.plans, key = { it.plan.id }) { card ->
                        val plan = card.plan
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(plan.title, style = MaterialTheme.typography.titleLarge)
                                Text(plan.description)
                                Text(plan.price, style = MaterialTheme.typography.titleMedium)
                                if (card.isActive) {
                                    Text(stringResource(R.string.sandbox_active_plan), color = MaterialTheme.colorScheme.primary)
                                }
                                Button(
                                    enabled = !pending && activity != null,
                                    onClick = {
                                        activity?.let { viewModel.onIntent(PurchaseSandboxIntent.Buy(it, plan.id)) }
                                    }
                                ) { Text(stringResource(R.string.sandbox_buy)) }
                            }
                        }
                    }
                }
            }
            item {
                FilledTonalButton(
                    modifier = Modifier.padding(bottom = 16.dp),
                    enabled = !pending && state != PurchaseSandboxState.Loading,
                    onClick = { viewModel.onIntent(PurchaseSandboxIntent.Refresh) }
                ) { Text(stringResource(R.string.sandbox_refresh)) }
            }
        }
    }
}
