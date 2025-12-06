# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in 'proguard-rules.pro' / 'lib/proguard-rules.pro' of the
# library dependencies.

# Suppress warnings for javax.sound classes (desktop-only artifacts included in dependencies)
-dontwarn javax.sound.sampled.**
-dontwarn javax.sound.**
-dontwarn org.tritonus.share.sampled.**
-dontwarn io.github.jseproject.**