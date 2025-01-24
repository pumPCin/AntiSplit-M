-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn android.content.res.**
-dontwarn org.**

-keep class org.apache.commons.compress.** { *; }
-keep class android.content.** { *; }
-keep class androidx.core.content.FileProvider { *; }
-keep class **.model.** { *; }
-keep class **.models.** { *; }
-keepnames class * { *; }
-keepnames interface * { *; }
-keepnames enum * { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile