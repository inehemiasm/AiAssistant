package com.neo.chevere.core

/**
 * Utility for converting numeric values to their English word representations.
 * This is used to prevent tokenization-induced digit duplication or dropping
 * issues in small on-device LLMs.
 */
object NumberUtils {
    private val UNITS = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )

    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    /**
     * Converts an integer to its English word representation.
     * E.g., 55 becomes "fifty-five".
     */
    fun toWords(number: Int): String {
        if (number == 0) return "zero"
        if (number < 0) {
            val positivePart = if (number == Int.MIN_VALUE) Int.MAX_VALUE else -number
            return "minus " + toWords(positivePart)
        }
        return convert(number).trim()
    }

    private fun convert(number: Int): String {
        return when {
            number < 20 -> UNITS[number]
            number < 100 -> {
                val ten = TENS[number / 10]
                val unit = UNITS[number % 10]
                if (unit.isEmpty()) ten else "$ten-$unit"
            }
            number < 1000 -> {
                val hundred = UNITS[number / 100] + " hundred"
                val remainder = number % 100
                if (remainder == 0) hundred else "$hundred " + convert(remainder)
            }
            number < 1000000 -> {
                val thousand = convert(number / 1000) + " thousand"
                val remainder = number % 1000
                if (remainder == 0) thousand else "$thousand " + convert(remainder)
            }
            else -> number.toString()
        }
    }
}
