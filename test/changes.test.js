'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { diffManifests, changesText } = require('../lib/changes');

const item = (id, sha, over) => ({ id, title: id.toUpperCase(), category: 'c', bytes: 2048, license: 'CC0-1.0', sha256: sha, ...over });
const m = (version, items) => ({ name: 'p', title: 'P', version, created: '2026-01-01T00:00:00.000Z', items, total_bytes: items.length * 2048 });

test('diff finds new, updated and removed items', () => {
  const d = diffManifests(m('1', [item('a', '1'), item('b', '1'), item('c', '1')]), m('2', [item('a', '1'), item('b', '2'), item('d', '1')]));
  assert.deepStrictEqual(d.added.map((i) => i.id), ['d']);
  assert.deepStrictEqual(d.updated.map((i) => i.id), ['b']);
  assert.deepStrictEqual(d.removed.map((i) => i.id), ['c']);
  assert.strictEqual(d.unchanged, 1);
});

test('change note reads sensibly for a first release and for no changes', () => {
  const one = m('1', [item('a', '1', { title_my: 'က' })]);
  assert.match(changesText(null, one), /First release/);
  assert.match(changesText(null, one), /New \(1\):\n {2}- A \/ က \(c, 2 KB, CC0-1.0\)/);
  assert.match(changesText(one, m('2', [item('a', '1')])), /No content changes/);
});
