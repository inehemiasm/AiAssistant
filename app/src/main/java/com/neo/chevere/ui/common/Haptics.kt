package com.neo.chevere.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Small app-level vocabulary for tactile feedback.
 *
 * Android will respect the user's system haptic settings. Newer devices get
 * semantic confirm/reject pulses, while older devices fall back to stable
 * platform constants.
 */
enum class ChevereHaptic {
    Selection,
    Action,
    Success,
    Warning
}

fun View.performChevereHaptic(type: ChevereHaptic) {
    val feedbackConstant = when (type) {
        ChevereHaptic.Selection -> HapticFeedbackConstants.CLOCK_TICK
        ChevereHaptic.Action -> HapticFeedbackConstants.CONTEXT_CLICK
        ChevereHaptic.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }

        ChevereHaptic.Warning -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    }
    performHapticFeedback(feedbackConstant)
}

fun String.hapticForFeedbackMessage(): ChevereHaptic {
    val normalized = lowercase()
    return when {
        normalized.contains("fail") ||
                normalized.contains("error") ||
                normalized.contains("cannot") ||
                normalized.contains("cancel") -> ChevereHaptic.Warning

        normalized.contains("complete") ||
                normalized.contains("ready") ||
                normalized.contains("activated") ||
                normalized.contains("saved") ||
                normalized.contains("deleted") -> ChevereHaptic.Success

        else -> ChevereHaptic.Selection
    }
}
