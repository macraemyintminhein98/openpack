#!/usr/bin/env node
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { build, verifyPack } = require('../lib/build');
const { keygen } = require('../lib/sign');

const HELP = `openpack - offline content packs for low-connectivity places

Usage:
  openpack keygen [dir]                 Create an ed25519 signing keypair (default ./keys)
  openpack build <packDir> [--key K] [--out dist] [--version V]
                                        Build a pack from <packDir>/pack.json
  openpack verify <builtPackDir> [--pub P]
                                        Check file hashes (and signature if --pub given)
  openpack serve <builtPackDir> [--port 8080]
                                        Serve a built pack locally to test the portal
`;

function flag(args, name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8', '.md': 'text/plain; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.mp3': 'audio/mpeg', '.mp4': 'video/mp4', '.pdf': 'application/pdf',
};

async function main() {
  const [cmd, ...args] = process.argv.slice(2);

  if (cmd === 'keygen') {
    const { priv, pub } = keygen(args[0] || 'keys');
    console.log(`Private key: ${priv}  (KEEP SECRET, never commit)`);
    console.log(`Public key:  ${pub}   (ship this with every box)`);
    return;
  }

  if (cmd === 'build') {
    const packDir = args[0];
    if (!packDir) { console.error(HELP); process.exit(1); }
    const { outDir, manifest, signed } = await build(packDir, {
      key: flag(args, '--key'), out: flag(args, '--out'), version: flag(args, '--version'),
    });
    const mb = (manifest.total_bytes / 1048576).toFixed(2);
    console.log(`Built ${manifest.name} v${manifest.version}: ${manifest.items.length} items, ${mb} MB`);
    console.log(`Signed: ${signed ? 'yes' : 'NO (pass --key to sign)'}`);
    console.log(`Output: ${outDir}`);
    return;
  }

  if (cmd === 'verify') {
    const dir = args[0];
    if (!dir) { console.error(HELP); process.exit(1); }
    const r = verifyPack(dir, flag(args, '--pub'));
    if (r.ok) { console.log(`OK: ${r.manifest.items.length} files verified${flag(args, '--pub') ? ', signature valid' : ''}`); return; }
    console.error('FAILED:\n  - ' + r.problems.join('\n  - '));
    process.exit(2);
  }

  if (cmd === 'serve') {
    const root = path.resolve(args[0] || '.');
    const port = Number(flag(args, '--port') || 8080);
    http.createServer((req, res) => {
      let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
      if (p.endsWith('/')) p += 'index.html';
      const file = path.join(root, p);
      if (!file.startsWith(root)) { res.writeHead(403); return res.end(); }
      fs.readFile(file, (err, buf) => {
        if (err) { res.writeHead(404); return res.end('Not found'); }
        res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
        res.end(buf);
      });
    }).listen(port, () => console.log(`Serving ${root} at http://localhost:${port}`));
    return;
  }

  console.log(HELP);
  process.exit(cmd ? 1 : 0);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
