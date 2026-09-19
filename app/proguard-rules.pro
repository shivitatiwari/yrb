# Preserve the youtubedl-android Java/Kotlin bridge. The large Python/FFmpeg
# payload stays untouched; R8 only removes unrelated unreachable app/library code.
-keep class com.yausername.youtubedl_android.** { *; }

# Keep JNI entry points referenced from native code.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
