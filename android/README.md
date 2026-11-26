# OpenPack for Android (test build)

The library as an app. No browser steps, no Termux, no computer.

Status: **first test build. It has not been run on a real phone yet.** See "What has been tested".

## What it does

- Opens the built-in pack straight away: tiles, search, the nine picture lessons, Burmese first.
- **Share with nearby phones**: one button. Other phones on the same Wi-Fi or on this phone's hotspot
  open the address shown (or scan the QR code) in any browser. They do not need the app.
- **Open a new pack**: pick a pack `.zip` from storage, a memory card or a Bluetooth transfer. It is
  only accepted if it is signed with the key built into the app and every file matches its hash.
- **Write notices**: the phone's owner types the notice board; visitors can only read it.
- Save buttons put a file where the person chooses (Downloads, a memory card).

## What it does not do

- No internet use at all. The one permission (INTERNET) is what Android requires to open a local
  socket for sharing. There are no Google services, no analytics, no crash reporting, no accounts.
- Nothing about what is read or who connected is recorded.
- No chat.
- It cannot switch the hotspot on for you. Android does not allow apps to do that. Turn the hotspot
  on in Settings, then tap Share.
- Sharing stops when the app is closed. Keep it open and plugged in while people are copying.
- Offline Wikipedia (`.zim`) files can be carried and saved, but not read inside the app. Use the
  Kiwix app for those.

## Build

Needs Java 17 or newer and an Android SDK (`ANDROID_HOME`). Android Studio provides both.

```bash
# 1. Build and sign a pack as usual
node bin/openpack.js build packs/example --key keys/openpack.key

# 2. Build the app with that pack and the PUBLIC key inside
scripts/build-android.sh dist/example-<version> keys/openpack.pub
# -> android/app/build/outputs/apk/debug/app-debug.apk
```

The script verifies the pack, copies it and the public key into the app, runs the tests and builds.
It refuses a private key. The copied pack and key are not committed (`android/.gitignore`).

Install on a phone: copy the `.apk` over, tap it, allow "install unknown apps" for the file manager.

## Two keys, both yours

1. **Pack key** (ed25519, `openpack keygen`): its public half is built into the app. The app opens
   only packs signed with the private half.
2. **App signing key** (Android keystore): Android only installs an update over an app signed with
   the same key. The debug build uses a throwaway key that every computer generates for itself, so
   debug builds from two computers cannot update each other. Before giving the app to anyone,
   create one release keystore, keep it offline with a backup, and never commit it.

Lose either private key and existing installs can never be updated. Leak either and someone else
can push content or app updates that phones will accept.

## How it is put together

| Part | File | Notes |
|---|---|---|
| Pack check | `core/PackVerifier.java` | Same rules as `server/verify.go`, plus: a signature is always required, files must be under `content/` |
| Server | `core/PackServer.java` | GET and HEAD only, byte ranges, no listings, no hidden files, no log. Content is served only if the signed manifest lists it |
| Zip import | `core/PackZip.java` | Takes only `manifest.*` and `content/`. Page files in a zip are ignored |
| Notices | `core/Notices.java` | Same format as `notices.txt` on the computer version |
| Screen | `app/MainActivity.java` | WebView on `http://127.0.0.1:8080/`; links to anywhere else are blocked |

The page files (`index.html`, `app.js` ...) are not covered by the pack signature, so the app never
takes them from an imported pack. It always uses the copy it was built with.

Plain Java, no Kotlin, no AndroidX in the app. One library: `net.i2p.crypto:eddsa` (CC0), because
Android only gained built-in ed25519 in version 13. Runs on Android 6.0 (2015) and newer.

## What has been tested

On a computer, in CI and by `scripts/build-android.sh`:

- 29 unit tests of the pack check, server, zip import and notices, against a pack built by the Node
  tool (so the two implementations are checked against each other).
- The real screen code started on a simulated Android 14 (Robolectric): first launch unpacks the
  library, verifies it, starts the server for this phone only, opens the page; second launch too.
- The full browser suite (`scripts/browser-test.py`, 115 checks) run against the app's server code:
  `OP_SERVER="java -cp <classes>:<jars> org.openpack.core.ServeMain" python3 scripts/browser-test.py`

Not tested, because it needs a phone in hand:

- Installing and opening the APK on any real device.
- How the page looks in the phone's own WebView, especially on old Android 6 to 8 phones.
- Sharing over a real hotspot. Some phones isolate hotspot clients from each other; the host phone
  should still be reachable, but this needs checking per phone brand.
- The file pickers for Open pack and Save.
- Battery use while sharing for hours.
- Native buttons on a phone whose system font is Zawgyi (the page itself carries its own font, the
  three native buttons do not).
