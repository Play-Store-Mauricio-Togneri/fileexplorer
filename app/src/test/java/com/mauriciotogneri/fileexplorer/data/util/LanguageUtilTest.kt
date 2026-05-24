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
    fun `toDisplayLanguage returns original string for truly invalid language code`() {
        // Locale.forLanguageTag handles malformed tags, so use truly unrecognizable input
        val invalidCode = "!!!"
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
    fun `toDisplayLanguage handles additional two-letter language codes`() {
        Locale.setDefault(Locale.ENGLISH)
        // More standard ISO 639-1 codes
        assertEquals("Italian", "it".toDisplayLanguage())
        assertEquals("Japanese", "ja".toDisplayLanguage())
        assertEquals("Chinese", "zh".toDisplayLanguage())
    }
}
