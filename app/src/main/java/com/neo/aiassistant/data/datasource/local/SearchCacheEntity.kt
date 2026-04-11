package com.neo.aiassistant.data.datasource.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey
    val query: String,
    val results: String,
    val timestamp: Long
)
