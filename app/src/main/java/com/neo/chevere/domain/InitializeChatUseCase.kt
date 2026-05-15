package com.neo.chevere.domain

import javax.inject.Inject

/**
 * Use case for initializing the AI chat engine with a specific model.
 *
 * This use case handles the preparation of the AI engine, making it ready
 * to process user messages.
 *
 * @property repository The repository used to manage the chat engine lifecycle.
 */
class InitializeChatUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    /**
     * Executes the use case to initialize the model.
     *
     * @param modelPath The absolute path to the model file on device.
     * @param notify Whether to emit an activation event on success.
     * @return A [Result] indicating whether initialization was successful.
     */
    suspend operator fun invoke(modelPath: String, notify: Boolean = true) = 
        repository.initializeModel(modelPath, notify)
}
