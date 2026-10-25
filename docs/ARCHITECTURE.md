# Architecture

## TV application

`cinema-launcher/app/src/main/java/com/tummapudi/cinema/` contains a Java/View app with no AndroidX, Compose or WebView runtime dependency.

- `MainActivity`, `SceneView` and `CharacterStage` own sections, composition and focus.
- `Catalog` validates local film/provider configuration; `Artwork` loads bundled or app-specific external assets off the UI thread.
- `TrailerRules` and `TechRules` classify uploads and conservatively group duplicates.
- `TrailerRepository` is shared by the UI and `TrailerRefreshJob`. It resolves channel uploads, fetches playlist items, batches video details, sorts by publication timestamp and caches atomically.
- `TrailerCredentials` owns the protected key form and Keystore encryption.
- `TrailerImages` handles bounded disk/bitmap caches. `TrailerSection` renders the horizontal native carousels and restores selection by video ID.

The source allowlist is configuration, not a static list of videos. Existing channels can publish new matching uploads without an app update. Refresh is approximate: six-hour interval/one-hour flex, network constrained, coalesced and subject to Android scheduling. It does not wake the TV or run a permanent service.

Thumbnails decode to at most 704×396 pixels. Disk cache is 64 MiB; each of the two feed sections owns an 8 MiB bitmap LRU. Distant cards release references. Foreground artwork and finite focus transitions avoid continuous rendering.

## Mac remote

`remote/server.py` exposes a small aiohttp API on localhost. It validates session/origin boundaries and sends allowlisted requests through androidtvremote2. The static HTML/CSS/JS UI uses local logos/fonts/icons. `remote/native/main.swift` provides AppKit menus, a resizable/full-screen WKWebView and backend lifecycle management.

The server persists the TV certificate identity learned during pairing and reconnects with bounded backoff. There is no ADB code path. The Mac can be off while the TV's feeds continue to work.

## Boundaries

No server is required for live feeds: the TV calls YouTube directly with its owner-provided key. YouTube TV handles the exact video's playback, ads, sign-in and restrictions. Streaming provider intents can lead to playback, a title page or an access prompt. Inputs/previous-track behavior varies by TV and playback context; unsupported behavior is documented rather than simulated.
