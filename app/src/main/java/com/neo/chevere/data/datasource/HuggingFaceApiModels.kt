package com.neo.chevere.data.datasource

import kotlinx.serialization.Serializable

@Serializable
data class HFHubModel(
    val id: String,
    val author: String? = null,
    val lastModified: String? = null,
    val siblings: List<HFHubSibling> = emptyList(),
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0
)

@Serializable
data class HFHubSibling(
    val rfilename: String,
    val size: Long? = null
)
