# AceStream TV ProGuard Rules

# Keep model classes
-keep class com.acestream.tv.model.** { *; }

# Keep AceStream engine classes
-keep class com.acestream.tv.acestream.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Leanback
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Flow
-keep class kotlinx.coroutines.flow.** { *; }
