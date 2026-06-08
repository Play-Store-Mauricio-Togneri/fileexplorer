package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {

    @Test
    fun `IMAGES matches images and svg`() {
        assertTrue(SearchFileType.IMAGES.matches(FakeTypeInfo(isImage = true)))
        assertTrue(SearchFileType.IMAGES.matches(FakeTypeInfo(isSvg = true)))
        assertFalse(SearchFileType.IMAGES.matches(FakeTypeInfo(isAudio = true)))
    }

    @Test
    fun `AUDIO matches audio only`() {
        assertTrue(SearchFileType.AUDIO.matches(FakeTypeInfo(isAudio = true)))
        assertFalse(SearchFileType.AUDIO.matches(FakeTypeInfo(isVideo = true)))
    }

    @Test
    fun `VIDEOS matches video only`() {
        assertTrue(SearchFileType.VIDEOS.matches(FakeTypeInfo(isVideo = true)))
        assertFalse(SearchFileType.VIDEOS.matches(FakeTypeInfo(isImage = true)))
    }

    @Test
    fun `DOCUMENTS matches document-like types`() {
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isPdf = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isOfficeDocument = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isText = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isCsv = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isEpub = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isVCard = true)))
        assertTrue(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isICalendar = true)))
        assertFalse(SearchFileType.DOCUMENTS.matches(FakeTypeInfo(isImage = true)))
    }

    @Test
    fun `OTHER matches anything outside the named categories`() {
        assertTrue(SearchFileType.OTHER.matches(FakeTypeInfo(isApk = true)))
        assertTrue(SearchFileType.OTHER.matches(FakeTypeInfo(isArchive = true)))
        assertTrue(SearchFileType.OTHER.matches(FakeTypeInfo(isFont = true)))
        assertTrue(SearchFileType.OTHER.matches(FakeTypeInfo()))
    }

    @Test
    fun `OTHER does not match named categories`() {
        assertFalse(SearchFileType.OTHER.matches(FakeTypeInfo(isImage = true)))
        assertFalse(SearchFileType.OTHER.matches(FakeTypeInfo(isAudio = true)))
        assertFalse(SearchFileType.OTHER.matches(FakeTypeInfo(isVideo = true)))
        assertFalse(SearchFileType.OTHER.matches(FakeTypeInfo(isPdf = true)))
    }

    @Test
    fun `matchesType with empty selection matches everything`() {
        val filters = SearchFilters(selectedTypes = emptySet())
        assertTrue(filters.matchesType(FakeTypeInfo(isImage = true)))
        assertTrue(filters.matchesType(FakeTypeInfo(isApk = true)))
    }

    @Test
    fun `matchesType matches any of the selected types`() {
        val filters = SearchFilters(selectedTypes = setOf(SearchFileType.IMAGES, SearchFileType.AUDIO))
        assertTrue(filters.matchesType(FakeTypeInfo(isImage = true)))
        assertTrue(filters.matchesType(FakeTypeInfo(isAudio = true)))
        assertFalse(filters.matchesType(FakeTypeInfo(isVideo = true)))
    }

    private data class FakeTypeInfo(
        override val isDirectory: Boolean = false,
        override val isImage: Boolean = false,
        override val isSvg: Boolean = false,
        override val isPdf: Boolean = false,
        override val isAudio: Boolean = false,
        override val isVideo: Boolean = false,
        override val isApk: Boolean = false,
        override val isZip: Boolean = false,
        override val isArchive: Boolean = false,
        override val isOfficeDocument: Boolean = false,
        override val isEpub: Boolean = false,
        override val isFont: Boolean = false,
        override val isSqlite: Boolean = false,
        override val isVCard: Boolean = false,
        override val isICalendar: Boolean = false,
        override val isCsv: Boolean = false,
        override val isText: Boolean = false
    ) : FileTypeInfo
}
