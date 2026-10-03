# T12: strip every android.util.Log call from release builds (defence in depth; the
# checkForbiddenApis task already rejects Log usage in our own sources, this also
# removes calls from dependencies).
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# JNA (used by Lazysodium) binds native functions by reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# Lazysodium interfaces are mapped to libsodium symbols by method name.
-keep class com.goterl.lazysodium.** { *; }
