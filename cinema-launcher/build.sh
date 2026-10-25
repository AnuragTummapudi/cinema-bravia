#!/bin/zsh
set -eu
cd "${0:A:h}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JDK="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
BT="$SDK/build-tools/36.1.0"
ANDROID="$SDK/platforms/android-36.1/android.jar"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
if [[ ! -f "$ANDROID" || ! -x "$BT/aapt2" || ! -x "$JDK/bin/javac" ]]; then
  print -u2 'Install JDK 17, Android SDK platform 36.1 and build-tools 36.1.0; set JAVA_HOME / ANDROID_HOME if needed.'
  exit 1
fi
KEYSTORE="${CINEMA_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
  mkdir -p "${KEYSTORE:h}"
  (umask 077; "$JDK/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000)
fi
mkdir -p build/classes build/dex dist
rm -rf build/classes/*(N) build/dex/*(N)
"$BT/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$BT/aapt2" link -I "$ANDROID" --manifest app/src/main/AndroidManifest.xml --java build/generated -A app/src/main/assets -o build/base.apk build/resources.zip --version-code 7 --version-name 1.6
find app/src/main/java build/generated -name '*.java' > build/sources.txt
"$JDK/bin/javac" --release 8 -encoding UTF-8 -cp "$ANDROID" -d build/classes @build/sources.txt
"$JDK/bin/jar" cf build/classes.jar -C build/classes .
"$BT/d8" --release --lib "$ANDROID" --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# Local preview uses the Mac's existing development key. Never check keys into source control.
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out dist/cinema-preview.apk build/aligned.apk
"$BT/apksigner" verify --verbose dist/cinema-preview.apk
shasum -a 256 dist/cinema-preview.apk > dist/SHA256SUMS
