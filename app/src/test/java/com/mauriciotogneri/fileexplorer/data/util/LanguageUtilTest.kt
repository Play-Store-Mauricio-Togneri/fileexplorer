package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageUtilTest {

    @Test
    fun `toDisplayLanguage returns display name for valid language code`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("English", "en".toDisplayLanguage())
        assertEquals("German", "de".toDisplayLanguage())
        assertEquals("French", "fr".toDisplayLanguage())
        assertEquals("Spanish", "es".toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage returns display name for language with region code`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("English", "en-US".toDisplayLanguage())
        assertEquals("English", "en-GB".toDisplayLanguage())
        assertEquals("Portuguese", "pt-BR".toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage returns original string for invalid language code`() {
        val invalidCode = "xyz-invalid"
        assertEquals(invalidCode, invalidCode.toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage returns original string for empty string`() {
        assertEquals("", "".toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage returns original string for blank string`() {
        val blankString = "   "
        assertEquals(blankString, blankString.toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage handles three-letter language codes`() {
        Locale.setDefault(Locale.ENGLISH)
        // ISO 639-2/T codes
        assertEquals("German", "deu".toDisplayLanguage())
        assertEquals("French", "fra".toDisplayLanguage())
    }
}
