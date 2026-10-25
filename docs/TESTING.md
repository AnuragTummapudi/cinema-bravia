# Testing and observed limits

## Local checks (no television required)

```sh
python3 -m unittest discover -s cinema-launcher/tests -p 'test_*.py' -v
remote/.venv/bin/python -m unittest discover -s remote/tests -p 'test_*.py' -v
node --check remote/static/app.js
python3 scripts/check_publication.py
```

After a launcher build, run the Java policy/cache fixtures:

```sh
zsh cinema-launcher/scripts/test_rules.sh
```

The host fixtures exercise the real production policy/cache code. `test_rules.sh` uses the Android SDK classes for compile-only platform types and a separately downloaded, checksum-verified org.json host-test dependency. Neither that jar nor the fixtures are packaged into Cinema.

For the public source preparation on 26 October 2026, a clean tracked-file export with no private movie imagery successfully built and signature-verified the Android APK and native Mac shell. The checks passed **8 catalogue tests, 10 remote security tests, 79 trailer-policy assertions, 19 cache/sort/dedup assertions and 52 tech-policy assertions**. JavaScript syntax and the publication file scan also passed. A clean build demonstrates reproducibility of the source, not the appearance of an asset-free installation or a new actual-TV acceptance run.

## Optional GitHub Actions

`.github/examples/checks.yml` is a ready-to-enable workflow for the host tests, clean Android build/policy fixtures and native Mac build. It pins the official setup actions by commit and requests read-only repository contents. To enable it, copy it to `.github/workflows/checks.yml` using an owner session authorized to manage workflows. It is a template in this distribution; no hosted CI result is claimed by the local validation above.

## Actual-device observations

Development used the Sony BRAVIA BF1, Android 12/API 31, Mali-G52 and 2.34 GiB RAM. Private diagnostics and raw device evidence are intentionally excluded from this public repository.

- Physical Sony remote: character directions, feed navigation and Hollywood/Telugu/filter switching were confirmed by the owner.
- Movie links: provider activities opened on the BF1. The owner confirmed the correct Ala Vaikunthapurramuloo Prime Video page requires subscription/rental. Doraemon and Shin Chan use JioHotstar series destinations; entitlements/language determine the final result.
- Feed videos: selected Telugu, Hollywood and official YC videos opened in YouTube TV with matching media titles; return selection and process-recreation restoration were exercised. This does not certify every discovered upload.
- Repository fixtures cover filtering, language ambiguity, duplicate versions, sorting, cache expiry, grouping and failure handling. On-device failure fixtures are separate instrumentation, not shipping code.
- Mac remote: navigation, app destinations, text search, volume/mute, media and recovery were tested during development. Inputs did not open this Sony's input menu through Remote v2; it is disabled with an explanation. Previous-track had no visible effect in the tested YouTube context.
- The remote redesign passed ten security tests and an isolated 21-control UI fixture. Actual-TV volume was observed at 20 → 19 → 20 with mute restored. Native full-screen and 390/1024/1440/1920-width layouts were inspected.
- Final screenshots were captured directly from the TV at 1920×1080 and the native Mac window at 2940×1846. No UI mockups stand in for those captures.

A persisted six-hour job is configured. The owner deferred the final reboot test; post-reboot feed/key persistence is not marked passed here. Debugging-disabled validation was also deferred while development continued. Do not infer those results from a successful build or paired connection.

## Device tests

The optional `cinema-launcher/tv-tests/` instrumentation APK contains isolated repository failure fixtures. Read its source and obtain the TV owner's approval before running it: it temporarily exercises app cache/preferences and requires the same development signing identity as Cinema. It must not run on unattended production devices.
