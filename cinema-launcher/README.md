# Cinema — native Android TV

A lightweight Java/View entertainment app for Android TV 8+ (API 26+), tested on Sony BRAVIA BF1 / Android 12. No Compose, AndroidX or WebView runtime is required.

This public build launches independently from the TV's apps list. It does **not** register as default Home or modify Sony/Google TV packages. The movie artwork shown in the root README is a personal setup; supply your own licensed assets as described in [asset setup](../docs/ASSETS.md).

## Build

Requires macOS/zsh, JDK 17, Android SDK **platform 36.1** and **build-tools 36.1.0**, plus `zip` and `shasum`. On macOS the defaults use the Android Studio SDK location and Homebrew JDK; override `ANDROID_HOME` and `JAVA_HOME` for other locations.

```sh
cd cinema-launcher
# Set these only if your tools are elsewhere:
# export ANDROID_HOME=/path/to/android-sdk
# export JAVA_HOME=/path/to/jdk-17
zsh build.sh
```

The script uses official aapt2/javac/d8/zipalign/apksigner tools. It creates `dist/cinema-preview.apk` and its checksum. A local Android debug keystore is generated if missing; keys are never distributed. For an existing personal installation, retain the same local signing key to update without losing data. This is a development build, not a store release.

Install on a TV you own and have authorized for ADB on a trusted local network:

```sh
adb connect <tv-ip>:5555
adb -s <tv-ip>:5555 install -r --no-incremental dist/cinema-preview.apk
adb -s <tv-ip>:5555 shell am start -n com.tummapudi.cinema/.MainActivity
```

Accept the TV-side debugging authorization when required. Do not expose ADB publicly. [Rollback and development shutdown](ROLLBACK.md) are separate from everyday Remote v2 operation.

## Navigate

- Home scene: Left/Right selects an app; OK opens it. Up reaches favourites/search/settings. Down enters My favourites.
- Characters: Left/Right selects a character. OK opens its labelled OTT destination. Up reaches collections/search/home; Down returns to the selection. Down from a character opens trailers.
- Trailers: Left/Right browses. OK opens that exact YouTube video. Up/Back restores the favourite. Down reaches Refresh/Sources/category; the top-right dropdown selects Hollywood or Telugu.
- Tech: Down from a trailer to Refresh, then Down again enters Updates from the internet. Up/Back returns to trailers. Down → Right → Right → OK opens the tech filter.
- System Home keeps the existing TV behavior. Back closes panels or exits the standalone app from its home scene.

Motion, artwork visibility, larger text, catalog reload and favourite dock apps are configurable in Cinema settings. All important interactions use a D-pad and OK; no long press, touch or mouse is required.

## Configure

`catalog.example.json` mirrors the bundled catalogue. It contains collections, films, favourites, artwork paths and HTTPS provider links. Validate an edited copy:

```sh
python3 scripts/validate_catalog.py catalog.example.json
adb -s <tv-ip>:5555 push catalog.example.json /sdcard/Android/data/com.tummapudi.cinema/files/catalog.json
```

Create matching `art/`, `characters/` and `home/` folders under that app-specific directory when using custom assets; choose Settings → Reload custom catalog. Missing artwork retains navigation/text. Invalid catalogues fall back to the bundle. Limits are five collections, 100 films and 512 KiB JSON.

Choose up to five installed dock apps; All Apps remains available. Provider links depend on installed app version, region, account and subscription. A resolved intent is not proof of immediate playback. The sample movie catalogue is a configurable set of shortcuts, while **video feeds are discovered live**, not hardcoded.

[Live trailer setup](TRAILERS.md) · [Tech feed](TECH-UPDATES.md) · [Testing](../docs/TESTING.md) · [CleanYourTV attribution](LICENSE.CleanYourTV)
