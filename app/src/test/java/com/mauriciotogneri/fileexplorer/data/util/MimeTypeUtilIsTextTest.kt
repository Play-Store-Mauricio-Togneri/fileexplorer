package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypeUtilIsTextTest {

    @Test
    fun `isText returns true for text mime types`() {
        assertTrue(MimeTypeUtil.isText("text/plain"))
        assertTrue(MimeTypeUtil.isText("text/markdown"))
        assertTrue(MimeTypeUtil.isText("text/html"))
        assertTrue(MimeTypeUtil.isText("text/x-kotlin"))
    }

    @Test
    fun `isText returns false for non-text mime types`() {
        assertFalse(MimeTypeUtil.isText("application/pdf"))
        assertFalse(MimeTypeUtil.isText("image/png"))
        assertFalse(MimeTypeUtil.isText("application/octet-stream"))
        assertFalse(MimeTypeUtil.isText("*/*"))
    }

    @Test
    fun `isTextByExtension returns true for common text extensions`() {
        assertTrue(MimeTypeUtil.isTextByExtension("notes.txt"))
        assertTrue(MimeTypeUtil.isTextByExtension("README.md"))
        assertTrue(MimeTypeUtil.isTextByExtension("data.json"))
        assertTrue(MimeTypeUtil.isTextByExtension("Main.kt"))
        assertTrue(MimeTypeUtil.isTextByExtension("server.log"))
        assertTrue(MimeTypeUtil.isTextByExtension("config.yaml"))
        assertTrue(MimeTypeUtil.isTextByExtension("app.config"))
        assertTrue(MimeTypeUtil.isTextByExtension("places.kml"))
        assertTrue(MimeTypeUtil.isTextByExtension("route.gpx"))
        assertTrue(MimeTypeUtil.isTextByExtension("styles.scss"))
        assertTrue(MimeTypeUtil.isTextByExtension("App.swift"))
    }

    @Test
    fun `isTextByExtension is case-insensitive`() {
        assertTrue(MimeTypeUtil.isTextByExtension("NOTES.TXT"))
        assertTrue(MimeTypeUtil.isTextByExtension("Readme.MD"))
        assertTrue(MimeTypeUtil.isTextByExtension("DATA.Json"))
    }

    @Test
    fun `isTextByExtension uses only the last extension and handles dotfiles`() {
        assertFalse(MimeTypeUtil.isTextByExtension("archive.tar.gz"))
        assertTrue(MimeTypeUtil.isTextByExtension("project.tar.kt"))
        assertTrue(MimeTypeUtil.isTextByExtension(".gitignore"))
    }

    @Test
    fun `isTextByExtension returns false for non-text and extensionless names`() {
        assertFalse(MimeTypeUtil.isTextByExtension("photo.png"))
        assertFalse(MimeTypeUtil.isTextByExtension("song.mp3"))
        assertFalse(MimeTypeUtil.isTextByExtension("blob.bin"))
        assertFalse(MimeTypeUtil.isTextByExtension("README"))
    }
}
