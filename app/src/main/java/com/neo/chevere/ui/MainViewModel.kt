package com.neo.chevere.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.InitializationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Global ViewModel for managing the application's initialization state.
 *
 * It observes the AI engine's initialization status to decide when to dismiss
 * the primary splash/loading overlay.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    init {
        viewModelScope.launch {
            // First, check if there's even anything to wait for.
            val models = repository.getLocalModels()
            if (models.isEmpty()) {
                Timber.tag("MainViewModel").d("No local models found; skipping initialization splash.")
                _isInitializing.value = false
                return@launch
            }

            // Observe the repository's initialization status using the typed sealed interface.
            repository.getInitStatus().collectLatest { status ->
                Timber.tag("MainViewModel").d("Engine status update: $status")

                when (status) {
                    is InitializationStatus.Ready,
                    is InitializationStatus.Failure -> {
                        Timber.tag("MainViewModel").d("Termination status reached; dismissing splash.")
                        _isInitializing.value = false
                    }

                    InitializationStatus.Uninitialized -> {
                        // If the engine is currently uninitialized, it might be because the
                        // ChatViewModel hasn't triggered a load yet.
                        delay(3500)
                        if (_isInitializing.value) {
                            Timber.tag("MainViewModel").d(
                                "Timed out waiting for init to start; dismissing splash."
                            )
                            _isInitializing.value = false
                        }
                    }

                    is InitializationStatus.Initializing -> {
                        // Stay in the initializing state
                    }
                }
            }
        }

        // Safety Fallback: Ensure the splash screen is NEVER shown for more than 10 seconds.
        viewModelScope.launch {
            delay(10000)
            if (_isInitializing.value) {
                Timber.tag("MainViewModel").w("Hard safety timeout reached; forcing splash dismissal.")
                _isInitializing.value = false
            }
        }
    }
}
