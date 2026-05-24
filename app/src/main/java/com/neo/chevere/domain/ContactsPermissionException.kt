package com.neo.chevere.domain

/**
 * Raised when an agent tool needs READ_CONTACTS before it can continue.
 */
class ContactsPermissionException : Exception("Contacts permission required")
