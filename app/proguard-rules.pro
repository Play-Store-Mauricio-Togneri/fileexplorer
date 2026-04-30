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

# ==================== Kotlin Serialization ====================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mauriciotogneri.fileexplorer.**$$serializer { *; }
-keepclassmembers class com.mauriciotogneri.fileexplorer.** {
    *** Companion;
}
-keepclasseswithmembers class com.mauriciotogneri.fileexplorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used with serialization
-keep class com.mauriciotogneri.fileexplorer.data.model.** { *; }

# ==================== Firebase ====================
-keep class com.google.firebase.** { *; }
