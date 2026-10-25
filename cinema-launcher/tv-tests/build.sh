#!/bin/zsh
set -eu
cd "${0:A:h}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JDK="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
BT="$SDK/build-tools/36.1.0"
mkdir -p build/classes build/dex
"$BT/aapt2" link -I "$SDK/platforms/android-36.1/android.jar" --manifest AndroidManifest.xml -o build/base.apk
"$JDK/bin/javac" --release 8 -cp "$SDK/platforms/android-36.1/android.jar:../build/classes" -d build/classes src/com/tummapudi/cinema/TrailerChecks.java
"$JDK/bin/jar" cf build/classes.jar -C build/classes .
"$BT/d8" --release --lib "$SDK/platforms/android-36.1/android.jar" --classpath ../build/classes.jar --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out build/cinema-checks.apk build/aligned.apk
