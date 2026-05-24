# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# AndroidX Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# App data classes
-keep class com.njfu.schedule.bean.** { *; }
-keep class com.njfu.schedule.njfu.NjfuImporter$** { *; }
