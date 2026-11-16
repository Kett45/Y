# Add project specific ProGuard / R8 rules here.
-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation,*Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep only serialized Firestore/JSON models, local cache DTOs, GitHub sync DTOs, and BuildConfig
# so R8 can optimize, shrink, and inline Compose UI, ViewModel, and Repository hot paths.
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.cache.CachedClassroomSnapshot { *; }
-keep class com.example.data.cache.LocalUserPreferences { *; }
-keep class com.example.data.sync.** { *; }
-keep class com.example.BuildConfig { *; }

# Keep Firebase, Firestore, Auth, Protobuf, and gRPC reflection targets
-keep @androidx.annotation.Keep class * { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class io.grpc.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

-dontwarn com.google.firebase.**
-dontwarn io.grpc.**
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**
