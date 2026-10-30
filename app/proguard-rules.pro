# Project-specific R8 / Proguard rules for Manager Pro
-keep class com.taandava.manager.data.** { *; }
-keep class com.taandava.manager.util.** { *; }

# OkHttp optimizations
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod, InnerClasses
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose optimizations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Strip debug logging calls in release APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
