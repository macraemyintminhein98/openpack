'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { checkLicense, NON_COMMERCIAL } = require('./licenses');
const { checkZawgyi } = require('./zawgyi');
const { fetchSource, sha256File, safeName } = require('./sources');
const { sign, verify } = require('./sign');
const portalData = require('./portal-data');
const { validateLesson, lessonScript } = require('./lesson');

const FORMAT = 1;
const MANIFEST_JS_PREFIX = 'window.OPENPACK_MANIFEST = ';
// "file" = plain download, "zim" = Kiwix archive served by kiwix-serve on the box,
// "apk" = Android app (offline sharing, replaces SHAREit-style sideloading),
// "lesson" = step-by-step picture lesson shown inside the portal (see lib/lesson.js).
const KINDS = new Set(['file', 'zim', 'apk', 'lesson']);
const KIND_EXT = { zim: '.zim', apk: '.apk', lesson: '.json' };

const LESSONS_DIR = path.resolve(__dirname, '..', 'lessons');
// The lessons listed in lessons/index.json, as pack items.
function standardLessons() {
  const indexFile = path.join(LESSONS_DIR, 'index.json');
  if (!fs.existsSync(indexFile)) return [];
  const index = JSON.parse(fs.readFileSync(indexFile, 'utf8'));
  return index.lessons.map((l) => ({
    ...l, category: index.category, kind: 'lesson', source: { type: 'builtin', path: `${l.id}.lesson.json` },
  }));
}

function weekVersion(d = new Date()) {
  // YYYY.WW (ISO-ish week number), e.g. 2026.48
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((t - yearStart) / 86400000 + 1) / 7);
  return `${t.getUTCFullYear()}.${String(week).padStart(2, '0')}`;
}

function validateConfig(cfg) {
  const errors = [];
  if (!cfg.name) errors.push('pack.json: missing "name"');
  if (!Array.isArray(cfg.items) || cfg.items.length === 0) errors.push('pack.json: "items" must be a non-empty array');
  const ids = new Set();
  for (const it of cfg.items || []) {
    const tag = `item "${it.id || '?'}"`;
    if (!it.id) errors.push(`${tag}: missing "id"`);
    if (it.id && ids.has(it.id)) errors.push(`${tag}: duplicate id`);
    ids.add(it.id);
    if (!it.title) errors.push(`${tag}: missing "title"`);
    if (!it.category) errors.push(`${tag}: missing "category"`);
    if (!it.source) errors.push(`${tag}: missing "source"`);
    for (const e of checkLicense(it)) errors.push(`${tag}: ${e}`);

    const kind = it.kind || 'file';
    if (!KINDS.has(kind)) errors.push(`${tag}: unknown kind "${kind}" (use file, zim, apk or lesson)`);
    const srcName = (it.source && (it.source.path || it.source.url)) || '';
    if (KIND_EXT[kind] && !srcName.toLowerCase().endsWith(KIND_EXT[kind])) {
      errors.push(`${tag}: kind "${kind}" expects a ${KIND_EXT[kind]} file`);
    }
    if (kind === 'apk' && !(it.source && it.source.sha256)) {
      errors.push(`${tag}: apk items must pin "source.sha256" (verify against the developer's published hash)`);
    }

    // Burmese text must be Unicode, not Zawgyi.
    for (const field of ['title_my', 'description_my']) {
      const z = checkZawgyi(it[field]);
      if (z.zawgyi) errors.push(`${tag}: "${field}" looks like Zawgyi (${z.reasons[0]}). Convert to Unicode first`);
    }
  }
  for (const field of ['title_my']) {
    const z = checkZawgyi(cfg[field]);
    if (z.zawgyi) errors.push(`pack.json: "${field}" looks like Zawgyi (${z.reasons[0]}). Convert to Unicode first`);
  }
  errors.push(...validatePortalOptions(cfg));
  return errors;
}

// Optional pack.json settings for the portal screen: category tiles, label overrides, default language.
function validatePortalOptions(cfg) {
  const errors = [];
  const { strings, icons } = portalData.load();

  if (cfg.default_lang !== undefined && !['my', 'en'].includes(cfg.default_lang)) {
    errors.push('pack.json: "default_lang" must be "my" or "en"');
  }

  if (cfg.categories !== undefined) {
    if (!Array.isArray(cfg.categories)) errors.push('pack.json: "categories" must be an array');
    else {
      const seen = new Set();
      for (const c of cfg.categories) {
        const tag = `category "${(c && c.id) || '?'}"`;
        if (!c || typeof c.id !== 'string' || !c.id) { errors.push(`${tag}: missing "id"`); continue; }
        if (seen.has(c.id)) errors.push(`${tag}: duplicate id`);
        seen.add(c.id);
        if (c.icon !== undefined && !icons[c.icon]) {
          errors.push(`${tag}: unknown icon "${c.icon}" (use one of: ${Object.keys(icons).join(', ')})`);
        }
        const z = checkZawgyi(c.title_my);
        if (z.zawgyi) errors.push(`${tag}: "title_my" looks like Zawgyi (${z.reasons[0]}). Convert to Unicode first`);
      }
    }
  }

  if (cfg.ui !== undefined) {
    if (!cfg.ui || typeof cfg.ui !== 'object' || Array.isArray(cfg.ui)) errors.push('pack.json: "ui" must be an object like {"my": {...}, "en": {...}}');
    else {
      for (const lang of Object.keys(cfg.ui)) {
        if (!strings[lang]) { errors.push(`ui: unknown language "${lang}" (use my or en)`); continue; }
        for (const [key, val] of Object.entries(cfg.ui[lang] || {})) {
          if (!(key in strings[lang])) { errors.push(`ui.${lang}.${key}: unknown label (see portal/strings.js)`); continue; }
          if (typeof val !== 'string' || !val.trim()) { errors.push(`ui.${lang}.${key}: must be a non-empty text`); continue; }
          if (portalData.placeholders(val) !== portalData.placeholders(strings[lang][key])) {
            errors.push(`ui.${lang}.${key}: keep the same {placeholders} as the original label`);
          }
          if (lang === 'my') {
            const z = checkZawgyi(val);
            if (z.zawgyi) errors.push(`ui.my.${key}: looks like Zawgyi (${z.reasons[0]}). Convert to Unicode first`);
          }
        }
      }
    }
  }
  return errors;
}

async function build(packDir, opts = {}) {
  const cfgPath = path.join(packDir, 'pack.json');
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));

  const errors = validateConfig(cfg);
  if (errors.length) {
    const err = new Error('Pack config rejected:\n  - ' + errors.join('\n  - '));
    err.errors = errors;
    throw err;
  }

  // Standard lessons (the lessons/ folder of this project) go into every pack, so no pack ships
  // without the emergency guides. A pack can opt out with "lessons": false in pack.json.
  if (cfg.lessons !== false) {
    const have = new Set(cfg.items.map((i) => i.id));
    for (const l of standardLessons()) if (!have.has(l.id)) cfg.items.push(l);
  }

  const version = opts.version || (cfg.version && cfg.version !== 'auto' ? cfg.version : weekVersion());
  const outRoot = path.resolve(opts.out || 'dist');
  const outDir = path.join(outRoot, `${cfg.name}-${version}`);
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(path.join(outDir, 'content'), { recursive: true });

  const items = [];
  for (const it of cfg.items) {
    const kind = it.kind || 'file';
    const ext = path.extname(it.source.path || it.source.url || '') || '';
    let rel = path.posix.join('content', safeName(it.category), `${safeName(it.id)}${ext}`);
    let outFile = path.join(outDir, rel);
    if (it.source.type === 'builtin') {
      fs.mkdirSync(path.dirname(outFile), { recursive: true });
      fs.copyFileSync(path.join(LESSONS_DIR, path.basename(it.source.path)), outFile);
    } else {
      await fetchSource(it, packDir, outFile);
    }
    if (kind === 'lesson') {
      // Check the lesson, then ship it as a script file the portal can load from any folder.
      let lesson;
      try { lesson = JSON.parse(fs.readFileSync(outFile, 'utf8')); }
      catch (e) { throw new Error(`item "${it.id}": lesson file is not valid JSON (${e.message})`); }
      const problems = validateLesson(lesson);
      if (problems.length) {
        const err = new Error(`Lesson "${it.id}" rejected:\n  - ` + problems.join('\n  - '));
        err.errors = problems;
        throw err;
      }
      fs.rmSync(outFile);
      rel = path.posix.join('content', safeName(it.category), `${safeName(it.id)}.lesson.js`);
      outFile = path.join(outDir, rel);
      fs.writeFileSync(outFile, lessonScript(it.id, lesson));
    }
    items.push({
      id: it.id,
      title: it.title,
      title_my: it.title_my || null,
      description: it.description || null,
      description_my: it.description_my || null,
      category: it.category,
      kind,
      path: rel,
      bytes: fs.statSync(outFile).size,
      sha256: sha256File(outFile),
      license: it.license,
      non_commercial: NON_COMMERCIAL.has(it.license),
      attribution: it.attribution || null,
      source_code: it.source_code || null,
      origin: it.source.url || it.origin || null,
    });
  }

  // Copy the portal so the pack is self-contained and can be dropped on a box as-is.
  const portalSrc = path.resolve(__dirname, '..', 'portal');
  fs.cpSync(portalSrc, outDir, { recursive: true });

  const manifest = {
    format: FORMAT,
    name: cfg.name,
    title: cfg.title || cfg.name,
    title_my: cfg.title_my || null,
    version,
    created: new Date().toISOString(),
    items,
    total_bytes: items.reduce((s, i) => s + i.bytes, 0),
  };
  // Optional portal settings, passed through untouched (already validated above).
  if (cfg.default_lang) manifest.default_lang = cfg.default_lang;
  if (cfg.categories) {
    manifest.categories = cfg.categories.map((c) => ({
      id: c.id, title: c.title || null, title_my: c.title_my || null, icon: c.icon || null,
    }));
  }
  if (cfg.ui) manifest.ui = cfg.ui;
  const manifestBytes = Buffer.from(JSON.stringify(manifest, null, 2));
  fs.writeFileSync(path.join(outDir, 'manifest.json'), manifestBytes);
  // Same data as a script file, so the portal also works when opened straight from a
  // folder or USB stick (file:// pages cannot fetch() JSON). verify checks it matches.
  fs.writeFileSync(path.join(outDir, 'manifest.js'), MANIFEST_JS_PREFIX + manifestBytes.toString('utf8') + ';\n');

  let signed = false;
  if (opts.key) {
    fs.writeFileSync(path.join(outDir, 'manifest.sig'), sign(manifestBytes, opts.key));
    signed = true;
  }

  return { outDir, manifest, signed };
}

function verifyPack(packOutDir, pubKeyPath) {
  const problems = [];
  const mPath = path.join(packOutDir, 'manifest.json');
  if (!fs.existsSync(mPath)) return { ok: false, problems: ['manifest.json not found'] };
  const bytes = fs.readFileSync(mPath);
  const manifest = JSON.parse(bytes.toString('utf8'));

  if (pubKeyPath) {
    const sigPath = path.join(packOutDir, 'manifest.sig');
    if (!fs.existsSync(sigPath)) problems.push('manifest.sig missing (pack is unsigned)');
    else if (!verify(bytes, fs.readFileSync(sigPath, 'utf8').trim(), pubKeyPath)) {
      problems.push('SIGNATURE INVALID: manifest was changed or signed by a different key');
    }
  }

  const mjs = path.join(packOutDir, 'manifest.js');
  if (fs.existsSync(mjs)) {
    const txt = fs.readFileSync(mjs, 'utf8');
    let same = false;
    try {
      const body = txt.slice(txt.indexOf('{'), txt.lastIndexOf('}') + 1);
      same = JSON.stringify(JSON.parse(body)) === JSON.stringify(manifest);
    } catch (e) { same = false; }
    if (!same) problems.push('manifest.js does not match manifest.json');
  }

  for (const it of manifest.items) {
    const f = path.join(packOutDir, it.path);
    if (!fs.existsSync(f)) { problems.push(`missing file: ${it.path}`); continue; }
    if (sha256File(f) !== it.sha256) problems.push(`HASH MISMATCH: ${it.path}`);
  }
  return { ok: problems.length === 0, problems, manifest };
}

module.exports = { build, verifyPack, validateConfig, weekVersion, standardLessons };
