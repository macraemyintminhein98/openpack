'use strict';
// Compares two pack manifests and describes what changed, for the weekly release note.
function diffManifests(prev, next) {
  const before = new Map(((prev && prev.items) || []).map((i) => [i.id, i]));
  const after = new Map(next.items.map((i) => [i.id, i]));
  const added = [], updated = [], removed = [];
  for (const [id, it] of after) {
    if (!before.has(id)) added.push(it);
    else if (before.get(id).sha256 !== it.sha256) updated.push(it);
  }
  for (const [id, it] of before) if (!after.has(id)) removed.push(it);
  return { added, updated, removed, unchanged: after.size - added.length - updated.length };
}

function mb(n) { return n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'; }

function changesText(prev, next) {
  const d = diffManifests(prev, next);
  const line = (i) => `  - ${i.title}${i.title_my ? ' / ' + i.title_my : ''} (${i.category}, ${mb(i.bytes)}, ${i.license})`;
  const out = [`${next.title || next.name} ${next.version}`, `Built: ${next.created}`,
    `Items: ${next.items.length}, total ${mb(next.total_bytes)}`,
    prev ? `Compared with: ${prev.version}` : 'First release (nothing to compare with)', ''];
  const section = (name, list) => { if (list.length) { out.push(`${name} (${list.length}):`, ...list.map(line), ''); } };
  section('New', d.added);
  section('Updated', d.updated);
  section('Removed', d.removed);
  if (prev && !d.added.length && !d.updated.length && !d.removed.length) out.push('No content changes since the last release.', '');
  return out.join('\n');
}

module.exports = { diffManifests, changesText };
