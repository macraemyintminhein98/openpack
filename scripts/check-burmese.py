#!/usr/bin/env python3
"""Mechanical checks on every Burmese line in docs/burmese-review.csv.

It cannot judge meaning or naturalness. It catches Zawgyi text, broken character order and
syllables that do not exist in real Burmese words.

Needs:  pip install myanmartools
        git clone --depth=1 https://github.com/myanmartools/myanmar-words   (MIT)
        git clone --depth=1 https://github.com/kanaung/wordlists
Usage:  python3 scripts/check-burmese.py <folder holding those two clones>
"""
import csv, glob, io, os, re, sys, collections, unicodedata
WORDS = sys.argv[1] if len(sys.argv) > 1 else '.'
from myanmartools import ZawgyiDetector
det = ZawgyiDetector()
rows = list(csv.reader(io.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'burmese-review.csv'), encoding='utf-8-sig')))[1:]
MY = re.compile(r'[က-႟]')
texts = [(r[0], r[3]) for r in rows if MY.search(r[3])]
print('Burmese lines checked:', len(texts))

# 1. Google's statistical Zawgyi detector (0 = surely Unicode, 1 = surely Zawgyi)
scores = [(det.get_zawgyi_probability(t), i, t) for i, t in texts]
worst = max(scores)
print('Zawgyi detector: highest score %.4f (%s); lines above 0.05: %d' % (worst[0], worst[1], sum(1 for s in scores if s[0] > 0.05)))

# 2. Syllables: every syllable I wrote should exist somewhere in real Burmese word lists
def syllables(s):
    out, cur = [], ''
    for i, ch in enumerate(s):
        c = ord(ch)
        if c < 0x1000 or c > 0x109F:
            if cur: out.append(cur); cur = ''
            continue
        prev = ord(s[i-1]) if i else 0
        nxt = ord(s[i+1]) if i+1 < len(s) else 0
        starter = (0x1000 <= c <= 0x102A) or c == 0x103F or (0x1040 <= c <= 0x1055)
        if starter and cur and prev != 0x1039 and nxt not in (0x103A, 0x1039):
            out.append(cur); cur = ''
        cur += ch
    if cur: out.append(cur)
    return out

known = set(); words = set()
files = glob.glob(WORDS + '/myanmar-words/text-files/*') + glob.glob(WORDS + '/wordlists/*.list') + [WORDS + '/wordlists/wordlist.dic']
for f in files:
    if os.path.isdir(f) or f.endswith('.md'): continue
    try:
        for line in io.open(f, encoding='utf-8-sig', errors='ignore'):
            w = unicodedata.normalize('NFC', line.strip().replace('﻿', '').replace('​', ''))
            if MY.search(w):
                words.add(w)
                for sy in syllables(w): known.add(sy)
    except Exception as e: pass
print('reference: %d words, %d distinct syllables from %d files' % (len(words), len(known), len(files)))

DIGITS = re.compile(r'^[၀-၉၊။]+$')
unknown = collections.defaultdict(list); total = 0
for i, t in texts:
    t = unicodedata.normalize('NFC', t)
    for sy in syllables(t):
        if DIGITS.match(sy): continue
        sy2 = sy.rstrip('၊။')
        if not sy2: continue
        total += 1
        if sy2 not in known: unknown[sy2].append(i)
print('syllables written: %d, not found in any reference word: %d distinct' % (total, len(unknown)))
for sy, where in sorted(unknown.items(), key=lambda kv: -len(kv[1])):
    print('  %-12s x%d  e.g. %s' % (sy, len(where), where[0]))

# 3. Known ordering mistakes
bad = []
for i, t in texts:
    if '့်' in t: bad.append((i, 'asat before dot-below'))
    if re.search(r'[ါ-ှ]{0}([့း်])\1', t): bad.append((i, 'doubled mark'))
    if re.search(r'ြျ|ွျ|ွြ|ှျ|ှြ|ှွ', t): bad.append((i, 'medials out of order'))
    if re.search(r'\s[၊။]', t): bad.append((i, 'space before punctuation'))
    if re.search(r'[ေ][ျ-ှ]', t): bad.append((i, 'vowel before medial'))
    if '​' in t or '‌' in t: bad.append((i, 'hidden zero-width character'))
print('ordering/encoding problems:', len(bad)); [print('  ', b) for b in bad[:20]]
print('--- lines above 0.05 ---')
for sc, i, t in sorted(scores, reverse=True)[:13]:
    print('%.3f  %-28s %s' % (sc, i[:28], t[:60]))
print('--- median score of lines longer than 40 chars: %.5f; max: %.5f' % (sorted(s for s,i,t in scores if len(t)>40)[len([1 for s,i,t in scores if len(t)>40])//2], max(s for s,i,t in scores if len(t)>40)))
terms = ['ဓာတ်ဆား','အပူရှပ်','ကာလဝမ်း','အူရောင်ငန်းဖျား','ဝမ်းလျှော','မြွေဆိပ်','ဖြေဆေး','ကြက်ခြေခတ်','ဓာတ်မီး','ခရာ','အရည်ကြည်ဖု','ကြွက်တက်','မူးဝေ','ရောဂါပိုး','ပိုးသတ်ဆေး','အနည်ထိုင်','ရေကြီး','မုန်တိုင်း','ထမ်းစင်','အမာရွတ်','ခြင်ထောင်','မှတ်ပုံတင်','ကြေညာချက်','စာကြည့်တိုက်','အမည်ပြောင်','လျှို့ဝှက်','ဆက်တင်','သင်ခန်းစာ','ဗဟုသုတ','ယပ်ခတ်','လက်ကောက်','ဆေးမြစ်','သက်ကြီးရွယ်အို','ရှေးဦးသူနာပြု','ဓာတ်ခဲ','အချက်ပြ','လောင်စာ','ပြတင်းပေါက်']
found = [t for t in terms if t in words]; missing = [t for t in terms if t not in words]
print('key terms found as dictionary words: %d of %d' % (len(found), len(terms)))
print('not in the word lists (compounds, newer or loan words; a human should look):', ' '.join(missing))
