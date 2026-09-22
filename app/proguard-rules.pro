# The Xposed API is provided by the framework at runtime and is absent from the
# APK, so R8 must not treat the missing superclass as an error.
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.annotation.**

# The framework loads the entry class by the name written in java_init.list.
-keep class my.github.MrxSiN.modeevolved.ModeEvolvedModule { *; }
-adaptresourcefilecontents META-INF/xposed/java_init.list

# DexKit's native half reaches back into these classes and their members by name,
# which R8 cannot see. It is only used when a hooked class has been renamed.
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**
