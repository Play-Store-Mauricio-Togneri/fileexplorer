package com.mauriciotogneri.fileexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameTest {

    @Test
    fun `hasInvalidFileNameCharacters returns true for forward slash`() {
        assertTrue(hasInvalidFileNameCharacters("file/name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for backslash`() {
        assertTrue(hasInvalidFileNameCharacters("file\\name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for asterisk`() {
        assertTrue(hasInvalidFileNameCharacters("file*name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for question mark`() {
        assertTrue(hasInvalidFileNameCharacters("file?name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for double quote`() {
        assertTrue(hasInvalidFileNameCharacters("file\"name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for less than`() {
        assertTrue(hasInvalidFileNameCharacters("file<name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for greater than`() {
        assertTrue(hasInvalidFileNameCharacters("file>name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for pipe`() {
        assertTrue(hasInvalidFileNameCharacters("file|name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns true for colon`() {
        assertTrue(hasInvalidFileNameCharacters("file:name"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns false for valid name`() {
        assertFalse(hasInvalidFileNameCharacters("valid-file_name.txt"))
    }

    @Test
    fun `hasInvalidFileNameCharacters returns false for empty string`() {
        assertFalse(hasInvalidFileNameCharacters(""))
    }

    @Test
    fun `isValidFileName returns false for empty string`() {
        assertFalse(isValidFileName(""))
    }

    @Test
    fun `isValidFileName returns false for blank string`() {
        assertFalse(isValidFileName("   "))
    }

    @Test
    fun `isValidFileName returns false for single dot`() {
        assertFalse(isValidFileName("."))
    }

    @Test
    fun `isValidFileName returns false for double dot`() {
        assertFalse(isValidFileName(".."))
    }

    @Test
    fun `isValidFileName returns false for name with invalid characters`() {
        assertFalse(isValidFileName("hello?.zip"))
    }

    @Test
    fun `isValidFileName returns true for valid name`() {
        assertTrue(isValidFileName("document.pdf"))
    }

    @Test
    fun `isValidFileName returns true for name with spaces`() {
        assertTrue(isValidFileName("my document.pdf"))
    }

    @Test
    fun `isValidFileName returns true for name starting with dot`() {
        assertTrue(isValidFileName(".gitignore"))
    }

    @Test
    fun `isValidFileName returns true for unicode characters`() {
        assertTrue(isValidFileName("文档.pdf"))
    }

    @Test
    fun `isValidFileName returns true for name with hyphens and underscores`() {
        assertTrue(isValidFileName("my-file_name"))
    }

    @Test
    fun `fileNameStem splits an ordinary name at its extension`() {
        assertEquals("photo", fileNameStem("photo.jpg"))
        assertEquals("backup.tar", fileNameStem("backup.tar.gz"))
        // A dotfile can still carry an extension: only the leading dot separates nothing.
        assertEquals(".env", fileNameStem(".env.local"))
        assertEquals("文档", fileNameStem("文档.pdf"))
    }

    @Test
    fun `fileNameStem keeps a name that has no extension to put back`() {
        // A dot that starts or ends the name separates nothing: the whole name is the stem, so a
        // rename selects all of it and a numbered copy keeps it intact.
        assertEquals("README", fileNameStem("README"))
        assertEquals(".gitignore", fileNameStem(".gitignore"))
        assertEquals("notes.", fileNameStem("notes."))
        assertEquals("", fileNameStem(""))
    }
}
