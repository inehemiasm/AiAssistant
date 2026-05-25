package com.neo.chevere.domain

/**
 * Exception thrown when the agent needs READ_CALENDAR or WRITE_CALENDAR permissions.
 */
class CalendarPermissionException : Exception("Calendar permission required")
