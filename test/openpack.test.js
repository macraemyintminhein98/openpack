'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { build, verifyPack, validateConfig } = require('../lib/build');
const { keygen } = require('../lib/sign');

const EXAMPLE = path.resolve(__dirname, '..', 'packs', 'example');
const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), 'openpack-'));

test('licence gate rejects missing and unknown licences', () => {
  const errs = validateConfig({
    name: 'x',
    items: [
      { id: 'a', title: 'A', category: 'c', source: { type: 'local', path: 'a' } },
      { id: 'b', title: 'B', category: 'c', license: 'All-Rights-Reserved', source: { type: 'local', path: 'b' } },
      { id: 'c', title: 'C', category: 'c', license: 'permission', attribution: 'X', source: { type: 'local', path: 'c' } },
      { id: 'd', title: 'D', category: 'c', license: 'CC-BY-4.0', source: { type: 'local', path: 'd' } },
    ],
  });
  assert.ok(errs.some((e) => e.includes('"a"') && e.includes('missing "license"')));
  assert.ok(errs.some((e) => e.includes('not on the redistributable list')));
  assert.ok(errs.some((e) => e.includes('requires a "proof"')));
  assert.ok(errs.some((e) => e.includes('"d"') && e.includes('attribution')));
});

test('build, sign and verify the example pack', async () => {
  const dir = tmp();
  const { priv, pub } = keygen(path.join(dir, 'keys'));
  const { outDir, manifest, signed } = await build(EXAMPLE, { key: priv, out: path.join(dir, 'dist'), version: 'test.1' });
  assert.ok(signed);
  assert.strictEqual(manifest.items.length, 3);
  assert.ok(fs.existsSync(path.join(outDir, 'index.html')), 'portal copied into pack');
  const r = verifyPack(outDir, pub);
  assert.ok(r.ok, r.problems.join('; '));
});

test('tampering with a file is detected', async () => {
  const dir = tmp();
  const { priv, pub } = keygen(path.join(dir, 'keys'));
  const { outDir } = await build(EXAMPLE, { key: priv, out: path.join(dir, 'dist'), version: 'test.2' });
  fs.appendFileSync(path.join(outDir, 'content', 'about', 'welcome.txt'), 'evil');
  const r = verifyPack(outDir, pub);
  assert.ok(!r.ok);
  assert.ok(r.problems.some((p) => p.includes('HASH MISMATCH')));
});

test('tampering with the manifest breaks the signature', async () => {
  const dir = tmp();
  const { priv, pub } = keygen(path.join(dir, 'keys'));
  const { outDir } = await build(EXAMPLE, { key: priv, out: path.join(dir, 'dist'), version: 'test.3' });
  const mp = path.join(outDir, 'manifest.json');
  fs.writeFileSync(mp, fs.readFileSync(mp, 'utf8').replace('"license": "CC0-1.0"', '"license": "CC0-1.1"'));
  const r = verifyPack(outDir, pub);
  assert.ok(r.problems.some((p) => p.includes('SIGNATURE INVALID')));
});

test('a different key fails verification', async () => {
  const dir = tmp();
  const a = keygen(path.join(dir, 'a'));
  const b = keygen(path.join(dir, 'b'));
  const { outDir } = await build(EXAMPLE, { key: a.priv, out: path.join(dir, 'dist'), version: 'test.4' });
  assert.ok(!verifyPack(outDir, b.pub).ok);
});

test('local sources cannot escape the pack folder', async () => {
  const dir = tmp();
  const pk = path.join(dir, 'p');
  fs.mkdirSync(pk);
  fs.writeFileSync(path.join(dir, 'secret.txt'), 's');
  fs.writeFileSync(path.join(pk, 'pack.json'), JSON.stringify({
    name: 'evil',
    items: [{ id: 'x', title: 'X', category: 'c', license: 'CC0-1.0', source: { type: 'local', path: '../secret.txt' } }],
  }));
  await assert.rejects(() => build(pk, { out: path.join(dir, 'dist') }), /escapes the pack folder/);
});

test('build writes manifest.js and verify catches a changed copy', async () => {
  const dir = tmp();
  const { priv, pub } = keygen(path.join(dir, 'keys'));
  const { outDir } = await build(EXAMPLE, { key: priv, out: path.join(dir, 'dist'), version: 'test.5' });
  const mjs = path.join(outDir, 'manifest.js');
  assert.ok(fs.existsSync(mjs), 'manifest.js written');
  assert.ok(fs.readFileSync(mjs, 'utf8').startsWith('window.OPENPACK_MANIFEST = '));
  assert.ok(verifyPack(outDir, pub).ok);
  fs.writeFileSync(mjs, fs.readFileSync(mjs, 'utf8').replace('"test.5"', '"evil"'));
  const r = verifyPack(outDir, pub);
  assert.ok(r.problems.some((p) => p.includes('manifest.js does not match')));
});
