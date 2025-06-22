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

# Firebase Realtime Database
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
}

# Keep Firebase related classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepnames class com.google.firebase.database.** { *; }

# Keep all data classes and their fields for Firebase serialization
-keep class com.spiritwisestudios.inkrollers.GameSettings { *; }
-keep class com.spiritwisestudios.inkrollers.PaintAction { *; }
-keep class com.spiritwisestudios.inkrollers.PlayerState { *; }
-keep class com.spiritwisestudios.inkrollers.model.PlayerProfile { *; }
-keep class com.spiritwisestudios.inkrollers.ui.FriendDisplay { *; }

# Keep model classes and their constructors/methods
-keepclassmembers class com.spiritwisestudios.inkrollers.GameSettings {
    public <init>(...);
    public <methods>;
    public <fields>;
}

-keepclassmembers class com.spiritwisestudios.inkrollers.PaintAction {
    public <init>(...);
    public <methods>;
    public <fields>;
}

-keepclassmembers class com.spiritwisestudios.inkrollers.PlayerState {
    public <init>(...);
    public <methods>;
    public <fields>;
}

-keepclassmembers class com.spiritwisestudios.inkrollers.model.PlayerProfile {
    public <init>(...);
    public <methods>;
    public <fields>;
}

-keepclassmembers class com.spiritwisestudios.inkrollers.ui.FriendDisplay {
    public <init>(...);
    public <methods>;
    public <fields>;
}

# Keep all classes with @IgnoreExtraProperties annotation
-keep @com.google.firebase.database.IgnoreExtraProperties class * { *; }

# Keep Firebase ServerValue fields
-keepclassmembers class com.google.firebase.database.ServerValue {
    public static <fields>;
}

# Keep serialization attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Kotlin data classes
-keepclassmembers class * {
    @kotlin.jvm.JvmField public <fields>;
}

# Keep companion objects
-keepclassmembers class * {
    public static final ** Companion;
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep any classes that extend Serializable
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep color palette
-keep class com.spiritwisestudios.inkrollers.model.PlayerColorPalette { *; }

# Keep any classes that might be used via reflection
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName *;
}

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }

# General Android rules for compatibility
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep setters and getters for data binding and serialization
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

# Additional rules for Firebase Database specifically
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
}

-keep @com.google.firebase.database.IgnoreExtraProperties class *
-keepclassmembers @com.google.firebase.database.IgnoreExtraProperties class * {
    <init>();
}

# Baseline profile rules to prevent issues
-dontwarn androidx.profileinstaller.**
-keep class androidx.profileinstaller.** { *; }

# Benchmark rules
-dontwarn androidx.benchmark.**
-keep class androidx.benchmark.** { *; } 