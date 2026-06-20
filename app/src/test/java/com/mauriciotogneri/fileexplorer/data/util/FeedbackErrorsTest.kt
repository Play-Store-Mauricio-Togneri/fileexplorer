package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class FeedbackErrorsTest {

    @Test
    fun `isNetworkError returns true for an unresolved host`() {
        // The exact failure reported by Crashlytics: no DNS resolution while offline.
        val e = UnknownHostException("Unable to resolve host \"script.google.com\"")
        assertTrue(isNetworkError(e))
    }

    @Test
    fun `isNetworkError returns true for the rest of the connectivity-failure family`() {
        assertTrue(isNetworkError(SocketTimeoutException("timeout")))
        assertTrue(isNetworkError(ConnectException("connection refused")))
        assertTrue(isNetworkError(SSLException("handshake failed")))
        assertTrue(isNetworkError(IOException("unexpected end of stream")))
    }

    @Test
    fun `isNetworkError returns false for unrelated exceptions`() {
        assertFalse(isNetworkError(IllegalStateException("boom")))
        assertFalse(isNetworkError(IllegalArgumentException()))
        assertFalse(isNetworkError(RuntimeException()))
        assertFalse(isNetworkError(OutOfMemoryError()))
    }
}
