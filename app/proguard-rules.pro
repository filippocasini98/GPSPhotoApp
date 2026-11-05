# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.example.gpsphotapp.**$$serializer { *; }
-keepclassmembers class com.example.gpsphotapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.gpsphotapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# GeoPackage / NGA SF
-keep class mil.nga.** { *; }
-dontwarn mil.nga.**
-keep class org.sqlite.** { *; }
-dontwarn org.sqlite.**

# Play Services Location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# Coil / OkHttp
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
