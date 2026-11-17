# The Web Player page calls the host through this @JavascriptInterface on WebViews without
# web message listeners (PlayerChannel.kt).
-keepclassmembers class io.github.thake.soundnest.host.android.PlayerChannel {
    @android.webkit.JavascriptInterface <methods>;
}
# Bundled resources read by name (bridge script, GraphQL operation table).
-keep class io.github.thake.soundnest.spotify.bridge.BridgeScript { *; }
-keep class io.github.thake.soundnest.spotify.gql.GqlOps { *; }
-dontwarn javax.annotation.concurrent.GuardedBy
-keepclassmembers enum * { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepattributes *Annotation*,JavascriptInterface,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.protos.** { *; }
-keep class ** extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class ** extends com.google.protobuf.GeneratedMessage { *; }
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class ** extends com.google.protobuf.GeneratedMessage {
    <fields>;
}

-dontwarn java.lang.instrument.**

-keep class androidx.work.** {
    <init>(...);
}

# SoundNest UI & Spotify data layer
-keep class io.github.thake.soundnest.core.model.** { *; }
-keep class io.github.thake.soundnest.feature.lyrics.data.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *; }
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# ML Kit (the Jam QR scanner) creates its component registrars by reflection: without their
# no-argument constructors it fails to start and the scan crashes.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { *; }

# The relay's Cronet (before Android 14): CronetProvider finds the Play services provider by its
# class name, and zstd's decoder is reached from its native library by JNI.
-keep class com.google.android.gms.net.PlayServicesCronetProvider { *; }
-keep class com.google.android.gms.net.GmsCoreCronetProvider { *; }
-keep class com.squareup.zstd.** { *; }

# ONNX Runtime (AutoMix's beat model): its native library finds its Java classes, constructors and
# fields by name over JNI.
-keep class ai.onnxruntime.** { *; }
