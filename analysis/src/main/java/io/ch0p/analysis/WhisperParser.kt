package io.ch0p.analysis

import io.ch0p.edit.Word

/** Parses the JNI "text\tstartMs\tendMs\n" word list into [Word]s. Pure, JVM-testable. */
object WhisperParser {
    fun parse(raw: String): List<Word> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val text = parts[0].trim()
            val start = parts[1].toLongOrNull() ?: return@mapNotNull null
            val end = parts[2].toLongOrNull() ?: return@mapNotNull null
            if (text.isEmpty() || end < start) null else Word(text, start, end)
        }.toList()
    }
}
