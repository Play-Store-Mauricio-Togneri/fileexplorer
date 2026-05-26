package com.mauriciotogneri.fileexplorer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreadcrumbPathParserTest {

    private val internalStorageName = "Internal Storage"

    @Test
    fun `parsePath returns empty list for blank path`() {
        val result = BreadcrumbPathParser.parsePath("", internalStorageName, null, null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePath returns empty list for whitespace path`() {
        val result = BreadcrumbPathParser.parsePath("   ", internalStorageName, null, null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePath splits path into segments`() {
        val result = BreadcrumbPathParser.parsePath("/foo/bar/baz", internalStorageName, null, null)

        assertEquals(3, result.size)
        assertEquals(BreadcrumbItem("foo", "/foo"), result[0])
        assertEquals(BreadcrumbItem("bar", "/foo/bar"), result[1])
        assertEquals(BreadcrumbItem("baz", "/foo/bar/baz"), result[2])
    }

    @Test
    fun `parsePath collapses internal storage path`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads",
            internalStorageName,
            null,
            null
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem(internalStorageName, "/storage/emulated/0"), result[0])
        assertEquals(BreadcrumbItem("Downloads", "/storage/emulated/0/Downloads"), result[1])
    }

    @Test
    fun `parsePath collapses internal storage with different user id`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/10/Documents",
            internalStorageName,
            null,
            null
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem(internalStorageName, "/storage/emulated/10"), result[0])
        assertEquals(BreadcrumbItem("Documents", "/storage/emulated/10/Documents"), result[1])
    }

    @Test
    fun `parsePath with rootPath filters to show only from root onwards`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads/foo/bar",
            internalStorageName,
            "/storage/emulated/0/Downloads/foo",
            null
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem("foo", "/storage/emulated/0/Downloads/foo"), result[0])
        assertEquals(BreadcrumbItem("bar", "/storage/emulated/0/Downloads/foo/bar"), result[1])
    }

    @Test
    fun `parsePath with rootPath shows single item when at root`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads/foo",
            internalStorageName,
            "/storage/emulated/0/Downloads/foo",
            null
        )

        assertEquals(1, result.size)
        assertEquals(BreadcrumbItem("foo", "/storage/emulated/0/Downloads/foo"), result[0])
    }

    @Test
    fun `parsePath with rootPath not found falls back to collapsed path`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads",
            internalStorageName,
            "/nonexistent/path",
            null
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem(internalStorageName, "/storage/emulated/0"), result[0])
        assertEquals(BreadcrumbItem("Downloads", "/storage/emulated/0/Downloads"), result[1])
    }

    @Test
    fun `parsePath with rootPath skips internal storage collapsing`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads/foo",
            internalStorageName,
            "/storage/emulated/0/Downloads",
            null
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem("Downloads", "/storage/emulated/0/Downloads"), result[0])
        assertEquals(BreadcrumbItem("foo", "/storage/emulated/0/Downloads/foo"), result[1])
    }

    @Test
    fun `parsePath handles path without leading slash`() {
        val result = BreadcrumbPathParser.parsePath("foo/bar", internalStorageName, null, null)

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem("foo", "/foo"), result[0])
        assertEquals(BreadcrumbItem("bar", "/foo/bar"), result[1])
    }

    @Test
    fun `parsePath handles external storage path without collapsing`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/1234-5678/DCIM/Camera",
            internalStorageName,
            null,
            null
        )

        assertEquals(4, result.size)
        assertEquals(BreadcrumbItem("storage", "/storage"), result[0])
        assertEquals(BreadcrumbItem("1234-5678", "/storage/1234-5678"), result[1])
        assertEquals(BreadcrumbItem("DCIM", "/storage/1234-5678/DCIM"), result[2])
        assertEquals(BreadcrumbItem("Camera", "/storage/1234-5678/DCIM/Camera"), result[3])
    }

    @Test
    fun `parsePath with rootDisplayName replaces root item name`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads/foo/bar",
            internalStorageName,
            "/storage/emulated/0/Downloads",
            "Downloads Folder"
        )

        assertEquals(3, result.size)
        assertEquals(BreadcrumbItem("Downloads Folder", "/storage/emulated/0/Downloads"), result[0])
        assertEquals(BreadcrumbItem("foo", "/storage/emulated/0/Downloads/foo"), result[1])
        assertEquals(BreadcrumbItem("bar", "/storage/emulated/0/Downloads/foo/bar"), result[2])
    }

    @Test
    fun `parsePath with rootDisplayName at root shows single item with display name`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads",
            internalStorageName,
            "/storage/emulated/0/Downloads",
            "My Downloads"
        )

        assertEquals(1, result.size)
        assertEquals(BreadcrumbItem("My Downloads", "/storage/emulated/0/Downloads"), result[0])
    }

    @Test
    fun `parsePath with rootDisplayName but no rootPath uses original names`() {
        val result = BreadcrumbPathParser.parsePath(
            "/storage/emulated/0/Downloads",
            internalStorageName,
            null,
            "Ignored Display Name"
        )

        assertEquals(2, result.size)
        assertEquals(BreadcrumbItem(internalStorageName, "/storage/emulated/0"), result[0])
        assertEquals(BreadcrumbItem("Downloads", "/storage/emulated/0/Downloads"), result[1])
    }
}
