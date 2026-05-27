package com.mauriciotogneri.fileexplorer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameValidatorTest {

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
}
