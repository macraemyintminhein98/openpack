# Changelog

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
