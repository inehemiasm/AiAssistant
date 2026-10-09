package com.neo.chevere.shared.domain

/**
 * Platform-neutral reference to media supplied to or produced by a model.
 * Platform adapters are responsible for converting it to Uri, NSURL, or a
 * runtime-specific representation.
 */
sealed interface MediaReference {
    val mimeType: String?

    data class Uri(
        val value: String,
        override val mimeType: String? = null
    ) : MediaReference {
        init {
            require(value.isNotBlank()) { "Media URI cannot be blank." }
        }
    }

    data class LocalFile(
        val path: String,
        override val mimeType: String? = null
    ) : MediaReference {
        init {
            require(path.isNotBlank()) { "Media file path cannot be blank." }
        }
    }
}
