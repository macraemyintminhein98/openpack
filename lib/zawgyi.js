'use strict';
// Lightweight Zawgyi heuristic. OpenPack's portal and manifests must be Unicode Burmese.
//
// This is a conservative marker check (few false positives), NOT a replacement for
// Google's ML detector (github.com/google/myanmar-tools, Apache-2.0). For borderline
// text, run it through `pip install myanmartools` and use ICU's "Zawgyi-my" transliterator
// to convert. Markers used:
//   1. Code points U+1060-U+1097: Zawgyi puts its own glyph variants here; everyday
//      Unicode Burmese does not use them (they serve Shan, Karen, Pa'O and similar).
//   2. U+1031 (vowel sign E) at the start of a word: Zawgyi writes it visually first,
//      Unicode always stores it after its consonant.
//   3. U+1039 (virama) at the end of a word: Unicode uses it only between two consonants.
//
// Code points are built from numbers so the source stays readable and escape-proof.

const ch = (n) => String.fromCharCode(n);
const VIRAMA = ch(0x1039);
const VOWEL_E = ch(0x1031);
const SECTION_MARKS = ch(0x104a) + ch(0x104b); // Burmese comma and full stop

const MARKERS = [
  {
    re: new RegExp(`[${ch(0x1060)}-${ch(0x1097)}]`),
    why: 'contains Zawgyi-only glyph code points (U+1060-U+1097)',
  },
  {
    re: new RegExp(`(^|[\\s${SECTION_MARKS}])${VOWEL_E}`),
    why: 'word starts with vowel sign E (Zawgyi visual order; Unicode stores it after the consonant)',
  },
  {
    re: new RegExp(`${VIRAMA}(?=\\s|$|[${SECTION_MARKS}])`),
    why: 'virama at end of word (Zawgyi style; Unicode uses it only between consonants)',
  },
];

function checkZawgyi(text) {
  if (!text) return { zawgyi: false, reasons: [] };
  const reasons = MARKERS.filter((m) => m.re.test(text)).map((m) => m.why);
  return { zawgyi: reasons.length > 0, reasons };
}

module.exports = { checkZawgyi };
