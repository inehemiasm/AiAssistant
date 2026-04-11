package com.neo.aiassistant.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE query = :query LIMIT 1")
    suspend fun getCachedResult(query: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(entity: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE timestamp < :expirationTime")
    suspend fun deleteExpired(expirationTime: Long)

    @Query("DELETE FROM search_cache WHERE query NOT IN (SELECT query FROM search_cache ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneToLimit(limit: Int)

    @Transaction
    suspend fun insertAndPrune(entity: SearchCacheEntity, limit: Int) {
        insertResult(entity)
        pruneToLimit(limit)
    }
}
