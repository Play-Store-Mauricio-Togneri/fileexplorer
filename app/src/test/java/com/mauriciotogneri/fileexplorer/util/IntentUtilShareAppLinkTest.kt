package com.mauriciotogneri.fileexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/**
 * Guards the install referrer on the link the About screen shares.
 *
 * Play Console attributes an install to a share only when the `referrer` parameter decodes to the
 * expected UTM pairs. A link with a mangled referrer — an unencoded `&` that splits
 * `utm_campaign` off into a parameter of its own, say — still opens the right store page, so
 * nobody tapping it would notice the installs going unattributed.
 */
class IntentUtilShareAppLinkTest {

    private fun queryParameters(link: String): Map<String, String> {
        return URI(link).rawQuery.split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }
    }

    @Test
    fun shareAppLink_pointsAtTheStorePageOfThePackage() {
        val link = URI(IntentUtil.shareAppLink(PACKAGE))

        assertEquals("https", link.scheme)
        assertEquals("play.google.com", link.host)
        assertEquals("/store/apps/details", link.path)
        assertEquals(PACKAGE, queryParameters(link.toString())["id"])
    }

    @Test
    fun shareAppLink_carriesTheShareReferrerAsASingleParameter() {
        val parameters = queryParameters(IntentUtil.shareAppLink(PACKAGE))

        assertEquals(setOf("id", "referrer"), parameters.keys)
        assertEquals("utm_source=app_share&utm_campaign=about", parameters["referrer"])
    }

    private companion object {
        const val PACKAGE = "com.mauriciotogneri.fileexplorer"
    }
}
