#!/usr/bin/env node
'use strict';
// Writes docs/lesson-review.md: every lesson, line by line, with its sources and a tick box,
// so a health professional can review all lessons in about an hour.
//   node scripts/make-review-packet.js
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const dir = path.join(root, 'lessons');
const index = JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8'));

const out = [`# Lesson review packet

**For the reviewer.** These ${index.lessons.length} short lessons will be shown to the public in Myanmar on phones,
offline. They were written by an AI from the sources listed under each lesson. Nobody
qualified has checked them yet, and the app tells readers so.

Please check each line for two things:

1. **Is it correct and safe** for an untrained person with no equipment?
2. **Is the Burmese clear** and does it say the same as the English?

Tick the box if a line is fine. If not, write the correction next to it. When a whole lesson
is approved, sign the line at its end. The maintainer then sets \`"reviewed": true\` for that
lesson and records your name and date in the lesson file's history.

Left out on purpose because a small mistake can kill: home-made rehydration recipes,
bleach or chlorine doses, medicine doses, pressure bandaging for snake bite, tourniquets,
and identifying wild plants. Tell us if you think any of these should be added, and how.
`];

for (const meta of index.lessons) {
  const l = JSON.parse(fs.readFileSync(path.join(dir, `${meta.id}.lesson.json`), 'utf8'));
  out.push(`\n---\n\n## ${meta.title} / ${meta.title_my}\n`);
  out.push(`Status: ${l.reviewed ? 'reviewed' : '**not reviewed**'} · file: \`lessons/${meta.id}.lesson.json\`\n`);
  out.push('Sources:');
  for (const s of l.sources) out.push(`- ${s.name}${s.url ? `: ${s.url}` : ''}`);
  const line = (label, v) => out.push(`- [ ] **${label}** ${v.en}\n      ${v.my}`);
  out.push('');
  if (l.warning) line('Warning.', l.warning);
  l.steps.forEach((s, n) => line(`Step ${n + 1}.`, s));
  (l.why || []).forEach((s, n) => line(`Why ${n + 1}.`, s));
  (l.tips || []).forEach((s, n) => line(`Tip ${n + 1}.`, s));
  out.push(`\nPictures checked (they match the steps and show nothing unsafe): [ ]\n`);
  out.push('Approved by: ______________________  Role: ______________  Date: __________');
}
const file = path.join(root, 'docs', 'lesson-review.md');
fs.writeFileSync(file, out.join('\n') + '\n');
console.log(`${index.lessons.length} lessons -> ${path.relative(root, file)}`);
