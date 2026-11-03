package com.neo.chevere.data.inference.minicpm

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.neo.chevere.R
import com.neo.chevere.data.inference.ModelEngine
import com.neo.chevere.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

/** Experimental CPU/LES engine. Each request starts with fresh bounded context. */
@Singleton
class MiniCpmEngine @Inject constructor(@param:ApplicationContext private val context: Context) : ModelEngine {
    private val lock = Mutex()
    @Volatile private var handle = 0L
    private val status = MutableStateFlow<InitializationStatus>(InitializationStatus.Uninitialized)
    override val initStatus: Flow<InitializationStatus> = status
    override var lastUsage: InferenceUsage? = null
        private set
    override suspend fun load(model: InstalledModel): LoadResult = lock.withLock {
        withContext(Dispatchers.IO) {
            status.value = InitializationStatus.Initializing(context.getString(R.string.model_status_loading, model.displayName))
            try {
                closeLocked()
                require(model.id == MiniCpmBundle.ID && android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty())
                val manifest = MiniCpmBundle.manifest(context)
                require(MiniCpmBundle.isComplete(File(model.filePath), manifest))
                val memory = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
                require(!memory.lowMemory && memory.availMem >= 3L * 1024 * 1024 * 1024)
                handle = MiniCpmNative.open(model.filePath.toByteArray(Charsets.UTF_8))
                check(handle != 0L)
                status.value = InitializationStatus.Ready
                LoadResult.Success
            } catch (e: CancellationException) { closeLocked(); throw e }
            catch (e: Exception) { loadFailure() }
            catch (e: LinkageError) { loadFailure() }
        }
    }
    private fun loadFailure(): LoadResult.Failure {
        closeLocked()
        val message = context.getString(R.string.ui_minicpm_load_failed)
        status.value = InitializationStatus.Failure(message)
        return LoadResult.Failure(message)
    }
    private fun closeLocked() { if (handle != 0L) { MiniCpmNative.close(handle); handle = 0 }; lastUsage = null }
    override suspend fun unload() = lock.withLock { withContext(Dispatchers.IO) { closeLocked(); status.value = InitializationStatus.Uninitialized } }
    override suspend fun clearConversation() = lock.withLock { lastUsage = null } // begin() clears native memory.
    override fun isVisionSupported() = handle != 0L
    override suspend fun generate(request: InferenceRequest): InferenceResult = generateStream(request).last()
    override fun generateStream(request: InferenceRequest): Flow<InferenceResult> = channelFlow {
        lock.withLock {
            lastUsage = null
            if (handle == 0L) { send(InferenceResult.Failure(context.getString(R.string.ui_minicpm_load_failed))); return@withLock }
            // Cancellation only changes an atomic native flag. Teardown waits for the worker.
            MiniCpmNative.arm(handle)
            val worker = launch(Dispatchers.IO) {
                try {
                    val image = request.imageUri?.let { uri ->
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val scale = minOf(1.0, 1344.0 / maxOf(info.size.width, info.size.height))
                            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                        }
                        try { ByteArrayOutputStream().use { bytes -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes); bytes.toByteArray() } }
                        finally { bitmap.recycle() }
                    }
                    ensureActive()
                    val input = MiniCpmNative.begin(handle, request.prompt.toByteArray(Charsets.UTF_8), image)
                    if (input == -2) { send(InferenceResult.Failure(context.getString(R.string.context_request_too_large))); return@launch }
                    check(input > 0)
                    var tokens = 0
                    val bytes = ByteArrayOutputStream()
                    var visible = ""
                    while (true) {
                        ensureActive()
                        val piece = MiniCpmNative.next(handle) ?: break
                        tokens++
                        bytes.write(piece)
                        // A token may end inside a UTF-8 character. Hold it until complete.
                        val decoded = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString() } catch (_: java.nio.charset.CharacterCodingException) { continue }
                        val next = miniCpmVisibleText(decoded)
                        if (next != visible) { visible = next; send(InferenceResult.Success(visible)) }
                    }
                    ensureActive()
                    lastUsage = InferenceUsage(input, tokens)
                    send(InferenceResult.Success(visible))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { send(InferenceResult.Failure(context.getString(R.string.ui_minicpm_generation_failed))) }
            }
            try { worker.join() }
            finally {
                MiniCpmNative.cancel(handle)
                withContext(NonCancellable) { worker.join() }
            }
        }
    }
}

/** Never expose a thought channel, including an unfinished opening/closing marker. */
internal fun miniCpmVisibleText(raw: String): String {
    var text = raw.replace(Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\[Start thinking\].*?(\[End thinking\]|$)""", RegexOption.DOT_MATCHES_ALL), "")
    for (marker in listOf("<think>", "[Start thinking]", "<|im_end|>")) {
        for (length in marker.length downTo 1) if (text.endsWith(marker.take(length))) {
            text = text.dropLast(length); break
        }
    }
    return text.trim()
}
