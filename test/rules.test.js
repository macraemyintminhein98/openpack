'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { validateConfig } = require('../lib/build');
const { checkZawgyi } = require('../lib/zawgyi');

const item = (over) => ({
  id: 'x', title: 'X', category: 'c', license: 'CC0-1.0',
  source: { type: 'local', path: 'x.txt' }, ...over,
});
const errsFor = (over) => validateConfig({ name: 'p', items: [item(over)] });

// Samples: the first is Zawgyi (UDHR title), the rest are Unicode.
const ZAWGYI = 'အျပည္ျပည္ဆိုင္ရာ လူ႔အခြင့္အေရး ေၾကညာစာတမ္း';
const UNICODE = [
  'အပြည်ပြည်ဆိုင်ရာ လူ့အခွင့်အရေး ကြေညာစာတမ်း',
  'OpenPack မှ ကြိုဆိုပါတယ်',
  'ဤဘောက်စ်ကား အဘယ်နည်း၊ ပက်ကေ့ချ်များကို မည်သို့ အသစ်လဲရမည်နည်း။',
  'ဖတ်ရန် (Kiwix)',
  'ထည့်သွင်းရန် (APK)',
  'ဗုဒ္ဓ ဘာသာ',
  'ကြေညာချက် လေယာဉ်ပျံ',
];

test('Zawgyi text is flagged', () => {
  assert.ok(checkZawgyi(ZAWGYI).zawgyi);
});

test('Unicode Burmese is not flagged', () => {
  for (const u of UNICODE) assert.ok(!checkZawgyi(u).zawgyi, `false positive: ${u}`);
  assert.ok(!checkZawgyi('plain English').zawgyi);
  assert.ok(!checkZawgyi('').zawgyi);
});

test('build config rejects Zawgyi titles', () => {
  const errs = errsFor({ title_my: ZAWGYI });
  assert.ok(errs.some((e) => e.includes('title_my') && e.includes('Zawgyi')));
});

test('GPL items need a source_code link', () => {
  const bad = errsFor({ license: 'GPL-3.0', attribution: 'Dev' });
  assert.ok(bad.some((e) => e.includes('source_code')));
  const ok = errsFor({ license: 'GPL-3.0', attribution: 'Dev', source_code: 'https://example.org/src' });
  assert.ok(!ok.some((e) => e.includes('source_code')));
});

test('kind must match the file extension', () => {
  assert.ok(errsFor({ kind: 'zim' }).some((e) => e.includes('expects a .zim')));
  assert.ok(!errsFor({ kind: 'zim', source: { type: 'local', path: 'wiki.zim' } }).some((e) => e.includes('expects')));
  assert.ok(errsFor({ kind: 'movie' }).some((e) => e.includes('unknown kind')));
});

test('apk items must pin a sha256', () => {
  const noPin = errsFor({ kind: 'apk', license: 'GPL-3.0', attribution: 'Dev', source_code: 'https://example.org/s', source: { type: 'http', url: 'https://example.org/app.apk' } });
  assert.ok(noPin.some((e) => e.includes('pin "source.sha256"')));
  const pinned = errsFor({ kind: 'apk', license: 'GPL-3.0', attribution: 'Dev', source_code: 'https://example.org/s', source: { type: 'http', url: 'https://example.org/app.apk', sha256: 'a'.repeat(64) } });
  assert.deepStrictEqual(pinned, []);
});
