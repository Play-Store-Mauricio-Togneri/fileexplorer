package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileExtensionUtilTest {

    @Test
    fun `getExtension returns lowercase extension for normal file`() {
        assertEquals("pdf", FileExtensionUtil.getExtension("/path/to/document.pdf"))
    }

    @Test
    fun `getExtension returns lowercase for uppercase extension`() {
        assertEquals("pdf", FileExtensionUtil.getExtension("/path/to/DOCUMENT.PDF"))
    }

    @Test
    fun `getExtension returns unknown for file without extension`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/README"))
    }

    @Test
    fun `getExtension returns unknown for a dotfile rather than its name`() {
        // A dotfile's leading dot opens its name, so `substringAfterLast` would report the whole
        // name as the extension. Nothing the user named may reach an analytics parameter.
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/.private-journal"))
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/.gitignore"))
    }

    @Test
    fun `getExtension returns unknown for free text after a dot inside the name`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/Q3.Acme Confidential"))
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/budget.secret"))
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/Dr. Smith appointment"))
    }

    @Test
    fun `getExtension returns unknown for an extension the app does not recognise`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/file.zqx"))
    }

    @Test
    fun `getExtension returns unknown for a trailing dot`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/document."))
    }

    @Test
    fun `getExtension ignores dots in the directory portion of the path`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path.to/README"))
        assertEquals("pdf", FileExtensionUtil.getExtension("/path.to/document.pdf"))
    }

    @Test
    fun `getExtension handles a bare file name with no directory`() {
        assertEquals("pdf", FileExtensionUtil.getExtension("document.pdf"))
    }

    @Test
    fun `getExtension handles multiple dots correctly`() {
        assertEquals("gz", FileExtensionUtil.getExtension("/path/to/archive.tar.gz"))
    }

    @Test
    fun `getExtension handles long extensions`() {
        assertEquals("xhtml", FileExtensionUtil.getExtension("/path/to/file.xhtml"))
    }

    @Test
    fun `getExtension handles json extension`() {
        assertEquals("json", FileExtensionUtil.getExtension("/path/to/config.json"))
    }

    @Test
    fun `getExtension handles mixed case extension`() {
        assertEquals("jpg", FileExtensionUtil.getExtension("/path/to/image.JpG"))
    }

    @Test
    fun `getExtension resolves a path with a trailing separator`() {
        // The `> 0` rule reads File.name, so it relies on the constructor normalising the trailing
        // separator away. Pinned because nothing else in the suite records that dependency.
        assertEquals("pdf", FileExtensionUtil.getExtension("/path/to/document.pdf/"))
    }

    @Test
    fun `getExtension returns unknown for a name that is only dots`() {
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/."))
        assertEquals("unknown", FileExtensionUtil.getExtension("/path/to/.."))
        assertEquals("unknown", FileExtensionUtil.getExtension(""))
    }

    @Test
    fun `getExtension resolves a known extension after a non-ASCII stem`() {
        assertEquals("mp3", FileExtensionUtil.getExtension("/music/\uD83C\uDFB5 Sommer-Mix.mp3"))
        assertEquals("pdf", FileExtensionUtil.getExtension("/docs/r\u00E9sum\u00E9.pdf"))
    }

    @Test
    fun `getExtension only ever answers with its own vocabulary`() {
        // The guarantee the allowlist exists for: whatever the user named the file, the value is a
        // token from FileExtensionUtil or nothing at all.
        val adversarial = listOf(
            "/x/Q3.Acme Confidential",
            "/x/Dr. Smith appointment",
            "/x/.private-journal",
            "/x/r\u00E9sum\u00E9.FINAL DRAFT",
            "/x/Mum's will.Not For Sharing",
            "/x/passport scan.Personal"
        )

        adversarial.forEach { path ->
            assertEquals("unknown", FileExtensionUtil.getExtension(path))
        }
    }

    @Test
    fun `getExtension recognises the formats the app opens`() {
        assertEquals("mp3", FileExtensionUtil.getExtension("/music/track.mp3"))
        assertEquals("mp4", FileExtensionUtil.getExtension("/video/clip.mp4"))
        assertEquals("zip", FileExtensionUtil.getExtension("/downloads/archive.zip"))
        assertEquals("apk", FileExtensionUtil.getExtension("/downloads/app.apk"))
        assertEquals("epub", FileExtensionUtil.getExtension("/books/novel.epub"))
        assertEquals("docx", FileExtensionUtil.getExtension("/docs/report.docx"))
        assertEquals("kt", FileExtensionUtil.getExtension("/src/Main.kt"))
        assertEquals("ttf", FileExtensionUtil.getExtension("/fonts/Roboto.ttf"))
        assertEquals("sqlite", FileExtensionUtil.getExtension("/data/store.sqlite"))
        assertEquals("heic", FileExtensionUtil.getExtension("/photos/IMG_0001.heic"))
        assertEquals("svg", FileExtensionUtil.getExtension("/icons/logo.svg"))
        assertEquals("tiff", FileExtensionUtil.getExtension("/scans/page.tiff"))
        assertEquals("psd", FileExtensionUtil.getExtension("/art/cover.psd"))
        assertEquals("vcf", FileExtensionUtil.getExtension("/contacts/export.vcf"))
        assertEquals("pem", FileExtensionUtil.getExtension("/certs/server.pem"))
        assertEquals("bak", FileExtensionUtil.getExtension("/backups/notes.bak"))
    }
}
