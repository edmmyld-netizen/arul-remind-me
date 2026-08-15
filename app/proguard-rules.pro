# Room / Compose work with the default rules shipped by their own AARs.
# Keep the Room generated database implementation names readable in crash logs.
-keepnames class * extends androidx.room.RoomDatabase
-dontwarn org.jetbrains.annotations.**
