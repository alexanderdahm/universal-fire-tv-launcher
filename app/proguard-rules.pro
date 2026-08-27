# The release build does not use minification (see app/build.gradle.kts), so this
# file exists only so the project is complete and ready if shrinking is enabled.

# Keep the launcher entry point.
-keep class com.example.universallauncher.MainActivity { *; }

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
