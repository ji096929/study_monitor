# Study Guardian — keep Moshi models if minify enabled later
-keepclassmembers class com.studyguardian.data.model.** { *; }
-keepclassmembers class com.studyguardian.data.mqtt.** { *; }

# HiveMQ / Netty (if minify enabled)
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-keep class org.jctools.** { *; }
-dontwarn org.jctools.**
