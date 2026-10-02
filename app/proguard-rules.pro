# R8 rules for AniSequel.
#
# The app talks to AniList through Retrofit, which resolves its generic return
# types reflectively, and through Moshi, which looks up the generated
# `FooJsonAdapter` classes by name. Both are invisible to R8's reachability
# analysis, so without these rules a minified release parses nothing and fails
# at runtime rather than at build time.
#
# Retrofit, OkHttp and Moshi all ship consumer rules of their own; what follows
# is the narrow safety net for this app's own reflection surfaces.

# --- Moshi codegen adapters -------------------------------------------------
# moshi-kotlin-codegen emits its own rules for the adapters it generates, but
# only for classes it processed. Keeping the annotated models and their adapters
# by name means a model can never lose its adapter because of an incremental
# build that skipped codegen.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# --- Retrofit ---------------------------------------------------------------
# Generic signatures are how Retrofit reads GraphQLResponse<T>.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# --- Kotlin coroutines -------------------------------------------------------
# Debug builds of coroutines need this to keep stack traces readable; harmless
# in release, and it keeps the standard rules honest if coroutines is upgraded.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep line numbers, but hide the original source file names in the shipped APK.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile