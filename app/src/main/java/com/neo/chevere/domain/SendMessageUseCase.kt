package com.neo.chevere.domain

import android.net.Uri
import javax.inject.Inject

/**
 * Use case for sending a message to the AI and receiving a response.
 *
 * This use case wraps the [ChatRepository.sendMessage] method to provide a clean
 * interface for sending text prompts and optional images to the AI.
 *
 * @property repository The repository used to interact with the AI agent.
 */
class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    /**
     * Executes the use case.
     *
     * @param text The user's input message.
     * @param imageUri Optional URI of an image to be processed by the AI.
     * @return A [Result] containing the AI's response text.
     */
    suspend operator fun invoke(text: String, imageUri: Uri? = null) =
        repository.sendMessage(text, imageUri)
}
