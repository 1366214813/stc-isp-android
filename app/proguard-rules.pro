# Keep WCH UART library
-keep class cn.wch.uartlib.** { *; }

# Keep SerialBridge for JavaScript interface
-keep class cn.wch.stcisp.SerialBridge { *; }
-keepclassmembers class cn.wch.stcisp.SerialBridge {
    @android.webkit.JavascriptInterface <methods>;
}
