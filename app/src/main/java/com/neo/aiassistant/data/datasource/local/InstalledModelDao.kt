package com.neo.aiassistant.data.datasource.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledModelDao {
    @Query("SELECT * FROM installed_models")
    suspend fun getAllModels(): List<InstalledModelEntity>

    @Query("SELECT * FROM installed_models")
    fun getAllModelsFlow(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models WHERE id = :id")
    suspend fun getModelById(id: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("UPDATE installed_models SET installStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: com.neo.aiassistant.domain.InstallStatus)
}
