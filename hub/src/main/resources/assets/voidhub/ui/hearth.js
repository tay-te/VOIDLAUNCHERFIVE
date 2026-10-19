// VOID Hearth in the hub (hearth/docs/OVERLAY.md; Figma 3c0Wgc4ZKFSpm98fWJoARS, section 651:37945). Loaded before
// hub.js, which calls these hooks:
//
//   hubHearth.layout(l)   every layout message (kind "hearth": an event screen; "quests": the hub's Quests page)
//   hubHearth.state(s)    every hub state message (the Quests page lives in the hub)
//   hubHearth.apply(m)    {type: "hearth", state}, {type: "hearthScreen", screen, seg}, {type: "hkey"|"hwheel"|"hmods"},
//                         {type: "visible"}
//
// Page -> Java: {type: "hearth", op, arg} (Bridge.HEARTH_OPS), {type: "hearthUi", modal, text} and
// {type: "hearthFx", items, stacks} (the rects the minigames' native parts are drawn in, and item wells).
// Everything is drawn from the state Java sends; a part is rewritten only when what it shows changed, nothing animates.
// OVERLAY.md 2026-10-19 (hearth-ui2): portraits for every resident-backed screen (market.merchant, dice.opponent,
// pickpocket), haggle.takes / takesLast ("he takes it" odds), bank.ledger (LATELY), board.wanted[].player / tracked and
// state.wantedTrack (a tracked poster: trackPoster / untrackPoster), and training's fee in the option's cost.
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var root = $("hearth");
  var H = null;          // the last Hearth state
  var screen = "";       // talk | confront | market | bank | dice | lockpick | pickpocket | quests
  var seg = "journal";   // the Quests page's segment
  var kind = "";         // the layout's kind
  var lay = null;
  var ui = {focus: -1, pop: "", offer: null, offerRow: -1, amount: 0, plot: -1, town: -1, quest: null, scroll: 0, shift: false, pslot: -1,
    bankTab: "", marketTab: "sells", asked: false};
  var drawn = "";        // the frame drawn (screen + style), to rebuild the parts when it changes
  var lastKey = "";      // tests: the last key Java passed on
  var icons = {};        // Hearth's icons by id ({"type":"icons","mod":"hearth"}), apart from Mettle's

  function send(msg) {
    try {
      if (typeof window.__void_native === "function") return window.__void_native(JSON.stringify(msg));
    } catch (e) { /* never throw into the host */ }
    return null;
  }
  function act(op, arg) { send({type: "hearth", op: op, arg: arg == null ? "" : String(arg)}); }
  function esc(s) { return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;"); }
  function toggle(el, cls, on) { if (el && el.classList.contains(cls) !== !!on) el.classList.toggle(cls, !!on); }
  function upper(s) { return String(s || "").toUpperCase(); }
  function sgn(n) { return n > 0 ? "+" + n : n < 0 ? "−" + Math.abs(n) : "0"; }
  function num(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ","); }
  function first(name) { return String(name || "").split(" ")[0]; }

  /** A child part (made once by id) whose content is rewritten only when its key changes. */
  function part(host, id, key, html) {
    var el = document.getElementById(id);
    if (!el) {
      el = document.createElement("div");
      el.id = id;
      el.className = "h-part";
      host.appendChild(el);
    }
    if (el.__k === key) return el;
    el.__k = key;
    el.innerHTML = typeof html === "function" ? html() : html;
    return el;
  }

  // ---- pixel art: kind glyphs (Figma kit 651:37946), coins, portraits -------------------------------------------------
  var GLYPHS = {
    talk: [".#####.", "#######", "#.#.#.#", "#######", ".#####.", "..#....", ".#....."],
    trade: ["...#...", ".#####.", "#..#..#", "##.#.##", "...#...", "...#...", ".#####."],
    persuade: [".##.##.", "#######", "#######", ".#####.", "..###..", "...#...", "......."],
    train: ["#######", ".#####.", "...#...", "..###..", ".#####.", "#######", "......."],
    leave: [".#####.", ".#...#.", ".#...#.", ".#..##.", ".#...#.", ".#...#.", "#######"],
    lock: ["..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"],
    eye: [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],
    dice: ["#######", "#.###.#", "#######", "###.###", "#######", "#.###.#", "#######"],
    hand: ["..#.#..", ".##.##.", ".######", "#######", "#######", ".#####.", "..###.."],
    pick: ["....##.", "...#..#", "...#..#", "..#.##.", ".#.....", "##.....", "#......"],
    scroll: [".#####.", "#.....#", ".#####.", ".#...#.", ".#####.", "#.....#", ".#####."],
    run: ["...##..", "..##...", ".####..", "#.##.#.", "..##...", ".#..#..", "#....#."],
    fight: ["#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"],
    cell: ["#######", "#.#.#.#", "#.#.#.#", "#.#.#.#", "#.#.#.#", "#.#.#.#", "#######"],
    quest: ["...#...", "...#...", "...#...", "...#...", "...#...", ".......", "...#..."],
    coin: [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", ".#####."],
    house: ["...#...", "..###..", ".#####.", "#######", ".#.#.#.", ".#####.", ".##.##."]
  };
  var KIND_GLYPH = {talk: "talk", trade: "trade", bank: "coin", quest: "quest", persuade: "persuade", intimidate: "hand", bribe: "coin",
    flatter: "persuade", train: "train", service: "trade", law: "scroll", fence: "hand", dice: "dice", repair: "pick", leave: "leave",
    pay: "coin", cell: "cell", submit: "cell", pillory: "scroll", threaten: "hand", run: "run", fight: "fight", allow: "talk",
    refuse: "leave"};
  var TYPE_GLYPH = {supply: "trade", haul: "trade", bounty: "fight", hunt: "fight", courier: "scroll", explore: "eye", scout: "eye",
    escort: "run", chore: "hand", build: "house", deliver: "scroll"};

  /** A 7x7 glyph as one SVG (crisp at any scale): cells 1.8 at a 2.05 pitch in a 16 box, scaled to size. */
  function glyph(name, size, colour, opacity) {
    var rows = GLYPHS[name] || GLYPHS.talk;
    var s = size || 16;
    var h = '<svg class="h-g" width="' + s + '" height="' + s + '" viewBox="0 0 16 16"' + (opacity != null ? ' opacity="' + opacity + '"' : "") + ">";
    for (var y = 0; y < 7; y++) {
      for (var x = 0; x < 7; x++) {
        if (rows[y].charAt(x) === "#") {
          h += '<rect x="' + (1 + x * 2.05).toFixed(2) + '" y="' + (1 + y * 2.05).toFixed(2) + '" width="1.8" height="1.8" rx="0.3" fill="' + (colour || "#EDEEEF") + '"/>';
        }
      }
    }
    return h + "</svg>";
  }

  var COIN = [".####.", "#++++#", "#+##+#", "#+##+#", "#++++#", ".####."];
  var COIN_RGB = {crown: "#E3C47E", mark: "#B9BDC2", bit: "#D9A07E"};
  /** The Hub's coin (Figma hearth/coin readout 271:52429): 6x6 cells of 3 at a 3.5 pitch in a 28 box. */
  function coin(type, size) {
    var s = size || 28;
    var h = '<svg width="' + s + '" height="' + s + '" viewBox="0 0 28 28">';
    for (var y = 0; y < 6; y++) {
      for (var x = 0; x < 6; x++) {
        var ch = COIN[y].charAt(x);
        if (ch === ".") continue;
        h += '<rect x="' + (3.75 + x * 3.5) + '" y="' + (3.75 + y * 3.5) + '" width="3" height="3" rx="0.9" fill="' + COIN_RGB[type] + '" fill-opacity="' + (ch === "#" ? 0.62 : 0.9) + '"/>';
      }
    }
    return h + "</svg>";
  }

  function readout(type, n) {
    var name = type === "crown" ? (n === 1 ? "CROWN" : "CROWNS") : type === "mark" ? (n === 1 ? "MARK" : "MARKS") : (n === 1 ? "BIT" : "BITS");
    return '<div class="h-cr flow">' + coin(type) + '<div class="crt"><div class="crc">' + n + '</div><div class="crn">' + name + "</div></div></div>";
  }

  /** Readouts for the denominations a sum has (at least one). */
  function readouts(c, style) {
    if (!c) return "";
    var h = "";
    if (c.crowns > 0) h += readout("crown", c.crowns);
    if (c.marks > 0) h += readout("mark", c.marks);
    if (c.bits > 0 || (!c.crowns && !c.marks)) h += readout("bit", c.bits);
    return '<div class="h-crs flow" style="' + (style || "") + '">' + h + "</div>";
  }

  /** All three readouts (a purse, a deposit). */
  function readouts3(c, style) {
    c = c || {crowns: 0, marks: 0, bits: 0};
    return '<div class="h-crs flow" style="gap:20px;' + (style || "") + '">' + readout("crown", c.crowns) + readout("mark", c.marks) + readout("bit", c.bits) + "</div>";
  }

  /** An inline price: a small square of each coin's colour before its count. */
  function price(c, cls, style) {
    if (!c) return "";
    var h = "";
    if (c.crowns > 0) h += '<span><i class="c"></i>' + c.crowns + "</span>";
    if (c.marks > 0) h += '<span><i class="m"></i>' + c.marks + "</span>";
    if (c.bits > 0 || !h) h += '<span><i class="b"></i>' + c.bits + "</span>";
    return '<div class="h-price ' + (cls || "") + '" style="' + (style || "") + '">' + h + "</div>";
  }

  function coinText(c) {
    if (!c) return "";
    var p = [];
    if (c.crowns) p.push(c.crowns + (c.crowns === 1 ? " crown" : " crowns"));
    if (c.marks) p.push(c.marks + (c.marks === 1 ? " mark" : " marks"));
    if (c.bits || !p.length) p.push(c.bits + (c.bits === 1 ? " bit" : " bits"));
    return p.join(" ");
  }

  function coinsOf(bits) {
    bits = Math.max(0, Math.round(bits));
    return {total: bits, crowns: Math.floor(bits / 100), marks: Math.floor(bits % 100 / 10), bits: bits % 10};
  }

  function face(p, size, style) {
    if (!p || !p.portrait) return "";
    return '<div class="h-face" style="width:' + size + "px;height:" + size + "px;" + (style || "") + '"><img src="' + p.portrait + '"></div>';
  }

  // ---- cells -----------------------------------------------------------------------------------------------------------
  /**
   * A −100…100 row (Figma hearth/standing row 271:52402): ten cells each side of the centre, lit outwards from it, the
   * outermost lit one in the meaning's colour.
   */
  function standingCells(v, left, top, pitch, gap, tone) {
    pitch = pitch || 14;
    gap = gap == null ? 5 : gap;
    var n = Math.min(10, Math.ceil(Math.abs(v) / 10));
    var h = '<div class="h-cel" style="left:' + left + "px;top:" + top + "px;width:" + (19 * pitch + gap + 9) + 'px">';
    for (var i = 0; i < 20; i++) {
      var pos = i >= 10;
      var x = pos ? 10 * pitch + gap + (i - 10) * pitch : i * pitch;
      var d = pos ? i - 9 : 10 - i;    // distance from the centre, 1…10
      var lit = v !== 0 && (v > 0) === pos && d <= n;
      var cls = !lit ? "" : d === n ? (tone || (v > 0 ? "violet" : v <= -40 ? "bad" : "wary")) : "on";
      h += '<i class="' + cls + '" style="left:' + x + 'px;width:9px;height:9px;border-radius:2.7px"></i>';
    }
    return h + "</div>";
  }

  /** n cells of size s at a pitch, the first `lit` on and the last of those in `last`'s colour. */
  function rowCells(n, lit, s, pitch, left, top, last, r) {
    var h = '<div class="h-cel" style="left:' + left + "px;top:" + top + "px;height:" + s + "px;width:" + ((n - 1) * pitch + s) + 'px">';
    for (var i = 0; i < n; i++) {
      var cls = i < lit ? (i === lit - 1 && last ? last : "on") : "";
      h += '<i class="' + cls + '" style="left:' + (Math.round(i * pitch * 10) / 10) + "px;width:" + s + "px;height:" + s + "px;border-radius:" + (r || 2) + 'px"></i>';
    }
    return h + "</div>";
  }

  function opinionTone(v) { return v > 0 ? "good" : v < 0 ? "bad" : "ink2"; }
  function oddsTone(o) { return o >= 70 ? "good" : o >= 40 ? "wary" : "bad"; }
  function titleTone(t) { return t === "burgher" || t === "hero" ? "brass" : t === "outlaw" ? "bad" : t === "distrusted" ? "wary" : "ink"; }

  // ---- the window frame ----------------------------------------------------------------------------------------------------
  function chip() {
    return '<div class="h-chip flow"><span class="modmark big"><i></i><i></i><i></i><i></i><i></i><i></i></span>from Hearth</div>';
  }

  function foot(left, right) {
    return '<div class="h-foot l">' + esc(left) + '</div><div class="h-foot r">PANEL  HEARTH:' + esc(upper(right)) + "  ·  LIVE</div>";
  }

  function win() {
    var w = document.getElementById("h-win");
    if (!w) {
      w = document.createElement("div");
      w.id = "h-win";
      w.className = "hw";
      root.appendChild(w);
    }
    return w;
  }

  function clear() {
    root.innerHTML = "";
    toggle(root, "cine", false);
  }

  // ---- talk: A, the portrait card (653:37976) and B, the cinematic bar (655:38485) ---------------------------------------
  function optionChip(o) {
    if (o.locked) return '<div class="h-mchip flow">' + esc(o.lockedReason || o.tooltip || "Locked") + "</div>";
    var dot = "", text = "";
    var kindName = o.kind ? o.kind.charAt(0).toUpperCase() + o.kind.slice(1) : "";
    if (o.kind === "train" && o.skill) {
      text = "Train " + o.skill.name + " " + o.skill.level + " → " + (o.skill.level + 1) + (o.cost && o.cost.total > 0 ? "  ·  " + coinText(o.cost) : "") + "  ·  Mettle";
      dot = "violet";
    } else if (o.odds >= 0) {
      text = (o.kind === "bribe" && o.cost ? "Bribe " + coinText(o.cost) : kindName + (o.skill ? "  ·  " + o.skill.name : "")) + "  ·  " + o.odds + "%";
      dot = oddsTone(o.odds);
    } else if (o.cost && o.cost.total > 0) {
      text = (o.hint || kindName) + "  ·  " + coinText(o.cost);
      dot = "wary";
    } else if (o.hint) {
      text = o.hint;
      dot = o.kind === "quest" ? "wary" : "";
    } else {
      return "";
    }
    return '<div class="h-mchip flow">' + (dot ? '<div class="h-dot ' + dot + '"></div>' : "") + "<span>" + esc(text) + "</span></div>";
  }

  function optionRow(o, i, width) {
    var g = o.locked ? "lock" : KIND_GLYPH[o.kind] || "talk";
    return '<div class="h-opt flow' + (o.locked ? " locked" : "") + (i === ui.focus ? " focus" : "") + '" data-opt="' + i + '" data-kind="' + esc(o.kind) + '" style="width:' + width + 'px">' +
      '<div class="k">' + (o.key >= 0 ? o.key : "") + "</div>" + glyph(g, 16) + '<div class="l">' + esc(optionLabel(o)) + "</div>" + optionChip(o) + "</div>";
  }

  /** At most ten rows: the first nine, and goodbye always last (key 0). */
  function talkOptions(t) {
    var out = [], leave = null;
    for (var i = 0; i < t.options.length; i++) {
      var o = t.options[i];
      if (o.kind === "leave" || o.key === 0) { if (!leave) leave = o; continue; }
      if (out.length < 9) out.push(o);
    }
    if (leave) out.push(leave);
    return out;
  }

  /** The option's words without the provider's own "[Persuade 62%]" prefix when the chip already says it. */
  function optionLabel(o) {
    var l = String(o.label || "");
    return o.odds >= 0 || o.kind === "train" || (o.cost && o.cost.total > 0) ? l.replace(/^\[[^\]]*\]\s*/, "") : l;
  }

  function placeLine(t) {
    var p = [];
    if (t.resident.place) p.push(t.resident.place);
    if (t.resident.town) p.push(t.resident.town);
    if (t.time && t.time.part) p.push(t.time.part);
    return p;
  }

  function talkA(t) {
    var w = win();
    var r = t.resident;
    part(w, "ht-head", "1|" + placeLine(t).join("|"), function () {
      return chip() +
        '<div class="h-eb" style="left:368px;top:30px;font-size:9.5px;letter-spacing:1.3px;color:#9A9DA1">' + esc(upper(placeLine(t).join("  ·  "))) + "</div>" +
        '<div class="h-pill" data-op="setConversationStyle" data-arg="cinematic" style="left:790px;top:24px">Cinematic view</div>' +
        '<div class="h-pill" data-ui="close" style="left:893px;top:24px">Esc  leave</div>';
    });
    part(w, "ht-card", JSON.stringify([r, t.standing]), function () {
      var traits = "";
      for (var i = 0; i < r.traits.length && i < 4; i++) traits += '<div class="h-pill">' + esc(r.traits[i].name) + "</div>";
      var job = r.job + (r.town ? " of " + r.town : "") + (r.place ? "  ·  " + r.place : "");
      var opWord = r.opinion > 0 ? "Likes you" : r.opinion < 0 ? "Dislikes you" : "Neutral";
      var st = t.standing || {};
      var next = st.next ? "  ·  " + upper(st.next.titleName) + " AT " + sgn(st.next.at) : "";
      return '<div class="h-panel h-res">' +
        '<div class="h-well pw">' + (r.portrait ? face(r, 168, "left:57.5px;top:15px") : '<div class="noface">' + glyph("talk", 48, "#626569") + "</div>") + "</div>" +
        '<div class="nm">' + esc(r.name) + "</div>" +
        '<div class="job">' + esc(job) + "</div>" +
        '<div class="h-traits flow" style="left:15px;top:267px">' + traits + "</div>" +
        '<div class="h-hair" style="left:15px;top:305px;width:285px"></div>' +
        '<div class="h-eb" style="left:15px;top:319px">OPINION OF YOU</div>' +
        '<div class="' + opinionTone(r.opinion) + '" style="right:15px;top:317px;font-size:11.5px;white-space:pre">' + opWord + "  " + sgn(r.opinion) + "</div>" +
        standingCells(r.opinion, 15, 339, 14, 5, r.opinion > 0 ? "good" : r.opinion < 0 ? "bad" : null) +
        '<div style="left:15px;top:357px;width:285px;font-size:11px;color:#9A9DA1;line-height:14px">' + (r.memory ? "Remembers: " + esc(r.memory.text) : esc(r.moodWord ? "Mood: " + r.moodWord : "")) + "</div>" +
        '<div class="h-hair" style="left:15px;top:395px;width:285px"></div>' +
        '<div style="left:15px;top:405px;font-size:13px;color:#EDEEEF;white-space:nowrap">Your standing · ' + esc(st.town || r.town) + "</div>" +
        '<div style="right:15px;top:406px;font-size:12px;color:#9A9DA1">' + sgn(st.value || 0) + "</div>" +
        standingCells(st.value || 0, 15, 430, 14, 5) +
        '<div class="h-eb2" style="left:15px;top:447px">' + esc(upper((st.titleName || "") + (st.town ? " in " + st.town : "")) + next) + "</div>" +
        "</div>";
    });
    part(w, "ht-speech", JSON.stringify([r.name, t.line, t.lastCheck]), function () {
      return '<div class="h-speech"><div class="h-eb">' + esc(upper(first(r.name))) + " SAYS</div>" +
        '<div class="line">“' + esc(t.line) + "”</div>" + (t.lastCheck ? '<div class="check">' + esc(t.lastCheck) + "</div>" : "") + "</div>" +
        '<div class="h-hair" style="left:368px;top:179px;width:590px"></div>';
    });
    var opts = talkOptions(t);
    part(w, "ht-opts", JSON.stringify([opts, ui.focus]), function () {
      var h = '<div class="h-opts flow">';
      for (var i = 0; i < opts.length; i++) h += optionRow(opts[i], i, 598);
      return h + "</div>";
    });
    part(w, "ht-foot", "f", function () {
      return foot("1–9 CHOOSE  ·  0 OR ESC LEAVES  ·  ODDS ARE HEARTH’S, SKILL LEVELS ARE METTLE’S", "talk");
    });
  }

  function talkB(t) {
    toggle(root, "cine", true);
    var r = t.resident;
    var vw = lay ? lay.vw : 1600, vh = lay ? lay.vh : 980;
    var bw = Math.min(1360, vw - 48), bx = Math.round((vw - bw) / 2), by = vh - 36 - 268;
    part(root, "hb-pill", JSON.stringify(placeLine(t)), function () {
      return '<div class="h-cpill">' + esc(placeLine(t).join("  ·  ")) + "</div>" +
        '<div class="h-cpill" data-op="setConversationStyle" data-arg="card" style="left:auto;right:132px;top:27px">Card view</div>';
    });
    part(root, "hb-sub", JSON.stringify([r.name, t.line, vw, by]), function () {
      return '<div class="h-sub flow" style="left:' + Math.round((vw - 868) / 2) + "px;bottom:" + (vh - by + 24) + 'px"><div class="who">' + esc(r.name) +
        '</div><div class="line">“' + esc(t.line) + "”</div></div>";
    });
    var opts = talkOptions(t);
    part(root, "hb-bar", JSON.stringify([r, t.standing, opts, ui.focus, bw, bx, by]), function () {
      var cw = Math.floor((bw - 335 - 19 - 6) / 2);
      var st = t.standing || {};
      var traits = [];
      for (var i = 0; i < r.traits.length && i < 2; i++) traits.push(r.traits[i].name);
      var c1 = "", c2 = "";
      for (var k = 0; k < opts.length; k++) {
        if (k < 5) c1 += optionRow(opts[k], k, cw); else c2 += optionRow(opts[k], k, cw);
      }
      return '<div class="h-bar" style="left:' + bx + "px;top:" + by + "px;width:" + bw + 'px">' +
        '<div class="h-well pw">' + face(r, 112, "left:-1px;top:-1px") + "</div>" +
        '<div style="left:151px;top:27px;font-size:18px;color:#EDEEEF;white-space:nowrap">' + esc(r.name) + "</div>" +
        '<div style="left:151px;top:51px;font-size:11.5px;color:#9A9DA1;white-space:nowrap">' + esc(r.job + (r.town ? " of " + r.town : "")) + "</div>" +
        '<div style="left:151px;top:67px;font-size:11.5px;color:#626569;white-space:nowrap">' + esc(r.place) + "</div>" +
        (traits.length ? '<div class="h-pill" style="left:151px;top:99px">' + esc(traits.join(" · ")) + "</div>" : "") +
        '<div class="h-eb" style="left:23px;top:151px">' + "OPINION OF YOU</div>" +
        '<div class="' + opinionTone(r.opinion) + '" style="right:' + (bw - 302) + 'px;top:149px;font-size:11px;white-space:pre">' + (r.opinion > 0 ? "Likes you " : r.opinion < 0 ? "Dislikes you " : "Neutral ") + sgn(r.opinion) + "</div>" +
        standingCells(r.opinion, 23, 169, 14, 4, r.opinion > 0 ? "good" : r.opinion < 0 ? "bad" : null) +
        '<div class="h-eb" style="left:23px;top:191px;width:200px;overflow:hidden;text-overflow:ellipsis">YOUR STANDING · ' + esc(upper(st.town || r.town)) + "</div>" +
        '<div style="right:' + (bw - 302) + 'px;top:189px;font-size:11px;color:#EDEEEF;white-space:pre">' + esc(st.titleName || "") + " " + sgn(st.value || 0) + "</div>" +
        standingCells(st.value || 0, 23, 209, 14, 4) +
        '<div style="left:23px;top:227px;width:279px;font-size:10.5px;color:#626569;white-space:nowrap;overflow:hidden">' + (r.memory ? "Remembers: " + esc(r.memory.text) : "") + "</div>" +
        '<div style="left:323px;top:23px;width:var(--hair,1px);height:220px;background:rgba(255,255,255,0.07)"></div>' +
        '<div class="cols"><div class="col">' + c1 + '</div><div class="col">' + c2 + "</div></div>" +
        '<div class="h-eb" style="left:347px;top:239px">1–9 CHOOSE  ·  0 OR ESC LEAVES  ·  MOUSE LOOK STAYS LOCKED</div>' +
        '<div class="h-eb" style="right:23px;top:239px">HEARTH:TALK  ·  LIVE</div>' +
        "</div>";
    });
  }

  // ---- a guard stops you (663:42751) -------------------------------------------------------------------------------------------
  function confront(c) {
    var w = win();
    var f = c.folk || {};
    var who = c.kind === "witness" ? "STOPPED BY A WITNESS" : c.kind === "search" ? "SEARCHED BY THE GUARD" : "STOPPED BY THE GUARD";
    part(w, "hc-head", JSON.stringify([who, c.charge && c.charge.town, c.bounty]), function () {
      return '<div class="h-glow lawwin" style="left:0;top:0;width:985px;height:604px"></div>' + chip() +
        '<div class="h-eb bad" style="left:361px;top:30px;font-size:9.5px;letter-spacing:1.3px">' + who + (c.charge && c.charge.town ? "  ·  " + esc(upper(c.charge.town)) : "") + "</div>" +
        (c.bounty && c.bounty.total > 0 ? '<div class="h-pill ink law" style="right:28px;top:24px"><div class="h-dot bad"></div><span>Bounty ' + esc(coinText(c.bounty)) + "</span></div>" : "");
    });
    part(w, "hc-card", JSON.stringify([f, c.charge, c.standing, c.name, H && H.player && H.player.purse]), function () {
      var traits = [];
      for (var i = 0; i < (f.traits || []).length && i < 3; i++) traits.push(String(f.traits[i].name).toLowerCase());
      var ch = c.charge || {}, st = c.standing || {};
      var purse = H && H.player ? H.player.purse : null;
      return '<div class="h-panel h-res">' +
        '<div class="h-well pw">' + (c.portrait || f.portrait ? face({portrait: c.portrait || f.portrait}, 168, "left:57.5px;top:15px") : '<div class="noface">' + glyph("fight", 48, "#626569") + "</div>") + "</div>" +
        '<div class="nm">' + esc(c.name || f.name) + "</div>" +
        '<div class="job">' + esc((f.job || "") + (traits.length ? "  ·  " + traits.join(", ") : "")) + "</div>" +
        '<div class="h-hair" style="left:15px;top:267px;width:285px"></div>' +
        '<div class="h-eb" style="left:15px;top:281px">THE CHARGE</div>' +
        '<div style="left:15px;top:297px;font-size:13px;color:#EDEEEF;white-space:nowrap">' + esc(ch.offence || "—") + (ch.offences > 1 ? "  ·  " + ch.offences + " offences" : "") + "</div>" +
        '<div style="left:15px;top:317px;width:285px;font-size:11px;color:#9A9DA1">' + esc(ch.witnesses ? "Witnessed by " + ch.witnesses : "") + "</div>" +
        '<div class="h-eb" style="left:15px;top:349px">OPINION OF YOU</div>' +
        '<div class="' + opinionTone(f.opinion || 0) + '" style="right:15px;top:347px;font-size:11.5px;white-space:pre">' + ((f.opinion || 0) > 0 ? "Likes you  " : (f.opinion || 0) < 0 ? "Dislikes you  " : "Neutral  ") + sgn(f.opinion || 0) + "</div>" +
        standingCells(f.opinion || 0, 15, 367, 14, 5, (f.opinion || 0) > 0 ? "good" : "bad") +
        '<div class="h-eb" style="left:15px;top:391px">YOUR STANDING · ' + esc(upper(st.town || "")) + "</div>" +
        '<div style="right:15px;top:389px;font-size:11.5px;color:#9A9DA1;white-space:pre">' + esc(st.titleName || "") + "  " + sgn(st.value || 0) + "</div>" +
        '<div class="h-hair" style="left:15px;top:417px;width:285px"></div>' +
        '<div class="h-eb" style="left:15px;top:431px">YOU CARRY</div>' +
        '<div style="left:15px;top:447px;width:285px;font-size:11.5px;color:#EDEEEF">' + esc(purse ? coinText(purse) : "") + "</div>" +
        "</div>";
    });
    part(w, "hc-speech", JSON.stringify([c.name, c.line]), function () {
      return '<div class="h-speech"><div class="h-eb bad">' + esc(upper(first(c.name || f.name))) + " SAYS</div>" +
        '<div class="line">“' + esc(c.line) + "”</div></div>" + '<div class="h-hair" style="left:368px;top:179px;width:590px"></div>';
    });
    part(w, "hc-opts", JSON.stringify([c.choices, ui.focus]), function () {
      var h = '<div class="h-opts gap4 flow">';
      for (var i = 0; i < c.choices.length && i < 8; i++) {
        var o = c.choices[i];
        var text = "", dot = "";
        if (o.odds >= 0) { text = (o.kind === "bribe" && o.cost ? "Bribe " + coinText(o.cost) : upperFirst(o.kind)) + "  ·  " + o.odds + "%"; dot = oddsTone(o.odds); }
        else if (o.cost && o.cost.total > 0) { text = coinText(o.cost); dot = "wary"; }
        else if (o.minutes > 0) { text = upperFirst(o.kind) + "  ·  " + o.minutes + " min"; dot = "ink"; }
        else if (o.kind === "run" || o.kind === "fight") { text = o.kind === "run" ? "Bounty grows" : "Assault"; dot = "bad"; }
        h += '<div class="h-opt flow' + (o.enabled ? "" : " locked") + (i === ui.focus ? " focus" : "") + '" data-choice="' + i + '" style="width:598px"><div class="k">' + o.key + "</div>" +
          glyph(KIND_GLYPH[o.kind] || "talk", 16) + '<div class="l">' + esc(o.label) + "</div>" +
          (text ? '<div class="h-mchip flow">' + (dot ? '<div class="h-dot ' + dot + '"></div>' : "") + "<span>" + esc(text) + "</span></div>" : "") + "</div>";
      }
      return h + "</div>" + '<div style="left:368px;top:483px;width:590px;font-size:11px;color:#626569;line-height:14px">Odds from Speech and Presence (Mettle) on top of Hearth’s factors: armour, dress, standing, their traits.</div>';
    });
    part(w, "hc-foot", "f" + c.choices.length, function () {
      return foot("1–" + Math.max(1, c.choices.length) + " CHOOSE  ·  ESC DOES NOTHING HERE: HE IS WAITING", "confront");
    });
  }

  function upperFirst(s) { s = String(s || ""); return s.charAt(0).toUpperCase() + s.slice(1); }

  // ---- market and haggling (660:41341) ---------------------------------------------------------------------------------------
  function marketRows(m) { return ui.marketTab === "buys" ? m.buys : m.sells; }

  function market(m) {
    var w = win();
    var rows = marketRows(m);
    part(w, "hm-head", JSON.stringify([m.town, m.shop, ui.marketTab, m.sells.length, m.buys.length]), function () {
      return chip() +
        '<div class="h-segs flow" style="left:401px">' +
        '<div class="h-seg' + (ui.marketTab === "sells" ? " on" : "") + '" data-ui="mtab:sells">For sale <span class="n">' + m.sells.length + "</span></div>" +
        '<div class="h-seg' + (ui.marketTab === "buys" ? " on" : "") + '" data-ui="mtab:buys">Wanted <span class="n">' + m.buys.length + "</span></div></div>" +
        '<div style="right:28px;top:29px;font-size:12px;color:#9A9DA1;white-space:nowrap">' + esc((m.shop ? "Shop" : "Market") + " of " + m.town) + "</div>";
    });
    var visible = rows.slice(ui.scroll, ui.scroll + 8);
    part(w, "hm-stock", JSON.stringify([m.merchant, m.note, visible, m.haggle && m.haggle.row, ui.scroll, rows.length, m.sour]), function () {
      var mr = m.merchant;
      var h = '<div class="h-panel h-stock">';
      if (mr) {
        var traits = [];
        for (var i = 0; i < (mr.traits || []).length && i < 2; i++) traits.push(String(mr.traits[i].name).toLowerCase());
        h += '<div class="h-well" style="left:16px;top:16px;width:52px;height:52px;border-radius:12px">' +
          (mr.portrait ? face(mr, 48, "left:1px;top:1px") : '<span style="left:10px;top:10px">' + glyph("trade", 32, "#626569") + "</span>") + "</div>" +
          '<div style="left:80px;top:18px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc(mr.name) + "</div>" +
          '<div style="left:80px;top:38px;font-size:11px;color:#9A9DA1;white-space:pre">' + esc(mr.job + (traits.length ? "  ·  " + traits.join(", ") : "")) + "</div>" +
          '<div class="h-pill ink" style="right:16px;top:20px"><div class="h-dot ' + (mr.mood > 20 ? "good" : mr.mood < -20 ? "bad" : "wary") + '"></div><span>Mood · ' + esc(mr.moodWord) + "</span></div>";
      } else {
        h += '<div class="h-well" style="left:16px;top:16px;width:52px;height:52px;border-radius:12px;display:flex;align-items:center;justify-content:center"><span style="position:relative">' + glyph("trade", 32) + "</span></div>" +
          '<div style="left:80px;top:18px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc("Market of " + m.town) + "</div>" +
          '<div style="left:80px;top:38px;font-size:11px;color:#9A9DA1;white-space:pre">' + esc(m.culture || "") + "</div>";
      }
      h += '<div style="left:80px;top:54px;width:350px;font-size:11px;color:#626569;white-space:nowrap;overflow:hidden">' + (m.note ? "“" + esc(m.note) + "”" : m.sour ? "Soured: dearer, and no haggling for a day." : "") + "</div>" +
        '<div class="h-eb" style="left:16px;top:84px">GOODS</div><div class="h-eb" style="left:262px;top:84px">LOT</div>' +
        '<div class="h-eb" style="left:330px;top:84px">' + (ui.marketTab === "buys" ? "YOU CARRY" : "TO SPARE") + '</div><div class="h-eb" style="right:16px;top:84px">PRICE A LOT</div>';
      for (var r = 0; r < visible.length; r++) {
        var row = visible[r];
        var hag = m.haggle && m.haggle.row === row.id;
        var note = row.flags.cannotAfford ? '<div class="note bad">you can’t afford it</div>' : row.flags.townCannotPay ? '<div class="note wary">the town can’t pay</div>'
          : row.flags.noRoom ? '<div class="note wary">no room</div>' : row.flags.refused ? '<div class="note bad">refused</div>' : row.flags.stolen ? '<div class="note bad">stolen</div>' : "";
        h += '<div class="h-row' + (row.canTrade ? "" : " off") + (hag ? " hag" : "") + '" data-row="' + row.id + '" style="top:' + (102 + r * 46) + 'px">' +
          '<div class="h-well rslot" data-item="' + esc(row.item) + '" data-n="' + Math.max(1, row.lot) + '"></div>' +
          '<div class="nm">' + esc(row.name) + "</div>" +
          '<div class="lot">by ' + row.lot + "</div>" +
          '<div class="spare">' + (ui.marketTab === "buys" ? row.owned : row.available > 0 ? row.available : "sold out") + "</div>" +
          (hag ? '<div class="h-pill ink"><div class="h-dot violet"></div><span>haggling</span></div>' : note) +
          price(row.price, row.canTrade ? "" : "dim", "position:absolute;right:16px;top:14px") + "</div>";
      }
      if (!rows.length) h += '<div class="h-empty" style="top:220px">' + (ui.marketTab === "buys" ? "They want nothing you could sell them today." : "Nothing for sale today.") + "</div>";
      if (rows.length > 8) h += '<div class="h-eb" style="right:16px;top:462px">' + (ui.scroll + 1) + "–" + Math.min(rows.length, ui.scroll + 8) + " OF " + rows.length + "</div>";
      return h + "</div>";
    });
    part(w, "hm-purse", JSON.stringify([m.purse, m.status]), function () {
      return '<div class="h-panel h-purse"><div style="left:16px;top:14px;font-size:15px;color:#EDEEEF">Purse</div>' +
        '<div class="h-eb" style="right:16px;top:18px">' + num(m.purse.total) + " BITS" + (m.shop && m.till ? "  ·  TILL " + num(m.till.total) : "") + "</div>" +
        readouts3(m.purse, "left:16px;top:52px") +
        (m.status ? '<div style="left:16px;top:96px;width:323px;font-size:10.5px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' + esc(m.status) + "</div>" : "") + "</div>";
    });
    part(w, "hm-hag", JSON.stringify([m.haggle, ui.offer, m.sour]), function () { return haggle(m); });
    part(w, "hm-foot", ui.marketTab, function () {
      return foot(ui.marketTab === "buys" ? "CLICK A ROW: SELL ONE LOT  ·  SHIFT: ALL YOU CARRY  ·  RIGHT-CLICK: HAGGLE" : "CLICK A ROW: BUY ONE LOT  ·  SHIFT: A STACK  ·  RIGHT-CLICK: HAGGLE", "market");
    });
  }

  /**
   * The odds he takes an offer (OVERLAY.md haggle.takes): 100 at his word or better, else the point at or below the
   * offer (the points run from the far end of his possible limit up to his word, ascending by bits); -1 without points.
   */
  function takesAt(g, offer) {
    if (!g.takes || !g.takes.length) return -1;
    if (g.playerBuys ? offer >= g.word.total : offer <= g.word.total) return 100;
    if (g.takesLast >= 0 && g.offer && offer === g.offer.total) return g.takesLast;
    var odds = -1;
    for (var i = 0; i < g.takes.length; i++) if (g.takes[i].bits <= offer) odds = g.takes[i].odds;
    return odds >= 0 ? odds : g.playerBuys ? 0 : g.takes[0].odds;
  }

  function haggle(m) {
    var g = m.haggle;
    var h = '<div class="h-panel h-hag"><div class="h-glow gold" style="left:190px;top:-110px;width:260px;height:260px"></div>';
    if (!g) {
      return h + '<div class="h-eb" style="left:16px;top:16px">HAGGLING</div>' +
        '<div style="left:16px;top:36px;width:323px;font-size:12px;line-height:18px;color:#9A9DA1">' +
        (m.sour ? "They have had enough of haggling with you today." : "Right-click a row to haggle over it. Name a price; watch their patience. The better your Speech, the better you read where their limit lies.") +
        "</div></div>";
    }
    var offer = ui.offer != null && ui.offerRow === g.row ? ui.offer : g.offer.total > 0 ? g.offer.total : g.word.total;
    var band = g.band;
    var inBand = band && offer >= band.low.total && offer <= band.high.total;
    var big = band ? (offer >= band.low.total ? "In band" : "Low") : "?";
    var bigTone = band ? (offer >= band.low.total ? "good" : "wary") : "ink2";
    var bigNote = band ? (inBand ? "inside his limit" : offer > band.high.total ? "more than he needs" : "below his limit") : "Speech " + g.bandSpeech + " reads his limit";
    var odds = takesAt(g, offer);
    if (odds >= 0) {
      big = odds + " %";
      bigTone = oddsTone(odds);
      bigNote = "he takes it";
    }
    // The track: twenty cells from well under his limit to the posted price; his band, his word and your offer on it.
    var lo = Math.min(offer, g.word.total, band ? band.low.total : g.word.total) * 0.85;
    var hi = Math.max(g.list.total, g.word.total, offer) * 1.02;
    function at(v) { return Math.max(0, Math.min(19, Math.round((v - lo) / Math.max(1, hi - lo) * 19))); }
    var cells = "";
    for (var i = 0; i < 20; i++) {
      var v0 = lo + (hi - lo) * i / 19;
      var cls = i === at(offer) ? "me" : i === at(g.word.total) ? "his" : band && v0 >= band.low.total && v0 <= band.high.total ? "band" : "";
      cells += '<i class="' + cls + '" style="left:' + (Math.round(i * 16.2 * 10) / 10) + 'px"></i>';
    }
    var pat = Math.round(g.patience / 10);
    var patCells = "";
    for (var p = 0; p < 10; p++) patCells += '<i class="' + (p < pat ? (p === pat - 1 && g.patience < 60 ? "low" : "on") : "") + '" style="left:' + (Math.round(p * 32.8 * 10) / 10) + 'px"></i>';
    var meX = 16 + at(offer) * 16.2 + 6;
    return h + '<div class="h-eb" style="left:16px;top:16px">HAGGLING  ·  ' + (g.count > 1 ? "A STACK" : "ONE LOT") + "</div>" +
      '<div style="left:16px;top:32px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc(g.name) + " × " + g.count + "</div>" +
      '<div class="' + bigTone + '" style="right:16px;top:26px;font-size:24px;white-space:nowrap">' + big + "</div>" +
      '<div style="right:16px;top:56px;font-size:10.5px;color:#9A9DA1;white-space:nowrap">' + esc(bigNote) + "</div>" +
      '<div class="h-eb" style="left:16px;top:80px">' + (g.playerBuys ? "HIS WORD" : "HIS OFFER") + "</div>" + price(g.word, "", "position:absolute;right:16px;top:78px") +
      '<div class="h-eb" style="left:16px;top:104px">YOUR OFFER</div>' + price(coinsOf(offer), "", "position:absolute;right:16px;top:102px") +
      '<div class="h-track" style="left:16px;top:130px;width:320px;height:12px">' + cells + "</div>" +
      '<div style="left:16px;top:146px;font-size:10.5px;color:#9A9DA1;white-space:nowrap">' + (band ? "his limit ≈ " + esc(coinText(band.low)) : "his limit: hidden") + "</div>" +
      '<div class="violet" style="left:' + Math.max(110, Math.min(230, meX - 23)) + 'px;top:146px;font-size:10.5px;white-space:nowrap">your offer</div>' +
      '<div style="right:16px;top:146px;font-size:10.5px;color:#9A9DA1;white-space:nowrap">his word</div>' +
      '<div class="h-nudge flow" style="left:16px;top:166px">' +
      '<div class="h-btn" data-ui="nudge:-10%">−10 %</div><div class="h-btn" data-ui="nudge:-1">−1</div>' +
      '<div class="h-btn" data-ui="nudge:+1">+1</div><div class="h-btn" data-ui="nudge:+10%">+10 %</div></div>' +
      '<div class="h-hair" style="left:16px;top:206px;width:323px"></div>' +
      '<div class="h-eb" style="left:16px;top:216px">HIS PATIENCE</div>' +
      '<div style="right:16px;top:216px;font-size:10.5px;color:#9A9DA1">' + g.offersLeft + (g.offersLeft === 1 ? " offer left" : " offers left") + "</div>" +
      '<div class="h-pat" style="left:16px;top:234px;width:323px;height:28px">' + patCells + "</div>" +
      '<div style="left:16px;top:270px;width:323px;font-size:10.5px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' + esc(g.line) + "</div>" +
      '<div class="h-btn primary" data-op="haggleOffer" data-arg="' + offer + '" style="left:16px;top:298px;width:156px">Make the offer</div>' +
      '<div class="h-btn" data-op="haggleAccept" style="left:180px;top:298px;width:159px">Take his word</div></div>';
  }

  // ---- bank (660:42260) -------------------------------------------------------------------------------------------------------
  function bank(b) {
    var w = win();
    var tabs = [];
    if (b.hasBank) tabs.push(["bank", "Bank", ""]);
    tabs.push(["property", "Property", b.owned.length || ""]);
    if (b.hasBank) tabs.push(["changer", "Money changer", ""]);
    if (b.hasWorks) tabs.push(["works", "Works", b.projects.length || ""]);
    if (b.contracts.length) tabs.push(["contracts", "Contracts", b.contracts.length]);
    if (!ui.bankTab) ui.bankTab = b.tab === "works" || b.tab === "contracts" ? b.tab : b.hasBank ? "bank" : "property";
    var tab = ui.bankTab;
    part(w, "hk-head", JSON.stringify([tabs, tab, b.town, b.hasBank]), function () {
      var h = chip() + '<div class="h-segs flow" style="left:50%;transform:translateX(-50%)">';
      for (var i = 0; i < tabs.length; i++) h += '<div class="h-seg' + (tabs[i][0] === tab ? " on" : "") + '" data-ui="btab:' + tabs[i][0] + '">' + tabs[i][1] + (tabs[i][2] !== "" ? ' <span class="n">' + tabs[i][2] + "</span>" : "") + "</div>";
      return h + '</div><div style="right:28px;top:29px;font-size:12px;color:#9A9DA1;white-space:nowrap">' + esc((b.hasBank ? "Bank of " : "Town hall of ") + b.town) + "</div>";
    });
    part(w, "hk-left", JSON.stringify([tab, b.deposit, b.carried, b.emeralds, b.status, ui.amount, b.projects, b.contracts, b.treasury, b.ledger]), function () {
      var h = '<div class="h-panel h-acct">';
      if (tab === "changer") {
        h += '<div class="h-eb" style="left:15px;top:15px">THE MONEY CHANGER</div>' +
          '<div style="left:15px;top:33px;width:408px;font-size:12px;color:#9A9DA1;line-height:17px">Emeralds for coin and coin for emeralds, at the bank’s rate.</div>' +
          '<div class="h-eb" style="left:15px;top:85px">YOU CARRY</div><div style="left:15px;top:101px;font-size:20px;color:#EDEEEF">' + b.emeralds.carried + ' <span style="position:static;font-size:11px;color:#9A9DA1">emeralds</span></div>' +
          '<div class="h-eb" style="left:230px;top:85px">THE BANK HOLDS</div><div style="left:230px;top:101px;font-size:20px;color:#EDEEEF">' + b.emeralds.reserve + "</div>" +
          '<div class="h-hair" style="left:15px;top:143px;width:408px"></div>' +
          '<div class="h-eb" style="left:15px;top:157px">THEY PAY FOR ONE</div>' + price(b.emeralds.buy, "", "position:absolute;left:15px;top:175px") +
          '<div class="h-eb" style="left:230px;top:157px">THEY ASK FOR ONE</div>' + price(b.emeralds.sell, "", "position:absolute;left:230px;top:175px") +
          '<div class="h-btn primary" data-op="sellEmeralds" data-arg="1" style="left:15px;top:213px;width:200px">Sell an emerald</div>' +
          '<div class="h-btn" data-op="buyEmeralds" data-arg="1" style="left:223px;top:213px;width:200px">Buy an emerald</div>';
      } else if (tab === "works") {
        h += '<div class="h-eb" style="left:15px;top:15px">PUBLIC WORKS  ·  TREASURY ' + esc(upper(coinText(b.treasury))) + "</div>";
        for (var i = 0; i < b.projects.length && i < 6; i++) {
          var p = b.projects[i];
          var y = 35 + i * 72;
          h += '<div class="h-plot own" style="top:' + y + 'px;width:424px"><div class="h-well g">' + glyph("house", 16) + '</div><div class="a">' + esc(p.name) + "</div>" +
            '<div class="b">' + esc("stage " + p.stage + " of " + p.stages + "  ·  " + coinText(p.given) + " of " + coinText(p.cost) + (p.stalled ? "  ·  stalled" : "")) + "</div>" +
            '<div class="h-btn" data-op="fund" data-arg="' + p.plot + "#" + (p.fund && p.fund.total > 0 ? p.fund.total : 10) + '" style="right:10px;top:16px;height:30px">Give ' + esc(coinText(p.fund && p.fund.total > 0 ? p.fund : coinsOf(10))) + "</div></div>";
        }
        if (!b.projects.length) h += '<div class="h-empty" style="top:200px">No public works are rising here.</div>';
      } else if (tab === "contracts") {
        h += '<div class="h-eb" style="left:15px;top:15px">SUPPLY CONTRACTS</div>';
        for (var k = 0; k < b.contracts.length && k < 6; k++) {
          var c = b.contracts[k];
          h += '<div class="h-plot own" style="top:' + (35 + k * 72) + 'px;width:424px"><div class="h-well g">' + glyph("trade", 16) + '</div><div class="a">' + esc(c.units + " × " + c.goodsName) + "</div>" +
            '<div class="b">' + esc(c.delivered + " delivered  ·  pays " + coinText(c.pays) + "  ·  " + c.daysLeft + " days") + "</div>" +
            (c.taken ? '<div class="h-btn' + (c.carrying ? " primary" : " off") + '" data-op="deliver" data-arg="' + c.id + '" style="right:10px;top:16px;height:30px">Deliver</div>'
              : '<div class="h-btn" data-op="acceptContract" data-arg="' + c.id + '" style="right:10px;top:16px;height:30px">Take it</div>') + "</div>";
        }
      } else {
        h += '<div class="h-well" style="left:15px;top:15px;width:52px;height:52px;border-radius:12px;display:flex;align-items:center;justify-content:center"><span style="position:relative">' + glyph("coin", 32, "#E3C47E") + "</span></div>" +
          '<div style="left:79px;top:19px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc((b.hasBank ? "Bank of " : "Town hall of ") + b.town) + "</div>" +
          '<div style="left:79px;top:39px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + esc("Standing " + sgn(b.reputation)) + "</div>" +
          '<div style="left:79px;top:53px;font-size:11px;color:#626569;white-space:nowrap">“Deposits are honoured at every bank.”</div>' +
          '<div class="h-hair" style="left:15px;top:83px;width:408px"></div>' +
          '<div class="h-eb" style="left:15px;top:97px">ON DEPOSIT</div><div style="right:15px;top:95px;font-size:11px;color:#9A9DA1">' + num(b.deposit.total) + " bits</div>" +
          readouts3(b.deposit, "left:15px;top:115px") +
          '<div class="h-eb" style="left:15px;top:169px">CARRIED</div><div style="right:15px;top:167px;font-size:11px;color:#9A9DA1">' + num(b.carried.total) + " bits</div>" +
          readouts3(b.carried, "left:15px;top:187px") +
          '<div class="h-hair" style="left:15px;top:239px;width:408px"></div>' +
          '<div class="h-eb" style="left:15px;top:253px">AMOUNT</div>' +
          '<div class="h-well" style="left:15px;top:271px;width:408px;height:44px;border-radius:14px"><div style="left:13px;top:11px;font-size:15px;color:#EDEEEF;white-space:pre">' +
          esc(ui.amount === -1 ? "All of it" : coinText(coinsOf(ui.amount))) + '</div><div style="right:13px;top:14px;font-size:11px;color:#626569">' + (ui.amount === -1 ? "" : "= " + num(ui.amount) + " bits") + "</div></div>" +
          '<div class="h-traits flow" style="left:15px;top:325px">' +
          '<div class="h-pill" data-ui="amt:100">+1 crown</div><div class="h-pill" data-ui="amt:10">+1 mark</div><div class="h-pill" data-ui="amt:1">+1 bit</div>' +
          '<div class="h-pill' + (ui.amount === -1 ? " on" : "") + '" data-ui="amt:all">All</div><div class="h-pill" data-ui="amt:clear">Clear</div></div>' +
          '<div class="h-btn primary" data-ui="deposit" style="left:15px;top:369px;width:200px">Deposit</div>' +
          '<div class="h-btn" data-ui="withdraw" style="left:223px;top:369px;width:200px">Withdraw</div>' +
          '<div class="h-eb" style="left:15px;top:419px">LATELY</div>' + lately(b);
      }
      return h + "</div>";
    });
    part(w, "hk-prop", JSON.stringify([b.owned, b.forSale, ui.plot, b.town]), function () {
      var h = '<div class="h-panel h-prop"><div class="h-eb" style="left:15px;top:15px">YOUR PROPERTY  ·  DEEDS HELD</div>';
      var y = 35;
      for (var i = 0; i < b.owned.length && i < 2; i++, y += 72) {
        var p = b.owned[i];
        h += '<div class="h-plot own" style="top:' + y + 'px"><div class="h-well g">' + glyph("house", 16) + '</div><div class="a">' + esc(p.name) + "</div>" +
          '<div class="b">' + esc(p.sizeX + " × " + p.sizeZ + " blocks" + (p.built ? "" : "  ·  unbuilt") + (p.business ? "  ·  " + p.workers + " at work" : "")) + '</div><div class="c brass">Deed held</div>' +
          (p.canHire ? '<div class="h-btn" data-op="hire" data-arg="' + p.plot + '" style="right:10px;top:28px;height:26px;font-size:11px">Hire</div>' : "") + "</div>";
      }
      if (!b.owned.length) { h += '<div style="left:15px;top:40px;font-size:11.5px;color:#626569">You hold no deeds here.</div>'; y = 72; }
      h += '<div class="h-hair" style="left:15px;top:' + (y + 4) + 'px;width:443px"></div><div class="h-eb" style="left:15px;top:' + (y + 18) + 'px">FOR SALE IN ' + esc(upper(b.town)) + "</div>";
      var sel = null;
      for (var k = 0, yy = y + 38; k < b.forSale.length && k < 2; k++, yy += 70) {
        var f = b.forSale[k];
        var locked = f.lowStanding || f.cannotAfford;
        if (ui.plot === f.plot) sel = f;
        h += '<div class="h-plot' + (locked ? " locked" : "") + (ui.plot === f.plot ? " sel" : "") + '" data-plot="' + f.plot + '" style="top:' + yy + 'px"><div class="h-well g">' + glyph("house", 16) + '</div><div class="a">' + esc(f.name) + "</div>" +
          '<div class="b">' + esc(f.sizeX + " × " + f.sizeZ + " blocks" + (f.lowStanding ? "  ·  needs standing " + sgn(f.minStanding) : f.cannotAfford ? "  ·  more than you have" : "  ·  you can build here")) + "</div>" +
          '<div class="c ' + (locked ? "ink3" : "ink") + '">' + esc(coinText(f.price)) + "</div></div>";
      }
      if (!b.forSale.length) h += '<div style="left:15px;top:' + (y + 40) + 'px;font-size:11.5px;color:#626569">Nothing for sale here today.</div>';
      if (sel) h += '<div class="h-btn" data-op="buyPlot" data-arg="' + sel.plot + '" style="left:15px;top:369px;width:443px">Buy the plot  ·  ' + esc(coinText(sel.price)) + "</div>";
      return h + '<div style="left:15px;top:415px;width:443px;font-size:11px;color:#626569;line-height:15px">A deed is an item: whoever holds it holds the title. Keep it safe, or keep it in the bank’s chest.</div></div>';
    });
    part(w, "hk-foot", "f", function () { return foot("YOUR COIN IS ONE ACCOUNT IN EVERY TOWN  ·  PROPERTY IS PER TOWN", "bank"); });
  }

  /** The bank's LATELY lines (Figma 3_bank): the ledger's newest two, "Day 33 · Deposited 2 crowns at Kestrel Ford". */
  function lately(b) {
    var led = b.ledger || [];
    if (!led.length) return '<div style="left:15px;top:435px;width:408px;font-size:11px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' + esc(b.status || "Nothing yet this visit.") + "</div>";
    var h = "";
    for (var i = 0; i < led.length && i < 2; i++) {
      var l = led[i];
      h += '<div class="flow" style="left:15px;top:' + (435 + i * 18) + 'px;width:408px;font-size:11px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' +
        '<span style="color:#626569">Day ' + (l.day + 1) + "  ·  </span>" + esc(l.text + (l.town ? " at " + l.town : "")) + "</div>";
    }
    return h;
  }

  // ---- dice (662:42287): the six dice are Java's (the board rect), the rest is the page ---------------------------------------
  var RULES = "<b>Scoring</b><br>A single 1 is 100, a single 5 is 50. Three of a kind is the face × 100 (three 1s: 1,000). Pick scoring dice to set them aside, then roll the rest or bank the turn. No scoring dice in a roll: bust, the turn is lost.";

  function dicePage(d) {
    var w = win();
    part(w, "hd-head", JSON.stringify([d.opponent, d.stake]), function () {
      return chip() + '<div class="h-title h-center" style="top:27px">Dice with ' + esc(d.opponent.name) + "</div>" +
        (d.stake && d.stake.total > 0 ? '<div class="h-pill ink" style="right:28px;top:24px"><div class="h-dot wary"></div><span>' + esc("Stake " + coinText(d.stake) + " each  ·  pot " + coinText(coinsOf(d.stake.total * 2))) + "</span></div>" : "");
    });
    var kept = [];
    for (var i = 0; i < d.dice.length; i++) if (d.dice[i].kept) kept.push(d.dice[i].face);
    part(w, "hd-felt", JSON.stringify([kept, d.turnPoints]), function () {
      var t = "";
      for (var k = 0; k < kept.length; k++) t += '<svg style="left:' + (7 + k * 36) + 'px" width="28" height="28" viewBox="0 0 28 28">' + dieSvg(kept[k]) + "</svg>";
      return '<div class="h-felt"><div class="h-native" data-nfx="dice" style="left:15px;top:15px;width:568px;height:300px"></div>' +
        '<div class="h-eb" style="left:15px;top:329px">SET ASIDE THIS TURN</div>' +
        '<div class="h-well h-tray">' + t + '<div style="left:' + (7 + Math.max(1, kept.length) * 36 + 12) + 'px;top:11px;font-size:12px;color:#9A9DA1;white-space:pre">' +
        (kept.length ? kept.join("  ·  ") + "   =  " + d.turnPoints : "Nothing set aside yet") + "</div></div></div>";
    });
    part(w, "hd-scores", JSON.stringify([d.opponent, d.target, d.scores, d.turnPoints, d.selectionScore, d.status, d.line]), function () {
      var t = d.target || 4000;
      var me = Math.round(Math.min(1, d.scores.player / t) * 20), them = Math.round(Math.min(1, d.scores.folk / t) * 20);
      return '<div class="h-panel h-scores">' +
        (d.opponent.portrait ? '<div class="h-well" style="left:15px;top:15px;width:44px;height:44px;border-radius:10px">' + face(d.opponent, 40, "left:1px;top:1px") + "</div>"
          : '<div class="h-well" style="left:15px;top:15px;width:44px;height:44px;border-radius:10px;display:flex;align-items:center;justify-content:center"><span style="position:relative">' + glyph("dice", 24) + "</span></div>") +
        '<div style="left:71px;top:17px;font-size:14px;color:#EDEEEF;white-space:nowrap">' + esc(d.opponent.name) + "</div>" +
        '<div style="left:71px;top:37px;font-size:11px;color:#9A9DA1;white-space:pre">' + esc(d.opponent.temper ? "plays " + d.opponent.temper : "") + "</div>" +
        '<div class="h-hair" style="left:15px;top:73px;width:283px"></div>' +
        '<div class="h-eb" style="left:15px;top:87px">FIRST TO ' + num(t) + "</div>" +
        '<div style="left:15px;top:107px;font-size:12.5px;color:#EDEEEF">You</div><div style="right:15px;top:105px;font-size:16px;color:#EDEEEF">' + num(d.scores.player) + "</div>" +
        rowCells(20, Math.max(me, d.scores.player > 0 ? 1 : 0), 10, 14.2, 15, 129, "violet", 2) +
        '<div style="left:15px;top:149px;font-size:12.5px;color:#EDEEEF">' + esc(d.opponent.name) + '</div><div style="right:15px;top:147px;font-size:16px;color:#9A9DA1">' + num(d.scores.folk) + "</div>" +
        rowCells(20, Math.max(them, d.scores.folk > 0 ? 1 : 0), 10, 14.2, 15, 171, "brass", 2) +
        '<div class="h-hair" style="left:15px;top:193px;width:283px"></div>' +
        '<div class="h-eb" style="left:15px;top:207px">THIS TURN</div>' +
        '<div style="left:15px;top:223px;font-size:28px;color:#EDEEEF">' + num(d.turnPoints) + "</div>" +
        (d.selectionScore > 0 ? '<div class="good" style="left:' + (25 + String(num(d.turnPoints)).length * 16) + 'px;top:235px;font-size:11.5px;white-space:pre">picked  +' + d.selectionScore + "</div>" : "") +
        '<div style="left:15px;top:267px;width:283px;font-size:11.5px;color:#9A9DA1;line-height:15px">' + esc(d.status) + "</div>" +
        '<div class="h-hair" style="left:15px;top:305px;width:283px"></div>' +
        '<div class="h-well" style="left:15px;top:319px;width:283px;height:68px;border-radius:12px"><div style="left:11px;top:9px;width:259px;font-size:11.5px;font-weight:400;color:#EDEEEF;line-height:15px">' +
        (d.line ? "“" + esc(d.line) + "”" : "") + "</div></div></div>";
    });
    part(w, "hd-actions", JSON.stringify([d.phase, d.canRoll, d.canBank, d.stakes, d.loaded, d.cheatCaught, d.turnPoints, d.selectionScore, kept.length, ui.pop]), function () {
      var h = '<div class="h-traits flow" style="left:27px;top:495px;gap:8px">';
      if (d.phase === "stake") {
        for (var i = 0; i < d.stakes.length && i < 5; i++) h += '<div class="h-btn big' + (i === 0 ? " primary" : "") + '" data-op="diceStake" data-arg="' + i + '" style="width:130px">' + (d.stakes[i].total ? esc(coinText(d.stakes[i])) : "For honour") + "</div>";
      } else if (d.phase === "player_rolled") {
        var left = 0;
        for (var k = 0; k < d.dice.length; k++) if (!d.dice[k].kept && !d.dice[k].selected) left++;
        h += '<div class="h-btn big' + (d.canRoll ? " primary" : " off") + '" data-op="diceRoll" style="width:180px">Roll on  ·  ' + (left || 6) + " dice</div>" +
          '<div class="h-btn big' + (d.canBank ? "" : " off") + '" data-op="diceBank" style="width:130px">Bank ' + num(d.turnPoints + d.selectionScore) + "</div>";
      } else if (d.phase === "player_bust") {
        h += '<div class="h-btn big primary" data-op="dicePass" style="width:180px">Pass the dice</div>';
      } else if (d.phase === "over") {
        if (!d.cheatCaught) h += '<div class="h-btn big primary" data-op="diceAgain" style="width:180px">Another game</div>';
      } else {
        h += '<div class="h-btn big off" style="width:180px">Their turn…</div>';
      }
      h += "</div>";
      if (d.loaded !== "none" && d.phase !== "over") {
        var on = d.loaded === "inPlay";
        h += '<div data-op="diceLoaded" data-arg="' + (on ? 0 : 1) + '" class="flow" style="left:643px;top:504px;display:flex;align-items:center;gap:8px"><div class="h-toggle' + (on ? " on" : "") + '" style="position:relative"><div class="tr" style="position:absolute"></div><div class="kn" style="position:absolute"></div></div>' +
          '<span style="font-size:11.5px;color:#9A9DA1">Loaded dice: ' + (on ? "on" : "off") + "</span></div>";
      }
      h += '<div data-ui="pop:rules" style="right:28px;top:505px;font-size:11.5px;color:#9A9DA1;white-space:pre;display:flex;align-items:center;gap:6px">How to score<span style="position:static;width:13px;height:13px;border-radius:7px;border:1px solid #626569;font-size:9px;line-height:11px;text-align:center;color:#9A9DA1">?</span></div>';
      if (ui.pop === "rules") h += '<div class="h-pop" style="right:28px;top:330px">' + RULES + "</div>";
      return h;
    });
    part(w, "hd-foot", "f", function () {
      return foot("SPACE ROLLS  ·  B BANKS  ·  CLICK DICE TO SET THEM ASIDE  ·  A SINGLE 1 = 100, A SINGLE 5 = 50, THREE OF A KIND = FACE × 100", "dice");
    });
  }

  function dieSvg(face) {
    var pos = {1: [[14, 14]], 2: [[8, 8], [20, 20]], 3: [[8, 8], [14, 14], [20, 20]], 4: [[8, 8], [20, 8], [8, 20], [20, 20]],
      5: [[8, 8], [20, 8], [14, 14], [8, 20], [20, 20]], 6: [[8, 8], [20, 8], [8, 14], [20, 14], [8, 20], [20, 20]]}[face] || [];
    var h = '<rect x="0" y="0" width="28" height="28" rx="7" fill="#EDEEEF" fill-opacity="0.75"/>';
    for (var i = 0; i < pos.length; i++) h += '<rect x="' + (pos[i][0] - 2) + '" y="' + (pos[i][1] - 2) + '" width="4" height="4" rx="1.2" fill="#111214"/>';
    return h;
  }

  // ---- lockpicking (663:43403): the lock, pick and strain fill are Java's ------------------------------------------------------------
  var TIERS = ["", "Easy", "Fair", "Hard", "Very hard", "Masterwork"];

  function lockpick(l) {
    var w = win();
    var law = H && H.law || {};
    var owner = law.hint && law.hint.owner;
    part(w, "hl-head", JSON.stringify([owner]), function () {
      return chip() + '<div class="h-title h-center" style="top:27px">Picking a lock</div>' +
        (owner ? '<div class="h-pill ink law" style="right:28px;top:24px">' + glyph("hand", 16, "#C2645A") + "<span>Owned by " + esc(owner) + "</span></div>" : "");
    });
    part(w, "hl-info", JSON.stringify([l, law.stealth, law.status && law.status.suspicion]), function () {
      var tiers = "";
      for (var i = 0; i < 5; i++) tiers += '<i class="' + (i < l.tier ? "on" : "") + '" style="left:' + (i * 54.8).toFixed(1) + 'px;width:49px"></i>';
      var picks = "";
      for (var p = 0; p < Math.max(4, Math.min(6, l.picks)); p++) picks += '<div class="h-well">' + glyph("pick", 32, "#EDEEEF", p < l.picks ? null : 0.25) + "</div>";
      var st = law.stealth || {};
      var risk = '<div class="h-pill ink" style="left:15px;top:329px"><div class="h-dot ' + (st.noticed ? "bad" : st.active ? "good" : "wary") + '"></div><span>' +
        (st.noticed ? "You have been noticed" : st.active ? "You are sneaking  ·  " + (st.visibility < 0.34 ? "hidden" : st.visibility < 0.67 ? "half seen" : "in plain sight") : "You are not sneaking") + "</span></div>";
      if (law.status && law.status.suspicion > 0) risk += '<div class="h-pill ink" style="left:15px;top:359px"><div class="h-dot wary"></div><span>Suspicion here  ·  ' + Math.round(law.status.suspicion) + "</span></div>";
      var stateText = l.state === "opened" ? "It opens." : l.state === "spotted" ? "Spotted!" : l.state === "noPicks" ? "No picks left." : "";
      return '<div class="h-panel h-lockinfo">' +
        '<div class="h-well" style="left:15px;top:15px;width:52px;height:52px;border-radius:12px">' + '<span style="left:9px;top:9px">' + glyph("lock", 32) + "</span></div>" +
        '<div style="left:79px;top:17px;width:205px;font-size:14px;color:#EDEEEF;white-space:nowrap;overflow:hidden">' + esc(l.what) + "</div>" +
        '<div style="left:79px;top:37px;font-size:11px;color:#9A9DA1;white-space:pre">' + esc(l.tierKey ? "Lock  ·  tier " + l.tier : "") + "</div>" +
        '<div class="h-hair" style="left:15px;top:83px;width:268px"></div>' +
        '<div class="h-eb" style="left:15px;top:97px">LOCK</div><div class="wary" style="right:15px;top:95px;font-size:11.5px">' + (TIERS[l.tier] || "") + "</div>" +
        '<div class="h-tiers" style="left:15px;top:115px;width:268px;height:8px">' + tiers + "</div>" +
        '<div class="h-eb" style="left:15px;top:137px">PICKS</div><div style="right:15px;top:135px;font-size:11.5px;color:#EDEEEF">' + l.picks + " left</div>" +
        '<div class="h-picks flow" style="left:15px;top:155px">' + picks + "</div>" +
        '<div class="h-eb" style="left:15px;top:227px">YOUR HANDS</div>' +
        '<div style="left:15px;top:243px;font-size:12px;color:#EDEEEF;white-space:pre">' + (l.skill > 0 ? "Thievery " + l.skill : "Untrained hands") + "</div>" +
        '<div style="left:15px;top:261px;width:268px;font-size:10.5px;color:#626569">Mettle sets the skill; Hearth widens the sweet spot by it.</div>' +
        '<div class="h-hair" style="left:15px;top:297px;width:268px"></div><div class="h-eb" style="left:15px;top:311px">RISK</div>' + risk +
        '<div style="left:15px;top:425px;width:268px;font-size:10.5px;color:' + (stateText ? "#EDEEEF" : "#626569") + '">' + esc(stateText || "Seen while picking: theft and trespass, witnessed. A darker room is a safer one.") + "</div>" +
        '<div class="h-btn" data-op="stopPicking" style="left:15px;top:' + 380 + 'px;width:120px;height:28px;font-size:11.5px">Stop</div>' +
        "</div>";
    });
    part(w, "hl-lock", "rects", function () {
      return '<div class="h-native" data-nfx="lock" data-p="130" data-q="179" style="left:343px;top:75px;width:615px;height:420px"></div>' +
        '<div class="h-panel" style="left:343px;top:505px;width:615px;height:44px;border-radius:14px"><div class="h-eb" style="left:13px;top:15px">STRAIN</div>' +
        '<div data-nfx="strain" style="left:69px;top:11px;width:456px;height:20px"></div></div>';
    });
    part(w, "hl-strain", l.state, function () {
      var s = l.state === "picking" ? "pick holds" : l.state === "opened" ? "open" : l.state === "spotted" ? "spotted" : l.state === "noPicks" ? "no picks" : "";
      return '<div style="left:894px;top:517px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + s + "</div>";
    });
    part(w, "hl-foot", "f", function () { return foot("MOVE THE MOUSE TO WORK THE PICK  ·  HOLD LEFT MOUSE, D OR SPACE TO TURN  ·  ESC TO STOP", "lockpick"); });
  }

  // ---- pickpocketing (663:43616): awareness, the hand ring and the lift are Java's -----------------------------------------------
  function pickpocket(p) {
    var w = win();
    var st = H && H.law && H.law.stealth || {};
    part(w, "hp-head", JSON.stringify([p.victim, p.asleep, st.active, p.state]), function () {
      return chip() + '<div class="h-title h-center" style="top:27px">' + esc(p.victim) + "’s pockets</div>" +
        '<div class="h-pill ink" style="right:28px;top:24px"><div class="h-dot ' + (p.state === "caught" ? "bad" : p.asleep || st.active ? "good" : "wary") + '"></div><span>' +
        (p.state === "caught" ? "Caught" : p.asleep ? "Asleep" : st.active ? "Sneaking" : "In plain sight") + "</span></div>";
    });
    part(w, "hp-mark", JSON.stringify([p.victim, p.asleep, p.state, p.taken, p.portraitKey, !!p.portrait]), function () {
      return '<div class="h-panel h-lockinfo">' +
        '<div class="h-well" style="left:15px;top:15px;width:268px;height:150px;border-radius:14px">' +
        (p.portrait ? face(p, 144, "left:61px;top:5px") : '<span style="left:102px;top:43px">' + glyph(p.asleep ? "eye" : "hand", 64, "#626569") + "</span>") + "</div>" +
        '<div style="left:15px;top:179px;font-size:18px;color:#EDEEEF;white-space:nowrap">' + esc(p.victim) + "</div>" +
        '<div style="left:15px;top:205px;font-size:11.5px;color:#9A9DA1;white-space:pre">' + (p.asleep ? "Asleep" : "Awake") + "</div>" +
        '<div class="h-pill ink" style="left:15px;top:229px"><div class="h-dot ' + (p.state === "caught" ? "bad" : p.asleep ? "good" : "wary") + '"></div><span>' +
        (p.state === "caught" ? "She has turned round" : p.asleep ? "Asleep  ·  easier" : "Awake  ·  careful") + "</span></div>" +
        '<div class="h-hair" style="left:15px;top:267px;width:268px"></div>' +
        '<div class="h-eb" style="left:15px;top:281px">AWARENESS</div>' +
        '<div data-nfx="awareness" style="left:15px;top:299px;width:268px;height:24px"></div>' +
        '<div style="left:15px;top:331px;width:268px;font-size:10.5px;color:#626569">Rises the longer you take and with what you take. Full: they turn round.</div>' +
        '<div class="h-eb" style="left:15px;top:375px">YOUR HANDS</div>' +
        '<div style="left:15px;top:391px;font-size:12px;color:#EDEEEF;white-space:pre">' + p.taken + (p.taken === 1 ? " thing taken" : " things taken") + "</div>" +
        '<div style="left:15px;top:411px;width:268px;font-size:10.5px;color:#626569">Asleep folk are easier. Gloves help; armour does not.</div></div>';
    });
    if (ui.pslot >= 0) {
      var still = false;
      for (var q = 0; q < p.items.length; q++) if (p.items[q].slot === ui.pslot && !p.items[q].empty) still = true;
      if (!still) ui.pslot = -1;
    }
    part(w, "hp-pocket", JSON.stringify([p.items, p.lifting, p.taken, ui.pslot, p.state]), function () {
      var n = Math.max(1, p.items.length), pitch = Math.min(97, 585 / n), size = Math.min(80, pitch - 8);
      var h = '<div class="h-panel" style="left:343px;top:75px;width:615px;height:330px"><div class="h-eb" style="left:15px;top:15px">THE POCKET  ·  WHAT YOU CAN SEE</div>' +
        '<div style="right:15px;top:13px;font-size:11px;color:#9A9DA1">' + p.taken + " taken</div>";
      var sel = null;
      for (var i = 0; i < p.items.length; i++) {
        var it = p.items[i];
        var x = 15 + i * pitch;
        var on = ui.pslot === it.slot;
        if (on) sel = it;
        h += '<div class="h-pslot' + (on ? " sel" : "") + (it.empty ? " empty" : "") + '" data-pslot="' + it.slot + '" style="left:' + x + "px;top:39px;width:" + size + "px;height:" + size + 'px"' + (it.empty ? "" : ' data-item="' + esc(it.item) + '" data-n="' + it.count + '"') + ">" +
          (it.count > 1 && !it.empty ? '<div class="cnt">' + it.count + "</div>" : "") + (p.lifting === it.slot ? '<div class="live"></div>' : "") + "</div>" +
          (p.lifting === it.slot ? '<div data-nfx="liftfill" style="left:' + x + "px;top:39px;width:" + size + "px;height:" + size + 'px"></div>' : "") +
          '<div style="left:' + x + "px;top:" + (47 + size) + "px;width:" + size + 'px;text-align:center;font-size:11px;color:' + (on ? "#EDEEEF" : "#9A9DA1") + ';white-space:nowrap;overflow:hidden">' + esc(it.empty ? "taken" : it.name) + "</div>";
      }
      h += '<div class="h-well" style="left:15px;top:179px;width:583px;height:134px;border-radius:14px"><div class="h-eb" style="left:15px;top:15px">SELECTED</div>';
      if (sel) {
        var lifting = p.lifting === sel.slot;
        h += '<div style="left:15px;top:31px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc(sel.name) + (sel.count > 1 ? " × " + sel.count : "") + "</div>" +
          '<div style="left:15px;top:53px;width:400px;font-size:11.5px;color:#9A9DA1">' + (lifting ? "Your hand is on it. Hold still until it comes free." : "Heavier things take longer to lift.") + "</div>" +
          (lifting ? '<div class="h-btn" data-op="stopLifting" style="left:15px;top:89px;width:140px">Let go</div>'
            : '<div class="h-btn primary" data-op="lift" data-arg="' + sel.slot + '" style="left:15px;top:89px;width:140px">Take it</div>');
      } else {
        h += '<div style="left:15px;top:35px;width:550px;font-size:11.5px;color:#9A9DA1">' + (p.state === "caught" ? "Caught in the act." : "Click a thing in the pocket to reach for it.") + "</div>";
      }
      return h + "</div></div>";
    });
    part(w, "hp-hand", "f", function () {
      return '<div class="h-panel" style="left:343px;top:421px;width:615px;height:136px"><div class="h-eb" style="left:15px;top:15px">YOUR HAND IN THE POCKET</div>' +
        '<div data-nfx="hand" style="left:15px;top:35px;width:84px;height:84px"></div>' +
        '<div style="left:115px;top:39px;width:470px;font-size:11.5px;color:#9A9DA1">The ring closes while your hand is in. Take what you can, then walk away before it shuts.</div>' +
        '<div class="h-btn" data-op="slipAway" style="left:115px;top:83px;width:140px">Walk away</div>' +
        '<div style="left:271px;top:91px;font-size:10.5px;color:#626569;white-space:pre">Esc  ·  the same</div></div>' +
        foot("CLICK AN ITEM TO TAKE IT  ·  EACH TAKE RAISES AWARENESS  ·  ESC WALKS AWAY", "pickpocket");
    });
  }

  // ---- the Quests page: journal (656:39391), notice board (656:40184), reputation (658:40916), finished -----------------------------
  function quests() {
    var w = win();
    var j = H.journal || {active: [], finished: {completed: 0, failed: 0, total: 0, recent: []}};
    var board = H.board;
    var counts = [["journal", "Journal", j.active.length], ["board", "Notice board", board ? board.counts.open : ""], ["reputation", "Reputation", ""], ["finished", "Finished", j.finished.total]];
    part(w, "hq-head", JSON.stringify([counts, seg]), function () {
      var h = chip() + '<div class="h-segs flow" style="left:50%;transform:translateX(-50%)">';
      for (var i = 0; i < counts.length; i++) h += '<div class="h-seg' + (counts[i][0] === seg ? " on" : "") + '" data-ui="seg:' + counts[i][0] + '">' + counts[i][1] + (counts[i][2] !== "" ? ' <span class="n">' + counts[i][2] + "</span>" : "") + "</div>";
      return h + '</div><div class="h-search"><svg width="15" height="15" viewBox="0 0 15 15"><circle cx="6.74" cy="6.74" r="6.08" stroke="#9A9DA1" stroke-width="1.48" fill="none"/><path d="M14.34 14.34L11.07 11.07" stroke="#9A9DA1" stroke-width="1.48" stroke-linecap="round"/></svg></div>';
    });
    var body = document.getElementById("hq-body");
    if (body && body.__seg !== seg) { body.__k = null; body.__seg = seg; }
    if (seg === "board") boardPage(w, board);
    else if (seg === "reputation") reputationPage(w, H.reputation);
    else if (seg === "finished") finishedPage(w, j);
    else journalPage(w, j);
  }

  function questEyebrow(q) { return upper(q.type) + "  ·  " + upper(q.town); }
  function giverWell(q, size, g) {
    return glyph(TYPE_GLYPH[q.type] || "scroll", g || 32, "#EDEEEF");
  }

  function journalPage(w, j) {
    var list = j.active;
    var sel = null;
    for (var i = 0; i < list.length; i++) if (list[i].id === ui.quest) sel = list[i];
    if (!sel && list.length) { sel = list[0]; ui.quest = sel.id; }
    var wt = H.wantedTrack || null;
    part(w, "hq-body", JSON.stringify(["j", list, j.finished, ui.quest, ui.pop, ui.scroll, wt]), function () {
      var tracked = [], under = [], ready = [];
      for (var i = 0; i < list.length; i++) {
        var q = list[i];
        if (q.tracked) tracked.push(q); else if (q.state === "ready") ready.push(q); else under.push(q);
      }
      var h = '<div class="h-qlist">', y = 0;
      function section(name, qs, lead) {
        if (!qs.length && !lead) return;
        h += '<div class="h-eb" style="left:4px;top:' + y + 'px">' + name + "</div>";
        y += 16;
        if (lead) {
          h += lead(y);
          y += 84;
        }
        for (var k = 0; k < qs.length; k++) {
          var q = qs[k];
          var obj = null;
          for (var o = 0; o < q.objectives.length; o++) if (!q.objectives[o].done) { obj = q.objectives[o]; break; }
          var line = q.state === "ready" ? "Done! Return to " + q.giver.name + " in " + q.town + "." : q.progressText || (obj ? obj.text : q.summary);
          var frac = 0, tot = 0;
          for (var o2 = 0; o2 < q.objectives.length; o2++) { tot += q.objectives[o2].need || 1; frac += Math.min(q.objectives[o2].have, q.objectives[o2].need || 1); }
          var lit = tot ? Math.round(frac / tot * 12) : 0;
          h += '<div class="h-q' + (q.id === ui.quest ? " sel" : "") + '" data-quest="' + q.id + '" style="top:' + y + 'px">' +
            '<div class="h-well gw">' + giverWell(q) + "</div>" +
            '<div class="h-eb e">' + esc(questEyebrow(q)) + "</div>" +
            '<div class="t">' + esc(q.title) + "</div>" +
            '<div class="p' + (q.state === "ready" ? " good" : "") + '">' + esc(line) + (q.distance >= 0 && q.state !== "ready" ? "  ·  " + Math.round(q.distance) + " m" : "") + "</div>" +
            (q.state !== "ready" ? rowCells(12, lit, 5, 9.4, 76, 64, "violet", 1.5) : "") +
            (q.tracked ? '<div class="h-pill ink"><div class="h-dot violet"></div><span>On HUD</span></div>' : "") + "</div>";
          y += 84;
        }
        y += 4;
      }
      // A tracked wanted poster leads the TRACKED list (one thing is tracked at a time, so usually alone).
      section("TRACKED", tracked, wt ? function (at) { return wantedRow(wt, at); } : null);
      section("UNDER WAY", under);
      section("READY TO HAND IN", ready);
      if (!list.length && !wt) h += '<div class="h-empty" style="top:200px">No quests under way. Notice boards in every town have work.</div>';
      var f = j.finished;
      if (f.total > 0 && y < 380) {
        var good = f.completed;
        h += '<div class="h-panel" style="left:0;top:396px;width:372px;height:86px;border-radius:16px"><div class="h-eb" style="left:14px;top:12px">FINISHED  ·  ' + f.total + "  ·  " + good + " ENDED WELL</div>";
        for (var r = 0; r < f.recent.length && r < 2; r++) {
          var e = f.recent[r];
          var ok = e.outcome === "completed";
          h += '<div style="left:14px;top:' + (30 + r * 22) + 'px;font-size:11.5px;color:' + (ok ? "#EDEEEF" : "#9A9DA1") + ';white-space:pre">' + (ok ? "✓  " : "✕  ") + esc(e.title) + "</div>" +
            '<div style="right:14px;top:' + (31 + r * 22) + 'px;font-size:10.5px;color:#9A9DA1">' + esc(e.town) + "</div>";
        }
        h += "</div>";
      }
      h += "</div>";
      return h + questDetail(sel);
    });
    part(w, "hq-foot", JSON.stringify(["j", list.length]), function () {
      var hud = 0;
      for (var i = 0; i < list.length; i++) if (list[i].tracked) hud++;
      return foot(list.length + " IN THE JOURNAL  ·  " + hud + " ON HUD  ·  J OPENS THE JOURNAL ANYWHERE", "journal");
    });
  }

  function questDetail(q) {
    if (!q) return '<div class="h-panel h-qd"><div class="h-empty" style="top:220px">Pick a quest to read it.</div></div>';
    var h = '<div class="h-panel h-qd">' +
      '<div class="h-well" style="left:20px;top:20px;width:72px;height:72px;border-radius:14px"><span style="left:12px;top:12px">' + giverWell(q, 72, 48) + "</span></div>" +
      '<div class="h-eb" style="left:108px;top:22px">' + esc(questEyebrow(q)) + "</div>" +
      '<div style="left:108px;top:38px;width:415px;font-size:20px;color:#EDEEEF;white-space:nowrap;overflow:hidden">' + esc(q.title) + "</div>" +
      '<div style="left:108px;top:66px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + esc(q.giver.name + (q.giver.job ? ", " + String(q.giver.job).toLowerCase() : "") + " of " + q.town) + "</div>" +
      '<div style="left:20px;top:108px;width:503px;height:54px;font-size:12px;color:#9A9DA1;line-height:18px;overflow:hidden">' + (q.story ? "“" + esc(q.story) + "”" : esc(q.summary)) + "</div>" +
      '<div class="h-hair" style="left:20px;top:170px;width:503px"></div><div class="h-eb" style="left:20px;top:184px">OBJECTIVES</div>';
    var y = 206, cellsShown = false;
    for (var i = 0; i < q.objectives.length && i < 4 && y < 300; i++) {
      var o = q.objectives[i];
      h += '<div class="h-box' + (o.done ? " done" : "") + '" style="left:20px;top:' + y + 'px">' + (o.done ? '<svg style="left:2px;top:2px" width="9" height="9" viewBox="0 0 9 9"><path d="M1.5 4.6L3.7 6.7L7.6 2.3" stroke="#0B0B0C" stroke-width="1.6" fill="none" stroke-linecap="round"/></svg>' : "") + "</div>" +
        '<div style="left:44px;top:' + (y - 1) + 'px;width:400px;font-size:12.5px;color:' + (o.done ? "#626569" : "#EDEEEF") + ';white-space:nowrap;overflow:hidden">' + esc(o.text) + "</div>" +
        '<div style="right:20px;top:' + y + 'px;font-size:11px;color:#9A9DA1">' + (o.done ? "done" : o.need > 1 ? o.have + " / " + o.need : "") + "</div>";
      y += 28;
      if (!o.done && o.need > 1 && !cellsShown) {
        cellsShown = true;
        h += rowCells(32, Math.round(Math.min(1, o.have / o.need) * 32), 9, 13.5, 44, y - 6, "violet", 2);
        y += 22;
      }
    }
    var rw = q.reward || {};
    h += '<div class="h-hair" style="left:20px;top:314px;width:503px"></div>' +
      '<div class="h-eb" style="left:20px;top:328px">TIME</div><div style="left:20px;top:344px;font-size:12.5px;color:#EDEEEF;white-space:nowrap">' + esc(q.timeLeft ? q.timeLeft + " left" : "No hurry") + "</div>" +
      '<div class="h-eb" style="left:212px;top:328px">WHERE</div><div style="left:212px;top:344px;width:120px;font-size:12.5px;color:#EDEEEF;white-space:nowrap;overflow:hidden">' + esc(q.state === "ready" ? q.giver.name : q.town) + "</div>" +
      '<div style="left:212px;top:364px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + (q.distance >= 0 ? Math.round(q.distance) + " m away" : q.waypoint ? "" : "no mark") + "</div>" +
      '<div class="h-eb" style="left:340px;top:328px">REWARD</div>' + (rw.coins && rw.coins.total > 0 ? readouts(rw.coins, "position:absolute;left:340px;top:344px;gap:14px") : '<div style="left:340px;top:344px;font-size:12px;color:#9A9DA1">' + esc(rw.text || "") + "</div>") +
      (rw.standing ? '<div class="good" style="left:340px;top:388px;font-size:11px;white-space:nowrap">' + sgn(rw.standing) + " standing in " + esc(q.town) + "</div>" : "") +
      '<div class="h-hair" style="left:20px;top:420px;width:503px"></div>' +
      (q.tracked ? '<div class="h-btn off" style="left:20px;top:436px;width:96px">On the HUD</div>' : '<div class="h-btn" data-op="track" data-arg="' + q.id + '" style="left:20px;top:436px;width:74px">Track</div>') +
      '<div class="h-btn danger" data-ui="pop:abandon" style="right:20px;top:436px;width:80px">Abandon</div>';
    if (ui.pop === "abandon") {
      h += '<div class="h-pop" style="right:20px;top:330px;width:260px">Abandon <b>' + esc(q.title) + '</b>? The giver will remember.' +
        '<div class="flow" style="display:flex;gap:8px;margin-top:10px"><div class="h-btn danger" data-op="abandon" data-arg="' + q.id + '" data-close="1" style="width:110px">Abandon</div><div class="h-btn" data-ui="pop:" style="width:110px">Keep it</div></div></div>';
    }
    return h + "</div>";
  }

  function boardPage(w, b) {
    part(w, "hq-body", JSON.stringify(["b", b, ui.scroll]), function () {
      if (!b) return '<div class="h-empty" style="top:280px">No notice board is open. Read one in any town square.</div>';
      var posts = [];
      for (var i = 0; i < b.posts.length; i++) posts.push({k: "post", p: b.posts[i]});
      for (var k = 0; k < b.wanted.length; k++) posts.push({k: "wanted", p: b.wanted[k]});
      var from = Math.min(ui.scroll * 3, Math.max(0, posts.length - 6));
      var h = '<div style="left:28px;top:76px;font-size:15px;color:#EDEEEF;white-space:nowrap">' + esc(b.town) + "</div>" +
        '<div style="left:152px;top:78px;font-size:11px;color:#9A9DA1;white-space:pre">' + esc(upperFirst(b.tier) + "  ·  " + b.counts.open + " posted" + (b.counts.handIns ? "  ·  " + b.counts.handIns + " to hand in" : "")) + "</div>" +
        '<div class="h-pill ink" style="right:28px;top:74px"><div class="h-dot violet"></div><span>' + esc("You are " + b.title + " here  " + sgn(b.reputation)) + "</span></div>";
      for (var n = 0; n < 6 && from + n < posts.length; n++) {
        var x = 28 + (n % 3) * 315, y = 112 + Math.floor(n / 3) * 226;
        var e = posts[from + n];
        if (e.k === "wanted") {
          var wv = e.p;
          h += '<div class="h-post wanted" style="left:' + x + "px;top:" + y + 'px"><div class="h-glow law" style="left:120px;top:-120px;width:260px;height:260px"></div>' +
            '<div class="h-well gw">' + glyph("fight", 32, "#C2645A") + "</div>" +
            '<div class="h-eb bad" style="left:82px;top:18px">' + (wv.outlaw ? "OUTLAW" : "WANTED") + "</div>" +
            '<div class="t">' + esc(wv.name + (wv.crime ? ", for " + String(wv.crime).toLowerCase() : "")) + "</div>" +
            '<div class="s">' + esc("Owes " + wv.owed + ". Bring them to the guard.") + "</div>" +
            '<div class="h-hair" style="left:16px;top:118px;width:269px"></div>' +
            '<div class="bad" style="right:16px;top:128px;font-size:10.5px">bounty</div><div style="right:16px;top:144px;font-size:10.5px;color:#9A9DA1">posted by the guard</div>' +
            '<div style="left:16px;top:130px;font-size:15px;color:#EDEEEF">' + esc(wv.owed) + "</div>" +
            posterButton(wv) + "</div>";
          continue;
        }
        var post = e.p, q = post.quest;
        var hand = b.handIns.indexOf(q.id) >= 0;
        var btn = hand ? '<div class="h-btn primary" data-op="handIn" data-arg="' + q.id + '">Hand it in</div>'
          : post.state === "open" ? '<div class="h-btn" data-op="accept" data-arg="' + q.id + '">Take it</div>'
          : post.state === "taken" ? '<div class="h-btn off">In your journal</div>'
          : '<div class="h-btn off" style="justify-content:flex-start;padding-left:9px">' + glyph("lock", 16, "#626569") + "<span>" + esc(post.lock) + "</span></div>";
        h += '<div class="h-post' + (post.state === "locked" ? " locked" : "") + '" style="left:' + x + "px;top:" + y + 'px">' +
          '<div class="h-well gw">' + giverWell(q) + "</div>" +
          '<div class="h-eb" style="left:82px;top:18px">' + esc(upper(q.type)) + "</div>" +
          '<div class="t">' + esc(q.title) + "</div>" +
          '<div class="s">' + esc(q.giver.name + (q.giver.job ? ", " + String(q.giver.job).toLowerCase() : "") + ": " + q.summary) + "</div>" +
          '<div class="h-hair" style="left:16px;top:118px;width:269px"></div>' +
          (q.reward.coins && q.reward.coins.total > 0 ? readouts(q.reward.coins, "position:absolute;left:16px;top:126px;gap:14px") : "") +
          (q.reward.standing ? '<div class="good" style="right:16px;top:128px;font-size:10.5px">' + sgn(q.reward.standing) + " standing</div>" : "") +
          '<div style="right:16px;top:144px;font-size:10.5px;color:#9A9DA1">' + esc(q.timeLeft || "") + "</div>" + btn.replace('class="h-btn', 'style="left:16px;top:168px;width:269px" class="h-btn') + "</div>";
      }
      if (!posts.length) h += '<div class="h-empty" style="top:300px">Nothing posted today.</div>';
      return h;
    });
    part(w, "hq-foot", JSON.stringify(["b", !!b]), function () {
      return foot(b ? "CLICK TAKE IT TO PUT A POST IN YOUR JOURNAL AND ON THE HUD" : "A BOARD OPENS HERE WHEN YOU READ ONE", "board");
    });
  }

  /**
   * A wanted poster's button (Figma 2_notice_board "Track him"): tracks its quarry (trackPoster; one thing tracked at a
   * time, followed on Atlas); tracked, it lets go (untrackPoster). An older server sends no quarry: read only.
   */
  function posterButton(wv) {
    var who = first(wv.name);
    if (!wv.player) return '<div class="h-btn off" style="left:16px;top:168px;width:269px">Read only</div>';
    if (wv.tracked) return '<div class="h-btn primary" data-op="untrackPoster" style="left:16px;top:168px;width:269px">Tracking ' + esc(who) + "  ·  let go</div>";
    return '<div class="h-btn" data-op="trackPoster" data-arg="' + esc(wv.player) + '" style="left:16px;top:168px;width:269px">Track ' + esc(who) + "</div>";
  }

  /** The tracked wanted poster in the journal's TRACKED list (state.wantedTrack), followed like a quest. */
  function wantedRow(t, y) {
    var where = !t.online ? "not on the roads now" : t.distance >= 0 ? Math.round(t.distance) + " m" + (t.bearing != null ? "  ·  " + compass(t.bearing) : "") : "somewhere else";
    return '<div class="h-q wanted" style="top:' + y + 'px">' +
      '<div class="h-well gw">' + glyph("fight", 32, "#C2645A") + "</div>" +
      '<div class="h-eb e bad">' + esc((t.outlaw ? "OUTLAW" : "WANTED") + "  ·  " + upper(t.town)) + "</div>" +
      '<div class="t">' + esc(t.name + (t.crime ? ", for " + String(t.crime).toLowerCase() : "")) + "</div>" +
      '<div class="p">' + esc("Owes " + coinText(t.owed) + "  ·  " + where) + "</div>" +
      '<div class="h-pill ink" data-op="untrackPoster"><div class="h-dot bad"></div><span>Tracked  ·  let go</span></div></div>';
  }

  /** A bearing (degrees from where the player looks, clockwise) as words. */
  function compass(b) {
    var a = Math.abs(b);
    return a <= 20 ? "ahead" : a >= 160 ? "behind you" : (b > 0 ? "to your right" : "to your left");
  }

  var LADDER = [["outlaw", "Outlaw"], ["distrusted", "Distrusted"], ["stranger", "Stranger"], ["known", "Known"], ["friend", "Friend"], ["burgher", "Burgher"], ["hero", "Hero"]];

  function reputationPage(w, r) {
    if (!r && !ui.asked) { ui.asked = true; act("requestReputation"); }
    part(w, "hq-body", JSON.stringify(["r", r, ui.town]), function () {
      if (!r) return '<div class="h-empty" style="top:280px">Asking around…</div>';
      var towns = r.towns;
      if (ui.town < 0 || ui.town >= towns.length) { ui.town = 0; for (var i = 0; i < towns.length; i++) if (towns[i].here) ui.town = i; }
      var h = '<div class="h-panel" style="left:27px;top:75px;width:317px;height:482px"><div class="h-eb" style="left:15px;top:15px">STANDING  ·  PER TOWN</div><div class="h-eb" style="right:15px;top:15px">−100 · 0 · +100</div>';
      for (var t = 0; t < towns.length && t < 6; t++) {
        var tw = towns[t], y = 31 + t * 72;
        h += '<div class="h-town' + (t === ui.town ? " sel" : "") + '" data-town="' + t + '" style="top:' + y + 'px"></div>' +
          '<div style="left:15px;top:' + (y + 8) + 'px;font-size:13px;color:#EDEEEF;white-space:nowrap">' + esc(tw.name) + "</div>" +
          '<div style="right:15px;top:' + (y + 9) + 'px;font-size:12px;color:#9A9DA1">' + sgn(tw.value) + "</div>" +
          standingCells(tw.value, 15, y + 30, 14, 5, tw.title === "burgher" || tw.title === "hero" ? "brass" : null) +
          '<div class="h-eb2" style="left:15px;top:' + (y + 47) + 'px">' + esc(upper(tw.titleName + " in " + tw.name) + (tw.owns ? "  ·  OWNS PROPERTY" : "") + (tw.distance >= 0 ? "  ·  " + Math.round(tw.distance) + " M" : "")) + "</div>";
      }
      if (!towns.length) h += '<div class="h-empty" style="top:200px">No town knows you yet.</div>';
      h += "</div>";
      var s = towns[ui.town];
      h += '<div class="h-panel" style="left:360px;top:75px;width:598px;height:482px">';
      if (!s) {
        h += '<div class="h-eb" style="left:19px;top:17px">YOUR NAME ON THE ROADS</div>' +
          '<div style="left:19px;top:33px;font-size:22px;color:#EDEEEF;white-space:nowrap">A stranger everywhere</div>' +
          '<div style="left:19px;top:71px;width:558px;font-size:11.5px;color:#626569;line-height:16px">Towns learn your name as you trade, work and keep (or break) their law. Each one remembers you on its own.</div>' +
          '<div class="h-hair" style="left:19px;top:127px;width:558px"></div>';
      }
      if (s) {
        var at = 0;
        for (var l = 0; l < LADDER.length; l++) if (LADDER[l][0] === s.title) at = l;
        var ladder = "";
        for (var k = 0; k < LADDER.length; k++) ladder += '<div class="h-pill c' + (k === at ? " on " + (s.title === "burgher" || s.title === "hero" ? "brass" : "") : k > 1 && k < at ? " past" : "") + '">' + LADDER[k][1] + "</div>";
        h += '<div class="h-eb" style="left:19px;top:17px">' + esc(upper(s.name) + "  ·  " + upper(s.tier) + "  ·  YOUR STANDING") + "</div>" +
          '<div style="left:19px;top:33px;font-size:22px;color:#EDEEEF;white-space:nowrap">' + esc(s.titleName + " of " + s.name) + "</div>" +
          '<div class="' + titleTone(s.title) + '" style="right:19px;top:33px;font-size:22px">' + sgn(s.value) + "</div>" +
          '<div class="h-ladder flow" style="left:19px;top:71px">' + ladder + "</div>" +
          '<div style="left:19px;top:103px;font-size:11px;color:#626569;white-space:nowrap">' + esc(s.culture ? upperFirst(s.culture) + " folk" : "") + (s.owns ? "  ·  you own property here" : "") + "</div>" +
          '<div class="h-hair" style="left:19px;top:127px;width:558px"></div>';
      }
      {
        h += '<div class="h-eb" style="left:19px;top:141px">WHAT FOLK NOTICE</div><div class="h-pills flow" style="left:19px;top:159px">';
        for (var n = 0; n < r.looks.length && n < 8; n++) h += '<div class="h-pill ink"><div class="h-dot ink"></div><span>' + esc(r.looks[n]) + "</span></div>";
        if (!r.looks.length) h += '<div class="h-pill">Nothing about you stands out</div>';
        var fameCells = "";
        var tier = Math.max(0, Math.min(5, Math.round(r.fame / 20)));
        for (var f = 0; f < 5; f++) fameCells += '<i class="' + (f < tier ? (f === tier - 1 ? "brass" : "on") : "") + '" style="left:' + f * 26 + 'px;width:22px;height:22px;border-radius:4px"></i>';
        h += "</div>" +
          '<div class="h-eb" style="left:19px;top:229px">FAME</div><div style="left:19px;top:245px;font-size:12.5px;color:#EDEEEF;white-space:nowrap">' + esc(r.fameWord + (r.renownWord ? "  ·  " + r.renownWord : "")) + "</div>" +
          '<div class="h-cel" style="left:19px;top:267px;width:126px;height:22px">' + fameCells + "</div>" +
          '<div class="h-eb" style="left:319px;top:229px">HOW YOU COME ACROSS</div>' +
          '<div style="left:319px;top:245px;font-size:12px;color:#EDEEEF;white-space:nowrap">' + esc("Charisma  ·  " + r.charismaWord) + "</div>" +
          '<div style="left:319px;top:265px;font-size:12px;color:#EDEEEF;white-space:nowrap">' + esc("Menace  ·  " + r.menaceWord) + "</div>" +
          '<div class="h-hair" style="left:19px;top:355px;width:558px"></div>' +
          '<div class="h-eb" style="left:19px;top:369px">YOUR COMPANY</div>' +
          '<div style="left:19px;top:387px;width:558px;font-size:12px;color:' + (r.company ? "#EDEEEF" : "#626569") + '">' + esc(r.company ? r.company + (r.members.length ? "  ·  " + r.members.join(", ") : "") : "You travel alone. Standing is per player; a company shares a part.") + "</div>";
      }
      return h + "</div>";
    });
    part(w, "hq-foot", JSON.stringify(["r", r ? r.towns.length : -1]), function () {
      var owns = 0;
      if (r) for (var i = 0; i < r.towns.length; i++) if (r.towns[i].owns) owns++;
      return foot((r ? r.towns.length : 0) + " TOWNS  ·  " + owns + " WITH PROPERTY  ·  STANDING IS PER PLAYER; A COMPANY SHARES A PART", "standing");
    });
  }

  function finishedPage(w, j) {
    part(w, "hq-body", JSON.stringify(["f", j.finished]), function () {
      var f = j.finished;
      var h = '<div class="h-panel" style="left:27px;top:75px;width:931px;height:482px"><div class="h-eb" style="left:19px;top:17px">' + f.total + " FINISHED  ·  " + f.completed + " ENDED WELL  ·  " + f.failed + " FAILED</div>";
      for (var i = 0; i < f.recent.length && i < 11; i++) {
        var e = f.recent[i], y = 45 + i * 38, ok = e.outcome === "completed";
        h += '<div style="left:19px;top:' + y + 'px;font-size:13px;color:' + (ok ? "#EDEEEF" : "#9A9DA1") + ';white-space:pre">' + (ok ? "✓  " : "✕  ") + esc(e.title) + "</div>" +
          '<div style="left:360px;top:' + (y + 1) + 'px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + esc(upperFirst(e.type) + "  ·  " + e.town + "  ·  " + e.giver) + "</div>" +
          '<div style="left:640px;top:' + (y + 1) + 'px;font-size:11px;color:#626569">Day ' + (e.day + 1) + "</div>" +
          (e.reward && e.reward.total > 0 ? price(e.reward, "", "position:absolute;right:19px;top:" + y + "px") : "");
      }
      if (!f.recent.length) h += '<div class="h-empty" style="top:220px">Nothing finished yet.</div>';
      return h + "</div>";
    });
    part(w, "hq-foot", "f", function () { return foot("WHAT YOU FINISHED, NEWEST FIRST", "journal"); });
  }

  // ---- render ---------------------------------------------------------------------------------------------------------------------------
  function active() { return kind === "hearth" || kind === "quests"; }

  function render() {
    if (!active()) return;
    var name = kind === "quests" ? "quests" : screen;
    var style = H && H.settings ? H.settings.conversationStyle : "card";
    var frame = name + "|" + (name === "talk" ? style : "") + "|" + (lay ? lay.vw + "x" + lay.vh : "");
    if (frame !== drawn) {
      drawn = frame;
      clear();
    }
    var cine = name === "talk" && style === "cinematic";
    toggle(document.body, "cinematic", cine && kind === "hearth");
    if (lay) {
      root.style.left = (cine ? 0 : lay.win.x) + "px";
      root.style.top = (cine ? 0 : lay.win.y) + "px";
    }
    if (!H || H.phase !== "online") {
      part(win(), "h-wait", "w", chip() + '<div class="h-empty" style="top:290px">Hearth is not here yet.</div>');
      return;
    }
    var wait = document.getElementById("h-wait");
    if (wait) wait.parentNode.removeChild(wait);
    if (name === "quests") quests();
    else if (name === "talk" && H.talk) { if (cine) talkB(H.talk); else talkA(H.talk); }
    else if (name === "confront" && H.confront) confront(H.confront);
    else if (name === "market" && H.market) market(H.market);
    else if (name === "bank" && H.bank) bank(H.bank);
    else if (name === "dice" && H.dice) dicePage(H.dice);
    else if (name === "lockpick" && H.lockpick) lockpick(H.lockpick);
    else if (name === "pickpocket" && H.pickpocket) pickpocket(H.pickpocket);
    reportFx(false);
    sendUi(false);
  }

  // ---- what Java needs: native rects and item wells, and who owns the keyboard ------------------------------------------------------
  var fxSent = "";
  var ITEM = /^[a-z0-9_.-]{1,64}:[a-z0-9_.\/-]{1,128}$/;
  function rect(el) { var r = el.getBoundingClientRect(); return {x: r.left, y: r.top, w: r.width, h: r.height}; }

  function reportFx(force) {
    var items = [], stacks = [];
    if (kind === "hearth") {
      var nfx = root.querySelectorAll("[data-nfx]");
      for (var i = 0; i < nfx.length && items.length < 32; i++) {
        var r = rect(nfx[i]);
        var it = {i: "h-" + nfx[i].getAttribute("data-nfx") + "-" + i, k: nfx[i].getAttribute("data-nfx"), x: r.x, y: r.y, w: r.w, h: r.h};
        if (nfx[i].getAttribute("data-p")) it.p = +nfx[i].getAttribute("data-p");
        if (nfx[i].getAttribute("data-q")) it.q = +nfx[i].getAttribute("data-q");
        items.push(it);
      }
      var wells = root.querySelectorAll("[data-item]");
      for (var k = 0; k < wells.length && stacks.length < 32; k++) {
        var id = wells[k].getAttribute("data-item");
        if (!ITEM.test(id)) continue;
        var s = rect(wells[k]);
        stacks.push({id: id, n: Math.max(1, +wells[k].getAttribute("data-n") || 1), x: s.x, y: s.y, w: s.w, h: s.h});
      }
    }
    var key = JSON.stringify([items, stacks]);
    if (key === fxSent && !force) return;
    fxSent = key;
    send({type: "hearthFx", items: items, stacks: stacks});
  }

  var uiSent = "";
  function modal() {
    if (ui.pop) return true;
    return kind === "hearth" && screen === "market" && !!(H && H.market && H.market.haggle);
  }
  function sendUi(force) {
    var m = modal();
    var k = m ? "m" : "";
    if (k === uiSent && !force) return;
    uiSent = k;
    send({type: "hearthUi", modal: m, text: false});
  }

  // ---- input ---------------------------------------------------------------------------------------------------------------------------
  function attr(t, name) {
    while (t && t !== document.body && t !== root.parentNode) {
      if (t.getAttribute && t.getAttribute(name) != null) return t;
      t = t.parentNode;
    }
    return null;
  }

  function options() {
    if (screen === "talk" && H && H.talk) return talkOptions(H.talk);
    if (screen === "confront" && H && H.confront) return H.confront.choices;
    return [];
  }

  function pickOption(i) {
    var list = options();
    var o = list[i];
    if (!o) return;
    if (screen === "talk") { if (!o.locked) act("choose", o.id); }
    else if (o.enabled) act("confront", o.id);
  }

  function onClick(ev) {
    if (!active()) return;
    var t = ev.target, el;
    if ((el = attr(t, "data-op"))) {
      act(el.getAttribute("data-op"), el.getAttribute("data-arg") || "");
      if (el.getAttribute("data-close")) ui.pop = "";
      if (el.getAttribute("data-op") === "haggleOffer" || el.getAttribute("data-op") === "haggleAccept") ui.offer = null;
      render();
      return;
    }
    if ((el = attr(t, "data-opt"))) { pickOption(+el.getAttribute("data-opt")); return; }
    if ((el = attr(t, "data-choice"))) { pickOption(+el.getAttribute("data-choice")); return; }
    if ((el = attr(t, "data-row"))) {
      act("trade", el.getAttribute("data-row") + "#" + (ui.shift ? 1 : 0));
      return;
    }
    if ((el = attr(t, "data-pslot"))) {
      var slot = +el.getAttribute("data-pslot");
      var p = H && H.pickpocket;
      ui.pslot = slot;
      if (p && p.state === "open") {
        if (p.lifting === slot) act("stopLifting");
        else if (p.lifting < 0) act("lift", slot);
      }
      render();
      return;
    }
    if ((el = attr(t, "data-quest"))) { ui.quest = el.getAttribute("data-quest"); ui.pop = ""; render(); return; }
    if ((el = attr(t, "data-town"))) { ui.town = +el.getAttribute("data-town"); render(); return; }
    if ((el = attr(t, "data-plot"))) { ui.plot = +el.getAttribute("data-plot"); render(); return; }
    if ((el = attr(t, "data-ui"))) { uiAction(el.getAttribute("data-ui")); return; }
    if (ui.pop && !attr(t, "class")) { ui.pop = ""; render(); }
  }

  function uiAction(a) {
    var c = a.indexOf(":"), k = c < 0 ? a : a.slice(0, c), v = c < 0 ? "" : a.slice(c + 1);
    if (k === "close") { send({type: "close"}); return; }
    if (k === "seg") { seg = v; ui.scroll = 0; ui.pop = ""; if (v === "reputation") ui.asked = false; }
    else if (k === "pop") ui.pop = ui.pop === v ? "" : v;
    else if (k === "mtab") { ui.marketTab = v; ui.scroll = 0; }
    else if (k === "btab") ui.bankTab = v;
    else if (k === "amt") {
      var b = H && H.bank;
      if (v === "all") ui.amount = -1;
      else if (v === "clear") ui.amount = 0;
      else ui.amount = Math.max(0, ui.amount) + (+v);
      if (b && ui.amount > Math.max(b.deposit.total, b.carried.total)) ui.amount = Math.max(b.deposit.total, b.carried.total);
    } else if (k === "deposit" || k === "withdraw") {
      if (ui.amount !== 0) act(k, ui.amount);
      ui.amount = 0;
    } else if (k === "nudge") {
      var g = H && H.market && H.market.haggle;
      if (!g) return;
      var base = ui.offer != null && ui.offerRow === g.row ? ui.offer : g.offer.total > 0 ? g.offer.total : g.word.total;
      var d = v === "-10%" ? -Math.max(1, Math.round(base * 0.1)) : v === "+10%" ? Math.max(1, Math.round(base * 0.1)) : +v;
      ui.offer = Math.max(1, base + d);
      ui.offerRow = g.row;
    }
    render();
  }

  function onDown(ev) {
    if (!active() || ev.button !== 2) return;
    var el = attr(ev.target, "data-row");
    if (el && screen === "market") act("haggle", el.getAttribute("data-row") + "#" + (ui.shift ? 1 : 0));
  }

  function key(k) {
    if (k === "Escape") {
      if (ui.pop) { ui.pop = ""; render(); return; }
      if (screen === "market" && H && H.market && H.market.haggle) { act("haggleCancel"); ui.offer = null; return; }
      return;
    }
    var list = options();
    if (list.length) {
      if (/^[0-9]$/.test(k)) {
        for (var i = 0; i < list.length; i++) if (String(list[i].key) === k) { pickOption(i); return; }
        return;
      }
      if (k === "Up" || k === "Down") {
        var n = Math.min(list.length, 10);
        ui.focus = ui.focus < 0 ? (k === "Down" ? 0 : n - 1) : (ui.focus + (k === "Down" ? 1 : n - 1)) % n;
        render();
        return;
      }
      if (k === "Enter" && ui.focus >= 0) { pickOption(ui.focus); return; }
    }
    if (screen === "dice" && H && H.dice) {
      if (k === "Space" && H.dice.canRoll) act("diceRoll");
      else if (k === "b" && H.dice.canBank) act("diceBank");
    }
  }

  function wheel(m) {
    var dy = m.dy > 0 ? -1 : 1;
    if (screen === "market" && H && H.market) ui.scroll = Math.max(0, Math.min(Math.max(0, marketRows(H.market).length - 8), ui.scroll + dy));
    else if (kind === "quests" && seg === "board" && H && H.board) ui.scroll = Math.max(0, Math.min(Math.ceil((H.board.posts.length + H.board.wanted.length) / 3) - 2, ui.scroll + dy));
    else return;
    render();
  }

  document.addEventListener("click", onClick, true);
  document.addEventListener("mousedown", onDown, true);

  // ---- hooks ----------------------------------------------------------------------------------------------------------------------------
  window.hubHearth = {
    layout: function (l) {
      kind = l.kind;
      lay = l;
      toggle(document.body, "hearth", kind === "hearth");
      toggle(document.body, "quests", kind === "quests");
      if (kind !== "hearth") toggle(document.body, "cinematic", false);
      var cap = document.querySelector("#close .keycap");
      if (cap) cap.textContent = kind === "hearth" ? "Esc" : "R-Shift";
      if (!active()) { fxSent = ""; return; }
      root.style.left = l.win.x + "px";
      root.style.top = l.win.y + "px";
      drawn = "";
      render();
      reportFx(true);
    },
    state: function (s) { /* the hub's own state: nothing of Hearth's in it */ },
    apply: function (m) {
      if (m.type === "hearth") {
        H = m.state;
        render();
      } else if (m.type === "hearthScreen") {
        if (m.screen !== screen) { ui.focus = -1; ui.pop = ""; ui.offer = null; ui.scroll = 0; ui.pslot = -1; ui.bankTab = ""; ui.plot = -1; ui.marketTab = "sells"; }
        screen = m.screen;
        if (m.seg) { if (m.seg !== seg) ui.scroll = 0; seg = m.seg; ui.asked = false; ui.pop = ""; }
        drawn = "";
        render();
        reportFx(true);
        sendUi(true);
      } else if (m.type === "icons") {
        // Hearth's own icons (namespaced "hearth:" by StateSync), kept apart from Mettle's; a reset drops only these.
        if (m.mod !== "hearth") return;
        if (m.reset) icons = {};
        for (var k in m.map || {}) icons[k.slice(7)] = m.map[k];
        if (H) render();
      } else if (m.type === "hkey") {
        lastKey = m.key + (active() ? "" : " (inactive)");
        if (active()) key(m.key);
      } else if (m.type === "hwheel") {
        if (active()) wheel(m);
      } else if (m.type === "hmods") {
        ui.shift = !!m.shift;
      } else if (m.type === "visible") {
        // No timers run here; a screen that opens hears the keyboard claims and native rects again.
        if (m.on && active()) { sendUi(true); reportFx(true); }
      }
    },
    // Tests: what the page holds.
    debug: function () { return JSON.stringify({kind: kind, screen: screen, seg: seg, has: !!H, modal: modal(), fx: fxSent.length, key: lastKey, icons: Object.keys(icons).length}); }
  };
})();
