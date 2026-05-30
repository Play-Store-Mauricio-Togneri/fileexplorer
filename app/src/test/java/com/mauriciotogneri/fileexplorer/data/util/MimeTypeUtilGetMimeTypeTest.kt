package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MimeTypeUtilGetMimeTypeTest {

    // Regression test for the StringIndexOutOfBoundsException crash in getMimeType
    // (paths containing '#' before the extension). getMimeType must never throw and
    // must fall back to the wildcard mime type when MIME resolution fails.
    //
    // In a plain JVM unit test, the android.webkit.MimeTypeMap fallback is an
    // unmocked stub that throws "not mocked". By passing a path that URLConnection
    // cannot classify (no extension -> guessContentTypeFromName returns null), the
    // MimeTypeMap branch is reached and throws, which exercises the try/catch added
    // to getMimeType. If the catch is removed or narrowed, this test fails.
    @Test
    fun `getMimeType returns wildcard and does not throw when resolution fails`() {
        val result = MimeTypeUtil.getMimeType(File("/storage/emulated/0/file-without-extension"))

        assertEquals("*/*", result)
    }
}
