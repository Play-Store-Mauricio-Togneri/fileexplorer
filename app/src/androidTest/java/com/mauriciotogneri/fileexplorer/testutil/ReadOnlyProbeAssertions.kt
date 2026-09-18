package com.mauriciotogneri.fileexplorer.testutil

import org.junit.Assert.assertEquals
import java.io.File
import java.security.MessageDigest

/** Checks successful probes too: a decoder can return metadata and still destroy its source. */
fun <T> assertReadOnlyProbe(file: File, probe: () -> T): T {
    val before = snapshot(file)
    val result = probe()
    assertEquals("Probing ${file.name} changed the source or its contents", before, snapshot(file))
    return result
}

private fun snapshot(file: File): Map<String, List<Byte>?> {
    if (!file.exists()) return emptyMap()
    // Include directories as well as files, so deleting an empty directory cannot pass.
    return file.walkTopDown().associate { entry ->
        val digest = if (entry.isDirectory) {
            null
        } else {
            val hash = MessageDigest.getInstance("SHA-256")
            entry.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    hash.update(buffer, 0, count)
                }
            }
            hash.digest().toList()
        }
        entry.relativeTo(file).path to digest
    }
}
