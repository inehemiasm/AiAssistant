package com.neo.chevere.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeSpeechPolicyTest {

    @Test
    fun isStopCommandRecognizesStandardExitPhrases() {
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("stop"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("Stop"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("stop talking"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("exit"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("quit"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("goodbye"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("good bye!"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("bye"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("that's all"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("cancel"))
        // Multilingual
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("adiós"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("arrête"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("tschüss"))
    }

    @Test
    fun isStopCommandRecognizesFillers() {
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("okay, stop please"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("chevere stop"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("hey chevere, goodbye please"))
        assertTrue(HandsFreeSpeechPolicy.isStopCommand("ok, that's all thanks"))
    }

    @Test
    fun isStopCommandRejectsNormalQueriesContainingStopWords() {
        assertFalse(HandsFreeSpeechPolicy.isStopCommand("How do I stop a leaking pipe?"))
        assertFalse(HandsFreeSpeechPolicy.isStopCommand("Where is the nearest bus stop?"))
        assertFalse(HandsFreeSpeechPolicy.isStopCommand("Can you explain how airplanes stop on runways?"))
        assertFalse(HandsFreeSpeechPolicy.isStopCommand("Never quit on your dreams"))
        assertFalse(HandsFreeSpeechPolicy.isStopCommand(""))
        assertFalse(HandsFreeSpeechPolicy.isStopCommand("   "))
    }

    @Test
    fun toSpeakableTextStripsMarkdownForCleanSpeechPlayback() {
        val markdown = """
            # Weather Forecast
            Here is the **current** forecast:
            * Temperature: 22°C
            * Condition: Sunny

            Check [details](https://weather.example.com) or run:
            ```bash
            curl wttr.in
            ```
            Have a great day!
        """.trimIndent()

        val speakable = HandsFreeSpeechPolicy.toSpeakableText(markdown)

        assertFalse(speakable.contains("#"))
        assertFalse(speakable.contains("**"))
        assertFalse(speakable.contains("```"))
        assertFalse(speakable.contains("https://"))
        assertTrue(speakable.contains("Weather Forecast"))
        assertTrue(speakable.contains("current forecast"))
        assertTrue(speakable.contains("Temperature: 22°C"))
        assertTrue(speakable.contains("Code omitted."))
        assertTrue(speakable.contains("Have a great day!"))
    }
}
