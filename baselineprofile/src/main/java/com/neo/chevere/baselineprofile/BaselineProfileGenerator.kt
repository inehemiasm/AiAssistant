package com.neo.chevere.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline Profiles for ChevereAI.
 *
 * Captures startup and common user navigation flows to pre-compile critical path code
 * ahead of time (AOT) for faster app launches and smoother UI rendering.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "dev.neo.chevereai",
        includeInStartupProfile = true
    ) {
        // Press home to start from a clean slate
        pressHome()

        // Launch the application MainActivity
        startActivityAndWait()

        // Wait for main UI elements to render
        device.waitForIdle()
    }
}
