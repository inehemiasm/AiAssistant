package com.neo.chevere.domain

/**
 * Exception thrown when location permissions are required but not granted.
 */
class LocationPermissionException : Exception("Location permission required")
