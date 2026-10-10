"""
Writes ui/cards/preview.html from manifest.json: hand cards, collection hover, pick + burn-away, the
Mythic reveal and the set-complete moment, using only the card assets and plain CSS (no script).
Every animation loops so a screenshot at any time shows a state; the pick loop is 4 s, the reveal 6 s,
the set moment 3 s. All layers use the _x1 files at device scale 1.
"""
import math
import os
import random

PAGE_W = 1960


def _f(m, key, layer=None, scale='x1'):
    e = m['assets'][key]
    files = e['layers'][layer]['files'] if layer else e['files']
    return files[scale]


class Css:
    def __init__(self):
        self.rules = []

    def add(self, s):
        self.rules.append(s)

    def strip(self, cls, m, key, layer=None, mode='bg', dur=None, iteration='infinite', delay=None,
              offset=None, frames=None):
        """A looping strip layer. mode: 'mask' (tinted by background-color: var(--c)) or 'bg'."""
        e = m['assets'][key]
        fw, fh = e['frame']
        n = frames or e['frames']
        url = _f(m, key, layer)
        ox, oy = offset if offset is not None else e.get('offset', [0, 0])
        dur = dur if dur is not None else n / float(e.get('fps', 10))
        name = 'k-' + cls
        if mode == 'mask':
            body = ('background-color:var(--c);-webkit-mask-image:url(%s);-webkit-mask-size:%dpx %dpx;'
                    '-webkit-mask-repeat:no-repeat;' % (url, fw * n, fh))
            prop = '-webkit-mask-position'
        else:
            body = 'background-image:url(%s);background-size:%dpx %dpx;background-repeat:no-repeat;' % (url, fw * n, fh)
            prop = 'background-position'
        anim = 'animation:%s %.3fs steps(%d) %s%s;' % (name, dur, n, iteration, (' %s' % delay) if delay else '')
        self.add('.%s{position:absolute;left:%dpx;top:%dpx;width:%dpx;height:%dpx;pointer-events:none;%s%s}'
                 % (cls, ox, oy, fw, fh, body, anim))
        self.add('@keyframes %s{from{%s:0 0}to{%s:-%dpx 0}}' % (name, prop, prop, fw * n))

    def oneshot(self, cls, m, key, layer=None, mode='bg', period=4.0, start=1.0, frame_ms=None,
                offset=None, first_frame=0, hold_last=False):
        """Plays a strip once inside a looping period: empty before `start`, frames, then empty."""
        e = m['assets'][key]
        fw, fh = e['frame']
        n = e['frames'] - first_frame
        url = _f(m, key, layer)
        ox, oy = offset if offset is not None else e.get('offset', [0, 0])
        fms = frame_ms or (1000.0 / e.get('fps', 10))
        p0 = start / period * 100
        p1 = (start + n * fms / 1000.0) / period * 100
        name = 'k-' + cls
        if mode == 'mask':
            body = ('background-color:var(--c);-webkit-mask-image:url(%s);-webkit-mask-size:%dpx %dpx;'
                    '-webkit-mask-repeat:no-repeat;' % (url, fw * e['frames'], fh))
            prop = '-webkit-mask-position'
        else:
            body = ('background-image:url(%s);background-size:%dpx %dpx;background-repeat:no-repeat;'
                    % (url, fw * e['frames'], fh))
            prop = 'background-position'
        before = '%dpx 0' % fw                                   # past the strip's left end: nothing drawn
        start_pos = -fw * first_frame
        end_pos = -fw * (first_frame + n) if not hold_last else -fw * (first_frame + n - 1)
        steps = n if not hold_last else n - 1
        self.add('.%s{position:absolute;left:%dpx;top:%dpx;width:%dpx;height:%dpx;pointer-events:none;%s'
                 'animation:%s %.3fs steps(1) infinite}' % (cls, ox, oy, fw, fh, body, name, period))
        # Opacity gates the layer too: Ultralight ignores a mask whose image is positioned out of
        # the box (draws the full colour), so "past the strip" is not enough on its own for masks.
        kf = ['0%%{%s:%s;opacity:0}' % (prop, before),
              '%.3f%%{%s:%s;opacity:0}' % (max(0, p0 - 0.01), prop, before),
              '%.3f%%{%s:%dpx 0;opacity:1;-webkit-animation-timing-function:steps(%d);animation-timing-function:steps(%d)}'
              % (p0, prop, start_pos, steps, steps),
              '%.3f%%{%s:%dpx 0;opacity:1}' % (p1 if not hold_last else p1 - (fms / 1000.0) / period * 100, prop, end_pos),
              '%.3f%%{%s:%dpx 0;opacity:%d}' % (p1 + 0.01, prop, end_pos, 1 if hold_last else 0),
              '100%%{%s:%dpx 0;opacity:%d}' % (prop, end_pos, 1 if hold_last else 0)]
        self.add('@keyframes %s{%s}' % (name, ''.join(kf)))

    def text(self):
        return '\n'.join(self.rules)


def gem(m, rarity):
    order = m['assets']['icons/gems']['order']
    return '<i class="gem" style="background-position:-%dpx 0"></i>' % (14 * order.index(rarity))


def glyph(m, kw, cls='glyph', size=16):
    order = m['assets']['icons/glyphs']['order']
    return '<i class="%s" style="background-position:-%dpx 0"></i>' % (cls, size * order.index(kw))


def dots(m, rarity, n):
    order = m['assets']['icons/trait_dots']['order']
    return ''.join('<i class="tdot" style="background-position:-%dpx 0"></i>' % (6 * order.index(rarity))
                   for _ in range(n))


def hand_face(m, eyebrow, eyecol, rarity, name, desc, kws, art_kw, colour, extra=''):
    k = ''.join('<span class="kw">%s%s</span>' % (glyph(m, w), w.capitalize()) for w in kws)
    return ('<div class="face">'
            '<div class="eyebrow" style="color:%s">%s%s</div>'
            '<div class="art" style="background:%s">%s</div>'
            '<div class="name">%s</div><div class="desc">%s</div><div class="kws">%s</div>%s</div>'
            % (eyecol, gem(m, rarity) if rarity else '', eyebrow, art_bg(colour), glyph(m, art_kw, 'glyph5', 80),
               name, desc, k, extra))


def art_bg(colour):
    return 'rgba(%d,%d,%d,0.16)' % tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))


def mythic_face(m):
    return ('<div class="face mface">'
            '<div class="eyebrow mythic">+&nbsp;&nbsp;MYTHIC&nbsp;&nbsp;+</div>'
            '<div class="art mart">%s<div class="foil"></div></div>'
            '<div class="name">HEART OF THE DEEP</div>'
            '<div class="desc">Darkness can\'t touch you; wardens only hear you run.</div>'
            '<div class="prov">WORLD FIRST &middot; ASHFORD &middot; DAY 41</div></div>' % glyph(m, 'deep', 'glyph5', 80))


def motes(m, n, w, h, seed, cls_prefix):
    rnd = random.Random(seed)
    cols = ['white', 'gold', 'cyan', 'pink']
    out = []
    for i in range(n):
        ang = rnd.random() * 2 * math.pi
        rx = w / 2 + 40 + rnd.random() * 90
        ry = h / 2 + 40 + rnd.random() * 90
        x = w / 2 + math.cos(ang) * rx - 5
        y = h / 2 + math.sin(ang) * ry - 5
        out.append('<i class="mote %s-%s" style="left:%dpx;top:%dpx;-webkit-animation-delay:-%.1fs,-%.1fs;'
                   'animation-delay:-%.1fs,-%.1fs"></i>'
                   % (cls_prefix, cols[i % 4], x, y, i * 0.1, i * 0.37, i * 0.1, i * 0.37))
    return ''.join(out)


def aura(m, prefix):
    return ('<div class="%s-aura aura">'
            '<div class="bloom"></div>'
            '<div class="rays r1"></div><div class="rays r2"></div>'
            '<div class="ring"></div><div class="halo"></div>%s</div>' % (prefix, motes(m, 22, 248, 328, 7, 'mote')))


def write(out, m):
    a = m['assets']
    css = Css()
    sk = m['colours']['skills']
    sp = m['colours']['special']
    rc = m['colours']['rarity']

    # Hand edges + embers + hover (hot) layers.
    for r in ('common', 'rare', 'epic', 'cursed', 'wildcard', 'hover'):
        css.strip('e-%s-f' % r, m, 'hand/edge_' + r, 'fill', 'mask')
        css.strip('e-%s-g' % r, m, 'hand/edge_' + r, 'glow', 'bg')
    for e in ('sparse', 'dense'):
        css.strip('em-%s-f' % e, m, 'hand/embers_' + e, 'fill', 'mask')
        css.strip('em-%s-g' % e, m, 'hand/embers_' + e, 'glow', 'bg')
    css.strip('my-edge', m, 'mythic/edge')
    for c in ('white', 'gold', 'cyan', 'pink'):
        e = a['mythic/mote_' + c]
        css.add('.mote-%s{background-image:url(%s);background-size:%dpx %dpx}' % (
            c, _f(m, 'mythic/mote_' + c), e['strip'][0], e['strip'][1]))
    css.add('@keyframes k-mote{from{background-position:0 0}to{background-position:-120px 0}}')
    css.add('@keyframes k-drift{from{-webkit-transform:translate(0,0);transform:translate(0,0)}'
            'to{-webkit-transform:translate(6px,-14px);transform:translate(6px,-14px)}}')
    # Foil sheen, 16 frames at 10 fps, once every 4 s (out-of-range position = nothing drawn).
    fo = a['mythic/foil']
    css.add('.foil{position:absolute;left:-28px;top:-%dpx;width:248px;height:328px;background-image:url(%s);'
            'background-size:%dpx 328px;background-repeat:no-repeat;animation:k-foil 4s steps(1) infinite}'
            % (52, _f(m, 'mythic/foil'), fo['strip'][0]))
    css.add('@keyframes k-foil{0%{background-position:0 0;animation-timing-function:steps(16)}'
            '40%{background-position:-3968px 0}100%{background-position:-3968px 0}}')
    # Rays / ring / halo.
    css.add('.aura{position:absolute;left:0;top:0;width:248px;height:328px;pointer-events:none}')
    css.add('.bloom{position:absolute;left:-196px;top:-156px;width:640px;height:640px;'
            'background:radial-gradient(circle,rgba(243,210,139,0.30) 0%,rgba(150,110,220,0.14) 38%,rgba(0,0,0,0) 70%)}')
    css.add('.rays{position:absolute;left:-132px;top:-92px;width:512px;height:512px;background:url(%s) 0 0/512px 512px no-repeat}'
            % _f(m, 'mythic/rays'))
    css.add('.r1{animation:k-rot 60s linear infinite}.r2{opacity:.6;animation:k-rot 90s linear infinite reverse}')
    css.add('@keyframes k-rot{from{-webkit-transform:rotate(0deg);transform:rotate(0deg)}'
            'to{-webkit-transform:rotate(360deg);transform:rotate(360deg)}}')
    css.add('.ring{position:absolute;left:-116px;top:-76px;width:480px;height:480px;background:url(%s) 0 0/480px 480px no-repeat;'
            'animation:k-pulse 2.4s ease-in-out infinite}' % _f(m, 'mythic/ring'))
    css.add('@keyframes k-pulse{0%{-webkit-transform:scale(.95);transform:scale(.95);opacity:.85}'
            '100%{-webkit-transform:scale(1.08);transform:scale(1.08);opacity:0}}')
    css.add('.halo{position:absolute;left:-60px;top:-60px;width:368px;height:448px;'
            'background:radial-gradient(ellipse,rgba(143,240,255,0) 52%,rgba(143,240,255,0.16) 60%,rgba(255,155,234,0.14) 66%,rgba(0,0,0,0) 72%)}')
    css.add('.mote{position:absolute;width:10px;height:10px;background-repeat:no-repeat;'
            'animation:k-mote 1.2s steps(12) infinite,k-drift 3s ease-in-out infinite alternate}')

    # Collection hover.
    for r in ('common', 'rare', 'epic', 'cursed', 'wildcard'):
        css.strip('ch-%s-f' % r, m, 'collection/hover_' + r, 'fill', 'mask')
        css.strip('ch-%s-g' % r, m, 'collection/hover_' + r, 'glow', 'bg')

    # Pick + burn-away (4 s loop, the pick at 1.0 s).
    P, T0 = 4.0, 1.0
    css.oneshot('flare-f', m, 'hand/flare', 'fill', 'mask', P, T0, 50)
    css.oneshot('flare-g', m, 'hand/flare', 'glow', 'bg', P, T0, 50)
    css.oneshot('bf-f', m, 'hand/burn_front', 'fill', 'mask', P, T0, 75)
    css.oneshot('bf-g', m, 'hand/burn_front', 'glow', 'bg', P, T0, 75)
    css.oneshot('ash', m, 'hand/burn_ash', None, 'bg', P, T0, 75)
    bm = a['hand/burn_mask']
    css.add('.burnme{position:absolute;left:0;top:0;width:248px;height:328px;-webkit-mask-image:url(%s);-webkit-mask-size:%dpx 328px;-webkit-mask-repeat:no-repeat;'
            'animation:k-burnmask %.1fs steps(1) infinite}' % (_f(m, 'hand/burn_mask'), bm['strip'][0], P))
    p0, p1 = T0 / P * 100, (T0 + 0.6) / P * 100
    css.add('@keyframes k-burnmask{0%%{-webkit-mask-position:0 0}%.2f%%{-webkit-mask-position:-248px 0;'
            'animation-timing-function:steps(8)}%.2f%%{-webkit-mask-position:-%dpx 0}'
            '96%%{-webkit-mask-position:-%dpx 0}100%%{-webkit-mask-position:0 0}}'
            % (p0, p1, 248 * 9, 248 * 9))
    css.add('.burnfade{animation:k-burnfade %.1fs linear infinite}' % P)
    css.add('@keyframes k-burnfade{0%%{opacity:1}%.2f%%{opacity:1}%.2f%%{opacity:0}90%%{opacity:0}100%%{opacity:1}}'
            % (p0, p0 + 3))
    css.add('.kept{animation:k-kept %.1fs ease-out infinite}' % P)
    css.add('@keyframes k-kept{0%%{-webkit-transform:none;transform:none;opacity:1}%.2f%%{-webkit-transform:none;transform:none;opacity:1}'
            '%.2f%%{-webkit-transform:translateY(-6px) scale(1.06);transform:translateY(-6px) scale(1.06);opacity:1}'
            '%.2f%%{-webkit-transform:translateY(-6px) scale(1.06);transform:translateY(-6px) scale(1.06);opacity:1}'
            '%.2f%%{-webkit-transform:translateY(-90px) scale(.6);transform:translateY(-90px) scale(.6);opacity:0}'
            '90%%{opacity:0}100%%{-webkit-transform:none;transform:none;opacity:1}}'
            % (p0, p0 + 2, p0 + 30, p0 + 42))
    css.add('.flarehalo{position:absolute;left:-80px;top:-80px;width:408px;height:488px;pointer-events:none;'
            'background:radial-gradient(ellipse,rgba(39,197,181,0.45) 0%%,rgba(39,197,181,0.12) 45%%,rgba(0,0,0,0) 70%%);'
            'opacity:0;animation:k-fh %.1fs linear infinite}' % P)
    css.add('@keyframes k-fh{0%%{opacity:0}%.2f%%{opacity:0}%.2f%%{opacity:1}%.2f%%{opacity:0}100%%{opacity:0}}'
            % (p0, p0 + 5, p0 + 30))

    # Mythic reveal (6 s loop).
    R = 6.0
    css.oneshot('crack', m, 'mythic/crack', None, 'bg', R, 0.6, 100)
    bu = a['mythic/burst']
    css.oneshot('burst', m, 'mythic/burst', None, 'bg', R, 1.4, 100, offset=[124 - 224, 164 - 224])
    css.add('.backc{position:absolute;left:-28px;top:-28px;width:304px;height:384px;'
            'background:url(%s) 0 0/304px 384px no-repeat}' % _f(m, 'hand/back'))
    pct = lambda t: t / R * 100
    css.add('.rv-back{animation:k-rvback %.1fs linear infinite}' % R)
    css.add('@keyframes k-rvback{0%%{-webkit-transform:none;transform:none;opacity:1}'
            '%.2f%%{-webkit-transform:translate(1px,0);transform:translate(1px,0)}'
            '%.2f%%{-webkit-transform:translate(-1px,1px);transform:translate(-1px,1px)}'
            '%.2f%%{-webkit-transform:translate(1px,-1px);transform:translate(1px,-1px)}'
            '%.2f%%{-webkit-transform:scaleX(1);transform:scaleX(1);opacity:1}'
            '%.2f%%{-webkit-transform:scaleX(0);transform:scaleX(0);opacity:1}'
            '%.2f%%{opacity:0}100%%{opacity:0}}'
            % (pct(0.8), pct(1.0), pct(1.2), pct(1.4), pct(1.5), pct(1.51)))
    css.add('.rv-face{animation:k-rvface %.1fs linear infinite}' % R)
    css.add('@keyframes k-rvface{0%%{opacity:0;-webkit-transform:scaleX(0);transform:scaleX(0)}'
            '%.2f%%{opacity:0;-webkit-transform:scaleX(0);transform:scaleX(0)}'
            '%.2f%%{opacity:1;-webkit-transform:scaleX(0);transform:scaleX(0)}'
            '%.2f%%{opacity:1;-webkit-transform:scaleX(1);transform:scaleX(1)}'
            '%.2f%%{opacity:1;-webkit-transform:scaleX(1);transform:scaleX(1)}100%%{opacity:0}}'
            % (pct(1.5), pct(1.51), pct(1.6), pct(5.6)))
    css.add('.rv-aura{animation:k-rvaura %.1fs linear infinite}' % R)
    css.add('@keyframes k-rvaura{0%%{opacity:0}%.2f%%{opacity:0}%.2f%%{opacity:1}%.2f%%{opacity:1}100%%{opacity:0}}'
            % (pct(1.6), pct(2.2), pct(5.6)))
    css.add('.rv-dim{position:absolute;left:0;top:0;right:0;bottom:0;background:radial-gradient(ellipse,rgba(0,0,0,0) 20%%,'
            'rgba(0,0,0,0.75) 70%%);animation:k-dim %.1fs linear infinite}' % R)
    css.add('@keyframes k-dim{0%%{opacity:0}%.2f%%{opacity:1}%.2f%%{opacity:1}%.2f%%{opacity:.4}100%%{opacity:0}}'
            % (pct(0.6), pct(1.4), pct(2.4)))
    css.add('.flash{position:absolute;left:-200px;top:-160px;width:648px;height:648px;pointer-events:none;'
            'background:radial-gradient(circle,rgba(255,255,255,0.9) 0%%,rgba(255,246,222,0.35) 30%%,rgba(0,0,0,0) 62%%);'
            'opacity:0;animation:k-flash %.1fs linear infinite}' % R)
    css.add('@keyframes k-flash{0%%{opacity:0}%.2f%%{opacity:0}%.2f%%{opacity:1}%.2f%%{opacity:0}100%%{opacity:0}}'
            % (pct(1.38), pct(1.45), pct(1.9)))

    # Set complete (3 s loop).
    S = 3.0
    css.oneshot('set-rays', m, 'set/rays', None, 'bg', S, 0.1, 100, offset=[0, 0])
    css.oneshot('set-sp1', m, 'set/sparks', None, 'mask', S, 0.1, 100, offset=[0, 0])
    css.oneshot('set-sp2', m, 'set/sparks', None, 'mask', S, 0.2, 100, offset=[0, 0])
    css.add('.set-sp2{-webkit-transform:scaleX(-1);transform:scaleX(-1)}')
    css.add('.settile{position:absolute;left:152px;top:88px;width:80px;height:80px;'
            'animation:k-tile %.1fs ease-out infinite}' % S)
    css.add('@keyframes k-tile{0%{-webkit-transform:scale(.4);transform:scale(.4);opacity:0}'
            '8%{-webkit-transform:scale(1.12);transform:scale(1.12);opacity:1}12%{-webkit-transform:scale(1);transform:scale(1)}'
            '85%{opacity:1}100%{opacity:0}}')
    css.add('.setbloom{position:absolute;left:-8px;top:-72px;width:400px;height:400px;'
            'background:radial-gradient(circle,rgba(232,221,188,0.28) 0%,rgba(0,0,0,0) 62%)}')

    g = a['icons/glyphs']
    style = '''
@font-face{font-family:Outfit;font-weight:400;src:url(../fonts/outfit-400.ttf)}
@font-face{font-family:Outfit;font-weight:500;src:url(../fonts/outfit-500.ttf)}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:#0B0B0C;color:#EDEEEF;font-family:Outfit,sans-serif;font-weight:500}
body{width:%(W)dpx;padding:28px 36px}
h1{font-size:22px;font-weight:500;margin-bottom:4px}
h2{font-size:10px;letter-spacing:2px;color:#9A9DA1;margin:22px 0 10px;font-weight:500}
.sub{font-size:12px;color:#9A9DA1}
.panel{position:relative;background:#131315;border-radius:16px;padding:44px 40px}
.row{position:relative;height:328px}
.slot{position:absolute;top:44px;width:248px;height:328px}
.lbl{position:absolute;top:-36px;left:0;width:248px;text-align:center;font-size:10px;letter-spacing:1.5px;color:#808387}
.face{position:absolute;left:0;top:0;width:248px;height:328px;border-radius:6px;background:#141012;overflow:hidden}
.eyebrow{position:absolute;left:28px;top:28px;font-size:9.5px;letter-spacing:2px;height:14px;line-height:14px}
.gem{display:inline-block;width:14px;height:14px;vertical-align:top;margin-right:6px;background:url(%(gems)s) 0 0/84px 14px no-repeat}
.art{position:absolute;left:28px;top:52px;width:192px;height:96px;border-radius:6px;overflow:hidden}
.glyph5{position:absolute;left:56px;top:8px;width:80px;height:80px;background:url(%(g5)s) 0 0/%(g5w)dpx 80px no-repeat}
.name{position:absolute;left:28px;top:160px;width:192px;text-align:center;font-size:15px;letter-spacing:1px}
.desc{position:absolute;left:28px;top:184px;width:192px;text-align:center;font-size:11px;font-weight:400;color:#9A9DA1;line-height:15px}
.kws{position:absolute;left:28px;top:226px;width:192px;text-align:center;font-size:10.5px;color:#D8CDBB}
.kw{display:inline-block;padding:2px 8px 2px 4px;margin:0 3px;border-radius:9px;background:#212225;line-height:16px}
.glyph{display:inline-block;width:16px;height:16px;vertical-align:top;margin-right:4px;background:url(%(g1)s) 0 0/%(g1w)dpx 16px no-repeat}
.tdot{display:inline-block;width:6px;height:6px;margin:0 2px;background:url(%(dots)s) 0 0/36px 6px no-repeat}
.prov{position:absolute;left:28px;top:262px;width:192px;text-align:center;font-size:9px;letter-spacing:1.5px;color:#C9A866}
.mface{background:#1C1530}
.mythic{left:28px;width:192px;text-align:center;color:#F3D28B}
.mart{background:radial-gradient(ellipse,rgba(143,240,255,0.22) 0%%,rgba(120,90,200,0.22) 50%%,rgba(28,21,48,1) 80%%)}
.hot{opacity:0}
.hoverdemo{animation:k-hdemo 3s ease-out infinite}
@keyframes k-hdemo{0%%,30%%{-webkit-transform:none;transform:none}36%%,80%%{-webkit-transform:translateY(-16px) scale(1.05);transform:translateY(-16px) scale(1.05)}88%%,100%%{-webkit-transform:none;transform:none}}
.hoverdemo .hot{animation:k-hhot 3s linear infinite}
@keyframes k-hhot{0%%,30%%{opacity:0}34%%,80%%{opacity:1}86%%,100%%{opacity:0}}
.slot.live:hover{-webkit-transform:translateY(-16px) scale(1.05);transform:translateY(-16px) scale(1.05)}
.slot.live:hover .hot{opacity:1}
.ccard{position:absolute;top:30px;width:96px;height:128px;-webkit-transition:-webkit-transform .12s ease-out;transition:transform .12s ease-out}
.cface{position:absolute;left:0;top:0;width:96px;height:128px;border-radius:6px;background:#141012;border:1.5px solid var(--r)}
.cart{position:absolute;left:8.5px;top:8.5px;width:76px;height:52px;border-radius:4px;background:#2b2216}
.cart .g3{position:absolute;left:14px;top:2px;width:48px;height:48px;background:url(%(g3)s) 0 0/%(g3w)dpx 48px no-repeat}
.cname{position:absolute;left:0;top:68px;width:93px;text-align:center;font-size:11px}
.cglyph{position:absolute;left:0;top:86px;width:93px;text-align:center}
.cglyph .glyph{margin:0 2px}
.cdots{position:absolute;left:0;top:108px;width:93px;text-align:center;line-height:6px}
.cgem{position:absolute;right:8px;top:8px}
.ccard .hov{opacity:0}
.ccard:hover,.ccard.forced{-webkit-transform:translateY(-4px);transform:translateY(-4px)}
.ccard:hover .hov,.ccard.forced .hov{opacity:1}
.stage{position:relative;height:520px;border-radius:16px;background:#0e0d12}
''' % {'W': PAGE_W, 'gems': _f(m, 'icons/gems'), 'g1': _f(m, 'icons/glyphs'), 'g1w': g['strip'][0],
       'g5': _f(m, 'icons/glyphs', None, 'x5'), 'g5w': g['strip'][0] * 5, 'g3': _f(m, 'icons/glyphs', None, 'x3'),
       'g3w': g['strip'][0] * 3, 'dots': _f(m, 'icons/trait_dots')}

    def edge_layers(r, embers=None, hot=True):
        s = '<div class="e-%s-f"></div><div class="e-%s-g"></div>' % (r, r)
        if embers:
            s += '<div class="em-%s-f"></div><div class="em-%s-g"></div>' % (embers, embers)
        if hot:
            s += '<div class="hot"><div class="e-hover-f"></div><div class="e-hover-g"></div></div>'
        return s

    hand = [
        ('COMMON &middot; MINING', 'common', sk['mining'], 'common', 'PROSPECTOR', 'Ore you mine has a 5 % chance to drop one extra.', ['hoard'], 'hoard', 'sparse', 'live'),
        ('RARE &middot; SMITHING', 'rare', sk['smithing'], 'rare', 'TEMPERED EDGE', 'Blades you forge keep their edge 20 % longer.', ['iron', 'keen'], 'iron', 'sparse', 'live'),
        ('EPIC &middot; STEALTH (HOVER DEMO)', 'epic', sk['stealth'], 'epic', 'NIGHTSTEP', 'Crouching in darkness hides your name and your steps.', ['echo', 'deep'], 'echo', 'dense', 'hoverdemo'),
        ('CURSED &middot; MINING', 'cursed', sp['cursed'], 'cursed', 'GREEDY PICK', 'Dig 30 % faster. One ore in eight crumbles to stone.', ['hex', 'brutal'], 'hex', 'dense', 'live'),
        ('WILDCARD &middot; ATHLETICS', 'wildcard', sk['athletics'], 'rare', 'IRONLUNG', 'Hold your breath 15 s longer and mine at full speed under water.', ['tide'], 'tide', 'sparse', 'live'),
    ]
    eyecol = {'common': rc['common'], 'rare': rc['rare'], 'epic': rc['epic'], 'cursed': sp['cursed'], 'wildcard': sk['athletics']}
    html_hand = []
    x = 0
    for lbl, r, col, rar, name, desc, kws, art, emb, mode in hand:
        face = hand_face(m, lbl.split(' (')[0].split(' &middot; ')[0] + ' &middot; II', eyecol[r], rar, name, desc, kws, art, col)
        html_hand.append('<div class="slot %s" style="left:%dpx;--c:%s"><div class="lbl">%s</div>%s%s</div>'
                         % (mode, x, col, lbl, face, edge_layers(r, emb)))
        x += 304
    html_hand.append('<div class="slot" style="left:%dpx"><div class="lbl">MYTHIC (AURA)</div>%s%s<div class="my-edge"></div></div>'
                     % (x, aura(m, 'hm'), mythic_face(m)))

    coll = [('Prospector', 'common', 'hoard', 1, sk['mining']), ('Tunneler', 'rare', 'echo', 2, sk['mining']),
            ('Quarryman', 'epic', 'brutal', 3, sk['mining']), ('Greedy Pick', 'cursed', 'hex', 2, sp['cursed']),
            ('Ironlung', 'wildcard', 'tide', 2, sk['athletics']), ('Hard Hat', 'rare', 'iron', 2, sk['mining']),
            ('Nightstep', 'epic', 'echo', 3, sk['stealth']), ('Fencer', 'epic', 'swift', 3, sk['blades'])]
    html_coll = []
    for i, (name, r, kw, n, col) in enumerate(coll):
        rr = 'rare' if r == 'wildcard' else r
        forced = ' forced' if i in (1, 4) else ''
        order = a['icons/glyphs']['order']
        html_coll.append(
            '<div class="ccard%s" style="left:%dpx;--c:%s;--r:%s"><div class="cface"><div class="cart">'
            '<i class="g3" style="background-position:-%dpx 0"></i></div><div class="cgem">%s</div>'
            '<div class="cname">%s</div><div class="cglyph">%s</div><div class="cdots">%s</div></div>'
            '<div class="hov"><div class="ch-%s-f"></div><div class="ch-%s-g"></div></div></div>'
            % (forced, i * 124, col, rc[rr] if r != 'wildcard' else sk['athletics'], 48 * order.index(kw),
               gem(m, rr).replace('margin-right:6px', ''), name, glyph(m, kw), dots(m, rr, n), r, r))
    order = a['icons/glyphs']['order']
    html_coll.append('<div class="ccard" style="left:%dpx;--r:%s"><div class="cface" style="background:#1C1530">'
                     '<div class="cart" style="background:#2a2140"><i class="g3" style="background-position:-%dpx 0"></i></div>'
                     '<div class="cname">Heart of the Deep</div></div></div>'
                     % (8 * 124, rc['mythic'], 48 * order.index('deep')))

    pick = ('<div class="slot" style="left:0;--c:%s"><div class="lbl">BURNS AWAY</div>'
            '<div class="burnme">%s</div><div class="burnfade">%s</div>'
            '<div class="bf-f"></div><div class="bf-g"></div><div class="ash"></div></div>'
            % (sk['mining'], hand_face(m, 'RARE &middot; II', rc['rare'], 'rare', 'ROCK HAULER', 'Stone, ore, raw metal and gems weigh 40 % less.', ['hoard'], 'hoard', sk['mining']),
               edge_layers('rare', 'sparse', False)))
    pick += ('<div class="slot kept" style="left:300px;--c:%s"><div class="lbl">KEPT &middot; FLARES</div>'
             '<div class="flarehalo"></div>%s%s<div class="flare-f"></div><div class="flare-g"></div></div>'
             % (sk['athletics'], hand_face(m, 'WILDCARD &middot; II', sk['athletics'], 'rare', 'IRONLUNG', 'Hold your breath 15 s longer and mine at full speed under water.', ['tide'], 'tide', sk['athletics']),
                edge_layers('wildcard', 'sparse', False)))
    pick += ('<div class="slot" style="left:600px;--c:%s"><div class="lbl">BURNS AWAY</div>'
             '<div class="burnme">%s</div><div class="burnfade">%s</div>'
             '<div class="bf-f"></div><div class="bf-g"></div><div class="ash"></div></div>'
             % (sp['cursed'], hand_face(m, 'CURSED &middot; II', sp['cursed'], 'cursed', 'QUARRYMAN', 'Stone-like blocks mine 30 % faster. The price: ore yields drop.', ['brutal'], 'brutal', sp['cursed']),
                edge_layers('cursed', 'dense', False)))

    reveal = ('<div class="rv-dim"></div>'
              '<div class="slot" style="left:160px;top:96px">'
              '<div class="rv-aura">%s</div>'
              '<div class="rv-back"><div class="backc"></div><div class="crack"></div></div>'
              '<div class="flash"></div>'
              '<div class="rv-face">%s<div class="my-edge"></div></div>'
              '<div class="burst"></div></div>' % (aura(m, 'rv'), mythic_face(m)))
    setm = ('<div style="position:absolute;left:640px;top:120px;width:384px;height:256px;--c:%s">'
            '<div class="setbloom"></div><div class="set-rays"></div><div class="set-sp1"></div>'
            '<div class="set-sp2" style="--c:%s"></div>'
            '<div class="settile" style="background:#212225;border-radius:14px">'
            '<i class="glyph5" style="left:0;top:0;background-position:-%dpx 0"></i></div>'
            '<div style="position:absolute;left:0;top:210px;width:384px;text-align:center;font-size:18px;letter-spacing:2px">DEEP 6 &middot; UNDERDARK</div>'
            '<div style="position:absolute;left:0;top:-30px;width:384px;text-align:center;font-size:10px;letter-spacing:3px;color:#9A9DA1">SET COMPLETE</div></div>'
            % (sk['mining'], sk['survival'], 80 * order.index('deep')))
    back_static = ('<div class="slot" style="left:1180px;top:96px"><div class="backc"></div>'
                   '<div class="lbl" style="top:-40px">CARD BACK (GLOW BAKED)</div></div>')

    html = '''<!doctype html>
<html><head><meta charset="utf-8"><title>VOID Mettle cards preview</title>
<style>%s
%s</style></head><body>
<h1>VOID Mettle &middot; card textures</h1>
<div class="sub">Every effect here is a PNG strip from ui/cards (manifest.json) stepped with CSS steps(); burning edges are white masks tinted with the skill colour. Loops: hand always, hover demo 3 s, pick 4 s, Mythic reveal 6 s, set moment 3 s.</div>
<h2>HAND MODE &middot; 248 &times; 328 &middot; EDGE FLICKER 4 FRAMES @ 8 FPS &middot; EMBERS 6 @ 5 FPS</h2>
<div class="panel" style="height:420px"><div class="row">%s</div></div>
<h2>COLLECTION MODE &middot; 96 &times; 128 &middot; CALM AT REST, 2-FRAME FLICKER @ 6 FPS ON HOVER (TUNNELER AND IRONLUNG FORCED)</h2>
<div class="panel" style="height:206px"><div style="position:relative;height:150px">%s</div></div>
<h2>PICK &middot; KEPT CARD FLARES 200 MS, THE OTHER TWO BURN AWAY 8 &times; 75 MS (LOOP 4 S, PICK AT 1.0 S)</h2>
<div class="panel" style="height:430px"><div class="row" style="left:240px">%s</div></div>
<h2>MYTHIC REVEAL (LOOP 6 S: DARKEN, CRACKS 0.6&ndash;1.4 S, FLIP + BURST 1.4 S, AURA) &middot; SET COMPLETE (LOOP 3 S)</h2>
<div class="stage">%s%s%s</div>
</body></html>
''' % (style, css.text(), ''.join(html_hand), ''.join(html_coll), pick, reveal, setm, back_static)
    with open(os.path.join(out, 'preview.html'), 'w', newline='\n', encoding='utf-8') as fh:
        fh.write(html)
