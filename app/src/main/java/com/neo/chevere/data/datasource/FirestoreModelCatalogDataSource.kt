package com.neo.chevere.data.datasource

import com.google.firebase.firestore.FirebaseFirestore
import com.neo.chevere.domain.ModelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ModelCatalogDataSource using Firebase Firestore.
 */
@Singleton
class FirestoreModelCatalogDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : ModelCatalogDataSource {
    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = firestore.collection("models").get().await()
                snapshot.toObjects(ModelEntry::class.java)
            }
        }
}
