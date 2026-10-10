// VOID Mettle cards v2: the moments over the Classes page. Figma 295:2 §2f (direction D: D1 hand 525:20157, D3 hover
// 525:20667, D4 pick 525:20922; Mythic M1-M3 533:20975/20977/20979) and §2g (set complete 539:23542).
//
//   MC.ov.draft(skill)  deal the skill's hand: three cards fan in 80 ms apart; hover lifts a card onto its hot frame;
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

  /** Closes whatever is up. quiet: no stop (the burn-away is still sounding after a pick). */
  ov.close = function (quiet) {
    if (!cur) return;
    var was = cur;
    cur = null;
    clearTimers();
    var el = host();
    if (el) { el.innerHTML = ""; el.className = ""; }
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
    return '<div class="ov-badge"><i class="backs"></i><span>' + n + (n === 1 ? " more draft waiting" : " more drafts waiting") + "</span></div>";
  }
  function title(eb, text, ebColour, t) {
    return '<div class="ov-eb" style="color:' + (ebColour || "#FF7A4A") + '">' + esc(eb) + '</div><div class="ov-title">' + MC.pix(text, t || 3, "#F4EEE6", 900) + "</div>";
  }

  // ---- the draft -----------------------------------------------------------------------------------------------
  var TOP = 175;
  /** Card x for a hand of n (3 fills the window as in D1; fewer centre). */
  function leftOf(i, n) { return Math.round(493.5 - (n * 248 + (n - 1) * 30) / 2) + i * 278; }

  /** Deals the skill's hand. A hand with a Mythic in it turns that card over first. */
  ov.draft = function (skillId) {
    var s = skill(skillId);
    if (!s) return;
    if (cur && cur.kind !== "draft") ov.close(true);
    var first = !cur;
    cur = cur || {kind: "draft", n: 0, total: Math.max(1, s.drafts)};
    cur.skill = skillId;
    cur.hover = -1;
    cur.lock = true;
    cur.picked = -1;
    cur.hand = (s.hand || []).slice();
    cur.handKey = handKey(s);
    cur.n++;
    var el = host();
    el.className = "ov-draft";
    if (first) setUi();
    var h = '<div class="ov-dim"></div><div class="ov-halo"></div><div id="dr-chip">' + chip(s) + '</div><div id="dr-badge">' + badge(waiting() - 1) + "</div>";
    h += '<div id="dr-title"></div><div class="dr-hand" id="dr-hand"></div><div id="dr-keys"></div><div id="dr-act"></div>';
    h += '<div class="footer ov-fl" id="dr-fl"></div><div class="footer right ov-fr" id="dr-fr"></div>';
    el.innerHTML = h;
    var mythicAt = -1;
    for (var i = 0; i < cur.hand.length; i++) if (MC.isMythic(cur.hand[i])) mythicAt = i;
    if (!cur.hand.length) {
      MC.$("dr-title").innerHTML = title(MC.upper(s.name) + "  ·  A DRAFT WAITS", "NOTHING TO DEAL YET", "#9A9DA1", 2);
      MC.$("dr-act").innerHTML = '<div class="dr-wait">This draft waits for a card it can deal. It keeps; press Esc.</div>';
      cur.lock = false;
      return;
    }
    if (mythicAt >= 0) { dealMythic(s, mythicAt); return; }
    MC.$("dr-title").innerHTML = title("LEVEL UP  ·  DRAFT " + cur.n + " OF " + Math.max(cur.n, cur.total) + "  ·  KEEP ONE, THE OTHER TWO BURN",
      MC.upper(s.name) + " REACHED LEVEL " + s.level);
    var hand = "";
    for (var k = 0; k < cur.hand.length && k < 3; k++) {
      hand += MC.hcard(cur.hand[k], k, s, {cls: "deal", style: "left:" + leftOf(k, Math.min(3, cur.hand.length)) + "px;top:" + TOP + "px;animation-delay:" + k * 80 + "ms"});
    }
    MC.$("dr-hand").innerHTML = hand;
    keys();
    redeal(s);
    footer(s);
    MC.sound("deal", skillId);
    later(80 * 2 + 260, function () { if (cur && cur.kind === "draft") cur.lock = false; });
  };

  function keys() {
    var h = "";
    for (var i = 0; i < cur.hand.length && i < 3; i++) {
      var on = cur.hover === i, c = cur.hand[i];
      var x = leftOf(i, Math.min(3, cur.hand.length)) + 124;
      h += '<div class="dr-key' + (on ? " on" : "") + '" style="left:' + (x - 40) + "px;" + (on ? "--c:" + (c.cursed ? MC.CURSED : c.colour) : "") + '"><span class="kc">' + (i + 1) +
        '</span><span class="kl">' + (on ? "Keep " + esc(c.baseName || c.name) : "Keep") + "</span></div>";
    }
    MC.patch(MC.$("dr-keys"), cur.hover + ":" + cur.handKey, h);
  }

  function redeal(s) {
    var d = MC.M.draft || {}, n = d.rerolls || 0, max = d.maxRerolls || 5;
    var pips = "";
    for (var i = 0; i < max; i++) pips += '<i class="' + (i < n ? "on" : "") + '"></i>';
    MC.$("dr-act").innerHTML = '<div class="dr-redeal' + (n ? "" : " off") + '" data-redeal="1"><svg width="14" height="14" viewBox="0 0 14 14"><path d="M11.6 5.2A5 5 0 1 0 12 8" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round"/><path d="M12.2 1.8v3.6H8.6" stroke="#EDEEEF" stroke-width="1.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
      '<span>Redeal all</span><span class="pips" style="width:' + (max * 14 - 6) + 'px">' + pips + "</span></div>";
  }

  function footer(s) {
    var d = MC.M.draft || {};
    MC.text(MC.$("dr-fl"), MC.upper(s.name) + "  ·  LEVEL " + s.level + "  ·  " + (d.rerolls || 0) + ((d.rerolls || 0) === 1 ? " REDEAL LEFT" : " REDEALS LEFT"));
    MC.text(MC.$("dr-fr"), "CLICK A CARD OR PRESS 1–3  ·  ESC SAVES THE DRAFT FOR LATER");
  }

  /** The pointer moved over the hand: lift the card under it onto its hot frame, cool the others. */
  ov.hover = function (target) {
    if (!cur || cur.kind !== "draft" || cur.picked >= 0) return;
    var hc = target && target.closest ? target.closest(".dr-hand .hc") : null;
    var i = hc ? +hc.getAttribute("data-hand") : -1;
    if (isNaN(i) || i === cur.hover) return;
    var hand = MC.$("dr-hand");
    if (!hand) return;
    var cards = hand.children;
    for (var k = 0; k < cards.length; k++) MC.toggle(cards[k], "hov", k === i);
    MC.toggle(hand, "has-hov", i >= 0);
    var was = cur.hover;
    cur.hover = i;
    keys();
    if (i >= 0) MC.sound("hover", cur.skill + "#" + i);
    else if (was >= 0) MC.sound("unhover", "");
  };

  /** Keeps card i: it flares, the other two burn away, and the next draft deals (or the page returns). */
  ov.pick = function (i) {
    if (!cur || cur.kind !== "draft" || cur.lock || cur.picked >= 0 || !cur.hand[i]) return;
    var c = cur.hand[i];
    if (MC.isMythic(c)) { claim(i); return; }
    cur.picked = i;
    cur.lock = true;
    MC.act("pickCard", cur.skill + "#" + i);  // plays the flare, the thunk and the burn (SOUNDS.md)
    burnAway(i, c);
    var more = waiting() - 1;
    MC.$("dr-title").innerHTML = title("KEPT  ·  THE OTHER TWO BURN", (c.baseName || c.name) + " JOINS YOUR HAND");
    MC.$("dr-badge").innerHTML = badge(more - 1 < 0 ? 0 : more);
    MC.$("dr-keys").innerHTML = "";
    MC.$("dr-act").innerHTML = '<div class="dr-toast" style="--c:' + (c.cursed ? MC.CURSED : c.colour) + '">Kept ' + esc(c.baseName || c.name) + "  ·  " +
      (more > 0 ? "next draft deals in a moment" : "back to your cards") + "</div>";
    MC.text(MC.$("dr-fl"), MC.upper(skill(cur.skill).name) + "  ·  DRAFT " + cur.n + " OF " + Math.max(cur.n, cur.total) + " DONE");
    MC.text(MC.$("dr-fr"), "THE NEXT DEAL TAKES INPUT AGAIN");
    later(1250, next);
  };

  function burnAway(i, c) {
    var hand = MC.$("dr-hand");
    MC.toggle(hand, "has-hov", false);
    var cards = hand.children, delay = ((MC.M.draft && MC.M.draft.sound && MC.M.draft.sound.burnAfterPickMs) || 260) / 1000;
    for (var k = 0; k < cards.length; k++) {
      var el = cards[k];
      MC.toggle(el, "hov", false);
      MC.toggle(el, "deal", false);
      if (k === i) {
        el.classList.add("kept");
        el.insertAdjacentHTML("afterbegin", '<div class="kept-halo" style="background:radial-gradient(closest-side,' + MC.rgba(c.cursed ? MC.CURSED : c.colour, 0.55) + " 0%," + MC.rgba(c.cursed ? MC.CURSED : c.colour, 0) + ' 100%)"></div>');
        el.insertAdjacentHTML("beforeend", '<div class="ed flare once"><i class="f"></i><i class="g"></i></div>');
      } else {
        el.classList.add("burn");
        var face = el.querySelector(".hc-face");
        if (face) { face.classList.add("burnmask"); face.style.animationDelay = delay + "s"; }
        el.insertAdjacentHTML("beforeend", '<div class="ed bfront once" style="animation-delay:' + delay + 's"><i class="f" style="animation-delay:' + delay + 's"></i><i class="g" style="animation-delay:' + delay +
          's"></i></div><div class="ed burnash once" style="animation-delay:' + delay + 's"></div>');
      }
    }
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
    if (!cur || cur.kind !== "draft" || cur.lock || cur.picked >= 0) return;
    var d = MC.M.draft || {};
    MC.act("reroll", cur.skill);  // Mettle plays the shuffle, or the dull "no"
    if (!d.rerolls) return;
    cur.lock = true;
    cur.rerolling = true;
    var hand = MC.$("dr-hand");
    MC.toggle(hand, "has-hov", false);
    hand.classList.add("sweep");
    cur.hover = -1;
    later(650, function () { if (cur && cur.rerolling) cur.rerollReady = true; tryRedeal(); });
    later(3000, function () { if (cur && cur.rerolling) { cur.rerolling = false; ov.draft(cur.skill); } });
  };

  function tryRedeal() {
    if (!cur || !cur.rerolling || !cur.rerollReady) return;
    var s = skill(cur.skill);
    if (s && handKey(s) !== cur.handKey) { cur.rerolling = false; cur.n--; ov.draft(cur.skill); }
  }

  // ---- the Mythic --------------------------------------------------------------------------------------------
  function markers() {
    var m = MC.M && MC.M.draft && MC.M.draft.sound && MC.M.draft.sound.markers && MC.M.draft.sound.markers.mythic_reveal;
    return {dark: m ? m.dark || 0 : 0, cracks: m && m.cracks ? m.cracks : [900, 1300, 1650], swell: m ? m.swell || 1500 : 1500, flip: m ? m.flip || 2500 : 2500};
  }

  /** The aura around a Mythic (Figma M1 "aura, back to front"): bloom, rays x2, pulse ring, prismatic halo, motes. */
  function aura(x, y) {
    var h = '<div class="aura" style="left:' + x + "px;top:" + y + 'px"><div class="au-bloom"></div><div class="my-rays a"></div><div class="my-rays b"></div><div class="my-ring"></div><div class="au-prism"></div>';
    var cols = ["white", "gold", "cyan", "pink"];
    for (var i = 0; i < 22; i++) {
      var a = i * 2.39996, r = 150 + (i * 37) % 70;
      var mx = Math.round(124 + Math.cos(a) * r * 1.05 - 5), my = Math.round(164 + Math.sin(a) * r * 0.95 - 5);
      h += '<i class="mote ' + cols[i % 4] + '" style="left:' + mx + "px;top:" + my + "px;animation-delay:-" + (i % 12) / 10 + "s,-" + (i % 30) / 10 + 's"></i>';
    }
    return h + "</div>";
  }

  function revealStage(el, stage) { el.className = el.className.replace(/\brv-\w+/g, "").trim() + " " + stage; }

  /** Runs the reveal on the card element at (x, y): the back trembles and cracks, flips into a burst, settles. */
  function runReveal(stage, then) {
    var m = markers();
    MC.sound("mythic_reveal", "");
    revealStage(stage, "rv-dark");
    later(m.cracks[0], function () { revealStage(stage, "rv-crack"); });
    later(m.swell, function () { revealStage(stage, "rv-swell"); });
    later(m.flip, function () { revealStage(stage, "rv-flip"); });
    later(m.flip + 1000, function () { revealStage(stage, "rv-settled"); if (then) then(); });
    // Three seconds after it settles the aura calms to 5 fps (the moment is over; a Mythic left on screen should not
    // keep Ultralight painting the whole aura ten times a second).
    later(m.flip + 4000, function () { stage.classList.add("rv-calm"); });
  }

  /** Skips to the settled card (Esc or a click after the flip). */
  function skipReveal() {
    if (!cur || !cur.revealing) return;
    clearTimers();
    MC.sound("stop", "");
    var stage = MC.$("rv-stage");
    if (stage) revealStage(stage, "rv-settled");
    cur.revealing = false;
    if (cur.afterReveal) cur.afterReveal();
  }

  function mythicStage(c, x, y) {
    return '<div id="rv-stage" class="rv"><div class="rv-dim"></div><div class="rv-glow"></div>' + aura(x, y) +
      '<div class="rv-card" style="left:' + x + "px;top:" + y + 'px"><div class="rv-back"><div class="ed cardback"></div><div class="ed my-crack once"></div></div>' +
      '<div class="rv-face">' + MC.mythicCard(c, null, c.holderLine || "1 of 1 in this world") + '</div><div class="rv-burst my-burst once"></div><div class="rv-flash"></div></div></div>';
  }

  /** A hand dealt with a Mythic in it (M2 storyboard): the other two fade back, the Mythic turns over. */
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

  function dealMythic(s, at) {
    var c = MC.mythicInfo(cur.hand[at], s), n = Math.min(3, cur.hand.length), slot = n === 3 ? 1 : at, free = [0, 2], f = 0;
    cur.mythicAt = at;
    cur.revealing = true;
    MC.$("dr-title").innerHTML = "";
    var others = "";
    for (var k = 0; k < cur.hand.length && k < 3; k++) {
      if (k === at) continue;
      var pos = n === 3 ? free[f++] : k;
      others += MC.hcard(cur.hand[k], k, s, {cls: "faded", style: "left:" + leftOf(pos, n) + "px;top:" + TOP + "px"});
    }
    MC.$("dr-hand").innerHTML = others;
    MC.$("dr-hand").insertAdjacentHTML("afterend", mythicStage(c, leftOf(slot, n), TOP));
    MC.text(MC.$("dr-fl"), "KEEPING A MYTHIC USES THIS DRAFT'S PICK  ·  THE OTHER TWO BURN");
    MC.text(MC.$("dr-fr"), "");
    cur.afterReveal = function () {
      cur.lock = false;
      MC.$("dr-title").innerHTML = title("A MYTHIC WAS DEALT", c.name || "", "#F3D28B", 3);
      MC.$("dr-act").innerHTML = '<div class="claim" data-claim="' + at + '">Claim Mythic</div>';
      MC.text(MC.$("dr-fr"), "ONE OF ONE IN THIS WORLD");
    };
    runReveal(MC.$("rv-stage"), function () { cur.revealing = false; cur.afterReveal(); });
  }

  /** Claims the Mythic offer: keeping it claims it for the world (CARDS.md §9); the other two burn. */
  function claim(i) {
    if (!cur || cur.lock) return;
    var c = cur.hand[i];
    cur.picked = i;
    cur.lock = true;
    MC.act("keepCard", cur.skill + "#" + i + "#0");
    MC.lastClaimAt = Date.now();
    MC.sound("pick", "mythic");
    later(260, function () { MC.sound("burn", ""); });
    burnAway(-1, c);
    MC.$("dr-act").innerHTML = '<div class="dr-toast my">Claimed ' + esc(c.name) + "  ·  the world is told</div>";
    later(1600, next);
  }

  /** A Mythic just earned outside a draft (cards.reveal): the reveal on its own, then Continue. */
  ov.reveal = function (r) {
    if (cur) return false;
    cur = {kind: "reveal", id: r.id, revealing: true};
    setUi();
    var el = host();
    el.className = "ov-reveal";
    var x = 369, y = 175, all = MC.M.cards.mythics ? MC.M.cards.mythics.all : [];
    for (var i = 0; i < all.length; i++) if (all[i].id === r.id && r.source === "deed" && all[i].copies > 1) r.holderLine = "No. " + all[i].copies + " in this world";
    el.innerHTML = '<div class="ov-dim"></div><div id="dr-title"></div>' + mythicStage(r, x, y) + '<div id="dr-act"></div><div class="footer ov-fl" id="dr-fl"></div><div class="footer right ov-fr" id="dr-fr"></div>';
    cur.afterReveal = function () {
      MC.$("dr-title").innerHTML = title(r.source === "world_first" ? "A WORLD FIRST" : r.source === "drawn" ? "A MYTHIC WAS DEALT" : "A LEGEND WAS MADE", r.name || "", "#F3D28B", 3);
      MC.$("dr-act").innerHTML = '<div class="claim" data-close-ov="1">Continue</div>';
      MC.text(MC.$("dr-fl"), MC.upper(r.how || ""));
      MC.text(MC.$("dr-fr"), "IT FITS ANY DECK  ·  TWO SLOTTED AT MOST");
    };
    runReveal(MC.$("rv-stage"), function () { cur.revealing = false; cur.afterReveal(); });
    return true;
  };

  // ---- set complete -------------------------------------------------------------------------------------------
  /** A set crossed a tier (Figma 2g-6): the glyph scales in, rays and sparks burst once; any click continues. */
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
    var h = '<div class="ov-dim"></div><div class="st-bloom"></div><div class="set-rays once"></div>';
    for (var k = 0; k < order.length && k < 2; k++) h += '<div class="set-sparks once' + (k ? " flip" : "") + '" style="background-color:' + skills[order[k]].colour + '"></div>';
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
    MC.sound("set_complete", "");
    later(4200, function () { if (cur && cur.kind === "set") ov.close(true); });
    return true;
  };

  // ---- input --------------------------------------------------------------------------------------------------
  /** A key from Java while an overlay is up (Esc, 1-9, Enter, r). */
  ov.key = function (k) {
    if (!cur) return false;
    if (k === "Escape") {
      if (cur.kind === "draft" && cur.revealing) { skipReveal(); return true; }
      if (cur.kind === "reveal" && cur.revealing) { skipReveal(); return true; }
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

  /** A new state while an overlay is up: a waiting next hand, a finished reroll, the badge. */
  ov.onState = function () {
    if (!cur || cur.kind !== "draft") return;
    var s = skill(cur.skill);
    if (cur.waitNext && s && handKey(s) !== cur.handKey) { cur.waitNext = false; next(); return; }
    if (cur.rerolling) { tryRedeal(); return; }
    if (cur.picked < 0 && !cur.revealing) {
      MC.patch(MC.$("dr-badge"), String(waiting()), badge(waiting() - 1));
      if (s && s.hand && s.hand.length && handKey(s) !== cur.handKey && !cur.lock) { cur.n--; ov.draft(cur.skill); }
    }
  };
})();
