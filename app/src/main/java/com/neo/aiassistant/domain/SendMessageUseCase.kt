package com.neo.aiassistant.domain

import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(text: String) = repository.sendMessage(text)
}
