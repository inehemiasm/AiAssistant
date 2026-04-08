package com.neo.aiassistant.di

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.neo.aiassistant.core.DefaultDispatcherProvider
import com.neo.aiassistant.core.DispatcherProvider
import com.neo.aiassistant.data.ChatRepositoryImpl
import com.neo.aiassistant.data.datasource.CompositeModelCatalogDataSource
import com.neo.aiassistant.data.datasource.FirebaseRemoteModelDataSource
import com.neo.aiassistant.data.datasource.FirestoreModelCatalogDataSource
import com.neo.aiassistant.data.datasource.ModelCatalogDataSource
import com.neo.aiassistant.data.datasource.RemoteModelDataSource
import com.neo.aiassistant.data.inference.LlmEngineWrapper
import com.neo.aiassistant.data.inference.RealLlmEngineWrapper
import com.neo.aiassistant.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindRemoteModelDataSource(
        firebaseRemoteModelDataSource: FirebaseRemoteModelDataSource
    ): RemoteModelDataSource

    @Binds
    @Singleton
    abstract fun bindModelCatalogDataSource(
        compositeModelCatalogDataSource: CompositeModelCatalogDataSource
    ): ModelCatalogDataSource

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        defaultDispatcherProvider: DefaultDispatcherProvider
    ): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindLlmEngineWrapper(
        realLlmEngineWrapper: RealLlmEngineWrapper
    ): LlmEngineWrapper

    companion object {
        @Provides
        @Singleton
        fun provideFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }

        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                        isLenient = true
                    })
                }
            }
        }

        @Provides
        @Singleton
        fun provideRealLlmEngineWrapper(): RealLlmEngineWrapper {
            return RealLlmEngineWrapper()
        }

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
            return WorkManager.getInstance(context)
        }
    }
}
