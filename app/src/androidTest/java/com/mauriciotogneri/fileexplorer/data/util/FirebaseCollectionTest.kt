package com.mauriciotogneri.fileexplorer.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The instrumentation suite runs against the debug build and drives failure paths on purpose, so
 * every [ErrorReporter] call it triggers would reach the production Crashlytics app — debug and
 * release ship the same applicationId. Collection is switched off through a build type manifest
 * placeholder, and this guards two ways that can silently stop working: the placeholder no longer
 * reaching the debug build, and the merged value no longer being encoded as a boolean. A value
 * that lands as a string reads back as the default `true`, and reporting resumes unnoticed.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseCollectionTest {

    @Test
    fun crashlyticsCollection_inTheBuildUnderTest_isDisabled() {
        assertFalse(applicationMetaData().getBoolean("firebase_crashlytics_collection_enabled", true))
    }

    @Test
    fun analyticsCollection_inTheBuildUnderTest_isDisabled() {
        assertFalse(applicationMetaData().getBoolean("firebase_analytics_collection_enabled", true))
    }

    @Suppress("DEPRECATION")
    private fun applicationMetaData(): Bundle {
        val context = ApplicationProvider.getApplicationContext<Context>()

        return context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
    }
}
