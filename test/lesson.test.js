'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { validateLesson, lessonScript, svgProblems } = require('../lib/lesson');
const { build, verifyPack, validateConfig, standardLessons } = require('../lib/build');

const SVG = '<svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>';
const good = () => ({
  format: 'openpack-lesson/1', reviewed: false,
  steps: [{ svg: SVG, en: 'One', my: 'တစ်' }, { svg: SVG, en: 'Two', my: 'နှစ်' }],
  sources: [{ name: 'A source', url: 'https://example.org/a' }],
});
const has = (l, text) => assert.ok(validateLesson(l).some((e) => e.includes(text)), `expected "${text}" in ${JSON.stringify(validateLesson(l))}`);

test('a well-formed lesson passes', () => {
  assert.deepStrictEqual(validateLesson(good()), []);
});

test('every standard lesson is valid, indexed, sourced and still marked unreviewed', () => {
  const dir = path.join(__dirname, '..', 'lessons');
  const index = JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8'));
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.lesson.json')).map((f) => f.replace('.lesson.json', '')).sort();
  assert.deepStrictEqual(index.lessons.map((l) => l.id).sort(), files, 'lessons/index.json must list exactly the lesson files');
  assert.ok(files.length >= 9);
  for (const meta of index.lessons) {
    const l = JSON.parse(fs.readFileSync(path.join(dir, `${meta.id}.lesson.json`), 'utf8'));
    assert.deepStrictEqual(validateLesson(l), [], meta.id);
    assert.ok(l.sources.every((s) => s.url), `${meta.id}: every source needs a link`);
    assert.strictEqual(l.reviewed, false, `${meta.id}: only a qualified reviewer may set reviewed to true`);
    assert.ok(/[\u1000-\u109F]/.test(meta.title_my) && /[\u1000-\u109F]/.test(meta.description_my), `${meta.id}: Burmese title and description`);
  }
  assert.deepStrictEqual(validateConfig({ name: 'p', items: standardLessons().map((i) => ({ ...i, source: { type: 'local', path: 'x.json' } })) }), []);
});

test('standard lessons go into every pack unless it opts out', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'op-std-'));
  const pack = path.join(dir, 'pk');
  fs.mkdirSync(path.join(pack, 'content'), { recursive: true });
  fs.writeFileSync(path.join(pack, 'content/a.txt'), 'hello');
  const cfg = { name: 'std', items: [{ id: 'a', title: 'A', category: 'about', license: 'CC0-1.0', source: { type: 'local', path: 'content/a.txt' } }] };
  fs.writeFileSync(path.join(pack, 'pack.json'), JSON.stringify(cfg));
  const withLessons = await build(pack, { out: path.join(dir, 'o1') });
  const n = standardLessons().length;
  assert.strictEqual(withLessons.manifest.items.length, 1 + n);
  assert.ok(withLessons.manifest.items.some((i) => i.id === 'water-safe' && i.kind === 'lesson' && i.category === 'survival'));
  assert.strictEqual(verifyPack(withLessons.outDir).ok, true);
  fs.writeFileSync(path.join(pack, 'pack.json'), JSON.stringify({ ...cfg, lessons: false }));
  const without = await build(pack, { out: path.join(dir, 'o2') });
  assert.strictEqual(without.manifest.items.length, 1);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('lessons must name their sources and say whether they were reviewed', () => {
  const a = good(); delete a.sources; has(a, 'sources');
  const b = good(); delete b.reviewed; has(b, 'reviewed');
  const c = good(); c.sources = [{ name: 'x', url: 'http://insecure.example' }]; has(c, 'https');
});

test('every step needs both languages, real Burmese, and Unicode', () => {
  const a = good(); delete a.steps[0].my; has(a, 'step 1');
  const b = good(); b.steps[1].my = 'not burmese'; has(b, 'no Burmese');
  const c = good(); c.steps[0].my = 'ေၾကညာ'; has(c, 'Zawgyi');
  const d = good(); d.steps = [d.steps[0]]; has(d, 'at least 2');
  const e = good(); e.tips = [{ en: 'only english' }]; has(e, 'tips 1');
});

test('pictures cannot carry scripts, links or outside files', () => {
  const bad = [
    '<svg viewBox="0 0 1 1"><script>alert(1)</script></svg>',
    '<svg viewBox="0 0 1 1" onload="x()"></svg>',
    '<svg viewBox="0 0 1 1"><image href="http://x/y.png"/></svg>',
    '<svg viewBox="0 0 1 1"><a href="javascript:x"><rect/></a></svg>',
    '<svg viewBox="0 0 1 1"><rect fill="url(http://x/#a)"/></svg>',
    '<svg viewBox="0 0 1 1"><foreignObject></foreignObject></svg>',
    '<svg viewBox="0 0 1 1"><style>*{}</style></svg>',
    '<svg viewBox="0 0 1 1"><!-- c --></svg>',
    '<div>not svg</div>',
    '<svg><rect/></svg>',
  ];
  for (const s of bad) assert.ok(svgProblems(s).length > 0, `should be rejected: ${s}`);
  assert.deepStrictEqual(svgProblems(SVG), []);
});

test('the shipped script registers the lesson and cannot break out of a script tag', () => {
  const l = good();
  l.steps[0].en = 'tricky </script><b>';
  const js = lessonScript('demo', l);
  assert.ok(!/<\/script/i.test(js));
  const box = { window: {} };
  vm.runInNewContext(js, box);
  assert.strictEqual(box.window.OPENPACK_LESSONS.demo.steps[0].en, 'tricky </script><b>');
  assert.strictEqual(box.window.OPENPACK_LESSONS.demo.extra, undefined);
  assert.strictEqual(lessonScript('demo', l), js, 'same lesson must give the same file');
});

test('build ships a lesson as a hashed .lesson.js and rejects a broken one', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'op-lesson-'));
  const pack = path.join(dir, 'pk');
  fs.mkdirSync(path.join(pack, 'content'), { recursive: true });
  const cfg = { name: 'lt', title: 'LT', lessons: false, items: [{ id: 'l1', title: 'L1', category: 'survival', kind: 'lesson', license: 'CC0-1.0', source: { type: 'local', path: 'content/l1.json' } }] };
  fs.writeFileSync(path.join(pack, 'pack.json'), JSON.stringify(cfg));
  fs.writeFileSync(path.join(pack, 'content/l1.json'), JSON.stringify(good()));
  const r = await build(pack, { out: path.join(dir, 'out') });
  const item = r.manifest.items[0];
  assert.strictEqual(item.kind, 'lesson');
  assert.match(item.path, /^content\/survival\/l1\.lesson\.js$/);
  assert.ok(fs.existsSync(path.join(r.outDir, item.path)));
  assert.ok(!fs.existsSync(path.join(r.outDir, 'content/survival/l1.json')));
  assert.strictEqual(verifyPack(r.outDir).ok, true);
  fs.appendFileSync(path.join(r.outDir, item.path), '//x');
  assert.strictEqual(verifyPack(r.outDir).ok, false, 'a changed lesson must fail verification');

  const broken = good(); broken.steps[0].svg = '<svg viewBox="0 0 1 1"><script>1</script></svg>';
  fs.writeFileSync(path.join(pack, 'content/l1.json'), JSON.stringify(broken));
  await assert.rejects(build(pack, { out: path.join(dir, 'out2') }), /rejected/);
  fs.rmSync(dir, { recursive: true, force: true });
});
