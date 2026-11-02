# Shrinking only: unused code is removed; nothing is renamed, so stack traces and reviews stay readable.
-dontobfuscate

# SQLCipher: called from its native library by name
-keep class net.zetetic.** { *; }

