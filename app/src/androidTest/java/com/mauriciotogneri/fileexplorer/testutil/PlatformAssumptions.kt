package com.mauriciotogneri.fileexplorer.testutil

import android.webkit.MimeTypeMap
import org.junit.Assume.assumeTrue

/**
 * Skips when the platform's own MIME map does not know `.apk`, read from the platform rather
 * than from MimeTypeUtil: a production detector that stops recognising the file must fail the
 * test, not skip it.
 */
fun assumePlatformMapsApkExtension() {
    assumeTrue(
        "Platform MIME map does not map the apk extension",
        MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk") == "application/vnd.android.package-archive"
    )
}
