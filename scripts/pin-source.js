#!/usr/bin/env node
'use strict';
// Fills in "sha256" for every http item in a pack.json that does not have one yet.
//
//   node scripts/pin-source.js packs/<name>/pack.json            # pin what is missing
//   node scripts/pin-source.js packs/<name>/pack.json --all      # re-pin everything
//
// It first looks for the publisher's own checksum file at <url>.sha256 (Kiwix publishes these).
// If there is none it downloads the file and hashes it. Run this on a machine with internet,
// CHECK the printed values against the publisher's page, then commit pack.json.
const fs = require('node:fs');
const https = require('node:https');
const crypto = require('node:crypto');

const file = process.argv[2];
const all = process.argv.includes('--all');
if (!file) { console.error('usage: pin-source.js <pack.json> [--all]'); process.exit(2); }

function get(url, redirects = 5) {
  return new Promise((resolve, reject) => {
    if (!url.startsWith('https://')) return reject(new Error('https only: ' + url));
    https.get(url, { headers: { 'user-agent': 'openpack-pin' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && redirects > 0) {
        res.resume();
        return resolve(get(new URL(res.headers.location, url).toString(), redirects - 1));
      }
      if (res.statusCode !== 200) { res.resume(); return reject(new Error(`HTTP ${res.statusCode} for ${url}`)); }
      resolve(res);
    }).on('error', reject);
  });
}
async function text(url) {
  const res = await get(url);
  let s = '';
  for await (const chunk of res) { s += chunk; if (s.length > 4096) break; }
  return s;
}
async function hashDownload(url) {
  const res = await get(url);
  const h = crypto.createHash('sha256');
  let n = 0;
  for await (const chunk of res) { h.update(chunk); n += chunk.length; }
  return { sha: h.digest('hex'), bytes: n };
}

(async () => {
  const cfg = JSON.parse(fs.readFileSync(file, 'utf8'));
  let changed = 0, failed = 0;
  for (const it of cfg.items || []) {
    const src = it.source || {};
    if (!src.url || (src.sha256 && !all)) continue;
    try {
      let sha = null, how = '';
      try {
        const m = /\b[a-f0-9]{64}\b/i.exec(await text(src.url + '.sha256'));
        if (m) { sha = m[0].toLowerCase(); how = 'publisher checksum file'; }
      } catch (e) { /* no checksum file: fall through to download */ }
      if (!sha) { const r = await hashDownload(src.url); sha = r.sha; how = `downloaded ${r.bytes} bytes`; }
      src.sha256 = sha;
      changed++;
      console.log(`pinned ${it.id}: ${sha} (${how})`);
    } catch (e) { failed++; console.error(`FAILED ${it.id}: ${e.message}`); }
  }
  if (changed) fs.writeFileSync(file, JSON.stringify(cfg, null, 2) + '\n');
  console.log(`${changed} pinned, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})();
