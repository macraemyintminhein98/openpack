#!/usr/bin/env python3
"""Browser test of the portal in a real Chrome: folder (file://) mode and served mode.

Needs: pip install playwright, a Chrome/Chromium, a built bundle (npm run bundle) and the Linux server binary.
Usage: python3 scripts/browser-test.py [path-to-chrome]
Screenshots go to /tmp/op-shots/.
"""
import glob, json, os, shutil, subprocess, sys, tempfile, time
from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHROME = sys.argv[1] if len(sys.argv) > 1 else '/usr/bin/google-chrome'
# Optional, to get closer to real phones:
#   OP_ENGINE=chromium|firefox|webkit   browser engine (default: the Chrome at the path above)
#   OP_DEVICE="Galaxy S5"               emulate a phone (screen, touch, user agent); see playwright devices
#   OP_HOST=192.168.1.20                reach the server by this address instead of localhost.
#                                       Use the machine's LAN address: phones are never on localhost,
#                                       and browsers switch some features off for non-localhost http.
ENGINE = os.environ.get('OP_ENGINE', '')
DEVICE = os.environ.get('OP_DEVICE', '')
HOST = os.environ.get('OP_HOST', 'localhost')
PW = None


def phone():
    """Context settings for one simulated phone."""
    if DEVICE:
        d = dict(PW.devices[DEVICE])
        if ENGINE == 'firefox':
            d.pop('is_mobile', None)
        return d
    return {'viewport': {'width': 360, 'height': 700}}
bundle = sorted(glob.glob(ROOT + '/dist/OpenPack-*/'))[-1]
pack = bundle + 'pack/'
SHOTS = '/tmp/op-shots/'
os.makedirs(SHOTS, exist_ok=True)
results = []


def check(name, cond, detail=''):
    results.append((name, bool(cond)))
    print(('PASS ' if cond else 'FAIL ') + name + ((' -> ' + str(detail)) if (detail != '' and not cond) else ''))


def no_overflow(page):
    return page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')


def text(page, sel):
    return page.locator(sel).first.inner_text()


# OP_OLD=1 takes away everything a 2013 browser (Chromium 30, Android 4.4) does not have, before the
# page's own scripts run. It cannot make a new engine draw like an old one, but it proves the page
# does not lean on anything newer.
OLD = os.environ.get('OP_OLD') == '1'
STRIP_NEW_APIS = """
(function () {
  // Map, Set and Symbol stay only because the test tool itself needs them inside the page; the page's
  // scripts are separately checked to contain none of them (test/portal.test.js).
  var gone = ['fetch', 'Promise', 'Proxy', 'Reflect', 'URLSearchParams',
              'IntersectionObserver', 'ResizeObserver', 'AbortController', 'Headers', 'Request', 'Response', 'CSS'];
  for (var i = 0; i < gone.length; i++) { try { Object.defineProperty(window, gone[i], { value: undefined, configurable: true, writable: true }); } catch (e) {} }
  // (A shorter list than a real 2013 browser lacks: the test tool needs the rest to report results.)
  var drop = [[Object, ['assign']],
              [Array.prototype, ['find', 'findIndex', 'includes', 'fill']],
              [String.prototype, ['includes', 'startsWith', 'endsWith', 'repeat', 'padStart', 'padEnd']],
              [Element.prototype, ['closest', 'matches', 'append', 'prepend', 'replaceWith', 'before', 'after']],
              [NodeList.prototype, ['forEach']]];
  for (var d = 0; d < drop.length; d++) { for (var k = 0; k < drop[d][1].length; k++) { try { delete drop[d][0][drop[d][1][k]]; } catch (e) {} } }
})();
"""


def new_ctx(b, **kw):
    ctx = b.new_context(**kw)
    if OLD:
        ctx.add_init_script(STRIP_NEW_APIS)
    return ctx


def run_flow(b, base_url, tag, served):
    ctx = new_ctx(b, **phone())
    page = ctx.new_page()
    errs = []
    page.on('pageerror', lambda e: errs.append(str(e)))
    page.goto(base_url)
    page.wait_for_selector('.tile')

    # ---- home ----
    check(tag + ': home shows 3 type tiles', page.locator('.tile').count() == 3, page.locator('.tile').count())
    check(tag + ': tiles are in Burmese by default', 'သင်ယူရန်' in page.inner_text('#view'), page.inner_text('#view')[:200])
    check(tag + ': page language is Burmese', page.evaluate('document.documentElement.lang') == 'my')
    check(tag + ': share button ' + ('shown' if served else 'hidden'), (page.locator('.bigbtn').count() == 1) == served)
    check(tag + ': no notice bar unless the hub writes notices', page.locator('.noticebar').count() == 0)
    check(tag + ': home fits 360px wide', no_overflow(page))
    page.wait_for_timeout(300)
    loaded = page.evaluate("(function(){var n=0;document.fonts.forEach(function(f){if(f.status==='loaded'){n++}});return n})()")
    check(tag + ': bundled Burmese font actually loaded', loaded >= 1, loaded)
    page.screenshot(path=SHOTS + tag + '-1-home.png')

    # ---- category + Back ----
    page.click('a.tile[href="#/c/learn"]')
    page.wait_for_selector('.card')
    check(tag + ': tile opens its list', page.locator('.card').count() == 1 and page.url.endswith('#/c/learn'), page.url)
    act = page.locator('.act').first
    check(tag + ': big button says Read in Burmese', 'ဖတ်ရန်' in act.inner_text(), act.inner_text())
    check(tag + ': big button is easy to tap (>=48px tall)', act.bounding_box()['height'] >= 48, act.bounding_box())
    check(tag + ': details hidden until asked', not page.locator('.card dl').first.is_visible())
    page.locator('.card summary').first.click()
    check(tag + ': details show licence + check code', page.locator('.card dl').first.is_visible() and 'CC-BY-SA-4.0' in page.inner_text('.card dl') and len(text(page, '.hash')) == 64)
    page.screenshot(path=SHOTS + tag + '-2-category.png')
    page.go_back()
    page.wait_for_selector('.tile')
    check(tag + ': phone Back returns to home', page.locator('.tile').count() == 3)
    page.click('a.tile[href="#/c/about"]')
    page.click('.backbtn')
    page.wait_for_selector('.tile')
    check(tag + ': on-screen Back button returns to home', page.locator('.tile').count() == 3)

    # ---- search ----
    page.fill('#q', 'lesson')
    page.press('#q', 'Enter')
    page.wait_for_selector('.card')
    check(tag + ': search finds the lesson', page.locator('.card').count() == 1 and '#/s/lesson' in page.url, page.url)
    page.fill('#q', 'ကြိုဆို')
    page.click('.go')
    for _ in range(50):  # wait for the new search, not the cards of the one before
        if '#/s/' in page.url and 'lesson' not in page.url:
            break
        page.wait_for_timeout(100)
    page.wait_for_selector('.card h3')
    page.wait_for_timeout(150)
    check(tag + ': Burmese search works', page.locator('.card').count() == 1 and 'OpenPack' in text(page, '.card h3'))
    page.fill('#q', 'ႀကိဳဆို')  # the same word typed on a Zawgyi keyboard
    page.click('.go')
    page.wait_for_selector('.card')
    check(tag + ': Zawgyi typing finds Unicode content', page.locator('.card').count() == 1 and 'OpenPack' in text(page, '.card h3'))
    check(tag + ': Zawgyi typing is shown back as Unicode', page.input_value('#q') == 'ကြိုဆို', page.input_value('#q'))
    page.fill('#q', 'ကြို ဆို')
    page.click('.go')
    page.wait_for_selector('.card')
    check(tag + ': Burmese search ignores added spaces', page.locator('.card').count() == 1)
    page.fill('#q', 'zzzzqq')
    page.click('.go')
    page.wait_for_selector('.empty')
    check(tag + ': nothing found shows a friendly message', 'ရလဒ် မတွေ့ပါ' in page.inner_text('.empty'))
    page.screenshot(path=SHOTS + tag + '-3-empty.png')

    # ---- picture lesson ----
    page.goto(base_url + '#/c/survival')
    page.wait_for_selector('.card')
    check(tag + ': lesson card says Start lesson in Burmese', 'လေ့လာရန်' in text(page, '.act'), text(page, '.act'))
    page.click('.act')
    page.wait_for_selector('.lstep')
    check(tag + ': lesson opens with a picture and step 1 of 5', page.locator('.lpic svg').count() == 1 and '၁' not in text(page, '.lcount') and '1 / 5' in text(page, '.lcount'), text(page, '.lcount'))
    check(tag + ': lesson shows the safety warning', page.locator('.lwarn').count() == 1)
    check(tag + ': Previous is disabled on the first step', page.locator('.lbtn').first.is_disabled())
    check(tag + ': Next button is easy to tap', page.locator('.lbtn.main').bounding_box()['height'] >= 48)
    check(tag + ': lesson fits 360px', no_overflow(page))
    page.screenshot(path=SHOTS + tag + '-9-lesson-step1.png', full_page=True)
    first_text = text(page, '.ltext')
    page.click('.lbtn.main')
    page.click('.lbtn.main')
    check(tag + ': Next moves through the steps', '3 / 5' in text(page, '.lcount') and text(page, '.ltext') != first_text and '၁ မိနစ်' in text(page, '.ltext'), text(page, '.lcount'))
    page.screenshot(path=SHOTS + tag + '-9-lesson-step3.png', full_page=True)
    page.click('.lbtn.main')
    page.click('.lbtn.main')
    check(tag + ': last step says finished and offers Start again', page.locator('.ldone').count() == 1 and page.locator('.lbtn.main').count() == 0)
    page.locator('.lbtn').nth(1).click()
    check(tag + ': Start again returns to step 1', '1 / 5' in text(page, '.lcount'))
    page.click('[data-val="why"]')
    check(tag + ': Why tab shows the reasons', page.locator('.lpoint').count() == 3)
    page.click('[data-val="tips"]')
    check(tag + ': Tips tab shows the tips', page.locator('.lpoint').count() == 5)
    page.screenshot(path=SHOTS + tag + '-9-lesson-tips.png', full_page=True)
    check(tag + ': lesson says it is not expert-checked and lists sources', 'မစစ်ဆေးရသေး' in page.inner_text('#view') and 'CDC' in page.text_content('#view'))
    page.go_back()
    page.wait_for_selector('.card')
    check(tag + ': phone Back leaves the lesson', page.url.endswith('#/c/survival'), page.url)

    # ---- every standard lesson opens and can be stepped through ----
    ids = page.evaluate("window.OPENPACK_MANIFEST.items.filter(function(i){return i.kind==='lesson'}).map(function(i){return i.id})")
    check(tag + ': pack carries all 9 standard lessons', len(ids) == 9, ids)
    page.goto(base_url + '#/c/survival')
    page.wait_for_selector('.card')
    check(tag + ': emergency tile lists every lesson', page.locator('.card').count() == len(ids), page.locator('.card').count())
    bad = []
    for lid in ids:
        page.goto(base_url + '#/l/' + lid)
        page.wait_for_selector('.lstep')
        n = 1
        while page.locator('.lbtn.main').count():
            if page.locator('.lpic svg').count() != 1 or not text(page, '.ltext').strip() or not no_overflow(page):
                bad.append(lid + ' step ' + str(n))
            page.click('.lbtn.main')
            n += 1
        if page.locator('.ldone').count() != 1 or n < 5:
            bad.append(lid + ' end')
        for tab in ('why', 'tips'):
            page.click('[data-val="%s"]' % tab)
            if page.locator('.lpoint').count() < 3:
                bad.append(lid + ' ' + tab)
    check(tag + ': all lessons step through to the end with pictures, reasons and tips', not bad, bad)
    page.click('#t-lang')
    check(tag + ': lessons switch to English', 'Why' in page.inner_text('.ltabs'), page.inner_text('.ltabs'))
    page.click('#t-lang')

    # ---- help ----
    page.click('#t-help')
    page.wait_for_selector('.acc')
    check(tag + ': help has 6 questions', page.locator('.acc').count() == 6)
    check(tag + ': help fits 360px', no_overflow(page))
    page.screenshot(path=SHOTS + tag + '-4-help.png')

    # ---- share ----
    page.goto(base_url + '#/share')
    if served:
        page.wait_for_selector('.sharebox svg')
        check(tag + ': share draws a QR code and an address', page.locator('.sharebox svg').count() == 1 and text(page, '.sharebox .url').startswith('http://'), text(page, '.sharebox .url'))
        page.screenshot(path=SHOTS + tag + '-5-share.png')
    else:
        page.wait_for_selector('.filenote')
        check(tag + ': share explains it needs the START file', page.locator('.filenote').count() == 1)

    # ---- settings ----
    page.click('#t-set')
    page.wait_for_selector('.setgroup')
    check(tag + ': settings has language, size, colour', page.locator('.setgroup').count() == 3)
    check(tag + ': language buttons are labelled in their own language', 'မြန်မာ' in page.inner_text('.setgroup') and 'English' in page.inner_text('.setgroup'))
    page.screenshot(path=SHOTS + tag + '-6-settings.png')
    page.click('[data-do=lang][data-val="en"]')
    check(tag + ': English applies immediately', text(page, '#t-help').strip() == 'Help' and 'Text size' in page.inner_text('#view'))
    page.click('[data-do=size][data-val="3"]')
    check(tag + ': largest text applies immediately', page.evaluate('getComputedStyle(document.documentElement).fontSize') == '26px')
    check(tag + ': settings fit 360px at largest text', no_overflow(page))
    page.screenshot(path=SHOTS + tag + '-7-settings-xl.png')
    page.click('[data-do=theme][data-val="dark"]')
    check(tag + ': dark colours apply', page.evaluate('getComputedStyle(document.body).backgroundColor') == 'rgb(21, 19, 15)')
    page.goto(base_url + '#/')
    page.wait_for_selector('.tile')
    check(tag + ': home fits 360px at largest text', no_overflow(page))
    page.screenshot(path=SHOTS + tag + '-8-home-xl-dark.png')
    page.reload()
    page.wait_for_selector('.tile')
    cls = page.evaluate('document.documentElement.className')
    check(tag + ': settings survive a reload', 'sz3' in cls and 'dark' in cls and page.evaluate('document.documentElement.lang') == 'en', cls)
    page.click('#t-set')
    page.click('[data-do=reset]')
    cls = page.evaluate('document.documentElement.className')
    check(tag + ': reset restores defaults', cls.strip() == 'sz1' and page.evaluate('document.documentElement.lang') == 'my', cls)

    # quick language switch in the top bar
    page.goto(base_url + '#/')
    page.wait_for_selector('.tile')
    check(tag + ': quick switch is labelled with the OTHER language', 'English' in text(page, '#t-lang'))
    page.click('#t-lang')
    check(tag + ': quick switch changes language', 'Learn' in page.inner_text('#view') and 'မြန်မာ' in text(page, '#t-lang'))

    check(tag + ': no JS errors', not errs, errs)
    ctx.close()


with sync_playwright() as p:
    PW = p
    b = getattr(p, ENGINE).launch() if ENGINE else p.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
    print('engine:', ENGINE or 'chrome', b.version, '| device:', DEVICE or '360x700', '| host:', HOST)

    run_flow(b, 'file://' + pack + 'index.html', 'file', False)

    exe = ROOT + '/dist-bin/openpack-linux-amd64'
    # OP_SERVER="java -cp ... org.openpack.core.ServeMain" runs the same checks against the Android app's server.
    cmd = os.environ['OP_SERVER'].split() if os.environ.get('OP_SERVER') else [exe]
    srv = subprocess.Popen(cmd + ['-dir', pack, '-pub', bundle + 'openpack.pub', '-port', '18090'],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    time.sleep(1.5)
    try:
        run_flow(b, 'http://' + HOST + ':18090/', 'served', True)
        ctx = new_ctx(b, )
        r = ctx.request.get('http://' + HOST + ':18090/fonts/noto-sans-myanmar-myanmar-400-normal.woff2')
        check('served: font file is delivered as a font', r.status == 200 and 'font/woff2' in r.headers.get('content-type', ''), (r.status, r.headers.get('content-type')))
        r = ctx.request.get('http://' + HOST + ':18090/content/learn/sample-lesson.md')
        check('served: .md opens as text, not a download', r.status == 200 and r.headers.get('content-type', '').startswith('text/plain'), r.headers.get('content-type'))
        ctx.close()

        # ---- Android app server only: visitors can take the app's pack away, the sharing phone itself is not offered it ----
        if os.environ.get('OP_SERVER') and os.environ.get('OP_LAN'):
            lan = 'http://' + os.environ['OP_LAN'] + ':18090/'
            v = new_ctx(b, **phone(), accept_downloads=True)
            pv = v.new_page()
            pv.goto(lan + '#/share')
            pv.wait_for_selector('.sharebox')
            check('app: a visitor is offered the library as one file', pv.locator('.takeaway a[href="pack.zip"]').count() == 1)
            names = []
            if pv.locator('.takeaway a[href="pack.zip"]').count() == 1:
                with pv.expect_download() as dl:
                    pv.click('.takeaway a[href="pack.zip"]')
                import zipfile
                names = zipfile.ZipFile(dl.value.path()).namelist()
            check('app: the downloaded file is a whole pack', any(n.endswith('/manifest.sig') for n in names) and any('/content/' in n for n in names), names[:4])
            check('app: the downloaded file carries no page scripts', not any(n.endswith('app.js') or n.endswith('index.html') for n in names))
            v.close()
            o = new_ctx(b, **phone())
            po = o.new_page()
            po.goto('http://localhost:18090/#/share')
            po.wait_for_selector('.sharebox')
            check('app: the sharing phone itself is not offered a download of its own pack', po.locator('.takeaway').count() == 0)
            o.close()

        # ---- very small screens: cheap phones with 240 or 320 pixel wide displays ----
        for w, h in ((240, 320), (320, 480)):
            tiny = new_ctx(b, viewport={'width': w, 'height': h}, has_touch=True)
            pt = tiny.new_page()
            for where, sel in (('#/', '.tile'), ('#/c/survival', '.card'), ('#/l/water-safe', '.lpic svg'), ('#/share', '.sharebox svg'), ('#/settings', '.setgroup'), ('#/help', '.acc')):
                pt.goto('http://localhost:18090/' + where)
                pt.wait_for_selector(sel)
                over = pt.evaluate('document.documentElement.scrollWidth - window.innerWidth')
                check('%dpx screen: %s fits without sideways scrolling' % (w, where), over <= 1, over)
            tiny.close()

        # ---- hub: notice board ----
        base = 'http://' + HOST + ':18090/'
        notices_before = open(bundle + 'notices.txt', 'rb').read() if os.path.exists(bundle + 'notices.txt') else None
        with open(bundle + 'notices.txt', 'w', encoding='utf8') as f:
            f.write('# comment\nဆေးခန်းဖွင့်ရက်\nကြာသပတေးနေ့ မနက် ၉ နာရီ\n<b>bold?</b>\n---\nNew books on Monday\n')
        a = new_ctx(b, **phone())
        pa = a.new_page()
        errs = []
        pa.on('pageerror', lambda e: errs.append(str(e)))
        pa.goto(base)
        pa.wait_for_selector('.noticebar')
        check('hub: home shows the notice bar with the count', '၂' not in text(pa, '.noticebar') and '2' in text(pa, '.noticebar'), text(pa, '.noticebar'))
        pa.click('.noticebar')
        pa.wait_for_selector('.notice')
        check('hub: both notices are listed, Burmese intact', pa.locator('.notice').count() == 2 and 'ဆေးခန်းဖွင့်ရက်' in text(pa, '.notice h3'))
        check('hub: notice text is shown as text, never as HTML', pa.locator('.notice b').count() == 0 and '<b>bold?</b>' in pa.inner_text('.notice'))
        check('hub: notices say they are not part of the library', pa.locator('.fromhub').count() == 1)
        pa.screenshot(path=SHOTS + 'hub-1-notices.png', full_page=True)

        check('hub: no JS errors', not errs, errs)
        a.close()
    finally:
        srv.terminate()
        if 'notices_before' in dir() and notices_before is not None:  # put the hub's own file back
            open(bundle + 'notices.txt', 'wb').write(notices_before)

    # ---- a pack that overrides categories, labels and default language ----
    tmp = tempfile.mkdtemp()
    src = ROOT + '/packs/example'
    shutil.copytree(src, tmp + '/pk')
    cfg = json.load(open(tmp + '/pk/pack.json', encoding='utf8'))
    cfg['default_lang'] = 'en'
    cfg['categories'] = [{'id': 'learn', 'title': 'Lessons', 'title_my': 'သင်ခန်းစာများ', 'icon': 'star'}]
    cfg['ui'] = {'en': {'act_read': 'Open this'}, 'my': {'act_read': 'ဖွင့်ရန်'}}
    json.dump(cfg, open(tmp + '/pk/pack.json', 'w', encoding='utf8'), ensure_ascii=False)
    out = subprocess.run(['node', ROOT + '/bin/openpack.js', 'build', tmp + '/pk', '--out', tmp + '/out'], capture_output=True, text=True)
    check('custom pack builds', out.returncode == 0, out.stdout + out.stderr)
    built = glob.glob(tmp + '/out/*/')[0]
    ctx = new_ctx(b, **phone())
    page = ctx.new_page()
    page.goto('file://' + built + 'index.html')
    page.wait_for_selector('.tile')
    first = page.locator('.tile').first.inner_text()
    check('custom: pack default language is used', page.evaluate('document.documentElement.lang') == 'en')
    check('custom: category name and order come from pack.json', 'Lessons' in first, first)
    page.click('a.tile[href="#/c/learn"]')
    page.wait_for_selector('.act')
    check('custom: label override applies', 'Open this' in text(page, '.act'), text(page, '.act'))
    ctx.close()
    shutil.rmtree(tmp, ignore_errors=True)

    b.close()

bad = [n for n, ok in results if not ok]
print('\n%d/%d passed' % (len(results) - len(bad), len(results)))
sys.exit(1 if bad else 0)
