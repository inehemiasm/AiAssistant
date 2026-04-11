package com.neo.aiassistant.di

import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.tools.SummarizeTextTool
import com.neo.aiassistant.data.agent.tools.WebSearchTool
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
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
    
}
