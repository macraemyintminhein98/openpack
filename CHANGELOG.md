# Changelog

## Unreleased
- Android app (`android/`), test build 0.3.0: the library in an app, a built-in pack, sharing to
  nearby phones over Wi-Fi or hotspot with a QR code, opening a new signed pack from a .zip, a notice editor.
  Built to keep going unattended: the app hands out its own installer and the pack to visitors, phones
  update each other (only missing files, resumable, signature checked first), sharing carries on with
  the screen off, Android 5+, its own Burmese font, and an optional spare signing key.
  No Google services, no analytics. See `android/README.md`.
- Old and cheap phones: the app installs on Android 4.4+ (was 5.0), any processor, with old-style and
  new-style APK signatures; a release build signed with your own keystore; a free-space check before
  copying; Android lint stops the build on any call Android 4.4 lacks.
- The page no longer needs `fetch` or `Promise` (plain XMLHttpRequest), so never-updated browsers on
  Android 4.4 to 6 can load it. A test fails if a post-2013 browser feature is used. Lesson tabs wrap
  on 240-pixel screens. `OP_OLD=1` runs the browser suite with newer features removed.
- Share page: when the server is the Android app, visitors see "Get the app" and "Get the library as one file".
- Hardening: `manifest.js` must now be an exact copy of the signed manifest. Before, text placed before
  or after the manifest inside that file was not checked (Node and Go checks both fixed, tests added).

## 0.6.0
- Hub notice board: the hub owner writes `notices.txt`; visitors read it in the portal.
- Tested across Chrome, WebKit and Firefox with phone profiles, over a network address; ARM
  builds run under emulation; Burmese text checker. Results in `docs/TESTING.md`.
- Fact-check fixes to two lessons; rollout documents.

## 0.5.0
- Picture lessons (`kind: "lesson"`): step-by-step guides with drawings, Why and Tips tabs, sources and a review flag.
- Nine standard lessons in `lessons/`, added to every pack automatically (opt out with `"lessons": false`):
  water, heat, bleeding, burns, snake bite, flood and cyclone, emergency bag, signalling, cooking fires.
  All Burmese + English, all marked not yet expert-reviewed.
- New "Emergency skills" category.

## 0.4.0
- Search understands Zawgyi typing, ignores spaces in Burmese, falls back to syllables, ranks title matches first.
- `scripts/weekly-release.sh`: one-command build, sign, verify, bundle, change note, checksum, pruning.
- `scripts/pin-source.js`: pin SHA-256 for http sources (uses the publisher's `.sha256` when present).
- GitHub CI and tag-release workflows; issue and pull-request templates.
- Docs: Burmese README, content policy draft, device-test checklist, sources, roadmap, Burmese review sheet.
- Shell scripts pass shellcheck.

## 0.3.0
- New portal: tiles, one-button items, settings (language, text size, theme), help, bundled Burmese font.
- Labels in one file (`portal/strings.js`); per-pack categories, label overrides, default language.

## 0.2.0
- Go single-file server, folder (file://) mode, QR sharing, bundle with start scripts.

## 0.1.0
- Pack builder, ed25519 signing, verification, licence gate, Zawgyi check.
