# What has been tested, and how close it is to real use

Last run: 2026-11-26. None of this replaces a real phone on a real hotspot
(`DEVICE-TEST.md`), and none of it judges whether the Burmese reads naturally.

## Browsers and phones (simulated)

The full browser suite (115 checks: library, lessons, settings, search, notice board)
passed in every row below. The server was reached at the
machine's network address, not `localhost`, because phones are never on localhost and
browsers disable some features on plain-http addresses.

| Engine | Phone profile | Result |
|---|---|---|
| Chrome 153 | 360 px generic | 115/115 |
| Chrome 153 | Galaxy S5 (small, old Android profile) | 115/115 |
| Chrome 153 | Moto G4 | 115/115 |
| Chrome 153 | Galaxy S9+ landscape | 115/115 |
| WebKit 26.6 (Safari's engine) | iPhone SE | 115/115 |
| WebKit 26.6 | iPad Mini | 115/115 |
| Firefox 155 | 360 px generic | 115/115 |

Run it yourself:

```bash
pip install playwright && python3 -m playwright install firefox webkit
OP_HOST=<this machine's LAN address> OP_DEVICE="Galaxy S5" python3 scripts/browser-test.py
OP_ENGINE=webkit OP_DEVICE="iPhone SE" OP_HOST=<address> python3 scripts/browser-test.py
```

**Limits.** These are today's browser engines with a phone-sized screen and touch. They are
not old Android WebViews. Every portal script parses as ES5 (checked with the acorn parser),
which covers syntax, but the code uses `fetch`, so Android System WebView / Chrome below
version 42 (2015) will not run it.

## Fonts

On a test machine whose only Burmese system font is a bitmap fallback, Chrome's own report
of which font drew each element showed the bundled Noto Sans Myanmar for all Burmese text.
With the bundled font blocked, the system font was used instead, which is what a Zawgyi
phone would do if the font failed to load. **Not tested with an actual Zawgyi system font.**

## Slow phone, weak link

Galaxy S5 profile, CPU slowed 6x, 30 KB/s with 400 ms delay: home screen in 8.6 s, a lesson
in 2.0 s, 203 KB transferred in total.

## Busy hotspot

Not load-tested in this version. A phone hotspot usually allows about 10 devices, and some
hotspots stop phones from reaching each other's addresses at all ("client isolation"). Only
a real test shows that.

## Server programs

| Build | Test | Result |
|---|---|---|
| Linux x86-64 | Go tests, end-to-end, browser suite | Pass |
| Linux ARM 64-bit (Android phones via Termux, Pi 4/5) | Run under QEMU: pack check + all Go tests | Pass |
| Linux ARM 32-bit (old phones, Pi Zero/2) | Run under QEMU: pack check + all Go tests | Pass |
| Windows, Android targets | Compile and vet only | **Not run.** No emulator available. |

QEMU emulates the processor, not Android itself. Termux, storage permissions and the START
scripts are untested.

## Burmese text (291 lines)

`scripts/check-burmese.py`, using Google's Zawgyi detector (myanmartools) and two open word
lists (31,300 words, 3,085 distinct syllables):

- **Zawgyi:** every line longer than 40 characters scored 0.000 (certainly Unicode). The few
  short lines that scored higher are words spelled identically in both encodings.
- **Character order:** 0 problems (mark order, doubled marks, hidden characters).
- **Syllables:** of 5,414 syllables written, all but 11 distinct ones occur in real Burmese
  words. The 11 were checked by hand: two standard symbols and loan words such as ကုဒ် (code),
  အက်ပ် (app), လက်ပ်တော့ပ် (laptop), ဂက်စ် (gas).
- **Words:** inconclusive. The lists are too small to judge compound and medical terms.

This shows the text is well-formed Burmese. It says nothing about whether it is the right
word, natural, or polite. That still needs a native reader.

## Not tested at all

- Any physical phone, tablet or Windows PC
- A real Wi-Fi hotspot, including client isolation and range
- Android below 8, and Android WebView / Chrome below 42
- A Zawgyi system font
- Battery use while a phone serves the pack
- Whether the lessons are medically right for Myanmar
