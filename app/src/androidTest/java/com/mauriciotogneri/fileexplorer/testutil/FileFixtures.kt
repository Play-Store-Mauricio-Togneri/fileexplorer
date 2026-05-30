package com.mauriciotogneri.fileexplorer.testutil

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

/**
 * Shared fixture helpers for instrumentation tests (plan prerequisite P5).
 *
 * The zip helpers mirror the creation logic already proven in
 * [com.mauriciotogneri.fileexplorer.integration.FileOperationsEndToEndTest], extracted here so the
 * UI-level stages (file-open routing, sorting, uncompress flow, ...) can reuse them instead of
 * duplicating zip4j boilerplate.
 */
object FileFixtures {

    fun createTextFile(dir: File, name: String, content: String = "content"): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    fun createFolder(dir: File, name: String): File {
        val folder = File(dir, name)
        folder.mkdirs()
        return folder
    }

    /**
     * A file with an `.apk` extension. File-open routing is mime/extension based
     * ([com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil.isApk]); the bytes are irrelevant
     * for the permission-dialog branch.
     */
    fun createFakeApk(dir: File, name: String = "app.apk"): File =
        createTextFile(dir, name, "fake-apk-bytes")

    /** Creates a plain (non-encrypted) zip whose entries are `relativePath -> textContent`. */
    fun createZip(dir: File, name: String, entries: Map<String, String>): File =
        buildZip(dir, name, entries, password = null)

    /** Creates an AES-encrypted, password-protected zip. */
    fun createPasswordZip(
        dir: File,
        name: String,
        password: String,
        entries: Map<String, String>
    ): File = buildZip(dir, name, entries, password = password)

    private fun buildZip(
        dir: File,
        name: String,
        entries: Map<String, String>,
        password: String?
    ): File {
        val zipFile = File(dir, name)
        val staging = File(dir, "__staging_$name")
        staging.mkdirs()
        try {
            entries.forEach { (entryName, content) ->
                val entryFile = File(staging, entryName)
                entryFile.parentFile?.mkdirs()
                entryFile.writeText(content)
            }

            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                if (password != null) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                }
            }

            val zip = if (password != null) ZipFile(zipFile, password.toCharArray()) else ZipFile(zipFile)
            zip.use { archive ->
                staging.listFiles()?.forEach { file ->
                    if (file.isDirectory) archive.addFolder(file, params) else archive.addFile(file, params)
                }
            }
        } finally {
            staging.deleteRecursively()
        }
        return zipFile
    }
}
