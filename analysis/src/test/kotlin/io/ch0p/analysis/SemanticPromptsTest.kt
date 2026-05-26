package io.ch0p.analysis

import io.ch0p.edit.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticPromptsTest {

    @Test fun `title prompt embeds transcript and asks for a title`() {
        val p = SemanticPrompts.titlePrompt("we went skiing today")
        assertTrue(p.contains("we went skiing today"))
        assertTrue(p.trimEnd().endsWith("Title:"))
    }

    @Test fun `parse title strips quotes prefixes and picks first line`() {
        assertEquals("Big Day Out", SemanticPrompts.parseTitle("\"Big Day Out\""))
        assertEquals("My Trip", SemanticPrompts.parseTitle("Title: My Trip\nextra"))
        assertEquals("Sendai", SemanticPrompts.parseTitle("\n\n  *Sendai*  "))
    }

    @Test fun `parse hook selections tolerates varied formatting`() {
        val resp = "Here are picks:\n2 | funny reaction\n[5]: great quote\n7 - the drop\nnonsense line"
        val sels = SemanticPrompts.parseHookSelections(resp)
        assertEquals(listOf(2, 5, 7), sels.map { it.index })
        assertEquals("funny reaction", sels[0].reason)
    }

    @Test fun `sentence candidates split at sentence ends`() {
        val words = listOf(
            Word("Hello.", 0, 400), Word("How", 500, 700), Word("are", 750, 900), Word("you?", 950, 1200),
        )
        val cands = SemanticPrompts.sentenceCandidates(words)
        assertEquals(2, cands.size)
        assertEquals("Hello.", cands[0].text)
        assertEquals(0L, cands[0].startMs)
        assertEquals(1200L, cands[1].endMs)
    }

    @Test fun `empty transcript yields no candidates`() {
        assertTrue(SemanticPrompts.sentenceCandidates(emptyList()).isEmpty())
    }
}
