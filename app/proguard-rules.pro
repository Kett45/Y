# Add project specific ProGuard / R8 rules here.
-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation,*Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep all Firestore data models and their constructors/getters/setters for reflection deserialization
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    *;
}

# Keep local cache and sync manifest models
-keep class com.example.data.cache.** { *; }
-keep class com.example.data.sync.** { *; }

# Keep Firebase Firestore & Auth reflection targets
-keep @androidx.annotation.Keep class * { *; }
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**
