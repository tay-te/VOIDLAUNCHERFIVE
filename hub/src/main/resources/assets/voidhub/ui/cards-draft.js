// VOID Mettle cards v2: the moments over the Classes page. Figma 295:2 §2f (direction D: D1 hand 525:20157, D3 hover
// 525:20667, D4 pick 525:20922; Mythic M1-M3 533:20975/20977/20979) and §2g (set complete 539:23542).
//
//   MC.ov.draft(skill)  deal the skill's hand: three cards fan in 80 ms apart; hover lifts a card onto its hot frame;
//                       a Mythic offer turns over first and is claimed on top of the keep (Mettle's claimMythic);
//                       1-3 or a click keeps one (it flares, the other two burn away); R redeals; Esc keeps the draft
//   MC.ov.reveal(m)     a Mythic turns over (dark, tremble, cracks, swell, flip into a burst, the aura settles)
//   MC.ov.setDone(set)  a set reached a 2 / 4 / 6 tier
//
// Timing follows the card sounds (mettle/docs/SOUNDS.md): Mettle plays them, the page only says when its animation
// starts (deal, hover, unhover, mythic_reveal, set_complete, stop). One overlay at a time, in #c-ov.
(function () {
  "use strict";

  var MC = window.MC, esc = MC.esc;
  var ov = MC.ov = {};
  var cur = null;        // the overlay up: {kind, ...}
  var timers = [];

  function later(ms, fn) { var t = setTimeout(fn, ms); timers.push(t); return t; }
  function clearTimers() { for (var i = 0; i < timers.length; i++) clearTimeout(timers[i]); timers = []; }

  function host() { return MC.$("c-ov"); }
  function setUi() {
    MC.toggle(MC.$("classes"), "ov-up", !!cur);
    MC.send({type: "mettleUi", modal: !!cur, text: !!MC.textFocus});
  }

  function skill(id) {
    var M = MC.M;
    for (var i = 0; M && i < M.skills.length; i++) if (M.skills[i].id === id) return M.skills[i];
    return null;
  }
  function waiting() { return MC.M && MC.M.draft ? MC.M.draft.waiting || 0 : 0; }
  function handKey(s) { return s ? JSON.stringify(s.hand || []) : ""; }

  ov.active = function () { return !!cur; };
  ov.kind = function () { return cur ? cur.kind : null; };
  /** The hovered hand card's index, or -1 (tests). */
  ov.hovered = function () { return cur && cur.kind === "draft" ? cur.hover : -1; };

  /** Closes whatever is up. quiet: no stop (the burn-away is still sounding after a pick). */
  ov.close = function (quiet) {
    if (!cur) return;
    var was = cur;
    cur = null;
    clearTimers();
    var el = host();
    if (el) { el.innerHTML = ""; el.className = ""; }
    MC.fxSet("draft", []);
    MC.toggle(MC.$("classes"), "ov-up", false);
    if (!quiet) MC.sound("stop", "");
    if (was.kind === "draft" && MC.onDraftClosed) MC.onDraftClosed(was);
    setUi();
    if (MC.render) MC.render();
  };

  // ---- shared chrome -----------------------------------------------------------------------------------------
  function chip(s) {
    var hue = s ? s.colour : "#9A9DA1";
    return '<div class="hd-chip ov-chip"><div class="sig" style="border-color:' + MC.rgba(hue, 0.55) + '">' + MC.img(s ? s.icon : "", 16) + '</div><span class="nm">' + esc(s ? s.name : "") +
      '</span><span class="lv" style="color:' + hue + '">LV ' + (s ? s.level : 0) + "</span></div>";
  }
  function badge(n) {
    if (n <= 0) return "";
    // The badge counts every skill's drafts (Mettle's draft.waiting), so it says so: the title's "draft 1 of 1" is this skill's.
    return '<div class="ov-badge"><i class="backs"></i><span>' + n + (n === 1 ? " more draft across your skills" : " more drafts across your skills") + "</span></div>";
  }
  function title(eb, text, ebColour, t) {
    return '<div class="ov-eb" style="color:' + (ebColour || "#FF7A4A") + '">' + esc(eb) + '</div><div class="ov-title">' + MC.pix(text, t || 3, "#F4EEE6", 900) + "</div>";
  }

  // ---- the draft -----------------------------------------------------------------------------------------------
  // The page draws each card once and keeps still. Everything that moves (the burning edge, embers, the hot frame,
  // the hovered card's lift and haze, the others cooling, the pick's flare, the burn-away's fire and ash) is a layer
  // reported to Java (MC.fxSet "draft") and drawn natively over the page; only the burn mask eating the two faces is
  // a short CSS one-shot here.
  // The hand sits low enough that a lifted card's flames (16 px lift, 5 % grow, 28 px of fire) stay under the title.
  var TOP = 192, gen = 0, OLD_BOTTOM = 175 + 328;
  /** What burns when one of n cards is kept. */
  function burnText(n) { return n >= 3 ? "THE OTHER TWO BURN" : n === 2 ? "THE OTHER ONE BURNS" : "NOTHING ELSE BURNS"; }
  function keepText(n) { return n >= 3 ? "KEEP ONE, THE OTHER TWO BURN" : n === 2 ? "KEEP ONE, THE OTHER ONE BURNS" : "KEEP IT"; }
  /**
   * Fits the hand's cards to their content (no empty lower half): every card takes the tallest card's height, never
   * under 248 or over 328, in 4 px steps so the native fire's texels line up. Moves the keys, the redeal and the toast
   * with the hand's bottom, and the glow behind the hand with it. Returns the height.
   */
  function fitHand(els, minH) {
    var H = 248;
    for (var k = 0; k < els.length; k++) {
      var flow = els[k].querySelector(".hc-flow"), need = els[k].querySelector(".hc-need");
      if (!flow) continue;
      H = Math.max(H, 128 + flow.scrollHeight + (need ? 34 : 0) + 24);
    }
    H = Math.min(328, Math.ceil(H / 4) * 4);
    for (var j = 0; j < els.length; j++) if (els[j].querySelector(".hc-flow")) els[j].style.height = H + "px";
    cur.H = H;
    var bottom = TOP + Math.max(H, minH || 0), dy = bottom - OLD_BOTTOM;
    var keys = MC.$("dr-keys"), act = MC.$("dr-act"), halo = MC.$("dr-halo");
    if (keys) keys.style.top = dy + "px";
    if (act) act.style.top = dy + "px";
    if (halo) { halo.style.top = (TOP - 50) + "px"; halo.style.height = (bottom - TOP + 60) + "px"; }
    return H;
  }
  /** Card x for a hand of n (3 fills the window as in D1; fewer centre). */
  function leftOf(i, n) { return Math.round(493.5 - (n * 248 + (n - 1) * 30) / 2) + i * 278; }
  function burnDelay() { return (MC.M.draft && MC.M.draft.sound && MC.M.draft.sound.burnAfterPickMs) || 260; }
  function tintOf(c) { return c.cursed ? MC.CURSED : c.colour || "#9A9DA1"; }

  /** The hand's native layers, from the overlay's state (hover, pick, deal), in draw order. */
  function draftFx() {
    if (!cur || cur.kind !== "draft") return;
    var w = MC.win(), items = [], n = Math.min(3, cur.hand.length);
    for (var k = 0; k < n; k++) {
      var c = cur.hand[k];
      if (!c || cur.pos[k] == null || MC.isMythic(c) || cur.faded) continue;
      var x = w.x + cur.pos[k], y = w.y + TOP, H = cur.H || 328, tint = tintOf(c), id = "g" + cur.gen + "c" + k;
      if (cur.burnt && cur.burnt[k]) {
        items.push({i: id + "-bf", k: "bfront", x: x, y: y, h: H, c: tint, d: burnDelay()});
        items.push({i: id + "-ash", k: "ash", x: x, y: y, h: H, d: burnDelay()});
        continue;
      }
      var hov = cur.picked < 0 && cur.hover === k, kept = cur.picked === k, cool = cur.picked < 0 && cur.hover >= 0 && !hov;
      if (hov || kept) {
        items.push({i: id + "-b", k: "bloom", x: x + 124, y: y + H / 2, w: 348, h: H + 80, c: tint, a: kept ? 0.55 : 0.34, q: 120});
        items.push({i: id, k: "lift", x: x, y: y, w: 248, h: H, p: kept ? -8 : -16, q: kept ? 1.04 : 1.05});
      }
      if (cool) items.push({i: id + "-cool", k: "rect", x: x - 1, y: y - 1, w: 250, h: H + 2, c: "#0B0A0B", a: 0.6, q: 120});
      var edge = MC.edgeOf(c), dense = hov || edge === "epic" || edge === "cursed", d = cur.dealing ? k * 80 + 240 : 0;
      items.push({i: id + "-e", k: "edge-" + edge, x: x, y: y, h: H, c: tint, a: cool ? 0.4 : 1, d: d});
      items.push({i: id + "-m" + (dense ? "d" : "s"), k: dense ? "embers-dense" : "embers-sparse", x: x, y: y, h: H, c: tint, a: cool ? 0.4 : 1, d: d});
      if (hov) items.push({i: id + "-h", k: "edge-hot", x: x, y: y, h: H, c: tint});
      if (kept) items.push({i: id + "-f", k: "flare", x: x, y: y, h: H, c: tint});
    }
    MC.fxSet("draft", items.concat(cur.mythicFx || []));
  }
  ov.refx = function () { if (cur && cur.kind === "draft") draftFx(); else if (cur && cur.mythicFx) MC.fxSet("draft", cur.mythicFx); };

  /** Deals the skill's hand. A hand with a Mythic offer turns that card over first. again: the same draft, re-shown. */
  ov.draft = function (skillId, again) {
    var s = skill(skillId);
    if (!s) return;
    if (cur && cur.kind !== "draft") ov.close(true);
    var first = !cur;
    cur = cur || {kind: "draft", n: 0, total: Math.max(1, s.drafts)};
    cur.skill = skillId;
    cur.hover = -1;
    cur.lock = true;
    cur.picked = -1;
    cur.burnt = null;
    cur.faded = false;
    cur.mythicFx = null;
    cur.revealing = false;
    cur.claimed = false;
    cur.waitClaim = false;
    if (!again) cur.afterClaim = null;
    cur.H = 328;
    cur.hand = (s.hand || []).slice();
    cur.handKey = handKey(s);
    cur.gen = ++gen;
    cur.pos = {};
    if (!again) cur.n++;
    var el = host();
    el.className = "ov-draft";
    if (first) setUi();
    var h = '<div class="ov-dim"></div><div class="ov-halo" id="dr-halo"></div><div id="dr-chip">' + chip(s) + '</div><div id="dr-badge">' + badge(waiting() - 1) + "</div>";
    h += '<div id="dr-title"></div><div class="dr-hand" id="dr-hand"></div><div id="dr-keys"></div><div id="dr-act"></div>';
    h += '<div class="footer ov-fl" id="dr-fl"></div><div class="footer right ov-fr" id="dr-fr"></div>';
    el.innerHTML = h;
    var mythicAt = s.mythicOffer != null && s.mythicOffer >= 0 ? s.mythicOffer : -1;
    for (var i = 0; mythicAt < 0 && i < cur.hand.length; i++) if (MC.isMythic(cur.hand[i])) mythicAt = i;
    if (!cur.hand.length) {
      MC.$("dr-title").innerHTML = title(MC.upper(s.name) + "  ·  A DRAFT WAITS", "NOTHING TO DEAL YET", "#9A9DA1", 2);
      MC.$("dr-act").innerHTML = '<div class="dr-wait">This draft waits for a card it can deal. It keeps; press Esc.</div>';
      cur.lock = false;
      MC.fxSet("draft", []);
      return;
    }
    if (mythicAt >= 0) { dealMythic(s, mythicAt); return; }
    var hand = "", n = Math.min(3, cur.hand.length);
    MC.$("dr-title").innerHTML = cur.afterClaim
      ? title("MYTHIC CLAIMED  ·  IT DID NOT USE THIS DRAFT  ·  " + keepText(n), (n === 1 ? "NOW KEEP THIS ONE" : n === 2 ? "NOW KEEP ONE OF THESE TWO" : "NOW KEEP ONE OF THESE THREE"), "#F3D28B")
      : title("LEVEL UP  ·  DRAFT " + cur.n + " OF " + Math.max(cur.n, cur.total) + "  ·  " + keepText(n), MC.upper(s.name) + " REACHED LEVEL " + s.level);
    for (var k = 0; k < n; k++) {
      cur.pos[k] = leftOf(k, n);
      hand += MC.hcard(cur.hand[k], k, s, {cls: "deal", style: "left:" + cur.pos[k] + "px;top:" + TOP + "px;animation-delay:" + k * 80 + "ms"});
    }
    MC.$("dr-hand").innerHTML = hand;
    keys();
    redeal(s);
    fitHand(MC.$("dr-hand").children);
    footer(s);
    if (!again) MC.sound("deal", skillId);
    cur.dealing = true;
    draftFx();
    later(80 * 2 + 260, function () { if (cur && cur.kind === "draft") cur.lock = false; });
  };

  function keys() {
    var h = "", n = Math.min(3, cur.hand.length);
    for (var i = 0; i < n; i++) {
      if (cur.pos[i] == null) continue;
      var on = cur.hover === i, c = cur.hand[i];
      var x = cur.pos[i] + 124;
      h += '<div class="dr-key' + (on ? " on" : "") + '" style="left:' + (x - 40) + "px;" + (on ? "--c:" + tintOf(c) : "") + '"><span class="kc">' + (i + 1) +
        '</span><span class="kl">' + (on ? "Keep " + esc(c.baseName || c.name) : "Keep") + "</span></div>";
    }
    MC.patch(MC.$("dr-keys"), cur.hover + ":" + cur.handKey, h);
  }

  function redeal(s) {
    var d = MC.M.draft || {}, n = d.rerolls || 0, max = d.maxRerolls || 5;
    var pips = "";
    for (var i = 0; i < max; i++) pips += '<i class="' + (i < n ? "on" : "") + '"></i>';
    MC.$("dr-act").innerHTML = '<div class="dr-redeal' + (n ? "" : " off") + '" data-redeal="1"><svg width="14" height="14" viewBox="0 0 14 14"><path d="M11.6 5.2A5 5 0 1 0 12 8" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round"/><path d="M12.2 1.8v3.6H8.6" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '<span>Redeal all</span><span class="pips">' + pips + "</span></div>";
    var b = MC.$("dr-act").firstChild;
    if (b) b.style.left = Math.round(493.5 - b.offsetWidth / 2) + "px";  // centred on the hand, whatever its width
  }

  function footer(s) {
    var d = MC.M.draft || {};
    MC.text(MC.$("dr-fl"), MC.upper(s.name) + "  ·  LEVEL " + s.level + "  ·  " + (d.rerolls || 0) + ((d.rerolls || 0) === 1 ? " REDEAL LEFT" : " REDEALS LEFT"));
    MC.text(MC.$("dr-fr"), "CLICK A CARD OR PRESS 1–3  ·  ESC SAVES THE DRAFT FOR LATER");
  }

  /** The pointer moved over the hand. The page only relabels the keycap; the lift and hot frame are native. */
  ov.hover = function (target) {
    if (!cur || cur.kind !== "draft" || cur.picked >= 0 || cur.revealing || cur.faded) return;
    var i = -1;
    var hc = target && target.closest ? target.closest(".dr-hand .hc") : null;
    if (hc) i = +hc.getAttribute("data-hand");
    if (isNaN(i) || i === cur.hover) return;
    var was = cur.hover;
    cur.hover = i;
    cur.dealing = false;
    keys();
    draftFx();
    if (i >= 0) MC.sound("hover", cur.skill + "#" + i);
    else if (was >= 0) MC.sound("unhover", "");
  };

  /** Keeps card i: it flares, the other two burn away, and the next draft deals (or the page returns). */
  ov.pick = function (i) {
    if (!cur || cur.kind !== "draft" || cur.lock || cur.picked >= 0 || !cur.hand[i] || cur.pos[i] == null) return;
    var c = cur.hand[i];
    if (MC.isMythic(c)) { claim(i); return; }
    cur.picked = i;
    cur.lock = true;
    MC.act("pickCard", cur.skill + "#" + i);  // plays the flare, the thunk and the burn (SOUNDS.md)
    burnAway(i);
    var more = waiting() - 1;
    MC.$("dr-title").innerHTML = title("KEPT  ·  " + burnText(Math.min(3, cur.hand.length)), (c.baseName || c.name) + " JOINS YOUR HAND");
    MC.$("dr-badge").innerHTML = badge(more - 1 < 0 ? 0 : more);
    MC.$("dr-keys").innerHTML = "";
    MC.$("dr-act").innerHTML = '<div class="dr-toast" style="--c:' + tintOf(c) + '">Kept ' + esc(c.baseName || c.name) + "  ·  " +
      (more > 0 ? "next draft deals in a moment" : "back to your cards") + "</div>";
    MC.text(MC.$("dr-fl"), MC.upper(skill(cur.skill).name) + "  ·  DRAFT " + cur.n + " OF " + Math.max(cur.n, cur.total) + " DONE");
    MC.text(MC.$("dr-fr"), "THE NEXT DEAL TAKES INPUT AGAIN");
    later(1250, next);
  };

  /** The kept card flares (native), the others' faces burn away (one CSS mask strip) under native fire and ash. */
  function burnAway(i) {
    var cards = MC.$("dr-hand").children;
    cur.burnt = {};
    cur.hover = -1;
    cur.dealing = false;
    for (var k = 0; k < cards.length; k++) {
      var el = cards[k], idx = +el.getAttribute("data-hand");
      MC.toggle(el, "deal", false);
      if (idx === i) continue;
      cur.burnt[idx] = true;
      var face = el.querySelector(".hc-face");
      if (face) { face.style.animationDelay = burnDelay() / 1000 + "s"; face.classList.add("burnmask"); }
    }
    draftFx();
  }

  function next() {
    if (!cur || cur.kind !== "draft") return;
    var s = skill(cur.skill);
    if (s && s.drafts > 0 && s.hand && s.hand.length && handKey(s) !== cur.handKey) { ov.draft(s.id); return; }
    if (s && s.drafts > 0 && handKey(s) === cur.handKey) {
      // The server's answer has not landed yet: wait for it (onState), but not forever.
      cur.waitNext = true;
      later(2500, function () { if (cur && cur.waitNext) ov.close(true); });
      return;
    }
    var M = MC.M;
    for (var k = 0; M && k < M.skills.length; k++) {
      if (M.skills[k].released && M.skills[k].drafts > 0 && M.skills[k].hand && M.skills[k].hand.length) { ov.draft(M.skills[k].id); return; }
    }
    ov.close(true);
  }

  /** Redeal all: the hand sweeps off, Mettle plays the shuffle, the new hand fans in when it arrives. */
  ov.reroll = function () {
    if (!cur || cur.kind !== "draft" || cur.lock || cur.picked >= 0 || cur.revealing) return;
    var d = MC.M.draft || {};
    MC.act("reroll", cur.skill);  // Mettle plays the shuffle, or the dull "no"
    if (!d.rerolls) return;
    cur.lock = true;
    cur.rerolling = true;
    cur.hover = -1;
    MC.$("dr-hand").classList.add("sweep");
    MC.fxSet("draft", []);
    later(650, function () { if (cur && cur.rerolling) cur.rerollReady = true; tryRedeal(); });
    later(3000, function () { if (cur && cur.rerolling) { cur.rerolling = false; ov.draft(cur.skill, true); } });
  };

  function tryRedeal() {
    if (!cur || !cur.rerolling || !cur.rerollReady) return;
    var s = skill(cur.skill);
    if (s && handKey(s) !== cur.handKey) { cur.rerolling = false; ov.draft(cur.skill, true); MC.sound("deal", cur.skill); }
  }

  // ---- the Mythic --------------------------------------------------------------------------------------------
  function markers() {
    var m = MC.M && MC.M.draft && MC.M.draft.sound && MC.M.draft.sound.markers && MC.M.draft.sound.markers.mythic_reveal;
    return {dark: m ? m.dark || 0 : 0, cracks: m && m.cracks ? m.cracks : [900, 1300, 1650], swell: m ? m.swell || 1500 : 1500, flip: m ? m.flip || 2500 : 2500};
  }

  /**
   * The reveal's native layers at a stage (Figma M1 "aura, back to front", M2 storyboard): the back trembling, cracks,
   * a bloom and two counter-rotating ray copies swelling, motes; at the flip a burst and a white flash; settled, the
   * pulse ring, the prismatic halo, the card (a copy of the page's face, so the aura stays behind it), its white-gold
   * fire and the foil sweep.
   */
  function mythicFx(stage, x, y) {
    var w = MC.win(), X = w.x + x, Y = w.y + y, cx = X + 124, cy = Y + 164, m = markers(), items = [];
    var lit = stage !== "dark" && stage !== "crack", flipped = stage === "flip" || stage === "settled";
    if (lit) {
      items.push({i: "mbl", k: "bloom", x: cx, y: cy, w: 760, h: 700, c: "#FFF1C9", a: flipped ? 0.32 : 0.16, q: 1000});
      items.push({i: "mra", k: "rays", x: cx, y: cy, a: flipped ? 1 : 0.45, p: 1, q: 1000});
      items.push({i: "mrb", k: "rays", x: cx, y: cy, a: flipped ? 0.6 : 0.27, p: -1, q: 1000});
      var cols = ["white", "gold", "cyan", "pink"];
      for (var i = 0; i < 22; i++) {
        var a = i * 2.39996, r = 150 + (i * 37) % 70;
        items.push({i: "mm" + i, k: "mote-" + cols[i % 4], x: Math.round(cx + Math.cos(a) * r * 1.05 - 5), y: Math.round(cy + Math.sin(a) * r * 0.95 - 5),
          a: flipped ? 1 : 0.45, p: (i % 12) * 100 + (i % 30) * 1000});
      }
    }
    if (flipped) {
      items.push({i: "mri", k: "ring", x: cx, y: cy});
      items.push({i: "mpr", k: "prism", x: cx, y: cy, w: 300, h: 380});
      items.push({i: "mcp", k: "lift", x: X, y: Y, w: 248, h: 328, p: 0, q: 1, d: 150});
      items.push({i: "med", k: "my-edge", x: X, y: Y});
      items.push({i: "mfo", k: "foil", x: X, y: Y, clip: [X + 32, Y + 46, 184, 112]});
      if (stage === "flip") {
        items.push({i: "mbu", k: "burst", x: X, y: Y});
        items.push({i: "mfl", k: "bloom", x: cx, y: cy, w: 600, h: 600, c: "#FFFFFF", a: 1, p: 450});
      }
    } else {
      items.push({i: "mbk", k: "back", x: X, y: Y, p: m.flip - (stage === "dark" ? 0 : m.cracks[0])});
      if (stage !== "dark") items.push({i: "mck", k: "crack", x: X, y: Y});
    }
    return items;
  }

  function revealStage(stageEl, stage, x, y) {
    stageEl.className = "rv rv-" + stage;
    if (!cur) return;
    cur.mythicFx = mythicFx(stage, x, y);
    if (cur.kind === "draft") draftFx(); else MC.fxSet("draft", cur.mythicFx);
  }

  /** Runs the reveal at (x, y): the back trembles and cracks, flips into a burst, settles. Timed to the sound. */
  function runReveal(stageEl, x, y, then) {
    var m = markers();
    MC.sound("mythic_reveal", "");
    revealStage(stageEl, "dark", x, y);
    later(m.cracks[0], function () { revealStage(stageEl, "crack", x, y); });
    later(m.swell, function () { revealStage(stageEl, "swell", x, y); });
    later(m.flip, function () { revealStage(stageEl, "flip", x, y); });
    later(m.flip + 1000, function () { revealStage(stageEl, "settled", x, y); if (then) then(); });
  }

  /** Skips to the settled card (Esc or a click). */
  function skipReveal() {
    if (!cur || !cur.revealing) return;
    clearTimers();
    MC.sound("stop", "");
    var stageEl = MC.$("rv-stage");
    if (stageEl) revealStage(stageEl, "settled", cur.rvX, cur.rvY);
    cur.revealing = false;
    if (cur.afterReveal) cur.afterReveal();
  }

  /** The page's part of a reveal: the dark, and the Mythic's face (shown at the flip). The rest is native. */
  function mythicStage(c, x, y) {
    return '<div id="rv-stage" class="rv"><div class="rv-dim"></div><div class="rv-card" style="left:' + x + "px;top:" + y + 'px"><div class="rv-face">' +
      MC.mythicCard(c, null, c.holderLine || "1 of 1 in this world") + "</div></div></div>";
  }

  /** A Mythic offer in a hand carries only its perk key (mettle:mythic/<id>): the rest comes from the Hall's list. */
  MC.mythicInfo = function (c, s) {
    var id = String(c.perk || c.id || "").replace("mythic/", ""), all = MC.M.cards && MC.M.cards.mythics ? MC.M.cards.mythics.all : [];
    var out = {};
    for (var k in c) out[k] = c[k];
    for (var i = 0; i < all.length; i++) {
      if (all[i].id === id) {
        out.id = id; out.name = all[i].name; out.effect = all[i].effect; out.lore = all[i].lore; out.icon = all[i].icon; out.source = all[i].source; out.deed = all[i].deed;
      }
    }
    if (s && !out.how) out.how = "Dealt at " + s.name + " " + s.level;
    return out;
  };

  /** A hand dealt with a Mythic offer (M2 storyboard): the other cards fade back, the Mythic turns over in the middle. */
  function dealMythic(s, at) {
    var c = MC.mythicInfo(cur.hand[at], s), n = Math.min(3, cur.hand.length), slot = n === 3 ? 1 : at, free = n === 3 ? [0, 2] : [0, 1], f = 0;
    cur.mythicAt = at;
    cur.revealing = true;
    cur.faded = true;
    MC.$("dr-title").innerHTML = "";
    var others = "";
    for (var k = 0; k < n; k++) {
      if (k === at) continue;
      var pos = n === 3 ? free[f++] : k;
      cur.pos[k] = leftOf(pos, n);
      others += MC.hcard(cur.hand[k], k, s, {cls: "faded", style: "left:" + cur.pos[k] + "px;top:" + TOP + "px"});
    }
    MC.$("dr-hand").innerHTML = others;
    fitHand(MC.$("dr-hand").children, 328);  // the Mythic stays 328 tall; the faded others fit their content
    cur.rvX = leftOf(slot, n);
    cur.rvY = TOP;
    MC.$("dr-hand").insertAdjacentHTML("afterend", mythicStage(c, cur.rvX, cur.rvY));
    var claimOnly = s.mythicOffer != null;  // Mettle with claimMythic: claiming keeps the draft open
    MC.text(MC.$("dr-fl"), claimOnly ? "MYTHICS DON'T USE YOUR PICK  ·  " + (n - 1 >= 2 ? "YOU STILL KEEP ONE OF THE OTHERS" : n - 1 === 1 ? "YOU STILL KEEP THE OTHER CARD TOO" : "A NEW HAND DEALS AFTER") : "KEEPING A MYTHIC USES THIS DRAFT'S PICK  ·  " + burnText(n));
    MC.text(MC.$("dr-fr"), "");
    cur.afterReveal = function () {
      cur.lock = false;
      MC.$("dr-title").innerHTML = title("A MYTHIC WAS DEALT", c.name || "", "#F3D28B", 3);
      MC.$("dr-act").innerHTML = '<div class="claim" data-claim="' + at + '">Claim Mythic</div>';
      MC.text(MC.$("dr-fr"), "ONE OF ONE IN THIS WORLD");
    };
    runReveal(MC.$("rv-stage"), cur.rvX, cur.rvY, function () { cur.revealing = false; cur.afterReveal(); });
  }

  /**
   * Claims the Mythic offer. With Mettle's claimMythic the claim does not use the draft: the Mythic leaves the hand
   * and the hand stays open for a keep. An older Mettle keeps it as the draft's pick (the others burn).
   */
  function claim(i) {
    if (!cur || cur.lock) return;
    var c = MC.mythicInfo(cur.hand[i], skill(cur.skill)), s = skill(cur.skill);
    cur.lock = true;
    MC.lastClaimAt = Date.now();
    MC.sound("pick", "mythic");
    if (s && s.mythicOffer != null) {
      MC.act("claimMythic", cur.skill);
      cur.claimed = true;
      MC.$("dr-act").innerHTML = '<div class="dr-toast my">Claimed ' + esc(c.name) + "  ·  " + (cur.hand.length - 1 >= 2 ? "now keep one of the others" : cur.hand.length - 1 === 1 ? "the other card is yours to keep" : "a new hand deals") + "</div>";
      cur.mythicFx = (cur.mythicFx || []).concat([{i: "mfl2", k: "bloom", x: MC.win().x + cur.rvX + 124, y: MC.win().y + cur.rvY + 164, w: 520, h: 520, c: "#FFF1C9", a: 0.9, p: 600}]);
      draftFx();
      cur.waitClaim = true;
      cur.afterClaim = c.name || "the Mythic";
      later(3000, function () { if (cur && cur.waitClaim) { cur.waitClaim = false; ov.draft(cur.skill, true); } });
      return;
    }
    cur.picked = i;
    MC.act("keepCard", cur.skill + "#" + i + "#0");
    later(260, function () { MC.sound("burn", ""); });
    burnAway(-1);
    MC.$("dr-act").innerHTML = '<div class="dr-toast my">Claimed ' + esc(c.name) + "  ·  the world is told</div>";
    later(1600, next);
  }

  /** A Mythic just earned outside a draft (cards.reveal): the reveal on its own, then Continue. */
  ov.reveal = function (r) {
    if (cur) return false;
    cur = {kind: "reveal", id: r.id, revealing: true, rvX: 369, rvY: 175};
    setUi();
    var el = host();
    el.className = "ov-reveal";
    var all = MC.M.cards.mythics ? MC.M.cards.mythics.all : [];
    for (var i = 0; i < all.length; i++) if (all[i].id === r.id && r.source === "deed" && all[i].copies > 1) r.holderLine = "No. " + all[i].copies + " in this world";
    el.innerHTML = '<div class="ov-dim"></div><div id="dr-title"></div>' + mythicStage(r, cur.rvX, cur.rvY) + '<div id="dr-act"></div><div class="footer ov-fl" id="dr-fl"></div><div class="footer right ov-fr" id="dr-fr"></div>';
    cur.afterReveal = function () {
      MC.$("dr-title").innerHTML = title(r.source === "world_first" ? "A WORLD FIRST" : r.source === "drawn" ? "A MYTHIC WAS DEALT" : "A LEGEND WAS MADE", r.name || "", "#F3D28B", 3);
      MC.$("dr-act").innerHTML = '<div class="claim" data-close-ov="1">Continue</div>';
      MC.text(MC.$("dr-fl"), MC.upper(r.how || ""));
      MC.text(MC.$("dr-fr"), "IT FITS ANY DECK  ·  TWO SLOTTED AT MOST");
    };
    runReveal(MC.$("rv-stage"), cur.rvX, cur.rvY, function () { cur.revealing = false; cur.afterReveal(); });
    return true;
  };

  // ---- set complete -------------------------------------------------------------------------------------------
  /** A set crossed a tier (Figma 2g-6): rays and sparks burst once (native) around the glyph; any click continues. */
  ov.setDone = function (set, cards, justSlotted) {
    if (cur) return false;
    cur = {kind: "set", id: set.id};
    setUi();
    var el = host();
    el.className = "ov-set";
    var skills = {}, order = [];
    for (var i = 0; i < cards.length; i++) {
      var c = cards[i];
      if (!skills[c.skill]) { skills[c.skill] = {name: c.skillName, colour: c.colour, n: 0}; order.push(c.skill); }
      skills[c.skill].n++;
    }
    var bonus = null;
    for (var b = 0; b < set.bonuses.length; b++) if (set.bonuses[b].tier === set.tier) bonus = set.bonuses[b];
    var h = '<div class="ov-dim"></div>';
    h += '<div class="st-tile">' + MC.glyph(set, 64) + "</div>";
    h += '<div class="st-eb">SET COMPLETE</div><div class="st-title">' + MC.pix(set.name + " " + set.tier + (set.theme ? " · " + String(set.theme).split(/[.,;]/)[0] : ""), 3, "#F4EEE6", 900) + "</div>";
    h += '<div class="st-pips">';
    for (var p = 0; p < 3; p++) h += '<i class="' + (set.tier >= (p + 1) * 2 ? "on" : "") + '"></i>';
    h += "</div>";
    h += '<div class="st-desc">' + esc(bonus ? bonus.description : "") + "</div>";
    var shown = cards.slice(0, 6), w = shown.length * 72 + 12;
    h += '<div class="st-cards" style="width:' + Math.max(w, 120) + "px;left:" + Math.round(492.5 - Math.max(w, 120) / 2) + 'px">';
    for (var q = 0; q < shown.length; q++) {
      var cc = shown[q];
      h += '<div class="st-chip" style="left:' + (21 + q * 72) + "px;border-color:" + MC.rgba(cc.colour, 0.8) + ";background:" + MC.shade(cc.colour) + '">' + MC.img(cc.icon, 32) +
        '<i class="gem" style="background:' + (MC.RARITY[cc.rarity] || MC.RARITY.common) + '"></i>' + (cc.id === justSlotted ? '<span class="new" style="background:' + cc.colour + '">NEW</span>' : "") + "</div>";
    }
    h += "</div>";
    var parts = [];
    for (var o = 0; o < order.length; o++) parts.push(skills[order[o]].name + " " + skills[order[o]].n);
    h += '<div class="st-skills">' + esc(parts.join(" · ") + (set.tier >= 6 ? "  ·  six needs two skills ✓" : "")) + "</div>";
    var act = [];
    for (var a = 0; a < MC.M.cards.sets.length; a++) if (MC.M.cards.sets[a].active) act.push(MC.M.cards.sets[a].name + " " + MC.M.cards.sets[a].count);
    h += '<div class="st-active">Active sets: ' + esc(act.join(" · ")) + "</div>";
    el.innerHTML = h;
    // Native: the bloom, the rays and sparks (one per counted skill, the second mirrored), the glyph tile over them.
    var wo = MC.win(), fx = [{i: "sbl", k: "bloom", x: wo.x + 492, y: wo.y + 253, w: 600, h: 440, c: "#D8CDBB", a: 0.24}, {i: "sra", k: "set-rays", x: wo.x + 300, y: wo.y + 103}];
    for (var sk = 0; sk < order.length && sk < 2; sk++) fx.push({i: "ssp" + sk, k: "set-sparks", x: wo.x + 300, y: wo.y + 103, c: skills[order[sk]].colour, p: sk});
    fx.push({i: "stl", k: "lift", x: wo.x + 451, y: wo.y + 190, w: 82, h: 82, p: 0, q: 1});
    cur.mythicFx = fx;
    MC.fxSet("draft", fx);
    MC.sound("set_complete", "");
    later(4200, function () { if (cur && cur.kind === "set") ov.close(true); });
    return true;
  };

  // ---- input --------------------------------------------------------------------------------------------------
  /** A key from Java while an overlay is up (Esc, 1-9, Enter, r). */
  ov.key = function (k) {
    if (!cur) return false;
    if (k === "Escape") {
      if (cur.revealing) { skipReveal(); return true; }
      ov.close(false);
      return true;
    }
    if (cur.kind === "set") { ov.close(true); return true; }
    if (cur.kind === "draft") {
      if (k >= "1" && k <= "3") { ov.pick(+k - 1); return true; }
      if (k === "Enter" && cur.hover >= 0) { ov.pick(cur.hover); return true; }
      if (k === "r") { ov.reroll(); return true; }
    }
    return false;
  };

  /** A click inside the overlay; true when it was ours. */
  ov.click = function (t) {
    if (!cur) return false;
    if (cur.kind === "set") { ov.close(true); return true; }
    while (t && t !== document.body) {
      if (t.getAttribute) {
        var v;
        if ((v = t.getAttribute("data-claim")) != null) { claim(+v); return true; }
        if (t.getAttribute("data-close-ov")) { ov.close(true); return true; }
        if (t.getAttribute("data-redeal")) { ov.reroll(); return true; }
        if (t.classList && t.classList.contains("hc") && t.parentNode && t.parentNode.id === "dr-hand") { ov.pick(+t.getAttribute("data-hand")); return true; }
        if (t.id === "rv-stage" || t.classList && t.classList.contains("rv-card")) { if (cur.revealing) skipReveal(); return true; }
        if (t.id === "c-ov") return true;
      }
      t = t.parentNode;
    }
    return false;
  };

  /** A new state while an overlay is up: a waiting next hand, a finished reroll or claim, the badge. */
  ov.onState = function () {
    if (!cur || cur.kind !== "draft") return;
    var s = skill(cur.skill);
    if (cur.waitClaim && s && handKey(s) !== cur.handKey) { cur.waitClaim = false; ov.draft(cur.skill, true); return; }
    if (cur.waitNext && s && handKey(s) !== cur.handKey) { cur.waitNext = false; next(); return; }
    if (cur.rerolling) { tryRedeal(); return; }
    if (cur.picked < 0 && !cur.revealing && !cur.claimed) {
      MC.patch(MC.$("dr-badge"), String(waiting()), badge(waiting() - 1));
      if (s && s.hand && s.hand.length && handKey(s) !== cur.handKey && !cur.lock) { ov.draft(cur.skill, true); }
    }
  };
})();
