'use strict';
// Step-by-step picture lessons (kind: "lesson").
//
// A lesson is one JSON file. The builder checks it and turns it into a small script file that
// the portal can load both from a server and straight from a folder (file://).
//
//   {
//     "format": "openpack-lesson/1",
//     "reviewed": false,                      // true once a qualified person has checked it
//     "warning": { "en": "...", "my": "..." },            (optional)
//     "steps":   [ { "svg": "<svg ...>", "en": "...", "my": "..." } ],
//     "why":     [ { "en": "...", "my": "..." } ],         (optional)
//     "tips":    [ { "en": "...", "my": "..." } ],         (optional)
//     "sources": [ { "name": "...", "url": "https://..." } ]   (required: where the facts come from)
//   }
const { checkZawgyi } = require('./zawgyi');

const FORMAT = 'openpack-lesson/1';
const MAX_STEPS = 12;
const MAX_SVG = 20000;

// Pictures are drawn into the page, so they must be plain drawings: no scripts, no outside files.
function svgProblems(svg) {
  const out = [];
  if (typeof svg !== 'string' || !/^\s*<svg[\s>]/i.test(svg) || !/<\/svg>\s*$/i.test(svg)) return ['must be one <svg>...</svg> drawing'];
  if (svg.length > MAX_SVG) out.push(`is too large (${svg.length} characters, limit ${MAX_SVG})`);
  if (!/viewBox=/.test(svg)) out.push('needs a viewBox so it scales on small screens');
  if (/<\s*(script|foreignObject|iframe|object|embed|image|use|a|style|animate|set)\b/i.test(svg)) out.push('uses an element that is not allowed (script, image, use, a, style, animation...)');
  if (/\son[a-z]+\s*=/i.test(svg)) out.push('has an event handler attribute');
  if (/(href|src)\s*=/i.test(svg)) out.push('links to something (href/src are not allowed)');
  if (/javascript:|data:|url\(\s*['"]?(?!#)/i.test(svg)) out.push('refers to an outside resource');
  if (/<!|<\?/.test(svg)) out.push('has a comment, doctype or processing instruction');
  return out;
}

function textPair(v, where, errors, required) {
  if (v === undefined && !required) return;
  if (!v || typeof v !== 'object' || typeof v.en !== 'string' || !v.en.trim() || typeof v.my !== 'string' || !v.my.trim()) {
    errors.push(`${where}: needs both "en" and "my" text`);
    return;
  }
  if (/[က-႟]/.test(v.en)) errors.push(`${where}: "en" contains Burmese`);
  if (!/[က-႟]/.test(v.my)) errors.push(`${where}: "my" has no Burmese`);
  const z = checkZawgyi(v.my);
  if (z.zawgyi) errors.push(`${where}: "my" looks like Zawgyi (${z.reasons[0]}). Convert to Unicode first`);
}

function validateLesson(l) {
  const errors = [];
  if (!l || typeof l !== 'object' || Array.isArray(l)) return ['lesson must be a JSON object'];
  if (l.format !== FORMAT) errors.push(`"format" must be "${FORMAT}"`);
  if (typeof l.reviewed !== 'boolean') errors.push('"reviewed" must be true or false (has a qualified person checked this lesson?)');
  textPair(l.warning, 'warning', errors, false);

  if (!Array.isArray(l.steps) || l.steps.length < 2) errors.push('"steps" needs at least 2 steps');
  else {
    if (l.steps.length > MAX_STEPS) errors.push(`too many steps (${l.steps.length}, limit ${MAX_STEPS}): split the lesson`);
    l.steps.forEach((s, n) => {
      const where = `step ${n + 1}`;
      textPair(s, where, errors, true);
      for (const p of svgProblems(s && s.svg)) errors.push(`${where}: picture ${p}`);
    });
  }
  for (const key of ['why', 'tips']) {
    if (l[key] === undefined) continue;
    if (!Array.isArray(l[key])) { errors.push(`"${key}" must be a list`); continue; }
    l[key].forEach((t, n) => textPair(t, `${key} ${n + 1}`, errors, true));
  }
  if (!Array.isArray(l.sources) || !l.sources.length) errors.push('"sources" needs at least one source for the facts in the lesson');
  else {
    l.sources.forEach((s, n) => {
      if (!s || typeof s.name !== 'string' || !s.name.trim()) errors.push(`source ${n + 1}: missing "name"`);
      if (s && s.url !== undefined && !/^https:\/\/[^\s"'<>]+$/.test(s.url)) errors.push(`source ${n + 1}: "url" must be an https link`);
    });
  }
  return errors;
}

// The file that ships in the pack. Only known fields are copied, in a fixed order, so the same
// lesson always gives the same file (and the same checksum).
function lessonScript(id, l) {
  const pair = (v) => ({ en: v.en, my: v.my });
  const clean = {
    reviewed: l.reviewed,
    warning: l.warning ? pair(l.warning) : null,
    steps: l.steps.map((s) => ({ svg: s.svg.trim(), en: s.en, my: s.my })),
    why: (l.why || []).map(pair),
    tips: (l.tips || []).map(pair),
    sources: l.sources.map((s) => ({ name: s.name, url: s.url || null })),
  };
  // "</" is escaped so the text can never close a script tag.
  const json = JSON.stringify(clean).replace(/<\//g, '<\\/');
  return `window.OPENPACK_LESSONS=window.OPENPACK_LESSONS||{};window.OPENPACK_LESSONS[${JSON.stringify(id)}]=${json};\n`;
}

module.exports = { FORMAT, validateLesson, lessonScript, svgProblems };
