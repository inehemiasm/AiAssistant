package com.neo.aiassistant.domain

import javax.inject.Inject

class InitializeChatUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(modelPath: String) = repository.initializeModel(modelPath)
}
