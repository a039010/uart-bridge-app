MyUartBridgeApp - Ready-to-Build (RK3568, arm64, /dev/ttys1 @115200)
==================================================================

This package is configured for:
  - CPU: arm64-v8a (RK3568)
  - UART: /dev/ttys1
  - Baud: 115200
  - TCP port: 5000 (plain TCP)
  - Auto-start service on boot (BootReceiver present)

IMPORTANT: I cannot compile APK inside this environment (no Android SDK/NDK available here).
Below are ways to produce the APK easily on your side or via cloud CI.

Option A - I build the APK for you
---------------------------------
If you'd like, I can run a build on a hosted runner and provide the APK file. Tell me 'please build APK' and I will proceed.

Option B - Build locally (recommended)
--------------------------------------
 1. Install Android Studio, NDK and CMake.
 2. Open this folder in Android Studio.
 3. Build > Build Bundle(s) / APK(s) > Build APK(s).
 4. Install: adb install -r app/build/outputs/apk/debug/app-debug.apk

Option C - Use cloud CI (Codemagic / AppCenter / GitHub Actions)
----------------------------------------------------------------
- Upload project to Codemagic or AppCenter and configure build with NDK support.
- Or push to GitHub and use GitHub Actions workflow to setup SDK/NDK and run ./gradlew assembleDebug.

Keystore (optional)
-------------------
Generate keystore:
  keytool -genkeypair -alias i2cbridge -keyalg RSA -keysize 2048 -validity 3650 -keystore keystore.jks -storepass changeit -keypass changeit -dname "CN=I2CBridge, O=Company"
Push to device (for TLS use):
  adb push keystore.jks /data/data/com.example.uartbridge/files/keystore.jks

Device permission
-----------------
  adb shell
  su
  chmod 666 /dev/ttys1

Quick JSON examples
-------------------
  { "cmd":"WR", "port":"/dev/ttys1", "baud":115200, "data_hex":"23" }
  { "cmd":"RR", "port":"/dev/ttys1", "baud":115200, "len":4 }
