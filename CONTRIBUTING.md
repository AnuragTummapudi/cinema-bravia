# Contributing

Keep changes small and explain the visible behavior or reliability issue they address. Include reproduction steps and relevant tests. For visual changes, include screenshots and test D-pad traversal, focus restoration, reduced motion and a compact Mac window where applicable.

Run the host checks in `docs/TESTING.md`. Never use an emulator result as proof of BRAVIA behavior; identify the actual hardware, OS and context for device observations. Do not run device-changing tests without the TV owner's permission.

Keep credentials, personal logs, package inventories, backups and third-party movie assets out of commits. Preserve upstream, font and icon notices. Use fixtures for API errors and classifier behavior; don't replace the live feeds with a static list.

System Home changes, debloat automation and broad network exposure are outside the current scope. Discuss architectural changes before adding frameworks, permanent services or substantial runtime dependencies.
