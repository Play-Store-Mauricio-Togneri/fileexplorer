package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * Returns true when [e] indicates the feedback request could not reach or complete
 * against the server because of a network-level failure. These are expected,
 * unactionable conditions (the user is offline, on flaky DNS, behind a captive
 * portal, etc.) — not bugs — and must not be reported to crash analytics.
 *
 * OkHttp's [okhttp3.Call.execute] surfaces the entire connectivity-failure family as
 * [IOException]: [java.net.UnknownHostException] (DNS), [java.net.ConnectException],
 * [java.net.SocketTimeoutException], [javax.net.ssl.SSLException], premature stream
 * close, and so on. Matched by type because that is the single net covering every
 * transport failure OkHttp raises; any other exception reaching the caller (e.g. a
 * bug while building the payload) is unexpected and remains reportable.
 */
internal fun isNetworkError(e: Throwable): Boolean = e is IOException
