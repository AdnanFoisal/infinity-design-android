# Infinity Design — ProGuard rules

# Keep all DSL classes — they're serialised with kotlinx.serialization.
-keep class com.adnanfoisal.infinitydesign.design.dsl.** { *; }
-keepclassmembers class com.adnanfoisal.infinitydesign.design.dsl.** {
    <fields>;
    <init>(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

# Hilt generated code — kept by Hilt's own rules.

# Room — kept by Room's own rules.

# Ktor client CIO engine — internal reflection.
-dontwarn io.ktor.client.engine.cio.**
-dontwarn org.slf4j.**

# OkHttp / Kotlinx-coroutines are well-tested for R8.
-dontwarn okhttp3.internal.platform.**

# Kotlin metadata — keep class names for reflection on Hilt.
-keep class kotlin.Metadata { *; }
