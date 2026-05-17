# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.internal.** { *; }

# Hilt / Dagger
-keep class androidx.hilt.** { *; }
-keep class dagger.hilt.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.RoomDatabase {
    static <fields>;
}

# Kotlin Serialization
-keepattributes *Annotation*, Enums
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class ** {
    public static ** Companion;
    public static ** $serializer;
}

# General native library handling
-keepclasseswithmembernames class * {
    native <methods>;
}

# Firebase and Crashlytics
# Ensure Firebase component discovery is not stripped
-keep public class * implements com.google.firebase.components.ComponentRegistrar
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Strip all logs in release builds (v, d, i, w, e, println)
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

# Preserve line numbers for Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# R8 missing classes fixes
-dontwarn javax.lang.model.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.processor.**
