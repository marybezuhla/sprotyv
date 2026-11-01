# Shrinking only: unused code is removed; nothing is renamed, so stack traces and reviews stay readable.
-dontobfuscate

# SQLCipher: called from its native library by name
-keep class net.zetetic.** { *; }
-keepclassmembers class * { native <methods>; }

# Mapsforge reads its render themes and draws with classes found at run time; keep what it uses,
# but let the online tile download classes (never used: release 1.0 has no internet) be removed
-dontwarn com.caverock.androidsvg.**
