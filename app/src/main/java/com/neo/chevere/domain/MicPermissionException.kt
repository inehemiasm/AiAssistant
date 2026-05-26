package com.neo.chevere.domain

/**
 * Raised when an agent tool needs RECORD_AUDIO permission before it can continue.
 * The UI layer translates this into a system permission request dialog.
 */
class MicPermissionException : Exception("Microphone permission required")
