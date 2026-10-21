// VOID Mettle 0.4.0 in the hub: talents and deed titles (mettle/docs/BUILD_FEEL.md §12-13, OVERLAY.md "Talents";
// Figma 3c0Wgc4ZKFSpm98fWJoARS page 295:2, section 2i): the talent card face with its ash edge (2i-16), the talent row on
// the character sheet (2i-1), the talent draft hand (2i-17), "Reconsider your talents" after a night's sleep (2i-18) and
// the Hall of Firsts' deeds, name card and title picker (2i-23). Stealth (2i-19, -20) needs no page of its own: it is a
// skill like the others (the gallery and the rail make room for it in cards-pages.js and mettle.js).
//
// Same rules as the other card screens: parts patched by key, nothing loops in CSS (the hand's burning ash edges, embers
// and lift are native CardFx layers), every string escaped, both panels modal for Esc. Mettle decides everything; the
// page reads `talents` and `cards.build.titles` and sends keepTalent / rerollTalents / slotTalent / unslotTalent / wearTitle.
(function () {
  "use strict";

  var MC = window.MC, esc = MC.esc, upper = MC.upper, part = MC.part, patch = MC.patch;
  var Y = function (y) { return y + 24; };
  var ASH = "#A39E96", CHAMP = "#E8DDBC", TEMPER = MC.TEMPER, AMBER = MC.AMBER, IVORY = "#EDE3C8";
  var tl = MC.tl = {};
  function P() { return MC.pg; }
  function M() { return MC.M; }
  function T() { return M() && M().talents ? M().talents : null; }
  function B() { return M() && M().cards ? M().cards.build : null; }

  // ---- the family glyphs (7 x 7 pixel art, stand-ins until Mettle ships talent icons) -------------------------------
  var FAM = {
    body: [".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#..."],
    motion: ["#..#...", "##.##..", ".##.##.", "..##.##", ".##.##.", "##.##..", "#..#..."],
    hands: [".#.#.#.", ".#.#.#.", ".#####.", "######.", ".#####.", ".#####.", "..###.."],
    mind: [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],
    fortune: ["...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."],
    none: ["...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."]
  };
  /** A family's glyph as crisp pixel art, t CSS px per texel. */
  tl.fam = function (family, t, colour) {
    var rows = FAM[family] || FAM.none, d = "";
    for (var y = 0; y < rows.length; y++) {
      var row = rows[y], run = -1;
      for (var x = 0; x <= row.length; x++) {
        var on = x < row.length && row.charAt(x) === "#";
        if (on && run < 0) run = x;
        if (!on && run >= 0) { d += "M" + run + " " + y + "h" + (x - run) + "v1h-" + (x - run) + "z"; run = -1; }
      }
    }
    return '<svg class="tfam" width="' + 7 * t + '" height="' + 7 * t + '" viewBox="0 0 7 7" shape-rendering="crispEdges"><path fill="' + (colour || "#EDEEEF") + '" d="' + d + '"/></svg>';
  };
  function rc(c) { return MC.RARITY[c && c.rarity] || MC.RARITY.common; }
  /** An opaque border colour (translucent rounded borders are slow in Ultralight's CPU raster). */
  function edge(colour, a) { return MC.mix("#161618", colour, a || 0.7); }
  function valueLine(c) { return c.valueText ? c.valueText + (c.familyName ? " · " + c.familyName : "") : c.familyName || ""; }

  // ---- tiles: the row, the binder, the reconsider panel (Figma 2i-16 "chip and row") --------------------------------
  /**
   * A 104 x 64 talent tile: family glyph, rarity gem, name, value in Temper blue (what it gives you). opt: {attr, sel,
   * drop, cls}. Earned talents (the general feats) burn champagne.
   */
  function tile(c, opt) {
    opt = opt || {};
    var col = rc(c);
    return '<div class="tt' + (opt.sel ? " sel" : "") + (opt.drop ? " drop" : "") + (opt.cls ? " " + opt.cls : "") + '"' + (opt.attr || "") + ' title="' + esc(c.name + " · " + (c.description || "")) +
      '" style="border-color:' + (opt.sel ? "#EDEEEF" : edge(col)) + '">' + tl.fam(c.family, 2, "#D9D4CA") + '<i class="tg" style="background:' + col + '"></i>' +
      '<div class="n">' + esc(c.name) + '</div><div class="v">' + esc(valueLine(c)) + "</div></div>";
  }
  function earnedTile(e) {
    var art = MC.img(e.icon, 16) || tl.fam("body", 2, CHAMP);
    return '<div class="tt earned" title="' + esc(e.name + " · " + (e.description || "")) + '"><span class="ic">' + art + '</span><span class="eb">EARNED</span><div class="n">' + esc(e.name) +
      '</div><div class="v">' + esc(e.description || "") + "</div></div>";
  }
  function emptyTile(slot, opt) {
    opt = opt || {};
    return '<div class="tt empty' + (opt.drop ? " drop" : "") + '"' + (opt.attr || "") + '><div class="n">' + esc(opt.text || "Empty slot " + (slot + 1)) + '</div><div class="v">' + esc(opt.sub || "") + "</div></div>";
  }
  function lockedTile(t) {
    return '<div class="tt locked"><i class="lock"></i><div class="n">' + esc(t.nextSlotAt ? "Opens at character level " + t.nextSlotAt : "Locked") + "</div></div>";
  }
  function heldEarned(t) { return (t.earned || []).filter(function (e) { return e.held; }); }

  // ---- the character sheet's talent row (Figma 2i-1, bottom) ---------------------------------------------------------
  /** What the row shows; excludes the waking window's seconds (they tick every second and would repaint the sheet). */
  tl.rowKey = function () {
    var t = T();
    if (!t) return "none";
    return JSON.stringify([t.row, t.binder.length, t.slots, t.slotsText, t.nextDraftAt, t.nextSlotAt, t.unlocked, t.draft.queued, t.draft.hand.length, t.earned,
      t.rearrange.way, t.rearrange.awake, P().iconsKey()]);
  };
  tl.row = function () {
    var t = T();
    if (!t) return "";
    var h = '<div class="eb9" style="left:16px;top:16px">TALENTS</div>';
    var open = t.row.filter(function (r) { return r.open; }), held = heldEarned(t);
    h += '<div class="tl-n">' + esc(t.unlocked ? t.slotsText.replace(/ talent slots$/, " slots") : "Not yet") + "</div>";
    var drafts = t.draft.queued > 0 && t.draft.hand.length;
    if (drafts) h += '<div class="tl-b draft" data-tldraft="1"><b>+</b>' + t.draft.queued + (t.draft.queued === 1 ? " talent draft" : " talent drafts") + "</div>";
    else if (t.rearrange.way === "wake") h += '<div class="tl-b wake" data-tlrow="1"><i class="moon"></i>Reconsider</div>';
    else h += '<div class="tl-nx">' + esc(t.unlocked ? "Next draft at level " + t.nextDraftAt : "Talents open at level " + t.nextDraftAt) + "</div>";
    // Open slots, the next locked one, then the earned talents; seven tiles fit.
    var tiles = [];
    for (var i = 0; i < open.length; i++) {
      var r = open[i];
      tiles.push(r.card ? tile(r.card, {attr: ' data-tlrow="' + r.slot + '"'}) : emptyTile(r.slot, {attr: ' data-tlrow="' + r.slot + '"', sub: t.binder.length ? "from your binder" : "from a draft"}));
    }
    if (open.length < t.row.length) tiles.push(lockedTile(t));
    var room = 7 - tiles.length;
    for (var e = 0; e < held.length; e++) {
      if (e === room - 1 && held.length > room) { tiles.push('<div class="tt more earned"><div class="n">+' + (held.length - e) + ' earned</div><div class="v">always on</div></div>'); break; }
      tiles.push(earnedTile(held[e]));
    }
    for (var k = 0; k < tiles.length && k < 7; k++) h += '<div class="tl-at" style="left:' + (150 + k * 108) + 'px">' + tiles[k] + "</div>";
    return h;
  };

  // ---- the talent card face in hand mode (Figma 2i-16 top, 2i-17) -----------------------------------------------------
  /** A talent as a 248 x 328 hand card: ash edge, the family where the skill sigil sits, no keyword row. */
  function hand(c, i, left, top) {
    var col = rc(c), ash = c.edge || ASH;
    var h = '<div class="hc tlc deal" data-hand="' + i + '" style="--c:' + ash + ";left:" + left + "px;top:" + top + "px;animation-delay:" + i * 80 + 'ms">';
    h += '<div class="hc-face">';
    h += '<div class="hc-gem" style="background:' + col + '"></div><div class="hc-eb" style="color:' + col + '">' + esc(upper(c.rarity) + "  ·  " + upper(c.familyName || "")) + "</div>";
    h += '<div class="hc-sig" style="border-color:' + edge(ash, 0.75) + '"><span class="tl-sig">' + tl.fam(c.family, 2, "#EDEEEF") + "</span></div>";
    h += '<div class="hc-art" style="background:' + MC.shade(ash) + '"><span class="tl-big">' + tl.fam(c.family, 6, "#E9E4DA") + "</span></div>";
    h += '<div class="hc-flow"><div class="hc-name">' + MC.pix(c.name || "", 2, "#F4EEE6", 192) + '</div><div class="hc-desc">' + esc(c.description || "") + "</div></div>";
    h += '<div class="hc-bot"><div class="hc-div"></div>';
    if (c.valueText) h += '<div class="hc-tr"><span class="n">This copy</span><span class="v">' + esc(c.valueText) + '</span><span class="b">' + esc(c.range ? "of " + c.range : "") + "</span></div>";
    else h += '<div class="hc-tr"><span class="n">Fixed</span><span class="v">no roll</span><span class="b"></span></div>';
    h += "</div></div>";
    return h + "</div>";
  }
  tl.hand = hand;

  // ---- the talent draft (Figma 2i-17) -------------------------------------------------------------------------------
  // The skill draft's layout (cards-draft.js, "pv"): title, three hand cards, a preview box under each, Keep row, Redeal.
  var TOP = 176, PV_TOP = 508, gen = 0, dr = null;
  function leftOf(i, n) { return Math.round((493.5 - (n * 248 + (n - 1) * 30) / 2 + i * 278) / 4) * 4; }
  function handKey() { var t = T(); return t ? JSON.stringify(t.draft.hand) : ""; }
  function burnDelay() { return (M().draft && M().draft.sound && M().draft.sound.burnAfterPickMs) || 260; }
  function firstFree(t) { for (var i = 0; i < t.row.length; i++) if (t.row[i].open && !t.row[i].card) return t.row[i].slot; return -1; }
  function goesLines(c, t) {
    if (c.goes === "slot") {
      var f = firstFree(t);
      return '<div class="pl"><b>Fills talent slot ' + (f + 1) + "</b> · free, always on</div>" + rolled(c);
    }
    if (c.goes === "upgrade") {
      return '<div class="pl"><b>Upgrades your copy</b> · it takes its slot</div><div class="pl">' + esc(c.name) + " " + esc(c.replacesValue || "") + ' → <b class="op">' + esc(c.valueText) + "</b>" + (c.range ? " of " + esc(c.range) : "") + "</div>";
    }
    return '<div class="pl amber"><b>Goes to your talent binder</b></div><div class="pl">' + esc(t.slots ? "Your row is full: slot it after a night's sleep" : "No talent slot is open yet") + "</div>";
  }
  /** "Rolled 16 % of 10 % – 25 %": where this copy's roll sits in its band (fixed talents have none). */
  function rolled(c) {
    return c.valueText ? '<div class="pl">Rolled <b class="op">' + esc(c.valueText) + "</b>" + (c.range ? " of " + esc(c.range) : "") + (c.familyName ? " · " + esc(c.familyName) + " talent" : "") + "</div>"
      : '<div class="pl">A fixed talent · no roll' + (c.familyName ? " · " + esc(c.familyName) : "") + "</div>";
  }
  function keepText(c) { return c.goes === "slot" ? "Keep & slot" : c.goes === "upgrade" ? "Keep · upgrade" : "Keep · to binder"; }

  /** The hand's native layers: the ash edges and embers, the hovered card's lift and hot frame, the pick's flare and burn. */
  function draftFx() {
    if (!dr) return;
    var w = MC.win(), items = [];
    for (var k = 0; k < dr.n; k++) {
      var c = dr.hand[k], x = w.x + dr.pos[k], y = w.y + TOP, H = 328, id = "t" + dr.gen + "c" + k, tint = c.edge || ASH;
      if (dr.burnt && dr.burnt[k]) {
        items.push({i: id + "-bf", k: "bfront", x: x, y: y, c: tint, d: burnDelay()});
        items.push({i: id + "-ash", k: "ash", x: x, y: y, d: burnDelay()});
        continue;
      }
      var hov = dr.picked < 0 && dr.hover === k, kept = dr.picked === k, cool = dr.picked < 0 && dr.hover >= 0 && !hov;
      if (hov || kept) {
        items.push({i: id + "-b", k: "bloom", x: x + 124, y: y + H / 2, w: 348, h: H + 80, c: tint, a: kept ? 0.5 : 0.3, q: 120});
        items.push({i: id, k: "lift", x: x, y: y, w: 248, h: H, p: kept ? -8 : -16, q: kept ? 1.04 : 1.05});
      }
      if (cool) items.push({i: id + "-cool", k: "rect", x: x - 1, y: y - 1, w: 250, h: H + 2, c: "#0B0A0B", a: 0.6, q: 120});
      var ed = c.rarity === "epic" || c.rarity === "rare" ? c.rarity : "common", d = dr.dealing ? k * 80 + 240 : 0;
      items.push({i: id + "-e", k: "edge-" + ed, x: x, y: y, c: tint, a: cool ? 0.4 : 1, d: d});
      items.push({i: id + "-m" + (hov ? "d" : "s"), k: hov || ed === "epic" ? "embers-dense" : "embers-sparse", x: x, y: y, c: tint, a: cool ? 0.4 : 1, d: d});
      if (hov) items.push({i: id + "-h", k: "edge-hot", x: x, y: y, c: tint});
      if (kept) items.push({i: id + "-f", k: "flare", x: x, y: y, c: tint});
    }
    MC.ov.panelFx(items);
  }

  /** Deals the waiting talent hand (the sheet's "+ n talent drafts"). */
  tl.openDraft = function () {
    var t = T();
    if (!t || !t.draft.queued || !t.draft.hand.length) return false;
    if (MC.ov.active() && MC.ov.kind() !== "talent") return false;
    var first = !dr;
    dr = {n: Math.min(3, t.draft.hand.length), hand: t.draft.hand.slice(0, 3), key: handKey(), pos: {}, hover: -1, picked: -1, burnt: null, gen: ++gen, dealing: true,
      lock: true, done: first ? 0 : dr.done, total: first ? t.draft.queued : dr.total};
    var h = '<div class="ov-dim"></div><div id="dr-chip"><div class="hd-chip ov-chip tl-chip">' + tl.fam("none", 2, ASH) + '<span class="nm">Talent draft</span><span class="lv">' +
      (dr.done + 1) + " of " + Math.max(dr.done + 1, dr.total) + "</span></div></div>";
    h += '<div id="dr-badge">' + (t.draft.queued > 1 ? '<div class="ov-badge tl-badge"><i class="backs"></i><span>' + (t.draft.queued - 1) + (t.draft.queued === 2 ? " more talent draft" : " more talent drafts") + "</span></div>" : "") + "</div>";
    h += '<div id="dr-title"><div class="ov-eb" style="color:#C9C3B8">' + esc("TALENT DRAFT  ·  CHARACTER LEVEL " + t.characterLevel + "  ·  " + (dr.n > 1 ? "KEEP ONE, THE OTHERS BURN" : "KEEP IT")) +
      '</div><div class="ov-title">' + MC.pix("WHAT KIND OF PERSON ARE YOU?", 3, "#F4EEE6", 900) + "</div></div>";
    var cards = "", pv = "", keys = "";
    for (var k = 0; k < dr.n; k++) {
      var c = dr.hand[k];
      dr.pos[k] = leftOf(k, dr.n);
      cards += hand(c, k, dr.pos[k], TOP);
      pv += '<div class="dr-pvb" data-pv="' + k + '" style="left:' + dr.pos[k] + "px;top:" + PV_TOP + 'px">' + goesLines(c, t) + "</div>";
      keys += '<div class="dr-key pv" data-key="' + k + '" style="left:' + (dr.pos[k] + 124 - 100) + 'px"><span class="kc">' + (k + 1) + '</span><span class="kl">Keep</span><span class="kp" data-tkeep="' + k + '">' +
        esc(keepText(c)) + "</span></div>";
    }
    h += '<div class="dr-hand" id="dr-hand">' + cards + '</div><div id="dr-pv">' + pv + '</div><div id="dr-keys">' + keys + '</div><div id="dr-act">' + redealHtml() + "</div>";
    h += '<div class="footer ov-fl" id="dr-fl"></div><div class="footer right ov-fr" id="dr-fr"></div>';
    MC.ov.panel("talent", {html: h, cls: "ov-draft pv tl-draft", onClick: draftClick, onKey: draftKey, onHover: draftHover, onState: draftState, onClose: function () { dr = null; }});
    var hc = MC.$("dr-hand").children;
    for (var j = 0; j < hc.length; j++) MC.layoutCard(hc[j]);
    for (var b = MC.$("dr-pv").firstChild; b; b = b.nextSibling) if (b.scrollHeight > 61) MC.toggle(b, "one", true);
    var rd = MC.$("dr-act").firstChild;
    if (rd) rd.style.left = Math.round(493.5 - rd.offsetWidth / 2) + "px";
    MC.text(MC.$("dr-fl"), "TALENTS  ·  " + upper(t.slotsText) + "  ·  " + t.draft.queued + (t.draft.queued === 1 ? " DRAFT WAITING" : " DRAFTS WAITING"));
    MC.text(MC.$("dr-fr"), "PRESS 1–3  ·  REDEALS ARE SHARED");
    draftFx();
    var g = dr.gen;
    MC.ov.later(80 * 2 + 260, function () { if (dr && dr.gen === g) { dr.lock = false; dr.dealing = false; draftFx(); } });
    return true;
  };
  function redealHtml() {
    var d = M().draft || {}, n = d.rerolls || 0, max = d.maxRerolls || 5, pips = "";
    for (var i = 0; i < max; i++) pips += '<i class="' + (i < n ? "on" : "") + '"></i>';
    return '<div class="dr-redeal' + (n ? "" : " off") + '" data-tredeal="1"><svg width="14" height="14" viewBox="0 0 14 14"><path d="M11.6 5.2A5 5 0 1 0 12 8" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round"/><path d="M12.2 1.8v3.6H8.6" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '<span>Redeal all</span><span class="pips">' + pips + "</span></div>";
  }
  function setHover(i) {
    if (!dr || dr.picked >= 0 || i === dr.hover) return;
    dr.hover = i;
    dr.dealing = false;
    var keys = MC.$("dr-keys");
    for (var el = keys ? keys.firstChild : null; el; el = el.nextSibling) {
      var on = +el.getAttribute("data-key") === i;
      for (var q = 0; q < 2; q++) { if (on) el.children[q].style.setProperty("--c", "#E9E4DA"); MC.toggle(el.children[q], "on", on); }
    }
    draftFx();
  }
  function draftHover(target) {
    if (!dr || dr.picked >= 0) return;
    var hc = target && target.closest ? target.closest(".dr-hand .hc") : null;
    setHover(hc ? +hc.getAttribute("data-hand") : -1);
  }
  function pick(i) {
    if (!dr || dr.lock || dr.picked >= 0 || !dr.hand[i]) return;
    var c = dr.hand[i], t = T();
    dr.picked = i;
    dr.lock = true;
    dr.hover = -1;
    MC.act("keepTalent", String(i));
    dr.burnt = {};
    var hc = MC.$("dr-hand").children;
    for (var k = 0; k < hc.length; k++) {
      var idx = +hc[k].getAttribute("data-hand");
      MC.toggle(hc[k], "deal", false);
      if (idx === i) continue;
      dr.burnt[idx] = true;
      var face = hc[k].querySelector(".hc-face");
      if (face) { face.style.animationDelay = burnDelay() / 1000 + "s"; face.classList.add("burnmask"); }
    }
    draftFx();
    var more = t.draft.queued - 1;
    MC.$("dr-title").innerHTML = '<div class="ov-eb" style="color:#C9C3B8">KEPT  ·  ' + (dr.n > 1 ? "THE OTHERS BURN" : "NOTHING ELSE BURNS") + '</div><div class="ov-title">' + MC.pix(c.name + (c.goes === "binder" ? " GOES TO YOUR BINDER" : " JOINS YOUR TALENTS"), 3, "#F4EEE6", 900) + "</div>";
    MC.$("dr-keys").innerHTML = "";
    MC.$("dr-pv").innerHTML = "";
    MC.$("dr-act").innerHTML = '<div class="dr-toast" style="--c:' + ASH + '">Kept ' + esc(c.name) + "  ·  " + (more > 0 ? "the next talent draft deals in a moment" : "back to your sheet") + "</div>";
    MC.text(MC.$("dr-fr"), "THE NEXT DEAL TAKES INPUT AGAIN");
    dr.done++;
    var g = dr.gen;
    MC.ov.later(1250, function () { if (dr && dr.gen === g) { dr.ready = true; nextDeal(); } });
    MC.ov.later(4000, function () { if (dr && dr.gen === g) MC.ov.close(true); });
  }
  function nextDeal() {
    var t = T();
    if (!dr || !t) return;
    if (t.draft.queued > 0 && t.draft.hand.length && handKey() !== dr.key) { tl.openDraft(); return; }
    if (!t.draft.queued) MC.ov.close(true);
    // else: the server's answer is on its way (draftState deals it)
  }
  function reroll() {
    if (!dr || dr.lock || dr.picked >= 0) return;
    var d = M().draft || {};
    if (!d.rerolls) return;
    MC.act("rerollTalents", "");
    dr.lock = true;
    dr.rerolling = true;
    MC.toggle(MC.$("dr-hand"), "sweep", true);
    MC.ov.panelFx([]);
    var g = dr.gen;
    MC.ov.later(3000, function () { if (dr && dr.gen === g && dr.rerolling) { dr.rerolling = false; tl.openDraft(); } });
  }
  function draftState() {
    if (!dr) return;
    var t = T();
    if (!t) { MC.ov.close(true); return; }
    if (dr.rerolling && handKey() !== dr.key) { dr.rerolling = false; tl.openDraft(); return; }
    if (dr.picked >= 0 && dr.ready) nextDeal();
  }
  function draftClick(t) {
    var cl = P().closest, el;
    if ((el = cl(t, "data-tkeep"))) { pick(+el.getAttribute("data-tkeep")); return; }
    if (cl(t, "data-tredeal")) { reroll(); return; }
    var hc = t && t.closest ? t.closest(".dr-hand .hc") : null;
    if (hc) pick(+hc.getAttribute("data-hand"));
  }
  function draftKey(k) {
    if (!dr) return false;
    if (k >= "1" && k <= "3") { pick(+k - 1); return true; }
    if (k === "r") { reroll(); return true; }
    if (k === "Enter" && dr.hover >= 0) { pick(dr.hover); return true; }
    if (k === "Left" || k === "Right" || k === "Tab" || k === "ShiftTab") {
      var d = k === "Left" || k === "ShiftTab" ? -1 : 1;
      setHover(dr.hover < 0 ? (d > 0 ? 0 : dr.n - 1) : (dr.hover + d + dr.n) % dr.n);
      return true;
    }
    if (k === "Escape") { MC.ov.close(false); return true; }
    return true;  // the draft owns the keyboard
  }
  tl.draftDebug = function () { return dr ? {n: dr.n, picked: dr.picked, hover: dr.hover, lock: dr.lock, done: dr.done} : null; };

  // ---- "Reconsider your talents" (Figma 2i-18) and the talent row's own panel -------------------------------------
  // After a night's sleep every slot moves freely for a while (rearrange.way "wake"); below the low-level line always;
  // else taking one out uses a free swap. Click a binder card, then a slot; click a slotted card to take it out.
  var rp = null, wakeSeen = {}, deadline = 0;
  function wayTitle(t) {
    var w = t.rearrange.way;
    return w === "wake" ? "A night's sleep: reconsider your talents" : w === "low_level" ? "Your talents · every move is free for now" : "Your talents";
  }
  function secondsLeft() { return Math.max(0, Math.ceil((deadline - Date.now()) / 1000)); }
  function clock(s) { return Math.floor(s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }
  function ringSvg(s, total) {
    var f = Math.max(0, Math.min(1, s / Math.max(1, total))), C = 2 * Math.PI * 19;
    return '<svg width="44" height="44" viewBox="0 0 44 44"><circle cx="22" cy="22" r="19" stroke="#2A2A2E" stroke-width="2.5" fill="none"/><circle cx="22" cy="22" r="19" stroke="#E9E4DA" stroke-width="2.5" fill="none" stroke-dasharray="' +
      (C * f).toFixed(1) + " " + C.toFixed(1) + '" transform="rotate(-90 22 22)" stroke-linecap="round"/></svg><span>' + s + "</span>";
  }
  function trackDeadline(t) {
    if (!t || t.rearrange.way !== "wake" || !t.rearrange.awake) { deadline = 0; return; }
    var d = Date.now() + t.rearrange.secondsLeft * 1000;
    if (Math.abs(d - deadline) > 1500) deadline = d;
  }
  function sub(t) {
    var w = t.rearrange.way;
    if (w === "wake") return "Rearrange every talent slot for free: no draught, fee or scar. Open for " + clock(secondsLeft()) + " more.";
    return t.rearrange.text || "";
  }
  tl.openRow = function (auto) {
    var t = T();
    if (!t || (MC.ov.active() && MC.ov.kind() !== "talents")) return false;
    rp = rp || {sel: null, scroll: 0, auto: !!auto};
    trackDeadline(t);
    var h = '<div class="ov-dim"></div><div class="rc-panel" id="rc-panel"><i class="moon big"></i><div class="rc-t" id="rc-t"></div><div class="rc-s" id="rc-s"></div><div class="rc-ring" id="rc-ring"></div>' +
      '<div class="rc-body" id="rc-body"></div><div class="rc-foot" id="rc-foot"></div></div>';
    MC.ov.panel("talents", {html: h, cls: "tl-rc", onClick: rowClick, onKey: rowKey, onState: rowState, onWheel: rowWheel, onClose: function () { rp = null; }});
    rowRender(true);
    tick();
    return true;
  };
  function tick() {
    if (!rp) return;
    var t = T();
    if (t && t.rearrange.way === "wake" && deadline) {
      var s = secondsLeft();
      patch(MC.$("rc-ring"), String(s), ringSvg(s, t.rearrange.windowSeconds || 60));
      MC.text(MC.$("rc-s"), sub(t));
      MC.ov.later(1000 - (Date.now() % 1000) + 5, tick);
    } else {
      patch(MC.$("rc-ring"), "-", "");
    }
  }
  function rowRender() {
    var t = T();
    if (!rp || !t) return;
    MC.text(MC.$("rc-t"), wayTitle(t));
    MC.text(MC.$("rc-s"), sub(t));
    var key = JSON.stringify([t.row, t.binder, t.slots, t.slotsText, t.rearrange.way, t.rearrange.freeSwaps, t.earned, rp.sel, rp.scroll, P().iconsKey()]);
    patch(MC.$("rc-body"), key, function () { return rowBody(t); });
    patch(MC.$("rc-foot"), JSON.stringify([rp.sel, t.rearrange.way, t.rearrange.freeSwaps, t.binder.length, t.row]), function () { return rowFoot(t); });
  }
  function selCard(t) {
    if (!rp || !rp.sel) return null;
    var i;
    for (i = 0; i < t.binder.length; i++) if (t.binder[i].id === rp.sel) return {c: t.binder[i], from: "binder"};
    for (i = 0; i < t.row.length; i++) if (t.row[i].card && t.row[i].card.id === rp.sel) return {c: t.row[i].card, from: "row", slot: t.row[i].slot};
    return null;
  }
  var COLS = 6, ROWS = 2;
  function rowBody(t) {
    var s = selCard(t), fromBinder = s && s.from === "binder", h = "", open = t.row.filter(function (r) { return r.open; });
    h += '<div class="eb9" style="left:28px;top:0">YOUR TALENTS  ·  ' + esc(upper(t.slotsText)) + "</div>";
    var x = 28;
    for (var i = 0; i < open.length; i++) {
      var r = open[i], attr = ' data-rcslot="' + r.slot + '"';
      h += '<div class="rc-at" style="left:' + x + 'px;top:16px">' + (r.card ? tile(r.card, {attr: attr, sel: rp.sel === r.card.id, drop: fromBinder})
        : emptyTile(r.slot, {attr: attr, drop: fromBinder, sub: fromBinder ? "put it here" : ""})) + "</div>";
      x += 114;
    }
    if (open.length < t.row.length) { h += '<div class="rc-at" style="left:' + x + 'px;top:16px">' + lockedTile(t) + "</div>"; x += 114; }
    var held = heldEarned(t);
    if (held.length && x < 700) h += '<div class="rc-earned" style="left:' + x + 'px;width:' + (712 - x) + 'px">Earned: ' + esc(held.map(function (e) { return e.name; }).join(", ")) + " (always on)</div>";
    h += '<i class="rc-hr"></i><div class="eb9" style="left:28px;top:112px">IN YOUR BINDER  ·  ' + t.binder.length + (t.binder.length === 1 ? " TALENT" : " TALENTS") + "</div>";
    var rows = Math.ceil(t.binder.length / COLS), max = Math.max(0, rows - ROWS);
    rp.scroll = Math.max(0, Math.min(rp.scroll, max));
    if (max) h += '<div class="rc-more">' + (rp.scroll + 1) + " / " + (max + 1) + " · scroll for more</div>";
    if (!t.binder.length) h += '<div class="rc-none">No talents in the binder. A talent draft kept with a full row lands here.</div>';
    for (var k = 0; k < COLS * ROWS; k++) {
      var c = t.binder[rp.scroll * COLS + k];
      if (!c) break;
      h += '<div class="rc-at" style="left:' + (28 + (k % COLS) * 114) + "px;top:" + (130 + Math.floor(k / COLS) * 74) + 'px">' + tile(c, {attr: ' data-rcbind="' + esc(c.id) + '"', sel: rp.sel === c.id}) + "</div>";
    }
    return h;
  }
  function rowFoot(t) {
    var s = selCard(t), w = t.rearrange.way, h = "";
    var line = "";
    if (s && s.from === "binder") {
      var target = firstFree(t), same = null;
      for (var i = 0; i < t.row.length; i++) if (t.row[i].card && t.row[i].card.talent === s.c.talent) same = t.row[i].card;
      line = "Preview · " + s.c.name + " " + (s.c.valueText || "") + ". " + (same ? same.name + "’s " + (same.valueText || "") + " leaves." : target >= 0 ? "Goes into slot " + (target + 1) + "; click a slot to choose." : "Click a slotted talent to swap it out.");
    } else if (s) {
      line = w ? s.c.name + " · " + (w === "free_swap" ? "taking it out uses a free swap (" + t.rearrange.freeSwaps + " left)" : "taking it out is free now") : s.c.name + " · the row is locked until you sleep in a bed";
    } else {
      line = t.binder.length ? "Pick a talent in your binder, then a slot. Pick a slotted talent to take it out." : "Pick a slotted talent to take it out.";
    }
    h += '<div class="rc-pv">' + esc(line) + "</div>";
    h += '<div class="rc-btns">';
    if (s && s.from === "row") h += '<div class="rc-btn' + (w ? "" : " off") + '" data-rcout="' + esc(s.c.id) + '">Take out</div>';
    if (s && s.from === "binder") h += '<div class="rc-btn" data-rcput="' + esc(s.c.id) + '">Put in a free slot</div>';
    h += '<div class="rc-btn quiet" data-rcclose="1">' + (w === "wake" ? "Keep as is" : "Close") + '</div><div class="rc-btn go" data-rcdone="1">Done</div></div>';
    return h;
  }
  function rowClick(target) {
    var cl = P().closest, el, t = T();
    if (!rp || !t) return;
    if (cl(target, "data-rcclose") || cl(target, "data-rcdone")) { MC.ov.close(true); return; }
    if ((el = cl(target, "data-rcout"))) { if (!el.classList.contains("off")) { MC.act("unslotTalent", el.getAttribute("data-rcout")); rp.sel = null; } rowRender(); return; }
    if ((el = cl(target, "data-rcput"))) { MC.act("slotTalent", el.getAttribute("data-rcput") + "#-1"); rp.sel = null; rowRender(); return; }
    if ((el = cl(target, "data-rcbind"))) { var id = el.getAttribute("data-rcbind"); rp.sel = rp.sel === id ? null : id; rowRender(); return; }
    if ((el = cl(target, "data-rcslot"))) {
      var slot = +el.getAttribute("data-rcslot"), s = selCard(t), r = null;
      for (var i = 0; i < t.row.length; i++) if (t.row[i].slot === slot) r = t.row[i];
      if (s && s.from === "binder") { MC.act("slotTalent", s.c.id + "#" + slot); rp.sel = null; }
      else if (r && r.card) rp.sel = rp.sel === r.card.id ? null : r.card.id;
      rowRender();
    }
  }
  function rowKey(k) {
    if (k === "Escape" || k === "Enter") { MC.ov.close(true); return true; }
    return true;
  }
  function rowWheel(step) { if (rp) { rp.scroll = Math.max(0, rp.scroll + step); rowRender(); } }
  function rowState() {
    var t = T();
    if (!t) { MC.ov.close(true); return; }
    var was = deadline;
    trackDeadline(t);
    if (rp && rp.sel && !selCard(t)) rp.sel = null;
    rowRender();
    if (!was && deadline) tick();
  }
  tl.rowDebug = function () { return rp ? {sel: rp.sel, scroll: rp.scroll, deadline: deadline ? secondsLeft() : 0} : null; };

  /**
   * After a night's sleep the row's panel opens by itself, once per waking, when the player opens the Classes page with
   * nothing else up (Mettle's HUD chip says the same in the world).
   */
  tl.moments = function () {
    var t = T();
    if (!t || MC.ov.active() || t.rearrange.way !== "wake" || !t.rearrange.awake || t.rearrange.secondsLeft <= 1) return;
    trackDeadline(t);
    var k = Math.round(deadline / 20000);
    if (wakeSeen[k] || wakeSeen[k - 1] || wakeSeen[k + 1]) return;
    wakeSeen[k] = true;
    tl.openRow(true);
  };

  // ---- clicks on the pages (the sheet's row) -----------------------------------------------------------------------
  tl.click = function (t) {
    var cl = P().closest;
    if (cl(t, "data-tldraft")) { tl.openDraft(); return true; }
    if (cl(t, "data-tlrow")) { tl.openRow(false); return true; }
    if (cl(t, "data-hallview")) { P().st.hall.view = cl(t, "data-hallview").getAttribute("data-hallview"); P().st.hall.scroll = 0; return true; }
    var el;
    if ((el = cl(t, "data-wtitle"))) { MC.act("wearTitle", el.getAttribute("data-wtitle")); return true; }
    return false;
  };

  // ---- the Hall of Firsts: deeds, the name card, titles (Figma 2i-23) -----------------------------------------------
  function deeds() {
    var all = (M().cards.mythics || {}).all || [];
    return all.filter(function (m) { return m.source === "deed"; });
  }
  function me() {
    var ps = M().cards.players || [];
    for (var i = 0; i < ps.length; i++) if (ps[i].name === MC.player) return ps[i];
    return ps[0] || null;
  }
  function held(id) {
    var h = (B() && B().titles ? B().titles.held : []) || [];
    for (var i = 0; i < h.length; i++) if (h[i].id === id) return h[i];
    return null;
  }
  /** Whether the Hall opens on the deeds (Mettle with deed titles) or the Mythics shelf. */
  tl.hallView = function () {
    var v = P().st.hall.view;
    if (!B() || !B().titles) return "shelf";
    return v === "shelf" ? "shelf" : "deeds";
  };
  tl.hall = function (cv) {
    var b = B(), titles = b.titles, list = deeds(), p = me(), ik = P().iconsKey();
    var dk = JSON.stringify([list, titles.held.map(function (t) { return t.id; }), P().st.hall.scroll, MC.player]);  // wearing one repaints only the right column
    var L = part(cv, "hl-deeds", "bf-panel hl-deeds", "left:28px;top:" + Y(70) + "px;width:568px;height:486px");
    L.setAttribute("data-scroll", "hall");
    var rows = 5, max = Math.max(0, list.length - rows);
    P().st.hall.scroll = Math.max(0, Math.min(P().st.hall.scroll, max));
    patch(L, dk, function () {
      var all = (M().cards.mythics || {}).all || [];
      var h = '<div class="eb9" style="left:18px;top:16px">LEGENDARY DEEDS  ·  ONE OF A KIND</div><div class="hl-sw" data-hallview="shelf">All ' + all.length + " Mythics ›</div>";
      if (!list.length) h += '<div class="hl-none">This world has no deed Mythics.</div>';
      for (var i = 0; i < rows; i++) {
        var m = list[P().st.hall.scroll + i];
        if (!m) break;
        var mine = !!held(m.id) && m.holder === MC.player, later = !!held(m.id) && !mine, others = Math.max(0, (m.doneBy || 0) - 1);
        var right = m.claimed ? '<div class="r">Held by ' + esc(m.holder === MC.player ? "you" : m.holder) + (m.day >= 0 ? " since day " + m.day : "") + "</div>"
          : '<div class="r amber">Unclaimed · the first to do it takes the card</div>';
        var foot = later ? (m.claimed ? "you did it too · the title “" + (m.title || "") + "” and shards" : "you hold its title “" + (m.title || "") + "”") : mine ? "yours · the card and the title “" + (m.title || "") + "”"
          : m.claimed ? "do it too: the title “" + (m.title || "") + "” and shards" : "the card, then its title “" + (m.title || "") + "”";
        h += '<div class="hl-row' + (mine || later ? " mine" : "") + '" style="top:' + (44 + i * 86) + 'px"><div class="ic' + (m.claimed ? "" : " q") + '">' + (m.claimed ? '<i class="bdia" style="width:10px;height:10px;background:' + IVORY + '"></i>' : "?") + "</div>" +
          '<div class="n">' + esc(m.name) + '</div><div class="d">' + esc(m.deed || "") + "</div>" + right +
          (others > 0 ? '<div class="also">also done by ' + others + (others === 1 ? " other" : " others") + "</div>" : "") + '<div class="f">' + esc(foot) + "</div></div>";
      }
      if (max) h += '<div class="hl-scr">scroll for ' + (list.length - rows) + " more</div>";
      return h;
    });
    var R = part(cv, "hl-name", "bf-panel hl-name", "left:608px;top:" + Y(70) + "px;width:349px;height:196px");
    patch(R, JSON.stringify([b.byname, titles, p, M().character.level, M().character.background, MC.player, ik]), function () { return nameCard(p); });
    var Q = part(cv, "hl-titles", "bf-panel hl-titles", "left:608px;top:" + Y(278) + "px;width:349px;height:278px");
    patch(Q, JSON.stringify([titles, b.byname && b.byname.legend, list.map(function (m) { return m.id; })]), function () { return titlePicker(list); });
  };
  function nameCard(p) {
    var b = B(), titles = b.titles, bn = b.byname || {}, worn = bn.worn, name = MC.player || (p && p.name) || "", bgd = P().bg();
    var h = '<div class="eb9" style="left:18px;top:16px">NAME CARD  ·  HOW OTHERS SEE YOU</div>';
    h += '<div class="hn-crest' + (bn.legend ? " lg" : "") + '">' + (bn.crest ? MC.img(MC.skillIcon(bn.crest), 32) : MC.img(bgd ? bgd.art : "", 40)) + "</div>";
    h += '<div class="hn-n">' + esc(name) + "</div>";
    var bnText = titles.instead && titles.worn ? "" : worn ? worn.text : "";
    h += '<div class="hn-b">' + esc(bnText || (titles.instead && titles.worn ? titles.wornText : "No byname yet")) + "</div>";
    var tags = "";
    if (titles.worn && !titles.instead) tags += '<span class="hn-chip"><i class="bdia" style="width:6px;height:6px;background:' + CHAMP + '"></i>' + esc(titles.wornText) + "</span>";
    if (bn.legend) tags += '<span class="hn-chip lg"><span class="stars">' + "★★★".slice(0, bn.legend.stars || 1) + "</span>" + esc(bn.legend.title || "") + "</span>";
    if (tags) h += '<div class="hn-tags">' + tags + "</div>";
    h += '<div class="hn-s">' + esc((bgd ? bgd.name + " · " : "") + "Character LV " + M().character.level) + "</div>";
    var ms = (p && p.mythics) || [], chips = "";
    for (var i = 0; i < ms.length && i < 2; i++) chips += '<span class="hn-m"><i class="bdia" style="width:6px;height:6px;background:' + IVORY + '"></i>' + esc(ms[i].name) + "</span>";
    if (ms.length > 2) chips += '<span class="hn-m">+' + (ms.length - 2) + " more</span>";
    h += '<div class="hn-ms">' + (chips || '<span class="hn-none">No Mythic yet</span>') + "</div>";
    var chat = titles.worn && titles.instead ? name + " " + titles.wornText : name + (worn ? " " + worn.text : "") + (titles.worn ? " · " + titles.wornText : "");
    h += '<div class="hn-chat">Chat hover: “' + esc(chat) + "”</div>";
    return h;
  }
  function titlePicker(list) {
    var titles = B().titles, bn = B().byname || {}, h = '<div class="eb9" style="left:18px;top:16px">TITLES YOU HOLD  ·  WEAR ONE</div>', y = 36, inst = titles.instead ? "1" : "0";
    var hl = titles.held || [];
    for (var i = 0; i < hl.length && i < 3; i++) {
      var t = hl[i];
      h += '<div class="ht-row' + (t.wearing ? " on" : "") + '" data-wtitle="' + esc(t.id + "#" + inst) + '" style="top:' + y + 'px"><i class="rd"></i><div class="n">' + esc(t.text) + '</div><div class="s">deed title</div></div>';
      y += 40;
    }
    if (bn.legend && y < 140) { h += '<div class="ht-row ro" style="top:' + y + 'px"><i class="rd lg"></i><div class="n">' + esc(bn.legend.title || "") + '</div><div class="s">legend title · white-gold frame</div></div>'; y += 40; }
    if (!hl.length) {
      var next = list.filter(function (m) { return !held(m.id); })[0];
      h += '<div class="ht-row ro dim" style="top:' + y + 'px"><div class="n">No deed title yet</div><div class="s">' + esc(next ? "do a deed: " + next.name + " gives “" + (next.title || "") + "”" : "every deed is done") + "</div></div>";
      y += 40;
    }
    h += '<i class="ht-hr" style="top:' + (y + 6) + 'px"></i><div class="eb9" style="left:18px;top:' + (y + 18) + 'px">HOW IT SHOWS</div>';
    var w = titles.worn, by = y + 36;
    h += '<div class="ht-pill' + (w && !titles.instead ? " on" : "") + (w ? "" : " off") + '"' + (w ? ' data-wtitle="' + esc(w + "#0") + '"' : "") + ' style="left:18px;top:' + by + 'px">Beside the byname</div>';
    h += '<div class="ht-pill' + (w && titles.instead ? " on" : "") + (w ? "" : " off") + '"' + (w ? ' data-wtitle="' + esc(w + "#1") + '"' : "") + ' style="left:150px;top:' + by + 'px">Instead of the byname</div>';
    h += '<div class="ht-pill' + (w ? "" : " on") + '" data-wtitle="" style="left:18px;top:' + (by + 34) + 'px">Byname only</div>';
    return h;
  }
})();
