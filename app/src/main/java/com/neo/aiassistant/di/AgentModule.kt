package com.neo.aiassistant.di

import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.tools.ListModelsTool
import com.neo.aiassistant.data.agent.tools.RuntimeStatusTool
import com.neo.aiassistant.data.agent.tools.SelectModelTool
import com.neo.aiassistant.data.agent.tools.SummarizeTextTool
import com.neo.aiassistant.data.agent.tools.WebSearchTool
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
import com.neo.aiassistant.domain.InstalledModelRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @IntoSet
    fun provideSummarizeTextTool(): AgentTool {
        return SummarizeTextTool()
    }

    @Provides
    @IntoSet
    fun provideWebSearchTool(
        httpClient: HttpClient,
        searchCacheDao: SearchCacheDao
    ): AgentTool {
        return WebSearchTool(httpClient, searchCacheDao)
    }

    @Provides
    @IntoSet
    fun provideListModelsTool(
        registry: InstalledModelRegistry
    ): AgentTool {
        return ListModelsTool(registry)
    }

    @Provides
    @IntoSet
    fun provideSelectModelTool(
        preferenceManager: PreferenceManager,
        registry: InstalledModelRegistry
    ): AgentTool {
        return SelectModelTool(preferenceManager, registry)
    }

    @Provides
    @IntoSet
    fun provideRuntimeStatusTool(
        preferenceManager: PreferenceManager
    ): AgentTool {
        return RuntimeStatusTool(preferenceManager)
    }
}
