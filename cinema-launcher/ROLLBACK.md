# Safety and rollback

This public Cinema app is a standalone preview. It does not declare the Android HOME category, change default Home, disable setupwraith or manage system packages. Press the physical remote's Home button to return to the existing system Home.

If Cinema is unresponsive, stop only the user app:

```sh
adb -s <tv-ip>:5555 shell am force-stop com.tummapudi.cinema
```

To undo custom content, remove or replace only Cinema's custom catalogue/art in its own external-files directory and reload. Keep backups and Mac remote credentials untouched. To disable live feeds and their scheduled job, use Sources → Disconnect and clear trailer metadata. Revoke the Google key separately if needed.

To remove Cinema, uninstall this user app through TV Settings → Apps. Do not apply these instructions to system apps. Uninstalling Cinema deletes its own saved settings/key/cache; export your custom catalogue/art first if you want to preserve them.

For source rollback, keep a known-good signed APK you built yourself. An update must retain the package name and signing key; version downgrade restrictions still apply. This repository does not publish the owner's development signing key or private archival rollback APKs.

Sony BF1-specific default-Home and debloat procedures need separate approval and recovery preparation. CleanYourTV's model notes warn that setupwraith may affect saved Wi-Fi and Sony services may re-enable packages. No generic script here switches Home or disables those services.

After development, revoke debugging authorizations, disable USB/network/wireless debugging as applicable, restart normally and verify debugging ports are closed. Confirm Remote v2 still reconnects and controls the TV. Keep debugging on the trusted LAN only while it is required; never forward its ports.
