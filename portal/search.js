/* Search for the portal. Plain ES5.

   Burmese needs three things an ordinary "find this word" search does not give:
   1. Many phones type Zawgyi, not Unicode. Zawgyi typing is detected and converted first
      (rules in zawgyi-rules.js), so people find things whichever keyboard they have.
   2. Burmese is written without spaces between words, and people add spaces differently.
      Spaces inside Burmese text are ignored when matching.
   3. If the exact phrase is not found, items containing all of its syllables still match,
      ranked lower.
   Results are ranked: matches in the title come first. */
(function () {
  'use strict';

  function c(n) { return String.fromCharCode(n); }
  var MY = '[' + c(0x1000) + '-' + c(0x109F) + c(0xAA60) + '-' + c(0xAA7F) + ']';
  var RE_MY = new RegExp(MY);
  var RE_INVISIBLE = new RegExp('[' + c(0x200B) + '-' + c(0x200D) + c(0xFEFF) + ']', 'g');
  var RE_ZG_ONLY = new RegExp('[' + c(0x1060) + '-' + c(0x1097) + ']');
  var RE_ZG_E_FIRST = new RegExp('(^|[\\s' + c(0x104A) + c(0x104B) + '])' + c(0x1031));
  var RE_ZG_VIRAMA_END = new RegExp(c(0x1039) + '(\\s|$|[' + c(0x104A) + c(0x104B) + '])');
  var VIRAMA = 0x1039, ASAT = 0x103A;

  var compiled = null;
  function rules() {
    if (compiled) { return compiled; }
    var raw = (typeof window !== 'undefined' && window.OPENPACK_ZG_RULES) || [];
    compiled = [];
    for (var i = 0; i < raw.length; i++) {
      try { compiled.push([new RegExp(raw[i][0], 'g'), raw[i][1]]); } catch (e) {}
    }
    return compiled;
  }

  // Same conservative markers as the builder's check (lib/zawgyi.js).
  function looksZawgyi(s) {
    if (!s) { return false; }
    return RE_ZG_ONLY.test(s) || RE_ZG_E_FIRST.test(s) || RE_ZG_VIRAMA_END.test(s);
  }
  function zg2uni(s) {
    var r = rules(), out = String(s);
    for (var i = 0; i < r.length; i++) { out = out.replace(r[i][0], r[i][1]); }
    return out;
  }

  function norm(s) {
    s = String(s == null ? '' : s).replace(RE_INVISIBLE, '');
    if (s.normalize) { try { s = s.normalize('NFC'); } catch (e) {} }
    return s.toLowerCase().replace(/\s+/g, ' ').replace(/^ | $/g, '');
  }
  function hasBurmese(s) { return RE_MY.test(s); }
  function compact(s) { return s.replace(/\s+/g, ''); }

  // Can a new syllable start at this character? (consonants, independent vowels, digits, symbols)
  function starter(code) {
    return (code >= 0x1000 && code <= 0x102A) || code === 0x103F || (code >= 0x1040 && code <= 0x1055);
  }
  // Split Burmese text into syllables: break before a consonant unless it is stacked under the
  // previous one (after virama) or closes the syllable (followed by asat or virama).
  function syllables(s) {
    var out = [], cur = '', i, code, prev, next;
    for (i = 0; i < s.length; i++) {
      code = s.charCodeAt(i);
      if (code < 0x1000 || code > 0x109F) {
        if (cur) { out.push(cur); cur = ''; }
        continue;
      }
      prev = i > 0 ? s.charCodeAt(i - 1) : 0;
      next = i + 1 < s.length ? s.charCodeAt(i + 1) : 0;
      if (starter(code) && cur && prev !== VIRAMA && next !== ASAT && next !== VIRAMA) {
        out.push(cur); cur = '';
      }
      cur += s.charAt(i);
    }
    if (cur) { out.push(cur); }
    return out;
  }

  function prepare(text) {
    var n = norm(text);
    return { plain: n, tight: compact(n), syl: null };
  }
  function sylSet(p) {
    if (!p.syl) {
      p.syl = {};
      var list = syllables(p.plain);
      for (var i = 0; i < list.length; i++) { p.syl[list[i]] = 1; }
    }
    return p.syl;
  }

  // 0 = no match. Higher is better.
  function termScore(term, p) {
    if (!hasBurmese(term)) { return p.plain.indexOf(term) >= 0 ? 2 : 0; }
    if (p.tight.indexOf(term) >= 0) { return 2; }
    var syl = syllables(term);
    if (syl.length < 2) { return 0; }
    var set = sylSet(p);
    for (var i = 0; i < syl.length; i++) { if (!set[syl[i]]) { return 0; } }
    return 1;
  }

  function scoreVariant(q, title, body) {
    var terms = q.split(' '), total = 0, i, t, b;
    // The whole Burmese query with its spaces removed, as one phrase.
    if (hasBurmese(q) && terms.length > 1) {
      var whole = compact(q);
      if (title.tight.indexOf(whole) >= 0) { return 40; }
      if (body.tight.indexOf(whole) >= 0) { return 20; }
    }
    for (i = 0; i < terms.length; i++) {
      if (!terms[i]) { continue; }
      t = termScore(terms[i], title);
      b = t ? 0 : termScore(terms[i], body);
      if (!t && !b) { return 0; }
      total += t ? t * 10 : b;
    }
    return total;
  }

  // What the search will actually look for: the typed words, plus a Unicode version if they
  // were typed in Zawgyi.
  function variants(q) {
    var n = norm(q), out = [];
    if (!n) { return out; }
    var u = hasBurmese(n) ? norm(zg2uni(n)) : n;
    if (u && u !== n && looksZawgyi(n)) { out.push(u); out.push(n); return out; }
    out.push(n);
    // Some Zawgyi words carry no tell-tale marker. Try the converted form too, as a last resort.
    if (u && u !== n) { out.push(u); }
    return out;
  }

  // items: manifest items. extra(item): more text to search (for example the category name).
  function search(items, q, extra) {
    var vs = variants(q), hits = [], i, v, best, s, it, title, body;
    if (!vs.length) { return []; }
    for (i = 0; i < items.length; i++) {
      it = items[i];
      title = prepare([it.title, it.title_my].join(' '));
      body = prepare([it.description, it.description_my, it.category, extra ? extra(it) : ''].join(' '));
      best = 0;
      for (v = 0; v < vs.length && !best; v++) { best = scoreVariant(vs[v], title, body); }
      if (best) { hits.push({ item: it, score: best, at: i }); }
    }
    hits.sort(function (a, b) { return b.score - a.score || a.at - b.at; });
    var out = [];
    for (i = 0; i < hits.length; i++) { out.push(hits[i].item); }
    return out;
  }

  // The query as it should be shown back to the person (converted if it was Zawgyi).
  function display(q) { var v = variants(q); return v.length ? v[0] : ''; }

  window.OPENPACK_SEARCH = {
    search: search, display: display, looksZawgyi: looksZawgyi, zg2uni: zg2uni, syllables: syllables, norm: norm
  };
})();
