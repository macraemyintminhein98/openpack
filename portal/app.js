/* OpenPack portal. Pure static, no network calls beyond the box itself.
   Plain ES5 on purpose: it must run on old Android WebViews and old laptop browsers.
   Words on screen are NOT in this file: edit portal/strings.js.

   Two ways to run:
     - served by the openpack server (http://...): fetches manifest.json and features.json
     - opened straight from a USB stick or folder (file://): uses manifest.js, no server needed

   Screens are chosen by the address after the # sign, so the phone's Back button works:
     #/ home   #/c/<category>   #/s/<search words>   #/settings   #/help   #/share */
(function () {
  'use strict';

  var isFile = location.protocol === 'file:';
  var S = window.OPENPACK_STRINGS || { my: {}, en: {} };
  var CATS = window.OPENPACK_CATEGORIES || {};
  var ICONS = window.OPENPACK_ICONS || {};
  var LANGS = window.OPENPACK_LANGS || { my: 'မြန်မာ', en: 'English' };
  var SIZE_PX = [16, 18, 22, 26]; // keep in step with html.sz0..sz3 in style.css
  var SIZE_KEYS = ['size_s', 'size_m', 'size_l', 'size_xl'];

  var state = {
    manifest: null,
    features: { kiwix: false, urls: [], notices: 0 },
    set: { lang: 'my', size: 1, theme: 'light' }
  };

  function $(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function ic(name) {
    return '<svg class="ic" width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + (ICONS[name] || ICONS.folder || '') + '</svg>';
  }

  /* ---------- labels ---------- */
  // A pack may override single labels in pack.json ("ui"); otherwise strings.js; English as last resort.
  function t(key, vars) {
    var lang = state.set.lang;
    var ui = (state.manifest && state.manifest.ui && state.manifest.ui[lang]) || {};
    var s = ui[key] || (S[lang] && S[lang][key]) || (S.en && S.en[key]) || key;
    if (vars) { for (var k in vars) { if (vars.hasOwnProperty(k)) { s = s.split('{' + k + '}').join(vars[k]); } } }
    return s;
  }
  function pick(obj, key) { return (state.set.lang === 'my' && obj[key + '_my']) || obj[key]; }
  function size(n) { return n > 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'; }

  /* ---------- settings (saved on this device) ---------- */
  function defaultSettings() {
    var d = state.manifest && state.manifest.default_lang;
    return { lang: d === 'en' ? 'en' : 'my', size: 1, theme: 'light' };
  }
  function loadSettings() {
    var s = defaultSettings();
    try {
      var o = JSON.parse(localStorage.getItem('op_settings') || 'null');
      if (o) {
        if (o.lang === 'my' || o.lang === 'en') { s.lang = o.lang; }
        if (typeof o.size === 'number' && o.size >= 0 && o.size <= 3) { s.size = Math.floor(o.size); }
        if (o.theme === 'dark' || o.theme === 'light') { s.theme = o.theme; }
      }
    } catch (e) {}
    return s;
  }
  function saveSettings() {
    try { localStorage.setItem('op_settings', JSON.stringify(state.set)); } catch (e) {}
  }
  function resetSettings() {
    try { localStorage.removeItem('op_settings'); } catch (e) {}
    state.set = defaultSettings();
  }
  function applySettings() {
    var h = document.documentElement;
    h.lang = state.set.lang;
    h.className = 'sz' + state.set.size + (state.set.theme === 'dark' ? ' dark' : '');
  }

  /* ---------- categories and items ---------- */
  function catInfo(id) {
    var lang = state.set.lang, d = CATS[id];
    var out = { id: id, title: id, icon: 'folder' };
    if (d) { out.title = d[lang] || d.en || id; out.icon = d.icon || 'folder'; }
    var list = (state.manifest && state.manifest.categories) || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].id !== id) { continue; }
      var own = lang === 'my' ? list[i].title_my : list[i].title;
      if (own) { out.title = own; } else if (!d && list[i].title) { out.title = list[i].title; }
      if (list[i].icon && ICONS[list[i].icon]) { out.icon = list[i].icon; }
    }
    return out;
  }
  function categoryIds() {
    var ids = [], i, c;
    var have = {};
    state.manifest.items.forEach(function (it) { have[it.category] = (have[it.category] || 0) + 1; });
    var declared = state.manifest.categories || [];
    for (i = 0; i < declared.length; i++) { c = declared[i].id; if (have[c] && ids.indexOf(c) < 0) { ids.push(c); } }
    state.manifest.items.forEach(function (it) { if (ids.indexOf(it.category) < 0) { ids.push(it.category); } });
    return ids.map(function (id) { var o = catInfo(id); o.count = have[id]; return o; });
  }
  function itemsIn(cat) { return state.manifest.items.filter(function (i) { return i.category === cat; }); }

  var READ = { txt: 1, md: 1, pdf: 1, epub: 1, html: 1, htm: 1 };
  var VIDEO = { mp4: 1, webm: 1, mkv: 1, '3gp': 1 };
  var AUDIO = { mp3: 1, m4a: 1, ogg: 1, opus: 1, wav: 1, aac: 1 };
  var IMAGE = { jpg: 1, jpeg: 1, png: 1, gif: 1, webp: 1, svg: 1 };
  function ext(p) { var m = /\.([a-z0-9]+)$/i.exec(p || ''); return m ? m[1].toLowerCase() : ''; }

  // What the one big button says and does, chosen from the file type so nobody has to guess.
  function describe(i) {
    var kind = i.kind || 'file', e = ext(i.path);
    if (kind === 'lesson') { return { type: 'type_lesson', act: 'act_learn', icon: 'steps', lesson: true }; }
    if (kind === 'apk') { return { type: 'type_app', act: 'act_install', icon: 'download', dl: true, apk: true }; }
    if (kind === 'zim') {
      return state.features.kiwix
        ? { type: 'type_library', act: 'act_read', icon: 'globe', kiwix: true }
        : { type: 'type_library', act: 'act_save', icon: 'download', dl: true };
    }
    if (READ[e]) { return { type: 'type_read', act: 'act_read', icon: 'book' }; }
    if (VIDEO[e]) { return { type: 'type_video', act: 'act_watch', icon: 'play' }; }
    if (AUDIO[e]) { return { type: 'type_audio', act: 'act_listen', icon: 'headphones' }; }
    if (IMAGE[e]) { return { type: 'type_image', act: 'act_view', icon: 'image' }; }
    return { type: 'type_file', act: 'act_save', icon: 'download', dl: true };
  }

  function card(i) {
    var d = describe(i), desc = pick(i, 'description');
    var href = d.lesson ? '#/l/' + encodeURIComponent(i.id) : d.kiwix ? 'kiwix/' : encodeURI(i.path);
    var rows = '<dt>' + esc(t('d_size')) + '</dt><dd>' + size(i.bytes) + '</dd>' +
      '<dt>' + esc(t('d_license')) + '</dt><dd>' + esc(i.license) + (i.non_commercial ? ' · ' + esc(t('d_noncommercial')) : '') + '</dd>' +
      (i.attribution ? '<dt>' + esc(t('d_by')) + '</dt><dd>' + esc(i.attribution) + '</dd>' : '') +
      (i.source_code ? '<dt>' + esc(t('d_source')) + '</dt><dd><a href="' + esc(i.source_code) + '">' + esc(i.source_code) + '</a></dd>' : '') +
      '<dt>' + esc(t('d_hash')) + '</dt><dd class="hash">' + esc(i.sha256) + '</dd>';
    return '<article class="card"><h3>' + esc(pick(i, 'title')) + '</h3>' +
      (desc ? '<p class="desc">' + esc(desc) + '</p>' : '') +
      '<p class="kind">' + esc(t(d.type)) + ' · ' + size(i.bytes) + '</p>' +
      '<a class="act" href="' + href + '"' + (d.dl ? ' download' : '') + '>' + ic(d.icon) + esc(t(d.act)) + '</a>' +
      (d.apk ? '<p class="note">' + esc(t('install_note')) + ' <a href="#/help">' + esc(t('help')) + '</a></p>' : '') +
      '<details><summary>' + esc(t('details')) + '</summary><dl>' + rows + '</dl></details></article>';
  }

  /* ---------- search ---------- */
  // Matching rules (Zawgyi typing, no-space Burmese, ranking) live in search.js.
  function search(q) {
    return window.OPENPACK_SEARCH.search(state.manifest.items, q, function (i) { return catInfo(i.category).title; });
  }
  function searchForm(q) {
    return '<form class="searchform" role="search" data-form="search">' +
      '<input id="q" type="search" autocomplete="off" autocapitalize="off" spellcheck="false" enterkeyhint="search" aria-label="' + esc(t('search_btn')) + '" placeholder="' + esc(t('search_ph')) + '" value="' + esc(q || '') + '">' +
      '<button class="go" type="submit">' + ic('search') + esc(t('search_btn')) + '</button></form>';
  }
  function backBtn() { return '<a class="backbtn" href="#/">' + ic('back') + esc(t('back')) + '</a>'; }

  /* ---------- share ---------- */
  function shareUrls() {
    var h = location.hostname;
    if ((h === 'localhost' || h === '127.0.0.1') && state.features.urls.length) { return state.features.urls; }
    return [location.protocol + '//' + location.host + '/'];
  }
  function qrSvg(text) {
    try { var qr = qrcode(0, 'M'); qr.addData(text); qr.make(); return qr.createSvgTag(5, 2); } catch (e) { return ''; }
  }

  /* ---------- screens ---------- */
  function viewHome() {
    var cats = categoryIds();
    var tiles = cats.map(function (c) {
      return '<a class="tile" href="#/c/' + encodeURIComponent(c.id) + '">' + ic(c.icon) + '<b>' + esc(c.title) + '</b><span>' + esc(t('items_n', { n: c.count })) + '</span></a>';
    }).join('');
    return '<p class="tagline">' + esc(t('app_tagline')) + '</p>' +
      '<span class="badge">' + esc(t('offline_badge')) + '</span>' +
      searchForm('') +
      (state.features.notices ? '<a class="noticebar" href="#/notices">' + ic('megaphone') + '<span>' + esc(t('notices_n', { n: state.features.notices })) + '</span>' + ic('chevron') + '</a>' : '') +
      '<h3 class="sub">' + esc(t('browse')) + '</h3><div class="tiles">' + tiles + '</div>' +
      (isFile ? '' : '<a class="bigbtn" href="#/share">' + ic('share') + esc(t('share')) + '</a>') +
      '<p class="ver">' + esc(t('pack_version', { v: state.manifest.version })) + '</p>';
  }
  function viewCategory(id) {
    var rows = itemsIn(id);
    if (!rows.length) { return backBtn() + emptyBox(); }
    var c = catInfo(id);
    return backBtn() + '<h2 class="title">' + ic(c.icon) + ' ' + esc(c.title) + '</h2>' + rows.map(card).join('');
  }
  function viewSearch(q) {
    var rows = q.replace(/\s/g, '') ? search(q) : [];
    q = window.OPENPACK_SEARCH.display(q) || q; // show Zawgyi typing back as Unicode
    return backBtn() + searchForm(q) +
      (rows.length ? '<h3 class="sub">' + esc(t('results_for', { n: rows.length, q: q })) + '</h3>' + rows.map(card).join('') : emptyBox());
  }
  function emptyBox() {
    return '<div class="empty"><b>' + esc(t('no_results')) + '</b><p class="muted">' + esc(t('no_results_hint')) + '</p></div>';
  }
  function viewSettings() {
    var s = state.set;
    function choice(act, val, on, inner) {
      return '<button type="button" class="choice' + (act === 'size' ? ' sample' : '') + '" data-do="' + act + '" data-val="' + val + '" aria-pressed="' + (on ? 'true' : 'false') + '">' + inner + '</button>';
    }
    var langs = ['my', 'en'].map(function (l) { return choice('lang', l, s.lang === l, (s.lang === l ? ic('check') : '') + esc(LANGS[l])); }).join('');
    var sizes = SIZE_PX.map(function (px, n) {
      return choice('size', n, s.size === n, '<span style="font-size:' + px + 'px;line-height:1.3">' + esc(t('sample_char')) + '</span><small>' + esc(t(SIZE_KEYS[n])) + '</small>');
    }).join('');
    var themes = ['light', 'dark'].map(function (th) { return choice('theme', th, s.theme === th, (s.theme === th ? ic('check') : '') + esc(t('theme_' + th))); }).join('');
    return backBtn() + '<h2 class="title">' + esc(t('set_title')) + '</h2>' +
      '<section class="setgroup"><h3>' + esc(t('set_lang')) + '</h3><div class="choices">' + langs + '</div></section>' +
      '<section class="setgroup"><h3>' + esc(t('set_size')) + '</h3><div class="choices">' + sizes + '</div></section>' +
      '<section class="setgroup"><h3>' + esc(t('set_theme')) + '</h3><div class="choices">' + themes + '</div></section>' +
      '<button type="button" class="resetbtn" data-do="reset">' + esc(t('set_reset')) + '</button>' +
      '<p class="note">' + esc(t('set_note')) + '</p>';
  }
  function viewHelp() {
    var out = '';
    for (var n = 1; n <= 6; n++) {
      out += '<details class="acc"' + (n === 1 ? ' open' : '') + '><summary>' + esc(t('h' + n + '_q')) + '</summary><p>' + esc(t('h' + n + '_a')) + '</p></details>';
    }
    return backBtn() + '<h2 class="title">' + esc(t('help_title')) + '</h2>' + out;
  }
  function viewShare() {
    var head = backBtn() + '<h2 class="title">' + esc(t('share_title')) + '</h2>';
    if (isFile) { return head + '<p class="filenote">' + esc(t('share_file')) + '</p>'; }
    var urls = shareUrls();
    return head + '<ol class="steps"><li>' + esc(t('share_s1')) + '</li><li>' + esc(t('share_s2')) + '</li><li>' + esc(t('share_s3')) + '</li></ol>' +
      '<div class="sharebox"><div class="qr">' + qrSvg(urls[0]) + '</div><p class="url-label">' + esc(t('share_addr')) + '</p>' +
      urls.map(function (u) { return '<p class="url">' + esc(u) + '</p>'; }).join('') + '</div>';
  }

  /* ---------- lessons (step-by-step picture guides) ---------- */
  // A lesson is a small script file in the pack. Loading it as a script (not fetch) is what
  // lets it work when the pack is opened straight from a folder.
  var lessonLoad = {}; // id -> 'loading' | 'ok' | 'error'
  var lessonAt = { id: null, step: 0, tab: 'steps' };
  function findItem(id) {
    var list = state.manifest.items;
    for (var i = 0; i < list.length; i++) { if (list[i].id === id) { return list[i]; } }
    return null;
  }
  function loadLesson(item) {
    if (lessonLoad[item.id]) { return; }
    lessonLoad[item.id] = 'loading';
    var el = document.createElement('script');
    el.src = encodeURI(item.path);
    el.onload = function () {
      lessonLoad[item.id] = (window.OPENPACK_LESSONS && window.OPENPACK_LESSONS[item.id]) ? 'ok' : 'error';
      render();
    };
    el.onerror = function () { lessonLoad[item.id] = 'error'; render(); };
    document.body.appendChild(el);
  }
  function both(v) { return v ? (state.set.lang === 'my' ? v.my : v.en) : ''; }
  function viewLesson(id) {
    var item = findItem(id);
    if (!item || item.kind !== 'lesson') { return backBtn() + emptyBox(); }
    var back = '<a class="backbtn" href="#/c/' + encodeURIComponent(item.category) + '">' + ic('back') + esc(t('back')) + '</a>';
    var head = back + '<h2 class="title">' + esc(pick(item, 'title')) + '</h2>';
    if (lessonAt.id !== id) { lessonAt = { id: id, step: 0, tab: 'steps' }; }
    loadLesson(item);
    if (lessonLoad[id] === 'error') { return head + '<p class="filenote">' + esc(t('l_error')) + '</p>'; }
    if (lessonLoad[id] !== 'ok') { return head + '<p class="muted">' + esc(t('l_loading')) + '</p>'; }

    var L = window.OPENPACK_LESSONS[id], n = L.steps.length, at = lessonAt;
    if (at.step < 0) { at.step = 0; }
    if (at.step > n - 1) { at.step = n - 1; }
    function tab(key, label, icon) {
      return '<button type="button" class="ltab" data-do="ltab" data-val="' + key + '" aria-pressed="' + (at.tab === key ? 'true' : 'false') + '">' + ic(icon) + esc(label) + '</button>';
    }
    var tabs = '<div class="ltabs">' + tab('steps', t('l_steps'), 'steps') +
      (L.why.length ? tab('why', t('l_why'), 'help') : '') +
      (L.tips.length ? tab('tips', t('l_tips'), 'star') : '') + '</div>';
    var body = '', k;
    if (at.tab === 'why' || at.tab === 'tips') {
      var list = at.tab === 'why' ? L.why : L.tips;
      for (k = 0; k < list.length; k++) { body += '<p class="lpoint">' + esc(both(list[k])) + '</p>'; }
    } else {
      var s = L.steps[at.step], last = at.step === n - 1, dots = '';
      for (k = 0; k < n; k++) { dots += '<i' + (k === at.step ? ' class="on"' : '') + '></i>'; }
      body = '<div class="lstep">' +
        '<div class="lpic">' + s.svg + '</div>' +
        '<p class="lcount">' + esc(t('l_step_n', { n: at.step + 1, t: n })) + '</p>' +
        '<div class="ldots" aria-hidden="true">' + dots + '</div>' +
        '<p class="ltext">' + esc(both(s)) + '</p>' +
        (last ? '<p class="ldone">' + ic('check') + esc(t('l_done')) + '</p>' : '') +
        '<div class="lnav">' +
        '<button type="button" class="lbtn" data-do="lstep" data-val="' + (at.step - 1) + '"' + (at.step === 0 ? ' disabled' : '') + '>' + ic('back') + esc(t('l_prev')) + '</button>' +
        (last
          ? '<button type="button" class="lbtn" data-do="lstep" data-val="0">' + esc(t('l_again')) + '</button>'
          : '<button type="button" class="lbtn main" data-do="lstep" data-val="' + (at.step + 1) + '">' + esc(t('l_next')) + ic('chevron') + '</button>') +
        '</div></div>';
    }
    var src = '';
    for (k = 0; k < L.sources.length; k++) {
      src += '<dd>' + esc(L.sources[k].name) + (L.sources[k].url ? '<br><span class="hash">' + esc(L.sources[k].url) + '</span>' : '') + '</dd>';
    }
    return head +
      (L.warning ? '<p class="lwarn">' + esc(both(L.warning)) + '</p>' : '') +
      tabs + body +
      (L.reviewed ? '' : '<p class="note">' + esc(t('l_unreviewed')) + '</p>') +
      '<details><summary>' + esc(t('l_sources')) + '</summary><dl>' + src + '</dl></details>';
  }

  /* ---------- hub: notice board ---------- */
  // Notices come from the place running the server, not from the signed pack. They exist only
  // in served mode, and nothing about them is kept on this device.
  function lines(s) { return esc(s).replace(/\n/g, '<br>'); }
  var hubNotices = null; // null = not loaded yet
  function loadNotices() {
    fetch('hub/notices.json', { cache: 'no-store' })
      .then(function (r) { return r.json(); })
      .then(function (d) { hubNotices = d.notices || []; })
      .catch(function () { hubNotices = []; })
      .then(function () { if (route().name === 'notices') { render(); } });
  }
  function viewNotices() {
    var head = backBtn() + '<h2 class="title">' + ic('megaphone') + ' ' + esc(t('notices')) + '</h2>' +
      '<p class="note fromhub">' + esc(t('notices_from')) + '</p>';
    if (hubNotices === null) { return head + '<p class="muted">' + esc(t('l_loading')) + '</p>'; }
    if (!hubNotices.length) { return head + '<p class="muted">' + esc(t('notices_none')) + '</p>'; }
    return head + hubNotices.map(function (n) {
      return '<article class="card notice"><h3>' + esc(n.title) + '</h3>' + (n.body ? '<p>' + lines(n.body) + '</p>' : '') + '</article>';
    }).join('');
  }

  /* ---------- routing and drawing ---------- */
  function route() {
    var h = location.hash.replace(/^#\/?/, '');
    var i = h.indexOf('/');
    var name = i < 0 ? h : h.slice(0, i), arg = i < 0 ? '' : h.slice(i + 1);
    try { arg = decodeURIComponent(arg); } catch (e) {}
    return { name: name, arg: arg };
  }
  var lastHash = null;
  function render() {
    var m = state.manifest;
    if (!m) { return; }
    applySettings();
    var r = route(), html;
    if (r.name !== 'l') { lessonAt.id = null; } // reopening a lesson starts again at step 1
    if (r.name === 'notices' && location.hash !== lastHash) { hubNotices = null; loadNotices(); }
    if (r.name === 'c') { html = viewCategory(r.arg); }
    else if (r.name === 's') { html = viewSearch(r.arg); }
    else if (r.name === 'l') { html = viewLesson(r.arg); }
    else if (r.name === 'notices') { html = viewNotices(); }
    else if (r.name === 'settings') { html = viewSettings(); }
    else if (r.name === 'help') { html = viewHelp(); }
    else if (r.name === 'share') { html = viewShare(); }
    else { html = viewHome(); r.name = 'home'; }

    var title = pick(m, 'title') || m.name;
    $('title').textContent = title;
    document.title = title;
    $('t-lang').innerHTML = ic('globe') + esc(LANGS[state.set.lang === 'my' ? 'en' : 'my']);
    $('t-lang').setAttribute('lang', state.set.lang === 'my' ? 'en' : 'my');
    $('t-help').innerHTML = ic('help') + esc(t('help'));
    $('t-set').innerHTML = ic('sliders') + esc(t('settings'));
    if (r.name === 'help') { $('t-help').setAttribute('aria-current', 'page'); } else { $('t-help').removeAttribute('aria-current'); }
    if (r.name === 'settings') { $('t-set').setAttribute('aria-current', 'page'); } else { $('t-set').removeAttribute('aria-current'); }
    $('foot').textContent = t('footer');
    $('view').innerHTML = html;
    if (location.hash !== lastHash) { window.scrollTo(0, 0); lastHash = location.hash; }
  }

  function toggleLang() {
    state.set.lang = state.set.lang === 'my' ? 'en' : 'my';
    saveSettings();
    render();
  }
  $('t-lang').addEventListener('click', toggleLang);

  $('view').addEventListener('click', function (e) {
    var el = e.target;
    while (el && el !== this && !(el.getAttribute && el.getAttribute('data-do'))) { el = el.parentNode; }
    if (!el || el === this) { return; }
    var what = el.getAttribute('data-do'), val = el.getAttribute('data-val');
    if (what === 'lang' && (val === 'my' || val === 'en')) { state.set.lang = val; }
    else if (what === 'size') { state.set.size = parseInt(val, 10) || 0; }
    else if (what === 'theme' && (val === 'light' || val === 'dark')) { state.set.theme = val; }
    else if (what === 'reset') { resetSettings(); render(); return; }
    else if (what === 'ltab') { lessonAt.tab = val; render(); return; }
    else if (what === 'lstep') {
      lessonAt.step = parseInt(val, 10) || 0;
      render();
      var top = document.querySelector('.ltabs');
      if (top && top.scrollIntoView) { top.scrollIntoView(); }
      return;
    }
    else { return; }
    saveSettings();
    render();
  });

  $('view').addEventListener('submit', function (e) {
    e.preventDefault();
    var q = ($('q').value || '').replace(/^\s+|\s+$/g, '');
    location.hash = q ? '#/s/' + encodeURIComponent(q) : '#/';
  });

  window.addEventListener('hashchange', render);

  function start(m) {
    if (!m || !m.items) {
      applySettings();
      $('view').innerHTML = '<p class="filenote">' + esc(S.my.err_manifest || '') + '<br>' + esc(S.en.err_manifest || '') + '</p>';
      return;
    }
    state.manifest = m;
    state.set = loadSettings();
    render();
  }

  if (isFile) {
    start(window.OPENPACK_MANIFEST || null);
  } else {
    // features.json is generated live by the server: is Kiwix running, and what are the LAN addresses?
    fetch('features.json', { cache: 'no-store' })
      .then(function (r) { return r.json(); })
      .then(function (f) { state.features = { kiwix: !!f.kiwix, urls: f.urls || [], notices: f.notices || 0 }; })
      .catch(function () {})
      .then(function () {
        return fetch('manifest.json', { cache: 'no-store' })
          .then(function (r) { return r.json(); })
          .catch(function () { return window.OPENPACK_MANIFEST || null; });
      })
      .then(start);
  }
})();
