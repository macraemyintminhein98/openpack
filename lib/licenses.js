'use strict';
// The licence gate. OpenPack only bundles content that may be redistributed.
// Anything else must carry explicit written permission (proof field).

const REDISTRIBUTABLE = new Set([
  'CC0-1.0',
  'public-domain',
  'CC-BY-4.0',
  'CC-BY-SA-4.0',
  'CC-BY-3.0',
  'CC-BY-SA-3.0',
  'CC-BY-NC-4.0', // allowed in the pack, but flagged non-commercial in the manifest
  'MIT',
  'Apache-2.0',
  'BSD-3-Clause',
  'ODbL-1.0',
  'GFDL-1.3',
  // Copyleft software (typically APKs). Allowed only with a link to the matching source.
  'GPL-2.0',
  'GPL-3.0',
  'LGPL-3.0',
  'AGPL-3.0',
  'MPL-2.0',
  'permission', // written permission from the rights holder; requires `proof`
]);

const NON_COMMERCIAL = new Set(['CC-BY-NC-4.0']);
// Redistributing binaries under these requires offering the corresponding source.
const NEEDS_SOURCE = new Set(['GPL-2.0', 'GPL-3.0', 'LGPL-3.0', 'AGPL-3.0', 'MPL-2.0']);

function checkLicense(item) {
  const errors = [];
  if (!item.license) {
    errors.push('missing "license"');
    return errors;
  }
  if (!REDISTRIBUTABLE.has(item.license)) {
    errors.push(`licence "${item.license}" is not on the redistributable list`);
  }
  if (item.license === 'permission' && !item.proof) {
    errors.push('licence "permission" requires a "proof" field (link or note on where the written permission is kept)');
  }
  if (NEEDS_SOURCE.has(item.license) && !/^https:\/\//.test(item.source_code || '')) {
    errors.push(`licence "${item.license}" requires a "source_code" https link to the matching source`);
  }
  if (item.license !== 'public-domain' && item.license !== 'CC0-1.0' && !item.attribution) {
    errors.push('missing "attribution" (author or rights holder)');
  }
  return errors;
}

module.exports = { REDISTRIBUTABLE, NON_COMMERCIAL, NEEDS_SOURCE, checkLicense };
