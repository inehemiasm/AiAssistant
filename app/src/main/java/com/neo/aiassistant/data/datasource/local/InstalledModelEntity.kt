package com.neo.aiassistant.data.datasource.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neo.aiassistant.domain.*

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val filePath: String,
    val fileName: String,
    val source: ModelSource,
    val format: ModelFormat,
    val runtime: ModelRuntime,
    val taskType: ModelTaskType,
    val capabilities: String, // Stored as comma-separated values
    val installStatus: InstallStatus,
    val sizeBytes: Long?,
    val checksum: String?,
    val installedAt: Long?
)

fun InstalledModelEntity.toDomain(): InstalledModel {
    return InstalledModel(
        id = id,
        displayName = displayName,
        filePath = filePath,
        fileName = fileName,
        source = source,
        format = format,
        runtime = runtime,
        taskType = taskType,
        capabilities = capabilities.split(",").filter { it.isNotEmpty() }.map { ModelCapability.valueOf(it) }.toSet(),
        installStatus = installStatus,
        sizeBytes = sizeBytes,
        checksum = checksum,
        installedAt = installedAt
    )
}

fun InstalledModel.toEntity(): InstalledModelEntity {
    return InstalledModelEntity(
        id = id,
        displayName = displayName,
        filePath = filePath,
        fileName = fileName,
        source = source,
        format = format,
        runtime = runtime,
        taskType = taskType,
        capabilities = capabilities.joinToString(",") { it.name },
        installStatus = installStatus,
        sizeBytes = sizeBytes,
        checksum = checksum,
        installedAt = installedAt
    )
}
