# Security

## Boundaries

The remote binds only to **127.0.0.1:8765**. Do not expose it through a tunnel, reverse proxy, public listener or router port forwarding. It accepts private-LAN TV addresses, checks Host/Origin, uses a per-run mutation token and allowlists commands and app destinations. The native wrapper stays on that localhost origin.

Android TV Remote v2 is separate from ADB. Pairing uses an on-TV code, TLS and a saved TV public-key pin; changed identity requires re-pairing. Credentials live in `remote/.state/` with directory mode 0700 and file mode 0600. These are owner-only files, **not macOS Keychain or encrypted-at-rest storage**. A compromised Mac can access them. Never publish them.

Cinema stores the owner-provided YouTube key with Android Keystore-backed AES-GCM and disables Android backup. The setup window prevents screenshots and input learning/autofill. Requests send the key in an HTTPS header; it is not in the APK or repository. A compromised running TV can still access a locally used key. Restrict its Google API access to YouTube Data API v3, monitor quota, and rotate it when necessary.

Typed TV text is not retained by the backend. Neither component implements telemetry, a cloud control service, rooting, system-package management or hidden ADB fallback.

## Development access

ADB is needed to install or inspect the TV app, not to operate the permanent remote. Use it only on a trusted local network. After development, revoke debugging authorizations, disable all relevant debugging options, restart normally and verify both closed debugging ports and working Remote v2 controls. A timeout alone does not prove a port is closed.

Do not commit `.state`, `.env`, keystores, certificates, API keys, device dumps or backups. `scripts/check_publication.py` checks the proposed tracked files; it is a focused guard, not a full credential scanner or security audit.

## Reporting

Do not put keys, private addresses, device identifiers or private logs in a public issue. Use GitHub's private vulnerability reporting if enabled on this repository. For an exposed credential, revoke/rotate it before discussing the issue publicly.
