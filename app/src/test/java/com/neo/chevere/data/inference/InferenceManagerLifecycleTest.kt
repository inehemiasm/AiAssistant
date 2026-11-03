package com.neo.chevere.data.inference

import com.neo.chevere.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class InferenceManagerLifecycleTest {
    private fun model() = InstalledModel("mini", "Mini", "/mini", "mini", ModelSource.LOCAL,
        ModelFormat.GGUF_BUNDLE, ModelRuntime.LLAMA_CPP, ModelTaskType.VISION_CHAT,
        setOf(ModelCapability.TEXT, ModelCapability.VISION), InstallStatus.INSTALLED)
    @Test fun failedLoadUnloadsPartialNativeState() = runTest {
        val factory = mock<ModelEngineFactory>()
        val engine = mock<ModelEngine>()
        whenever(engine.initStatus).thenReturn(flowOf(InitializationStatus.Uninitialized))
        whenever(factory.getEngine(ModelRuntime.LLAMA_CPP)).thenReturn(engine)
        whenever(engine.load(any())).thenReturn(LoadResult.Failure("test"))
        val manager = InferenceManager(factory)
        assertTrue(manager.loadModel(model()) is LoadResult.Failure)
        verify(engine).unload()
        assertNull(manager.currentEngine)
        assertNull(manager.currentModel)
    }
    @Test fun cancellationUnloadsAndPropagates() = runTest {
        val entered = CompletableDeferred<Unit>()
        val engine = object : ModelEngine {
            var closed = false
            override val initStatus = flowOf(InitializationStatus.Uninitialized)
            override suspend fun load(model: InstalledModel): LoadResult { entered.complete(Unit); awaitCancellation() }
            override suspend fun generate(request: InferenceRequest) = InferenceResult.Success("")
            override fun generateStream(request: InferenceRequest) = emptyFlow<InferenceResult>()
            override suspend fun clearConversation() {}
            override fun isVisionSupported() = false
            override suspend fun unload() { closed = true }
        }
        val factory = mock<ModelEngineFactory>()
        whenever(factory.getEngine(ModelRuntime.LLAMA_CPP)).thenReturn(engine)
        val manager = InferenceManager(factory)
        val job = launch { manager.loadModel(model()) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(engine.closed)
        assertNull(manager.currentEngine)
    }
}
