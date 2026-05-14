package com.neo.chevere.data.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.neo.chevere.core.Constants
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer
import java.util.Locale
import java.util.Random
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val TEXT_ENCODER_MODEL = Constants.ImageGeneration.ONNX_REQUIRED_FILES[0]
private val TOKENIZER_VOCAB = Constants.ImageGeneration.ONNX_REQUIRED_FILES[1]
private val TOKENIZER_MERGES = Constants.ImageGeneration.ONNX_REQUIRED_FILES[2]
private val UNET_MODEL = Constants.ImageGeneration.ONNX_REQUIRED_FILES[3]
private val VAE_DECODER_MODEL = Constants.ImageGeneration.ONNX_REQUIRED_FILES[4]
private const val MAX_TOKENS = 77
private const val START_TOKEN = 49406
private const val END_TOKEN = 49407
private const val LATENT_SCALE = 0.18215f

/**
 * Local Stable Diffusion backend for Android ONNX Runtime bundles.
 *
 * The expected bundle shape matches the public SDAI ONNX model layout:
 * `text_encoder/model.ort`, `tokenizer/vocab.json`, `tokenizer/merges.txt`,
 * `unet/model.ort`, and `vae_decoder/model.ort`.
 */
@Singleton
class OnnxLocalDiffusionEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ImageGenerationEngine {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var modelDirectory: File? = null
    private var textEncoder: OrtSession? = null
    private var unet: OrtSession? = null
    private var vaeDecoder: OrtSession? = null
    private var tokenizer: ClipTokenizer? = null

    override suspend fun load(model: InstalledModel): LoadResult = withContext(Dispatchers.IO) {
        val directory = File(model.filePath)
        if (!directory.isDirectory) {
            return@withContext LoadResult.Failure("ONNX diffusion model must be an extracted model directory.")
        }

        val missingFiles = Constants.ImageGeneration.ONNX_REQUIRED_FILES.filterNot { relativePath -> File(directory, relativePath).isFile }
        if (missingFiles.isNotEmpty()) {
            return@withContext LoadResult.Failure(
                "ONNX diffusion model is missing required files: ${missingFiles.joinToString(", ")}"
            )
        }

        unload()

        try {
            val sessionOptions = OrtSession.SessionOptions().apply {
                addConfigEntry("session.load_model_format", "ORT")
            }
            textEncoder = environment.createSession(File(directory, TEXT_ENCODER_MODEL).absolutePath, sessionOptions)
            unet = environment.createSession(File(directory, UNET_MODEL).absolutePath, sessionOptions)
            vaeDecoder = environment.createSession(File(directory, VAE_DECODER_MODEL).absolutePath, sessionOptions)
            tokenizer = ClipTokenizer(
                vocabFile = File(directory, TOKENIZER_VOCAB),
                mergesFile = File(directory, TOKENIZER_MERGES)
            )
            modelDirectory = directory
            LoadResult.Success
        } catch (throwable: Throwable) {
            unload()
            LoadResult.Failure("Failed to load ONNX diffusion model: ${throwable.message}", throwable)
        }
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult = withContext(Dispatchers.Default) {
        val activeTokenizer = tokenizer
            ?: return@withContext ImageGenerationResult.Failure("ONNX diffusion tokenizer is not loaded.")
        val activeTextEncoder = textEncoder
            ?: return@withContext ImageGenerationResult.Failure("ONNX diffusion text encoder is not loaded.")
        val activeUnet = unet
            ?: return@withContext ImageGenerationResult.Failure("ONNX diffusion UNet is not loaded.")
        val activeVaeDecoder = vaeDecoder
            ?: return@withContext ImageGenerationResult.Failure("ONNX diffusion VAE decoder is not loaded.")

        try {
            val width = request.width.coerceIn(256, 512).roundDownToMultipleOf(8)
            val height = request.height.coerceIn(256, 512).roundDownToMultipleOf(8)
            val steps = request.steps.coerceIn(1, 40)
            val seed = request.seed ?: System.currentTimeMillis()
            val guidanceScale = request.guidanceScale.coerceIn(1f, 15f)

            val conditionalEmbeddings = encodePrompt(
                tokenizer = activeTokenizer,
                textEncoder = activeTextEncoder,
                prompt = request.prompt
            )
            val unconditionalEmbeddings = encodePrompt(
                tokenizer = activeTokenizer,
                textEncoder = activeTextEncoder,
                prompt = request.negativePrompt.orEmpty()
            )
            val textEmbeddings = stackEmbeddings(unconditionalEmbeddings, conditionalEmbeddings)

            val scheduler = EulerAncestralScheduler(steps)
            var latents = createLatents(seed, height / 8, width / 8, scheduler.initNoiseSigma)

            for (stepIndex in 0 until steps) {
                coroutineContext.ensureActive()
                val sigma = scheduler.sigma(stepIndex)
                val scaledLatents = scaleLatents(duplicateBatch(latents), sigma)
                val timestep = scheduler.timestep(stepIndex)
                val noisePrediction = runUnet(
                    session = activeUnet,
                    sample = scaledLatents,
                    timestep = timestep,
                    textEmbeddings = textEmbeddings
                )
                val guidedNoise = applyClassifierFreeGuidance(noisePrediction, guidanceScale)
                latents = scheduler.step(guidedNoise, latents, stepIndex, seed + stepIndex + 1L)
            }

            val decoded = runVaeDecoder(activeVaeDecoder, multiplyLatents(latents, 1f / LATENT_SCALE))
            val bitmap = decoded.toBitmap(width, height)
            val imageUri = saveBitmap(bitmap)
            ImageGenerationResult.Success(
                imageUri = imageUri,
                prompt = request.prompt,
                width = width,
                height = height,
                seed = seed
            )
        } catch (throwable: Throwable) {
            ImageGenerationResult.Failure("ONNX diffusion generation failed: ${throwable.message}", throwable)
        }
    }

    override suspend fun unload() {
        textEncoder?.close()
        unet?.close()
        vaeDecoder?.close()
        textEncoder = null
        unet = null
        vaeDecoder = null
        tokenizer = null
        modelDirectory = null
    }

    private fun encodePrompt(
        tokenizer: ClipTokenizer,
        textEncoder: OrtSession,
        prompt: String
    ): Array<Array<FloatArray>> {
        val tokenIds = tokenizer.encode(prompt)
        val inputIds = OnnxTensor.createTensor(environment, IntBuffer.wrap(tokenIds), longArrayOf(1, MAX_TOKENS.toLong()))
        inputIds.use { tensor ->
            textEncoder.run(mapOf("input_ids" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return deepCopy3d(result[0].value as Array<Array<FloatArray>>)
            }
        }
    }

    private fun runUnet(
        session: OrtSession,
        sample: Array<Array<Array<FloatArray>>>,
        timestep: Int,
        textEmbeddings: Array<Array<FloatArray>>
    ): Array<Array<Array<FloatArray>>> {
        val sampleTensor = OnnxTensor.createTensor(environment, sample)
        val timestepTensor = OnnxTensor.createTensor(environment, IntBuffer.wrap(intArrayOf(timestep)), longArrayOf(1))
        val embeddingsTensor = OnnxTensor.createTensor(environment, textEmbeddings)
        sampleTensor.use { sampleInput ->
            timestepTensor.use { timestepInput ->
                embeddingsTensor.use { embeddingsInput ->
                    val inputs = mapOf(
                        "sample" to sampleInput,
                        "timestep" to timestepInput,
                        "encoder_hidden_states" to embeddingsInput
                    )
                    session.run(inputs).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        return deepCopy4d(result[0].value as Array<Array<Array<FloatArray>>>)
                    }
                }
            }
        }
    }

    private fun runVaeDecoder(
        session: OrtSession,
        latents: Array<Array<Array<FloatArray>>>
    ): Array<Array<Array<FloatArray>>> {
        val latentTensor = OnnxTensor.createTensor(environment, latents)
        latentTensor.use { tensor ->
            session.run(mapOf("latent_sample" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return deepCopy4d(result[0].value as Array<Array<Array<FloatArray>>>)
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap): android.net.Uri {
        val outputDirectory = File(context.filesDir, Constants.ImageGeneration.GENERATED_IMAGES_DIRECTORY).apply { mkdirs() }
        val outputFile = File(
            outputDirectory,
            "${Constants.ImageGeneration.GENERATED_IMAGE_PREFIX}${System.currentTimeMillis()}${Constants.ImageGeneration.PNG_EXTENSION}"
        )
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
    }
}

private class ClipTokenizer(
    vocabFile: File,
    mergesFile: File
) {
    private val tokenPattern = Pattern.compile("""'s|'t|'re|'ve|'m|'ll|'d|[A-Za-z]+|[0-9]+|[^A-Za-z0-9\s]+""")
    private val encoder: Map<String, Int>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    private val cache = mutableMapOf<String, String>()

    init {
        val vocabJson = Json.parseToJsonElement(vocabFile.readText()).jsonObject
        encoder = vocabJson.mapValues { it.value.jsonPrimitive.content.toInt() }
        bpeRanks = mergesFile.readLines()
            .asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .withIndex()
            .associate { it.value to it.index }
    }

    fun encode(text: String): IntArray {
        val tokens = mutableListOf(START_TOKEN)
        val matcher = tokenPattern.matcher(text.lowercase(Locale.ROOT))
        while (matcher.find()) {
            val token = matcher.group()
            val encodedBytes = token.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
                byteEncoder[byte.toInt() and 0xff].toString()
            }
            val bpeTokens = bpe(encodedBytes).split(" ")
            bpeTokens.forEach { bpeToken ->
                encoder[bpeToken]?.let(tokens::add)
            }
        }
        tokens.add(END_TOKEN)

        val result = IntArray(MAX_TOKENS) { END_TOKEN }
        tokens.take(MAX_TOKENS).forEachIndexed { index, tokenId ->
            result[index] = tokenId
        }
        result[MAX_TOKENS - 1] = END_TOKEN
        return result
    }

    private fun bpe(token: String): String {
        cache[token]?.let { return it }
        if (token.isEmpty()) return token

        var word = token.mapIndexed { index, char ->
            if (index == token.lastIndex) "$char</w>" else char.toString()
        }
        if (word.size == 1) return word.first()

        while (true) {
            val pairs = word.zipWithNext()
            val best = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE }
            if (best == null || !bpeRanks.containsKey(best)) break

            val merged = mutableListOf<String>()
            var index = 0
            while (index < word.size) {
                if (index < word.lastIndex && word[index] == best.first && word[index + 1] == best.second) {
                    merged.add(best.first + best.second)
                    index += 2
                } else {
                    merged.add(word[index])
                    index += 1
                }
            }
            word = merged
            if (word.size == 1) break
        }

        return word.joinToString(" ").also { cache[token] = it }
    }

    private fun bytesToUnicode(): Map<Int, Char> {
        val bytes = mutableListOf<Int>()
        bytes += ('!'.code..'~'.code)
        bytes += (161..172)
        bytes += (174..255)

        val chars = bytes.toMutableList()
        var next = 0
        for (byte in 0..255) {
            if (byte !in bytes) {
                bytes.add(byte)
                chars.add(256 + next)
                next += 1
            }
        }
        return bytes.zip(chars).associate { it.first to it.second.toChar() }
    }
}

private class EulerAncestralScheduler(private val steps: Int) {
    private val trainTimesteps = 1000
    private val timesteps: List<Float>
    private val sigmas: List<Float>
    val initNoiseSigma: Float

    init {
        val betas = linspace(sqrt(0.00085f), sqrt(0.012f), trainTimesteps).map { it * it }
        val alphas = betas.map { 1f - it }
        val alphasCumprod = mutableListOf<Float>()
        var product = 1f
        alphas.forEach { alpha ->
            product *= alpha
            alphasCumprod.add(product)
        }
        val trainingSigmas = alphasCumprod
            .map { sqrt((1f - it) / it) }
        initNoiseSigma = trainingSigmas.maxOrNull() ?: 1f
        timesteps = linspace((trainTimesteps - 1).toFloat(), 0f, steps)
        sigmas = timesteps.map { timestep -> interpolateSigma(timestep, trainingSigmas) } + 0f
    }

    fun timestep(index: Int): Int = timesteps[index].roundToInt()

    fun sigma(index: Int): Float = sigmas[index]

    fun step(
        modelOutput: Array<Array<Array<FloatArray>>>,
        sample: Array<Array<Array<FloatArray>>>,
        stepIndex: Int,
        seed: Long
    ): Array<Array<Array<FloatArray>>> {
        val sigma = sigmas[stepIndex]
        val nextSigma = sigmas[stepIndex + 1]
        val sigmaUp = if (sigma == 0f) {
            0f
        } else {
            sqrt(max(0f, nextSigma * nextSigma * (sigma * sigma - nextSigma * nextSigma) / (sigma * sigma)))
        }
        val sigmaDown = sqrt(max(0f, nextSigma * nextSigma - sigmaUp * sigmaUp))
        val noise = createLatents(seed, sample[0][0].size, sample[0][0][0].size, 1f)

        val output = createEmptyLatents(sample[0][0].size, sample[0][0][0].size)
        forEachLatent(sample) { channel, y, x ->
            val predictedOriginal = sample[0][channel][y][x] - sigma * modelOutput[0][channel][y][x]
            val derivative = if (sigma == 0f) 0f else (sample[0][channel][y][x] - predictedOriginal) / sigma
            val dt = sigmaDown - sigma
            output[0][channel][y][x] = sample[0][channel][y][x] + derivative * dt + noise[0][channel][y][x] * sigmaUp
        }
        return output
    }

    private fun interpolateSigma(timestep: Float, trainingSigmas: List<Float>): Float {
        val lowIndex = timestep.toInt().coerceIn(0, trainTimesteps - 1)
        val highIndex = min(lowIndex + 1, trainTimesteps - 1)
        val weight = timestep - lowIndex
        return trainingSigmas[lowIndex] * (1f - weight) + trainingSigmas[highIndex] * weight
    }
}

private fun createLatents(seed: Long, latentHeight: Int, latentWidth: Int, scale: Float): Array<Array<Array<FloatArray>>> {
    val random = Random(seed)
    val latents = createEmptyLatents(latentHeight, latentWidth)
    forEachLatent(latents) { channel, y, x ->
        val u1 = random.nextDouble().coerceAtLeast(1e-7)
        val u2 = random.nextDouble()
        val value = sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        latents[0][channel][y][x] = (value * scale).toFloat()
    }
    return latents
}

private fun createEmptyLatents(latentHeight: Int, latentWidth: Int): Array<Array<Array<FloatArray>>> {
    return Array(1) { Array(4) { Array(latentHeight) { FloatArray(latentWidth) } } }
}

private fun duplicateBatch(latents: Array<Array<Array<FloatArray>>>): Array<Array<Array<FloatArray>>> {
    return Array(2) { batch ->
        Array(4) { channel ->
            Array(latents[0][channel].size) { y ->
                FloatArray(latents[0][channel][y].size) { x -> latents[0][channel][y][x] }
            }
        }
    }
}

private fun scaleLatents(latents: Array<Array<Array<FloatArray>>>, sigma: Float): Array<Array<Array<FloatArray>>> {
    val scale = sqrt(sigma * sigma + 1f)
    for (batch in latents.indices) {
        forEachLatent(latents, batch) { channel, y, x ->
            latents[batch][channel][y][x] /= scale
        }
    }
    return latents
}

private fun applyClassifierFreeGuidance(
    noisePrediction: Array<Array<Array<FloatArray>>>,
    guidanceScale: Float
): Array<Array<Array<FloatArray>>> {
    val output = createEmptyLatents(noisePrediction[0][0].size, noisePrediction[0][0][0].size)
    forEachLatent(output) { channel, y, x ->
        val unconditional = noisePrediction[0][channel][y][x]
        val conditional = noisePrediction[1][channel][y][x]
        output[0][channel][y][x] = unconditional + guidanceScale * (conditional - unconditional)
    }
    return output
}

private fun multiplyLatents(
    latents: Array<Array<Array<FloatArray>>>,
    scale: Float
): Array<Array<Array<FloatArray>>> {
    val output = createEmptyLatents(latents[0][0].size, latents[0][0][0].size)
    forEachLatent(output) { channel, y, x ->
        output[0][channel][y][x] = latents[0][channel][y][x] * scale
    }
    return output
}

private fun stackEmbeddings(
    unconditional: Array<Array<FloatArray>>,
    conditional: Array<Array<FloatArray>>
): Array<Array<FloatArray>> {
    return Array(2) { batch ->
        Array(MAX_TOKENS) { token ->
            FloatArray(768) { hidden ->
                if (batch == 0) unconditional[0][token][hidden] else conditional[0][token][hidden]
            }
        }
    }
}

private fun Array<Array<Array<FloatArray>>>.toBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val red = (((this[0][0][y][x] / 2f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            val green = (((this[0][1][y][x] / 2f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            val blue = (((this[0][2][y][x] / 2f) + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()
            bitmap.setPixel(x, y, Color.rgb(red, green, blue))
        }
    }
    return bitmap
}

private fun forEachLatent(
    latents: Array<Array<Array<FloatArray>>>,
    batch: Int = 0,
    block: (channel: Int, y: Int, x: Int) -> Unit
) {
    for (channel in latents[batch].indices) {
        for (y in latents[batch][channel].indices) {
            for (x in latents[batch][channel][y].indices) {
                block(channel, y, x)
            }
        }
    }
}

private fun linspace(start: Float, end: Float, count: Int): List<Float> {
    if (count == 1) return listOf(start)
    val step = (end - start) / (count - 1)
    return List(count) { index -> start + step * index }
}

private fun Int.roundDownToMultipleOf(multiple: Int): Int = this - (this % multiple)

private fun deepCopy3d(input: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
    return Array(input.size) { i ->
        Array(input[i].size) { j -> input[i][j].copyOf() }
    }
}

private fun deepCopy4d(input: Array<Array<Array<FloatArray>>>): Array<Array<Array<FloatArray>>> {
    return Array(input.size) { i ->
        Array(input[i].size) { j ->
            Array(input[i][j].size) { k -> input[i][j][k].copyOf() }
        }
    }
}
