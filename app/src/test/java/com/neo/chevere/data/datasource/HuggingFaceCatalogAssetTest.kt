package com.neo.chevere.data.datasource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HuggingFaceCatalogAssetTest {

    @Test
    fun curatedImageModelsUseHuggingFaceRepositoryFiles() {
        val catalog = File("src/main/assets/model_catalog/huggingface_models.json").readText()

        assertFalse(catalog.contains("github.com/ShiftHackZ/Local-Diffusion-Models-SDAI-ONXX"))
        assertTrue(catalog.contains("https://huggingface.co/TensorStack/Dreamshaper-amuse"))
        assertTrue(catalog.contains("repositoryFiles"))
        assertTrue(catalog.contains("unet/model.onnx.data|unet/model.onnx.data"))
        assertTrue(catalog.contains("vae_decoder/model.onnx|vae_decoder/model.onnx"))
    }
}
