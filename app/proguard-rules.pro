# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firebase Rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep Firebase Database model classes
-keep class com.spiritwisestudios.inkrollers.PlayerState { *; }
-keep class com.spiritwisestudios.inkrollers.model.** { *; }
-keepclassmembers class com.spiritwisestudios.inkrollers.PlayerState {
    <init>();
    public <methods>;
    public <fields>;
}
-keepclassmembers class com.spiritwisestudios.inkrollers.model.** {
    <init>();
    public <methods>;
    public <fields>;
}

# Keep all classes with Firebase annotations
-keep @com.google.firebase.database.IgnoreExtraProperties class ** { *; }
-keepclassmembers class ** {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
}

# Keep all constructors for Firebase serialization
-keepclassmembers class * {
    <init>();
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep classes that use reflection (common with Firebase)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Firebase Crashlytics
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

# OkHttp (used by Firebase)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep your custom classes from obfuscation if they interact with Firebase
-keep class com.spiritwisestudios.inkrollers.** { *; } 