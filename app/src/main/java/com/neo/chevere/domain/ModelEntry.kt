package com.neo.chevere.domain

import com.neo.chevere.core.Constants

/**
 * Metadata for a remote AI model available in the catalog.
 *
 * @property name A user-friendly display name for the model.
 * @property url The direct download URL for the model file.
 * @property description A brief summary of the model's purpose or origin.
 * @property provider The organization or source providing the model (e.g., "Hugging Face").
 * @property sizeBytes The size of the model file in bytes.
 * @property runtimeType The required runtime for the model (e.g., "LiteRT").
 * @property sha256 Optional SHA-256 hash for file verification.
 * @property fileName The name of the file on disk.
 * @property supportsVision Whether the model supports vision tasks.
 * @property license Optional license information (e.g., "Apache 2.0").
 * @property repositoryFiles Optional direct file URLs for directory-style model bundles.
 * Each item may be either a direct URL or `url|relative/path/in/model`.
 */
data class ModelEntry(
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val provider: String = "Firebase",
    val sizeBytes: Long = 0,
    val runtimeType: String = "LiteRT",
    val sha256: String? = null,
    val fileName: String? = null,
    val supportsVision: Boolean = false,
    val license: String? = null,
    val repositoryFiles: List<String> = emptyList()
) {
    /**
     * The effective file name to use for this model, derived from [fileName] or [name].
     */
    val effectiveFileName: String get() = fileName ?: (name.replace(" ", "_")
        .lowercase() + if (runtimeType == "LiteRT") Constants.ModelFiles.LITERTLM_EXTENSION else Constants.ModelFiles.BIN_EXTENSION)

    /**
     * The canonical installed model id. ZIP bundles install as extracted directories.
     */
    val effectiveInstalledId: String get() = effectiveFileName.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION)
}
