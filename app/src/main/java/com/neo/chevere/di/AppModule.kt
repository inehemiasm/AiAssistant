package com.neo.chevere.di

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DefaultDispatcherProvider
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.ChatRepositoryImpl
import com.neo.chevere.data.datasource.CompositeModelCatalogDataSource
import com.neo.chevere.data.datasource.DefaultRemoteModelDataSource
import com.neo.chevere.data.datasource.ModelCatalogDataSource
import com.neo.chevere.data.datasource.RemoteModelDataSource
import com.neo.chevere.data.inference.LlmEngineWrapper
import com.neo.chevere.data.inference.RealLlmEngineWrapper
import com.neo.chevere.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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
        defaultRemoteModelDataSource: DefaultRemoteModelDataSource
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
                
                install(HttpRedirect) {
                    checkHttpMethod = false
                }
                
                // Add default headers to avoid being blocked by Kaggle/CDNs
                install(DefaultRequest) {
                    header(HttpHeaders.UserAgent, Constants.Network.DEFAULT_USER_AGENT)
                    header(HttpHeaders.Accept, Constants.Network.ACCEPT_ALL)
                    header("Referer", Constants.Network.KAGGE_REFERER)
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
