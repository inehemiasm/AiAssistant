package com.neo.aiassistant.data.datasource

import android.util.Log
import com.neo.aiassistant.domain.ModelEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A composite implementation of ModelCatalogDataSource that merges results from multiple sources.
 */
@Singleton
class CompositeModelCatalogDataSource @Inject constructor(
    private val firestoreCatalog: FirestoreModelCatalogDataSource,
    private val huggingFaceCatalog: HuggingFaceModelCatalogDataSource,
    private val kaggleCatalog: KaggleModelCatalogDataSource
) : ModelCatalogDataSource {

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> = coroutineScope {
        val deferreds = listOf(
            async { firestoreCatalog.fetchAvailableModels() },
            async { huggingFaceCatalog.fetchAvailableModels() },
            async { kaggleCatalog.fetchAvailableModels() }
        )

        val results = deferreds.awaitAll()
        val allModels = mutableListOf<ModelEntry>()
        var overallFailure: Throwable? = null

        results.forEach { result ->
            result.onSuccess { models ->
                allModels.addAll(models)
            }.onFailure {
                overallFailure = it
                // We continue to show models from other sources even if one fails
            }
        }

        if (allModels.isEmpty() && overallFailure != null) {
            Result.failure(overallFailure)
        } else {
            // Deduplicate by URL to be safe
            val distinctModels = allModels.distinctBy { it.url }
            Log.d("CompositeCatalog", "merged catalog count: ${distinctModels.size}")
            Result.success(distinctModels)
        }
    }
}
