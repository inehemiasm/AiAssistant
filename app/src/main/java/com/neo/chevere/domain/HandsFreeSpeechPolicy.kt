package com.neo.chevere.domain

/**
 * Pure rules for the hands-free conversation loop: recognising spoken exit commands and turning
 * Markdown-formatted assistant replies into text that sounds natural through text-to-speech.
 */
object HandsFreeSpeechPolicy {

    /** Consecutive silent listening turns tolerated before hands-free mode ends on its own. */
    const val MAX_SILENT_TURNS = 3

    private val stopPhrases = setOf(
        // English
        "stop", "stop listening", "stop talking", "exit", "quit", "goodbye", "good bye", "bye",
        "end", "end conversation", "end chat", "thats all", "that s all", "that's all", "that is all", "cancel",
        // Spanish
        "para", "detente", "parar", "adiós", "adios", "salir", "terminar",
        // French
        "arrête", "arrete", "stop merci", "au revoir", "quitter", "terminer",
        // German
        "stopp", "halt", "tschüss", "tschuss", "beenden", "ende",
    )

    private val leadingFillers = listOf("ok ", "okay ", "please ", "chevere ", "hey chevere ")
    private val trailingFillers = listOf(" please", " now", " thanks", " thank you")

    /**
     * Returns true when the whole utterance is an exit command such as "stop" or "okay, goodbye".
     * Commands embedded in a longer request ("how do I stop a bus") are not treated as exits.
     */
    fun isStopCommand(utterance: String): Boolean {
        var normalized = utterance.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("[\\p{Punct}¡¿]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return false
        var changed = true
        while (changed) {
            changed = false
            leadingFillers.firstOrNull { normalized.startsWith(it) }?.let {
                normalized = normalized.removePrefix(it).trim(); changed = true
            }
            trailingFillers.firstOrNull { normalized.endsWith(it) }?.let {
                normalized = normalized.removeSuffix(it).trim(); changed = true
            }
        }
        return normalized in stopPhrases
    }

    /**
     * Converts Markdown into plain prose for speech: drops fenced code, link targets, emphasis,
     * heading/list markers, tables, and URLs, and collapses whitespace.
     */
    fun toSpeakableText(markdown: String): String {
        var text = markdown
        text = text.replace(Regex("```[\\s\\S]*?```"), " Code omitted. ")
        text = text.replace(Regex("`([^`]*)`"), "$1")
        text = text.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("https?://\\S+"), "")
        text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*>\\s?"), "")
        text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$"), "")
        text = text.replace("|", ", ")
        text = text.replace(Regex("(\\*\\*|__|\\*|_|~~)(?=\\S)(.+?)(?<=\\S)\\1"), "$2")
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\s*\\n\\s*\\n\\s*"), ". ")
        text = text.replace(Regex("\\s*\\n\\s*"), " ")
        text = text.replace(Regex("\\.(\\s*\\.)+"), ".")
        return text.trim().trimStart('.', ' ')
    }
}
