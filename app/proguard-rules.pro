# Add project specific ProGuard rules here.
# You can use the wildcard characters * and ** to specify groups of
# class names or method names.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Suppress warnings for javax.sound.sampled classes which are not available on Android
# These are referenced by the jspeex library (io.github.jseproject:jse-spi-speex)
-dontwarn javax.sound.sampled.**

# Suppress warnings for other classes referenced in the error logs
-dontwarn org.tritonus.share.sampled.**
