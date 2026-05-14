# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.internal.** { *; }

# Qualcomm QNN / AI Stack
-keep class com.qualcomm.qti.** { *; }
-keep class com.qualcomm.qti.qnn.** { *; }

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

# Preserve line numbers for Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
