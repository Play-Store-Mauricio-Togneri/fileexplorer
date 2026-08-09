package com.mauriciotogneri.fileexplorer.data.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LanguageUtilTest {

    private lateinit var originalLocale: Locale

    /**
     * `toDisplayLanguage` renders in the default locale, so these assertions need a known one. The
     * default is process-global and unit tests share a JVM: setting it without restoring leaks into
     * whatever runs next. `FileSizeFormatterTest` has the same requirement and the same pairing.
     */
    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `toDisplayLanguage returns display name for valid language code`() {
        assertEquals("English", "en".toDisplayLanguage())
        assertEquals("German", "de".toDisplayLanguage())
        assertEquals("French", "fr".toDisplayLanguage())
        assertEquals("Spanish", "es".toDisplayLanguage())
    }

    @Test
    fun `toDisplayLanguage returns display name for language with region code`() {
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
        // More standard ISO 639-1 codes
        assertEquals("Italian", "it".toDisplayLanguage())
        assertEquals("Japanese", "ja".toDisplayLanguage())
        assertEquals("Chinese", "zh".toDisplayLanguage())
    }
}
