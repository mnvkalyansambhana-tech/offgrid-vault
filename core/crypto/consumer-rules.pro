# JNA (used by Lazysodium) binds native functions by reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# Lazysodium interfaces are mapped to libsodium symbols by method name.
-keep class com.goterl.lazysodium.** { *; }
