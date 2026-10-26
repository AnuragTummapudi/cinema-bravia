# BRAVIA Remote for Mac

A local AppKit/WKWebView remote with a graphite UI, tactile D-pad, authentic app marks and Android TV Remote v2 TLS. It controls the TV independently of ADB. No software is installed on the television.

## Run

Requires macOS, Python 3.10+ and network access to install the pinned dependencies. The native window additionally needs Xcode Command Line Tools/Swift. The Python backend/browser interface can also run without building the native wrapper.

```sh
cd remote
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python server.py --tv <your-private-tv-ip>
```

Open http://127.0.0.1:8765. The TV must be awake and on the same trusted local network with Android TV Remote Service available. On first launch open Connection settings, enter its private IPv4 address, choose Pair television and enter the six-character TV code. This is **not** an ADB wireless-debugging code.

Pairing credentials and the pinned TV identity are saved in the ignored `.state/` directory. The next launch reconnects automatically. If DHCP changes the address, update Connection settings and follow pairing as needed. No continuous mDNS rediscovery is implemented.

## Native window

```sh
zsh build-mac-app.sh
open 'BRAVIA Remote.app'
```

Keep the app beside `server.py`, `static/` and `.venv/`. It starts/reuses the local backend. If it owns that process, quitting stops it; a separately started development server is left running. The app can be kept in the Dock without moving its files.

**Control–Command–F** toggles native full screen, **Command–R** reloads, **Command–Q** quits. A clean ICNS is generated from the included original iconset. Signing is local ad-hoc, not Apple notarization. A synced folder/Finder may add bundle metadata after signing; build from an ordinary local development directory when distributing a signed shell.

The layout fills desktop widths, uses 32px/48px gutters and stacks controls in compact windows. Local Geist and Phosphor assets have included licenses. No 3D engine, remote font fetch or continuous animation is used. [Asset sources](ASSETS.json).

## Controls

| Key | Action |
| --- | --- |
| Arrows / Enter | D-pad / OK |
| Escape or Backspace | Back |
| H or Home | Home |
| Space | Play/pause |
| + / = and - | Volume up/down |
| M | Mute |

Text fields keep their normal typing behavior. Tab/Enter/Space retain native button activation. To send text, first select a text field on the TV, then use Text to TV → Send. Text is inserted; automatic search/form submission is not promised. Support varies by application/keyboard/password field. The backend does not retain typed content.

Netflix, YouTube, Prime Video and Sony LIV shortcuts target the known TV apps. Media key effects depend on the current app. **Inputs is disabled**: Remote v2 did not open the tested Sony's input menu. Previous-track had no visible effect in the tested YouTube context. Use the physical Sony remote for Inputs.

“Sent” means a protocol command was submitted, not that every TV action was acknowledged. Volume/app values are TV reports. Sign-in, playback and subscription access are not guaranteed by launching an app.

## Security and tests

Server binds only to **127.0.0.1:8765**. It validates Host/Origin, requires a per-run mutation token, accepts private-LAN TV targets and allowlists commands/links. Pairing pins the public key learned through the TV code and checks it after reconnection. Keys are owner-only files, not Keychain or encrypted-at-rest storage. Keep `.state/` private.

```sh
.venv/bin/python -m unittest discover -s tests -p 'test_*.py' -v
```

Ten tests cover origins/session tokens, disconnected-command behavior, allowlists, credential non-serving, text non-retention, expiry, changed identity and recovery. [Actual-device observations](../docs/TESTING.md) are separate from fixture tests.

The remote never falls back to ADB. When TV development is finished, close debugging access and verify Remote v2 still works. [Security](../SECURITY.md) describes the boundaries and reporting process.
