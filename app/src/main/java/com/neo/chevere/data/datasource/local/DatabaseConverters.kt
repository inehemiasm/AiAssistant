package com.neo.chevere.data.datasource.local

import androidx.room.TypeConverter
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.ModelFormat
import com.neo.chevere.domain.ModelRuntime
import com.neo.chevere.domain.ModelSource
import com.neo.chevere.domain.ModelTaskType

class DatabaseConverters {
    @TypeConverter
    fun fromModelSource(value: ModelSource) = value.name

    @TypeConverter
    fun toModelSource(value: String) = enumValueOrDefault<ModelSource>(value, ModelSource.UNKNOWN)

    @TypeConverter
    fun fromModelFormat(value: ModelFormat) = value.name

    @TypeConverter
    fun toModelFormat(value: String): ModelFormat {
        return when (value) {
            "STABLE_DIFFUSION" -> ModelFormat.IMAGE_GENERATOR_BUNDLE
            else -> enumValueOrDefault(value, ModelFormat.UNKNOWN)
        }
    }

    @TypeConverter
    fun fromModelRuntime(value: ModelRuntime) = value.name

    @TypeConverter
    fun toModelRuntime(value: String): ModelRuntime {
        return when (value) {
            "STABLE_DIFFUSION" -> ModelRuntime.IMAGE_GENERATOR
            else -> enumValueOrDefault(value, ModelRuntime.UNKNOWN)
        }
    }

    @TypeConverter
    fun fromModelTaskType(value: ModelTaskType) = value.name

    @TypeConverter
    fun toModelTaskType(value: String) =
        enumValueOrDefault<ModelTaskType>(value, ModelTaskType.UNKNOWN)

    @TypeConverter
    fun fromInstallStatus(value: InstallStatus) = value.name

    @TypeConverter
    fun toInstallStatus(value: String) = enumValueOrDefault(value, InstallStatus.INVALID)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
