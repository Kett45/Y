# Add project specific ProGuard / R8 rules here.
-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation,*Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep all application classes and data models to prevent reflection/serialization crashes
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }

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
