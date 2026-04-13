package com.neo.chevere.data.datasource.local

import androidx.room.TypeConverter
import com.neo.chevere.domain.*

class DatabaseConverters {
    @TypeConverter
    fun fromModelSource(value: ModelSource) = value.name

    @TypeConverter
    fun toModelSource(value: String) = ModelSource.valueOf(value)

    @TypeConverter
    fun fromModelFormat(value: ModelFormat) = value.name

    @TypeConverter
    fun toModelFormat(value: String) = ModelFormat.valueOf(value)

    @TypeConverter
    fun fromModelRuntime(value: ModelRuntime) = value.name

    @TypeConverter
    fun toModelRuntime(value: String) = ModelRuntime.valueOf(value)

    @TypeConverter
    fun fromModelTaskType(value: ModelTaskType) = value.name

    @TypeConverter
    fun toModelTaskType(value: String) = ModelTaskType.valueOf(value)

    @TypeConverter
    fun fromInstallStatus(value: InstallStatus) = value.name

    @TypeConverter
    fun toInstallStatus(value: String) = InstallStatus.valueOf(value)
}
