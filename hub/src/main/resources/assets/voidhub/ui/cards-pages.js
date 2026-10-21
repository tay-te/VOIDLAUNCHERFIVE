// VOID Mettle cards v2: the Classes page's screens (Figma 295:2 §2h "Mettle cards v2 — cleanup" 546:23595: deck
// builder 546:23599, binder 546:23854, trade 546:24109, shrine rebuild 546:24364, shrine fuse 546:24619; the skill
// gallery and attributes strip the page had before stay as the home).
//
// Each screen builds its parts once (MC.part) and patches them by key (MC.patch) on every state message: a state
// tick that changes nothing visible repaints nothing, a hover repaints one card, a scroll rewrites twelve tiles.
// Coordinates are the design's window-local numbers + 24 (the page's box starts 24 above the drawn window).
(function () {
  "use strict";

  var MC = window.MC, esc = MC.esc, upper = MC.upper, part = MC.part, patch = MC.patch;
  var Y = function (y) { return y + 24; };

  var nav = MC.nav = {view: "home", tab: "skills", skill: null, stab: "deck", shrine: "rebuild"};
  var st = MC.st = {
    binder: {skill: "", rarity: "", kw: "", trait: "", slot: false, att: false, sets: false, sort: "newest", q: "", scroll: 0, sel: null},
    deck: {rarity: "", kw: "", trait: "", slot: false, sort: "rarity", scroll: 0, sel: null, pop: null, totals: false},
    rebuild: {skill: null, pick: {}}, fuse: {pick: []}, release: {pick: []}, hall: {scroll: 0},
    trade: {adding: false, scroll: 0, coinDraft: null, req: null, meScroll: 0, themScroll: 0},
    menu: null,           // the open dropdown: {id, x, y, w, scroll, card}
    textTarget: "search"  // what typing goes to while MC.textFocus: the binder search or the trade's coin field
  };
  MC.near = [];           // players within trade reach, nearest first ({name, d}), from Java's "nearby" message
  var root = null, cv = null, screen = "", byId = {}, lastWaitingSeen = -1, dismissedWaiting = 0, seenReveal = {}, prevTiers = null;
  var justSlotted = null, slotSoundFor = null, kind = "inventory";
  MC.attached = false;
  MC.textFocus = false;

  // ---- data ---------------------------------------------------------------------------------------------------
  function M() { return MC.M; }
  function skills() { var out = []; for (var i = 0; i < M().skills.length; i++) if (M().skills[i].released) out.push(M().skills[i]); return out; }
  function skill(id) { for (var i = 0; i < M().skills.length; i++) if (M().skills[i].id === id) return M().skills[i]; return null; }
  function deck(id) { var d = M().cards.decks; for (var i = 0; i < d.length; i++) if (d[i].skill === id) return d[i]; return null; }
  function cards() { return M().cards.binder.cards; }
  var RANK = {mythic: 5, epic: 4, rare: 3, cursed: 2, common: 1};
  function sortCards(list, how) {
    var idx = {};
    for (var i = 0; i < list.length; i++) idx[list[i].id] = i;
    return list.slice().sort(function (a, b) {
      if (how === "rarity") return (RANK[MC.isMythic(b) ? "mythic" : MC.rarityOf(b)] || 0) - (RANK[MC.isMythic(a) ? "mythic" : MC.rarityOf(a)] || 0) || idx[a.id] - idx[b.id];
      if (how === "requirement") return (a.needs || 0) - (b.needs || 0) || idx[a.id] - idx[b.id];
      if (how === "name") return String(a.name).localeCompare(String(b.name)) || idx[a.id] - idx[b.id];
      return idx[a.id] - idx[b.id];
    });
  }
  function kwNames(c) { var o = []; for (var i = 0; i < (c.keywords || []).length; i++) o.push(c.keywords[i].name); return o.join(", "); }
  function bits(n, coin) { return coin === false ? n + (n === 1 ? " emerald" : " emeralds") : n + " bits"; }
  function feeText(rb) {
    if (!rb) return "";
    var coin = !M().cards.sitting || M().cards.sitting.coin;
    return coin ? rb.bits + " bits" : rb.emeralds + (rb.emeralds === 1 ? " emerald" : " emeralds");
  }

  // ---- header pieces ------------------------------------------------------------------------------------------
  function tabs(list, on, attr, w) {
    var h = '<div class="hd-tabs" style="width:' + (w || 230) + 'px">', x = 2;
    for (var i = 0; i < list.length; i++) {
      var tw = list[i][2] || 72;
      h += '<div class="hd-tab' + (list[i][0] === on ? " on" : "") + '" ' + attr + '="' + list[i][0] + '" style="left:' + x + "px;width:" + tw + 'px">' + esc(list[i][1]) + "</div>";
      x += tw + 3;
    }
    return h + "</div>";
  }
  function skillChip(s, back) {
    return '<div class="hd-chip skill' + (back ? " back" : "") + '"' + (back ? ' data-nav="home"' : "") + '><div class="sig" style="border-color:' + MC.rgba(s.colour, 0.55) + '">' + MC.img(s.icon, 16) +
      '</div><span class="nm">' + esc(s.name) + '</span><span class="lv" style="color:' + s.colour + '">LV ' + s.level + "</span></div>";
  }
  function head(left, right) {
    patch(part(cv, "c-hl", "c-hl"), left, left);
    patch(part(cv, "c-hr", "c-hr"), right, right);
  }
  function footer(l, r) {
    MC.text(part(cv, "c-fl", "footer c-foot"), l || "");
    MC.text(part(cv, "c-fr", "footer right c-foot"), r || "");
  }
  function draftsBadge(short) {
    var n = M().draft ? M().draft.waiting : 0;
    return n > 0 ? '<div class="ov-badge hd-badge" data-deal-any="1"><i class="backs"></i><span>' + n + (short ? (n === 1 ? " draft" : " drafts") : n === 1 ? " draft waiting" : " drafts waiting") + "</span></div>" : "";
  }

  // ---- the router ------------------------------------------------------------------------------------------------
  function screenId() {
    var c = M().cards;
    if (c.sitting) return "shrine";
    if (c.trade) return "trade";
    if (nav.view === "shrine-info") return "shrine-info";
    if (nav.view === "skill" && nav.skill && skill(nav.skill)) return "skill-" + (SCREENS["skill-" + nav.stab] ? nav.stab : "deck");
    return "home-" + (SCREENS["home-" + nav.tab] ? nav.tab : "skills");
  }

  /** Draws the current screen: builds its skeleton when the screen changed, then patches what the state changed. */
  MC.render = function () {
    if (!MC.attached || !M() || !M().cards || kind !== "classes") return;
    var id = screenId();
    if (id !== screen) {
      screen = id;
      cv.innerHTML = "";
      cv.className = "scr scr-" + id;
      st.deck.pop = null;
      st.menu = null;
      st.trade.adding = false;
      focus.key = null;
      if (MC.textFocus) MC.textFocus = false;
    }
    var f = SCREENS[id] || SCREENS["home-skills"];
    var outer = !MC.fitQ;
    if (outer) MC.fitQ = [];
    try {
      f();
    } finally {
      if (outer) MC.fitFlush();
    }
    menu();
    if (id !== "trade") MC.itemsSet([]);
    toast();
    moments();
    if (MC.bf) MC.bf.render(id);
    pageFx();
    ring();
    ui();
  };

  // ---- native layers of the page itself (CardFx): the collection hover flicker and the drop-slot pulse --------------
  // Both loop, so neither is CSS: the page reports where they are and Java draws them over the still page.
  var hoverEl = null, hoverN = 0;
  function setHover(el) {
    if (el === hoverEl) return;
    hoverEl = el;
    if (!el) { MC.fxSet("hover", []); return; }
    hoverN = (hoverN + 1) % 100000;
    var a = MC.abs(el);
    // The card itself lifts 4 px in CSS (a 96 x 128 repaint); the flicker sits on the lifted card.
    MC.fxSet("hover", [{i: "hv" + hoverN, k: el.getAttribute("data-fxhover"), x: a.x, y: a.y - 4, c: el.getAttribute("data-fxtint") || "#9A9DA1"}]);
  }
  function hoverFrom(target) {
    var el = closest(target, "data-fxhover");
    if (el && (st.deck.drag || MC.ov.active() || kind !== "classes" || closest(el, "data-nofx"))) el = null;
    setHover(el);
  }
  function pageFx() {
    var items = [];
    if (MC.attached && kind === "classes" && !MC.ov.active() && screen === "skill-deck" && (st.deck.drag || st.deck.sel)) {
      var ds = cv.querySelectorAll(".slot.drop");
      for (var i = 0; i < ds.length; i++) {
        var a = MC.abs(ds[i]);
        var sz = MC.size(ds[i]);
        items.push({i: "pl" + ds[i].getAttribute("data-slot"), k: "pulse", x: a.x, y: a.y, w: sz.w, h: sz.h, c: "#F5B03C", a: 0.16});
      }
    }
    if (MC.bf && MC.attached && kind === "classes" && !MC.ov.active()) items = items.concat(MC.bf.pulses(cv));
    MC.fxSet("pulse", items);
    if (hoverEl && (!document.documentElement.contains(hoverEl) || st.deck.drag || MC.ov.active())) setHover(null);
  }

  // Mettle answers every card action with a chip (a refusal says why); the page shows the newest for a few seconds.
  var toastShown = "", toastTimer = null;
  function toast() {
    var fb = M().feedback, f = fb && fb.length ? fb[fb.length - 1] : null, key = f ? f.perk + "|" + f.text : "";
    if (key === toastShown) return;
    toastShown = key;
    var el = part(root, "c-toast", "c-toast");
    // Another player asked to trade (Mettle's "<name> wants to trade" chip): it waits half a minute, with Accept.
    var asked = !!f && /trade$/.test(String(f.perk || "")) && /wants to trade/i.test(String(f.text || "")) && !M().cards.trade;
    var life = asked ? 30000 : 3500;
    if (!f || (f.ageMs || 0) > life) { el.style.display = "none"; MC.toggle(root, "has-toast", false); return; }
    el.style.display = "";
    MC.toggle(root, "has-toast", true);
    MC.toggle(el, "ask", asked);
    if (asked) el.innerHTML = '<span class="tx">' + esc(f.text) + '</span><span class="acc" data-taccept="1">Accept</span>';
    else MC.text(el, f.text);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.style.display = "none"; MC.toggle(root, "has-toast", false); }, life - (f.ageMs || 0));
  }
  function hideToast() {
    var el = MC.$("c-toast");
    if (el) el.style.display = "none";
    if (root) MC.toggle(root, "has-toast", false);
  }

  // What owns Esc and the keys, told to Java whenever it changes: an overlay (draft, reveal, set moment), a dropdown or
  // the sound panel, the deck's take-out popover, the trade's card picker, or the keyboard focus ring. Esc closes the
  // top one, not the window; the hotbar keys stay quiet. force: a new screen (Java starts from "nothing").
  var uiSent = "";
  function modal() { return MC.ov.active() || !!st.menu || !!st.deck.pop || !!st.trade.adding || !!focus.key || !!(MC.bf && MC.bf.modal()); }
  function ui(force) {
    var m = modal(), k = (m ? "m" : "") + (MC.textFocus ? "t" : "");
    if (k === uiSent && !force) return;
    uiSent = k;
    MC.send({type: "mettleUi", modal: m, text: !!MC.textFocus});
  }
  MC.ui = ui;
  /** Tests: whether the page holds Esc (what it last told Java). */
  MC.modal = modal;

  /** Opens a draft, a reveal or a set moment when one is due and nothing else is up. */
  function moments() {
    if (MC.ov.active() || screen === "shrine" || screen === "trade") return;
    var c = M().cards, r = c.reveal;
    if (r && !seenReveal[r.id]) {
      seenReveal[r.id] = true;
      // The Mythic just claimed in the draft was seen there; another one (granted, earned) is revealed.
      if (r.source === "drawn" && r.id === MC.lastClaimId && Date.now() - (MC.lastClaimAt || 0) < 30000) return;
      if ((r.ageMs || 0) < 30000 && MC.ov.reveal(r)) return;
    }
    if (pendingSet && MC.ov.setDone(pendingSet.set, pendingSet.cards, pendingSet.slotted)) { pendingSet = null; return; }
    var waiting = M().draft ? M().draft.waiting : 0;
    if (waiting < dismissedWaiting) dismissedWaiting = waiting;
    if (waiting > dismissedWaiting) {
      var list = skills();
      for (var i = 0; i < list.length; i++) if (list[i].drafts > 0 && list[i].hand && list[i].hand.length) { MC.ov.draft(list[i].id); return; }
    }
    // A milestone's banner (build feel) comes after a waiting draft: the draft is what the player opened the page for.
    if (MC.bf) MC.bf.banner();
  }
  MC.onDraftClosed = function () { dismissedWaiting = M().draft ? M().draft.waiting : 0; };

  var pendingSet = null;
  /** A slot change crossed a 2 / 4 / 6 tier of an active set: the moment (Figma 2g-6) is queued. */
  function watchSets() {
    var tiers = {}, best = null;
    var sets = M().cards.sets;
    for (var i = 0; i < sets.length; i++) {
      tiers[sets[i].id] = sets[i].tier;
      if (prevTiers && sets[i].active && sets[i].tier > (prevTiers[sets[i].id] || 0) && (!best || sets[i].tier > best.tier)) best = sets[i];
    }
    prevTiers = tiers;
    if (best) {
      var counted = [];
      var all = cards();
      for (var k = 0; k < all.length; k++) {
        var c = all[k];
        if (c.place !== "slotted" || c.dormant) continue;
        for (var q = 0; q < (c.keywords || []).length; q++) if (c.keywords[q].id === best.id) { counted.push(c); break; }
      }
      pendingSet = {set: best, cards: counted, slotted: justSlotted};
    }
  }

  // ---- home: Skills (the gallery and the attributes strip) ----------------------------------------------------
  function bg() {
    for (var i = 0; i < M().backgrounds.length; i++) if (M().backgrounds[i].id === M().character.background) return M().backgrounds[i];
    return null;
  }

  function homeHead(on) {
    var boons = MC.bf ? MC.bf.homeBoons() : "";
    var right = on === "cards" ? countBox() : on === "character" && MC.bf ? MC.bf.shardsPill() : draftsBadge(!!boons) + boons;
    var bf = !!(M().cards && M().cards.build);
    head(tabs(bf ? [["character", "Character", 85], ["skills", "Skills", 55], ["cards", "Cards", 60], ["hall", "Hall of Firsts", 97]]
      : [["skills", "Skills"], ["cards", "Cards"], ["hall", "Hall of Firsts", 146]], on, "data-tab", bf ? 307 : 300),
      '<div class="hd-row">' + right + soundButton() + "</div>");
  }

  // ---- card sounds (Mettle's client option, SOUNDS.md §8): a speaker in the home header opens a small popover ------
  var sndLocal = null;  // what was just asked for, shown until Mettle's state says so (a tick or two)
  function sound() {
    var s = (M().draft && M().draft.sound) || {};
    var st0 = {enabled: s.enabled !== false, volume: typeof s.volume === "number" ? s.volume : 100};
    if (sndLocal && Date.now() - sndLocal.at < 1500 && (sndLocal.enabled !== st0.enabled || sndLocal.volume !== st0.volume)) return sndLocal;
    sndLocal = null;
    return st0;
  }
  function setSound(enabled, volume) {
    var cur = sound();
    volume = Math.max(0, Math.min(100, Math.round(volume / 5) * 5));
    if (enabled !== cur.enabled) MC.act("setCardSounds", enabled ? "1" : "0");
    if (volume !== cur.volume) MC.act("setCardSoundsVolume", String(volume));
    sndLocal = {enabled: enabled, volume: volume, at: Date.now()};
  }
  var SPEAKER = '<svg width="16" height="16" viewBox="0 0 16 16"><path d="M2.5 6h2.2L8 3.2v9.6L4.7 10H2.5z" fill="#EDEEEF"/>';
  function soundButton() {
    var s = sound(), open = st.menu && st.menu.id === "sound";
    return '<div class="hd-snd' + (open ? " open" : "") + (s.enabled ? "" : " off") + '" data-dd="sound" title="Card sounds">' + SPEAKER +
      (s.enabled ? '<path d="M10.5 5.5c.9.7 1.4 1.6 1.4 2.5s-.5 1.8-1.4 2.5M12.2 3.8c1.4 1.1 2.2 2.6 2.2 4.2s-.8 3.1-2.2 4.2" stroke="#EDEEEF" stroke-width="1.2" fill="none" stroke-linecap="round"/>'
        : '<path d="M10.5 6l4 4M14.5 6l-4 4" stroke="#9A9DA1" stroke-width="1.2" stroke-linecap="round"/>') + "</svg></div>";
  }
  function soundPanel() {
    var s = sound(), w = 168;
    var h = '<div class="ddm-h">Card sounds</div>';
    h += '<div class="snd-row" data-sndtoggle="1"><span>Play card sounds</span><i class="sw' + (s.enabled ? " on" : "") + '"><b></b></i></div>';
    h += '<div class="snd-k">Volume</div><div class="snd-v">' + s.volume + " %</div>";
    h += '<div class="snd-track' + (s.enabled ? "" : " off") + '" data-sndvol="1"><i class="fl" style="width:' + Math.round(w * s.volume / 100) + 'px"></i><i class="kn" style="left:' + Math.round(w * s.volume / 100 - 6) + 'px"></i></div>';
    h += '<div class="snd-n">Draws, burns, the Mythic reveal and slotting. Saved for this game.</div>';
    return h;
  }
  function countBox() {
    var b = M().cards.binder, w = Math.round(78 * Math.min(1, b.count / Math.max(1, b.max)));
    return '<div class="hd-count"><span>' + b.count + " / " + b.max + '</span><i class="tr"></i><i class="fl" style="width:' + w + 'px"></i></div>';
  }

  function strip() {
    var c = M().character, b = bg();
    var h = '<div class="o-art">' + MC.img(b ? b.art : "", 96) + "</div>";
    h += '<div style="left:122px;top:9px"><div class="m-eb">BACKGROUND</div><div class="m-h3" style="margin-top:3px">' + esc(b ? b.name : "") + "</div></div>";
    h += '<div style="left:212px;top:9px"><div class="m-eb">CHARACTER LEVEL</div><div class="o-lv">' + c.level + "</div></div>";
    h += '<div class="o-sep" style="left:326px"></div>';
    for (var k = 0; k < M().attributes.length; k++) {
      var at = M().attributes[k];
      h += '<div class="o-at" style="left:' + (338 + k * 94) + 'px"><div class="m-eb" style="left:10px;top:6px">' + esc(upper(at.name).slice(0, 3)) +
        '</div><div class="o-av">' + c.attributes[at.id] + "</div>" + (c.points > 0 && c.attributes[at.id] < c.maxAttribute ? '<div class="o-plus" data-attr="' + at.id + '">+</div>' : "") + "</div>";
    }
    if (c.points > 0) h += '<div class="pts big" style="right:12px;top:11px"><b></b>' + c.points + " to place</div>";
    return h;
  }

  function skillTile(s) {
    var hue = s.colour, light = MC.mix(hue, "#FFFFFF", 0.42), d = deck(s.id), hot = s.drafts > 0;
    var h = '<div class="sk-halo" style="background:radial-gradient(closest-side,' + MC.rgba(hue, hot ? 0.30 : 0.10) + " 0%," + MC.rgba(hue, 0) + ' 100%)"></div>';
    h += '<div class="o-card" data-skill="' + s.id + '" style="background:' + MC.mix("#191A1C", hue, hot ? 0.075 : 0.05) + ";border:1.5px solid " + MC.rgba(hue, hot ? 0.85 : 0.28) + '">';
    h += '<div class="o-plate" style="background:' + MC.mix("#131315", hue, 0.09) + '"><div class="o-halo" style="background:radial-gradient(circle,' + MC.rgba(hue, 0.5) + " 0%," + MC.rgba(hue, 0.16) + " 45%," + MC.rgba(hue, 0) + ' 72%)"></div>';
    h += '<div style="left:12px;top:9px">' + MC.img(s.icon, 64, "m-img") + '</div><div class="o-num" style="color:' + light + '">' + s.level + '</div><div class="m-eb" style="right:14px;top:56px;color:' + MC.rgba(hue, 0.7) + '">LEVEL</div></div>';
    h += '<div class="m-h3" style="left:14px;top:100px">' + esc(s.name) + "</div>";
    if (hot) h += '<div class="o-pill deal" data-deal="' + s.id + '" style="right:12px;top:98px;background:' + MC.mix("#131315", hue, 0.22) + ";border:1px solid " + MC.rgba(hue, 0.7) + ";color:" + light + '"><b style="background:' + hue + '"></b>Deal ' + s.drafts + "</div>";
    h += '<div class="o-title" style="color:' + MC.mix("#9A9DA1", hue, 0.35) + '">' + esc(s.level > 0 ? s.title : "Untried") + "</div>";
    var frac = s.level >= 20 ? 1 : Math.max(0, Math.min(1, (s.xp - s.levelXp) / Math.max(1, s.nextXp - s.levelXp)));
    h += '<div class="sk-xp" style="left:14px;top:146px;background:' + MC.mix("#131315", hue, 0.3) + '"><i style="width:' + Math.round(192 * (s.level >= 20 ? 1 : frac)) + "px;background:" + hue + '"></i></div>';
    h += '<div class="o-how' + (s.level === 0 ? " always" : "") + '" style="color:' + MC.mix("#B5B8BC", hue, 0.25) + '"><span class="m-eb" style="color:' + MC.rgba(hue, 0.85) + '">HOW IT LEVELS</span><br>' + esc(s.levelsBy || "") + "</div>";
    if (s.level > 0) h += '<div class="m-eb o-xpt" style="left:14px;top:158px;color:' + MC.mix("#626569", hue, 0.3) + '">' + (s.level >= 20 ? "MASTERED" : fmt(s.xp) + " / " + fmt(s.nextXp) + " XP") + "</div>";
    // The deck: its slotted cards' icons in their rarity colours.
    var row = "", n = 0, extra = 0;
    if (d) {
      for (var i = 0; i < d.row.length; i++) {
        var c = d.row[i].card && byId[d.row[i].card];
        if (!c) continue;
        if (n < 6) {
          var rc = MC.RARITY[MC.isMythic(c) ? "mythic" : MC.rarityOf(c)];
          row += '<div class="o-perk" style="background:' + MC.shade(hue) + ";border:1px solid " + MC.rgba(rc, 0.7) + '">' + MC.img(c.icon, 20) + "</div>";
          n++;
        } else extra++;
      }
    }
    if (extra) row += '<div class="o-more">+' + extra + "</div>";
    if (!n) row = '<span class="m-eb">' + (d && d.slots ? "DECK EMPTY  ·  " + d.slots + " SLOTS" : "NO DECK YET") + "</span>";
    if (s.level === 0) row = "";
    h += '<div class="o-perks">' + row + "</div>";
    return h + "</div>";
  }
  function fmt(n) { return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ","); }

  var SCREENS = {};
  SCREENS["home-skills"] = function () {
    homeHead("skills");
    var c = M().character, b = bg();
    patch(part(cv, "h-strip", "o-strip"), JSON.stringify([c, b && b.id, MC.icons[b && b.art] ? 1 : 0]), strip);
    var list = skills();
    for (var i = 0; i < 8; i++) {
      var s = list[i], el = part(cv, "sk-" + i, "sk-wrap", "left:" + (28 + (i % 4) * 237) + "px;top:" + (156 + Math.floor(i / 4) * 216) + "px" + (s ? "" : ";display:none"));
      if (!s) continue;
      var d = deck(s.id), key = JSON.stringify([s.id, s.level, s.xp, s.drafts, s.title, d && d.row, MC.icons[s.icon] ? 1 : 0, iconsKey]);
      patch(el, key, function () { return skillTile(s); });
    }
    var all = cards(), slotted = 0;
    for (var k = 0; k < all.length; k++) if (all[k].place === "slotted") slotted++;
    var w = M().draft ? M().draft.waiting : 0;
    footer(list.length + " SKILLS  ·  " + all.length + " CARDS  ·  " + slotted + " SLOTTED  ·  " + w + (w === 1 ? " DRAFT WAITING" : " DRAFTS WAITING"), "CLICK A SKILL TO OPEN ITS DECK");
  };

  // ---- the binder (home Cards, and a skill's Cards tab) --------------------------------------------------------
  var BIN = {x: 204, y: 105, cols: 4, rows: 3};

  // The filtered, sorted list is kept until the state or a filter changes: a wheel step (it only moves the window over
  // the list) costs no filtering or sorting of the whole binder.
  var stateGen = 0, listCache = {key: null, list: null};
  function binderList(fixed) {
    var b = st.binder;
    var ck = stateGen + "|" + JSON.stringify([fixed, b.skill, b.rarity, b.kw, b.trait, b.slot, b.att, b.sets, b.sort, b.q]);
    if (listCache.key === ck) return listCache.list;
    listCache.key = ck;
    return (listCache.list = filterBinder(fixed));
  }
  function filterBinder(fixed) {
    var b = st.binder, out = [], all = cards(), q = b.q.toLowerCase();
    var sk = fixed || b.skill;
    for (var i = 0; i < all.length; i++) {
      var c = all[i];
      if (sk && c.skill !== sk && !(MC.isMythic(c) && c.deck === sk)) continue;
      if (b.rarity && (MC.isMythic(c) ? "mythic" : MC.rarityOf(c)) !== b.rarity) continue;
      if (b.kw && !hasKw(c, b.kw)) continue;
      if (b.trait && !hasTrait(c, b.trait)) continue;
      if (b.slot && !c.canSlot) continue;
      if (b.att && c.attuned !== false) continue;
      if (b.sets && !(MC.bf && MC.bf.completesSet(c))) continue;
      if (q && (c.name + " " + kwNames(c) + " " + (c.code || "") + " " + traitText(c)).toLowerCase().indexOf(q) < 0) continue;
      out.push(c);
    }
    return sortCards(out, b.sort);
  }
  function hasKw(c, id) { for (var i = 0; i < (c.keywords || []).length; i++) if (c.keywords[i].id === id) return true; return false; }
  function hasTrait(c, id) { for (var i = 0; i < (c.traits || []).length; i++) if (c.traits[i].id === id) return true; return false; }
  function traitText(c) { var o = ""; for (var i = 0; i < (c.traits || []).length; i++) o += c.traits[i].name + " "; return o; }
  function kwName(id) { var k = M().cards.keywords; for (var i = 0; i < k.length; i++) if (k[i].id === id) return k[i].name; return id; }

  function binderLeft(fixed) {
    var b = st.binder, all = cards(), counts = {}, h = "";
    for (var i = 0; i < all.length; i++) counts[all[i].skill] = (counts[all[i].skill] || 0) + 1;
    h += '<div class="eb9" style="left:28px;top:' + Y(73) + 'px">SKILL</div>';
    if (!fixed) {
      h += '<div class="bl-row' + (!b.skill ? " on" : "") + '" data-bskill="" style="top:' + Y(91) + 'px"><span class="n">All skills</span><span class="c">' + all.length + "</span></div>";
      var list = skills();
      for (var k = 0; k < list.length && k < 10; k++) {
        var s = list[k];
        h += '<div class="bl-row' + (b.skill === s.id ? " on" : "") + '" data-bskill="' + s.id + '" style="top:' + Y(115 + k * 24) + 'px"><i style="background:' + s.colour + '"></i><span class="n">' + esc(s.name) + '</span><span class="c">' + (counts[s.id] || 0) + "</span></div>";
      }
    } else {
      var fs = skill(fixed);
      h += '<div class="bl-row on" style="top:' + Y(91) + 'px"><i style="background:' + fs.colour + '"></i><span class="n">' + esc(fs.name) + '</span><span class="c">' + (counts[fixed] || 0) + "</span></div>";
    }
    h += '<div class="eb9" style="left:28px;top:' + Y(321) + 'px">SHOW</div>';
    h += ddPill("bl-f", "b-rarity", b.rarity ? rarityLabel(b.rarity) : "Any rarity", !!b.rarity, "top:" + Y(339) + "px");
    h += ddPill("bl-f", "b-kw", b.kw ? kwName(b.kw) : "Any keyword", !!b.kw, "top:" + Y(371) + "px");
    h += ddPill("bl-f", "b-trait", b.trait ? traitName(b.trait) : "Any trait", !!b.trait, "top:" + Y(403) + "px");
    h += '<div class="bl-f half' + (b.slot ? " on" : "") + '" data-bf="slot" style="top:' + Y(435) + 'px">Slottable</div>';
    h += '<div class="bl-f half r' + (b.att ? " on" : "") + '" data-bf="att" style="top:' + Y(435) + 'px">Attuning</div>';
    if (M().cards.build && MC.bf) {
      h += '<div class="bl-f' + (b.sets ? " on" : "") + '" data-bf="sets" style="top:' + Y(467) + 'px">Completes a set · ' + MC.bf.completesCount(fixed) + "</div>";
      h += '<div class="bl-f half" data-shrineinfo="1" style="top:' + Y(499) + 'px">Shrine ›</div>';
      h += '<div class="bl-f half r" data-dd="trade-req" style="top:' + Y(499) + 'px">Trade ›</div>';
    } else {
      h += '<div class="bl-f half" data-shrineinfo="1" style="top:' + Y(467) + 'px">Shrine ›</div>';
      h += '<div class="bl-f half r" data-dd="trade-req" style="top:' + Y(467) + 'px">Trade ›</div>';
    }
    var mine = null;
    for (var m = 0; m < all.length; m++) if (MC.isMythic(all[m])) { mine = all[m]; break; }
    h += '<div class="bl-my" data-tab="hall" style="top:' + Y(M().cards.build ? 538 : 517) + 'px"><div class="t">' + (mine ? MC.img(mine.icon, 24) : "") + '</div><div class="n">' + esc(mine ? mine.name : "No Mythic yet") +
      '</div><div class="s">Hall of Firsts ›</div></div>';
    return h;
  }

  function binderGrid(list, id) {
    var b = st.binder, maxScroll = Math.max(0, Math.ceil(list.length / BIN.cols) - BIN.rows);
    b.scroll = Math.max(0, Math.min(b.scroll, maxScroll));
    var grid = part(cv, id, "bin-grid", "left:" + BIN.x + "px;top:" + Y(BIN.y) + "px");
    grid.setAttribute("data-scroll", "binder");
    for (var i = 0; i < BIN.cols * BIN.rows; i++) {
      var c = list[b.scroll * BIN.cols + i];
      var t = part(grid, id + "-" + i, "tile", "left:" + (i % BIN.cols) * 104 + "px;top:" + Math.floor(i / BIN.cols) * 136 + "px");
      patch(t, c ? cardKey(c) + (b.sel === c.id) : "-", c ? function () { return MC.ccard(c, {sel: b.sel === c.id, check: b.sel === c.id, attrs: ' data-bcard="' + c.id + '"'}); } : "");
    }
    return maxScroll;
  }
  function cardKey(c) { return c.id + ":" + JSON.stringify([c.place, c.slot, c.attuned, c.attuningMinutes, c.meetsRequirement, c.dormant, c.traits && c.traits.length, c.canSlot, MC.icons[c.icon] ? 1 : 0, c.marks ? [!!c.marks.tempered, c.marks.inscribed ? c.marks.inscribed.keyword : 0] : 0, !!c.signature]); }

  function detail(c) {
    if (!c) return '<div class="bd-empty">Pick a card to see its traits and history.</div>';
    var mythic = MC.isMythic(c);
    var h = '<div class="bd-big" data-nofx="1">' + MC.ccard(c, {}) + "</div>";
    var inDeck = c.place === "slotted" ? (skill(c.deck) ? skill(c.deck).name : "") + " · slot " + (c.slot + 1) : c.place === "trade" ? "In a trade" : "Binder";
    var facts = [["Rarity", mythic ? "Mythic" : (c.rarityName || "") + " · " + (c.tierName || "")], ["Keywords", mythic ? "—" : kwNames(c) || "—"], ["Needs", mythic ? "Any deck" : String(c.needsText || "").replace(/^Needs /, "")], ["In deck", inDeck]];
    for (var i = 0; i < facts.length; i++) h += '<div class="bd-k" style="top:' + (21 + i * 40) + 'px">' + facts[i][0] + '</div><div class="bd-v" style="top:' + (35 + i * 40) + 'px">' + esc(facts[i][1]) + "</div>";
    var y = 225;
    if (mythic) {
      h += '<div class="eb9" style="left:15px;top:' + y + 'px">MYTHIC</div><div class="bd-h" style="top:' + (y + 18) + 'px">' + esc(c.description || "") + "</div>";
      if (c.lore) h += '<div class="bd-h my" style="top:' + (y + 54) + 'px">“' + esc(c.lore) + "”</div>";
      y += 92;
    } else {
      h += '<div class="eb9" style="left:15px;top:' + y + 'px">TRAITS</div>';
      y += 18;
      var ts = c.traits || [];
      if (!ts.length) { h += '<div class="bd-h" style="top:' + y + 'px">No traits: the perk alone.</div>'; y += 22; }
      for (var t = 0; t < ts.length && t < 3; t++) {
        var tr = ts[t], boost = tr.boostedText && tr.boostedText !== tr.valueText;
        h += '<div class="bd-t" style="top:' + y + 'px"><span class="n">' + esc(tr.name) + '</span><span class="v">' + esc(tr.valueText) + '</span><span class="b">' + (boost ? esc(tr.boostedText) + " with " + esc(attrName(tr.attribute)) : "") + "</span></div>";
        y += 22;
      }
      y += 10;
    }
    h += '<div class="eb9" style="left:15px;top:' + y + 'px">HISTORY</div>';
    y += 18;
    var lines = [];
    if (c.drawn && c.drawn.text) lines.push(c.drawn.text);
    if (c.owners > 1 && c.lastOwners) {
      var o = [];
      for (var k = 0; k < c.lastOwners.length; k++) o.push(c.lastOwners[k].name + " on day " + c.lastOwners[k].day);
      lines.push("Held by " + c.owners + " · " + o.join(", "));
    }
    if (c.scars) lines.push("Scar ×" + c.scars);
    if (c.dormant && c.dormantReason) lines.push("Dormant: " + c.dormantReason);
    if (c.attuned === false && c.attuningMinutes > 0) lines.push("Attuning · " + c.attuningMinutes + " min");
    if (MC.bf && c.marks) {
      var bfd = MC.bf.detailExtra(c, y, lines);
      h += bfd.html;
      y = bfd.y;
    } else {
      for (var l = 0; l < lines.length && l < 4; l++) { h += '<div class="bd-h" style="top:' + y + 'px">' + esc(lines[l]) + "</div>"; y += 19; }
      if (c.code) h += '<div class="bd-code" style="top:' + (y + 2) + 'px">' + esc(c.code) + "</div>";
    }
    // Actions: slot or take out, trade.
    var a1, a2, at = M().cards.atShrine, tr2 = M().cards.trade;
    if (c.place === "slotted") a1 = '<div class="bd-a" data-takeout="' + c.id + '">Take out…</div>';
    else if (c.canSlot) a1 = '<div class="bd-a" data-slotbest="' + c.id + '">Slot' + (M().cards.build && !MC.isMythic(c) && skill(c.skill) ? " in " + esc(skill(c.skill).name) : "") + "</div>";
    else {
      a1 = '<div class="bd-a off">Can’t slot</div>';
      if (c.lockedReason) h += '<div class="bd-why">' + esc(c.lockedReason) + "</div>";
    }
    if (tr2 && tr2.open && c.place === "binder" && c.attuned !== false) a2 = '<div class="bd-a" data-toffer="' + c.id + '">Offer in trade</div>';
    else if (at && c.place === "binder") a2 = '<div class="bd-a" data-goshrine="release">Release at the shrine</div>';
    else if (!tr2 && c.place === "binder" && c.attuned !== false) a2 = '<div class="bd-a" data-dd="trade-req" data-ddcard="' + c.id + '">Trade…</div>';
    else a2 = '<div class="bd-a off">' + (c.attuned === false ? "Attuning" : "Trade") + "</div>";
    h += '<div class="bd-acts">' + a1 + a2 + "</div>";
    return h;
  }
  function attrName(id) { for (var i = 0; i < M().attributes.length; i++) if (M().attributes[i].id === id) return M().attributes[i].name + " " + M().character.attributes[id]; return id; }

  function binderScreen(fixed) {
    var b = st.binder;
    if (fixed && b.skill !== "") b.skill = "";
    var list = binderList(fixed);
    // The left column does not move with the grid: a scroll or a selection leaves it alone (no rewrite, no repaint).
    var leftKey = [b.skill, b.rarity, b.kw, b.trait, b.slot, b.att, b.sets, fixed, cards().length, MC.icons ? iconsKey : 0, st.menu && st.menu.id, MC.bf && b.sets !== undefined ? MC.bf.completesCount(fixed) : 0];
    patch(part(cv, "b-left", "b-left"), JSON.stringify(leftKey), function () { return binderLeft(fixed); });
    var search = '<div class="b-search' + (MC.textFocus ? " on" : "") + '" data-search="1">' + (b.q ? esc(b.q) + (MC.textFocus ? '<i class="caret"></i>' : "") : (MC.textFocus ? '<i class="caret"></i>' : '<span class="ph">Search cards, traits, ids</span>')) + "</div>";
    search += ddPill("b-sort", "b-sort", sortLabel(b.sort), false, "", st.menu && st.menu.id === "b-sort");
    patch(part(cv, "b-top", "b-top"), b.q + MC.textFocus + b.sort + (st.menu && st.menu.id), search);
    var maxScroll = binderGrid(list, "bg");
    var shown = Math.min(list.length - b.scroll * BIN.cols, BIN.cols * BIN.rows);
    MC.text(part(cv, "b-note", "b-note", "left:204px;top:" + Y(515) + "px"), Math.max(0, shown) + " of " + list.length + (maxScroll > 0 ? " · scroll" : "") + "  ·  release three of one skill for a reroll at a Card Shrine");
    var sel = b.sel ? byId[b.sel] : null;
    if (b.sel && !sel) b.sel = null;
    patch(part(cv, "b-detail", "b-detail"), sel ? cardKey(sel) + JSON.stringify([sel.traits, sel.drawn, M().cards.atShrine, !!M().cards.trade, sel.marks, sel.signature, MC.bf ? MC.bf.slotPreviewKey(sel) : 0]) : "none", function () { return detail(sel); });
  }

  SCREENS["home-cards"] = function () {
    homeHead("cards");
    binderScreen(null);
    footer("", "");
  };

  // ---- the Hall of Firsts ----------------------------------------------------------------------------------------
  SCREENS["home-hall"] = function () {
    homeHead("hall");
    var my = M().cards.mythics || {all: []}, all = my.all || [], mineIds = {}, owned = cards();
    for (var i = 0; i < owned.length; i++) if (MC.isMythic(owned[i]) && owned[i].perk) mineIds[String(owned[i].perk).replace("mythic/", "")] = true;
    // Yours first, then the claimed ones with their holders, then the silhouettes.
    all = all.slice().sort(function (a, b) { return (mineIds[b.id] ? 2 : b.claimed ? 1 : 0) - (mineIds[a.id] ? 2 : a.claimed ? 1 : 0); });
    var known = 0, mine = 0;
    for (var k = 0; k < all.length; k++) { if (all[k].claimed) known++; if (mineIds[all[k].id]) mine++; }
    var panel = part(cv, "hall", "hall");
    panel.setAttribute("data-scroll", "hall");
    var rows = Math.ceil(all.length / 5), maxScroll = Math.max(0, rows - 2);
    st.hall.scroll = Math.max(0, Math.min(st.hall.scroll, maxScroll));
    patch(panel, JSON.stringify([all, st.hall.scroll, mineIds, iconsKey]), function () {
      var h = '<div class="hall-glow"></div><div class="eb9" style="left:20px;top:16px">MYTHICS IN THIS WORLD</div><div class="hall-sub">' + known + " known · " + mine + " yours · each exists once per world" +
        (my.drawnWaiting ? "  ·  " + my.drawnWaiting + " drawn Mythics still wait" : "") + "</div>";
      for (var j = 0; j < 10; j++) {
        var m = all[st.hall.scroll * 5 + j];
        if (!m) break;
        var x = 20 + (j % 5) * 180, y = 46 + Math.floor(j / 5) * 220, isMine = !!mineIds[m.id];
        h += '<div class="hall-t" style="left:' + x + "px;top:" + y + 'px">' + MC.miniMythic(m, isMine) + '<div class="hall-n">' + esc(m.claimed || isMine ? m.name : m.name) + '</div><div class="hall-h' + (isMine ? " mine" : "") + '">' +
          esc(isMine ? "Yours" + (m.day >= 0 ? " · day " + m.day : "") : m.claimed ? "Held by " + m.holder : m.source === "drawn" ? "Unclaimed · dealt from skill 15" : "Unclaimed · " + (m.deed || "")) + "</div></div>";
      }
      return h;
    });
    footer("YOUR MYTHIC SITS ABOVE YOUR SKILL HANDS  ·  OTHERS' MYTHICS ARE PUBLIC", maxScroll > 0 ? "SCROLL FOR MORE" : "");
  };

  // ---- a skill: Deck / Tree / Cards ------------------------------------------------------------------------------
  function skillHead(s, on) {
    var d = deck(s.id), b = d && d.boons > 0 && MC.bf ? MC.bf.boonBadge(s, d.boons, on === "boon" || on === "temper" || on === "inscribe") : "";
    head('<div class="hd-row">' + skillChip(s, true) + b + "</div>", tabs([["deck", "Deck"], ["tree", "Tree"], ["cards", "Cards"]], on === "boon" || on === "temper" || on === "inscribe" ? "deck" : on, "data-stab", 230));
  }

  var SLOT = {x: 28, y: 95, pitch: 104};

  function slotHtml(d, r, i, s) {
    var dz = st.deck.drag || st.deck.sel;
    if (r.card) {
      var c = byId[r.card];
      if (!c) return "";
      return MC.ccard(c, {sel: st.deck.pop === c.id, check: st.deck.pop === c.id, attrs: ' data-scard="' + c.id + '" data-slot="' + i + '"', cls: r.dormant ? "dormant" : ""});
    }
    if (r.open) {
      if (dz) return '<div class="slot drop" data-slot="' + i + '"><div class="t">Drag a card<br>here · free</div></div>';
      return '<div class="slot empty" data-slot="' + i + '"><div class="t">Empty</div></div>';
    }
    var amber = r.locked && r.locked.indexOf(s.name) >= 0;
    return '<div class="slot locked"><i class="lock big' + (amber ? " amber" : "") + '"></i><div class="t' + (amber ? " amber" : "") + '">' + esc(r.locked || "Locked").replace(/^Opens at /, "Opens at<br>") + "</div></div>";
  }

  function setsColumn() {
    var sets = M().cards.sets, pins = M().cards.pins, h = "", y = 41, active = 0;
    var shown = sets.slice().sort(function (a, b) { return (b.active ? 1 : 0) - (a.active ? 1 : 0) || b.count - a.count; });
    for (var i = 0; i < sets.length; i++) if (sets[i].active) active++;
    var bfx = M().cards.build;
    h += '<div class="eb9" style="left:15px;top:13px">SETS · ' + (active ? active + " ACTIVE" : "NONE YET") + "</div>";
    if (bfx && MC.bf) h += MC.bf.focusChip("sc-focus");
    else h += '<div class="sc-pin" data-pinauto="1">' + (pins.keywords.length ? "Pinned" : "Automatic") + (pins.canChange ? "" : " · once a day") + "</div>";
    if (st.deck.totals) {
      var tr = M().cards.traits;
      for (var t = 0; t < tr.length && y < 270; t++) { h += '<div class="sc-tt" style="top:' + y + 'px">' + esc(tr[t].text) + "</div>"; y += 22; }
      if (!tr.length) h += '<div class="sc-tt" style="top:' + y + 'px">No traits slotted yet.</div>';
    } else {
      for (var k = 0; k < shown.length && y < 270; k++) {
        var s = shown[k];
        if (!s.active && !s.count) continue;
        var pinned = pins.keywords.indexOf(s.id) >= 0;
        var near = s.oneAway && s.next >= 4, cue = s.asleep ? " asleep" : near ? " away" : "";
        if (s.active || near || s.asleep) {
          var sub = s.asleep ? s.asleepText : near && s.nextText ? "One card away · " + s.nextText : setSkills(s);
          h += '<div class="sc-set on' + cue + '" data-pin="' + s.id + '" data-kwhover="1" style="top:' + y + 'px">' + MC.glyph(s, 16) + '<span class="n">' + esc(s.name) + (s.focus ? '<i class="fmark"></i>' : "") + '</span><span class="c">' + s.count + "</span>" + pips(s.tier, true, s.asleep) +
            (near && s.nextText ? '<span class="away">' + esc(s.nextText) + "</span>" : pinned ? '<span class="pinmark">PINNED</span>' : "") + '<div class="sub">' + esc(sub) + "</div></div>";
          y += 50;
        } else {
          h += '<div class="sc-set" data-pin="' + s.id + '" style="top:' + y + 'px">' + MC.glyph(s, 16) + '<span class="n">' + esc(s.name) + '</span><span class="c">' + s.count + "</span>" + pips(s.tier, false) + "</div>";
          y += 34;
        }
      }
    }
    h += '<div class="sc-tog" data-totals="1"><span>Trait totals</span><span class="r">' + (st.deck.totals ? "hide ‹" : "show ›") + "</span></div>";
    return h;
  }
  function pips(tier, on, asleep) {
    var h = '<span class="pips3">';
    for (var i = 0; i < 3; i++) h += '<i class="' + (tier >= (i + 1) * 2 ? (on ? "on" : "dim") : asleep && i === 2 ? "sleep" : on ? "" : "off") + '"></i>';
    return h + "</span>";
  }
  function setSkills(set) {
    var per = {}, order = [], all = cards();
    for (var i = 0; i < all.length; i++) {
      var c = all[i];
      if (c.place !== "slotted" || c.dormant || !hasKw(c, set.id)) continue;
      var n = skill(c.deck) ? skill(c.deck).name : c.skillName;
      if (!per[n]) { per[n] = 0; order.push(n); }
      per[n]++;
    }
    var o = [];
    for (var k = 0; k < order.length; k++) o.push(order[k] + " " + per[order[k]]);
    return o.join(" · ");
  }

  function deckBinderList(s) {
    var b = st.deck, out = [], all = cards();
    for (var i = 0; i < all.length; i++) {
      var c = all[i];
      if (c.place !== "binder") continue;
      if (c.skill !== s.id && !MC.isMythic(c)) continue;
      if (b.rarity && (MC.isMythic(c) ? "mythic" : MC.rarityOf(c)) !== b.rarity) continue;
      if (b.kw && !hasKw(c, b.kw)) continue;
      if (b.trait && !hasTrait(c, b.trait)) continue;
      if (b.slot && !c.canSlot) continue;
      out.push(c);
    }
    return sortCards(out, b.sort);
  }

  function popover(c, d, s) {
    var rb = c.rebuild || {}, costs = M().cards.costs, free = c.dormant || costs.lowLevel || d.freeRebuild || costs.freeSwaps > 0;
    var how = c.dormant ? "Take it out (dormant: free)" : costs.lowLevel ? "Take it out (free below level " + costs.freeBelowLevel + ")" : d.freeRebuild ? "Use the free rebuild" : costs.freeSwaps > 0 ? "Use a free swap" : "";
    var slot = d.row.indexOf ? 0 : 0;
    for (var i = 0; i < d.row.length; i++) if (d.row[i].card === c.id) slot = i;
    var x = Math.max(28, Math.min(669, SLOT.x + slot * SLOT.pitch - 20));
    var h = '<div class="pop" style="left:' + x + "px;top:" + Y(231) + "px" + (M().cards.build ? ";height:236px" : "") + '"><i class="ptr" style="left:' + (SLOT.x + slot * SLOT.pitch + 40 - x) + 'px"></i>';
    h += '<div class="pt">Take ' + esc(c.baseName || c.name) + " out</div>";
    h += '<div class="pk" style="top:41px">Fee</div><div class="pv" style="top:40px">' + (free ? "free" : esc(feeText(rb))) + "</div>";
    h += '<div class="pk" style="top:63px">Draught</div><div class="pv" style="top:62px">' + (free ? "none" : "Forgetting · " + esc(s.name) + " · once a sitting") + "</div>";
    h += '<div class="pk" style="top:85px">Scar</div><div class="pv" style="top:84px">' + (free || !rb.loses ? (rb.spared ? "spared" : "none") : "loses " + esc(rb.loses.text)) + "</div>";
    h += '<div class="pn">' + esc(free ? "Free swaps refill as you level. Rebuilds happen at a trainer or a Card Shrine." : d.rebuildHint || "") + "</div>";
    if (free) h += '<div class="pb pri" data-unslot="' + c.id + '">' + esc(how) + "</div>";
    else if (M().cards.atShrine) h += '<div class="pb pri" data-goshrine="rebuild">Rebuild at the shrine</div>';
    else h += '<div class="pb pri off">Needs a trainer or a shrine</div>';
    if (MC.bf && M().cards.build) h += MC.bf.signatureButton(c, d);
    h += '<div class="pb" data-popclose="1">Cancel</div>';
    return h + "</div>";
  }

  SCREENS["skill-deck"] = function () {
    var s = skill(nav.skill), d = deck(s.id);
    skillHead(s, "deck");
    if (!d) { patch(part(cv, "dk-none", "dk-none"), "none", "This skill has no deck yet: it opens at level 2."); footer("", ""); return; }
    var used = 0;
    for (var i = 0; i < d.row.length; i++) if (d.row[i].card) used++;
    var costs = M().cards.costs, sw = "";
    for (var p = 0; p < costs.maxFreeSwaps; p++) sw += '<i class="' + (p < costs.freeSwaps ? "on" : "") + '"></i>';
    var marksPips = MC.bf ? MC.bf.deckMarks(d) : "";
    patch(part(cv, "dk-head", "dk-head"), JSON.stringify([s.name, used, d.slots, costs.freeSwaps, costs.maxFreeSwaps, marksPips]),
      '<div class="eb9" style="left:28px;top:' + Y(71) + 'px">' + esc(upper(s.name)) + ' DECK</div><div class="dk-n" style="left:' + (28 + upper(s.name).length * 9 + 50) + "px;top:" + Y(69) + 'px">' + used + " of " + d.slots + " slots</div>" +
      '<div class="dk-swaps"><span class="pp">' + sw + "</span>" + costs.freeSwaps + (costs.freeSwaps === 1 ? " free swap" : " free swaps") + "</div>" + marksPips);
    var row = part(cv, "dk-row", "dk-row", "left:" + SLOT.x + "px;top:" + Y(SLOT.y) + "px");
    var n = d.row.length, scale = n > 9 ? 929 / (n * SLOT.pitch - 8) : 1;
    row.style.transform = scale < 1 ? "scale(" + scale.toFixed(4) + ")" : "";
    for (var k = 0; k < Math.max(n, 9); k++) {
      var r = d.row[k], el = part(row, "dk-s" + k, "tile", "left:" + k * SLOT.pitch + "px;top:0" + (r ? "" : ";display:none"));
      if (!r) continue;
      var c = r.card && byId[r.card];
      patch(el, JSON.stringify([r, c ? cardKey(c) : 0, !!(st.deck.drag || st.deck.sel), st.deck.pop]), function () { return slotHtml(d, r, k, s); });
    }
    // The binder under the deck.
    var b = st.deck, list = deckBinderList(s), cols = 6, rows = 2;
    var filters = '<div class="eb9" style="left:28px;top:' + Y(247) + 'px">BINDER · ' + esc(upper(s.name)) + '</div><div class="dk-n" style="left:' + (28 + upper(s.name).length * 9 + 76) + "px;top:" + Y(245) + 'px">' + list.length + (list.length === 1 ? " card" : " cards") + "</div>";
    // The filter bar: right-aligned pills ending where the sets column begins.
    filters += '<div class="pf-bar">';
    filters += ddPill("pf", "d-rarity", b.rarity ? rarityLabel(b.rarity) : "Rarity", !!b.rarity);
    filters += ddPill("pf", "d-kw", b.kw ? kwName(b.kw) : "Keyword", !!b.kw);
    filters += ddPill("pf", "d-trait", b.trait ? traitName(b.trait) : "Trait", !!b.trait);
    filters += '<div class="pf' + (b.slot ? " on" : "") + '" data-df="slot">Slottable</div>';
    filters += ddPill("pf", "d-sort", "Sort: " + (b.sort === "requirement" ? "Need" : sortLabel(b.sort)), false);
    filters += "</div>";
    patch(part(cv, "dk-bh", "dk-bh"), JSON.stringify([b.rarity, b.kw, b.trait, b.slot, b.sort, list.length, s.name, st.menu && st.menu.id]), filters);
    var maxScroll = Math.max(0, Math.ceil(list.length / cols) - rows);
    b.scroll = Math.max(0, Math.min(b.scroll, maxScroll));
    var grid = part(cv, "dk-grid", "bin-grid", "left:28px;top:" + Y(275) + "px");
    grid.setAttribute("data-scroll", "deck");
    for (var g = 0; g < cols * rows; g++) {
      var bc = list[b.scroll * cols + g];
      var t = part(grid, "dk-g" + g, "tile", "left:" + (g % cols) * 104 + "px;top:" + Math.floor(g / cols) * 136 + "px");
      patch(t, bc ? cardKey(bc) + (st.deck.sel === bc.id) + (st.deck.drag && st.deck.drag.id === bc.id) : "-", bc ? function () {
        return MC.ccard(bc, {sel: st.deck.sel === bc.id, check: st.deck.sel === bc.id, attrs: ' data-dcard="' + bc.id + '"', cls: st.deck.drag && st.deck.drag.id === bc.id ? "lifted" : ""});
      } : "");
    }
    var shown = Math.max(0, Math.min(list.length - b.scroll * cols, cols * rows));
    MC.text(part(cv, "dk-note", "b-note", "left:28px;top:" + Y(551) + "px"), shown + " of " + list.length + (maxScroll > 0 ? " · scroll for more" : "") + (st.deck.sel ? "  ·  click an open slot, or drag the card there" : ""));
    patch(part(cv, "dk-sets", "dk-sets"), JSON.stringify([M().cards.sets, M().cards.pins, st.deck.totals, st.deck.totals ? M().cards.traits : 0, iconsKey]), setsColumn);
    var pc = st.deck.pop && byId[st.deck.pop];
    if (st.deck.pop && !pc) st.deck.pop = null;
    patch(part(cv, "dk-pop", "dk-pop"), pc ? cardKey(pc) + JSON.stringify([pc.rebuild, costs, M().cards.atShrine, pc.signature, d.signature]) : "", pc ? function () { return popover(pc, d, s); } : "");
    footer(upper(s.name) + " " + s.level + "  ·  " + (d.nextSlotAt ? "NEXT SLOT AT " + upper(s.name) + " " + d.nextSlotAt : "EVERY SLOT OPEN") + "  ·  SLOTTING IS FREE, ANYWHERE", upper(d.rebuildHint || ""));
  };

  SCREENS["skill-tree"] = function () {
    var s = skill(nav.skill);
    skillHead(s, "tree");
    var key = JSON.stringify([s, M().perks, M().character, MC.treeSel, iconsKey]);
    patch(part(cv, "tree", "c-tree"), key, function () { return window.hubMettle && window.hubMettle.treeHtml ? window.hubMettle.treeHtml(s.id) : ""; });
    footer("", "");
  };

  SCREENS["skill-cards"] = function () {
    var s = skill(nav.skill);
    skillHead(s, "cards");
    binderScreen(s.id);
    footer("", "");
  };

  // ---- the Card Shrine (a sitting: a shrine, or a trainer from conversation) --------------------------------------
  function sittingSkills() {
    var sit = M().cards.sitting, out = [];
    var list = skills();
    for (var i = 0; i < list.length; i++) if (!sit.skills.length || sit.skills.indexOf(list[i].id) >= 0) out.push(list[i]);
    return out;
  }

  function shrineHead() {
    var sit = M().cards.sitting, name = sit.kind === "shrine" ? "Card Shrine" : (sit.master ? "Master trainer" : "Trainer") + (sit.skills.length === 1 && skill(sit.skills[0]) ? " · " + skill(sit.skills[0]).name : "");
    var forge = sit.kind === "shrine" && M().cards.build && MC.bf;
    var t = sit.kind === "shrine" ? (forge ? [["rebuild", "Rebuild", 62], ["fuse", "Fuse", 47], ["release", "Release", 64], ["forge", "Forge", 68]] : [["rebuild", "Rebuild"], ["fuse", "Fuse"], ["release", "Release"]]) : [["rebuild", "Rebuild"], ["fuse", "Fuse"]];
    if (nav.shrine === "forge" && !forge) nav.shrine = "rebuild";
    if (sit.kind !== "shrine" && nav.shrine === "release") nav.shrine = "rebuild";
    head('<div class="hd-chip plain"><span class="nm">' + esc(name) + "</span></div>", tabs(t, nav.shrine, "data-shtab", forge ? 251 : 230));
  }

  var LIST_Y = 131, ROW = 60;
  SCREENS.shrine = function () {
    shrineHead();
    var sit = M().cards.sitting;
    if (nav.shrine === "fuse") fuseTab();
    else if (nav.shrine === "release") releaseTab();
    else if (nav.shrine === "forge" && MC.bf) { shrineClear("fg"); MC.bf.forgeTab(cv); }
    else rebuildTab();
    if (nav.shrine !== "forge" && MC.bf) MC.bf.forgeClear(cv);
    footer(sit.kind === "shrine" ? "THE SHRINE BURNS ITS FEES  ·  WALK 8 BLOCKS AWAY TO LEAVE" : "A TRAINER KEEPS THE FEE  ·  CAPS CARDS BY ITS TIER", "");
    patch(part(cv, "sh-leave", "sh-leave"), "1", '<span data-closesit="1">LEAVE THE ' + (sit.kind === "shrine" ? "SHRINE" : "TRAINER") + "</span>");
  };

  function shrineClear(keep) {
    var ids = ["rb-chips", "rb-heads", "rb-list", "rb-total", "fu-head", "fu-list", "fu-arrow", "fu-result", "fu-bar", "rl-head", "rl-list", "rl-bar"];
    for (var i = 0; i < ids.length; i++) {
      if (keep.indexOf(ids[i].slice(0, 2)) === 0) continue;
      var el = MC.$(ids[i]);
      if (el) el.parentNode.removeChild(el);
    }
  }

  function rebuildTab() {
    shrineClear("rb");
    var sit = M().cards.sitting, rb = st.rebuild, list = sittingSkills().filter(function (x) {
      var dk = deck(x.id);
      if (!dk) return false;
      for (var i = 0; i < dk.row.length; i++) if (dk.row[i].card) return true;
      return false;
    });
    if (!rb.skill || !skill(rb.skill) || list.map(function (x) { return x.id; }).indexOf(rb.skill) < 0) rb.skill = list.length ? list[0].id : null;
    var chips = "";
    for (var i = 0, x = 28; i < list.length; i++) {
      var s = list[i], w = 34 + s.name.length * 6.6;
      if (x + w > 664) break;
      chips += '<div class="sk-chip' + (rb.skill === s.id ? " on" : "") + '" data-rbskill="' + s.id + '" style="left:' + Math.round(x) + 'px"><i style="background:' + s.colour + '"></i>' + esc(s.name) + "</div>";
      x += w + 8;
    }
    var cur = skill(rb.skill);
    chips += '<div class="draught"><i class="dr"></i>Draught of Forgetting · ' + esc(cur ? cur.name : "") + " · drunk once a sitting</div>";
    patch(part(cv, "rb-chips", "rb-chips"), JSON.stringify([rb.skill, list.length]), chips);
    patch(part(cv, "rb-heads", "rb-heads"), "1", '<div class="eb9" style="left:72px;top:' + Y(111) + 'px">CARD</div><div class="eb9" style="left:324px;top:' + Y(111) + 'px">TRAITS</div><div class="eb9 r" style="right:186px;top:' + Y(111) +
      'px">FEE</div><div class="eb9 r" style="right:46px;top:' + Y(111) + 'px">SCAR IF TAKEN OUT</div>');
    var d = deck(rb.skill), rows = [];
    if (d) for (var k = 0; k < d.row.length; k++) if (d.row[k].card && byId[d.row[k].card]) rows.push(byId[d.row[k].card]);
    for (var p in rb.pick) if (rows.map(function (c) { return c.id; }).indexOf(p) < 0) delete rb.pick[p];
    var listEl = part(cv, "rb-list", "rb-list", "left:28px;top:" + Y(LIST_Y) + "px");
    listEl.setAttribute("data-scroll", "rebuild");
    var coin = sit.coin, n = 0, fee = 0, scars = 0;
    for (var r = 0; r < 5; r++) {
      var c = rows[r + (rb.scroll || 0)], el = part(listEl, "rb-r" + r, "rwrap", "top:" + r * ROW + "px");
      patch(el, c ? cardKey(c) + JSON.stringify([c.rebuild, !!rb.pick[c.id], coin]) : "-", c ? (function (c) {
        return function () {
          var b = c.rebuild || {}, over = b.overCap;
          var right = MC.cols([{r: 176, text: over ? "over the cap" : coin ? b.bits + " bits" : b.emeralds + " emeralds", colour: rb.pick[c.id] ? "#EDEEEF" : over ? MC.AMBER : "#9A9DA1"},
            {r: 16, text: b.spared || !b.loses ? (c.dormant ? "dormant · free" : b.spared ? "spared" : "mark only") : "− " + b.loses.text, colour: rb.pick[c.id] ? "#EDEEEF" : b.loses && !b.spared ? "#9A9DA1" : "#626569"}]);
          return MC.row(c, {check: !!rb.pick[c.id], sel: !!rb.pick[c.id], attrs: ' data-rbpick="' + c.id + '"', right: right, traitX: 296, glyphX: 246, traitW: 360});
        };
      })(c) : "");
    }
    for (var q in rb.pick) { var pc = byId[q]; if (!pc) continue; n++; var pb = pc.rebuild || {}; fee += coin ? pb.bits || 0 : pb.emeralds || 0; if (pb.loses && !pb.spared) scars++; }
    if (!rows.length) patch(part(listEl, "rb-r0", "rwrap", "top:0"), "none", '<div class="rw-empty">Nothing slotted in this deck.</div>');
    var free = M().cards.costs.lowLevel || (d && d.freeRebuild);
    patch(part(cv, "rb-total", "tbar", "top:" + Y(445) + "px;height:120px"), JSON.stringify([n, fee, scars, coin, free]),
      '<div class="eb9" style="left:19px;top:17px">' + n + ' SELECTED</div><div class="tb-big" style="top:35px">' + (free ? "Free" : bits(fee, coin)) + "  ·  " + (n && !free ? "1 draught" : "no draught") + "  ·  " + scars + (scars === 1 ? " scar" : " scars") +
      '</div><div class="tb-note" style="top:65px">' + esc(sit.kind === "shrine" ? "The shrine burns the fee. A trainer keeps it, caps cards by its tier, and a city master scars only one card in two. The scar shown never changes: nothing to fish for." :
        "This trainer keeps the fee and takes out cards up to its cap" + (sit.master ? "; a master's hand spares one card in two." : ".")) + '</div><div class="tb-go' + (n ? "" : " off") + '" data-rebuild="1" style="top:39px">' + (n ? "Rebuild " + n + (n === 1 ? " card" : " cards") : "Pick cards to take out") + "</div>");
  }

  function fuseGroups() {
    var groups = {}, order = [], all = cards();
    for (var i = 0; i < all.length; i++) {
      var c = all[i];
      if (c.place !== "binder" || MC.isMythic(c) || c.rarity === "epic" || c.attuned === false) continue;
      var k = c.perk + "|" + c.rarity;
      if (!groups[k]) { groups[k] = []; order.push(k); }
      groups[k].push(c);
    }
    var out = [];
    for (var j = 0; j < order.length; j++) if (groups[order[j]].length >= 3) out.push(groups[order[j]]);
    return out;
  }
  function bestTrait(list) {
    var best = null, score = -1;
    for (var i = 0; i < list.length; i++) for (var t = 0; t < (list[i].traits || []).length; t++) {
      var tr = list[i].traits[t], v = tr.value / Math.max(0.0001, tr.cap || 1);
      if (v > score) { score = v; best = tr; }
    }
    return best;
  }
  var NEXT_RARITY = {common: "rare", rare: "epic", cursed: "cursed"};

  function fuseTab() {
    shrineClear("fu");
    var f = st.fuse, groups = fuseGroups(), picked = [];
    for (var i = 0; i < f.pick.length; i++) if (byId[f.pick[i]] && byId[f.pick[i]].place === "binder") picked.push(byId[f.pick[i]]);
    f.pick = picked.map(function (c) { return c.id; });
    var pg = picked.length ? picked[0].perk + "|" + picked[0].rarity : null;
    patch(part(cv, "fu-head", "fu-head"), JSON.stringify([f.pick, groups.length]), '<div class="eb9" style="left:28px;top:' + Y(75) + 'px">THREE COPIES OF ONE PERK AND RARITY → ONE CARD OF THE NEXT RARITY</div>' +
      '<div class="fu-count">' + (picked.length ? esc(picked[0].baseName + " · " + picked[0].rarityName + " · " + picked.length + " of 3 picked") : groups.length ? "Pick three copies" : "") + "</div>");
    var rows = [];
    if (picked.length) { for (var g = 0; g < groups.length; g++) if (groups[g][0].perk + "|" + groups[g][0].rarity === pg) rows = groups[g]; }
    else for (var h = 0; h < groups.length; h++) rows = rows.concat(groups[h]);
    var best = bestTrait(picked.length === 3 ? picked : rows.filter(function (c) { return f.pick.indexOf(c.id) >= 0; }));
    var listEl = part(cv, "fu-list", "rb-list", "left:28px;top:" + Y(103) + "px");
    listEl.setAttribute("data-scroll", "fuse");
    var max = picked.length === 3 ? 3 : 5;
    for (var r = 0; r < 5; r++) {
      var c = r < max ? rows[r + (picked.length ? 0 : f.scroll || 0)] : null, el = part(listEl, "fu-r" + r, "rwrap", "top:" + r * ROW + "px");
      var on = c && f.pick.indexOf(c.id) >= 0;
      patch(el, c ? cardKey(c) + on + (best && best.id) : "-", c ? (function (c, on) {
        return function () {
          var isBest = on && best && (c.traits || []).some(function (t) { return t === best; });
          return MC.row(c, {check: on, sel: on, attrs: ' data-fupick="' + c.id + '"', best: isBest ? best.id : null, traitX: 296, glyphX: 246,
            right: on ? MC.cols([{r: 16, text: isBest ? "best trait · kept" : "used up", colour: isBest ? MC.TEMPER : "#626569"}]) : ""});
        };
      })(c, on) : "");
    }
    if (!groups.length) patch(part(listEl, "fu-r0", "rwrap", "top:0"), "none", '<div class="rw-empty">No three copies of one perk and rarity in your binder yet.</div>');
    var full = picked.length === 3, p0 = picked[0];
    patch(part(cv, "fu-arrow", "fu-arrow", full ? "" : "display:none"), String(full), '<svg width="16" height="22" viewBox="0 0 16 22"><path d="M8 1v19M1.5 13.5L8 20l6.5-6.5" stroke="#9A9DA1" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>');
    var fees = M().cards.costs.fees, fee = null;
    if (p0) for (var q = 0; q < fees.length; q++) if (p0.needs >= fees[q].needsFrom && p0.needs <= fees[q].needsTo) fee = fees[q];
    var coin = M().cards.sitting.coin;
    patch(part(cv, "fu-result", "fu-result", full ? "" : "display:none"), full ? JSON.stringify([f.pick, best && best.id]) : "-", full ? function () {
      var nr = NEXT_RARITY[p0.rarity] || "epic", rc = MC.RARITY[nr];
      return '<div class="fr-b" style="border-color:' + MC.rgba(rc, 0.6) + '"></div><div class="fr-tile" style="background:' + MC.shade(p0.colour) + '">' + MC.img(p0.icon, 64) + '</div><div class="fr-gem" style="background:' + rc + '"></div>' +
        '<div class="fr-eb" style="color:' + rc + '">' + esc(upper(p0.baseName)) + '</div><div class="fr-t">' + esc(upper(nr.charAt(0)) + nr.slice(1) + " · " + (p0.tierName || "I") + " · " + kwNames(p0) + " + one rolled keyword") + "</div>" +
        '<div class="fr-k" style="left:138px">Keeps</div><div class="fr-v tb" style="left:138px">' + esc(best ? best.name + " " + best.valueText : "nothing") + "</div>" +
        '<div class="fr-k" style="left:328px">Rolls</div><div class="fr-v" style="left:328px">1–2 new traits, top two thirds</div>' +
        '<div class="fr-k" style="left:518px">Needs</div><div class="fr-v" style="left:518px">' + esc(String(p0.needsText || "").replace(/^Needs /, "")) + "</div>" +
        '<div class="fr-k" style="left:708px">History</div><div class="fr-v" style="left:708px">Fused from three</div>';
    } : "");
    patch(part(cv, "fu-bar", "tbar", "top:" + Y(469) + "px;height:96px" + (full ? "" : ";display:none")), JSON.stringify([full, fee, coin]), full ? '<div class="eb9" style="left:19px;top:15px">COST</div><div class="tb-big" style="top:33px;font-size:14px">The three ' +
      esc((p0.rarityName || "") + " " + p0.baseName) + ' cards are used up</div><div class="tb-note" style="top:59px">' + esc(fee ? "Fee: " + (coin ? fee.bits + " bits" : bits(fee.emeralds, false)) + " (the result's rebuild fee). Epic triples don't fuse; slotted copies need a rebuild first." : "") +
      '</div><div class="tb-go" data-fuse="1" style="top:27px">Fuse three</div>' : "");
  }

  function releaseTab() {
    shrineClear("rl");
    var r = st.release, all = cards(), picked = [];
    for (var i = 0; i < r.pick.length; i++) if (byId[r.pick[i]] && byId[r.pick[i]].place === "binder") picked.push(byId[r.pick[i]]);
    r.pick = picked.map(function (c) { return c.id; });
    var sk = picked.length ? picked[0].skill : null, rows = [];
    for (var k = 0; k < all.length; k++) if (all[k].place === "binder" && !MC.isMythic(all[k]) && (!sk || all[k].skill === sk)) rows.push(all[k]);
    patch(part(cv, "rl-head", "fu-head"), JSON.stringify([r.pick, rows.length]), '<div class="eb9" style="left:28px;top:' + Y(75) + 'px">THREE BINDER CARDS OF ONE SKILL → ONE REROLL</div><div class="fu-count">' +
      (picked.length ? esc(picked[0].skillName + " · " + picked.length + " of 3 picked") : "Rerolls: " + (M().draft ? M().draft.rerolls : 0) + " of " + (M().draft ? M().draft.maxRerolls : 5)) + "</div>");
    var listEl = part(cv, "rl-list", "rb-list", "left:28px;top:" + Y(103) + "px");
    listEl.setAttribute("data-scroll", "release");
    r.scroll = Math.max(0, Math.min(r.scroll || 0, Math.max(0, rows.length - 5)));
    for (var j = 0; j < 5; j++) {
      var c = rows[j + r.scroll], el = part(listEl, "rl-r" + j, "rwrap", "top:" + j * ROW + "px"), on = c && r.pick.indexOf(c.id) >= 0;
      patch(el, c ? cardKey(c) + on : "-", c ? (function (c, on) { return function () { return MC.row(c, {check: on, sel: on, attrs: ' data-rlpick="' + c.id + '"', traitX: 296, glyphX: 246}); }; })(c, on) : "");
    }
    var full = picked.length === 3;
    patch(part(cv, "rl-bar", "tbar", "top:" + Y(445) + "px;height:120px"), JSON.stringify([r.pick]), '<div class="eb9" style="left:19px;top:17px">' + picked.length + ' SELECTED</div><div class="tb-big" style="top:35px">' +
      (full ? "Three " + esc(picked[0].skillName) + " cards → one reroll" : "Pick three cards of one skill") + '</div><div class="tb-note" style="top:65px">Released cards are gone for good. A reroll redeals a whole draft hand (Redeal all).</div><div class="tb-go' + (full ? "" : " off") +
      '" data-release="1" style="top:39px">Release three</div>');
  }

  // ---- the Card Shrine away from one: what it does, and that it opens here when used ----------------------------
  SCREENS["shrine-info"] = function () {
    head('<div class="hd-chip plain back" data-nav="home"><span class="nm">Card Shrine</span></div>',
      '<div class="hd-tabs" style="width:230px"><div class="hd-tab off" style="left:2px;width:72px">Rebuild</div><div class="hd-tab off" style="left:77px;width:72px">Fuse</div><div class="hd-tab off" style="left:152px;width:72px">Release</div></div>');
    var costs = M().cards.costs, fees = costs.fees || [], f = [];
    for (var i = 0; i < fees.length; i++) f.push("needs " + fees[i].needsFrom + "–" + fees[i].needsTo + ": " + fees[i].bits + " bits or " + bits(fees[i].emeralds, false));
    patch(part(cv, "si", "si"), JSON.stringify([fees, costs.freeSwaps, costs.lowLevel]), '<div class="t">You are not at a Card Shrine</div><div class="s">Card Shrines stand in the world. Use one and this page opens on it: rebuild a deck, fuse three copies into the next rarity, or release cards for a reroll. A trainer of a skill rebuilds and fuses that skill’s cards too.</div>' +
      '<div class="box" style="left:28px"><div class="h">Rebuild</div><div class="b">Take slotted cards out of one deck. A Draught of Forgetting opens the sitting, each card pays its fee, and a card may lose one trait (a scar). ' +
      (costs.lowLevel ? "Below character level " + costs.freeBelowLevel + " it is free." : costs.freeSwaps ? "You also hold " + costs.freeSwaps + " free swaps, usable anywhere from the deck." : "") + '</div></div>' +
      '<div class="box" style="left:348px"><div class="h">Fuse</div><div class="b">Three binder copies of one perk and rarity become one card of the next rarity. The best trait of the three is kept; one or two new traits roll.</div></div>' +
      '<div class="box" style="left:668px"><div class="h">Release</div><div class="b">Three binder cards of one skill are given up for one reroll (Redeal all in a draft). Released cards are gone for good.</div></div>' +
      '<div class="fees">Fees by requirement  ·  ' + esc(f.join("  ·  ")) + "</div>");
    footer("SLOTTING INTO AN OPEN SLOT IS FREE, ANYWHERE", "");
  };

  // ---- trade (CARDS.md §8.1, Figma 2h-4) --------------------------------------------------------------------------
  // Each side's offered cards sit in a list of three rows (103 to 271) that scrolls: up to nine a side, and the ITEMS
  // label (301), the wells (319) and the coin (381) below it never move or get covered. With three or more offered,
  // "+ Add a card" is a small pill under the list, beside the "4-6 of 9 · scroll" note.
  var TR_ROWS = 3, TR_PITCH = 58, TR_TOP = 103;
  function tradeScroll(side, mine) {
    var n = (side.cards || []).length, max = Math.max(0, n - TR_ROWS), k = mine ? "meScroll" : "themScroll";
    st.trade[k] = Math.max(0, Math.min(st.trade[k] || 0, max));
    return st.trade[k];
  }
  function tradeRows(side, mine, x) {
    var list = side.cards || [], n = list.length, sc = tradeScroll(side, mine);
    var h = '<div class="tr-list" id="' + (mine ? "tr-me-list" : "tr-them-list") + '" data-scroll="' + (mine ? "tme" : "tthem") + '" style="left:' + x + "px;top:" + Y(TR_TOP) + 'px">';
    for (var i = 0; i < TR_ROWS && i + sc < n; i++) {
      var c = list[i + sc], need = c.needs ? (c.skillName || "") + " " + c.needs : "";
      var lvl = skill(c.skill) ? skill(c.skill).level : 0;
      var right = MC.cols([{r: 16, text: need, sub: mine ? "" : "you have " + lvl, colour: mine ? "#9A9DA1" : c.meetsRequirement === false ? MC.AMBER : "#9A9DA1"}]);
      h += MC.row(c, {style: "left:0;top:" + i * TR_PITCH + "px;width:456px", attrs: (mine ? ' data-ttake="' + c.id + '"' : (c.warning ? ' title="' + esc(c.warning) + '"' : "")) + ' data-tcard="' + c.id + '"', glyphX: 176, traitX: 218, traitW: 140, maxTraits: 1, right: right});
    }
    h += "</div>";
    if (n > TR_ROWS) {
      h += '<div class="tr-more" style="left:' + (x + 256) + "px;top:" + Y(279) + 'px">' + (sc + 1) + "\u2013" + Math.min(n, sc + TR_ROWS) + " of " + n + "  \u00b7  scroll</div>";
    }
    return h;
  }
  // Item wells: Minecraft draws the stacks (icon and count) natively at the rects the page reports (MC.itemsSet), as
  // it draws the inventory's; the page draws only the well, and the hovered stack's name in the ITEMS line.
  function itemSlots(side, mine, x) {
    var h = "";
    for (var i = 0; i < 9; i++) {
      var it = side.items[i];
      var attrs = mine ? (it ? ' data-titem="' + i + '"' : i === side.items.length ? ' data-toffitem="1"' : "") : "";
      if (it) attrs += ' data-itid="' + esc(it.item) + '" data-itc="' + it.count + '" data-itn="' + esc(it.name + (it.count > 1 ? " ×" + it.count : "")) + '"';
      h += '<div class="it-slot' + (it ? " full" : "") + '"' + attrs + ' style="left:' + (x + i * 51) + "px;top:" + Y(319) + 'px">' +
        (!it && mine && i === side.items.length ? '<span class="plus">+</span>' : "") + "</div>";
    }
    return h;
  }
  function tradeItems() {
    var out = [];
    if (MC.ov.active()) return out;
    var els = cv.querySelectorAll(".it-slot.full"), pick = st.trade.adding ? MC.$("tr-pick") : null;
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (pick && el.getAttribute("data-titem") != null) continue;  // under the binder picker
      var p = MC.abs(el), sz = MC.size(el);
      out.push({id: el.getAttribute("data-itid"), n: +el.getAttribute("data-itc"), x: p.x, y: p.y, w: sz.w, h: sz.h});
    }
    return out;
  }
  /** The coin row: a typed amount or steps of 10 bits (and the wheel), with Hearth; else emeralds go in as items. */
  function coinRow(t, me) {
    var h = '<div class="eb9" style="left:28px;top:' + Y(381) + 'px">COIN</div>';
    if (!t.coin) return h + '<div class="coinf off" style="left:28px;top:' + Y(399) + 'px"><i></i>Emeralds as items</div><div class="coin-hint" style="top:' + Y(409) + 'px">No Hearth coin in this world: offer emeralds from your hand</div>';
    var typing = MC.textFocus && st.textTarget === "coin";
    var shown = typing ? (st.trade.coinDraft || "") : String(me.coin);
    h += '<div class="coin-step" data-tcstep="-10" style="left:28px;top:' + Y(399) + 'px">−</div>';
    h += '<div class="coinf in' + (typing ? " on" : "") + '" data-tcoinf="1" data-scroll="coin" style="left:66px;top:' + Y(399) + 'px"><i></i>' + esc(shown || "0") + (typing ? '<i class="caret"></i>' : "") + '<span class="u">bits</span></div>';
    h += '<div class="coin-step" data-tcstep="10" style="left:232px;top:' + Y(399) + 'px">+</div>';
    h += '<div class="coin-hint" style="top:' + Y(409) + 'px">' + (typing ? "Type the bits · Enter to offer · Esc to cancel" : "Click to type · ± 10 bits · or scroll") + "</div>";
    return h;
  }
  function setCoin(bits) {
    var t = M().cards.trade;
    if (!t || !t.coin) return;
    bits = Math.max(0, Math.min(999999999, Math.floor(bits) || 0));
    if (bits !== t.me.coin) MC.act("tradeCoin", String(bits));
  }
  function commitCoin() {
    if (st.trade.coinDraft != null && st.trade.coinDraft !== "") setCoin(+st.trade.coinDraft);
    st.trade.coinDraft = null;
  }

  SCREENS.trade = function () {
    var t = M().cards.trade;
    // A trade asked for from a card's Trade… opens with that card offered.
    var rq = st.trade.req;
    if (rq && (Date.now() - rq.at > 120000 || t.open && t.partner !== rq.name)) st.trade.req = rq = null;
    if (rq && t.open && rq.card && byId[rq.card] && byId[rq.card].place === "binder") { MC.act("tradeOfferCard", rq.card); st.trade.req = null; }
    head('<div class="hd-chip trade"><span class="nm">Trade · ' + esc(t.partner) + '</span><i class="dot"></i><span class="sub">max 8 blocks</span></div>', "");
    tradeScroll(t.me || {}, true);
    tradeScroll(t.them || {}, false);
    if (!t.open) {
      patch(part(cv, "tr-ask", "tr-ask"), t.partner, '<div class="t">Waiting for ' + esc(t.partner) + ' to accept…</div><div class="s">They see an Accept chip. Walking 8 blocks apart calls it off.</div><div class="tb-go" data-tcancel="1">Cancel</div>');
      MC.itemsSet([]);
      footer("", "");
      return;
    }
    var ask = MC.$("tr-ask");
    if (ask) ask.parentNode.removeChild(ask);
    var me = t.me, them = t.them;
    var lockPill = function (locked, mine, x) {
      return '<div class="lockp' + (locked ? " on" : "") + '"' + (mine && !locked ? ' data-tlock="1"' : "") + ' style="left:' + x + "px;top:" + Y(67) + 'px">' + (locked ? '<i class="tick"></i>Locked' : mine ? "Lock my side" : "Not locked") + "</div>";
    };
    var coinKey = [MC.textFocus && st.textTarget === "coin", st.trade.coinDraft];
    patch(part(cv, "tr-me", "tr-col"), JSON.stringify([me, iconsKey, st.trade.adding, t.coin, coinKey, st.trade.meScroll]), function () {
      var h = '<div class="eb9" style="left:28px;top:' + Y(75) + 'px">YOU OFFER' + (me.cards.length ? "  \u00b7  " + me.cards.length + (me.cards.length === 1 ? " CARD" : " CARDS") : "") + "</div>" + lockPill(me.locked, true, 392) + tradeRows(me, true, 28);
      var n = me.cards.length;
      if (n < (t.maxCards || 9)) {
        h += n < TR_ROWS ? '<div class="tr-add" data-tadd="1" style="top:' + Y(TR_TOP + n * TR_PITCH) + 'px">+ Add a card from your binder (up to ' + (t.maxCards || 9) + ")</div>"
          : '<div class="tr-add small" data-tadd="1" style="top:' + Y(275) + 'px">+ Add a card</div>';
      }
      h += '<div class="eb9" id="tr-me-items" style="left:28px;top:' + Y(301) + 'px">ITEMS</div>' + itemSlots(me, true, 28);
      h += coinRow(t, me);
      return h;
    });
    patch(part(cv, "tr-them", "tr-col"), JSON.stringify([them, iconsKey, t.partner, st.trade.themScroll]), function () {
      var h = '<div class="eb9" style="left:500px;top:' + Y(75) + 'px">' + esc(upper(t.partner)) + " OFFERS" + (them.cards.length ? "  \u00b7  " + them.cards.length + (them.cards.length === 1 ? " CARD" : " CARDS") : "") + "</div>" + lockPill(them.locked, false, 864) + tradeRows(them, false, 500);
      h += '<div class="eb9" id="tr-them-items" style="left:500px;top:' + Y(301) + 'px">ITEMS</div>' + itemSlots(them, false, 500);
      h += '<div class="eb9" style="left:500px;top:' + Y(381) + 'px">COIN</div><div class="coinf" style="left:500px;top:' + Y(399) + 'px"><i></i>' + (t.coin ? them.coin + " bits" : "Emeralds as items") + "</div>";
      return h;
    });
    part(cv, "tr-div", "tr-div");
    var both = me.locked && them.locked, cd = t.countdownSeconds;
    patch(part(cv, "tr-foot", "tr-foot"), JSON.stringify([both, cd, me.locked, them.locked]), function () {
      var h = '<div class="tr-note">Cards you get attune for 20 minutes: no slotting, trading or listing until then. Amber = you can\'t slot it yet.</div>';
      h += '<div class="tr-status"><i class="tick' + (both ? "" : " off") + '"></i>' + (both ? "Both sides locked" : me.locked ? "Waiting for " + esc(t.partner) + " to lock" : "Lock your side when it's right") + "</div>";
      if (cd >= 0) h += '<div class="tr-go cd">Swapping in ' + cd + '<i class="bar"><b style="width:' + Math.round(120 * (1 - cd / 3)) + 'px"></b></i></div>';
      else h += '<div class="tr-go' + (both ? "" : " off") + '"' + (both ? ' data-tconfirm="1"' : "") + ">" + (both ? "Confirm swap" : "Confirm") + "</div>";
      h += '<div class="tr-warn">Any change, 8+ blocks apart or damage cancels.</div><div class="tr-cancel" data-tcancel="1">Cancel</div>';
      return h;
    });
    // The binder picker for "+ Add a card".
    var picker = part(cv, "tr-pick", "tr-pick", st.trade.adding ? "" : "display:none");
    if (st.trade.adding) {
      var list = [], all = cards(), offered = {};
      for (var o = 0; o < me.cards.length; o++) offered[me.cards[o].id] = true;
      for (var i = 0; i < all.length; i++) if (all[i].place === "binder" && all[i].attuned !== false && !offered[all[i].id]) list.push(all[i]);
      st.trade.scroll = Math.max(0, Math.min(st.trade.scroll, Math.max(0, list.length - 6)));
      picker.setAttribute("data-scroll", "tpick");
      patch(picker, JSON.stringify([list.map(function (c) { return c.id; }), st.trade.scroll, iconsKey]), function () {
        var h = '<div class="tp-h">Add a card · ' + list.length + ' you can trade</div><div class="tp-done" data-tadd="0">Done</div>';
        for (var j = 0; j < 6; j++) {
          var c = list[j + st.trade.scroll];
          if (c) h += MC.row(c, {style: "left:12px;top:" + (40 + j * 58) + "px;width:432px", attrs: ' data-toffer="' + c.id + '"', glyphX: 176, traitX: 218, traitW: 160, maxTraits: 1});
        }
        if (!list.length) h += '<div class="rw-empty" style="top:60px">No attuned binder cards to offer.</div>';
        return h;
      });
    }
    MC.itemsSet(tradeItems());
    footer("", "");
  };

  // ---- dropdown menus (binder and deck filters and sort, the trade request list; Figma 2h-2 / 2h-3) ---------------
  // One menu at a time, drawn last in #cv under its opener (or above it when there is no room below). Options are
  // [value, label, extra]; the current one is ticked. Esc, a click outside or a pick closes it.
  var SORTS = [["newest", "Newest"], ["rarity", "Rarity"], ["requirement", "Requirement"], ["name", "Name"]];
  var RARITY_NAMES = [["common", "Common"], ["rare", "Rare"], ["epic", "Epic"], ["cursed", "Cursed"], ["mythic", "Mythic"]];
  function rarityLabel(r) { for (var i = 0; i < RARITY_NAMES.length; i++) if (RARITY_NAMES[i][0] === r) return RARITY_NAMES[i][1]; return r; }
  function sortLabel(v) { for (var i = 0; i < SORTS.length; i++) if (SORTS[i][0] === v) return SORTS[i][1]; return v; }
  function short(t, n) { t = String(t || ""); return t.length > n ? t.slice(0, n - 1) + "…" : t; }
  function ddPill(cls, id, label, on, style, open) {
    var isOpen = open || (st.menu && st.menu.id === id);
    return '<div class="' + cls + " dd-pill" + (on ? " set" : "") + (isOpen ? " open" : "") + '" data-dd="' + id + '"' + (style ? ' style="' + style + '"' : "") + ">" + esc(short(label, 16)) + '<i class="dd"></i></div>';
  }
  /** Every trait the given cards carry, by name. */
  function traitsOf(list) {
    var seen = {}, out = [];
    for (var i = 0; i < list.length; i++) for (var k = 0; k < (list[i].traits || []).length; k++) {
      var tr = list[i].traits[k];
      if (!seen[tr.id]) { seen[tr.id] = true; out.push([tr.id, tr.name]); }
    }
    return out.sort(function (a, b) { return a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0; });
  }
  function traitName(id) { var all = traitsOf(cards()); for (var i = 0; i < all.length; i++) if (all[i][0] === id) return all[i][1]; return id; }
  function deckCards() { var s = skill(nav.skill), out = [], all = cards(); for (var i = 0; i < all.length; i++) if (s && all[i].place === "binder" && all[i].skill === s.id) out.push(all[i]); return out; }
  function binderCards() { var sk = st.binder.skill || (screen === "skill-cards" ? nav.skill : ""), all = cards(), out = []; for (var i = 0; i < all.length; i++) if (!sk || all[i].skill === sk) out.push(all[i]); return out; }
  function menuSpec(id) {
    var b = st.binder, d = st.deck, kws = [["", "Any keyword"]], k = M().cards.keywords, i;
    for (i = 0; i < k.length; i++) kws.push([k[i].id, k[i].name, MC.glyph(k[i], 16)]);
    var rar = [["", "Any rarity"]];
    for (i = 0; i < RARITY_NAMES.length; i++) rar.push([RARITY_NAMES[i][0], RARITY_NAMES[i][1], MC.gem(RARITY_NAMES[i][0], 7)]);
    switch (id) {
      case "b-rarity": return {title: "Rarity", opts: rar, cur: b.rarity};
      case "b-kw": return {title: "Keyword", opts: kws, cur: b.kw};
      case "b-trait": return {title: "Trait", opts: [["", "Any trait"]].concat(traitsOf(binderCards())), cur: b.trait};
      case "b-sort": return {title: "Sort by", opts: SORTS, cur: b.sort};
      case "d-rarity": return {title: "Rarity", opts: rar, cur: d.rarity};
      case "d-kw": return {title: "Keyword", opts: kws, cur: d.kw};
      case "d-trait": return {title: "Trait", opts: [["", "Any trait"]].concat(traitsOf(deckCards())), cur: d.trait};
      case "d-sort": return {title: "Sort by", opts: SORTS, cur: d.sort};
      case "sound": return {custom: soundPanel, w: 220, h: 168, opts: []};
      case "focus": case "bynames": return MC.bf ? MC.bf.menuSpec(id) : {opts: []};
      case "trade-req":
        var rows = [];
        for (i = 0; i < MC.near.length; i++) rows.push([MC.near[i].name, MC.near[i].name, '<span class="ddo-d">' + Math.max(1, Math.round(MC.near[i].d)) + " m</span>"]);
        return {title: "Request a trade · within 8 blocks", opts: rows, cur: null, empty: "Nobody within 8 blocks. Stand next to the player you want to trade with.", wide: true};
    }
    return {opts: []};
  }
  function menuPick(id, v) {
    var b = st.binder, d = st.deck;
    switch (id) {
      case "b-rarity": b.rarity = v; b.scroll = 0; break;
      case "b-kw": b.kw = v; b.scroll = 0; break;
      case "b-trait": b.trait = v; b.scroll = 0; break;
      case "b-sort": b.sort = v; break;
      case "d-rarity": d.rarity = v; d.scroll = 0; break;
      case "d-kw": d.kw = v; d.scroll = 0; break;
      case "d-trait": d.trait = v; d.scroll = 0; break;
      case "d-sort": d.sort = v; break;
      case "focus": case "bynames": if (MC.bf) MC.bf.menuPick(id, v); break;
      case "trade-req":
        MC.act("tradeRequest", v);
        st.trade.req = {name: v, at: Date.now(), card: st.menu && st.menu.card || null};
        break;
    }
  }
  var MENU_ROWS = 9, MENU_ROW = 26;
  function menuHeight(count) { return count ? 34 + Math.min(MENU_ROWS, count) * MENU_ROW + 6 : 34 + 44; }
  function openMenu(id, el) {
    var p = MC.abs(el), o = MC.abs(cv), spec = menuSpec(id);
    var w = spec.w || (spec.wide ? 260 : Math.max(el.offsetWidth, 176));
    var h = spec.h || menuHeight(spec.opts.length);
    var x = Math.max(12, Math.min(987 - w - 12, spec.w ? p.x - o.x + el.offsetWidth - w : p.x - o.x)), below = p.y - o.y + el.offsetHeight + 4, above = p.y - o.y - h - 4;
    st.menu = {id: id, x: x, y: below + h <= 618 || above < 30 ? below : above, w: w, scroll: 0, card: el.getAttribute("data-ddcard")};
    if (st.menu.y + h > 622) st.menu.y = Math.max(30, 622 - h);
    ui();
  }
  function closeMenu() {
    if (!st.menu) return;
    st.menu = null;
    ui();
  }
  function menu() {
    var m = st.menu, spec = m ? menuSpec(m.id) : null, opts = spec ? spec.opts : [], n = Math.min(MENU_ROWS, opts.length);
    var el = part(cv, "c-menu", "ddm", m ? "left:" + m.x + "px;top:" + m.y + "px;width:" + m.w + "px;height:" + (spec.h || menuHeight(opts.length)) + "px" : "display:none");
    if (!m) { patch(el, "", ""); return; }
    if (spec.custom) {
      el.setAttribute("data-scroll", "menu");
      el.setAttribute("data-ddm", "1");
      var html = spec.custom();
      patch(el, html, html);
      return;
    }
    m.scroll = Math.max(0, Math.min(m.scroll, opts.length - n));
    el.setAttribute("data-scroll", "menu");
    el.setAttribute("data-ddm", "1");
    patch(el, JSON.stringify([m, opts.map(function (o) { return o[0] + o[1]; }), spec.cur, iconsKey]), function () {
      var h = '<div class="ddm-h">' + esc(spec.title || "") + (opts.length > MENU_ROWS ? '<span class="ddm-n">' + (m.scroll + 1) + "–" + (m.scroll + n) + " of " + opts.length + "</span>" : "") + "</div>";
      for (var i = 0; i < n; i++) {
        var o = opts[i + m.scroll], on = spec.cur === o[0];
        h += '<div class="ddo' + (on ? " on" : "") + '" data-ddv="' + esc(o[0]) + '" style="top:' + (34 + i * MENU_ROW) + 'px">' + (o[2] && m.id !== "trade-req" ? '<span class="ddo-i">' + o[2] + "</span>" : "") +
          '<span class="ddo-l">' + esc(o[1]) + "</span>" + (m.id === "trade-req" ? o[2] : "") + (on ? '<i class="tick"></i>' : "") + "</div>";
      }
      if (!opts.length) h += '<div class="ddm-e">' + esc(spec.empty || "Nothing to choose") + "</div>";
      return h;
    });
  }
  /** Players in trade reach, from Java (HubInventoryScreen.pushNearby). */
  MC.nearby = function (list) {
    MC.near = list || [];
    if (st.menu && st.menu.id === "trade-req") MC.render();
  };

  // ---- input --------------------------------------------------------------------------------------------------
  function closest(t, attr) {
    while (t && t !== document.body) {
      if (t.getAttribute && t.getAttribute(attr) != null) return t;
      t = t.parentNode;
    }
    return null;
  }

  function bestSlot(c) {
    var d = deck(MC.isMythic(c) ? (nav.skill || c.deck) : c.skill);
    if (!d) return -1;
    for (var i = 0; i < d.row.length; i++) if (d.row[i].open && !d.row[i].card) return i;
    return -1;
  }
  function slotCard(c, slot, deckId) {
    justSlotted = c.id;
    slotSoundFor = c.id;
    if (MC.isMythic(c)) MC.act("slotCard", c.id + "#" + (slot == null ? -1 : slot) + "#" + (deckId || nav.skill || c.deck));
    else MC.act("slotCard", slot == null || slot < 0 ? c.id : c.id + "#" + slot);
    MC.sound("slot", "");
  }

  /** Clicks on the cards screens (capture phase: mettle.js's own handler never sees them). */
  function onClick(ev) {
    if (!MC.attached || kind !== "classes" || !M() || !M().cards) return;
    var t = ev.target, el, v;
    // The guide's chip sits over everything, a draft included: its ×, Skip and buttons answer first.
    if (MC.bf && MC.bf.guideClick(t)) { ev.stopPropagation(); ev.preventDefault(); MC.render(); return; }
    if (MC.ov.active()) {
      if (MC.ov.click(t)) { ev.stopPropagation(); ev.preventDefault(); }
      return;
    }
    if (dragJustEnded) { dragJustEnded = false; ev.stopPropagation(); return; }
    if (!kbdClick && focus.key) focus.key = null;  // the mouse took over: the focus ring goes
    var handled = true;
    if (st.menu) {
      // An open menu takes the click: a pick, its own opener (toggles it shut), or anywhere else (just closes it).
      var opt = closest(t, "data-ddv"), opener = closest(t, "data-dd"), id = st.menu.id, sv;
      if (id === "sound" && closest(t, "data-sndtoggle")) { sv = sound(); setSound(!sv.enabled, sv.volume); ev.stopPropagation(); MC.render(); return; }
      if (id === "sound" && (el = closest(t, "data-sndvol"))) {
        var r = el.getBoundingClientRect();
        sv = sound();
        setSound(true, (MC.ex(ev) - r.left) / Math.max(1, r.width) * 100);
        ev.stopPropagation();
        MC.render();
        return;
      }
      if (MC.bf && MC.bf.menuClick(id, t)) { ev.stopPropagation(); MC.render(); return; }
      if (opt) menuPick(id, opt.getAttribute("data-ddv"));
      if (opt || !closest(t, "data-ddm") || opener) closeMenu();
      ev.stopPropagation();
      MC.render();
      return;
    }
    if (MC.textFocus && !closest(t, st.textTarget === "coin" ? "data-tcoinf" : "data-search")) {
      if (st.textTarget === "coin") commitCoin();
      MC.textFocus = false;
      ui();
    }
    if (MC.bf && MC.bf.click(t)) { /* build feel's own controls */ }
    else if ((el = closest(t, "data-taccept"))) { MC.act("tradeAccept", ""); hideToast(); }
    else if ((el = closest(t, "data-dd"))) { openMenu(el.getAttribute("data-dd"), el); }
    else if ((el = closest(t, "data-tcstep"))) { var tt = M().cards.trade; if (tt && tt.coin) setCoin(tt.me.coin + (+el.getAttribute("data-tcstep"))); }
    else if ((el = closest(t, "data-tcoinf"))) { if (!MC.textFocus) { MC.textFocus = true; st.textTarget = "coin"; st.trade.coinDraft = ""; ui(); } }
    else if ((el = closest(t, "data-tab"))) { nav.view = "home"; nav.tab = el.getAttribute("data-tab"); }
    else if ((el = closest(t, "data-nav"))) { nav.view = "home"; nav.tab = "skills"; }
    else if ((el = closest(t, "data-deal"))) { MC.ov.draft(el.getAttribute("data-deal")); }
    else if ((el = closest(t, "data-deal-any"))) { var l = skills(); for (var i = 0; i < l.length; i++) if (l[i].drafts > 0) { MC.ov.draft(l[i].id); break; } }
    else if ((el = closest(t, "data-stab"))) { nav.stab = el.getAttribute("data-stab"); }
    else if ((el = closest(t, "data-shtab"))) { nav.shrine = el.getAttribute("data-shtab"); }
    else if (screen === "skill-tree" && (el = closest(t, "data-perk"))) { MC.treeSel = el.getAttribute("data-perk"); if (window.hubMettle) window.hubMettle.treeSelect(MC.treeSel); handled = true; }
    else if ((el = closest(t, "data-skill"))) { nav.view = "skill"; nav.skill = el.getAttribute("data-skill"); if (screen !== "skill-tree") nav.stab = "deck"; st.deck.sel = null; st.deck.pop = null; st.deck.scroll = 0; }
    else if ((el = closest(t, "data-bskill")) != null && el) { st.binder.skill = el.getAttribute("data-bskill"); st.binder.scroll = 0; }
    else if ((el = closest(t, "data-bf"))) {
      v = el.getAttribute("data-bf");
      if (v === "slot") st.binder.slot = !st.binder.slot;
      if (v === "att") st.binder.att = !st.binder.att;
      if (v === "sets") st.binder.sets = !st.binder.sets;
      st.binder.scroll = 0;
    }
    else if ((el = closest(t, "data-shrineinfo"))) { nav.view = "shrine-info"; }
    else if ((el = closest(t, "data-search"))) { MC.textFocus = true; st.textTarget = "search"; ui(); }
    else if ((el = closest(t, "data-bcard"))) { v = el.getAttribute("data-bcard"); st.binder.sel = st.binder.sel === v ? null : v; }
    else if ((el = closest(t, "data-df"))) {
      v = el.getAttribute("data-df");
      if (v === "slot") st.deck.slot = !st.deck.slot;
      st.deck.scroll = 0;
    }
    else if ((el = closest(t, "data-dcard"))) { v = el.getAttribute("data-dcard"); st.deck.sel = st.deck.sel === v ? null : v; st.deck.pop = null; }
    else if ((el = closest(t, "data-scard"))) { v = el.getAttribute("data-scard"); st.deck.pop = st.deck.pop === v ? null : v; st.deck.sel = null; }
    else if ((el = closest(t, "data-slot")) && st.deck.sel && byId[st.deck.sel]) { slotCard(byId[st.deck.sel], +el.getAttribute("data-slot")); st.deck.sel = null; }
    else if ((el = closest(t, "data-unslot"))) { MC.act("unslotCards", el.getAttribute("data-unslot")); st.deck.pop = null; }
    else if ((el = closest(t, "data-popclose"))) { st.deck.pop = null; }
    else if ((el = closest(t, "data-goshrine"))) { nav.shrine = el.getAttribute("data-goshrine"); st.deck.pop = null; if (!M().cards.sitting) nav.view = "shrine-info"; }
    else if ((el = closest(t, "data-takeout"))) {
      var tc = byId[el.getAttribute("data-takeout")];
      if (tc) { nav.view = "skill"; nav.skill = tc.deck; nav.stab = "deck"; st.deck.pop = tc.id; }
    }
    else if ((el = closest(t, "data-slotbest"))) { var sc = byId[el.getAttribute("data-slotbest")]; if (sc) slotCard(sc, bestSlot(sc)); }
    else if ((el = closest(t, "data-totals"))) { st.deck.totals = !st.deck.totals; }
    else if ((el = closest(t, "data-pin"))) {
      var pins = M().cards.pins, id = el.getAttribute("data-pin"), list = pins.keywords.slice(), at = list.indexOf(id);
      if (pins.canChange) { if (at >= 0) list.splice(at, 1); else if (list.length < pins.max) list.push(id); MC.act("pinSets", list.join(",")); }
    }
    else if ((el = closest(t, "data-pinauto"))) { if (M().cards.pins.canChange && M().cards.pins.keywords.length) MC.act("pinSets", ""); }
    else if ((el = closest(t, "data-rbskill"))) { st.rebuild.skill = el.getAttribute("data-rbskill"); st.rebuild.pick = {}; }
    else if ((el = closest(t, "data-rbpick"))) { v = el.getAttribute("data-rbpick"); if (st.rebuild.pick[v]) delete st.rebuild.pick[v]; else st.rebuild.pick[v] = true; }
    else if ((el = closest(t, "data-rebuild"))) { var ids = Object.keys(st.rebuild.pick); if (ids.length) { MC.act("rebuildCards", ids.join(",")); st.rebuild.pick = {}; } }
    else if ((el = closest(t, "data-fupick"))) { toggleTrio(st.fuse, el.getAttribute("data-fupick"), function (a, b) { return a.perk === b.perk && a.rarity === b.rarity; }); }
    else if ((el = closest(t, "data-fuse"))) { if (st.fuse.pick.length === 3) { MC.act("fuseCards", st.fuse.pick.join(",")); st.fuse.pick = []; } }
    else if ((el = closest(t, "data-rlpick"))) { toggleTrio(st.release, el.getAttribute("data-rlpick"), function (a, b) { return a.skill === b.skill; }); }
    else if ((el = closest(t, "data-release"))) { if (st.release.pick.length === 3) { MC.act("releaseCards", st.release.pick.join(",")); st.release.pick = []; } }
    else if ((el = closest(t, "data-closesit"))) { MC.act("closeSitting", ""); }
    else if ((el = closest(t, "data-tlock"))) { MC.act("tradeLock", ""); }
    else if ((el = closest(t, "data-tconfirm"))) { MC.act("tradeConfirm", ""); }
    else if ((el = closest(t, "data-tcancel"))) { MC.act("tradeCancel", ""); st.trade.adding = false; }
    else if ((el = closest(t, "data-tadd"))) { st.trade.adding = el.getAttribute("data-tadd") === "1"; }
    else if ((el = closest(t, "data-toffer"))) { MC.act("tradeOfferCard", el.getAttribute("data-toffer")); }
    else if ((el = closest(t, "data-ttake"))) { MC.act("tradeTakeBackCard", el.getAttribute("data-ttake")); }
    else if ((el = closest(t, "data-titem"))) { MC.act("tradeTakeBackItem", el.getAttribute("data-titem")); }
    else if ((el = closest(t, "data-toffitem"))) { MC.act("tradeOfferItem", "-1"); }
    else if (closest(t, "data-attr")) handled = false;  // mettle.js places attribute points
    else handled = false;
    if (handled) {
      ev.stopPropagation();
      MC.render();
    }
  }

  function toggleTrio(sel, id, same) {
    var at = sel.pick.indexOf(id), c = byId[id];
    if (!c) return;
    if (at >= 0) { sel.pick.splice(at, 1); return; }
    if (sel.pick.length && !same(byId[sel.pick[0]], c)) sel.pick = [];
    if (sel.pick.length < 3) sel.pick.push(id);
  }

  // ---- drag a card into a deck slot (Figma anatomy: "Drag: -5° tilt; the drop slot pulses 1 s") ----------------
  var press = null, dragJustEnded = false;
  function onDown(ev) {
    if (!MC.attached || kind !== "classes" || MC.ov.active() || screen !== "skill-deck") return;
    var el = closest(ev.target, "data-dcard") || closest(ev.target, "data-scard");
    if (!el) return;
    var id = el.getAttribute("data-dcard") || el.getAttribute("data-scard");
    var r = el.getBoundingClientRect();
    press = {id: id, x: MC.ex(ev), y: MC.ey(ev), ox: MC.ex(ev) - r.left, oy: MC.ey(ev) - r.top, from: el.getAttribute("data-scard") ? "slot" : "binder"};
  }
  function onMove(ev) {
    if (MC.bf && MC.attached && kind === "classes" && !ev.buttons) MC.bf.hover(ev.target);
    if (MC.ov.kind() === "draft") { MC.ov.hover(ev.target); return; }
    if (!press) {
      // Only the Classes page has card hovers; any other page's moves (and every drag) pass through untouched.
      if (MC.attached && kind === "classes" && !ev.buttons) { hoverFrom(ev.target); itemHover(ev.target); }
      else if (hoverEl) setHover(null);
      return;
    }
    var d = st.deck.drag;
    if (!d) {
      if (Math.abs(MC.ex(ev) - press.x) + Math.abs(MC.ey(ev) - press.y) < 5) return;
      var c = byId[press.id];
      if (!c) { press = null; return; }
      st.deck.drag = d = {id: press.id, from: press.from};
      st.deck.sel = null;
      st.deck.pop = null;
      var ghost = part(root, "c-ghost", "ghost");
      ghost.innerHTML = MC.ccard(c, {});
      MC.render();
    }
    var g = MC.$("c-ghost"), win = MC.$("win");
    var wp = MC.win(), wx = wp.x, wy = wp.y;
    if (g) g.style.transform = "translate(" + Math.round(MC.ex(ev) - wx - press.ox) + "px," + Math.round(MC.ey(ev) - wy - press.oy) + "px) rotate(-5deg)";
    var over = slotUnder(MC.ex(ev), MC.ey(ev));
    if (over !== d.over) {
      var row = MC.$("dk-row");
      if (row) {
        var old = MC.$("dk-s" + d.over), now = MC.$("dk-s" + over);
        if (old) MC.toggle(old, "over", false);
        if (now) MC.toggle(now, "over", true);
      }
      d.over = over;
    }
  }
  var itemShown = null;
  function itemHover(target) {
    if (screen !== "trade") return;
    var el = closest(target, "data-itn");
    if (el === itemShown) return;
    var me = MC.$("tr-me-items"), them = MC.$("tr-them-items");
    MC.text(me, "ITEMS");
    MC.text(them, "ITEMS");
    itemShown = el;
    if (el) MC.text(el.getAttribute("data-titem") != null ? me : them, "ITEMS  ·  " + upper(el.getAttribute("data-itn")));
  }
  function slotUnder(x, y) {
    var row = MC.$("dk-row");
    if (!row) return -1;
    for (var i = 0; i < row.children.length; i++) {
      var r = row.children[i].getBoundingClientRect();
      if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom && row.children[i].querySelector("[data-slot]")) return i;
    }
    return -1;
  }
  function onUp(ev) {
    var d = st.deck.drag;
    press = null;
    if (!d) return;
    st.deck.drag = null;
    dragJustEnded = true;
    setTimeout(function () { dragJustEnded = false; }, 50);
    var g = MC.$("c-ghost");
    if (g) g.parentNode.removeChild(g);
    var c = byId[d.id], s = skill(nav.skill), dk = deck(nav.skill);
    if (c && d.over >= 0 && dk && dk.row[d.over] && dk.row[d.over].open && !dk.row[d.over].card && d.from === "binder") slotCard(c, d.over);
    else if (c && d.from === "slot" && d.over < 0) { st.deck.pop = c.id; }
    MC.render();
  }

  // ---- keys and wheel from Java ----------------------------------------------------------------------------------
  MC.key = function (m) {
    if (MC.ov.key(m.key)) return;
    if (st.menu) {
      if (m.key === "Escape") { closeMenu(); focus.key = null; MC.render(); }
      else if (NAV[m.key]) navigate(m.key);
      return;
    }
    if (MC.textFocus && st.textTarget === "coin") {
      var d = st.trade.coinDraft || "";
      if (m.ch && /^[0-9]$/.test(m.ch)) d = (d + m.ch).replace(/^0+(?=\d)/, "").slice(0, 9);
      else if (m.key === "Backspace") d = d.slice(0, -1);
      else if (m.key === "Enter") { st.trade.coinDraft = d; commitCoin(); MC.textFocus = false; ui(); MC.render(); return; }
      else if (m.key === "Escape") { st.trade.coinDraft = null; MC.textFocus = false; ui(); MC.render(); return; }
      st.trade.coinDraft = d;
      MC.render();
      return;
    }
    if (MC.textFocus) {
      var b = st.binder;
      if (m.ch) b.q = (b.q + m.ch).slice(0, 40);
      else if (m.key === "Backspace") b.q = b.q.slice(0, -1);
      else if (m.key === "Escape" || m.key === "Enter") { MC.textFocus = false; ui(); }
      b.scroll = 0;
      MC.render();
      return;
    }
    if (MC.bf && MC.bf.key(m.key)) return;
    if (m.key === "Escape") {
      // The top popover first, then the keyboard focus; with none of them up Java closes the window (not modal).
      if (MC.bf && MC.bf.escape()) { /* a build feel step went back */ }
      else if (st.trade.adding) st.trade.adding = false;
      else if (st.deck.pop) st.deck.pop = null;
      else if (focus.key) focus.key = null;
      MC.render();
      return;
    }
    if (NAV[m.key]) navigate(m.key);
  };

  // ---- keyboard focus: arrows (and Tab once a focus is up) move a ring over the screen's controls; Enter clicks ------
  var NAV = {Up: 1, Down: 1, Left: 1, Right: 1, Tab: 1, ShiftTab: 1, Enter: 1};
  var FOCUS_ATTRS = ["data-ddv", "data-bcard", "data-dcard", "data-scard", "data-slot", "data-skill", "data-deal", "data-perk", "data-slotbest", "data-takeout", "data-toffer",
    "data-ttake", "data-tlock", "data-tconfirm", "data-tcancel", "data-tadd", "data-titem", "data-toffitem", "data-tcstep", "data-taccept", "data-unslot", "data-popclose",
    "data-goshrine", "data-rbskill", "data-rbpick", "data-rebuild", "data-fupick", "data-fuse", "data-rlpick", "data-release", "data-closesit", "data-shtab",
    "data-tab", "data-stab", "data-bskill", "data-bf", "data-df", "data-dd", "data-search", "data-pin", "data-totals",
    "data-boon", "data-bchoice", "data-bcardpick", "data-bnext", "data-bback", "data-btrait", "data-bkw", "data-bconfirm", "data-takebyname", "data-keepmine",
    "data-sigdeck", "data-forgepick", "data-forge", "data-legend", "data-gbtn", "data-gskip", "data-gx", "data-setsig"];
  var FOCUS_SEL = FOCUS_ATTRS.map(function (a) { return "[" + a + "]"; }).join(",");
  // Where a screen's focus starts: its cards, then its main action.
  var PRIMARY = "[data-bcard],[data-dcard],[data-scard],[data-skill],[data-rbpick],[data-fupick],[data-rlpick],[data-ttake],[data-tlock],[data-toffer],[data-perk]";
  var focus = {key: null}, kbdClick = false;
  function focusKey(el) {
    for (var i = 0; i < FOCUS_ATTRS.length; i++) if (el.hasAttribute(FOCUS_ATTRS[i])) return "[" + FOCUS_ATTRS[i] + '="' + el.getAttribute(FOCUS_ATTRS[i]) + '"]';
    return null;
  }
  function shown(el) {
    if (!el.offsetWidth || !el.offsetHeight || el.classList.contains("off")) return false;
    for (var p = el; p && p !== cv; p = p.parentNode) if (p.style && p.style.display === "none") return false;
    return true;
  }
  function focusables() {
    var scope = st.trade.adding && MC.$("tr-pick") ? MC.$("tr-pick") : st.deck.pop && MC.$("dk-pop") && MC.$("dk-pop").firstChild ? MC.$("dk-pop") : st.menu ? MC.$("c-menu") : cv;
    var all = scope ? scope.querySelectorAll(st.menu ? "[data-ddv]" : FOCUS_SEL) : [], out = [];
    for (var i = 0; i < all.length; i++) if (shown(all[i]) && (st.menu || !closest(all[i].parentNode, "data-scard") || all[i].hasAttribute("data-scard"))) out.push(all[i]);
    return out;
  }
  function focused() {
    if (!focus.key || !cv) return null;
    var el = cv.querySelector(focus.key) || (root && root.querySelector(focus.key));
    return el && shown(el) ? el : null;
  }
  MC.focusKey = function () { return focus.key; };
  function centre(el) { var r = el.getBoundingClientRect(); return {x: r.left + r.width / 2, y: r.top + r.height / 2, r: r}; }
  function towards(from, list, dir) {
    var a = centre(from), best = null, score = 1e9;
    for (var i = 0; i < list.length; i++) {
      if (list[i] === from) continue;
      var b = centre(list[i]), dx = b.x - a.x, dy = b.y - a.y;
      var along = dir === "Right" ? dx : dir === "Left" ? -dx : dir === "Down" ? dy : -dy;
      var across = dir === "Right" || dir === "Left" ? Math.abs(dy) : Math.abs(dx);
      if (along <= 4) continue;
      var sc = along + across * 2;
      if (sc < score) { score = sc; best = list[i]; }
    }
    return best;
  }
  function nearestTo(rect, list) {
    var best = null, d = 1e9;
    for (var i = 0; i < list.length; i++) {
      var c = centre(list[i]), dd = Math.abs(c.x - (rect.left + rect.width / 2)) + Math.abs(c.y - (rect.top + rect.height / 2));
      if (dd < d) { d = dd; best = list[i]; }
    }
    return best;
  }
  function navigate(k) {
    var list = focusables(), cur = focused();
    if (!list.length) return;
    if (k === "Enter") {
      if (!cur) return;
      var at0 = centre(cur).r;
      kbdClick = true;
      try { cur.click(); } finally { kbdClick = false; }
      if (!focused()) { var near = nearestTo(at0, focusables()); focus.key = near ? focusKey(near) : null; }
      MC.render();
      return;
    }
    var next = null;
    if (!cur) {
      for (var i = 0; i < list.length && !next; i++) if (list[i].matches && list[i].matches(PRIMARY)) next = list[i];
      next = next || list[0];
    } else if (k === "Tab" || k === "ShiftTab") {
      var order = list.slice().sort(function (a, b) { var A = centre(a), B = centre(b); return Math.abs(A.y - B.y) > 8 ? A.y - B.y : A.x - B.x; });
      var at = order.indexOf(cur);
      next = order[(at + (k === "Tab" ? 1 : order.length - 1)) % order.length];
    } else {
      // Inside a scrolling list the ring stays in the list: at its edge the list moves a row and the ring stays put.
      var box = closest(cur, "data-scroll");
      if (box) {
        next = towards(cur, list.filter(function (e) { return box.contains(e); }), k);
        if (!next && (k === "Up" || k === "Down")) {
          var before = centre(cur).r, which = box.getAttribute("data-scroll"), was = JSON.stringify(st);
          scrollBox(which, k === "Down" ? 1 : -1);
          MC.render();
          if (JSON.stringify(st) !== was) next = nearestTo(before, focusables().filter(function (e) { var b2 = closest(e, "data-scroll"); return b2 && b2.getAttribute("data-scroll") === which; }));
        }
      }
      if (!next) next = towards(cur, list, k);
    }
    if (next) focus.key = focusKey(next);
    MC.render();
  }
  /** The ring around the focused control (drawn by the page, over the screen). */
  function ring() {
    var el = part(cv, "c-focus", "c-focus"), f = focused();
    if (focus.key && !f) focus.key = null;
    if (!f) { if (el.style.display !== "none") { el.style.display = "none"; el.__s = null; } return; }
    var r = f.getBoundingClientRect(), o = cv.getBoundingClientRect();
    var st2 = "display:block;left:" + (r.left - o.left - 3) + "px;top:" + (r.top - o.top - 3) + "px;width:" + (r.width + 6) + "px;height:" + (r.height + 6) + "px";
    if (el.__s !== st2) { el.__s = st2; el.setAttribute("style", st2); }
    if (el.parentNode !== cv || el !== cv.lastChild) cv.appendChild(el);
  }

  MC.wheel = function (m) {
    if (MC.ov.active()) return;
    var el = document.elementFromPoint(m.x * (window.HUBZ || 1), m.y * (window.HUBZ || 1)), box = closest(el, "data-scroll");
    if (!box) return;
    var step = m.dy > 0 ? -1 : 1, which = box.getAttribute("data-scroll");
    if (which === "coin") { var t = M().cards.trade; if (t && t.coin && !MC.textFocus) setCoin(t.me.coin + (step > 0 ? -10 : 10)); return; }
    scrollBox(which, step);
    MC.render();
  };
  function scrollBox(which, step) {
    if (which === "menu" && st.menu && st.menu.id === "sound") { var sw = sound(); setSound(sw.enabled, sw.volume + step * 10); }
    else if (which === "menu") { if (st.menu) st.menu.scroll = Math.max(0, st.menu.scroll + step); }
    else if (which === "binder") st.binder.scroll = Math.max(0, st.binder.scroll + step);
    else if (which === "deck") st.deck.scroll = Math.max(0, st.deck.scroll + step);
    else if (which === "hall") st.hall.scroll = Math.max(0, st.hall.scroll + step);
    else if (which === "rebuild") st.rebuild.scroll = Math.max(0, (st.rebuild.scroll || 0) + step);
    else if (which === "fuse") st.fuse.scroll = Math.max(0, (st.fuse.scroll || 0) + step);
    else if (which === "release") st.release.scroll = Math.max(0, (st.release.scroll || 0) + step);
    else if (which === "tpick") st.trade.scroll = Math.max(0, st.trade.scroll + step);
    else if (which === "tme") st.trade.meScroll = Math.max(0, (st.trade.meScroll || 0) + step);
    else if (which === "tthem") st.trade.themScroll = Math.max(0, (st.trade.themScroll || 0) + step);
    else if (MC.bf) MC.bf.wheel(which, step);
  }

  // ---- hooks mettle.js calls -----------------------------------------------------------------------------------
  var iconsKey = 0;
  MC.iconsChanged = function (replaced) { iconsKey++; if (replaced) screen = ""; MC.render(); };

  /** Takes over #classes (cards mode). */
  MC.attach = function (el) {
    root = el;
    root.innerHTML = '<div id="cv"></div><div id="c-ov"></div>';
    cv = MC.$("cv");
    screen = "";
    MC.attached = true;
  };
  MC.detach = function () {
    if (!MC.attached) return;
    MC.ov.close(true);
    hoverEl = null;
    MC.fxClear();
    MC.attached = false;
    if (root) root.innerHTML = "";
    screen = "";
  };

  // ---- one voice for Mettle's numbers ----------------------------------------------------------------------------
  // Mettle counts days from 0 (the day the world began); the hub's world island says "Day 1" for that day. Every day the
  // cards show goes through the hub's numbering. A trait's value always carries its unit: Mettle's text has it for
  // percentages ("10 %"); a bare number ("0.2") takes the unit its own description gives it ("reach 0.2 blocks further"
  // -> "+0.2 blocks"), or at least a sign.
  function hubDay(d) { return typeof d === "number" && d >= 0 ? d + 1 : d; }
  function dayText(t) { return String(t).replace(/\bday (\d+)\b/g, function (_, d) { return "day " + (+d + 1); }); }
  function withUnit(text, desc) {
    var v = String(text == null ? "" : text).trim();
    if (!/^[+-]?\d+(\.\d+)?$/.test(v)) return v;  // has a unit already
    var m = String(desc || "").match(new RegExp(v.replace(/[.+]/g, "\\$&") + "\\s+([A-Za-z]+)"));
    var unit = m ? m[1] : "";
    return (v.charAt(0) === "-" || v.charAt(0) === "+" ? v : "+" + v) + (unit ? " " + unit : "");
  }
  function unitTraits(c) {
    if (!c || c.__units) return;
    c.__units = true;
    var ts = c.traits || [];
    for (var i = 0; i < ts.length; i++) {
      ts[i].valueText = withUnit(ts[i].valueText, ts[i].description);
      if (ts[i].boostedText) ts[i].boostedText = withUnit(ts[i].boostedText, ts[i].description);
    }
    if (c.drawn && c.drawn.text) c.drawn.text = dayText(c.drawn.text);
    if (c.drawn) c.drawn.day = hubDay(c.drawn.day);
    if (c.lastOwners) for (var k = 0; k < c.lastOwners.length; k++) c.lastOwners[k].day = hubDay(c.lastOwners[k].day);
  }
  function normalise(state) {
    var cs = state.cards || {}, b = cs.binder ? cs.binder.cards : [];
    for (var i = 0; i < b.length; i++) unitTraits(b[i]);
    var sk = state.skills || [];
    for (var j = 0; j < sk.length; j++) for (var k = 0; sk[j].hand && k < sk[j].hand.length; k++) unitTraits(sk[j].hand[k]);
    var t = cs.trade;
    if (t && t.me) for (var a = 0; t.me.cards && a < t.me.cards.length; a++) unitTraits(t.me.cards[a]);
    if (t && t.them) for (var e = 0; t.them.cards && e < t.them.cards.length; e++) unitTraits(t.them.cards[e]);
    var my = cs.mythics, lists = my ? [my.all || [], my.mine || []] : [];
    for (var l = 0; l < lists.length; l++) for (var n = 0; n < lists[l].length; n++) {
      if (!lists[l][n].__day) { lists[l][n].__day = true; lists[l][n].day = hubDay(lists[l][n].day); }
    }
  }
  MC.withUnit = withUnit;

  /** A new Mettle state. */
  MC.state = function (state) {
    MC.M = state;
    stateGen++;
    if (!state || !state.cards) return;
    normalise(state);
    byId = {};
    var all = state.cards.binder.cards;
    for (var i = 0; i < all.length; i++) byId[all[i].id] = all[i];
    watchSets();
    if (MC.bf) MC.bf.state(state);
    if (slotSoundFor && byId[slotSoundFor] && byId[slotSoundFor].place === "slotted") slotSoundFor = null;
    MC.ov.onState();
  };

  /** The window switched page (inventory, gear, classes). Leaving Classes ends any moment quietly. */
  MC.layout = function (l) {
    kind = l.kind;
    var u = l.u || 1, scaled = u !== MC.u;
    MC.u = u;
    MC.css(u);
    if (kind !== "classes") {
      if (MC.ov.active()) MC.ov.close(false);
      if (MC.textFocus) MC.textFocus = false;
      st.menu = null;
      st.trade.coinDraft = null;
      MC.itemsReset();
      press = null;
      st.deck.drag = null;
      hoverEl = null;
      MC.fxClear();
      focus.key = null;
      st.trade.adding = false;
      st.deck.pop = null;
      ui();
    } else {
      // The screen's DOM is kept across opens (the page outlives the hub's screens): a reopen patches what changed.
      // A new scale rebuilds it (icon and text sizes are per scale).
      if (scaled) {
        screen = "";
        var hand = document.querySelectorAll("#dr-hand .hc");
        for (var i = 0; i < hand.length; i++) MC.layoutCard(hand[i]);
      }
      // Java drops the native layers when the window changes page or size: send them again (positions may move).
      hoverEl = null;
      MC.fxSet("hover", []);
      MC.fxResend();
      if (MC.ov.active()) MC.ov.refx();
      // Opening Classes (K after "a draft to pick", or the segment) deals what waits; Esc keeps it for this visit.
      dismissedWaiting = 0;
      ui(true);
    }
  };

  /**
   * Whether a screen shows the page (Java: the hub opened or closed). Hidden, the overlays' timers pause and nothing is
   * sent; shown, they carry on and Java hears the keyboard claims again (its new screen starts from none).
   */
  MC.visible = function (on) {
    if (!on) { MC.ov.hide(); return; }
    MC.ov.show();
    ui(true);
  };

  // Build feel (cards-build.js) works on these.
  MC.pg = {SCREENS: SCREENS, nav: nav, st: st, head: head, footer: footer, tabs: tabs, skillChip: skillChip, skillHead: skillHead, skill: skill, skills: skills,
    deck: deck, cards: cards, byId: function () { return byId; }, cv: function () { return cv; }, root: function () { return root; }, screen: function () { return screen; },
    closest: closest, closeMenu: closeMenu, openMenu: openMenu, cardKey: cardKey, setSkills: setSkills, pips: pips, homeHead: homeHead, kwName: kwName,
    iconsKey: function () { return iconsKey; }, kind: function () { return kind; }, ui: ui, bg: bg, fmt: fmt, strip: strip};

  /** What the tests print when a step fails. */
  MC.debug = function () {
    var out = {screen: screen, attached: MC.attached, kind: kind, dismissed: dismissedWaiting, ov: MC.ov.kind()};
    if (M() && M().skills) {
      out.waiting = M().draft ? M().draft.waiting : null;
      out.hands = skills().map(function (s) { return s.id + ":" + s.drafts + ":" + (s.hand ? s.hand.length : "none"); }).join(" ");
    }
    return JSON.stringify(out);
  };

  document.addEventListener("click", onClick, true);
  document.addEventListener("mousedown", onDown, true);
  document.addEventListener("mousemove", onMove, true);
  document.addEventListener("mouseup", onUp, true);
})();
