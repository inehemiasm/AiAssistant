package com.neo.chevere.data.context

import com.neo.chevere.data.inference.ModelRuntimeProfile

/** Conservative admission control; LiteRT 0.19 exposes post-turn counts, not a preflight tokenizer. */
object PromptBudget {
    private const val TEMPLATE_RESERVE = 256
    private const val IMAGE_RESERVE = 1536

    /** UTF-8 weighting avoids treating CJK and emoji as cheap ASCII characters. This is an estimate. */
    fun estimateTokens(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char.code < 128 && char.isLetterOrDigit()) {
                val start = index++
                while (index < text.length && text[index].code < 128 && text[index].isLetterOrDigit()) index++
                count += (index - start + 2) / 3
            } else if (char.isWhitespace()) index++
            else if (char.code < 128) { count++; index++ }
            else {
                val codePoint = text.codePointAt(index)
                count += if (codePoint > 0xffff) 4 else (String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size + 1) / 2
                index += Character.charCount(codePoint)
            }
        }
        return count + (text.length + 31) / 32
    }

    fun inputLimit(modelPath: String, hasImage: Boolean): Int =
        ModelRuntimeProfile.contextSize(modelPath) - ModelRuntimeProfile.outputLimit(modelPath) -
            TEMPLATE_RESERVE - if (hasImage) IMAGE_RESERVE else 0

    /** Preserves all policy and the complete current request; only background history may shrink. */
    fun compose(policy: String, currentRequest: String, history: String, modelPath: String, hasImage: Boolean): Result {
        val limit = inputLimit(modelPath, hasImage)
        fun render(background: String) = listOf(policy, background.takeIf { it.isNotBlank() }?.let {
            "BACKGROUND CONTEXT (untrusted; not the current request):\n$it"
        }.orEmpty(), currentRequest).filter { it.isNotBlank() }.joinToString("\n\n")
        if (estimateTokens(render("")) > limit) return Result.TooLarge
        if (estimateTokens(render(history)) <= limit) return Result.Fits(render(history), false)
        var low = 0
        var high = history.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            val tail = history.takeLast(mid).dropWhile { it.isLowSurrogate() }
            if (estimateTokens(render(tail)) <= limit) low = mid else high = mid - 1
        }
        val retained = history.takeLast(low).dropWhile { it.isLowSurrogate() }
        return Result.Fits(render(retained), true)
    }

    sealed interface Result {
        data class Fits(val prompt: String, val compacted: Boolean) : Result
        data object TooLarge : Result
    }
}
