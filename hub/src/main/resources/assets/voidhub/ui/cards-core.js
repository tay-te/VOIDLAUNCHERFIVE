// VOID Mettle cards v2 in the hub: shared parts (Figma 3c0Wgc4ZKFSpm98fWJoARS, page 295:2, section 2h 546:23595).
//
//   MC.h / esc / patch      small DOM helpers; patch() rewrites a part only when its key changed
//   MC.pix(text, t, colour) pixel-font names (hand mode only) as one SVG path, crisp at any scale
//   MC.css(scale)           the texture rules for the device scale (README: never let Ultralight enlarge a strip)
//   MC.ccard / hcard / row  the three card modes: collection 96x128, hand 248x328, row 52 high
//
// The page is still: every moving part of a card (burning edges, embers, the hot frame, flares, burn-away, the
// Mythic's fire, rays, motes, ring and foil, glows, a hovered card's lift) is reported to Java with MC.fx and drawn
// natively over the page (dev.voidmc.hub.screen.CardFx), so Ultralight only repaints when something really changes.
(function () {
  "use strict";

  var MC = window.MC = {};
  MC.icons = {};

  MC.$ = function (id) { return document.getElementById(id); };
  var esc = MC.esc = function (s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  };
  MC.upper = function (s) { return String(s || "").toUpperCase(); };

  MC.send = function (msg) {
    try {
      if (typeof window.__void_native === "function") return window.__void_native(JSON.stringify(msg));
    } catch (e) { /* never throw into the host */ }
    return null;
  };
  /** One Mettle action (Bridge.METTLE_OPS): the op and its one string argument. */
  MC.act = function (op, arg) { MC.send({type: "mettle", op: op, arg: arg == null ? "" : String(arg)}); };
  /** A card sound Mettle times to our animation (SOUNDS.md §5). */
  MC.sound = function (cue, arg) { MC.act("cardSound", cue + "#" + (arg || "")); };

  /** Rewrites el's content only when key changed: a state tick that changes nothing on screen paints nothing. */
  MC.patch = function (el, key, html) {
    if (!el || el.__k === key) return false;
    el.__k = key;
    el.innerHTML = typeof html === "function" ? html() : html;
    return true;
  };
  /** A child div made once (by id), so later updates patch it instead of rebuilding its parent. */
  MC.part = function (host, id, cls, style) {
    var el = document.getElementById(id);
    if (!el) {
      el = document.createElement("div");
      el.id = id;
      host.appendChild(el);
    }
    if (cls != null && el.className !== cls) el.className = cls;
    if (style != null && el.__s !== style) { el.__s = style; el.setAttribute("style", style); }
    return el;
  };
  // ---- native effects (CardFx): the page reports its moving layers, Java draws them --------------------------------
  var fxGroups = {}, fxOrder = [], fxSent = "";
  /** Replaces one group's layers (draft, hover, pulse, …) and sends the whole set when it changed. */
  MC.fxSet = function (group, items) {
    if (fxOrder.indexOf(group) < 0) fxOrder.push(group);
    fxGroups[group] = items || [];
    var all = [];
    for (var i = 0; i < fxOrder.length; i++) all = all.concat(fxGroups[fxOrder[i]] || []);
    var key = JSON.stringify(all);
    if (key === fxSent) return;
    fxSent = key;
    MC.send({type: "fx", items: all});
  };
  /** The last set sent to Java, as JSON (tests). */
  MC.fxDebug = function () { return fxSent; };
  /** Sends the current set again even if unchanged (Java cleared its copy). */
  MC.fxResend = function () { fxSent = "!"; MC.fxSet("draft", fxGroups.draft || []); };
  MC.fxClear = function () {
    for (var g in fxGroups) fxGroups[g] = [];
    MC.fxSet("draft", []);
  };
  /** The window's top-left in page px (the page's own coordinates for #win children are relative to it). */
  MC.win = function () {
    var w = document.getElementById("win");
    return w ? {x: w.offsetLeft, y: w.offsetTop} : {x: 0, y: 0};
  };
  /** An element's untransformed top-left in page px (offset chain: a running transform does not move it). */
  MC.abs = function (el) {
    var x = 0, y = 0;
    while (el && el !== document.body) { x += el.offsetLeft; y += el.offsetTop; el = el.offsetParent; }
    return {x: x, y: y};
  };

  MC.toggle = function (el, cls, on) { if (el && el.classList.contains(cls) !== !!on) el.classList.toggle(cls, !!on); };
  MC.text = function (el, s) { if (el && el.textContent !== s) el.textContent = s; };

  // ---- colour ------------------------------------------------------------------------------------------------
  function rgbOf(h) { h = String(h || "#9A9DA1").replace("#", ""); return [parseInt(h.substr(0, 2), 16), parseInt(h.substr(2, 2), 16), parseInt(h.substr(4, 2), 16)]; }
  MC.rgba = function (h, a) { var c = rgbOf(h); return "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + a + ")"; };
  MC.mix = function (a, b, t) {
    var A = rgbOf(a), B = rgbOf(b), s = "#";
    for (var i = 0; i < 3; i++) { var v = Math.round(A[i] + (B[i] - A[i]) * t); s += (v < 16 ? "0" : "") + v.toString(16); }
    return s;
  };
  MC.RARITY = {common: "#B9BEC6", rare: "#4DA3F5", epic: "#B27CFF", cursed: "#D8453B", mythic: "#F3D28B", earned: "#E8DDBC"};
  MC.CURSED = "#FF2E4A";
  MC.TEMPER = "#8EC5FF";
  MC.AMBER = "#E8A84A";
  /** The icon panel's shade: the skill's colour, dark (Figma "icon panel · skill tint", #3A2C16 for Mining). */
  MC.shade = function (hue) { return MC.mix("#141416", hue || "#9A9DA1", 0.17); };

  // ---- icons -------------------------------------------------------------------------------------------------
  /** A Mettle texture (perk, skill, Mythic icon) at w CSS px, from the data URLs Java sends once. */
  MC.img = function (id, w, cls) {
    var url = MC.icons[id];
    return url ? '<img class="' + (cls || "mc-img") + '" src="' + url + '" style="width:' + w + "px;height:" + w + 'px">' : "";
  };
  var GLYPHS = ["brutal", "deep", "echo", "ember", "gale", "hex", "hoard", "iron", "keen", "kin", "swift", "tide", "warden", "wild"];
  MC.glyphIndex = function (kw) {
    var id = typeof kw === "string" ? kw : kw && kw.id;
    var path = String(id || "").replace(/^.*:/, "").replace(/^.*\//, "");
    return GLYPHS.indexOf(path);
  };
  /** A keyword glyph (8x8 pixel art) at 16 or 24 px. */
  MC.glyph = function (kw, size) {
    var i = MC.glyphIndex(kw);
    if (i < 0) return "";
    size = size || 16;
    return '<i class="gl' + (size === 24 ? " g24" : size === 64 ? " g64" : "") + '" style="background-position:-' + i * size + 'px 0"></i>';
  };
  /** A skill's 16 px icon id, from the last state (set by the pages). */
  MC.skillIcon = function (id) {
    var M = MC.M;
    if (!M || !M.skills) return "";
    for (var i = 0; i < M.skills.length; i++) if (M.skills[i].id === id) return M.skills[i].icon;
    return "";
  };
  MC.gem = function (rarity, size) {
    var c = MC.RARITY[rarity] || MC.RARITY.common;
    return '<i class="gem" style="width:' + size + "px;height:" + size + "px;background:" + c + '"></i>';
  };

  // ---- the pixel font (Figma uses Pixelify Sans Bold for names in hand mode) ----------------------------------
  // 5x7 capitals, digits and the punctuation names use, drawn bold (each texel smeared one to the right). One SVG
  // path per name: no font file, no per-letter elements, crisp edges at every device scale.
  var FONT = {
    A: ".###.|#...#|#...#|#####|#...#|#...#|#...#", B: "####.|#...#|#...#|####.|#...#|#...#|####.",
    C: ".###.|#...#|#....|#....|#....|#...#|.###.", D: "####.|#...#|#...#|#...#|#...#|#...#|####.",
    E: "#####|#....|#....|####.|#....|#....|#####", F: "#####|#....|#....|####.|#....|#....|#....",
    G: ".###.|#...#|#....|#.###|#...#|#...#|.####", H: "#...#|#...#|#...#|#####|#...#|#...#|#...#",
    I: "###|.#.|.#.|.#.|.#.|.#.|###", J: "..###|...#.|...#.|...#.|#..#.|#..#.|.##..",
    K: "#...#|#..#.|#.#..|##...|#.#..|#..#.|#...#", L: "#....|#....|#....|#....|#....|#....|#####",
    M: "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#", N: "#...#|##..#|#.#.#|#..##|#...#|#...#|#...#",
    O: ".###.|#...#|#...#|#...#|#...#|#...#|.###.", P: "####.|#...#|#...#|####.|#....|#....|#....",
    Q: ".###.|#...#|#...#|#...#|#.#.#|#..#.|.##.#", R: "####.|#...#|#...#|####.|#.#..|#..#.|#...#",
    S: ".####|#....|#....|.###.|....#|....#|####.", T: "#####|..#..|..#..|..#..|..#..|..#..|..#..",
    U: "#...#|#...#|#...#|#...#|#...#|#...#|.###.", V: "#...#|#...#|#...#|#...#|#...#|.#.#.|..#..",
    W: "#...#|#...#|#...#|#.#.#|#.#.#|##.##|#...#", X: "#...#|#...#|.#.#.|..#..|.#.#.|#...#|#...#",
    Y: "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..", Z: "#####|....#|...#.|..#..|.#...|#....|#####",
    "0": ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.", "1": "..#..|.##..|..#..|..#..|..#..|..#..|.###.",
    "2": ".###.|#...#|....#|...#.|..#..|.#...|#####", "3": "####.|....#|....#|.###.|....#|....#|####.",
    "4": "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.", "5": "#####|#....|####.|....#|....#|#...#|.###.",
    "6": ".###.|#....|#....|####.|#...#|#...#|.###.", "7": "#####|....#|...#.|..#..|.#...|.#...|.#...",
    "8": ".###.|#...#|#...#|.###.|#...#|#...#|.###.", "9": ".###.|#...#|#...#|.####|....#|....#|.###.",
    "·": ".|.|.|#|.|.|.", "'": "#|#|.|.|.|.|.", "’": "#|#|.|.|.|.|.", "-": "...|...|...|###|...|...|...",
    "&": ".##..|#..#.|.##..|.#...|#.#.#|#..#.|.##.#", ".": ".|.|.|.|.|.|#", ",": ".|.|.|.|.|#|#",
    ":": ".|.|#|.|.|#|.", "!": "#|#|#|#|#|.|#", "?": ".###.|#...#|....#|...#.|..#..|.....|..#..",
    "/": "....#|...#.|...#.|..#..|.#...|.#...|#....", "+": ".....|..#..|..#..|#####|..#..|..#..|.....",
    "×": ".....|#...#|.#.#.|..#..|.#.#.|#...#|.....", " ": "...|...|...|...|...|...|..."
  };
  var bold = {};
  function glyphRows(ch) {
    if (bold[ch]) return bold[ch];
    var src = FONT[ch] || FONT[" "], rows = src.split("|"), out = [];
    for (var r = 0; r < rows.length; r++) {
      var row = rows[r] + ".", b = "";
      for (var x = 0; x < row.length; x++) b += row.charAt(x) === "#" || (x > 0 && row.charAt(x - 1) === "#") ? "#" : ".";
      out.push(b);
    }
    return (bold[ch] = out);
  }
  /** Width in texels of a line of pixel text (1 texel of space between letters). */
  function pixWidth(text) {
    var w = 0;
    for (var i = 0; i < text.length; i++) w += glyphRows(text.charAt(i))[0].length + (i ? 1 : 0);
    return w;
  }
  /**
   * Pixel text as an SVG, t CSS px per texel, centred on its box when wrapped by the caller. Lines that would pass
   * maxW are broken at spaces.
   */
  MC.pix = function (text, t, colour, maxW) {
    text = MC.upper(text).replace(/\s+/g, " ");
    var lines = [text];
    if (maxW && pixWidth(text) * t > maxW) {
      lines = [];
      var words = text.split(" "), cur = "";
      for (var k = 0; k < words.length; k++) {
        var next = cur ? cur + " " + words[k] : words[k];
        if (cur && pixWidth(next) * t > maxW) { lines.push(cur); cur = words[k]; } else cur = next;
      }
      if (cur) lines.push(cur);
    }
    var W = 0, d = "";
    for (var l = 0; l < lines.length; l++) W = Math.max(W, pixWidth(lines[l]));
    for (var li = 0; li < lines.length; li++) {
      var line = lines[li], x0 = Math.floor((W - pixWidth(line)) / 2), y0 = li * 9;
      for (var i = 0; i < line.length; i++) {
        var g = glyphRows(line.charAt(i));
        for (var y = 0; y < g.length; y++) {
          var row = g[y], run = -1;
          for (var x = 0; x <= row.length; x++) {
            var on = x < row.length && row.charAt(x) === "#";
            if (on && run < 0) run = x;
            if (!on && run >= 0) { d += "M" + (x0 + run) + " " + (y0 + y) + "h" + (x - run) + "v1h-" + (x - run) + "z"; run = -1; }
          }
        }
        x0 += g[0].length + 1;
      }
    }
    var H = lines.length * 9 - 2;
    return '<svg class="pix" width="' + W * t + '" height="' + H * t + '" viewBox="0 0 ' + W + " " + H + '" shape-rendering="crispEdges"><path fill="' + colour + '" d="' + d + '"/></svg>';
  };

  // ---- textures by device scale ---------------------------------------------------------------------------------
  // Every looping strip (edges, embers, hovers, the Mythic's aura, the set moment) is drawn natively by CardFx from
  // the same _px files; the page keeps only the burn mask (a one-shot) and the glyph sheets, at the file whose
  // scale is at least the device scale so Ultralight only ever shrinks them.
  var cssScale = 0;

  /** The texture rules for this device scale (1, 2 or 3): one stylesheet, replaced when the scale changes. */
  MC.css = function (deviceScale) {
    var sx = deviceScale <= 1 ? 1 : deviceScale <= 2 ? 2 : 3;
    if (sx === cssScale) return;
    cssScale = sx;
    var f = "_x" + sx + ".png", r = "";
    r += ".burnmask{-webkit-mask-image:url(cards/hand/burn_mask" + f + ")}";
    r += ".gl{background-image:url(cards/icons/glyphs" + f + ")}";
    r += ".gl.g24{background-image:url(cards/icons/glyphs_x" + Math.min(3, Math.max(2, sx + 1)) + ".png)}";
    r += ".gl.g64{background-image:url(cards/icons/glyphs_x5.png)}";
    var st = document.getElementById("mc-scale");
    if (!st) {
      st = document.createElement("style");
      st.id = "mc-scale";
      document.head.appendChild(st);
    }
    st.textContent = r;
  };

  // ---- cards ------------------------------------------------------------------------------------------------
  MC.rarityOf = function (c) { return c.cursed ? "cursed" : c.rarity || "common"; };
  MC.edgeOf = function (c) {
    if (c.rarity === "mythic") return "mythic";
    if (c.cursed || c.rarity === "cursed") return "cursed";
    if (c.wildcard) return "wildcard";
    return c.rarity === "epic" || c.rarity === "rare" ? c.rarity : "common";
  };
  MC.isMythic = function (c) { return c && (c.rarity === "mythic" || c.kind === "mythic"); };

  function traitDots(n, colour) {
    if (!n) return "";
    var s = '<div class="cc-d" style="width:' + (n * 10 - 4) + 'px">';
    for (var i = 0; i < n; i++) s += '<i style="left:' + i * 10 + "px;background:" + colour + '"></i>';
    return s + "</div>";
  }

  function glyphRow(kws, size, gap) {
    var s = "", n = 0;
    for (var i = 0; i < (kws || []).length; i++) if (MC.glyphIndex(kws[i]) >= 0) { s += MC.glyph(kws[i], size); n++; }
    return {html: s, w: n * size + Math.max(0, n - 1) * gap};
  }

  /** "Mining 13" chip text: a requirement short enough for a 96 px card. */
  MC.shortNeed = function (c) {
    var name = c.skillName || "";
    if (name.length > 8) name = name.slice(0, 5) + ".";
    return name + " " + c.needs;
  };

  /**
   * Collection mode (Figma "mettle / card v2 / collection 96x128"): still at rest, a calm border in the rarity colour;
   * hovered, it lifts 4 px and a 2-frame edge flicker in the skill colour runs (only while hovered: CSS starts it).
   */
  MC.ccard = function (c, opt) {
    opt = opt || {};
    var mythic = MC.isMythic(c), rar = mythic ? "mythic" : MC.rarityOf(c), rc = MC.RARITY[rar] || MC.RARITY.common;
    var hue = mythic ? MC.RARITY.mythic : c.colour || "#9A9DA1";
    var hov = mythic ? "" : "hover-" + (c.cursed ? "cursed" : c.wildcard ? "wildcard" : rar);
    var long = String(c.baseName || c.name || "").length > 13;
    var bare = !(c.keywords || []).length && !(c.traits || []).length;
    var h = '<div class="cc' + (long ? " long" : "") + (bare ? " bare" : "") + (opt.sel ? " sel" : "") + (opt.cls ? " " + opt.cls : "") + '"' + (hov ? ' data-fxhover="' + hov + '"' : "") +
      ' data-fxtint="' + (c.cursed ? MC.CURSED : hue) + '"' + (opt.attrs || "") + ' style="--c:' + (c.cursed ? MC.CURSED : hue) + (opt.style ? ";" + opt.style : "") + '">';
    if (opt.sel) h += '<div class="cc-sel"></div>';
    h += '<div class="cc-b' + (mythic ? " my" : "") + '" style="border-color:' + MC.rgba(rc, 0.7) + '"></div>';
    h += '<div class="cc-p" style="background:' + (mythic ? "#2a2236" : MC.shade(hue)) + '">' + MC.img(c.icon, 48) + "</div>";
    h += '<div class="cc-gem" style="background:' + rc + '"></div>';
    h += '<div class="cc-n">' + esc(c.baseName || c.name) + "</div>";
    var g = glyphRow(c.keywords, 16, 6);
    if (g.html) h += '<div class="cc-k" style="width:' + g.w + "px;left:" + (48 - g.w / 2) + 'px">' + g.html + "</div>";
    h += traitDots((c.traits || []).length, rc);
    if (!mythic && c.place !== "slotted" && c.attuned === false && c.attuningMinutes > 0) {
      h += '<div class="cc-dim"></div><div class="cc-chip att">' + c.attuningMinutes + " min</div>";
    } else if (!mythic && c.meetsRequirement === false) {
      h += '<div class="cc-chip amber"><i class="lock"></i>' + esc(MC.shortNeed(c)) + "</div>";
    } else if (c.dormant) {
      h += '<div class="cc-dim"></div><div class="cc-chip">Dormant</div>';
    }
    if (opt.check) h += '<div class="cc-check"></div>';
    return h + "</div>";
  };

  /** A trait line's numbers: the rolled value, and the boosted one in Temper blue when attributes raise it. */
  function traitLine(t) {
    var boosted = t.boostedText && t.boostedText !== t.valueText;
    return {name: t.name || "", value: t.valueText || "", boost: boosted ? "→ " + t.boostedText : ""};
  }
  MC.traitLine = traitLine;

  /**
   * Hand mode (Figma "mettle / card v2 / hand · clean 248x328"): the obsidian face plus sibling layers for the burning
   * edge, the hot frame and embers (strips tinted with the card's skill colour through a mask). Text keeps the 28 px
   * safe area; the burn never passes 12 px.
   */
  MC.hcard = function (c, i, skill, opt) {
    opt = opt || {};
    var mythic = MC.isMythic(c);
    if (mythic) return MC.mythicCard(c, i);
    var edge = MC.edgeOf(c), rar = MC.rarityOf(c), rc = c.cursed ? MC.CURSED : MC.RARITY[rar] || MC.RARITY.common;
    var hue = c.colour || "#9A9DA1", tint = c.cursed ? MC.CURSED : hue;
    var dense = edge === "epic" || edge === "cursed";
    var h = '<div class="hc' + (opt.cls ? " " + opt.cls : "") + '" data-hand="' + i + '" style="--c:' + tint + (opt.style ? ";" + opt.style : "") + '">';
    h += '<div class="hc-face">';
    var eyebrow = c.wildcard ? "WILDCARD  ·  " + MC.upper(c.skillName) : MC.upper(c.rarityName || rar) + (c.tierName ? "  ·  " + c.tierName : "");
    h += '<div class="hc-gem" style="background:' + rc + '"></div><div class="hc-eb" style="color:' + (c.wildcard ? hue : rc) + '">' + esc(eyebrow) + "</div>";
    h += '<div class="hc-sig" style="border-color:' + MC.rgba(hue, 0.6) + '">' + MC.img(MC.skillIcon(c.skill), 16) + "</div>";
    h += '<div class="hc-art" style="background:radial-gradient(closest-side,' + MC.rgba(hue, 0.28) + " 0%," + MC.rgba(hue, 0) + " 100%)," + MC.shade(hue) + '">' + MC.img(c.icon, 64) + "</div>";
    // Name, rules, keywords, the price or a divider, traits: one column in the 28 px safe area, so a long name or
    // rule pushes the rest down instead of overlapping it.
    var sparse = !(c.traits || []).length && !(c.cursed && c.downside);
    h += '<div class="hc-flow' + (sparse ? " sparse" : "") + '"><div class="hc-name">' + MC.pix(c.name || "", 2, "#F4EEE6", 192) + "</div>";
    h += '<div class="hc-desc">' + esc(c.description) + "</div>";
    if (c.keywords && c.keywords.length) {
      h += '<div class="hc-kws">';
      for (var k = 0; k < c.keywords.length; k++) h += '<span class="hc-kw">' + MC.glyph(c.keywords[k], 16) + esc(c.keywords[k].name) + "</span>";
      h += "</div>";
    }
    if (c.cursed && c.downside) {
      h += '<div class="hc-price"><div class="hc-pl">THE PRICE</div><div class="hc-pt">' + esc(c.downside) + "</div></div>";
    } else if ((c.traits || []).length) {
      h += '<div class="hc-div"></div>';
    }
    var ts = c.traits || [];
    for (var t = 0; t < ts.length && t < 3; t++) {
      var L = traitLine(ts[t]);
      h += '<div class="hc-tr"><span class="n">' + esc(L.name) + '</span><span class="v">' + esc(L.value) + '</span><span class="b">' + esc(L.boost) + "</span></div>";
    }
    h += "</div>";
    if (c.slotsAt) h += '<div class="hc-need"><i class="lock"></i>' + esc(c.slotsAt) + "</div>";
    h += "</div>";
    // The burning edge, embers, the hot frame and the lift are CardFx layers (cards-draft.js draftFx), not markup.
    return h + "</div>";
  };

  /** A Mythic in hand mode (Figma "mettle / draft / mythic card"): white-gold, never tinted, never flickers. */
  MC.mythicCard = function (c, i, holder) {
    var h = '<div class="hc my" data-hand="' + (i == null ? "" : i) + '">';
    h += '<div class="hc-face my-face">';
    h += '<div class="my-head"><i class="star" style="left:38px"></i>' + '<div class="my-hl">' + MC.pix("MYTHIC", 2, "#F3D28B") + '</div><i class="star" style="left:204px"></i></div>';
    h += '<div class="my-art"><div class="my-halo"></div>' + MC.img(c.icon, 80) + "</div>";
    h += '<div class="my-flow"><div class="hc-name">' + MC.pix(c.name || "", 2, "#FFF1C9", 192) + "</div>";
    h += '<div class="my-eff">' + esc(c.effect || c.description || "") + "</div>";
    if (c.lore) h += '<div class="my-lore">“' + esc(String(c.lore).replace(/^“|”$/g, "")) + "”</div>";
    h += "</div>";
    h += '<div class="my-dots"></div>';
    h += '<div class="my-prov">' + esc(MC.upper(c.how || c.deed || "")) + "</div>";
    if (holder) h += '<div class="my-prov2">' + esc(MC.upper(holder)) + "</div>";
    h += "</div>";
    return h + "</div>";
  };

  /** Row mode (Figma "mettle / card v2 / row 52h"): shrine, trade offers, release and fuse pickers. */
  MC.row = function (c, opt) {
    opt = opt || {};
    var mythic = MC.isMythic(c), rar = mythic ? "mythic" : MC.rarityOf(c), rc = MC.RARITY[rar] || MC.RARITY.common;
    var hue = mythic ? MC.RARITY.mythic : c.colour || "#9A9DA1", x = opt.check === undefined ? 16 : 44;
    var h = '<div class="rw' + (opt.sel ? " sel" : "") + (opt.cls ? " " + opt.cls : "") + '"' + (opt.attrs || "") + (opt.style ? ' style="' + opt.style + '"' : "") + ">";
    h += '<div class="rw-b" style="' + (opt.sel ? "" : "border-color:" + MC.rgba(rc, 0.35)) + '"></div>';
    if (opt.check !== undefined) h += '<div class="rw-ck' + (opt.check ? " on" : "") + '"></div>';
    h += '<div class="rw-i" style="left:' + x + "px;background:" + (mythic ? "#2a2236" : MC.shade(hue)) + '">' + MC.img(c.icon, 32) + "</div>";
    h += '<div class="rw-gem" style="left:' + (x + 33) + "px;background:" + rc + '"></div>';
    var kwn = [];
    for (var k = 0; k < (c.keywords || []).length; k++) kwn.push(c.keywords[k].name);
    var sub = mythic ? "Mythic" : (c.rarityName || rar) + " · " + (c.tierName || "I") + (kwn.length ? " · " + kwn.join(", ") : "") + (c.scars ? " · scar ×" + c.scars : "");
    var gx = opt.glyphX || (x + 202), tw = "max-width:" + (gx - x - 60) + "px";
    h += '<div class="rw-n" style="left:' + (x + 52) + "px;" + tw + '">' + esc(c.baseName || c.name) + '</div><div class="rw-s" style="left:' + (x + 52) + "px;" + tw + '">' + esc(opt.sub || sub) + "</div>";
    var g = glyphRow(c.keywords, 16, 4);
    if (g.html) h += '<div class="rw-k" style="left:' + gx + 'px">' + g.html + "</div>";
    var tx = opt.traitX || (gx + 50), ts = c.traits || [], shown = 0;
    h += '<div class="rw-ts" style="left:' + tx + "px;max-width:" + (opt.traitW || 330) + 'px">';
    for (var t = 0; t < ts.length && shown < (opt.maxTraits || 3); t++, shown++) {
      var best = opt.best === ts[t].id;
      h += '<span class="rw-t' + (best ? " best" : "") + '">' + esc((ts[t].name || "") + " " + (ts[t].valueText || "")) + "</span>";
    }
    if (ts.length > shown) h += '<span class="rw-more">+' + (ts.length - shown) + "</span>";
    if (!ts.length && !mythic) h += '<span class="rw-none">no traits</span>';
    h += "</div>";
    if (opt.right) h += opt.right;
    return h + "</div>";
  };

  /** Right-hand value columns of a row: [{x (right edge), text, sub, colour}]. */
  MC.cols = function (list) {
    var h = "";
    for (var i = 0; i < list.length; i++) {
      var c = list[i];
      h += '<div class="rw-c" style="right:' + c.r + "px" + (c.sub ? "" : ";top:17px") + (c.colour ? ";color:" + c.colour : "") + '"><div class="v">' + esc(c.text) + "</div>" + (c.sub ? '<div class="s">' + esc(c.sub) + "</div>" : "") + "</div>";
    }
    return h;
  };

  /** A small Mythic for the Hall of Firsts shelf (×0.42 of the hand card), static: no strips run on a shelf. */
  MC.miniMythic = function (m, mine) {
    if (!m.claimed && !mine) {
      return '<div class="mm unclaimed"><div class="mm-q">' + MC.pix("?", 4, "#5A5560") + "</div></div>";
    }
    return '<div class="mm' + (mine ? " mine" : "") + '"><div class="mm-glow"></div><div class="mm-face"><div class="mm-h">' + MC.pix("MYTHIC", 1, "#F3D28B") +
      '</div><div class="mm-art">' + MC.img(m.icon, 40) + '</div><div class="mm-n">' + MC.pix(m.name, 1, "#FFF1C9", 80) + "</div></div></div>";
  };
})();
