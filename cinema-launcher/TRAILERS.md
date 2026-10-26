# Live Hollywood and Telugu trailers

Cinema queries public uploads on the TV through YouTube Data API v3. No static video list, key, server or embedded player is bundled.

## Configure a key

1. In Google Cloud Console enable **YouTube Data API v3** in a dedicated project.
2. Create an API key, restrict its APIs to YouTube Data API v3, and monitor project quota. Keep the key out of source, shell history, chat and screenshots.
3. In Cinema open Sources → YouTube API key (also available from settings). Use the protected TV field or the paired Mac remote's text entry. Review the terms/privacy notice, then connect.
4. The form prevents screenshots, input learning/autofill and saved form state. The key is stored in private app files using Android Keystore-backed AES-GCM with Android backup disabled.

Requests send the key in an HTTPS header. It is not a backend-held secret: Keystore protects storage, not a compromised running TV. Sources → Disconnect and clear trailer metadata removes the local key, cache metadata and job; revoke the key separately in Google Cloud if necessary. Thumbnails remain subject to expiry and size limits. No YouTube account, watch history or subscriptions are requested.

## Sources and policy

`app/src/main/assets/trailer-sources.json` pins publisher channel IDs, uploads playlist IDs, language/category policy and ownership-reference URLs. It contains 16 Telugu publishers and six Hollywood studios, alongside the tech sources. Names/badges alone are not trusted. [Source notes](../docs/SOURCES.md) describe the allowlist.

The repository resolves `channels.list`, polls uploads via `playlistItems.list` and batches candidate/current details via `videos.list`. It avoids routine search requests. Up to 30 items per movie category are retained within a rolling 90-day publication window. Dates are upload publication dates, not theatrical release dates.

Telugu includes Telugu-dubbed film trailers when there is Telugu-language evidence. Hollywood requires appropriate English-channel metadata and conservative film-origin filtering. Rules reject reactions, songs, interviews, fan edits, countdowns, livestreams and ambiguous posts; they cannot perfectly classify every future title. Deduplication preserves distinct teasers, trailers and numbered versions.

## Refresh, cache and playback

A shared network-constrained JobScheduler job uses an approximate six-hour interval and one-hour flex. Opening the app loads cache immediately and refreshes asynchronously when stale. Jobs/foreground refresh coalesce; errors back off. ETags/cursors reduce requests; manual refresh is rate limited and the local cap is 500 API calls per UTC day.

Metadata is atomically written. Partial failures retain still-valid cached items. Deleted/private or explicitly region-blocked videos are removed after successful revalidation. Stored YouTube metadata is revalidated or expires within 30 days. First-ever offline use has an honest setup/retry state.

Thumbnails use HTTPS YouTube image URLs, a shared 64 MiB disk cache and an 8 MiB bitmap LRU per feed section. Decodes are bounded to 704×396 pixels and nearby cards. No videos are downloaded or played inside Cinema.

OK sends the exact validated video ID in a package-targeted watch URL to YouTube TV, then tries `vnd.youtube` if necessary. Only after both fail does it offer an explicit app-only fallback. YouTube handles access, ads, account/age and playback restrictions. Selection and category scroll restore on return.

[Testing and limits](../docs/TESTING.md) · [YouTube API](https://developers.google.com/youtube/v3) · [YouTube terms](https://www.youtube.com/t/terms) · [Google privacy](https://policies.google.com/privacy)
