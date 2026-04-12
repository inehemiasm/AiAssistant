package com.neo.aiassistant.di

import android.content.Context
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.actions.AndroidAppActionExecutor
import com.neo.aiassistant.data.agent.actions.DefaultAndroidAppActionExecutor
import com.neo.aiassistant.data.agent.tools.*
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
import com.neo.aiassistant.domain.InstalledModelRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAndroidAppActionExecutor(
        executor: DefaultAndroidAppActionExecutor
    ): AndroidAppActionExecutor

    companion object {
        @Provides
        @IntoSet
        fun provideSummarizeTextTool(): AgentTool {
            return SummarizeTextTool()
        }

        @Provides
        @IntoSet
        fun provideWebSearchTool(
            @ApplicationContext context: Context,
            httpClient: HttpClient,
            searchCacheDao: SearchCacheDao
        ): AgentTool {
            return WebSearchTool(context, httpClient, searchCacheDao)
        }

        @Provides
        @IntoSet
        fun provideListModelsTool(
            registry: InstalledModelRegistry,
            preferenceManager: PreferenceManager
        ): AgentTool {
            return ListModelsTool(registry, preferenceManager)
        }

        @Provides
        @IntoSet
        fun provideGetActiveModelTool(
            registry: InstalledModelRegistry,
            preferenceManager: PreferenceManager
        ): AgentTool {
            return GetActiveModelTool(registry, preferenceManager)
        }

        @Provides
        @IntoSet
        fun provideGetModelDetailsTool(
            registry: InstalledModelRegistry,
            preferenceManager: PreferenceManager
        ): AgentTool {
            return GetModelDetailsTool(registry, preferenceManager)
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
        fun provideRecommendModelTool(
            registry: InstalledModelRegistry
        ): AgentTool {
            return RecommendModelTool(registry)
        }

        @Provides
        @IntoSet
        fun provideRuntimeStatusTool(
            preferenceManager: PreferenceManager
        ): AgentTool {
            return RuntimeStatusTool(preferenceManager)
        }

        @Provides
        @IntoSet
        fun provideCopyToClipboardTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = CopyToClipboardTool(executor)

        @Provides
        @IntoSet
        fun provideShareTextTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = ShareTextTool(executor)

        @Provides
        @IntoSet
        fun provideOpenUrlTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = OpenUrlTool(executor)

        @Provides
        @IntoSet
        fun provideOpenMapsTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = OpenMapsTool(executor)

        @Provides
        @IntoSet
        fun provideDraftEmailTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = DraftEmailTool(executor)

        @Provides
        @IntoSet
        fun provideCreateCalendarEventTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = CreateCalendarEventTool(executor)

        @Provides
        @IntoSet
        fun provideSearchAppsTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = SearchAppsTool(executor)

        @Provides
        @IntoSet
        fun provideListAppsTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = ListAppsTool(executor)

        @Provides
        @IntoSet
        fun provideLaunchAppTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = LaunchAppTool(executor)

        @Provides
        @IntoSet
        fun provideOpenAppTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = OpenAppTool(executor)

        @Provides
        @IntoSet
        fun provideGetAppCapabilitiesTool(
            executor: AndroidAppActionExecutor
        ): AgentTool = GetAppCapabilitiesTool(executor)
    }
}
