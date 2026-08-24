# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name
-renamesourcefileattribute SourceFile

# Keep the class names of every Throwable. Crashlytics de-obfuscates the exception class and the
# stack frames of a report through the mapping file, but not an arbitrary message string — and
# `Throwable.scrubbed` puts the failing type's name into the message, which is the only place the
# real type survives once the message is dropped. Without this a report from a bundled library or
# from this app's own exception types reads `l.a.b.c` in release and nothing maps it back.
-keep,allowshrinking,allowoptimization class * extends java.lang.Throwable
