# Roadmap notes

## Burmese audio (read-aloud for people who do not read comfortably)

Status: researched, not built. Findings (2026-11-24):

| Option | Licence | Notes |
|---|---|---|
| Meta MMS-TTS Burmese (`facebook/mms-tts-mya`) | CC BY-NC 4.0 | Small VITS model, runs on a laptop CPU. Needs numbers written out as words first. Quality unverified. |
| F5-Myanmar-TTS (community, ~795 h of speech) | CC BY-NC 4.0 | Larger (1.26 GB weights), needs a GPU to be practical. Check the training-data provenance before relying on it. |
| Nara Audio (naraaudio.app) | Commercial service | Burmese-first, made in Myanmar. Terms would need checking for redistribution of generated audio. |

Plan: generate audio **at build time on the maintainer's machine** and ship plain `.mp3`
files as normal items (the portal already shows "Listen"). Nothing runs on the phones.
Both open models are non-commercial, so audio made with them goes in free packs only, marked
non-commercial. First step is a listening test by Burmese speakers on 10 sample paragraphs.

## Kolibri content

Kolibri has a public Khan Academy Burmese channel, and Kolibri itself is translated into
Burmese. But channels are imported through Kolibri, not offered as plain downloads, and Khan
Academy content is CC BY-NC-SA. Two honest options:

1. Run Kolibri next to OpenPack on a laptop or Pi box and link to it from the portal (like
   Kiwix today). No repackaging, licences stay intact. Too heavy for a phone.
2. Export single openly-licensed videos/PDFs and add them as normal items. Needs
   `CC-BY-NC-SA-4.0` added to the allow list (share-alike + non-commercial) after a licence
   review.

## Known gaps

- Typing Zawgyi into the search box is converted for matching, but while typing it is drawn
  with the Unicode font, so it can look wrong on screen until Search is pressed.
- Burmese search is syllable-based, not word-based: no spelling tolerance or synonyms.
- `box/` hotspot scripts pass shellcheck but have never run on hardware.
