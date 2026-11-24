# Content sources for a first real pack

Checked on 2026-11-24 by web search. **Verify the licence on each item's own page before
adding it**: collections mix licences.

| Source | What | Licence (as published) | Notes |
|---|---|---|---|
| Kiwix library (library.kiwix.org) | Burmese Wikipedia, Wiktionary etc. as `.zim` | CC BY-SA (Wikipedia) | Use `kind: "zim"`. Take the exact file name from the library page, then run `node scripts/pin-source.js` to pin the checksum. |
| StoryWeaver (storyweaver.org.in) | Children's storybooks, many languages incl. Burmese translations | CC BY 4.0 | Download as PDF/ePub per book; credit author, illustrator, translator and Pratham Books. |
| Let's Read (The Asia Foundation) | Burmese children's books, incl. 15 made with Third Story Project | CC BY-NC 4.0 for those titles | Non-commercial: the builder flags these. Check each title. |
| Kolibri Library (Learning Equality) | Khan Academy Burmese channel and others | Per item; Khan Academy is CC BY-NC-SA | Not directly usable yet: see ROADMAP. CC BY-NC-SA is not in the allow list today. |
| F-Droid | Open-source Android apps (Kiwix reader, a PDF reader, a Unicode keyboard) | Per app (GPL, Apache...) | Use `kind: "apk"`, pin the checksum F-Droid publishes, add `source_code`. |
| Hesperian, WHO, UNICEF Myanmar | Health booklets | **Not open by default** | Needs written permission: use `license: "permission"` with a `proof` note, or leave out. |

## Starter pack

`packs/starter/pack.json.example` has the Wikipedia entry ready. Steps:

```bash
cp packs/starter/pack.json.example packs/starter/pack.json
# 1. open library.kiwix.org, filter language "Burmese", copy the real file name into "url"
# 2. pin checksums (uses the publisher's .sha256 file when there is one)
node scripts/pin-source.js packs/starter/pack.json
# 3. build, sign, bundle
scripts/weekly-release.sh packs/starter
```

The file name in the example follows Kiwix's usual pattern but its date is a placeholder.
