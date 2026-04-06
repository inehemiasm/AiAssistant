package com.neo.aiassistant.di

import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.tools.SummarizeTextTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @IntoSet
    fun provideSummarizeTextTool(): AgentTool {
        return SummarizeTextTool()
    }
    
    // Additional tools can be added here using @IntoSet
}
