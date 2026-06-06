# ============================================================
# نبض (Nabd) — ProGuard Rules
# ============================================================

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# --- Kotlin ---
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Google LiteRT-LM (Strict Reflection Protection) ---
-keep class com.google.ai.edge.litertlm.** {
    <fields>;
    <methods>;
}
-keep class com.google.ai.edge.litertlm.**$* {
    <fields>;
    <methods>;
}
-keepnames class com.google.ai.edge.litertlm.** { *; }
-keepnames class com.google.ai.edge.litertlm.**$* { *; }
-dontwarn com.google.ai.edge.litertlm.**

# --- Shaded / CodeGen dependencies (fix for R8 missing classes) ---
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# --- Google ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- MediaPipe ---
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# --- TensorFlow Lite ---
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# --- Markwon ---
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# --- App Data Classes (Room entities) ---
-keep class com.example.localqwen.data.** { *; }

# --- App Models used in JSON serialization ---
-keep class com.example.localqwen.chat.ChatMessage { *; }
-keep class com.example.localqwen.chat.ChatSession { *; }
-keep class com.example.localqwen.chat.Role { *; }
-keep class com.example.localqwen.document.LocalDocument { *; }

# --- ViewModels ---
-keep class com.example.localqwen.viewmodel.** { *; }

# --- Prevent stripping of Parcelable/Serializable ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# --- AndroidX Lifecycle ---
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# --- General Android ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
