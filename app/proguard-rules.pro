-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * { @androidx.room.* <fields>; }

-keep,includedescriptorclasses class ** { @kotlinx.serialization.Serializable *; }
-keepclassmembers class ** { @kotlinx.serialization.Serializable <fields>; }

-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn org.slf4j.**

-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.Shizuku { *; }
-keep interface moe.shizuku.server.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

-keep class com.droid.dolphy.plugin.** { *; }
-keep class androidx.compose.material.icons.** { *; }
