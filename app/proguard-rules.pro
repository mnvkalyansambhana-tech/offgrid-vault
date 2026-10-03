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

# JNA/Lazysodium keep rules ship with :core:crypto (consumer-rules.pro).
