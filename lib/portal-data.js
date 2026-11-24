'use strict';
// Loads the portal's label files (written for the browser) so build and tests can check them.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const dir = path.resolve(__dirname, '..', 'portal');

function load() {
  const sandbox = { window: {} };
  vm.createContext(sandbox);
  for (const f of ['strings.js', 'icons.js', 'zawgyi-rules.js', 'search.js']) {
    vm.runInContext(fs.readFileSync(path.join(dir, f), 'utf8'), sandbox, { filename: f });
  }
  const w = sandbox.window;
  return { strings: w.OPENPACK_STRINGS, categories: w.OPENPACK_CATEGORIES, icons: w.OPENPACK_ICONS, langs: w.OPENPACK_LANGS, search: w.OPENPACK_SEARCH };
}

// {n} style placeholders in a label, sorted, so two languages can be compared.
function placeholders(s) {
  return (String(s).match(/\{[a-z]+\}/g) || []).sort().join(',');
}

module.exports = { load, placeholders, portalDir: dir };
