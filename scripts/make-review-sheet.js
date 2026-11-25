#!/usr/bin/env node
'use strict';
// Writes docs/burmese-review.csv: every Burmese label next to its English meaning, with empty
// columns for a native speaker to fill in. Opens in Excel, LibreOffice or Google Sheets.
// Re-run after changing portal/strings.js:  node scripts/make-review-sheet.js
const fs = require('node:fs');
const path = require('node:path');
const { load } = require('../lib/portal-data');

const root = path.resolve(__dirname, '..');
const P = load();
const WHERE = [
  [/^(app_|offline_|pack_|browse|items_)/, 'Home screen'], [/^(search_|results_|no_results)/, 'Search'],
  [/^(back|help|settings|share)$/, 'Top bar / buttons'], [/^(details|d_)/, 'Item details'],
  [/^type_/, 'Item type word'], [/^act_/, 'Big button on an item'], [/^install_/, 'Under an app item'],
  [/^(set_|size_|theme_|sample_)/, 'Settings screen'], [/^(help_|h\d_)/, 'Help screen'],
  [/^share_/, 'Share screen'], [/^(err_|footer)/, 'Messages'],
];
const where = (k) => (WHERE.find(([re]) => re.test(k)) || [null, 'Other'])[1];

const rows = [['ID', 'Where it appears', 'English', 'Burmese (current)', 'OK? (yes/no)', 'Better Burmese', 'Notes']];
for (const k of Object.keys(P.strings.en)) rows.push([`strings.${k}`, where(k), P.strings.en[k], P.strings.my[k], '', '', '']);
for (const [id, c] of Object.entries(P.categories)) rows.push([`category.${id}`, 'Home screen tile', c.en, c.my, '', '', '']);

const MY = /[က-႟]/;
// Lessons: titles, then every step, reason and tip.
const lessonsDir = path.join(root, 'lessons');
const lessonIndex = JSON.parse(fs.readFileSync(path.join(lessonsDir, 'index.json'), 'utf8'));
for (const meta of lessonIndex.lessons) {
  const l = JSON.parse(fs.readFileSync(path.join(lessonsDir, `${meta.id}.lesson.json`), 'utf8'));
  const id = `lesson ${meta.id}`;
  rows.push([`${id}: title`, 'Lesson title', meta.title, meta.title_my, '', '', '']);
  rows.push([`${id}: description`, 'Lesson card', meta.description, meta.description_my, '', '', '']);
  if (l.warning) rows.push([`${id}: warning`, 'Lesson warning box', l.warning.en, l.warning.my, '', '', '']);
  l.steps.forEach((s, n) => rows.push([`${id}: step ${n + 1}`, 'Lesson step', s.en, s.my, '', '', '']));
  (l.why || []).forEach((s, n) => rows.push([`${id}: why ${n + 1}`, 'Lesson "Why" tab', s.en, s.my, '', '', '']));
  (l.tips || []).forEach((s, n) => rows.push([`${id}: tip ${n + 1}`, 'Lesson "Tips" tab', s.en, s.my, '', '', '']));
}

const extra = [['bundle/START-HERE.txt', 'Instructions file'], ['README.my.md', 'Burmese README'], ['portal/index.html', 'Shown when JavaScript is off']];
for (const [file, label] of extra) {
  const lines = fs.readFileSync(path.join(root, file), 'utf8').split(/\r?\n/);
  lines.forEach((line, n) => {
    if (MY.test(line)) rows.push([`${file}:${n + 1}`, label, '', line.replace(/<[^>]+>/g, ' ').trim(), '', '', '']);
  });
}

const cell = (v) => '"' + String(v).replace(/"/g, '""') + '"';
const csv = '﻿' + rows.map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n';
const out = path.join(root, 'docs', 'burmese-review.csv');
fs.writeFileSync(out, csv);
console.log(`${rows.length - 1} rows -> ${path.relative(root, out)}`);
