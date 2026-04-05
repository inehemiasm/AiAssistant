package com.neo.aiassistant.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Base MVI ViewModel class implementing the Model-View-Intent pattern
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    application: Application,
    initialState: S,
) : AndroidViewModel(application) {
    val currentState: S
        get() = uiState.value
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()
    private val _effect: Channel<E> = Channel(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: I) {
        viewModelScope.launch {
            handleIntent(intent)
        }
    }

    protected abstract suspend fun handleIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    protected fun sendEffect(builder: () -> E) {
        viewModelScope.launch {
            _effect.send(builder())
        }
    }
}

interface UiState

interface UiIntent

interface UiEffect
