# Sunshine ProGuard Rules

# Keep line numbers for crash reporting stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room - keep entities and DAOs
-keep class com.sunshine.app.data.local.database.entities.** { *; }
-keep class com.sunshine.app.data.local.database.*Dao { *; }

# Ktor + kotlinx.serialization
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.sunshine.app.data.remote.elevation.ElevationApi$* {
    *;
}
-keepnames class kotlinx.serialization.internal.** { *; }

# Keep @Serializable data classes
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** serializer(...);
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class com.sunshine.app.di.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# commons-suncalc
-keep class org.shredzone.commons.suncalc.** { *; }

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
