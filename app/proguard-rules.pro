# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepclassmembers class com.example.ui.screens.child.youtube.YouTubeShieldJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.example.data.model.** { *; }
-keep class com.example.data.remote.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
