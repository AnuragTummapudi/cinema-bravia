# Third-party notices

## Code and dependencies

- Cinema follows the lightweight Java/View launcher approach from **CleanYourTV** by Gathin. Preserve `cinema-launcher/LICENSE.CleanYourTV` with derivative code. Upstream: https://github.com/Gathin23/cleanyourtv (MIT).
- The Mac remote uses `androidtvremote2` (MIT), pinned by commit in `remote/requirements.txt`, and aiohttp (Apache-2.0). Dependencies are installed separately, not vendored here. Their licenses continue to apply.
- Phosphor control icons: MIT; full notice in `remote/static/PHOSPHOR-LICENSE.txt`.
- Geist fonts: SIL Open Font License 1.1; notices in `remote/static/GEIST-LICENSE.txt` and `cinema-launcher/app/src/main/assets/fonts/OFL.txt`.
- The remote application icon is original project vector geometry, with source in `remote/static/icon.svg` and renderer in `remote/native/make_icon.py`.

## Brand marks and imagery

Service marks identify the apps that can be opened. They are not a grant of trademark rights or an endorsement. Source/treatment records: `remote/ASSETS.json` and `cinema-launcher/dock-brand-sources.json`.

Movie/character wallpaper, cutouts and film backdrops used in the personal demo are excluded from the source distribution. Their original references remain in `cinema-launcher/ARTWORK-SOURCES.md` and the character source records. Attribution alone does not establish redistribution rights. Bring artwork you have permission to use; see `docs/ASSETS.md`.

Screenshots document the author's personal implementation. They contain third-party imagery/marks and are not offered as an open asset pack. The MIT license does not cover those third-party rights.

TMDB metadata/artwork references retain the notice: this product uses the TMDB API but is not endorsed or certified by TMDB. This repository contains no TMDB API credential or runtime TMDB client. Live YouTube content remains subject to YouTube's terms, API policies and playback restrictions.
