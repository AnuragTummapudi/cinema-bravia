#!/bin/zsh
set -eu
cd "${0:A:h}/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
JDK="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
ANDROID="$SDK/platforms/android-36.1/android.jar"
JSON_JAR=build/test-libs/json.jar
JSON_SHA=7dd2de1533e766e2b6c07d8194da85145ad313a167a4760050f5c9b6839604fe
[[ -d build/classes ]] || { print -u2 'Build Cinema first with zsh build.sh'; exit 1; }
mkdir -p build/test-libs build/test-classes
if [[ ! -f "$JSON_JAR" ]]; then
  curl --fail --location --silent --show-error --max-time 60 https://repo.maven.apache.org/maven2/org/json/json/20260814/json-20260814.jar -o "$JSON_JAR"
fi
print "$JSON_SHA  $JSON_JAR" | shasum -a 256 -c -
"$JDK/bin/javac" --release 8 -cp "build/classes:$ANDROID:$JSON_JAR" -d build/test-classes tests/java/*.java
for test_name in TrailerRulesTest TrailerCacheTest TechRulesTest; do
  "$JDK/bin/java" -cp "build/test-classes:build/classes:$JSON_JAR:$ANDROID" "com.tummapudi.cinema.$test_name"
done
