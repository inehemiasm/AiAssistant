package com.neo.chevere.core

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

/**
 * Base ViewModel class implementing the Model-View-Intent (MVI) pattern.
 *
 * This class provides a structured way to manage UI state, handle user intents,
 * and emit one-time side effects.
 *
 * @param S The type of the UI state, must implement [UiState].
 * @param I The type of user intents, must implement [UiIntent].
 * @param E The type of side effects, must implement [UiEffect].
 * @property application The application context.
 * @param initialState The initial state of the UI.
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    application: Application,
    initialState: S,
) : AndroidViewModel(application) {
    
    /**
     * The current state of the UI.
     */
    val currentState: S
        get() = uiState.value
    
    private val _uiState = MutableStateFlow(initialState)
    
    /**
     * A [StateFlow] emitting the current UI state.
     */
    val uiState: StateFlow<S> = _uiState.asStateFlow()
    
    private val _effect: Channel<E> = Channel(Channel.BUFFERED)
    
    /**
     * A [Flow] emitting one-time side effects.
     */
    val effect = _effect.receiveAsFlow()

    /**
     * Handles a user intent.
     *
     * @param intent The intent to be processed.
     */
    fun onIntent(intent: I) {
        viewModelScope.launch {
            handleIntent(intent)
        }
    }

    /**
     * Subclasses must implement this to define how each intent is handled.
     *
     * @param intent The intent to be processed.
     */
    protected abstract suspend fun handleIntent(intent: I)

    /**
     * Updates the current UI state.
     *
     * @param reducer A function that takes the current state and returns the new state.
     */
    protected fun setState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    /**
     * Emits a one-time side effect.
     *
     * @param builder A function that returns the effect to be sent.
     */
    protected fun sendEffect(builder: () -> E) {
        viewModelScope.launch {
            _effect.send(builder())
        }
    }
}

/**
 * Marker interface for UI state.
 */
interface UiState

/**
 * Marker interface for user intents.
 */
interface UiIntent

/**
 * Marker interface for one-time side effects.
 */
interface UiEffect
