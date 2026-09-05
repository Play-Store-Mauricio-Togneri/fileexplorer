package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search box decides between a pattern and a substring by what the user typed, so both what
 * counts as a pattern and what a pattern then matches are the contract here.
 */
class GlobPatternTest {

    @Test
    fun `a query without a wildcard is not a pattern`() {
        // Null is what keeps every query typed before this release on the substring match it had.
        assertNull(globPatternOrNull("report"))
        assertNull(globPatternOrNull(""))
        assertNull(globPatternOrNull("report (1).pdf"))
    }

    @Test
    fun `a star stands for any run of characters`() {
        val pattern = globPatternOrNull("log-*.txt")!!
        assertTrue(pattern.matches("log-2024.txt"))
        assertTrue(pattern.matches("log-.txt"))
        assertTrue(pattern.matches("log-2024-01-31.txt"))
    }

    @Test
    fun `a question mark stands for exactly one character`() {
        val pattern = globPatternOrNull("IMG_?.jpg")!!
        assertTrue(pattern.matches("IMG_3.jpg"))
        assertFalse(pattern.matches("IMG_42.jpg"))
        assertFalse(pattern.matches("IMG_.jpg"))
    }

    @Test
    fun `a pattern matches the whole name rather than part of it`() {
        // The difference from substring matching: an extension pattern means the name ends there.
        val pattern = globPatternOrNull("*.txt")!!
        assertTrue(pattern.matches("notes.txt"))
        assertFalse(pattern.matches("notes.txt.bak"))
    }

    @Test
    fun `a pattern is case insensitive like the substring match it replaces`() {
        val pattern = globPatternOrNull("*.TXT")!!
        assertTrue(pattern.matches("notes.txt"))
        assertTrue(pattern.matches("NOTES.Txt"))
    }

    @Test
    fun `regex syntax between the wildcards stays literal`() {
        // Without escaping, the parentheses would group and the dot would match any character, so
        // a query naming a real file would quietly match files it does not name.
        val pattern = globPatternOrNull("report (1)*.pdf")!!
        assertTrue(pattern.matches("report (1) final.pdf"))
        assertFalse(pattern.matches("report 1 final.pdf"))

        val dotted = globPatternOrNull("a.b*")!!
        assertTrue(dotted.matches("a.b.txt"))
        assertFalse(dotted.matches("axb.txt"))
    }

    @Test
    fun `a pattern folds case beyond ASCII, as the substring match it replaces does`() {
        // The substring path folds the whole Unicode range, so a query that worked without a
        // wildcard must not stop working when one is added.
        assertTrue(globPatternOrNull("*É*")!!.matches("résumé final.pdf"))
        assertTrue(globPatternOrNull("übung*")!!.matches("ÜBUNG.pdf"))
        assertTrue(globPatternOrNull("Отчёт*")!!.matches("ОТЧЁТ 2024.doc"))
    }

    @Test
    fun `a star spans characters a filename may legally contain`() {
        // A newline is legal in a name on this platform and arrives from extracted archives.
        assertTrue(globPatternOrNull("*.txt")!!.matches("we\nird.txt"))
        assertTrue(globPatternOrNull("*")!!.matches("a\nb.txt"))
    }

    @Test
    fun `a question mark stands for one character the user can see`() {
        // An emoji is two chars and must not need two question marks.
        assertTrue(globPatternOrNull("IMG_?.jpg")!!.matches("IMG_\uD83D\uDE00.jpg"))
        assertFalse(globPatternOrNull("IMG_??.jpg")!!.matches("IMG_\uD83D\uDE00.jpg"))
    }

    @Test
    fun `a star-heavy query against a long name returns promptly`() {
        // The shape that made the previous regex implementation backtrack exponentially: ten stars
        // separated by literals, against a name that very nearly matches. Five seconds at 36
        // characters then, and unkillable, because a match in progress polls no cancellation.
        val pattern = globPatternOrNull("*a*a*a*a*a*a*a*a*a*a*b")!!
        val name = "a".repeat(2000) + ".txt"

        val elapsed = kotlin.system.measureTimeMillis { assertFalse(pattern.matches(name)) }

        assertTrue("Matching took $elapsed ms", elapsed < 1000)
    }

    @Test
    fun `a lone star matches every name`() {
        val pattern = globPatternOrNull("*")!!
        assertTrue(pattern.matches("notes.txt"))
        assertTrue(pattern.matches(""))
        assertTrue(pattern.matches("a folder"))
    }

    @Test
    fun `wildcards can be combined and repeated`() {
        val pattern = globPatternOrNull("*-??-*")!!
        assertTrue(pattern.matches("log-01-final"))
        assertFalse(pattern.matches("log-1-final"))
    }
}
