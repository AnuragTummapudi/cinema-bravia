#!/bin/zsh
set -eu
cd "${0:A:h}"
APP="BRAVIA Remote.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
iconutil -c icns native/AppIcon.iconset -o "$APP/Contents/Resources/AppIcon.icns"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>BRAVIARemote</string>
<key>CFBundleIdentifier</key><string>local.bravia.remote</string>
<key>CFBundleName</key><string>BRAVIA Remote</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleShortVersionString</key><string>1.1</string>
<key>CFBundleVersion</key><string>2</string>
<key>CFBundleIconFile</key><string>AppIcon</string>
<key>NSHighResolutionCapable</key><true/>
<key>NSAppTransportSecurity</key><dict><key>NSAllowsLocalNetworking</key><true/></dict>
<key>NSLocalNetworkUsageDescription</key><string>Connect to your Sony television on your trusted Wi-Fi.</string>
</dict></plist>
PLIST
xcrun swiftc -swift-version 5 -O -module-cache-path /tmp/bravia-remote-swift-cache native/main.swift -o "$APP/Contents/MacOS/BRAVIARemote" -framework AppKit -framework WebKit
xattr -dr com.apple.FinderInfo "$APP" 2>/dev/null || true
xattr -dr com.apple.ResourceFork "$APP" 2>/dev/null || true
codesign --force --sign - "$APP"
codesign --verify --deep --strict "$APP"
print "Built $PWD/$APP"
