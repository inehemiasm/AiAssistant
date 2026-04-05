package com.neo.aiassistant.domain

import android.net.Uri
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(text: String, imageUri: Uri? = null) = repository.sendMessage(text, imageUri)
}
