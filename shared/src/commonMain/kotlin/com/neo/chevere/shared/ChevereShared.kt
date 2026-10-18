package com.neo.chevere.shared

/**
 * Small initial API used to verify that the shared module is available to both
 * Android and iOS. Product behavior will move here incrementally.
 */
object ChevereShared {
    const val moduleName: String = "ChevereShared"

    fun platformDescription(): String = "$moduleName on ${platformName()}"
}

internal expect fun platformName(): String
