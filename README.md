# OpenPack

Open-source offline content packs and local Wi-Fi boxes for places where data is
expensive, slow or unavailable. Myanmar-first, built to work anywhere.

Inspired by Cuba's *El Paquete Semanal* (a weekly bundle of content passed hand to hand)
and *SNET* (neighbourhood networks), but built on a different rule: **only content that
may legally be redistributed, no accounts, no chat, no logs.**

## Download

- **Just try it:** get `OpenPack-demo-bundle.zip` from the
  [latest release](https://github.com/macraemyintminhein98/openpack/releases/latest), unzip, and open
  `pack/index.html` in a browser. To share over Wi-Fi, run the `START-...` file for your device
  (see `START-HERE.txt` inside). The demo pack is signed with a throwaway demo key.
- **Server programs only** (Windows, Linux, Android/ARM): same release page.
- **Build your own packs:**

```bash
git clone https://github.com/macraemyintminhein98/openpack.git
cd openpack
npm test                                  # needs Node 18+ and Go 1.21+
node bin/openpack.js keygen keys          # once; keep keys/openpack.key secret
scripts/weekly-release.sh packs/example   # -> dist/OpenPack-example-<version>.zip
```

## How it works

```
 content sources ──► openpack build ──► signed pack ──► scripts/make-bundle.sh ──► one folder / .zip
                                                                                       │
                                                       USB stick, SD card or phone-to-phone copy
                                                                                       ▼
        ┌─────────────────────── any phone or cheap laptop ───────────────────────┐
        │ 1. Open pack/index.html in a browser: nothing to install                 │
        │ 2. Or run the one-file server: everyone on the same Wi-Fi or hotspot     │
        │    opens the address (or scans the QR code) and browses the library      │
        └──────────────────────────────────────────────────────────────────────────┘
```

No special hardware is needed, because spare parts like a Raspberry Pi are hard to get in
many places. The target devices are an old Android phone, a cheap Windows laptop, or any
Linux machine.

1. **Pack builder** (this repo, Node 18+, zero dependencies). Reads `pack.json`, rejects any
   item without a redistributable licence, fetches or copies the files, writes a
   `manifest.json` with SHA-256 hashes, and signs it with ed25519. It runs on your computer,
   not on the devices in the field.
2. **Portal** (`portal/`). Static offline web app in plain ES5 for old browsers. Burmese by
   default with English toggle, search, categories, licence and attribution on every item,
   and a "Share" button that shows a QR code. Works opened straight from a folder (`file://`)
   or served.
3. **Server** (`server/`). One small Go file (about 6 MB, about 10 MB of RAM in use) for
   Windows, Android (via Termux) and Linux. It re-verifies the signature and every file hash
   before serving, so a damaged or swapped USB stick is refused. No accounts, no request logs.
4. **Bundle** (`scripts/make-bundle.sh`, `bundle/`). Packs the pack, every server build,
   start scripts and bilingual instructions into one folder to copy to a USB stick or phone.
5. **Pi/router scripts** (`box/`). Optional, for a dedicated always-on hotspot box with a
   captive portal if you do have the hardware. Untested on hardware.

## What runs where

| Device | How | Needs |
|---|---|---|
| Any phone or laptop, even very old | Open `pack/index.html` in a browser | A browser. Nothing else. |
| Windows 10+ laptop (32-bit or 64-bit) | Double-click `START-WINDOWS.bat`, turn on Mobile hotspot | Nothing to install |
| Android 7+ phone | Termux app + `START-ANDROID.sh`, turn on the phone hotspot | The free Termux app |
| Linux laptop / mini PC | `sh START-LINUX.sh` | Nothing to install |
| Windows 7/8 or very old devices | Folder mode only (the server needs Windows 10+) | A browser |

Limits to know about:
- Folder mode shows the library to one person at a time. To serve many people at once, one
  device must run the server and act as the Wi-Fi hotspot.
- Windows shows the hotspot at `192.168.137.1`. Android hotspot addresses vary (often
  `192.168.43.1`); the server prints what it finds, and on Android 11+ it sometimes cannot,
  in which case the phone's hotspot settings show the address.
- Nobody is "redirected" to the portal automatically on these devices, because that needs
  root or a dedicated router. People open the address or scan the QR code. The `box/` scripts
  give you the automatic captive portal on supported hardware.
- A hotspot phone's battery drains while serving. Keep it charging.

## Quick start

```bash
git clone <this repo> && cd openpack
npm test                                   # Node tests + Go tests (needs Go 1.21+ once)

node bin/openpack.js keygen keys           # once; keep keys/openpack.key secret
node bin/openpack.js build packs/example --key keys/openpack.key
npm run build:server                       # cross-compiles the server for 6 platforms
bash scripts/make-bundle.sh dist/example-* keys/openpack.pub
# -> dist/OpenPack-example-<version>.zip: copy to a USB stick, SD card or phone

npm run e2e                                # optional: full build/verify/tamper/serve check
node bin/openpack.js serve dist/example-*  # quick local preview on your own machine
```

## Writing a pack

`packs/<name>/pack.json`:

```json
{
  "name": "weekly",
  "title": "Weekly Pack",
  "title_my": "အပတ်စဉ် ပက်ကေ့ချ်",
  "version": "auto",
  "items": [
    {
      "id": "wikipedia-my",
      "title": "Wikipedia (Burmese)",
      "category": "reference",
      "license": "CC-BY-SA-4.0",
      "attribution": "Wikipedia contributors",
      "source": { "type": "http", "url": "https://example.org/file.zim", "sha256": "optional but recommended" }
    }
  ]
}
```

- `version: "auto"` uses year.week (for example `2026.48`).
- Source types: `local` (file inside the pack folder) and `http` (https only, optional sha256 pin).
- Allowed licences: CC0, public-domain, CC-BY, CC-BY-SA, CC-BY-NC (flagged non-commercial),
  MIT, Apache-2.0, ODbL, GFDL, and `permission` (needs a `proof` note). See `lib/licenses.js`.

### Customising the portal screen (all optional)

```json
{
  "default_lang": "my",
  "categories": [
    { "id": "learn", "title": "Learn", "title_my": "သင်ယူရန်", "icon": "learn" }
  ],
  "ui": {
    "my": { "act_read": "ဖွင့်ရန်" },
    "en": { "act_read": "Open" }
  }
}
```

- `categories` sets the name, icon and order of the tiles on the home screen. Categories you
  do not list still appear, using the built-in names in `portal/strings.js` (or the raw id).
  Icons: `folder book learn info news heart leaf star play headphones apps globe image`.
- `ui` replaces single labels for this pack only. The key names are the ones in
  `portal/strings.js`. The builder rejects unknown keys, missing `{placeholders}` and Zawgyi.
- `default_lang` is the language people see before they change it (`my` or `en`).

Good sources to start with: [Kiwix](https://www.kiwix.org) (offline Wikipedia and more) and
[Kolibri](https://learningequality.org/kolibri/) (offline education). Check each file's
licence before adding it.

## Security model

- Every file is hashed; the manifest is signed. Boxes hold only the **public** key.
- `update-pack.sh` verifies before swapping in a new pack, so a swapped USB stick or a
  modified file is rejected.
- The box runs no accounts, no chat, no uplink, and nginx access logging is off.
- Keep the private key offline. Anyone with it can publish packs your boxes will trust.

## Item kinds

| kind | what it is | rules enforced at build time |
|---|---|---|
| `file` (default) | Any plain download | licence + attribution |
| `zim` | Kiwix archive (offline Wikipedia, Wiktionary, books). The box serves it with `kiwix-serve` at `/kiwix/` | must be a `.zim` file |
| `apk` | Android app shared offline (replaces SHAREit-style sideloading) | must pin `source.sha256`; copyleft licences need a `source_code` https link |

Copyleft licences (GPL, LGPL, AGPL, MPL) are accepted for apps only when `source_code`
points at the matching source, because redistributing the binary creates that obligation.
Check each app's published hash against the developer's release page before pinning it.

## Relationship to Project NOMAD

[Project NOMAD](https://github.com/crosstalk-solutions/project-nomad) (Apache-2.0) is
already a full offline knowledge server: Docker-based, bundling Kiwix, Kolibri, offline maps
and a local AI assistant, installed on Debian/Ubuntu x86 machines. OpenPack does not try to
replace it. The two solve different problems:

| | Project NOMAD | OpenPack |
|---|---|---|
| Updates | Download from the internet when online | Signed packs carried on USB, verified before install |
| Licence control | None enforced | Build refuses unlicensed or unlicensed-source items |
| Language | English UI | Burmese-first UI, Zawgyi check |
| Hardware | Wants a real PC (4 GB RAM minimum) | Targets a Pi or small router-class box |
| Access control | No built-in auth | Open hotspot by design, no accounts, no access logs |

If a box has a PC and internet access at install time, running NOMAD for Kolibri and maps
and using OpenPack for the weekly curated pack is a reasonable combination.

## Picture lessons (in every pack)

Step-by-step guides shown inside the portal: one simple drawing and one or two sentences per
step, big Next / Previous buttons, plus "Why" and "Tips" tabs. They work offline, from a
folder or a server, on old phones (no 3D, no video; each lesson is 10 to 14 KB).

The standard lessons live in `lessons/` and are **added to every pack automatically**, under
the "Emergency skills" tile. A pack can opt out with `"lessons": false` in `pack.json`.

| Lesson | Main sources |
|---|---|
| Make water safe to drink | US CDC, WHO, Eawag SODIS manual |
| Too hot: heat exhaustion and heatstroke | NHS, Mayo Clinic |
| Stop heavy bleeding | British and Australian Red Cross, St John Ambulance |
| Burns and scalds | British Red Cross, NHS |
| Snake bite | WHO, PAHO, Mayo Clinic |
| Before a flood or cyclone | IFRC, UNICEF Myanmar, US CDC |
| Pack an emergency bag | UNICEF Myanmar, IFRC |
| Signal for help | State of Utah, Colorado Parks and Wildlife |
| Safe cooking fires | WHO, UNICEF |

**These lessons were written by an AI from the sources above and have not been checked by a
health professional.** Each one is marked `"reviewed": false`, and the portal shows readers a
notice saying so. Do not change the flag until a qualified person has reviewed the lesson, in
Burmese.

To add a lesson: put `<id>.lesson.json` in `lessons/` (format at the top of `lib/lesson.js`)
and add its title and description to `lessons/index.json`. A pack can also carry its own
lesson as a normal item with `"kind": "lesson"`. The builder refuses a lesson unless it has:

- Burmese (Unicode) and English for every step, reason and tip;
- at least one named **source** for its facts;
- a `reviewed` flag;
- pictures that are plain SVG drawings: no scripts, links, images or outside files.

Lesson text is included in `docs/burmese-review.csv` for native review.

**Safety rule for health and survival lessons:** follow a recognised source (WHO, Red Cross,
a national health agency), keep to what the source says, and leave out anything where a small
mistake can kill (identifying wild plants or mushrooms, medicine doses, home-made rehydration
recipes).

## Weekly release in one command

```bash
node bin/openpack.js keygen keys            # once; keep keys/openpack.key secret and backed up
scripts/weekly-release.sh packs/weekly      # build, sign, verify, bundle, change note, checksum
```

Output lands in `dist/`: the bundle zip to copy onto USB sticks, its `.sha256`, and
`CHANGES-<name>-<version>.txt` listing what is new, updated and removed since the last
release. Old releases are pruned (`--keep 4` by default). For `http` sources, pin checksums
first with `node scripts/pin-source.js packs/<name>/pack.json`.

## Search

- **Zawgyi keyboards work.** Zawgyi typing is detected and converted to Unicode before
  matching (rules from Rabbit Converter, MIT). Words without a clear Zawgyi marker are also
  tried in converted form as a fallback.
- **Spaces do not matter in Burmese.** `ကြို ဆို` finds `ကြိုဆို`.
- **Syllable fallback.** If the exact phrase is not found, items containing all of its
  syllables still match, ranked lower. Title matches rank above description matches.

## Using the portal (what people see)

Built for someone who has never used an app like this:

- The home screen is big picture tiles for each type of content, one search box, and one
  Share button. Nothing else.
- Each item has **one** big coloured button whose word depends on the file: Read, Watch,
  Listen, View, Install or Save (`ဖတ်ရန်`, `ကြည့်ရန်`, `နားထောင်ရန်`, ...). Licence, author, file size
  and the check code are tucked under "Details".
- Top bar: quick language switch (written in the *other* language so people can find it),
  Help and Settings, each with an icon and a word.
- **Settings**: language (မြန်မာ / English), text size (4 steps, up to very large), light or
  dark. Changes apply immediately and are saved on that device. "Reset" restores defaults.
- **Help**: six plain questions (how to open a file, search, install an APK, share, what to do
  if a file will not open, whether internet is needed).
- The phone's Back button works, because every screen has its own address.
- Works on small old phones: plain ES5, no external files, readable at 360 px wide.

**All words on screen are in one file, `portal/strings.js`**, in Burmese and English side by
side. To fix a label, edit it there and rebuild. Tests make sure both languages have the same
labels and that the Burmese is Unicode. The Burmese was written by an AI: **it needs a native
speaker to review it** (see CONTRIBUTING).

## Burmese text notes

- The portal expects **Unicode** Burmese, not Zawgyi. Zawgyi is still common on older
  phones and will look garbled in a Unicode page.
- The builder runs a conservative Zawgyi check on every `title_my` and `description_my` and
  rejects the pack if it finds Zawgyi markers. It is a marker heuristic with few false
  positives (tested on Pali loanwords like ဗုဒ္ဓ), not Google's ML model: it can miss
  Zawgyi text with no marker. For borderline text, use Google's
  [myanmar-tools](https://github.com/google/myanmar-tools) (Apache-2.0; use the PyPI
  package `myanmartools`, because the npm package ships TypeScript sources without its
  build step) and convert with ICU's `Zawgyi-my` transliterator.
- Search converts Zawgyi typing (see Search). While typing, Zawgyi input is drawn with the
  Unicode font and can look wrong until Search is pressed.
- The Burmese interface text (`portal/strings.js`) and `bundle/START-HERE.txt` were written by
  an AI and need a review by a native speaker before real use.
- Windows command windows cannot display Burmese, so the server's console output is English
  only. Burmese lives in the portal and in `START-HERE.txt`.
- **Font.** Many phones in Myanmar use a Zawgyi system font, which draws Unicode Burmese as
  garbage. The portal therefore ships its own Unicode font, Noto Sans Myanmar (SIL OFL 1.1,
  `portal/fonts/`, about 220 KB for both weights in both formats), and uses it for Burmese
  characters only. Do not add `local()` system fonts in front of it. A test enforces this.

## Getting Kiwix content

Take the exact file name from the [Kiwix library](https://library.kiwix.org) (Burmese
Wikipedia is published there). Pin the file's SHA-256 in `source.sha256` so a corrupted
download fails the build. Kiwix content is CC BY-SA and similar, so list the licence and
attribution shown on its library page.

## More documents

- [README.my.md](README.my.md): short Burmese introduction
- [docs/SOURCES.md](docs/SOURCES.md): open content sources and the starter pack
- [docs/CONTENT-POLICY.md](docs/CONTENT-POLICY.md): what may go in a pack, removal requests (draft, needs legal review)
- [docs/DEVICE-TEST.md](docs/DEVICE-TEST.md): 20-minute checklist for real phones and Windows
- [docs/burmese-review.csv](docs/burmese-review.csv): every Burmese label, for native review
- [docs/ROADMAP.md](docs/ROADMAP.md): audio, Kolibri, known gaps

> **Name.** "OpenPack" is a working name. `openpack` is already an npm package (a Webpack
> plugin) and a Rust library on GitHub, so pick a final name before publishing.

## Status

| Piece | State |
|---|---|
| Pack builder, signing, verification, licence gate | Working, tested (44 Node tests) |
| Zawgyi lint, copyleft source rule, `zim`/`apk` kinds | Working, tested |
| Picture lessons: builder checks, player, 9 lessons included in every pack | Working, tested in Node and Chrome (every lesson stepped through). **Content is AI-written from the listed sources and not yet expert-reviewed.** |
| Search: Zawgyi input, space-insensitive Burmese, syllable fallback, ranking | Working, tested in Node and in Chrome. Conversion rules need a native check on real typing. |
| Weekly release script, change notes, checksum pinning | Release script tested end to end here. `pin-source.js` download-and-hash path tested against a live https server; the publisher `.sha256` shortcut is not yet tested against Kiwix. |
| Portal: tiles, one-button items, settings (language, text size, theme), help, QR share, bundled Burmese font | Tested in headless Chrome in folder and served mode (108 checks incl. Back button, saved settings, 360 px width, largest text, dark). Not yet tried on real Burmese phones. |
| Go server: verification, serving, hidden dotfiles, no listings, range downloads | Tested (Go tests plus a Node-signed pack verified by Go, and tamper checks). Runs on Linux here. |
| Windows, Android/Termux and 32-bit ARM builds | **Compiled but not run.** They cross-compile cleanly; nobody has executed them on those devices yet. |
| `START-*` scripts | Written to spec, not yet run on real devices |
| `box/` Pi/router scripts | Written to spec, **untested on hardware** |
| Burmese audio narration of news digests | Roadmap: Nara Mini (Apache-2.0 Burmese TTS) or Qwen3-TTS, test quality first |
| Kolibri content, agent dashboard, wallet payments | Roadmap |

## Deliberately not included

- **Messaging or mesh chat.** Existing tools (bitchat, Briar, Meshtastic) already cover that
  need, each with its own security trade-offs, and adding chat would change what this
  project is and who it exposes.
- **Pirated media.** The licence gate exists to keep it out.

## Not legal advice

Distribution and public Wi-Fi rules vary by country and change over time. Check the law
where you operate before deploying a box.

## Licence

Code: MIT. Content in packs keeps its own licence, recorded per item.
