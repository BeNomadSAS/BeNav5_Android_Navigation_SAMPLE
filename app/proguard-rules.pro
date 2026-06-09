# This sample ships with minification disabled (see app/build.gradle.kts).
# If you enable R8/minification in your own app, keep the mSDK classes — the SDK
# is JNI-backed and reflects over its own types, so stripping/renaming them breaks it.
-keep class com.benomad.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
