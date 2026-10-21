// VOID Mettle 0.3.0 "build feel" in the hub (mettle/docs/BUILD_FEEL.md, OVERLAY.md "Build feel"; Figma 3c0Wgc4ZKFSpm98fWJoARS
// page 295:2, section 2i 679:41228): the character sheet (2i-1), boons with Temper and Inscribe (2i-3, -4, -5), milestone
// toasts and banners (2i-6, -7), the guide's hints (2i-8 to -11), the Card Shrine's Forge (2i-12, -13), the binder's
// "Completes a set" and "If slotted…" (2i-14). Card marks (2i-15) are in cards-core.js, the hand's preview lines (2i-2)
// in cards-draft.js; cards-pages.js calls the MC.bf hooks below.
//
// Same rules as the other card screens: parts made once and patched by key (no rebuilds of big regions), nothing loops in
// CSS (the one-away pulse is a native CardFx layer), every string escaped, every popover modal for Esc. Mettle decides
// everything; the page only reads cards.build and asks (MC.bf.ask -> Java -> MettleActions previews, nothing sent).
(function () {
  "use strict";

  var MC = window.MC, esc = MC.esc, upper = MC.upper, part = MC.part, patch = MC.patch;
  var Y = function (y) { return y + 24; };
  var CHAMP = "#E8DDBC", TEMPER = MC.TEMPER, AMBER = MC.AMBER, IVORY = "#EDE3C8", LEGEND = "#F4EDDA";
  var ROMAN = ["", "I", "II", "III", "IV"];
  var bf = MC.bf = {};
  var P = function () { return MC.pg; };
  function M() { return MC.M; }
  function B() { return M() && M().cards ? M().cards.build : null; }
  var boon = {skill: null, choice: null, card: null, trait: null, kw: null, scroll: 0};  // the boon flow's choices
  MC.boonSel = boon;  // tests
  var forgeSel = null, keptMine = {}, sigPick = null;

  // ---- questions to Mettle (previews; nothing is sent to the server) -----------------------------------------------
  var req = 0, waiting = {}, answers = {}, stale = {}, cardsSig = "";
  /**
   * The answer to op(arg) if Mettle gave one since the cards last changed; else asks (once) and returns the answer from
   * before the change meanwhile (no "Asking…" flicker), or undefined.
   */
  bf.ask = function (op, arg) {
    var k = op + "|" + arg;
    if (k in answers) return answers[k];
    for (var r in waiting) if (waiting[r] === k) return stale[k];
    req = (req + 1) % 1000000000;
    waiting[req] = k;
    MC.send({type: "mettleAsk", op: op, arg: String(arg), req: req});
    return stale[k];
  };
  bf.answer = function (m) {
    var k = waiting[m.req];
    delete waiting[m.req];
    if (!k) return;
    answers[k] = m.result || {ok: false, reason: ""};
    if (MC.render) MC.render();
  };
  bf.answers = function () { return answers; };  // tests

  // ---- small parts ---------------------------------------------------------------------------------------------
  function dia(colour, size) { return '<i class="bdia" style="width:' + (size || 8) + "px;height:" + (size || 8) + "px;background:" + colour + '"></i>'; }
  function quill() { return '<i class="quill big"></i>'; }
  function skillOf(id) { return P().skill(id); }
  function cardById(id) { return P().byId()[id]; }
  function hueOf(id) { var s = skillOf(id); return s ? s.colour : "#9A9DA1"; }
  function setById(id) { var s = M().cards.sets; for (var i = 0; i < s.length; i++) if (s[i].id === id) return s[i]; return null; }
  function kwName(id) { return P().kwName(id); }
  function tierPips(tier, colour, w) {
    var h = '<span class="tpips">';
    for (var i = 1; i <= 4; i++) h += '<i style="width:' + (w || 22) + "px;" + (i <= tier ? "background:" + colour : "") + '"></i>';
    return h + "</span>";
  }
  /** The crest: the Signature of the worn byname's first skill (its card's art). */
  function crestCard() {
    var b = B(), worn = b && b.byname && b.byname.worn, sk = worn ? worn.skill1 : null;
    var sigs = b ? b.signatures || [] : [];
    for (var i = 0; i < sigs.length; i++) if (!sk || sigs[i].skill === sk) return cardById(sigs[i].card) || null;
    return null;
  }
  function bgName() { var g = P().bg(); return g ? g.name : ""; }
  function playerName() { return MC.player || "You"; }

  // ---- header parts ----------------------------------------------------------------------------------------------
  bf.shardsPill = function () {
    var s = B() && B().shards;
    if (!s) return "";
    var f = Math.max(0, Math.min(1, s.count / Math.max(1, s.cost)));
    return '<div class="hd-shards"><i class="shard"></i><span class="k">Shards</span><span class="v">' + s.count + " / " + s.cost + '</span><i class="tr"></i><i class="fl" style="width:' + Math.round(56 * f) + 'px"></i></div>';
  };
  bf.boonBadge = function (s, n, on) {
    return '<div class="hd-boon' + (on ? " on" : "") + '" data-boon="' + esc(s.id) + '">' + dia(AMBER, 9) + "<span>" + n + (n === 1 ? " boon" : " boons") + "</span></div>";
  };
  /** The home header's boon count (every skill), next to the drafts badge. */
  bf.homeBoons = function () {
    var b = B();
    if (!b || !b.boons || !b.boons.total) return "";
    var first = b.boons.bySkill && b.boons.bySkill.length ? b.boons.bySkill[0].skill : "";
    for (var i = 0; i < (b.boons.bySkill || []).length; i++) if (b.boons.bySkill[i].count > 0) { first = b.boons.bySkill[i].skill; break; }
    return '<div class="hd-boon" data-boon="' + esc(first) + '" title="' + esc(b.boons.text || "") + '">' + dia(AMBER, 9) + "<span>" + b.boons.total + (b.boons.total === 1 ? " boon" : " boons") + "</span></div>";
  };
  bf.focusChip = function (cls) {
    var f = B() && B().focus, on = !!(f && f.keyword), open = P().st.menu && P().st.menu.id === "focus";
    return '<div class="bf-focus ' + (cls || "") + (on ? " on" : "") + (open ? " open" : "") + '" data-dd="focus"><i class="fring"></i>' + (on ? "Focus · " + esc(f.name) : "Focus") + '<i class="dd"></i></div>';
  };
  bf.deckMarks = function (d) {
    if (!d || d.temperMax == null) return "";
    var h = '<div class="dk-marks">';
    h += '<span class="pp t">';
    for (var i = 0; i < d.temperMax; i++) h += '<i class="' + (i < d.tempered ? "on" : "") + '"></i>';
    h += "</span>" + esc(d.temperedText || "") + '<span class="pp q">';
    for (var k = 0; k < d.inscribeMax; k++) h += '<i class="' + (k < d.inscribed ? "on" : "") + '"></i>';
    return h + "</span>" + esc(d.inscribedText || "") + "</div>";
  };
  bf.signatureButton = function (c, d) {
    if (MC.isMythic(c) && !c.deck) return "";
    if (c.signature) return '<div class="pb sig on" data-setsig="clear#' + esc(d.skill) + '">' + dia(CHAMP, 8) + "Signature · clear</div>";
    return '<div class="pb sig" data-setsig="' + esc(c.id) + '">' + dia(CHAMP, 8) + "Make it this deck’s Signature</div>";
  };

  // ---- the character sheet (2i-1) -------------------------------------------------------------------------------
  function nameCard() {
    var b = B(), bn = b.byname || {}, worn = bn.worn, c = M().character, crest = crestCard(), legend = bn.legend;
    var h = '<div class="ch-crest' + (legend ? " lg" : "") + '" style="background:' + MC.shade(crest ? crest.colour : CHAMP) + '">' + (crest ? MC.img(crest.icon, 48) : '<i class="bdia big" style="background:#3A3B3F"></i>') + "</div>";
    if (crest) h += '<i class="ch-crest-c"></i>';
    h += '<div class="ch-nm">' + esc(playerName()) + '</div><div class="ch-sub">' + esc(bgName() + " · Character LV " + c.level) + "</div>";
    h += '<div class="ch-chip">' + esc(crest ? "Crest: " + (crest.baseName || crest.name) : "No crest yet · pick a Signature") + "</div>";
    h += '<div class="eb9" style="left:16px;top:102px">BYNAME · ' + (worn ? "WORN" : "NONE YET") + "</div>";
    h += '<div class="ch-bn' + (worn ? "" : " none") + '">' + esc(worn ? worn.text : "A 2-set gives you one") + "</div>";
    h += '<div class="ch-tp">' + tierPips(bn.tier || 0, CHAMP, 22) + '<span class="tn">' + esc(bn.tier ? ROMAN[bn.tier] + " · " + (bn.tierName || "") : "No tier yet") + "</span></div>";
    h += '<div class="ch-line">' + esc(worn ? worn.line : "Your build has no shape yet") + "</div>";
    if (legend) h += '<div class="ch-legend">' + stars(legend.stars) + esc(legend.title) + "</div>";
    var held = (bn.held || []).length, open = P().st.menu && P().st.menu.id === "bynames";
    h += '<div class="ch-pill' + (open ? " open" : "") + '"' + (held ? ' data-dd="bynames"' : "") + ">Bynames · " + held + " held" + (held ? '<i class="dd"></i>' : "") + "</div>";
    h += '<div class="ch-note">Shown on your name card</div>';
    return h;
  }
  function stars(n) { var s = '<span class="stars">'; for (var i = 0; i < n; i++) s += "★"; return s + "</span>"; }

  var TIER_HOW = ["", "first 2-set", "first 4-set", "first 6-set, or a Mastery beside a 4-set", "tier III and a Mythic slotted"];
  function grew() {
    var bn = B().byname || {}, held = bn.held || [], h = "";
    h += '<div class="eb9" style="left:16px;top:14px">HOW IT GREW</div><div class="bf-note">tiers never drop</div><i class="ch-spine"></i>';
    for (var t = 1; t <= 4; t++) {
      var got = null;
      for (var i = held.length - 1; i >= 0; i--) if (held[i].tier === t) { got = held[i]; break; }
      var reached = (bn.tier || 0) >= t, y = 36 + (t - 1) * 36;
      h += '<div class="ch-tier' + (reached ? " on" : "") + '" style="top:' + y + 'px"><i class="dot"></i><span class="rn">' + ROMAN[t] + '</span><span class="tx">' +
        esc(got ? got.text : t === 4 ? "… of your Mythic" : reached ? "Tier " + ROMAN[t] : "Not yet") + '</span><span class="sb">' +
        esc(got ? (got.tierName || "") + (got.wearing ? " · worn" : "") : reached ? "" : TIER_HOW[t]) + "</span></div>";
    }
    return h;
  }
  function offerBox() {
    var bn = B().byname || {};
    return '<div class="eb9" style="left:16px;top:14px">YOUR BUILD DRIFTED</div><div class="of-k">Your build reads as</div><div class="of-v">' + esc(bn.reads ? bn.reads.text : "") +
      '</div><div class="of-go" data-takebyname="1">Take it</div><div class="of-no" data-keepmine="1">Keep mine</div><div class="of-n">Words follow the build; the tier never drops.</div>';
  }
  function buildCard() {
    var sm = B().summary || {}, hd = sm.headline, h = "";
    h += '<div class="eb9" style="left:16px;top:16px">YOUR BUILD</div>';
    if (hd) h += '<div class="yb-tier">' + esc(hd.tierLine || "") + "</div>";
    h += '<div class="yb-h">' + esc(hd ? hd.text : "No byname yet") + '</div><div class="yb-l">' + esc(hd ? hd.line : "") + '</div><i class="yb-hr"></i>';
    if (sm.empty || !(sm.lines || []).length) {
      h += '<div class="yb-empty"><i class="cardback"></i><div>' + esc(sm.emptyText || "Slot cards to give your build a shape") + "</div></div>";
    }
    var ls = sm.lines || [];
    for (var i = 0; i < ls.length && i < 5; i++) {
      var L = ls[i], icon = L.kind === "set" ? MC.glyph(L.id, 16) : L.kind === "mythic" ? dia(IVORY, 9) : L.kind === "mastery" ? dia(CHAMP, 9) : '<i class="yb-dot"></i>';
      h += '<div class="yb-line" style="top:' + (90 + i * 38) + 'px"><span class="ic">' + icon + '</span><span class="tx">' + esc(L.text) + '</span><span class="src">' + esc(L.source || "") + "</span></div>";
    }
    if (sm.next) h += '<div class="yb-next"><span class="k">NEXT</span><span class="tx">' + esc(String(sm.next.text || "").replace(/^Next:\s*/, "")) + "</span></div>";
    return h;
  }
  function sheetSets() {
    var sets = M().cards.sets.slice(), act = 0, h = "", y = 40;
    for (var i = 0; i < sets.length; i++) if (sets[i].active) act++;
    var near = function (x) { return x.oneAway && x.next >= 4; };
    sets.sort(function (a, b) { return (b.active ? 2 : near(b) || b.asleep ? 1 : 0) - (a.active ? 2 : near(a) || a.asleep ? 1 : 0) || b.count - a.count; });
    h += '<div class="eb9" style="left:16px;top:16px">SETS · ' + (act ? act + " ACTIVE" : "NONE YET") + "</div>" + bf.focusChip("ch-fc");
    for (var k = 0; k < sets.length && y < 290; k++) {
      var s = sets[k];
      if (!s.count && !s.focus) continue;
      var big = s.active || near(s) || s.asleep;
      if (big) {
        var sub = s.asleep ? s.asleepText : near(s) ? "One card away · " + nextBonus(s) : P().setSkills(s);
        var tall = s.asleep && String(sub).length > 34;
        h += '<div class="ss-set big' + (s.asleep ? " asleep" : near(s) ? " away" : "") + '" data-kwhover="' + esc(s.id) + '" data-ssid="' + esc(s.id) + '" style="top:' + y + "px;height:" + (tall ? 62 : 48) + 'px">' + MC.glyph(s, 16) +
          '<span class="n">' + esc(s.name) + (s.focus ? '<i class="fmark"></i>' : "") + '</span><span class="c">' + s.count + "</span>" + bars(s) +
          (near(s) && s.nextText ? '<span class="nx">' + esc(s.nextText) + "</span>" : "") + '<div class="sub">' + esc(sub) + "</div></div>";
        y += (tall ? 62 : 48) + 6;
      } else {
        h += '<div class="ss-set" data-kwhover="' + esc(s.id) + '" style="top:' + y + 'px">' + MC.glyph(s, 16) + '<span class="n">' + esc(s.name) + (s.focus ? '<i class="fmark"></i>' : "") + '</span><span class="c">' + s.count + "</span>" + bars(s) + "</div>";
        y += 26;
      }
    }
    h += '<div class="ss-note">Focus leans the deal, never counts for sets.</div>';
    return h;
  }
  function nextBonus(s) {
    for (var i = 0; i < (s.bonuses || []).length; i++) if (s.bonuses[i].tier === s.next) return s.name + " " + s.next + ": " + String(s.bonuses[i].description || "").replace(/^[^:]*:\s*/, "");
    return s.nextText || "";
  }
  function bars(s) {
    var h = '<span class="ss-bars">';
    for (var i = 0; i < 3; i++) {
      var on = s.tier >= (i + 1) * 2, sleep = s.asleep && i === 2;
      h += '<i class="' + (on ? "on" : sleep ? "sleep" : s.oneAway && s.next === (i + 1) * 2 ? "next" : "") + '"></i>';
    }
    return h + "</span>";
  }
  function attrs() {
    var c = M().character, h = '<div class="eb9" style="left:16px;top:14px">ATTRIBUTES</div><div class="bf-note">boost card traits of their kind</div>';
    for (var k = 0; k < M().attributes.length && k < 5; k++) {
      var at = M().attributes[k], v = c.attributes[at.id] || 0, x = 16 + k * 61, plus = c.points > 0 && v < c.maxAttribute;
      h += '<div class="at-k" style="left:' + x + 'px" title="' + esc(at.perPoint || "") + '">' + esc(upper(at.name).slice(0, 3)) + '</div><div class="at-v" style="left:' + x + 'px">' + v + "</div>" +
        (plus ? "" : '<i class="at-b" style="left:' + (x + 24) + 'px"><b style="width:' + Math.round(30 * Math.min(1, v / Math.max(1, c.maxAttribute || 20))) + 'px"></b></i>') +
        (plus ? '<div class="at-plus" data-attr="' + esc(at.id) + '" style="left:' + (x + 34) + 'px">+</div>' : "");
    }
    return h;
  }
  function sigs() {
    var b = B(), list = b.signatures || [], worn = b.byname && b.byname.worn, h = "", n = 0;
    var crestSkill = worn ? worn.skill1 : null, cs = crestSkill && skillOf(crestSkill);
    h += '<div class="eb9" style="left:16px;top:14px">SIGNATURES</div><div class="sg-r">' + esc(cs ? cs.name + (/s$/.test(cs.name) ? "’" : "’s") + " is your crest" : list.length ? "" : "One per deck · pure identity") + "</div>";
    for (var i = 0; i < list.length && n < 4; i++) {
      var c = cardById(list[i].card);
      if (!c) continue;
      var hue = hueOf(list[i].skill), crest = list[i].skill === crestSkill;
      h += '<div class="sg-t" data-sigdeck="' + esc(list[i].skill) + '" title="' + esc((c.baseName || c.name) + " · " + (skillOf(list[i].skill) ? skillOf(list[i].skill).name : "")) + '" style="left:' + (16 + n * 62) + "px;border-color:" + MC.rgba(hue, 0.85) + ";background:" + MC.shade(hue) + '">' + MC.img(c.icon, 32) + (crest ? '<i class="crestc"></i>' : "") + "</div>";
      n++;
    }
    if (n < 4) {
      // A deck with slotted cards and no Signature yet: the + opens it, where a slotted card can be made its Signature.
      var target = "";
      var ds = M().cards.decks;
      for (var d = 0; d < ds.length && !target; d++) {
        if (ds[d].signature) continue;
        for (var r = 0; r < ds[d].row.length; r++) if (ds[d].row[r].card) { target = ds[d].skill; break; }
      }
      h += '<div class="sg-t add"' + (target ? ' data-sigdeck="' + esc(target) + '"' : "") + ' style="left:' + (16 + n * 62) + 'px">+</div>';
    }
    return h;
  }
  function talents() {
    var t = M().talents;
    if (!t) return "";
    var list = t.slots || t.talents || [];
    var h = '<div class="eb9" style="left:16px;top:14px">TALENTS</div><div class="tl-n">' + esc(t.text || list.length + " slots") + "</div>";
    for (var i = 0; i < list.length && i < 7; i++) h += '<div class="tl-t" style="left:' + (150 + i * 108) + 'px"><div class="n">' + esc(list[i].name || "") + '</div><div class="v">' + esc(list[i].text || list[i].description || "") + "</div></div>";
    return h;
  }

  P().SCREENS["home-character"] = function () {
    P().homeHead("character");
    var cv = P().cv(), b = B();
    if (!b) { patch(part(cv, "ch-none", "dk-none"), "none", "This Mettle has no character sheet."); return; }
    var c = M().character, ik = P().iconsKey(), menuId = P().st.menu && P().st.menu.id;
    patch(part(cv, "ch-name", "bf-panel ch-name", "left:29px;top:" + Y(68) + "px;width:300px;height:226px"),
      JSON.stringify([b.byname, b.signatures, c.level, c.background, MC.player, ik, menuId === "bynames"]), nameCard);
    var offer = b.byname && b.byname.offer && !keptMine[b.byname.offerText];
    patch(part(cv, "ch-grew", "bf-panel ch-grew" + (offer ? " offer" : ""), "left:29px;top:" + Y(306) + "px;width:300px;height:182px"),
      JSON.stringify([offer, b.byname]), offer ? offerBox : grew);
    patch(part(cv, "ch-build", "bf-panel ch-build", "left:341px;top:" + Y(68) + "px;width:330px;height:330px"), JSON.stringify([b.summary, ik]), buildCard);
    patch(part(cv, "ch-attr", "bf-panel ch-attr", "left:341px;top:" + Y(410) + "px;width:330px;height:78px"), JSON.stringify([c.attributes, c.points]), attrs);
    patch(part(cv, "ch-sets", "bf-panel ch-sets", "left:683px;top:" + Y(68) + "px;width:275px;height:318px"),
      JSON.stringify([M().cards.sets, b.focus, ik, menuId === "focus"]), sheetSets);
    patch(part(cv, "ch-sigs", "bf-panel ch-sigs", "left:683px;top:" + Y(398) + "px;width:275px;height:90px"), JSON.stringify([b.signatures, b.byname && b.byname.worn, ik,
      M().cards.decks.map(function (d) { return d.skill + (d.signature || "") + d.row.filter(function (r) { return r.card; }).length; })]), sigs);
    // Talents (a later Mettle wave): the row stays hidden until Mettle reports them.
    var tl = part(cv, "ch-talents", "bf-panel ch-talents", "left:29px;top:" + Y(498) + "px;width:929px;height:88px" + (M().talents ? "" : ";display:none"));
    patch(tl, JSON.stringify(M().talents || null), talents);
    P().footer("", "");
  };

  // ---- menus: the bynames list, the Focus picker ------------------------------------------------------------------
  var focusPick = null;
  bf.menuSpec = function (id) {
    if (id === "bynames") return {custom: bynamesPanel, w: 275, h: 64 + Math.min(6, ((B().byname || {}).held || []).length) * 38, opts: []};
    if (id === "focus") return {custom: focusPanel, w: 275, h: 386, opts: []};
    return {opts: []};
  };
  function bynamesPanel() {
    var held = (B().byname || {}).held || [], h = '<div class="ddm-h">Bynames you have held</div><div class="bn-sub">Wear any of them. A tier rise is worn at once.</div>';
    for (var i = 0; i < held.length && i < 6; i++) {
      var b = held[i];
      h += '<div class="bn-row' + (b.wearing ? " on" : "") + '" data-bnw="' + b.index + '" style="top:' + (56 + i * 38) + 'px"><i class="rd"></i><span class="t">' + esc(b.text) + '</span><span class="r">' + esc(ROMAN[b.tier] || "") + "</span></div>";
    }
    return h;
  }
  function focusPanel() {
    var f = B().focus || {}, cur = focusPick != null ? focusPick : f.keyword || "", kws = M().cards.keywords || [], sets = {}, h = "";
    for (var s = 0; s < M().cards.sets.length; s++) sets[M().cards.sets[s].id] = M().cards.sets[s];
    h += '<div class="ddm-h">Focus a keyword</div><div class="fp-sub">The deal leans toward it. It counts as two cards for the lean, never for sets.</div>';
    for (var i = 0; i < kws.length && i < 14; i++) {
      var k = kws[i], m = sets[k.id] ? sets[k.id].momentum || 0 : 0, hex = /hex$/.test(k.id), on = cur === k.id;
      h += '<div class="fp-k' + (on ? " on" : "") + (hex ? " hex" : "") + '"' + (hex ? "" : ' data-fkw="' + esc(k.id) + '"') + ' style="left:' + (12 + (i % 2) * 128) + "px;top:" + (70 + Math.floor(i / 2) * 34) + 'px">' + MC.glyph(k, 16) +
        '<span class="n">' + esc(k.name) + (on ? '<i class="fring"></i>' : "") + "</span>" + (hex ? '<span class="nv">never</span>' : leanBars(m)) + "</div>";
    }
    h += '<div class="fp-note">' + esc(f.canChange === false ? "Changed today · " + (f.note || "Changes once a day") : "Bars: how hard it leans. Scarce words lean most. " + (f.note || "Changes once a day") + ".") + "</div>";
    var go = cur && cur !== f.keyword && f.canChange !== false;
    h += '<div class="fp-go' + (go ? "" : " off") + '"' + (go ? ' data-fgo="1"' : "") + ">" + esc(cur ? "Focus " + kwName(cur) : "Pick a keyword") + "</div>";
    if (f.keyword && f.canChange !== false) h += '<div class="fp-clear" data-fclear="1">No Focus</div>';
    return h;
  }
  function leanBars(m) {
    var lit = Math.round((m || 0) * 4), h = '<span class="lean">';
    for (var i = 0; i < 4; i++) h += '<i class="' + (i < lit ? "on" : "") + '" style="height:' + (4 + i * 2) + 'px"></i>';
    return h + "</span>";
  }
  bf.menuPick = function () {};
  /** Clicks inside build feel's menus (custom panels): true when handled. */
  bf.menuClick = function (id, t) {
    var cl = P().closest, el;
    if (id === "bynames" && (el = cl(t, "data-bnw"))) { MC.act("wearByname", el.getAttribute("data-bnw")); P().closeMenu(); return true; }
    if (id === "focus") {
      if ((el = cl(t, "data-fkw"))) { focusPick = el.getAttribute("data-fkw"); return true; }
      if (cl(t, "data-fgo") && focusPick) { MC.act("setFocus", focusPick); focusPick = null; P().closeMenu(); return true; }
      if (cl(t, "data-fclear")) { MC.act("setFocus", ""); focusPick = null; P().closeMenu(); return true; }
      if (cl(t, "data-ddm") && !cl(t, "data-dd")) return true;  // inside the panel: stays open
    }
    return false;
  };

  // ---- boons: the choice and the card picker (2i-3) --------------------------------------------------------------
  function boonDeck() { return P().deck(boon.skill); }
  function skillCards(sk) {
    var all = P().cards(), out = [];
    for (var i = 0; i < all.length; i++) if (all[i].skill === sk && !MC.isMythic(all[i])) out.push(all[i]);
    return out;
  }
  /** Why a card can't take this boon (what the page knows; Mettle's boonOptions has the last word), or "". */
  function why(c, choice, ask) {
    var d = boonDeck() || {};
    if (c.place === "trade" || c.place === "listed") return "In a trade";
    if (c.attuned === false) return "Attuning · " + (c.attuningMinutes || 0) + " min";
    if (choice === "temper") {
      if (MC.temperedTrait(c)) return "Tempered already · once a card";
      if (!(c.traits || []).length) return "No traits to temper";
      if (c.place === "slotted" && d.tempered >= d.temperMax) return "Deck holds " + d.tempered + " / " + d.temperMax + " tempered";
    } else {
      if (MC.inscribedKw(c)) return "Inscribed already · once a card";
      if (c.place === "slotted" && d.inscribed >= d.inscribeMax) return "Deck holds " + d.inscribed + " / " + d.inscribeMax + " inscribed";
    }
    if (!ask) return "";
    var opts = bf.ask("boonOptions", c.id);
    if (opts && opts.ok === false && opts.reason) return opts.reason;
    if (opts && choice === "temper" && opts.temper && !opts.temper.some(function (x) { return x.ok; })) return (opts.temper[0] && opts.temper[0].reason) || "Nothing to raise";
    if (opts && choice === "inscribe" && opts.inscribe && !opts.inscribe.some(function (x) { return x.ok; })) return "No other keyword in this deck to copy";
    return "";
  }
  function boonHead(s) {
    P().skillHead(s, "boon");
  }
  P().SCREENS["skill-boon"] = function () {
    var s = skillOf(P().nav.skill), cv = P().cv(), b = B(), d = P().deck(s.id);
    if (boon.skill !== s.id) { boon.skill = s.id; boon.choice = null; boon.card = null; boon.trait = null; boon.kw = null; boon.scroll = 0; }
    boonHead(s);
    var n = d ? d.boons || 0 : 0;
    patch(part(cv, "bo-eb", "bo-eb", "left:29px;top:" + Y(72) + "px"), String(n) + s.level, '<div class="eb9" style="left:0;top:0">BOON · ' + esc(upper(s.name)) + " " + s.level + " · ODD LEVELS FROM 7 GIVE ONE" + (n > 1 ? " · " + n + " WAITING" : "") + "</div>");
    MC.text(part(cv, "bo-note", "bo-note"), n ? "Boons wait like drafts and never expire." : "No boon waits in " + s.name + ": odd levels from 7 give one.");
    var ch = b.boons.choices || [], dd = d || {};
    for (var i = 0; i < 3; i++) {
      var c = ch[i], el = part(cv, "bo-c" + i, "bo-choice", "left:" + (29 + i * 314) + "px;top:" + Y(92) + "px");
      if (!c) continue;
      var off = !c.enabled || !n, sel = boon.choice === c.id;
      patch(el, JSON.stringify([c, sel, off, dd.temperedText, dd.inscribedText, M().draft && M().draft.rerolls]), (function (c, i, sel, off) {
        return function () {
          var icon = c.id === "temper" ? dia(TEMPER, 14) : c.id === "inscribe" ? quill() : '<svg width="18" height="18" viewBox="0 0 14 14"><path d="M11.6 5.2A5 5 0 1 0 12 8" stroke="#9A9DA1" stroke-width="1.4" fill="none" stroke-linecap="round"/><path d="M12.2 1.8v3.6H8.6" stroke="#9A9DA1" stroke-width="1.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>';
          var what = c.id === "temper" ? "Raise one trait on one " + s.name + " card by a quarter of its range." : c.id === "inscribe" ? "Set a " + s.name + " card’s second keyword to one already in this deck." : (c.what || "+1 reroll") + " for any draft, as before.";
          var chips = c.id === "temper" ? ["Once per card ◆", dd.temperedText ? "Deck " + String(dd.temperedText).replace(/^Tempered\s*/, "") + " tempered" : ""]
            : c.id === "inscribe" ? ["Once per card ✎", dd.inscribedText ? "Deck " + String(dd.inscribedText).replace(/^Inscribed\s*/, "") + " inscribed" : ""]
            : ["You hold " + (M().draft ? M().draft.rerolls : 0) + " / " + (M().draft ? M().draft.maxRerolls : 5) + (b.boons.rerollsFull ? " · full" : "")];
          var h = '<div class="bo-in' + (sel ? " sel" : "") + (off ? " off" : "") + '"' + (off ? "" : ' data-bchoice="' + c.id + '"') + '><div class="well ' + c.id + '">' + icon + '</div><div class="nm">' + esc(c.name) + '</div><div class="kn">' + (i + 1) + '</div><div class="wh">' + esc(what) + '</div><div class="chips">';
          for (var k = 0; k < chips.length; k++) if (chips[k]) h += "<span>" + esc(chips[k]) + "</span>";
          return h + "</div></div>";
        };
      })(c, i, sel, off));
    }
    // The card picker.
    var pick = part(cv, "bo-pick", "bo-pick", "left:29px;top:" + Y(256) + "px" + (boon.choice ? "" : ";display:none"));
    pick.setAttribute("data-scroll", "boonpick");
    if (boon.choice) {
      var list = skillCards(s.id).map(function (c) { return {c: c, why: why(c, boon.choice, false)}; });
      list.sort(function (a, b2) { return (a.why ? 1 : 0) - (b2.why ? 1 : 0); });
      var maxScroll = Math.max(0, list.length - 8);
      boon.scroll = Math.max(0, Math.min(boon.scroll, maxScroll));
      // Mettle's own rules have the last word for the cards on show (boonOptions: band tops, the seed rule).
      for (var v = boon.scroll; v < boon.scroll + 8 && v < list.length; v++) if (!list[v].why) list[v].why = why(list[v].c, boon.choice, true);
      var key = JSON.stringify([boon.choice, boon.card, boon.scroll, list.map(function (x) { return P().cardKey(x.c) + x.why; }), P().iconsKey()]);
      patch(pick, key, function () {
        var h = '<div class="eb9" style="left:0;top:0">PICK ' + (boon.choice === "temper" ? "A " : "A ") + esc(upper(s.name)) + " CARD TO " + (boon.choice === "temper" ? "TEMPER" : "INSCRIBE") + "</div>" +
          '<div class="bo-pn">' + (list.length ? "Lit cards can take it. The others say why." + (maxScroll ? " · scroll for more" : "") : "") + "</div>";
        if (!list.length) h += '<div class="bo-empty">You hold no ' + esc(s.name) + " cards yet.</div>";
        for (var j = 0; j < 8; j++) {
          var it = list[j + boon.scroll];
          if (!it) break;
          var on = boon.card === it.c.id;
          h += '<div class="tile bo-t' + (it.why ? " dim" : "") + '" style="left:' + j * 114 + 'px;top:20px"' + (it.why ? "" : ' data-bcardpick="' + esc(it.c.id) + '"') + ">" +
            MC.ccard(it.c, {sel: on, check: on, attrs: ' data-nofx="1"'}) + '<div class="bo-why' + (on ? " on" : it.why ? "" : " ok") + '">' + esc(on ? "Selected" : it.why || "Can take it") + "</div></div>";
        }
        return h;
      });
    }
    // The bar under it.
    var sc = boon.card && cardById(boon.card);
    if (boon.card && (!sc || why(sc, boon.choice, true))) { boon.card = null; sc = null; }
    var bar = part(cv, "bo-bar", "bf-panel bo-bar", "left:29px;top:" + Y(470) + "px;width:929px;height:116px" + (boon.choice ? "" : ";display:none"));
    patch(bar, JSON.stringify([boon.choice, sc ? P().cardKey(sc) : 0]), function () {
      if (!sc) return '<div class="eb9" style="left:20px;top:20px">NOTHING SELECTED</div><div class="bb-t" style="color:#9A9DA1">Pick a lit card above</div><div class="bb-s">Temper and Inscribe show the result before anything changes.</div><div class="bb-back" data-bback="1">Back</div>';
      var where = sc.place === "slotted" ? "slot " + (sc.slot + 1) + " in " + s.name : "in your binder";
      var tr = (sc.traits || []).map(function (t) { return t.name + " " + t.valueText; }).join(" · ");
      return '<div class="eb9" style="left:20px;top:20px">1 SELECTED</div><div class="bb-t">' + esc((sc.baseName || sc.name) + " · " + (sc.rarityName || "") + " · " + (sc.tierName || "") + " · " + where) + "</div>" +
        '<div class="bb-s">' + esc((tr ? tr + ". " : "") + (boon.choice === "temper" ? "Choose the trait next; you see the result before anything changes." : "Choose the keyword next; you see the result before anything changes.")) + "</div>" +
        '<div class="bb-back" data-bback="1">Back</div><div class="bb-go" data-bnext="1">' + (boon.choice === "temper" ? "Choose the trait →" : "Choose the keyword →") + "</div>";
    });
    P().footer(upper(s.name) + " " + s.level + "  ·  " + n + (n === 1 ? " BOON WAITS" : " BOONS WAIT"), "1–3 CHOOSE  ·  ESC GOES BACK");
  };

  // ---- Temper confirm (2i-4) -----------------------------------------------------------------------------------
  function num(text) { var m = /-?\d+(?:\.\d+)?/.exec(String(text || "")); return m ? parseFloat(m[0]) : null; }
  /** valueText with its number scaled from `from` to `to` (Mettle's raw values; the text carries the unit). */
  function scaled(text, from, to) {
    var v = num(text);
    if (v == null || !from) return text;
    var nv = v * to / from, d = /\.\d/.test(String(text)) ? 1 : Math.abs(nv - Math.round(nv)) > 0.05 ? 1 : 0;
    return String(text).replace(/-?\d+(?:\.\d+)?/, nv.toFixed(d));
  }
  function afterCard(c, patchFn) {
    var o = {}, k;
    for (k in c) o[k] = c[k];
    o.traits = (c.traits || []).map(function (t) { var n = {}; for (var x in t) n[x] = t[x]; return n; });
    o.keywords = (c.keywords || []).slice();
    o.marks = {tempered: c.marks && c.marks.tempered, inscribed: c.marks && c.marks.inscribed, signed: []};
    patchFn(o);
    return o;
  }
  function handFx(id, c, x, y) {
    var w = MC.win(), edge = MC.edgeOf(c), tint = c.cursed ? MC.CURSED : c.colour || "#9A9DA1";
    MC.fxSet("boon", [{i: id + "-e", k: "edge-" + edge, x: w.x + x, y: w.y + y, c: tint}, {i: id + "-m", k: edge === "epic" || edge === "cursed" ? "embers-dense" : "embers-sparse", x: w.x + x, y: w.y + y, c: tint}]);
  }
  function previewCard(cv, card, key) {
    var el = part(cv, "tc-card", "tc-card", "left:41px;top:" + Y(122) + "px");
    if (patch(el, key, MC.hcard(card, 0, null, {}))) { var hc = el.querySelector(".hc"); if (hc) MC.layoutCard(hc); }
    handFx("bfpv", card, 41, Y(122));
  }
  function crumbs(cv, parts) { MC.text(part(cv, "tc-crumb", "tc-crumb"), parts.join("  ›  ")); }

  P().SCREENS["skill-temper"] = function () {
    var s = skillOf(P().nav.skill), cv = P().cv(), c = boon.card && cardById(boon.card), d = P().deck(s.id) || {};
    if (!c) { P().nav.stab = "boon"; MC.render(); return; }
    boonHead(s);
    var opts = bf.ask("boonOptions", c.id), list = opts && opts.temper ? opts.temper : [];
    if (!boon.trait || !list.some(function (t) { return t.trait === boon.trait; })) {
      boon.trait = null;
      for (var i = 0; i < list.length; i++) if (list[i].ok) { boon.trait = list[i].trait; break; }
    }
    var pv = boon.trait ? bf.ask("previewTemper", c.id + "#" + boon.trait) : null;
    MC.text(part(cv, "tc-eb", "eb9 tc-eb"), "TEMPER · SPEND " + (/^[aeiou]/i.test(s.name) ? "AN " : "A ") + upper(s.name) + " BOON");
    crumbs(cv, ["Boon", "Temper", c.baseName || c.name].concat(boon.trait ? [traitName(c, boon.trait)] : []));
    MC.text(part(cv, "tc-pa", "eb9 tc-pa"), "PREVIEW · AFTER");
    var tr = boon.trait, from = pv && pv.ok ? pv.from : null, to = pv && pv.ok ? pv.to : null;
    var after = afterCard(c, function (o) {
      if (!tr || from == null) return;
      for (var k = 0; k < o.traits.length; k++) if (o.traits[k].id === tr) {
        o.traits[k].valueText = scaled(o.traits[k].valueText, from, to);
        if (o.traits[k].boostedText) o.traits[k].boostedText = scaled(o.traits[k].boostedText, from, to);
      }
      o.marks.tempered = {trait: tr};
    });
    previewCard(cv, after, JSON.stringify([P().cardKey(c), tr, from, to, P().iconsKey()]));
    MC.text(part(cv, "tc-hist", "tc-hist"), "Kept through trades and scars. History: “Tempered by " + playerName() + " at " + s.name + " " + s.level + (tr && pv && pv.ok ? " · " + traitName(c, tr) + " " + valueOf(c, tr) + " → " + scaled(valueOf(c, tr), from, to) : "") + "”.");
    var panel = part(cv, "tc-panel", "bf-panel tc-panel", "left:321px;top:" + Y(92) + "px;width:637px;height:494px");
    patch(panel, JSON.stringify([opts, pv, tr, d.tempered, d.temperMax]), function () {
      var h = '<div class="eb9" style="left:20px;top:18px">1 · WHICH TRAIT</div>';
      if (!opts) return h + '<div class="tc-wait">Asking Mettle…</div>';
      for (var k = 0; k < list.length && k < 3; k++) {
        var t = list[k], ct = traitOf(c, t.trait) || {}, on = t.trait === tr;
        h += '<div class="tc-opt' + (on ? " on" : "") + (t.ok ? "" : " off") + '"' + (t.ok ? ' data-btrait="' + esc(t.trait) + '"' : "") + ' style="top:' + (36 + k * 44) + 'px"><i class="rd"></i><span class="n">' + esc(t.name || ct.name || "") +
          '</span><span class="v">' + esc(ct.valueText || "") + (t.ok ? "" : " · top") + '</span><span class="d">' + esc(t.ok ? (ct.description || "") + (ct.cap ? " · cap " + capText(ct) : "") : t.reason || "") + "</span></div>";
      }
      var y0 = 36 + Math.min(3, list.length) * 44 + 14;
      h += '<i class="tc-hr" style="top:' + y0 + 'px"></i><div class="eb9" style="left:20px;top:' + (y0 + 16) + 'px">2 · BEFORE → AFTER</div>';
      if (!tr) return h + '<div class="tc-why" style="top:' + (y0 + 40) + 'px">' + esc("Nothing on this card can rise: " + ((list[0] && list[0].reason) || "every trait is at its band top")) + "</div>" + buttons("temper", false, "");
      if (!pv) return h + '<div class="tc-wait" style="top:' + (y0 + 40) + 'px">Asking Mettle…</div>' + buttons("temper", false, "");
      var ct2 = traitOf(c, tr) || {}, fromT = valueOf(c, tr), toT = pv.ok ? scaled(fromT, pv.from, pv.to) : fromT;
      h += '<div class="tc-from" style="top:' + (y0 + 34) + 'px">' + esc(fromT) + '</div><i class="tc-arrow" style="top:' + (y0 + 46) + 'px"></i><div class="tc-to" style="top:' + (y0 + 34) + 'px">' + esc(toT) + "</div>";
      h += '<div class="tc-ex" style="top:' + (y0 + 38) + 'px">' + esc(pv.ok ? (ct2.name || "") + " rises by a quarter of its range, rounded to its step: " + diffText(fromT, toT) + "." : pv.reason || "") + "</div>";
      if (pv.ok) {
        // The band: rolled value, the Temper's step in Temper blue, the band top (Common: two thirds of the range).
        var cap = ct2.cap || Math.max(pv.to, pv.bandTop || pv.to), W = 597, X = function (v) { return Math.round(W * Math.max(0, Math.min(1, v / Math.max(1e-9, cap)))); };
        var by = y0 + 98;
        h += '<i class="tc-band" style="top:' + by + 'px"></i><i class="tc-band roll" style="top:' + by + "px;width:" + X(pv.from) + 'px"></i><i class="tc-band step" style="top:' + by + "px;left:" + (20 + X(pv.from)) + "px;width:" + Math.max(2, X(pv.to) - X(pv.from)) + 'px"></i>';
        h += tick(X(pv.from), by, "now " + fromT, false) + tick(X(pv.to), by, "after " + toT, true);
        if (pv.bandTop != null) h += tick(X(pv.bandTop), by, (c.rarityName || "") + " top " + scaled(fromT, pv.from, pv.bandTop), false, true);
        if (c.rarity === "common") h += '<div class="tc-cn" style="top:' + (by + 36) + 'px">Common cards stop at two thirds of the range' + (pv.bandTop != null ? " (" + esc(scaled(fromT, pv.from, pv.bandTop)) + ")" : "") + ".</div>";
        var y3 = by + 68;
        h += '<i class="tc-hr" style="top:' + y3 + 'px"></i><div class="eb9" style="left:20px;top:' + (y3 + 16) + 'px">3 · THE LIMITS</div>';
        var tt = (pv.traits || [])[0], lines = [
          [dia(TEMPER, 9), "<b>Once per card.</b> " + esc((c.baseName || c.name) + " can never be tempered again.")],
          [deckPips(d.tempered, d.temperMax, 3), "<b>" + esc(s.name + " deck: Tempered " + d.tempered + " / " + d.temperMax + (c.place === "slotted" ? " → " + (d.tempered + 1) + " / " + d.temperMax : "") + ".") + "</b> " + esc("A fourth tempered card can’t be slotted here.")],
          ['<i class="info"></i>', tt ? "<b>" + esc("Trait total: " + tt.text + ".") + "</b> Character ceilings are untouched." : "Trait totals and character ceilings are untouched."],
          ['<i class="info"></i>', "<b>A scar can still take a tempered trait;</b> fusion keeps the best trait at its tempered value."]];
        for (var q = 0; q < lines.length; q++) h += '<div class="tc-lim" style="top:' + (y3 + 38 + q * 24) + 'px"><span class="ic">' + lines[q][0] + '</span><span class="tx">' + lines[q][1] + "</span></div>";
      }
      return h + buttons("temper", pv.ok, pv.ok ? "Temper " + (ct2.name || "") + " · spend 1 " + s.name + " boon" : "", pv.ok ? "" : pv.reason);
    });
    P().footer(upper(s.name) + " " + s.level + "  ·  " + (d.boons || 0) + ((d.boons || 0) === 1 ? " BOON WAITS" : " BOONS WAIT"), "ENTER CONFIRMS  ·  ESC GOES BACK");
  };
  function tick(x, by, label, above, right) {
    return '<i class="tc-tick" style="left:' + (20 + x) + "px;top:" + (by - 4) + 'px"></i><span class="tc-tl' + (above ? " up" : "") + (right ? " r" : "") + '" style="' + (right ? "right:" + Math.max(20, 617 - 20 - x - 0) + "px" : "left:" + Math.max(0, 20 + x - 18) + "px") + ";top:" + (above ? by - 22 : by + 16) + 'px">' + esc(label) + "</span>";
  }
  function diffText(a, b) { var x = num(a), y = num(b); if (x == null || y == null) return ""; var d = y - x; return (d >= 0 ? "+" : "") + (Math.abs(d - Math.round(d)) > 0.05 ? d.toFixed(1) : Math.round(d)) + String(a).replace(/^[^0-9]*-?\d+(?:\.\d+)?/, ""); }
  function capText(t) { return t.cap != null ? scaled(t.valueText, t.value, t.cap) : ""; }
  function traitOf(c, id) { for (var i = 0; i < (c.traits || []).length; i++) if (c.traits[i].id === id) return c.traits[i]; return null; }
  function traitName(c, id) { var t = traitOf(c, id); return t ? t.name : id; }
  function valueOf(c, id) { var t = traitOf(c, id); return t ? t.valueText : ""; }
  function deckPips(n, max, w) { var h = '<span class="dpips">'; for (var i = 0; i < max; i++) h += '<i class="' + (i < n ? "on" : "") + '" style="width:' + w + 'px"></i>'; return h + "</span>"; }
  function buttons(kind, ok, label, reason) {
    var h = '<div class="tc-back" data-bback="1">Back</div>';
    h += '<div class="tc-go' + (ok ? "" : " off") + '"' + (ok ? ' data-bconfirm="' + kind + '"' : "") + ">" + (kind === "temper" ? dia(ok ? "#0B0B0C" : "#626569", 9) : '<i class="quill dark"></i>') + esc(ok ? label : "Can’t " + kind) + "</div>";
    if (reason) h += '<div class="tc-reason">' + esc(reason) + "</div>";
    return h;
  }

  // ---- Inscribe confirm (2i-5) -----------------------------------------------------------------------------------
  P().SCREENS["skill-inscribe"] = function () {
    var s = skillOf(P().nav.skill), cv = P().cv(), c = boon.card && cardById(boon.card), d = P().deck(s.id) || {};
    if (!c) { P().nav.stab = "boon"; MC.render(); return; }
    boonHead(s);
    var opts = bf.ask("boonOptions", c.id), list = opts && opts.inscribe ? opts.inscribe : [];
    if (!boon.kw || !list.some(function (k) { return k.keyword === boon.kw; })) {
      boon.kw = null;
      for (var i = 0; i < list.length; i++) if (list[i].ok) { boon.kw = list[i].keyword; break; }
    }
    var kw = boon.kw, pv = kw ? bf.ask("previewInscribe", c.id + "#" + kw) : null;
    MC.text(part(cv, "tc-eb", "eb9 tc-eb"), "INSCRIBE · SPEND " + (/^[aeiou]/i.test(s.name) ? "AN " : "A ") + upper(s.name) + " BOON");
    crumbs(cv, ["Boon", "Inscribe", c.baseName || c.name].concat(kw ? [kwName(kw)] : []));
    MC.text(part(cv, "tc-pa", "eb9 tc-pa"), "PREVIEW · AFTER");
    var native = c.keywords && c.keywords[0] ? c.keywords[0] : null;
    var afterKws = native ? [native] : [];
    if (kw) afterKws.push({id: kw, name: kwName(kw)});
    var after = afterCard(c, function (o) { if (kw && pv && pv.ok) { o.keywords = afterKws; o.marks.inscribed = {keyword: kw}; } });
    previewCard(cv, after, JSON.stringify([P().cardKey(c), kw, pv && pv.ok, P().iconsKey()]));
    MC.text(part(cv, "tc-hist", "tc-hist"), "History: “Inscribed " + (kw ? kwName(kw) + " " : "") + "by " + playerName() + " at " + s.name + " " + s.level + "”. Fusion rolls fresh keywords, so an inscription is not carried into a fused card.");
    var panel = part(cv, "tc-panel", "bf-panel tc-panel", "left:321px;top:" + Y(92) + "px;width:637px;height:494px");
    patch(panel, JSON.stringify([opts, pv, kw, d.inscribed, P().cardKey(c)]), function () {
      var h = '<div class="eb9" style="left:20px;top:18px">1 · CHOOSE THE SECOND KEYWORD · ' + esc(upper(s.name)) + "’S POOL</div>";
      if (!opts) return h + '<div class="tc-wait">Asking Mettle…</div>';
      for (var k = 0; k < list.length && k < 6; k++) {
        var o = list[k], on = o.keyword === kw;
        h += '<div class="ic-k' + (on ? " on" : "") + (o.ok ? "" : " off") + '"' + (o.ok ? ' data-bkw="' + esc(o.keyword) + '"' : "") + ' style="left:' + (12 + (k % 3) * 205) + "px;top:" + (38 + Math.floor(k / 3) * 56) + 'px">' + MC.glyph(o.keyword, 16) +
          '<span class="n">' + esc(o.name) + '</span><span class="s">' + esc(o.ok ? kwContext(c, o.keyword) : o.reason || "") + "</span>" + (on ? '<i class="quill"></i>' : "") + "</div>";
      }
      var rows = Math.ceil(Math.min(6, list.length) / 3), y0 = 38 + rows * 56;
      h += '<div class="ic-hex" style="top:' + (y0 + 4) + 'px">Hex is in no pool: it can never be inscribed.</div>';
      y0 += 28;
      h += '<i class="tc-hr" style="top:' + y0 + 'px"></i><div class="eb9" style="left:20px;top:' + (y0 + 16) + 'px">2 · BEFORE → AFTER</div>';
      h += '<div class="ic-ba" style="top:' + (y0 + 36) + 'px">' + chips(c.keywords || [], null) + '<i class="tc-arrow sm"></i>' + chips(kw && pv && pv.ok ? afterKws : c.keywords || [], kw) + "</div>";
      var ly = y0 + 74;
      if (!kw) h += '<div class="tc-why" style="top:' + ly + 'px">No keyword of this deck can go on this card yet.</div>';
      else if (!pv) h += '<div class="tc-wait" style="top:' + ly + 'px">Asking Mettle…</div>';
      else if (!pv.ok) h += '<div class="tc-why" style="top:' + ly + 'px">' + esc(pv.reason || "") + "</div>";
      else {
        var lines = (pv.sets || []).map(function (x) { return x.text; });
        if (pv.byname) lines.push(pv.byname);
        if (!lines.length) lines.push("No set changes tier.");
        for (var q = 0; q < lines.length && q < 3; q++) h += '<div class="ic-l" style="top:' + (ly + q * 20) + 'px">' + MC.previewLine(lines[q], 1, pv).replace(/^<div class="pl[^"]*">/, "").replace(/<\/div>$/, "") + "</div>";
      }
      var y3 = ly + 66;
      h += '<i class="tc-hr" style="top:' + y3 + 'px"></i><div class="eb9" style="left:20px;top:' + (y3 + 16) + 'px">3 · THE LIMITS</div>';
      var lim = [
        ['<i class="quill"></i>', "<b>Once per card.</b> " + esc((c.baseName || c.name) + " can never be inscribed again.")],
        [deckPips(d.inscribed, d.inscribeMax, 8), "<b>" + esc(s.name + " deck: Inscribed " + d.inscribed + " / " + d.inscribeMax + (c.place === "slotted" ? " → " + (d.inscribed + 1) + " / " + d.inscribeMax : "") + ".") + "</b> A second inscribed card can’t be slotted here."],
        ['<i class="info"></i>', "<b>Seed rule: only a keyword already on another card in this deck.</b> Inscribe grows a set you began."],
        ['<i class="info"></i>', "<b>A 6-set always needs cards that came with the keyword.</b>"]];
      for (var r = 0; r < lim.length; r++) h += '<div class="tc-lim" style="top:' + (y3 + 38 + r * 22) + 'px"><span class="ic">' + lim[r][0] + '</span><span class="tx">' + lim[r][1] + "</span></div>";
      return h + buttons("inscribe", !!(pv && pv.ok), kw ? "Inscribe " + kwName(kw) + " · spend 1 " + s.name + " boon" : "", pv && !pv.ok ? pv.reason : "");
    });
    P().footer(upper(s.name) + " " + s.level + "  ·  " + (d.boons || 0) + ((d.boons || 0) === 1 ? " BOON WAITS" : " BOONS WAIT"), "ENTER CONFIRMS  ·  ESC GOES BACK");
  };
  function chips(kws, ins) {
    var h = "";
    for (var i = 0; i < kws.length; i++) h += '<span class="kchip' + (kws[i].id === ins ? " ins" : "") + '">' + MC.glyph(kws[i], 16) + esc(kws[i].name) + (kws[i].id === ins ? '<i class="quill"></i>' : "") + "</span>";
    return h;
  }
  /** Why a pool keyword is a good choice: how many cards of the deck carry it, or which one, or that this card rolled it. */
  function kwContext(c, kw) {
    if ((c.keywords || []).slice(1).some(function (k) { return k.id === kw; })) return "rolled now";
    var d = P().deck(c.skill), n = 0, first = null;
    if (d) for (var i = 0; i < d.row.length; i++) {
      var o = d.row[i].card && cardById(d.row[i].card);
      if (o && o.id !== c.id && (o.keywords || []).some(function (k) { return k.id === kw; })) { n++; if (!first) first = o; }
    }
    return n > 1 ? kwName(kw) + " " + n + " in this deck" : first ? "on " + (first.baseName || first.name) : "in this deck";
  }

  // ---- the Forge (2i-12, 2i-13) ------------------------------------------------------------------------------------
  bf.forgeClear = function (cv) {
    var ids = ["fg-meter", "fg-head", "fg-grid", "fg-sel"];
    for (var i = 0; i < ids.length; i++) { var el = MC.$(ids[i]); if (el && el.parentNode) el.parentNode.removeChild(el); }
  };
  var SHARD_SOURCES = "Drafts from skill 15 · 1   Skill to 20 · 2   Feat or synergy · 1   First 6-set · 2   A skill’s three all held · 3   Boss after its first · 2";
  bf.forgeTab = function (cv) {
    var b = B(), sh = b.shards, fg = b.forge, pool = fg.pool || [], legend = !!fg.legendOffered;
    patch(part(cv, "fg-meter", "bf-panel fg-meter", "left:29px;top:" + Y(68) + "px;width:929px;height:84px"), JSON.stringify([sh, legend, fg.waiting]), function () {
      var f = Math.max(0, Math.min(1, sh.count / Math.max(1, sh.cost))), lad = sh.ladder || [30, 45, 60], h = "";
      h += '<i class="shard big"></i><div class="eb9" style="left:44px;top:16px">MYTHIC SHARDS</div><div class="fg-n">' + sh.count + " / " + sh.cost + '</div><div class="fg-st">' + (legend ? "no Mythic left" : sh.enough ? "ready to forge" : sh.cost - sh.count + " to go") + "</div>";
      h += '<i class="fg-tr"></i><i class="fg-fl" style="width:' + Math.round(370 * f) + 'px"></i>';
      var labels = ["1st forge · " + lad[0], "2nd · " + (lad[1] || lad[0]), "then " + (lad[2] || lad[lad.length - 1]) + " each"], x = 250, ws = [78, 54, 80];
      for (var i = 0; i < 3; i++) { h += '<span class="fg-lad' + (Math.min(2, sh.forges) === i ? " on" : "") + '" style="left:' + x + 'px">' + esc(labels[i]) + "</span>"; x += ws[i] + 6; }
      h += '<div class="eb9" style="left:650px;top:16px">WHERE SHARDS COME FROM</div><div class="fg-src">' + esc(SHARD_SOURCES) + "</div>";
      return h;
    });
    var world = (document.getElementById("world-name") || {}).textContent || "";
    patch(part(cv, "fg-head", "fg-head", "left:29px;top:" + Y(164) + "px"), JSON.stringify([fg.poolText, legend, world, pool.length]),
      '<div class="eb9" style="left:0;top:0">' + esc(upper(pool.length + " DRAWN MYTHICS · " + (legend ? "EVERY ONE HAS A HOLDER" : fg.poolText || ""))) + "</div>" +
      '<div class="fg-hn">' + esc(legend ? "Nothing left to forge" + (world ? " in " + world : "") + "." : "Forge one of a skill you hold at 18. Each exists once.") + "</div>");
    // Columns by skill, three Mythics each.
    var cols = [], by = {};
    for (var i = 0; i < pool.length; i++) { if (!by[pool[i].skill]) { by[pool[i].skill] = []; cols.push(pool[i].skill); } by[pool[i].skill].push(pool[i]); }
    if (forgeSel && !pool.some(function (m) { return m.id === forgeSel && m.state === "forgeable"; })) forgeSel = null;
    if (!forgeSel && !legend) for (var f2 = 0; f2 < pool.length; f2++) if (pool[f2].state === "forgeable") { forgeSel = pool[f2].id; break; }
    patch(part(cv, "fg-grid", "fg-grid", "left:29px;top:" + Y(182) + "px"), JSON.stringify([pool, forgeSel, P().iconsKey(), legend]), function () {
      var h = "";
      for (var c = 0; c < cols.length && c < 8; c++) {
        var s = skillOf(cols[c]), x = c * 117, lv = s ? s.level : 0;
        h += '<div class="fg-col" style="left:' + x + 'px">' + (s ? MC.img(s.icon, 16) : "") + '<span class="n">' + esc(s ? s.name : by[cols[c]][0].skillName) + '</span><span class="lv' + (lv >= 18 ? " ok" : "") + '">' + lv + "</span></div>";
        for (var r = 0; r < by[cols[c]].length && r < 3; r++) {
          var m = by[cols[c]][r], sel = m.id === forgeSel, hue = s ? s.colour : "#9A9DA1";
          var line = m.state === "held" ? m.text : m.state === "forgeable" ? "Forge · " + sh.cost : "needs 18 · you " + lv;
          h += '<div class="fg-t ' + m.state + (sel ? " sel" : "") + (legend ? " lg" : "") + '"' + (m.state === "forgeable" ? ' data-forgepick="' + esc(m.id) + '"' : "") + ' style="left:' + x + "px;top:" + (legend ? 28 + r * 66 : 28 + r * 80) + "px;height:" + (legend ? 60 : 74) + 'px">' +
            '<span class="sg" style="border-color:' + MC.rgba(hue, m.state === "held" ? 0.25 : 0.8) + '">' + MC.img(m.icon, 12) + "</span>" + (sel ? '<i class="ck"></i>' : "") +
            (legend ? '<span class="hd">' + esc(m.holder || "") + '</span><span class="nm lg">' + esc(m.name) + "</span>" : '<span class="nm">' + esc(m.name) + '</span><span class="st">' + esc(line) + "</span>") + "</div>";
        }
      }
      return h;
    });
    var sel = forgeSel && pool.filter(function (m) { return m.id === forgeSel; })[0];
    var bn = b.byname || {}, lg = bn.legend;
    patch(part(cv, "fg-sel", "bf-panel fg-sel" + (legend ? " lg" : ""), "left:29px;top:" + Y(legend ? 414 : 452) + "px;width:929px;height:" + (legend ? 172 : 134) + "px"),
      JSON.stringify([sel, legend, fg.waitingText, sh, fg.legendText, lg, MC.player, bn.worn, P().iconsKey()]), function () {
        if (legend) {
          var h = '<div class="eb9" style="left:20px;top:18px">NO MYTHIC LEFT · A LEGEND INSTEAD</div><div class="lg-t">Your shards buy a legend title and frame</div><div class="lg-s">A cosmetic: no power. One per player; each later forge adds a star, up to three.</div>';
          h += '<div class="lg-o on"><i class="rd"></i>' + esc(lg ? lg.title : String(fg.legendText || "").indexOf("legend") >= 0 ? "the Legend of " + (world || "this world") : fg.legendText || "") + "</div>";
          var can = sh.enough && (!lg || lg.stars < 3);
          h += '<div class="lg-go' + (can ? "" : " off") + '"' + (can ? ' data-legend="1"' : "") + ">★ " + esc(lg && lg.stars >= 3 ? "Three stars: the legend is whole" : (lg ? "Add a star · " : "Take the legend · ") + sh.cost + " shards") + "</div>";
          h += '<div class="lg-card">' + '<span class="lg-stars">' + stars(Math.min(3, (lg ? lg.stars : 0) + (can ? 1 : 0)) || 1) + "</span>" + '<div class="ch-crest sm">' + (crestCard() ? MC.img(crestCard().icon, 24) : "") + "</div><div class=\"nm\">" + esc(playerName()) +
            '</div><div class="tl">' + esc(lg ? lg.title : "the Legend of " + (world || "this world")) + '</div><div class="bn">' + esc(bn.worn ? bn.worn.text : "") + '</div><div class="sb">' + esc(bgName() + " · LV " + M().character.level) + "</div></div>";
          return h;
        }
        if (!sel) {
          return '<div class="eb9" style="left:20px;top:18px">YOUR SHARDS WAIT</div><div class="fs-n" style="top:38px">' + esc(fg.waitingText || "No unclaimed Mythic of a skill you hold at 18 yet.") + "</div>" +
            '<div class="fs-go off">Nothing to forge yet</div>';
        }
        var s2 = skillOf(sel.skill), can2 = sel.canForgeNow;
        var h2 = '<div class="fs-tile">' + MC.img(sel.icon, 32) + "</div>";
        h2 += '<div class="eb9" style="left:100px;top:22px">' + esc(upper((s2 ? s2.name : sel.skillName) + " · DRAWN MYTHIC · ONE OF A KIND")) + '</div><div class="fs-n">' + esc(sel.name) + '</div><div class="fs-e">' + esc(sel.effect) + "</div>";
        h2 += '<div class="fs-h">Its history will read “Forged by ' + esc(playerName()) + "”, in the Hall too.</div>";
        h2 += '<div class="fs-go' + (can2 ? "" : " off") + '"' + (can2 ? ' data-forge="' + esc(sel.id) + '"' : "") + ">" + dia(can2 ? "#0B0B0C" : "#626569", 9) + esc(can2 ? "Forge " + sel.name + " · " + sh.cost + " shards" : !fg.atShrine ? "Forge at a Card Shrine" : sh.count < sh.cost ? sh.cost - sh.count + " more shards" : "Can’t forge") + "</div>";
        var next = (sh.ladder || [])[Math.min((sh.ladder || []).length - 1, sh.forges + 1)];
        h2 += '<div class="fs-nt">' + esc((next ? "The next forge costs " + next + ". " : "") + "Two Mythics slotted at most, never two in one deck.") + "</div>";
        return h2;
      });
  };

  // ---- the binder: Completes a set, If slotted…, marks ------------------------------------------------------------
  /** A binder card whose slotting would raise a set's tier (counting no replacement; the 6 stays asleep where it sleeps). */
  bf.completesSet = function (c) {
    if (!c || c.place !== "binder" || MC.isMythic(c) || !c.canSlot) return false;
    for (var i = 0; i < (c.keywords || []).length; i++) {
      var s = setById(c.keywords[i].id);
      if (!s) continue;
      var next = s.count < 2 ? 2 : s.count < 4 ? 4 : s.count < 6 ? 6 : 0;
      if (next && s.count + 1 >= next && !(next === 6 && s.asleep)) return true;
    }
    return false;
  };
  bf.completesCount = function (fixed) {
    var all = P().cards(), n = 0;
    for (var i = 0; i < all.length; i++) if ((!fixed || all[i].skill === fixed) && bf.completesSet(all[i])) n++;
    return n;
  };
  bf.slotPreviewKey = function (c) { var a = c.place === "binder" && !MC.isMythic(c) ? answers["previewSlot|" + c.id] : null; return a ? JSON.stringify(a.lines || a.reason || 0) : 0; };
  bf.detailExtra = function (c, y, lines) {
    var h = "";
    for (var l = 0; l < lines.length && l < 2; l++) { h += '<div class="bd-h" style="top:' + y + 'px">' + esc(lines[l]) + "</div>"; y += 19; }
    if (c.place === "binder" && !MC.isMythic(c)) {
      var pv = bf.ask("previewSlot", c.id);
      h += '<div class="eb9" style="left:15px;top:' + (y + 4) + 'px">IF SLOTTED…</div>';
      y += 22;
      if (pv && pv.ok && pv.lines) { for (var i = 0; i < pv.lines.length && i < 3; i++) { h += '<div class="bd-pv" style="top:' + y + 'px">' + MC.previewLine(pv.lines[i], i, pv) + "</div>"; y += 18; } }
      else h += '<div class="bd-h" style="top:' + y + 'px">' + esc(pv ? pv.reason || "Nothing changes" : "…") + "</div>", y += 18;
      y += 4;
    }
    var mk = [];
    if (c.marks && c.marks.tempered) mk.push(["dia", c.marks.tempered.text]);
    if (c.marks && c.marks.inscribed) mk.push(["quill", c.marks.inscribed.text]);
    var sg = c.marks && c.marks.signed ? c.marks.signed : [];
    for (var s = 0; s < sg.length; s++) mk.push(["sigc", sg[s]]);
    if (mk.length || c.code) {
      h += '<div class="eb9" style="left:15px;top:' + (y + 4) + 'px">MARKS</div>' + (c.code ? '<div class="bd-code2" style="top:' + (y + 4) + 'px">' + esc(c.code) + "</div>" : "");
      y += 22;
      for (var m = 0; m < mk.length && m < 3; m++) { h += '<div class="bd-mk" style="top:' + y + 'px"><i class="mk ' + mk[m][0] + '"></i>' + esc(String(mk[m][1] || "").replace(/^[◆✎]\s*/, "")) + "</div>"; y += 18; }
      if (!mk.length) { h += '<div class="bd-h" style="top:' + y + 'px">None yet</div>'; y += 18; }
    }
    return {html: h, y: y};
  };

  // ---- milestone toasts and banners (2i-6, 2i-7) --------------------------------------------------------------------
  var seenMoments = {}, toastQ = [], bannerQ = [], toastUp = null, toastTimer = null, firstState = true;
  function momentKey(m) { return [m.kind, m.perk || m.skill || "", m.tier, m.text || m.title || ""].join("|"); }
  bf.state = function (state) {
    // The previews are worked out from the synced cards: when they change, the answers are asked again.
    var b = state.cards && state.cards.build;
    var sig = state.cards ? JSON.stringify([state.cards.binder.cards.length, state.cards.decks, b && b.boons, b && b.focus, state.cards.sets.map(function (x) { return x.count; }),
      state.cards.binder.cards.map(function (c) { return c.id + (c.marks && c.marks.tempered ? "t" : "") + (c.marks && c.marks.inscribed ? "i" : "") + c.place; }).join(",")]) : "";
    if (sig !== cardsSig) { cardsSig = sig; for (var a in answers) stale[a] = answers[a]; answers = {}; }
    var ms = state.moments || [];
    for (var i = 0; i < ms.length; i++) {
      var m = ms[i];
      if (!m || !m.milestone) continue;
      var k = momentKey(m);
      if (seenMoments[k]) continue;
      seenMoments[k] = true;
      if ((m.ageMs || 0) > 4000) continue;
      m.__at = Date.now() - (m.ageMs || 0);
      if (m.big) bannerQ.push(m); else toastQ.push(m);
    }
    firstState = false;
    if (pendingSwap) trySwap(state);
    if (b && b.guide) guideState(b.guide);
  };
  function toastIcon(m) {
    var k = m.kind, p = m.perk || "";
    if (k === "boon") return dia(AMBER, 12);
    if (k === "forge_ready") return dia(IVORY, 12);
    if (/temper/i.test(m.title || "")) return dia(TEMPER, 12);
    if (/inscrib/i.test(m.title || "")) return '<i class="quill big"></i>';
    if (MC.glyphIndex(p) >= 0) return MC.glyph(p, 24);
    var all = P().cards();
    for (var i = 0; i < all.length; i++) if (all[i].perk === p) return MC.img(all[i].icon, 24);
    return dia(m.colour || CHAMP, 12);
  }
  function showToast() {
    // One at a time; held while a draft, a reveal or a banner is up (it shows when the page is the player's again).
    if (toastUp || !toastQ.length || !MC.attached || P().kind() !== "classes" || MC.ov.active()) return;
    // Mettle's own HUD toast shows every milestone once the hub closes; the page shows the ones that are news now.
    while (toastQ.length && Date.now() - toastQ[0].__at > 12000) toastQ.shift();
    if (!toastQ.length) return;
    var m = toastQ.shift(), el = part(P().root(), "bf-toast", "bf-toast");
    var title = String(m.title || ""), cut = title.indexOf(" · "), lead = cut > 0 ? title.slice(0, cut) : title, rest = cut > 0 ? title.slice(cut + 3) : "";
    var accent = /temper|inscrib/i.test(title) ? TEMPER : m.kind === "boon" || m.kind === "card" ? AMBER : m.kind === "forge_ready" ? IVORY : m.kind === "signature" ? CHAMP : "#EDEEEF";
    var sub = m.subtitle || (m.text && m.text !== title ? m.text : "");
    toastUp = m;
    el.style.opacity = "1";
    el.style.borderColor = MC.rgba(accent, m.kind === "set" && m.tier >= 4 || m.kind === "forge_ready" ? 0.55 : 0.32);
    el.innerHTML = '<div class="w" style="background:' + MC.rgba(accent, 0.12) + '">' + toastIcon(m) + '</div><div class="t"><span style="color:' + accent + '">' + esc(lead) + "</span>" + (rest ? " · " + esc(rest) : "") + "</div>" + (sub ? '<div class="s">' + esc(sub) + "</div>" : "");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.style.opacity = "0"; toastUp = null; showToast(); }, toastQ.length ? 2000 : 3200);
  }
  bf.toasts = function () { return {up: toastUp ? toastUp.title : null, queued: toastQ.length, banners: bannerQ.length}; };  // tests
  /** A big milestone waits for nothing else on the page, then shows its banner. True when one opened. */
  bf.banner = function () {
    // A banner is a moment's: one that waited behind a draft or a reveal for long is let go (Mettle's HUD toast tells it).
    while (bannerQ.length && Date.now() - bannerQ[0].__at > 8000) bannerQ.shift();
    if (!bannerQ.length || MC.ov.active()) return false;
    var m = bannerQ.shift(), b = B() || {}, bn = b.byname || {}, worn = bn.worn, crest = crestCard(), c = M().character, h = '<div class="ov-dim"></div>';
    var byname = m.kind === "byname", six = !byname;
    var plate = '<div class="bn-plate" style="top:' + (byname ? 282 : 330) + 'px"><div class="cr">' + (crest ? MC.img(crest.icon, 32) : "") + '</div><div class="nm">' + esc(playerName()) + '</div><div class="sb">' + esc(bgName() + " · Character LV " + c.level) +
      '</div><div class="tp">' + tierPips(bn.tier || 0, CHAMP, 22) + "</div></div>";
    if (byname) {
      h += '<div class="bn-eb">' + esc(upper("Byname  ·  " + (worn ? worn.tierLine : "Tier " + ROMAN[m.tier]))) + '</div><div class="bn-t">' + MC.pix(worn ? worn.text : String(m.text || "").replace(/^You are\s*/, ""), 4, "#F4EEE6", 900) + "</div>";
      h += '<div class="bn-l">' + esc(worn ? worn.line : "Your build has a name") + "</div>" + plate + '<div class="bn-k" style="top:350px">Any key skips</div>';
    } else {
      var set = null, title = String(m.text || m.title || "");
      for (var i = 0; i < M().cards.sets.length; i++) if (title.indexOf(M().cards.sets[i].name) >= 0) set = M().cards.sets[i];
      var six6 = set && set.bonuses ? set.bonuses.filter(function (x) { return x.tier === 6; })[0] : null;
      var name = six6 && /:/.test(six6.description) ? six6.description.slice(0, six6.description.indexOf(":")) : title.split(" · ")[0];
      h += '<div class="bn-gl">' + (set ? MC.glyph(set, 64) : "") + '</div><div class="bn-eb six">' + esc(upper("First 6-set  ·  " + (set ? set.name + " 6" : title))) + '</div><div class="bn-t six">' + MC.pix(name, 4, "#F4EEE6", 900) + "</div>";
      h += '<div class="bn-l six">' + esc(six6 ? six6.description.replace(/^[^:]*:\s*/, "") : m.subtitle || "") + "</div>" + plate + '<div class="bn-k" style="top:398px">+2 Mythic shards  ·  any key skips</div>';
    }
    var w = MC.win();
    var fx = [{i: "bnb", k: "bloom", x: w.x + 493, y: w.y + (byname ? 200 : 210), w: 760, h: 360, c: byname ? CHAMP : "#FFFFFF", a: byname ? 0.2 : 0.16, q: 250}];
    if (six) fx.push({i: "bnm", k: "bloom", x: w.x + 493, y: w.y + 150, w: 300, h: 300, c: IVORY, a: 0.35, q: 250, p: 800});
    MC.sound("set_complete", "");
    return MC.ov.banner(h, fx, 2500);
  };

  // ---- the guide (2i-8 to 2i-11) ------------------------------------------------------------------------------------
  var guide = null, kwSent = false, kwHover = null;
  function guideState(g) { guide = g; }
  var ANCHORS = {
    xp_chip: ".sk-xp, .o-strip",
    hand: "#dr-hand .hc[data-hand=\"1\"], #dr-hand .hc",
    keep_slot: "[data-keepslot]:not(.off)",
    deck_row: "#dk-row",
    keyword_badge: "[data-kwhover].kwon, .hc-kw.kwon",
    card_preview: ".dr-pvb",
    set_tracker: ".dk-sets .sc-set.on, #ch-sets .ss-set.big, .dk-sets",
    card: "",
    free_swaps: ".dk-swaps",
    boon_badge: ".hd-boon"
  };
  function anchorEl(h) {
    if (h.anchor === "card") return h.card ? document.querySelector('[data-bcard="' + h.card + '"], [data-dcard="' + h.card + '"], [data-scard="' + h.card + '"]') : null;
    if (h.anchor === "card_preview") {
      // The preview line of the card that would make the first set.
      var bs = document.querySelectorAll(".dr-pvb");
      for (var i = 0; i < bs.length; i++) if (/→ 2/.test(bs[i].textContent)) return bs[i];
    }
    var sel = ANCHORS[h.anchor];
    if (!sel) return null;
    var all = document.querySelectorAll(sel);
    for (var k = 0; k < all.length; k++) if (all[k].offsetWidth && all[k].offsetHeight && visible(all[k])) return all[k];
    return null;
  }
  function visible(el) { for (var e = el; e && e !== document.body; e = e.parentElement) if (e.style && e.style.display === "none") return false; return true; }
  var chipKey = "";
  function guideChip() {
    var root = P().root(), el = part(root, "g-chip", "g-chip"), h = guide && guide.on && guide.hint, a = h ? anchorEl(h) : null;
    if (!h || !a || P().kind() !== "classes") {
      if (el.style.display !== "none") { el.style.display = "none"; el.__k = null; chipKey = ""; }
      return;
    }
    var r = a.getBoundingClientRect(), o = root.getBoundingClientRect(), z = window.HUBZ || 1;
    var ax = (r.left - o.left) / z, ay = (r.top - o.top) / z, aw = r.width / z, ah = r.height / z;
    var w = Math.max(230, Math.min(330, 64 + String(h.text).length * 5.6)), tall = (h.skip ? 30 : 0) + ((h.buttons || []).length ? 40 : 0);
    var hh = 34 + tall, below = ay + ah + 10 + hh < 610, x = Math.max(12, Math.min(975 - w, ax + aw / 2 - w / 2)), y = below ? ay + ah + 10 : ay - hh - 10;
    var key = JSON.stringify([h.id, h.text, h.skip, h.buttons, Math.round(x), Math.round(y)]);
    if (key === chipKey) return;
    chipKey = key;
    el.setAttribute("data-gchip", "1");
    el.setAttribute("style", "left:" + Math.round(x) + "px;top:" + Math.round(y) + "px;width:" + Math.round(w) + "px");
    var px = Math.max(14, Math.min(w - 14, ax + aw / 2 - x));
    var html = '<i class="ptr ' + (below ? "up" : "dn") + '" style="left:' + Math.round(px - 6) + 'px"></i><div class="gr"><i class="cardback"></i><span class="tx">' + esc(h.text) + '</span><span class="x" data-gx="1">×</span></div>';
    if ((h.buttons || []).length) {
      html += '<div class="gb">';
      for (var i = 0; i < h.buttons.length; i++) html += '<span class="b' + (i === 0 ? " pri" : "") + '" data-gbtn="' + esc(h.buttons[i].id + "#" + (h.buttons[i].keyword || "")) + '">' + (h.buttons[i].id === "focus" ? '<i class="fring"></i>' : "") + esc(h.buttons[i].text) + "</span>";
      html += "</div>";
    }
    if (h.skip) html += '<div class="gs"><span class="sk" data-gskip="1">' + esc(h.skipText || "Skip the guide") + '</span><span class="n">hint ' + h.id + " of 10</span></div>";
    el.innerHTML = html;
  }
  bf.guideDebug = function () { var c = MC.$("g-chip"); return guide && guide.hint ? guide.hint.key + (c && c.style.display !== "none" ? ":shown" : ":hidden") : "none"; };

  bf.hover = function (target) {
    var el = P().closest(target, "data-kwhover");
    var id = el ? el.getAttribute("data-kwhover") : null;
    if (el && id === "1") id = el.getAttribute("data-pin");
    if (id === kwHover) return;
    if (kwHover && guide && guide.hint && guide.hint.key === "keyword") MC.act("guideAdvance", "unhover");
    var old = document.querySelector(".kwon");
    if (old) old.classList.remove("kwon");
    kwHover = id;
    if (el) el.classList.add("kwon");
    if (id && guide && guide.on && !kwSent && (guide.seen || []).indexOf(5) < 0) { kwSent = true; MC.act("guideAdvance", "keyword"); }
  };

  // ---- the one-away pulse (native, the set tracker's "3 / 4") --------------------------------------------------------
  bf.pulses = function (cv) {
    var out = [], els = cv.querySelectorAll(".sc-set.away, .ss-set.away");
    for (var i = 0; i < els.length && i < 4; i++) {
      var a = MC.abs(els[i]), sz = MC.size(els[i]);
      out.push({i: "aw" + i, k: "pulse", x: a.x, y: a.y, w: sz.w, h: sz.h, c: TEMPER, a: 0.14});
    }
    return out;
  };

  // ---- each render: the guide's chip, the milestone toast, the Deck opened ------------------------------------------
  var lastScreen = "";
  bf.render = function (id) {
    if (id !== lastScreen) {
      if (id !== "skill-temper" && id !== "skill-inscribe") MC.fxSet("boon", []);
      if (id === "skill-deck" && guide && guide.hint && guide.hint.key === "slotted") MC.act("guideAdvance", "deck");
      lastScreen = id;
    }
    part(P().root(), "bf-toast", "bf-toast");  // made with the screen, so a toast later restyles nothing around it
    guideChip();
    showToast();
  };
  /** Tests: drops the toasts waiting and the one up (the audits measure an idle page). */
  bf.clearToasts = function () {
    toastQ = [];
    clearTimeout(toastTimer);
    var el = MC.$("bf-toast");
    if (el && toastUp) el.style.opacity = "0";
    toastUp = null;
  };
  // ---- the free swap after "Keep & swap" ----------------------------------------------------------------------------
  var pendingSwap = null;
  bf.swapAfterKeep = function (c, pl) {
    var ids = {}, all = P().cards();
    for (var i = 0; i < all.length; i++) ids[all[i].id] = true;
    pendingSwap = {perk: c.perk, replaces: pl.replaces, slot: pl.slot, ids: ids, at: Date.now()};
  };
  function trySwap(state) {
    var all = state.cards.binder.cards;
    if (Date.now() - pendingSwap.at > 15000) { pendingSwap = null; return; }
    for (var i = 0; i < all.length; i++) {
      var c = all[i];
      if (pendingSwap.ids[c.id] || c.perk !== pendingSwap.perk || c.place !== "binder") continue;
      var sw = pendingSwap;
      pendingSwap = null;
      MC.act("unslotCards", sw.replaces);
      MC.act("slotCard", c.id + "#" + sw.slot);
      MC.sound("slot", "");
      return;
    }
  }

  // ---- clicks and Esc --------------------------------------------------------------------------------------------
  bf.guideClick = function (t) {
    var cl = P().closest, el, v;
    if ((el = cl(t, "data-gx"))) { MC.act("guideAdvance", "dismiss"); chipKey = ""; return true; }
    if ((el = cl(t, "data-gskip"))) { MC.act("guideSkip", ""); chipKey = ""; return true; }
    if ((el = cl(t, "data-gbtn"))) {
      v = el.getAttribute("data-gbtn").split("#");
      if (v[0] === "focus" && v[1]) { MC.act("setFocus", v[1]); MC.act("guideAdvance", "focus"); } else MC.act("guideAdvance", "not_now");
      chipKey = "";
      return true;
    }
    return !!cl(t, "data-gchip");  // the chip itself: a click on it does nothing underneath
  };
  bf.click = function (t) {
    var cl = P().closest, nav = P().nav, el, v;
    if ((el = cl(t, "data-boon"))) { v = el.getAttribute("data-boon"); if (v) { nav.view = "skill"; nav.skill = v; nav.stab = "boon"; } return true; }
    if ((el = cl(t, "data-bchoice"))) {
      v = el.getAttribute("data-bchoice");
      if (v === "reroll") { MC.act("boonReroll", boon.skill); boon.choice = null; nav.stab = "deck"; return true; }
      boon.choice = boon.choice === v ? null : v;
      boon.card = null; boon.trait = null; boon.kw = null; boon.scroll = 0;
      return true;
    }
    if ((el = cl(t, "data-bcardpick"))) { v = el.getAttribute("data-bcardpick"); boon.card = boon.card === v ? null : v; boon.trait = null; boon.kw = null; return true; }
    if ((el = cl(t, "data-bnext"))) { if (boon.card && boon.choice) nav.stab = boon.choice; return true; }
    if ((el = cl(t, "data-bback"))) { back(); return true; }
    if ((el = cl(t, "data-btrait"))) { boon.trait = el.getAttribute("data-btrait"); return true; }
    if ((el = cl(t, "data-bkw"))) { boon.kw = el.getAttribute("data-bkw"); return true; }
    if ((el = cl(t, "data-bconfirm"))) { confirm(el.getAttribute("data-bconfirm")); return true; }
    if ((el = cl(t, "data-takebyname"))) { MC.act("takeByname", ""); return true; }
    if ((el = cl(t, "data-keepmine"))) { var bn = B().byname; if (bn) keptMine[bn.offerText] = true; return true; }
    if ((el = cl(t, "data-sigdeck"))) { nav.view = "skill"; nav.skill = el.getAttribute("data-sigdeck"); nav.stab = "deck"; P().st.deck.sel = null; P().st.deck.pop = null; return true; }
    if ((el = cl(t, "data-setsig"))) {
      v = el.getAttribute("data-setsig");
      if (v.indexOf("clear#") === 0) MC.act("clearSignature", v.slice(6)); else MC.act("setSignature", v);
      P().st.deck.pop = null;
      return true;
    }
    if ((el = cl(t, "data-forgepick"))) { forgeSel = el.getAttribute("data-forgepick"); return true; }
    if ((el = cl(t, "data-forge"))) { MC.act("forgeMythic", el.getAttribute("data-forge")); return true; }
    if ((el = cl(t, "data-legend"))) { MC.act("forgeLegend", ""); return true; }
    if ((el = cl(t, "data-ssid"))) return false;
    return false;
  };
  function confirm(kind) {
    var c = boon.card;
    if (!c) return;
    if (kind === "temper" && boon.trait) MC.act("temperCard", c + "#" + boon.trait);
    else if (kind === "inscribe" && boon.kw) MC.act("inscribeCard", c + "#" + boon.kw);
    else return;
    boon.choice = null; boon.card = null; boon.trait = null; boon.kw = null;
    MC.fxSet("boon", []);
    P().nav.stab = "deck";
  }
  function back() {
    var nav = P().nav;
    if (nav.stab === "temper" || nav.stab === "inscribe") { nav.stab = "boon"; MC.fxSet("boon", []); }
    else if (nav.stab === "boon") { if (boon.card) boon.card = null; else if (boon.choice) boon.choice = null; else nav.stab = "deck"; }
  }
  /** Esc inside the boon flow steps back (a step is a popover for Esc). */
  bf.escape = function () {
    var nav = P().nav;
    if (nav.view === "skill" && (nav.stab === "boon" || nav.stab === "temper" || nav.stab === "inscribe")) { back(); return true; }
    return false;
  };
  bf.modal = function () {
    var nav = P().nav;
    return nav.view === "skill" && (nav.stab === "boon" || nav.stab === "temper" || nav.stab === "inscribe") && P().screen().indexOf("skill-") === 0;
  };
  /** Keys in the boon flow: 1-3 choose, Enter confirms. True when used. */
  bf.key = function (k) {
    var nav = P().nav;
    if (nav.view !== "skill") return false;
    if (nav.stab === "boon" && k >= "1" && k <= "3") {
      var ch = (B().boons.choices || [])[+k - 1], d = P().deck(nav.skill);
      if (ch && ch.enabled && d && d.boons > 0) { if (ch.id === "reroll") { MC.act("boonReroll", nav.skill); nav.stab = "deck"; } else { boon.choice = ch.id; boon.card = null; } MC.render(); }
      return true;
    }
    if ((nav.stab === "temper" || nav.stab === "inscribe") && k === "Enter") {
      var go = document.querySelector("[data-bconfirm]");
      if (go) { confirm(go.getAttribute("data-bconfirm")); MC.render(); }
      return true;
    }
    return false;
  };
  bf.wheel = function (which, step) { if (which === "boonpick") { boon.scroll = Math.max(0, boon.scroll + step); return true; } return false; };
})();
