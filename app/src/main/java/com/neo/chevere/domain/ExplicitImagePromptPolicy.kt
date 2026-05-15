package com.neo.chevere.domain

import com.neo.chevere.BuildConfig
import java.util.Locale

/**
 * Deterministic preflight policy for image prompts that ask for explicit nudity
 * or sexualized content.
 *
 * This runs before the LLM/tool loop so image-generation behavior is owned by
 * the app instead of by whichever chat model is currently selected. Age
 * verification is used to gate generation and to mark generated images for
 * masked display by default.
 */
class ExplicitImagePromptPolicy {
    /**
     * Evaluates [prompt] and returns the app-owned handling decision.
     */
    fun evaluate(prompt: String): ExplicitImagePromptDecision {
        return if (requiresAgeVerification(prompt)) {
            if (BuildConfig.DEBUG) {
                ExplicitImagePromptDecision.Allow
            } else {
                ExplicitImagePromptDecision.Block("Explicit content generation is restricted in this version.")
            }
        } else {
            ExplicitImagePromptDecision.Allow
        }
    }

    /**
     * Returns true when [prompt] should show an age-verification dialog before
     * the app decides how to handle the image request.
     */
    fun requiresAgeVerification(prompt: String): Boolean {
        val normalized = prompt.lowercase(Locale.ROOT)
        if (!normalized.looksLikeImageRequest()) return false
        return explicitNudityTerms.any { normalized.contains(it) } ||
                sexualTerms.any { normalized.contains(it) }
    }

    private fun String.looksLikeImageRequest(): Boolean {
        val hasGenerationVerb = imageGenerationTerms.any { contains(it) }
        val hasImageNoun = imageNouns.any { contains(it) }
        return hasGenerationVerb && hasImageNoun
    }

    private companion object {
        val imageGenerationTerms = listOf(
            "create",
            "generate",
            "make",
            "draw",
            "render",
            "paint"
        )
        val imageNouns = listOf(
            "image",
            "picture",
            "photo",
            "portrait",
            "art",
            "scene"
        )
        val explicitNudityTerms = listOf(
            "naked",
            "nude",
            "nudity",
            "topless",
            "bare breast",
            "bare breasts",
            "genitals",
            "explicit nude"
        )
        val sexualTerms = listOf(
            "sex",
            "sexual",
            "erotic",
            "porn",
            "pornographic",
            "nsfw",
            "fetish",
            "orgasm"
        )
    }
}

/**
 * Result of explicit image prompt preflight.
 */
sealed interface ExplicitImagePromptDecision {
    /** The prompt can continue through normal chat or image generation flow. */
    data object Allow : ExplicitImagePromptDecision

    /**
     * The prompt should be stopped and [message] should be shown to the user.
     */
    data class Block(val message: String) : ExplicitImagePromptDecision
}
