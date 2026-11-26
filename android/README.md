# OpenPack for Android (test build)

The library as an app. No browser steps, no Termux, no computer.

Status: **first test build. It has not been run on a real phone yet.** See "What has been tested".

## What it does

- Opens the built-in pack straight away: tiles, search, the nine picture lessons, Burmese first.
- **Share with nearby phones**: one button. Other phones on the same Wi-Fi or on this phone's hotspot
  open the address shown (or scan the QR code) in any browser. They do not need the app.
- **Open a new pack**: pick a pack `.zip` from storage, a memory card or a Bluetooth transfer. It is
  only accepted if it is signed with a key built into the app and every file matches its hash.
- **Write notices**: the phone's owner types the notice board; visitors can only read it.
- Save buttons put a file where the person chooses (Downloads, a memory card).

## Built to keep going without anyone looking after it

The app is meant to keep working and keep spreading for years with no internet, no app store and
no central server. Size was not a constraint; these were:

- **It passes itself on.** A visitor's share page offers "Get the app" (`/openpack.apk`, the very
  installer this phone is running) and "Get the library as one file" (`/pack.zip`). One phone with
  the app can seed a village.
- **Phones update each other.** More > "Get the newest pack from a nearby phone" finds a sharing
  phone on the same Wi-Fi or hotspot by itself (the hotspot owner is tried first, then the rest of
  the local network) and copies the pack across. Only files this phone lacks are copied, and a
  broken transfer carries on where it stopped. A newer pack reaches a place when one person arrives
  with it.
- **The other phone is never trusted.** Its manifest must carry a valid signature before any file
  is asked for; every file is hashed before the pack is used; page scripts are never copied.
  Only local-network addresses (10.x, 172.16-31.x, 192.168.x, 169.254.x) are ever contacted.
- **Sharing survives the screen turning off.** A foreground notice keeps the server, processor and
  Wi-Fi awake until Stop is tapped. Plug the phone in for long sessions.
- **Old phones.** Android 5.0 (2014) and newer.
- **Its own Burmese font** on the native buttons and dialogs, so they read correctly on phones
  whose system font is Zawgyi.
- **A spare signing key.** The app can trust more than one pack key. Build it with an everyday key
  and a spare kept offline, and losing the everyday key does not strand every installed phone.
- **Few moving parts.** Plain Java, one small library, no Google services, a pinned build tool.
  The same code is checked on simulated Android 5, 9 and 14 on every change.

## What it does not do

- No internet use at all. There are no Google services, no analytics, no crash reporting, no accounts.
- Nothing about what is read or who connected is recorded.
- No chat.
- It cannot switch the hotspot on for you. Android does not allow apps to do that. Turn the hotspot
  on in Settings, then tap Share.
- Offline Wikipedia (`.zim`) files can be carried and saved, but not read inside the app. Use the
  Kiwix app for those. PDFs are saved and opened with the phone's PDF viewer.
- A pack older than the one installed is only copied after asking. There is no hard block on going
  back to an older pack.

Permissions, all granted without a prompt except the last:
`INTERNET` (Android's name for opening any socket, including local ones),
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK` (sharing with the screen off),
`POST_NOTIFICATIONS` (Android 13+: shows the "sharing is on" notice; sharing works if refused).

## Build

Needs Java 17 or newer and an Android SDK (`ANDROID_HOME`). Android Studio provides both.

```bash
# 1. Build and sign a pack as usual
node bin/openpack.js build packs/example --key keys/openpack.key

# 2. Build the app with that pack and the PUBLIC key inside
#    (add a second public key to build in a spare: ... keys/openpack.pub spare/openpack.pub)
scripts/build-android.sh dist/example-<version> keys/openpack.pub
# -> android/app/build/outputs/apk/debug/app-debug.apk
```

The script verifies the pack, copies it and the public key into the app, runs the tests and builds.
It refuses a private key. The copied pack and key are not committed (`android/.gitignore`).

Install on a phone: copy the `.apk` over, tap it, allow "install unknown apps" for the file manager.

## Two keys, both yours

1. **Pack key** (ed25519, `openpack keygen`): its public half is built into the app. The app opens
   only packs signed with the private half. Make a second one as a spare (`openpack keygen spare`),
   build both public halves in, and keep the spare's private half offline, somewhere else.
2. **App signing key** (Android keystore): Android only installs an update over an app signed with
   the same key. The debug build uses a throwaway key that every computer generates for itself, so
   debug builds from two computers cannot update each other. Before giving the app to anyone,
   create one release keystore, keep it offline with a backup, and never commit it.

Lose either private key and existing installs can never be updated. Leak either and someone else
can push content or app updates that phones will accept.

## How it is put together

| Part | File | Notes |
|---|---|---|
| Pack check | `core/PackVerifier.java` | Same rules as `server/verify.go`, plus: a signature is always required, files must be under `content/`, several trusted keys |
| Server | `core/PackServer.java` | GET and HEAD only, byte ranges, no listings, no hidden files, no log. Content is served only if the signed manifest lists it. Also `/openpack.apk` and `/pack.zip` |
| Phone to phone | `core/PeerSync.java` | Find a sharing phone, check its signature, copy only what is missing, resume |
| Zip import | `core/PackZip.java` | Takes only `manifest.*` and `content/`. Page files in a zip are ignored |
| Notices | `core/Notices.java` | Same format as `notices.txt` on the computer version |
| Library state | `app/Library.java` | Which pack is in use; swaps packs only after a full check, and puts the old one back if the swap fails |
| Background sharing | `app/ShareService.java` | The notice that keeps sharing alive |
| Screen | `app/MainActivity.java` | WebView on `http://127.0.0.1:8080/`; links to anywhere else are blocked |

The page files (`index.html`, `app.js` ...) are not covered by the pack signature, so the app never
takes them from an imported pack or another phone. It always uses the copy it was built with.

Plain Java, no Kotlin, no AndroidX in the app. One library: `net.i2p.crypto:eddsa` (CC0), because
Android only gained built-in ed25519 in version 13. Runs on Android 5.0 (2014) and newer.

## What has been tested

On a computer, in CI and by `scripts/build-android.sh`:

- 41 unit tests of the pack check, server, phone-to-phone copying, zip import and notices, against
  a pack built by the Node tool (so the two implementations are checked against each other).
  Phone-to-phone tests use two real sockets: full copy, copy of only what is missing, resume after
  a break, a peer with changed files, an oversized file, a pack signed by a stranger.
- The real app code on simulated Android 5, 9 and 14 (Robolectric): first launch, second launch,
  sharing that outlives the screen and stops from the notice, a zip import that is refused when one
  bit is changed and accepted when intact. This caught one crash that only Android 5 would have hit.
- The browser suite (`scripts/browser-test.py`, 119 checks with the app's extras) run against the
  app's server code:
  `OP_LAN=<this computer's address> OP_SERVER="java -cp <classes>:<jars> org.openpack.core.ServeMain" python3 scripts/browser-test.py`

Not tested, because it needs phones in hand:

- Installing and opening the APK on any real device.
- How the page looks in the phone's own WebView, especially on old Android 5 to 8 phones.
- Sharing and phone-to-phone copying over a real hotspot. Some phones isolate hotspot clients from
  each other; the hotspot owner should still be reachable, but this needs checking per phone brand.
- Whether each phone brand really lets the notice keep sharing alive for hours. Some brands
  (Xiaomi, Oppo, Vivo, Huawei) stop background apps unless battery saving is switched off for the app.
- Installing the app from another phone's share page (the browser's "unknown apps" prompt).
- The file pickers for Open pack and Save.
- Battery use while sharing for hours.
