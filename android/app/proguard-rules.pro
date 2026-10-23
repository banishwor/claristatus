# Media3 Transformer & ExoPlayer rules
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# Keep MediaCodec & OpenGL helpers
-keepattributes *Annotation*
-dontwarn androidx.media3.**
