/* ============================================================================
   ALL WORDS SHOWN ON SCREEN LIVE IN THIS FILE.

   To fix or improve a label, edit it here. You do not need to touch any other file.
   - "my" is Burmese (must be Unicode, NOT Zawgyi). "en" is English.
   - Every key must exist in BOTH languages (the tests check this).
   - {n}, {q}, {v} are filled in by the program. Keep them exactly as written.
   - A pack can also override single labels from its pack.json ("ui" section) without
     editing this file.
   ============================================================================ */
window.OPENPACK_STRINGS = {
  my: {
    // Home screen
    app_tagline: 'အင်တာနက်မလိုဘဲ ဖတ်နိုင် ကြည့်နိုင် နားထောင်နိုင်ပါသည်',
    offline_badge: 'အင်တာနက် မလိုပါ',
    pack_version: 'ထုတ်ဝေမှု {v}',
    browse: 'အမျိုးအစားများ',
    items_n: '{n} ခု',

    // Search
    search_ph: 'စကားလုံး ရိုက်ထည့်ပါ',
    search_btn: 'ရှာရန်',
    search_clear: 'ဖျက်ရန်',
    results_for: '“{q}” အတွက် ရလဒ် {n} ခု',
    no_results: 'ရလဒ် မတွေ့ပါ',
    no_results_hint: 'အခြားစကားလုံးဖြင့် ထပ်ရှာကြည့်ပါ၊ သို့မဟုတ် အမျိုးအစားများကို ကြည့်ပါ။',

    // Navigation
    back: 'နောက်သို့',
    help: 'အကူအညီ',
    settings: 'ဆက်တင်',
    share: 'မျှဝေရန်',

    // Item cards
    details: 'အသေးစိတ်',
    d_size: 'ဖိုင်အရွယ်အစား',
    d_license: 'မျှဝေခွင့် လိုင်စင်',
    d_by: 'ရေးသားသူ / ပိုင်ရှင်',
    d_source: 'ရင်းမြစ် ကုဒ်',
    d_hash: 'စစ်ဆေးကုဒ် (SHA-256)',
    d_noncommercial: 'ရောင်းချခြင်း မပြုရပါ',
    type_read: 'စာဖတ်ရန်',
    type_video: 'ဗီဒီယို',
    type_audio: 'အသံ',
    type_image: 'ဓာတ်ပုံ',
    type_app: 'အက်ပ်',
    type_library: 'စာကြည့်တိုက်',
    type_file: 'ဖိုင်',
    act_read: 'ဖတ်ရန်',
    act_watch: 'ကြည့်ရန်',
    act_listen: 'နားထောင်ရန်',
    act_view: 'ကြည့်ရန်',
    act_install: 'ထည့်သွင်းရန်',
    act_save: 'သိမ်းရန်',
    install_note: 'ထည့်သွင်းရာတွင် ဖုန်းက ခွင့်ပြုချက် မေးနိုင်ပါသည်။ အကူအညီတွင် ကြည့်ပါ။',

    // Settings
    set_title: 'ဆက်တင်',
    set_lang: 'ဘာသာစကား',
    set_size: 'စာလုံး အရွယ်အစား',
    sample_char: 'က',
    size_s: 'သေး',
    size_m: 'ပုံမှန်',
    size_l: 'ကြီး',
    size_xl: 'အကြီးဆုံး',
    set_theme: 'မျက်နှာပြင် အရောင်',
    theme_light: 'အလင်း',
    theme_dark: 'အမှောင်',
    set_reset: 'မူလအတိုင်း ပြန်ထားရန်',
    set_note: 'ပြောင်းလဲမှုများကို ဤစက်တွင် သိမ်းဆည်းထားပါမည်။',

    // Help
    help_title: 'အကူအညီ',
    h1_q: 'ဖိုင်ကို ဘယ်လို ဖွင့်မလဲ',
    h1_a: 'ပင်မစာမျက်နှာတွင် အမျိုးအစားတစ်ခုကို နှိပ်ပါ။ စာရင်းထဲမှ ကြိုက်ရာကို ရွေးပြီး အရောင်ခလုတ် (ဖတ်ရန် / ကြည့်ရန် / နားထောင်ရန်) ကို နှိပ်ပါ။',
    h2_q: 'ရှာချင်ရင် ဘယ်လိုလုပ်မလဲ',
    h2_a: 'ပင်မစာမျက်နှာထိပ်ရှိ အကွက်ထဲတွင် စကားလုံး ရိုက်ပြီး “ရှာရန်” ကို နှိပ်ပါ။',
    h3_q: 'အက်ပ် (APK) ကို ဘယ်လို ထည့်မလဲ',
    h3_a: 'ဖိုင်ကို သိမ်းပြီးလျှင် ဖွင့်ပါ။ ဖုန်းက “မသိသော ရင်းမြစ်မှ ထည့်သွင်းခြင်း” ကို မေးလာလျှင် ခွင့်ပြုပါ။ ထည့်ပြီးနောက် ထိုဆက်တင်ကို ပြန်ပိတ်ထားနိုင်ပါသည်။',
    h4_q: 'အခြားသူများကို ဘယ်လို မျှဝေမလဲ',
    h4_a: 'ဤစက်ကို Hotspot ဖွင့်ပါ။ အခြားသူများ ချိတ်ဆက်ပြီးနောက် “မျှဝေရန်” ကို နှိပ်ပါ။ ပေါ်လာသော QR ကုဒ်ကို သူတို့၏ ဖုန်းကင်မရာဖြင့် စကင်ဖတ်ပါ။',
    h5_q: 'ဖိုင်မပွင့်ရင် ဘာလုပ်မလဲ',
    h5_a: 'စာမျက်နှာကို ပြန်ဖွင့်ကြည့်ပါ။ Chrome ဘရောက်ဇာဖြင့် ဖွင့်ကြည့်ပါ။ ဖုန်းတွင် နေရာလွတ် လုံလောက်မှု ရှိမရှိ စစ်ကြည့်ပါ။',
    h6_q: 'အင်တာနက် လိုပါသလား',
    h6_a: 'မလိုပါ။ ဤစာကြည့်တိုက်ကို အင်တာနက်မရှိဘဲ သုံးနိုင်ပါသည်။ မည်သူ၏ အချက်အလက်ကိုမျှ မှတ်တမ်းမတင်ပါ။',

    // Share
    share_title: 'အခြားသူများကို မျှဝေရန်',
    share_s1: 'ဤစက်၏ Hotspot ကို ဖွင့်ပါ။',
    share_s2: 'အခြားသူများက ထို Wi-Fi သို့ ချိတ်ဆက်ပါ။',
    share_s3: 'ဖုန်းကင်မရာဖြင့် အောက်ပါ QR ကုဒ်ကို စကင်ဖတ်ပါ၊ သို့မဟုတ် လိပ်စာကို ဘရောက်ဇာတွင် ရိုက်ထည့်ပါ။',
    share_addr: 'လိပ်စာ',
    share_file: 'ဤစာမျက်နှာကို ဖိုလ်ဒါမှ တိုက်ရိုက်ဖွင့်ထားပါသည်။ လူများစွာကို မျှဝေလိုလျှင် START ဖိုင်ဖြင့် စတင်ပါ။',

    // Lessons (step-by-step picture guides)
    type_lesson: 'ပုံပြ သင်ခန်းစာ',
    act_learn: 'လေ့လာရန်',
    l_steps: 'လုပ်နည်း',
    l_why: 'အကြောင်းရင်း',
    l_tips: 'အကြံပြုချက်',
    l_step_n: 'အဆင့် {n} / {t}',
    l_next: 'ရှေ့သို့',
    l_prev: 'ယခင်',
    l_again: 'အစမှ ပြန်ကြည့်ရန်',
    l_done: 'သင်ခန်းစာ ပြီးပါပြီ။',
    l_sources: 'ကိုးကားချက်များ',
    l_unreviewed: 'ဤသင်ခန်းစာကို ကျွမ်းကျင်သူက မစစ်ဆေးရသေးပါ။',
    l_loading: 'ဖွင့်နေပါသည်…',
    l_error: 'သင်ခန်းစာကို ဖွင့်၍ မရပါ။ ဖိုလ်ဒါတစ်ခုလုံးကို မူရင်းမှ ပြန်ကူးပါ။',

    // Messages
    err_manifest: 'ဖိုင်များ မတွေ့ပါ။ ဖိုလ်ဒါတစ်ခုလုံးကို မူရင်းမှ ပြန်ကူးပါ။',
    footer: 'အကြောင်းအရာတစ်ခုချင်းစီကို ၎င်း၏ လိုင်စင်စည်းကမ်းအတိုင်း မျှဝေထားပါသည်။'
  },

  en: {
    app_tagline: 'Read, watch and listen without internet',
    offline_badge: 'No internet needed',
    pack_version: 'Pack {v}',
    browse: 'Browse by type',
    items_n: '{n} items',

    search_ph: 'Type a word',
    search_btn: 'Search',
    search_clear: 'Clear',
    results_for: '{n} results for “{q}”',
    no_results: 'Nothing found',
    no_results_hint: 'Try a different word, or browse by type.',

    back: 'Back',
    help: 'Help',
    settings: 'Settings',
    share: 'Share',

    details: 'Details',
    d_size: 'File size',
    d_license: 'Sharing licence',
    d_by: 'Author / owner',
    d_source: 'Source code',
    d_hash: 'Check code (SHA-256)',
    d_noncommercial: 'Not for sale',
    type_read: 'To read',
    type_video: 'Video',
    type_audio: 'Audio',
    type_image: 'Picture',
    type_app: 'App',
    type_library: 'Library',
    type_file: 'File',
    act_read: 'Read',
    act_watch: 'Watch',
    act_listen: 'Listen',
    act_view: 'View',
    act_install: 'Install',
    act_save: 'Save',
    install_note: 'Your phone may ask for permission to install. See Help.',

    set_title: 'Settings',
    set_lang: 'Language',
    set_size: 'Text size',
    sample_char: 'A',
    size_s: 'Small',
    size_m: 'Normal',
    size_l: 'Large',
    size_xl: 'Largest',
    set_theme: 'Screen colour',
    theme_light: 'Light',
    theme_dark: 'Dark',
    set_reset: 'Reset to default',
    set_note: 'Your changes are saved on this device.',

    help_title: 'Help',
    h1_q: 'How do I open a file?',
    h1_a: 'On the home page, tap a type. Pick an item from the list, then tap its coloured button (Read / Watch / Listen).',
    h2_q: 'How do I search?',
    h2_a: 'Type a word in the box at the top of the home page and tap “Search”.',
    h3_q: 'How do I install an app (APK)?',
    h3_a: 'Save the file, then open it. If your phone asks about “installing from unknown sources”, allow it. You can switch that setting off again afterwards.',
    h4_q: 'How do I share this with other people?',
    h4_a: 'Turn on this device’s Hotspot. When others have connected, tap “Share” and let them scan the QR code with their phone camera.',
    h5_q: 'What if a file will not open?',
    h5_a: 'Reload the page. Try opening it in Chrome. Check that your phone has enough free space.',
    h6_q: 'Do I need internet?',
    h6_a: 'No. This library works without internet. Nobody’s activity is recorded.',

    share_title: 'Share with other people',
    share_s1: 'Turn on this device’s Hotspot.',
    share_s2: 'Other people connect to that Wi-Fi.',
    share_s3: 'They scan the QR code below with their phone camera, or type the address into a browser.',
    share_addr: 'Address',
    share_file: 'This page was opened directly from a folder. To share with many people, start it with the START file.',

    type_lesson: 'Picture lesson',
    act_learn: 'Start lesson',
    l_steps: 'Steps',
    l_why: 'Why',
    l_tips: 'Tips',
    l_step_n: 'Step {n} of {t}',
    l_next: 'Next',
    l_prev: 'Previous',
    l_again: 'Start again',
    l_done: 'You have finished this lesson.',
    l_sources: 'Sources',
    l_unreviewed: 'This lesson has not yet been checked by an expert.',
    l_loading: 'Loading…',
    l_error: 'This lesson could not be opened. Copy the whole folder again from the original.',

    err_manifest: 'Files not found. Copy the whole folder again from the original.',
    footer: 'Each item is shared under the licence shown in its details.'
  }
};

/* Each language is always written in its OWN language, so people can find theirs. */
window.OPENPACK_LANGS = { my: 'မြန်မာ', en: 'English' };

/* Names and icons for the type tiles on the home screen.
   A pack can add or rename its own in pack.json ("categories"). */
window.OPENPACK_CATEGORIES = {
  survival:  { my: 'အရေးပေါ် လမ်းညွှန်',       en: 'Emergency skills',   icon: 'shield' },
  about:     { my: 'ဤစာကြည့်တိုက် အကြောင်း', en: 'About this library', icon: 'info' },
  learn:     { my: 'သင်ယူရန်',               en: 'Learn',              icon: 'learn' },
  reference: { my: 'ဗဟုသုတ',                 en: 'Knowledge',          icon: 'book' },
  books:     { my: 'စာအုပ်များ',               en: 'Books',              icon: 'book' },
  stories:   { my: 'ပုံပြင်များ',               en: 'Stories',            icon: 'book' },
  news:      { my: 'သတင်း',                  en: 'News',               icon: 'news' },
  health:    { my: 'ကျန်းမာရေး',              en: 'Health',             icon: 'heart' },
  farming:   { my: 'စိုက်ပျိုးရေး',            en: 'Farming',            icon: 'leaf' },
  kids:      { my: 'ကလေးများ',               en: 'Kids',               icon: 'star' },
  video:     { my: 'ဗီဒီယို',                 en: 'Videos',             icon: 'play' },
  audio:     { my: 'အသံ',                   en: 'Audio',              icon: 'headphones' },
  music:     { my: 'ဂီတ',                   en: 'Music',              icon: 'headphones' },
  apps:      { my: 'အက်ပ်များ',               en: 'Apps',               icon: 'apps' },
  tools:     { my: 'ကိရိယာများ',              en: 'Tools',              icon: 'apps' }
};
