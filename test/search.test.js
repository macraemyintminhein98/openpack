'use strict';
// Search behaviour: Zawgyi typing, Burmese without spaces, syllable fallback, ranking.
const test = require('node:test');
const assert = require('node:assert');
const { load } = require('../lib/portal-data');
const { checkZawgyi } = require('../lib/zawgyi');

const S = load().search;
const items = [
  { id: 'welcome', title: 'Welcome to OpenPack', title_my: 'OpenPack မှ ကြိုဆိုပါတယ်', description: 'What this box is', category: 'about' },
  { id: 'lesson', title: 'Sample lesson: how a Wi-Fi box works', title_my: 'နမူနာသင်ခန်းစာ: Wi-Fi ဘောက်စ် အလုပ်လုပ်ပုံ', category: 'learn' },
  { id: 'health', title: 'Clean water', title_my: 'သောက်ရေ သန့်ရှင်းရေး', description_my: 'ရေကို ကျိုချက်သောက်ပါ။ သင်ခန်းစာ တို', category: 'health' },
];
const ids = (q, extra) => Array.from(S.search(items, q, extra), (i) => i.id);
const syl = (t) => Array.from(S.syllables(t));
// Zawgyi spellings of Unicode words (produced with Rabbit's uni2zg).
const ZG = { 'ကြိုဆို': 'ႀကိဳဆို', 'သင်ခန်းစာ': 'သင္ခန္းစာ', 'ကြေညာ': 'ေၾကညာ' };

test('English search, any letter case, all words must match', () => {
  assert.deepStrictEqual(ids('WELCOME'), ['welcome']);
  assert.deepStrictEqual(ids('wi-fi lesson'), ['lesson']);
  assert.deepStrictEqual(ids('welcome lesson'), []);
});

test('empty or blank search finds nothing', () => {
  assert.deepStrictEqual(ids(''), []);
  assert.deepStrictEqual(ids('   '), []);
});

test('Burmese search ignores spaces people add or leave out', () => {
  assert.deepStrictEqual(ids('ကြိုဆို'), ['welcome']);
  assert.deepStrictEqual(ids('ကြို ဆို'), ['welcome']);
  assert.deepStrictEqual(ids('သောက်ရေသန့်ရှင်းရေး'), ['health']);
});

test('Zawgyi typing is converted and finds Unicode content', () => {
  for (const [uni, zg] of Object.entries(ZG)) {
    assert.strictEqual(S.zg2uni(zg), uni);
  }
  assert.strictEqual(S.looksZawgyi(ZG['ကြိုဆို']), true);
  assert.strictEqual(checkZawgyi(ZG['ကြေညာ']).zawgyi, true);
  // this spelling has no Zawgyi-only marker; the fallback conversion must still find it
  assert.strictEqual(S.looksZawgyi(ZG['သင်ခန်းစာ']), false);
  assert.deepStrictEqual(ids(ZG['သင်ခန်းစာ']), ['lesson', 'health']);
  assert.deepStrictEqual(ids(ZG['ကြိုဆို']), ['welcome']);
  assert.strictEqual(S.display(ZG['ကြိုဆို']), 'ကြိုဆို');
});

test('Unicode typing is never run through the Zawgyi converter', () => {
  for (const u of ['ကြိုဆို', 'ဗုဒ္ဓ ဘာသာ', 'အပြည်ပြည်ဆိုင်ရာ လူ့အခွင့်အရေး ကြေညာစာတမ်း', 'သင်ခန်းစာ']) {
    assert.strictEqual(S.looksZawgyi(u), false, u);
    assert.strictEqual(S.display(u), S.norm(u));
  }
});

test('syllables are split correctly, keeping stacked consonants together', () => {
  assert.deepStrictEqual(syl('သင်ခန်းစာ'), ['သင်', 'ခန်း', 'စာ']);
  assert.deepStrictEqual(syl('ကြိုဆိုပါတယ်'), ['ကြို', 'ဆို', 'ပါ', 'တယ်']);
  assert.deepStrictEqual(syl('ဗုဒ္ဓ'), ['ဗုဒ္ဓ']);
  assert.deepStrictEqual(syl('ရေ wifi စာ'), ['ရေ', 'စာ']);
});

test('syllables in a different order still match, ranked below exact matches', () => {
  // "စာသင်ခန်း" is not a phrase in any item, but all its syllables are in two of them.
  const r = ids('စာသင်ခန်း');
  assert.deepStrictEqual(r.slice().sort(), ['health', 'lesson']);
  // exact phrase: title match (lesson) must come before description match (health)
  assert.deepStrictEqual(ids('သင်ခန်းစာ'), ['lesson', 'health']);
});

test('a single stray syllable does not match half of a different one', () => {
  assert.deepStrictEqual(ids('ဆေး'), []);
});

test('category names are searchable through the extra text', () => {
  assert.deepStrictEqual(ids('ကျန်းမာရေး', (i) => (i.category === 'health' ? 'ကျန်းမာရေး' : '')), ['health']);
});
