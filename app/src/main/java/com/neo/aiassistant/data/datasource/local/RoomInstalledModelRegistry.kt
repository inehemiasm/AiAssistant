package com.neo.aiassistant.data.datasource.local

import com.neo.aiassistant.domain.InstallStatus
import com.neo.aiassistant.domain.InstalledModel
import com.neo.aiassistant.domain.InstalledModelRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomInstalledModelRegistry @Inject constructor(
    private val dao: InstalledModelDao
) : InstalledModelRegistry {

    override suspend fun getInstalledModels(): List<InstalledModel> {
        return dao.getAllModels().map { it.toDomain() }
    }

    override fun getInstalledModelsFlow(): Flow<List<InstalledModel>> {
        return dao.getAllModelsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getInstalledModel(id: String): InstalledModel? {
        return dao.getModelById(id)?.toDomain()
    }

    override suspend fun upsertInstalledModel(model: InstalledModel) {
        dao.upsertModel(model.toEntity())
    }

    override suspend fun removeInstalledModel(id: String) {
        dao.deleteModel(id)
    }

    override suspend fun updateInstallStatus(id: String, status: InstallStatus) {
        dao.updateStatus(id, status)
    }
}
