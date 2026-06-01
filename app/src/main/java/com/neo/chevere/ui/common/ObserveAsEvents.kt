package com.neo.chevere.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Collects a [Flow] of one-off events/effects.
 *
 * Ensures events are collected and processed immediately.
 */
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    onEvent: suspend (T) -> Unit
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(flow) {
        withContext(Dispatchers.Main.immediate) {
            flow.collect { event ->
                launch {
                    currentOnEvent(event)
                }
            }
        }
    }
}
