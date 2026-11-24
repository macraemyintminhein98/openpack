'use strict';
// Checks on the words and icons shown in the portal, so a typo cannot ship a broken screen.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { load, placeholders, portalDir } = require('../lib/portal-data');
const { checkZawgyi } = require('../lib/zawgyi');
const { validateConfig } = require('../lib/build');

const P = load();
const MYANMAR = /[က-႟]/;
const appJs = fs.readFileSync(path.join(portalDir, 'app.js'), 'utf8');
const indexHtml = fs.readFileSync(path.join(portalDir, 'index.html'), 'utf8');

test('Burmese and English have exactly the same labels', () => {
  const my = Object.keys(P.strings.my).sort();
  const en = Object.keys(P.strings.en).sort();
  assert.deepStrictEqual(my.filter((k) => !en.includes(k)), [], 'only in Burmese');
  assert.deepStrictEqual(en.filter((k) => !my.includes(k)), [], 'only in English');
});

test('no label is empty or has stray spaces around it', () => {
  for (const lang of ['my', 'en']) {
    for (const [k, v] of Object.entries(P.strings[lang])) {
      assert.ok(typeof v === 'string' && v.length > 0, `${lang}.${k} is empty`);
      assert.strictEqual(v, v.trim(), `${lang}.${k} has spaces at the ends`);
    }
  }
});

test('{placeholders} match between languages', () => {
  for (const k of Object.keys(P.strings.en)) {
    assert.strictEqual(placeholders(P.strings.my[k]), placeholders(P.strings.en[k]), `placeholders differ for "${k}"`);
  }
});

test('every Burmese label is Burmese script and Unicode (not Zawgyi)', () => {
  const all = [];
  for (const [k, v] of Object.entries(P.strings.my)) all.push([`my.${k}`, v]);
  for (const [k, v] of Object.entries(P.categories)) all.push([`category ${k}`, v.my]);
  all.push(['langs.my', P.langs.my]);
  for (const [name, v] of all) {
    assert.ok(MYANMAR.test(v), `${name} has no Burmese letters`);
    const z = checkZawgyi(v);
    assert.ok(!z.zawgyi, `${name} looks like Zawgyi: ${z.reasons && z.reasons[0]}`);
  }
});

test('English labels contain no Burmese and the other way round for category names', () => {
  for (const [k, v] of Object.entries(P.strings.en)) assert.ok(!MYANMAR.test(v), `en.${k} contains Burmese`);
  for (const [k, v] of Object.entries(P.categories)) assert.ok(!MYANMAR.test(v.en), `category ${k} en has Burmese`);
});

test('Burmese text written in index.html is Unicode', () => {
  const runs = indexHtml.match(/[က-႟][က-႟\s.,၊။]*/g) || [];
  assert.ok(runs.length > 0);
  for (const r of runs) assert.ok(!checkZawgyi(r).zawgyi, `index.html: Zawgyi-looking text: ${r}`);
});

test('every icon name used exists', () => {
  for (const [id, c] of Object.entries(P.categories)) assert.ok(P.icons[c.icon], `category ${id}: icon "${c.icon}" missing`);
  const used = [...appJs.matchAll(/\bic\('([a-z]+)'\)/g)].map((m) => m[1]);
  assert.ok(used.length > 5);
  for (const n of used) assert.ok(P.icons[n], `app.js uses missing icon "${n}"`);
  // icons chosen in describe(): icon: 'name'
  for (const m of appJs.matchAll(/icon: '([a-z]+)'/g)) assert.ok(P.icons[m[1]], `app.js uses missing icon "${m[1]}"`);
});

test('every label key the code asks for exists', () => {
  const keys = new Set([...appJs.matchAll(/\bt\('([a-z0-9_]+)'[,)]/g)].map((m) => m[1]));
  for (const m of appJs.matchAll(/(?:type|act): '([a-z_]+)'/g)) keys.add(m[1]);
  for (const k of ['size_s', 'size_m', 'size_l', 'size_xl', 'theme_light', 'theme_dark']) keys.add(k);
  for (let n = 1; n <= 6; n++) { keys.add(`h${n}_q`); keys.add(`h${n}_a`); }
  assert.ok(keys.size > 30);
  for (const k of keys) assert.ok(k in P.strings.en, `app.js needs label "${k}" which is missing from strings.js`);
});

test('the bundled font files and licence are present', () => {
  for (const f of ['noto-sans-myanmar-myanmar-400-normal.woff2', 'noto-sans-myanmar-myanmar-400-normal.woff',
    'noto-sans-myanmar-myanmar-700-normal.woff2', 'noto-sans-myanmar-myanmar-700-normal.woff', 'OFL-LICENSE.txt']) {
    assert.ok(fs.existsSync(path.join(portalDir, 'fonts', f)), `missing portal/fonts/${f}`);
  }
  const css = fs.readFileSync(path.join(portalDir, 'style.css'), 'utf8');
  assert.ok(!/local\(/.test(css), 'style.css must not use local() fonts: on Zawgyi phones they would break Unicode Burmese');
});

test('app.js stays old-browser safe (no arrow functions, let/const or template strings)', () => {
  for (const f of ['app.js', 'search.js']) {
    const code = fs.readFileSync(path.join(portalDir, f), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    assert.ok(!/=>/.test(code), f + ': arrow function');
    assert.ok(!/\b(let|const)\s/.test(code), f + ': let/const');
    assert.ok(!/`/.test(code), f + ': template string');
  }
});

// ----- pack.json options for the portal -----
const base = { name: 'p', items: [{ id: 'x', title: 'X', category: 'c', license: 'CC0-1.0', source: { type: 'local', path: 'x.txt' } }] };

test('pack.json categories, ui and default_lang are accepted when valid', () => {
  const errs = validateConfig({
    ...base, default_lang: 'en',
    categories: [{ id: 'c', title: 'Course', title_my: 'သင်တန်း', icon: 'learn' }],
    ui: { my: { act_read: 'ဖတ်ရန်' }, en: { act_read: 'Open' } },
  });
  assert.deepStrictEqual(errs, []);
});

test('pack.json portal options are rejected when wrong', () => {
  const has = (cfg, text) => assert.ok(validateConfig({ ...base, ...cfg }).some((e) => e.includes(text)), `expected error containing "${text}"`);
  has({ default_lang: 'fr' }, 'default_lang');
  has({ categories: [{ id: 'c', icon: 'nope' }] }, 'unknown icon');
  has({ categories: [{ id: 'c' }, { id: 'c' }] }, 'duplicate');
  has({ categories: [{ title: 'no id' }] }, 'missing "id"');
  has({ ui: { en: { not_a_label: 'x' } } }, 'unknown label');
  has({ ui: { de: {} } }, 'unknown language');
  has({ ui: { my: { results_for: 'ရလဒ်' } } }, 'placeholders');
  has({ ui: { en: { act_read: '   ' } } }, 'non-empty');
  has({ ui: { my: { act_read: 'ေၾကညာ' } } }, 'Zawgyi');
  has({ categories: [{ id: 'c', title_my: 'အျပည္ျပည္' }] }, 'Zawgyi');
});
