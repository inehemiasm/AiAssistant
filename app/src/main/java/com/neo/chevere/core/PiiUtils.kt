package com.neo.chevere.core

import java.util.regex.Pattern

/**
 * Utility for identifying and redacting Personally Identifiable Information (PII).
 */
object PiiUtils {

    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )

    private val PHONE_PATTERN = Pattern.compile(
        "(\\+\\d{1,2}\\s?)?1?\\-?\\.?\\s?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}"
    )

    private val CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d{4}[ -]?){3}(?:\\d{4}|\\d{3})\\b"
    )
    
    private val SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    )

    /**
     * Replaces sensitive patterns with generic placeholders.
     */
    fun scrub(text: String?): String {
        if (text == null) return ""
        var scrubbed = text
        
        scrubbed = EMAIL_PATTERN.matcher(scrubbed).replaceAll("[EMAIL]")
        scrubbed = PHONE_PATTERN.matcher(scrubbed).replaceAll("[PHONE]")
        scrubbed = CREDIT_CARD_PATTERN.matcher(scrubbed).replaceAll("[CARD]")
        scrubbed = SSN_PATTERN.matcher(scrubbed).replaceAll("[SSN]")
        
        return scrubbed
    }

    /**
     * Checks if a string contains likely PII.
     */
    fun containsPii(text: String?): Boolean {
        if (text == null) return false
        return EMAIL_PATTERN.matcher(text).find() || 
               PHONE_PATTERN.matcher(text).find() || 
               CREDIT_CARD_PATTERN.matcher(text).find() ||
               SSN_PATTERN.matcher(text).find()
    }
}
