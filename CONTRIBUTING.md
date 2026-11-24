# Contributing

## Ground rules

1. **Redistributable content only.** The builder enforces this; do not bypass it.
2. **No piracy.** No films, music, books or software without the rights holder's written
   permission, recorded in the item's `proof` field.
3. **Keep it apolitical and factual.** OpenPack distributes content. It is not a
   messaging or organising tool, and features that turn it into one will not be merged.
4. **No user tracking.** No accounts, analytics, or access logs in the box.
5. **Zero runtime dependencies** in the builder. It should run on any machine with Node 18+.

## Development

```bash
npm test
node bin/openpack.js build packs/example
python3 scripts/browser-test.py   # real-browser check; needs playwright + Chrome and `npm run bundle`
```

### Improving the Burmese (most wanted)

Every word the portal shows is in `portal/strings.js`. The easy way: open
`docs/burmese-review.csv` in a spreadsheet, fill in the "Better Burmese" column and send it
back (issue or pull request). Maintainers: regenerate it with `node scripts/make-review-sheet.js`.
 If you read Burmese, please check each
label for naturalness and accuracy (the first version was written by an AI) and send a pull
request. Keep to **Unicode** Burmese, never Zawgyi, and keep `{placeholders}` as they are.
`npm test` checks that both languages have the same keys and flags Zawgyi.

Add a test for any change to `lib/`. Pull requests that touch signing or verification
need a test showing tampering is still detected.

## Content contributions

Open a PR adding an item to a pack. Include licence, attribution, and for `permission`
items a link or note to the written consent. Burmese titles and descriptions
(`title_my`, `description_my`) are very welcome; please use Unicode, not Zawgyi.
