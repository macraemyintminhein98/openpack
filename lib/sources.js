'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function sha256File(file) {
  const h = crypto.createHash('sha256');
  h.update(fs.readFileSync(file));
  return h.digest('hex');
}

function safeName(s) {
  return String(s).replace(/[^a-zA-Z0-9._-]+/g, '_').slice(0, 120) || 'file';
}

// Fetch one item into outFile. Supports "local" and "http" sources.
async function fetchSource(item, packDir, outFile) {
  const src = item.source || {};
  fs.mkdirSync(path.dirname(outFile), { recursive: true });

  if (src.type === 'local') {
    const from = path.resolve(packDir, src.path);
    if (!from.startsWith(path.resolve(packDir) + path.sep)) {
      throw new Error(`local source escapes the pack folder: ${src.path}`);
    }
    fs.copyFileSync(from, outFile);
    return;
  }

  if (src.type === 'http') {
    if (!/^https:\/\//.test(src.url)) throw new Error('http sources must use https://');
    const res = await fetch(src.url, { redirect: 'follow' });
    if (!res.ok) throw new Error(`HTTP ${res.status} for ${src.url}`);
    const buf = Buffer.from(await res.arrayBuffer());
    fs.writeFileSync(outFile, buf);
    if (src.sha256) {
      const got = sha256File(outFile);
      if (got !== src.sha256) {
        fs.rmSync(outFile, { force: true });
        throw new Error(`sha256 mismatch for ${src.url}: expected ${src.sha256}, got ${got}`);
      }
    }
    return;
  }

  throw new Error(`unknown source type "${src.type}" (use "local" or "http")`);
}

module.exports = { fetchSource, sha256File, safeName };
