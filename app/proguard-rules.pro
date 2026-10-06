# Added by Athar (Phase 10)
# Hilt/Dagger, Firebase and Compose ship their own consumer rules, so this file
# only covers what this app does that a shrinker can't see.

# Enum names are persisted in Realtime Database as strings and parsed back with
# valueOf(); keep them so a renamed constant can never break stored data.
-keepclassmembers enum com.athar.ludo3d.domain.model.** { *; }
-keepclassmembers enum com.athar.ludo3d.domain.engine.** { *; }

# Readable crash reports from release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
