# Picovoice/Porcupine
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx-serialization
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Hermes / Totoro
-keep class com.totoro.assistant.** { *; }
