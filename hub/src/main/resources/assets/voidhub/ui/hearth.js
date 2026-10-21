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
// OVERLAY.md 2026-10-21 (hearth-lwui, the living-world screens): the Quests page's People (F4 A) and Household (F2 B)
// segments, the gear screen (F3), a stranger's conversation (F1, talk.stranger) and the reputation page's burgher and
// retinue cards (F5). The contest and arrival lines (F6, F7) are Hearth's own HUDs.
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
    bankTab: "", marketTab: "sells", asked: false, member: null, person: null, lord: 0, hand: -1, askedHouse: false};
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
    house: ["...#...", "..###..", ".#####.", "#######", ".#.#.#.", ".#####.", ".##.##."],
    tower: ["#.#.#.#", "#######", ".#####.", ".##.##.", ".#####.", ".#####.", "#######"],
    shield: ["#######", "#######", "##.#.##", ".#####.", ".#####.", "..###..", "...#..."]
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

  /** Every option, goodbye always last (key 0). Past the ninth an option has no number key (key -1): it is clicked or picked
   *  with the arrows, and the list scrolls when there are more than the view's rows (talkWindow). */
  function talkOptions(t) {
    var out = [], leave = null;
    for (var i = 0; i < t.options.length; i++) {
      var o = t.options[i];
      if (o.kind === "leave" || o.key === 0) { if (!leave) leave = o; continue; }
      out.push(o);
    }
    if (leave) out.push(leave);
    return out;
  }

  /** The first option row the talk view shows: ui.scroll clamped to the list, moved so the focused option is in view.
   *  Remembers the view's rows and wheel step for wheel(). */
  function talkWindow(len, rows, step) {
    var top = Math.max(0, Math.min(ui.scroll, len - rows));
    if (ui.focus >= 0 && ui.focus < len) {
      if (ui.focus < top) top = ui.focus;
      else if (ui.focus >= top + rows) top = ui.focus - rows + 1;
    }
    ui.scroll = top;
    ui.talkRows = rows;
    ui.talkStep = step || 1;
    return top;
  }

  /** A still scrollbar beside an option list that does not fit (no thumb when it does). */
  function talkBar(len, rows, top, left, y, height) {
    if (len <= rows) return "";
    var th = Math.max(16, Math.round(height * rows / len)), ty = Math.round((height - th) * top / Math.max(1, len - rows));
    return '<div class="h-optbar" style="left:' + left + "px;top:" + y + "px;height:" + height + 'px"><i style="top:' + ty + "px;height:" + th + 'px"></i></div>';
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
    if (t.stranger) { talkStranger(t); return; }
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
    var opts = talkOptions(t), top = talkWindow(opts.length, 10);
    part(w, "ht-opts", JSON.stringify([opts, ui.focus, top]), function () {
      var h = '<div class="h-opts flow">';
      for (var i = top; i < opts.length && i < top + 10; i++) h += optionRow(opts[i], i, 598);
      return h + "</div>" + talkBar(opts.length, 10, top, 964, 189, 378);
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
    var opts = talkOptions(t), top = talkWindow(opts.length, 10, 5);
    part(root, "hb-bar", JSON.stringify([r, t.standing, opts, ui.focus, top, bw, bx, by]), function () {
      var cw = Math.floor((bw - 335 - 19 - 6) / 2);
      var st = t.standing || {};
      var traits = [];
      for (var i = 0; i < r.traits.length && i < 2; i++) traits.push(r.traits[i].name);
      var c1 = "", c2 = "";
      for (var k = top; k < opts.length && k < top + 10; k++) {
        if (k < top + 5) c1 += optionRow(opts[k], k, cw); else c2 += optionRow(opts[k], k, cw);
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
        '<div class="cols"><div class="col">' + c1 + '</div><div class="col">' + c2 + "</div></div>" + talkBar(opts.length, 10, top, bw - 15, 19, 196) +
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
    var counts = [["journal", "Journal", j.active.length], ["board", "Notice board", board ? board.counts.open : ""], ["reputation", "Reputation", ""], ["finished", "Finished", j.finished.total],
      ["people", "People", H.people ? H.people.met : ""], ["household", "Household", ""]];
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
    else if (seg === "people") peoplePage(w, H.people);
    else if (seg === "household") householdPage(w, H.household);
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
    if (r && ((r.retinue && r.retinue.length) || anyBurgher(r))) { reputationLw(w, r); return; }
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

  // ---- the living-world screens (OVERLAY.md "Living-world screens", 2026-10-21; Figma section "Hearth - Living world
  // screens (explore)" on 295:2): F1 the stranger (on the conversation page), F2 Household B (708/710:45834), F3 companion
  // gear (711:47250, its own event screen), F4 People A (713:48066), F5 retinue and burgher (712:47881, on Reputation).
  // F6 and F7 are Hearth's own HUD lines. -------------------------------------------------------------------------------

  var ORDERS = [["follow", "Follow me.", "run"], ["wait", "Wait here.", "hand"], ["distance", "Keep your distance.", "eye"],
    ["fight", "Fight what I fight.", "fight"], ["hold", "Hold back.", "shield"], ["home", "Go home.", "house"]];
  var GEAR_SLOTS = [["head", "HEAD"], ["chest", "CHEST"], ["legs", "LEGS"], ["feet", "FEET"], ["mainhand", "MAIN HAND"], ["offhand", "OFF HAND"]];
  var SLOT_WORD = {head: "head", chest: "chest", legs: "legs", feet: "feet", mainhand: "main hand", offhand: "off hand"};

  function lower(s) { return String(s || "").toLowerCase(); }
  function stepTone(s) { return s >= 3 ? "good" : s === 2 ? "ink2" : s === 1 ? "wary" : "bad"; }
  function daysAgo(n) { return n <= 0 ? "TODAY" : n === 1 ? "A DAY AGO" : n + " DAYS AGO"; }
  function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }

  /** A small inline price (Figma's coin pills: 11 px counts, 7 px coin squares). */
  function smallPrice(c, style) { return price(c, "sm", style); }

  /** A pill with a dot and words (the designs' status pills). */
  function dotPill(tone, text, style, attrs) {
    return '<div class="h-pill ink"' + (attrs || "") + ' style="' + (style || "") + '">' + (tone ? '<div class="h-dot ' + tone + '"></div>' : "") + "<span>" + esc(text) + "</span></div>";
  }

  /** n cells of w x h at a pitch, the first `lit` in `tone` (Figma's loyalty and opinion bars). */
  function barCells(n, lit, w, hgt, pitch, left, top, tone) {
    var h = '<div class="h-cel" style="left:' + left + "px;top:" + top + "px;height:" + hgt + "px;width:" + ((n - 1) * pitch + w) + 'px">';
    for (var i = 0; i < n; i++) h += '<i class="' + (i < lit ? tone || "on" : "") + '" style="left:' + i * pitch + "px;width:" + w + "px;height:" + hgt + 'px;border-radius:2.5px"></i>';
    return h + "</div>";
  }

  /** A −100…100 opinion over 21 cells from the centre one (People A's bar, 713:49156). */
  function opinionBar(v, left, top) {
    var n = Math.min(10, Math.round(Math.abs(v) / 10));
    var h = '<div class="h-cel" style="left:' + left + "px;top:" + top + 'px;height:9px;width:395px">';
    for (var i = 0; i < 21; i++) {
      var d = i - 10;
      var lit = d === 0 || (v > 0 ? d > 0 && d <= n : v < 0 && d < 0 && -d <= n);
      h += '<i class="' + (lit ? (d === 0 ? "on" : v > 0 ? "good" : "bad") : "") + '" style="left:' + i * 19 + 'px;width:15px;height:9px;border-radius:2.5px"></i>';
    }
    return h + "</div>";
  }

  // ---- F2: Household B, the roster (710:45834) ------------------------------------------------------------------------
  /** Everyone in the player's service, in the roster's order: companions, then each house's servants. */
  function members(h) {
    var out = [];
    var cs = h.companions || [];
    for (var i = 0; i < cs.length; i++) out.push({kind: "companion", id: cs[i].id, m: cs[i]});
    var hs = h.houses || [];
    for (var k = 0; k < hs.length; k++) {
      var sv = hs[k].servants || [];
      for (var s = 0; s < sv.length; s++) out.push({kind: "servant", id: sv[s].id || "s" + k + "-" + s, m: sv[s], house: hs[k]});
    }
    return out;
  }

  function companionById(h, id) {
    var cs = h && h.companions || [];
    for (var i = 0; i < cs.length; i++) if (cs[i].id === id) return cs[i];
    return null;
  }

  function slotOf(c, name) {
    var sl = c.slots || [];
    for (var i = 0; i < sl.length; i++) if (sl[i].slot === name) return sl[i];
    return null;
  }

  function memberDot(m) {
    var limit = m.unpaidLimit || 3;
    if (m.unpaidDays >= limit - 1 && m.unpaidDays > 0) return "bad";
    if (m.unpaidDays > 0 || m.loyaltyStep != null && m.loyaltyStep <= 1) return "wary";
    return "good";
  }

  function memberLine(e) {
    var m = e.m;
    if (m.unpaidDays > 0) return (e.kind === "servant" ? "Servant" : m.job) + "  ·  " + plural(m.unpaidDays, "day", "days") + " unpaid";
    if (e.kind === "servant") return "Servant  ·  " + (m.dutyText || lower(m.job));
    return m.job + "  ·  " + (m.ordersText || m.orders || "") + (m.orders === "wait" && m.where ? " in " + m.where : "");
  }

  function rosterRow(e, y) {
    var m = e.m;
    return '<div class="h-mem' + (e.id === ui.member ? " sel" : "") + '" data-member="' + esc(e.id) + '" style="top:' + y + 'px">' +
      '<div class="h-well fw">' + (m.portrait ? face(m, 32, "left:3px;top:3px") : '<span style="left:8px;top:8px">' + glyph("talk", 24, "#626569") + "</span>") + "</div>" +
      '<div class="nm">' + esc(m.name) + "</div>" +
      '<div class="h-dot ' + memberDot(m) + '" style="left:58px;top:31px"></div>' +
      '<div class="st">' + esc(memberLine(e)) + "</div></div>";
  }

  function roster(h, list) {
    var cs = h.companions || [];
    var out = '<div class="h-panel h-roster"><div class="h-eb" style="left:16px;top:16px">IN YOUR SERVICE</div>' +
      '<div class="h-pill" style="right:16px;top:11px;height:21px">' + smallPrice(h.dailyWages) + "<span>a day</span></div>" +
      '<div class="h-eb" style="left:16px;top:40px">' + esc("WITH YOU  ·  " + cs.length + " OF " + (h.companionsMax || cs.length)) + "</div>";
    var y = 52, i = 0;
    for (; i < list.length && list[i].kind === "companion"; i++, y += 52) out += rosterRow(list[i], y);
    if (!cs.length) { out += '<div style="left:16px;top:56px;width:285px;font-size:11px;color:#626569;line-height:15px">Nobody travels with you. Folk for hire say so when you talk to them.</div>'; y = 92; }
    var hs = h.houses || [];
    for (var k = 0; k < hs.length && y < 440; k++) {
      var hs0 = hs[k];
      y += 14;
      out += '<div class="h-eb" style="left:16px;top:' + y + 'px">' + esc(upper("the " + hs0.building + "  ·  " + hs0.town + "  ·  " + hs0.larder + "/" + hs0.larderSize)) + "</div>";
      y += 18;
      for (var s = 0; s < (hs0.servants || []).length && y < 440; s++, i++, y += 52) out += rosterRow(list[i], y);
      if (!(hs0.servants || []).length) { out += '<div style="left:16px;top:' + (y + 2) + 'px;font-size:11px;color:#626569">No one keeps it yet.</div>'; y += 22; }
    }
    return out + "</div>";
  }

  function wageBlock(m, home) {
    var left = Math.max(0, (m.unpaidLimit || 3) - (m.unpaidDays || 0));
    var paid = "Paid " + m.paidDays + " of " + m.servedDays + (m.servedDays === 1 ? " day" : " days");
    return '<div class="h-hair" style="left:20px;top:400px;width:558px"></div><div class="h-eb" style="left:20px;top:414px">WAGE</div>' +
      '<div class="flow h-wage" style="left:20px;top:430px">' + price(m.wageCoins, "", "") + "<span>" + esc("a day" + (home ? ", at " + home + "’s dawn" : "")) + "</span></div>" +
      '<div class="' + (m.unpaidDays > 0 ? "wary" : "") + '" style="left:20px;top:452px;font-size:11px;color:' + (m.unpaidDays > 0 ? "" : "#626569") + ';white-space:pre">' +
      esc((m.unpaidDays > 0 ? plural(m.unpaidDays, "day", "days") + " unpaid" : paid) + "  ·  " + (left <= 1 ? "one more unpaid and they go home" : left + " unpaid and they go home")) + "</div>";
  }

  function orderRow(c, o, n) {
    var verb = o[0];
    var now = verb === "fight" ? c.fight : verb === "hold" ? !c.fight : verb === c.orders;
    var far = (verb === "wait") && !c.near;
    var chip = now ? dotPill("good", "now") : verb === "home" ? dotPill("bad", "to " + (c.home || "home")) : far ? dotPill("wary", "come closer") : "";
    return '<div class="h-opt flow' + (far ? " locked" : "") + '"' + (far ? "" : ' data-op="companionOrder" data-arg="' + esc(c.id) + "#" + verb + '"') + ' style="width:282px">' +
      '<div class="k">' + n + "</div>" + glyph(o[2], 16) + '<div class="l">' + esc(o[1]) + "</div>" + chip + "</div>";
  }

  /** What "Take this" hands over first: the first thing in the player's hands that goes somewhere on them. */
  function firstGift(h) {
    var hands = h.hands || [];
    for (var i = 0; i < hands.length; i++) if (!hands[i].empty && hands[i].gives) return hands[i];
    return null;
  }

  function companionCard(h, c) {
    var traits = [];
    for (var i = 0; i < (c.traitNames || []).length && i < 3; i++) traits.push(lower(c.traitNames[i].name));
    var lit = Math.max(0, Math.min(20, Math.round(((c.loyalty || 0) + 100) / 10)));
    var out = '<div class="h-panel h-hh">' +
      '<div class="h-well" style="left:20px;top:20px;width:120px;height:120px;border-radius:16px">' + (c.portrait ? face(c, 96, "left:11px;top:11px") : '<span style="left:36px;top:36px">' + glyph("talk", 48, "#626569") + "</span>") + "</div>" +
      '<div class="h-eb" style="left:156px;top:22px">' + esc("COMPANION  ·  HIRED IN " + upper(c.home) + " " + daysAgo((c.servedDays || 1) - 1)) + "</div>" +
      '<div style="left:156px;top:38px;font-size:22px;color:#EDEEEF;white-space:nowrap">' + esc(c.name) + "</div>" +
      '<div style="left:156px;top:68px;width:422px;font-size:11.5px;color:#9A9DA1;white-space:pre;overflow:hidden">' + esc(c.job + (c.home ? " of " + c.home : "") + (traits.length ? "  ·  " + traits.join(", ") : "")) + "</div>" +
      '<div class="h-eb" style="left:156px;top:96px">LOYALTY</div>' +
      '<div class="' + stepTone(c.loyaltyStep) + '" style="right:20px;top:94px;font-size:11.5px;white-space:nowrap">' + esc(c.loyaltyWord || "") + "</div>" +
      barCells(20, lit, 17, 9, 21, 156, 112, stepTone(c.loyaltyStep) === "ink2" ? "on" : stepTone(c.loyaltyStep)) +
      '<div style="left:156px;top:128px;width:422px;font-size:11px;color:#626569;white-space:pre;overflow:hidden">' +
      esc("Thinks of you: " + (c.opinionWord || "neutral") + " " + sgn(c.opinion || 0) + "  ·  below " + sgn(c.leaveBelow == null ? -60 : c.leaveBelow) + " they go home") + "</div>" +
      '<div class="h-hair" style="left:20px;top:156px;width:558px"></div>' +
      '<div class="h-eb" style="left:20px;top:170px">ORDERS  ·  SAY IT, OR PICK IT HERE</div>';
    var c1 = "", c2 = "";
    for (var k = 0; k < ORDERS.length; k++) { if (k < 3) c1 += orderRow(c, ORDERS[k], k + 1); else c2 += orderRow(c, ORDERS[k], k + 1); }
    out += '<div class="h-ocol flow" style="left:12px;top:184px">' + c1 + '</div><div class="h-ocol flow" style="left:304px;top:184px">' + c2 + "</div>" +
      '<div class="h-hair" style="left:20px;top:300px;width:558px"></div>' +
      '<div class="h-eb" style="left:20px;top:314px">GEAR  ·  YOURS, LENT</div>';
    for (var g = 0; g < GEAR_SLOTS.length; g++) {
      var s = slotOf(c, GEAR_SLOTS[g][0]);
      var full = s && !s.empty && s.item;
      out += '<div class="h-well h-gslot' + (full ? "" : " empty") + '"' + (full ? ' data-item="' + esc(s.item) + '" data-n="1"' : "") + ' style="left:' + (20 + g * 44) + 'px;top:330px"></div>';
    }
    out += '<div style="left:20px;top:376px;font-size:11px;color:#626569;white-space:pre">head  ·  chest  ·  legs  ·  feet  ·  hand  ·  off hand</div>' +
      '<div class="h-pill ink" data-op="companionGear" data-arg="' + esc(c.id) + '" style="right:20px;top:330px;height:25px">Open gear</div>';
    var gift = firstGift(h);
    if (gift) {
      out += '<div class="h-pill' + (c.near ? " ink" : "") + '"' + (c.near ? ' data-op="companionLend" data-arg="' + esc(c.id) + "#" + gift.slot + '"' : "") +
        ' style="right:20px;top:360px;height:25px">' + esc("Take this: " + gift.name + (c.near ? "" : "  ·  come closer")) + "</div>";
    }
    out += wageBlock(c, c.home) +
      '<div class="h-eb" style="left:330px;top:414px">' + esc(upper(first(c.name)) + " SAYS") + "</div>" +
      '<div style="left:330px;top:430px;width:248px;font-size:13px;color:#EDEEEF;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + (c.lastLine ? "“" + esc(c.lastLine) + "”" : "—") + "</div>" +
      '<div style="left:330px;top:452px;width:248px;font-size:11px;color:#626569;white-space:pre;overflow:hidden">' + esc(c.lastLine ? "last: " + (c.lastWhere ? "in " + c.lastWhere : "on the road") : "") + "</div>";
    return out + "</div>";
  }

  function servantCard(sv, house) {
    var house0 = house || {};
    var size = Math.max(1, Math.min(20, house0.larderSize || 16));
    var lit = Math.round(Math.min(1, (house0.larder || 0) / Math.max(1, house0.larderSize || 16)) * size);
    var out = '<div class="h-panel h-hh">' +
      '<div class="h-well" style="left:20px;top:20px;width:120px;height:120px;border-radius:16px">' + (sv.portrait ? face(sv, 96, "left:11px;top:11px") : '<span style="left:36px;top:36px">' + glyph("house", 48, "#626569") + "</span>") + "</div>" +
      '<div class="h-eb" style="left:156px;top:22px">' + esc("SERVANT  ·  THE " + upper(house0.building) + "  ·  " + upper(house0.town)) + "</div>" +
      '<div style="left:156px;top:38px;font-size:22px;color:#EDEEEF;white-space:nowrap">' + esc(sv.name) + "</div>" +
      '<div style="left:156px;top:68px;width:422px;font-size:11.5px;color:#9A9DA1;white-space:pre;overflow:hidden">' + esc((sv.job || "Servant") + (sv.dutyText ? "  ·  " + sv.dutyText : "")) + "</div>" +
      '<div class="h-eb" style="left:156px;top:96px">THE LARDER</div>' +
      '<div class="' + (house0.larderShort ? "wary" : "ink2") + '" style="right:20px;top:94px;font-size:11.5px;white-space:nowrap">' + esc((house0.larder || 0) + " of " + (house0.larderSize || 0) + (house0.larderShort ? "  ·  running short" : "")) + "</div>" +
      barCells(size, lit, Math.floor(416 / size) - 4, 9, Math.floor(416 / size), 156, 112, house0.larderShort ? "wary" : "on") +
      '<div class="h-hair" style="left:20px;top:156px;width:558px"></div>' +
      '<div class="h-eb" style="left:20px;top:170px">THEIR DAY</div>' +
      '<div style="left:20px;top:188px;font-size:13px;color:#EDEEEF;white-space:nowrap">' + esc(upperFirst(sv.dutyText || "keeps the house")) + "</div>" +
      '<div style="left:20px;top:210px;width:558px;font-size:11.5px;color:#626569;line-height:16px">Servants keep the house as their day has them; they take no orders here. Their wages come out of your bank account each morning.</div>';
    return out + wageBlock(sv, house0.town) + "</div>";
  }

  function householdPage(w, h) {
    if (!h && !ui.askedHouse) { ui.askedHouse = true; act("refreshHousehold"); }
    var list = h ? members(h) : [];
    var sel = null;
    for (var i = 0; i < list.length; i++) if (list[i].id === ui.member) sel = list[i];
    if (!sel && list.length) { sel = list[0]; ui.member = sel.id; }
    part(w, "hq-body", JSON.stringify(["h", h && [h.companions, h.houses, h.companionsMax, h.dailyWages, firstGift(h)], ui.member]), function () {
      if (!h) return '<div class="h-empty" style="top:280px">Asking after your household…</div>';
      if (!list.length) {
        return '<div class="h-panel h-roster">' + '<div class="h-eb" style="left:16px;top:16px">IN YOUR SERVICE</div><div class="h-empty" style="top:200px">Nobody yet.</div></div>' +
          '<div class="h-panel h-hh"><div class="h-empty" style="top:200px">Hire a companion in any town (talk to folk who would travel), or a servant for a house you hold.</div></div>';
      }
      return roster(h, list) + (sel.kind === "companion" ? companionCard(h, sel.m) : servantCard(sel.m, sel.house));
    });
    part(w, "hq-foot", JSON.stringify(["h", h ? h.inService : -1]), function () {
      return foot((h ? h.inService : 0) + " IN YOUR SERVICE  ·  ORDERS ARE THE SAME AS SAYING THEM ALOUD  ·  THE SERVER CHECKS EVERY ONE", "household");
    });
  }

  // ---- F3: companion gear (711:47250), an event screen of its own ---------------------------------------------------------
  function gearActions(h, c) {
    var out = [];
    if (!c) return out;
    var hands = h.hands || [];
    var gifts = [];
    for (var i = 0; i < hands.length; i++) if (!hands[i].empty && hands[i].gives) gifts.push(hands[i]);
    gifts.sort(function (a, b) { return (b.slot === ui.hand) - (a.slot === ui.hand); });
    for (var k = 0; k < gifts.length && out.length < 2; k++) {
      out.push({label: "Take this: " + gifts[k].name + ".", glyph: "hand", tone: "good", chip: "to their " + SLOT_WORD[gifts[k].gives],
        op: "companionLend", arg: c.id + "#" + gifts[k].slot, near: true});
    }
    var worn = 0;
    for (var s = 0; s < (c.slots || []).length; s++) if (!c.slots[s].empty && c.slots[s].item) worn++;
    if (worn) out.push({label: "Give that back.", glyph: "leave", tone: "bad", chip: "all of it", op: "companionOrder", arg: c.id + "#return", near: true});
    return out;
  }

  function gearPage(h) {
    var w = win();
    var c = companionById(h, h.gearFor);
    var who = c ? first(c.name) : "";
    part(w, "hg-head", JSON.stringify([c && c.name]), function () {
      return chip() + '<div class="h-eb h-center" style="top:31px;font-size:9.5px;letter-spacing:1.3px;color:#9A9DA1">' + esc(c ? upper(c.name) + "  ·  COMPANION  ·  GEAR" : "GEAR") + "</div>" +
        '<div class="h-pill" data-ui="close" style="left:893px;top:24px">Esc  leave</div>';
    });
    if (!c) {
      part(w, "hg-body", "none", '<div class="h-empty" style="top:280px">Their gear is not open.</div>');
      return;
    }
    part(w, "hg-worn", JSON.stringify([c.slots, c.portrait, c.armor, c.weapon, c.fight, c.near]), function () {
      var out = '<div class="h-panel h-worn"><div class="h-eb" style="left:16px;top:16px">WORN AND HELD  ·  DRAWN ON THEM IN THE WORLD</div>' +
        '<div class="h-well h-figure">' + (c.portrait ? face(c, 160, "left:29px;top:39px") : '<span style="left:78px;top:118px">' + glyph("talk", 64, "#626569") + "</span>") +
        '<div class="floor"></div></div>';
      var at = {head: [40, 58], chest: [40, 122], legs: [40, 186], feet: [40, 250], mainhand: [350, 58], offhand: [350, 122]};
      for (var g = 0; g < GEAR_SLOTS.length; g++) {
        var name = GEAR_SLOTS[g][0], p = at[name];
        var s = slotOf(c, name);
        var full = s && !s.empty && s.item;
        out += '<div class="h-eb h-slotname" style="left:' + (p[0] - 17) + "px;top:" + (p[1] - 15) + 'px">' + GEAR_SLOTS[g][1] + "</div>" +
          '<div class="h-well h-wslot' + (full ? "" : " empty") + '"' + (full ? ' data-item="' + esc(s.item) + '" data-n="1"' + (c.near ? ' data-op="companionTake" data-arg="' + esc(c.id) + "#" + name + '"' : "") : "") +
          ' style="left:' + p[0] + "px;top:" + p[1] + 'px"></div>';
      }
      return out + '<div class="h-hair" style="left:16px;top:358px;width:404px"></div>' +
        '<div class="h-eb" style="left:16px;top:372px">WHAT THIS GIVES ' + esc(upper(who)) + "</div>" +
        '<div class="h-eb" style="left:16px;top:392px">ARMOUR</div><div style="left:16px;top:408px;font-size:13px;color:#EDEEEF">' + (c.armor >= 0 ? c.armor : "—") + "</div>" +
        '<div class="h-eb" style="left:152px;top:392px">WEAPON</div><div style="left:152px;top:408px;width:130px;font-size:13px;color:#EDEEEF;white-space:nowrap;overflow:hidden">' + esc(c.weapon || "bare hands") + "</div>" +
        '<div class="h-eb" style="left:288px;top:392px">FIGHTS</div><div class="' + (c.fight ? "good" : "wary") + '" style="left:288px;top:408px;font-size:13px;white-space:nowrap">' + (c.fight ? "what you fight" : "holds back") + "</div>" +
        '<div style="left:16px;top:446px;width:404px;font-size:11px;color:#626569;white-space:nowrap;overflow:hidden">Handed back when they leave your service; dropped where they fall if they die.</div></div>';
    });
    var hands = h.hands || [];
    if (ui.hand == null || ui.hand < 0) { var g0 = firstGift(h); ui.hand = g0 ? g0.slot : -1; }
    var acts = gearActions(h, c);
    part(w, "hg-hands", JSON.stringify([hands, ui.hand, acts, c.near, c.lastLine, ui.focus]), function () {
      var out = '<div class="h-panel h-pack"><div class="h-eb" style="left:16px;top:16px">' + esc("THEIR PACK  ·  NONE YET") + "</div>";
      for (var p = 0; p < 6; p++) out += '<div class="h-well h-wslot empty" style="left:' + (16 + p * 50) + 'px;top:34px"></div>';
      out += '<div style="left:16px;top:88px;width:447px;font-size:11px;color:#626569;white-space:nowrap">Companions keep no pack yet: what they carry is what they wear and hold.</div>' +
        '<div class="h-hair" style="left:16px;top:114px;width:447px"></div>' +
        '<div class="h-eb" style="left:16px;top:128px">IN YOUR HANDS  ·  WHAT “TAKE THIS” GIVES</div>';
      var sel = null;
      for (var i = 0; i < hands.length && i < 10; i++) {
        var hd = hands[i];
        if (hd.slot === ui.hand) sel = hd;
        out += '<div class="h-well h-hslot' + (hd.slot === ui.hand ? " sel" : "") + (hd.empty ? " empty" : "") + '" data-hand="' + hd.slot + '"' +
          (hd.empty ? "" : ' data-item="' + esc(hd.item) + '" data-n="' + hd.count + '"') + ' style="left:' + (16 + i * 44.6).toFixed(1) + 'px;top:146px">' +
          (hd.count > 1 && !hd.empty ? '<div class="cnt">' + hd.count + "</div>" : "") + (hd.gives && !hd.empty ? '<div class="gv"></div>' : "") + "</div>";
      }
      var note = "Pick something in your hands to see where it would go.";
      if (sel && !sel.empty) {
        var on = sel.gives ? slotOf(c, sel.gives) : null;
        note = sel.name + "  ·  " + (!sel.gives ? "nothing they can wear or hold" : "to their " + SLOT_WORD[sel.gives] + (on && !on.empty && on.name ? "; " + on.name + " comes back to you" : "; they have none"));
      }
      out += '<div style="left:16px;top:200px;width:447px;font-size:11.5px;color:#EDEEEF;white-space:pre;overflow:hidden">' + esc(note) + "</div>" +
        '<div class="h-hair" style="left:16px;top:230px;width:447px"></div><div class="h-acts flow">';
      for (var a = 0; a < acts.length; a++) {
        var o = acts[a];
        var ok = c.near;
        out += '<div class="h-opt flow' + (ok ? "" : " locked") + (a === ui.focus ? " focus" : "") + '"' + (ok ? ' data-op="' + o.op + '" data-arg="' + esc(o.arg) + '"' : "") + ' style="width:463px">' +
          '<div class="k">' + (a + 1) + "</div>" + glyph(o.glyph, 16) + '<div class="l">' + esc(o.label) + "</div>" + dotPill(ok ? o.tone : "wary", ok ? o.chip : "come closer") + "</div>";
      }
      if (!acts.length) out += '<div style="position:relative;padding:10px 8px;font-size:11.5px;color:#626569">Nothing in your hands for them, and nothing of yours on them.</div>';
      return out + "</div>" + '<div class="h-hair" style="left:16px;top:362px;width:447px"></div>' +
        '<div class="h-eb" style="left:16px;top:376px">' + esc(upper(who) + " SAYS") + "</div>" +
        '<div style="left:16px;top:394px;width:447px;font-size:15px;color:#EDEEEF;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + (c.lastLine ? "“" + esc(c.lastLine) + "”" : "—") + "</div>" +
        '<div style="left:16px;top:424px;width:447px;font-size:11px;color:#626569;line-height:15px">Armour goes to its slot, a shield to the off hand, a weapon or tool to the main hand; the piece it replaces comes back to you.</div></div>';
    });
    part(w, "hg-foot", "f" + acts.length, function () {
      return foot("CLICK A PIECE TO TAKE IT BACK  ·  1–" + Math.max(1, acts.length) + " ACT  ·  ESC CLOSES", "gear");
    });
  }

  // ---- F4: People A, the list with story (713:48066) -----------------------------------------------------------------------
  function peoplePage(w, p) {
    var rows = p ? p.rows : [];
    var sel = null;
    for (var i = 0; i < rows.length; i++) if ((rows[i].id || rows[i].name) === ui.person) sel = rows[i];
    if (!sel && rows.length) { sel = rows[0]; ui.person = sel.id || sel.name; }
    part(w, "hq-body", JSON.stringify(["p", rows, ui.person, H.personTrack && H.personTrack.id, ui.scroll]), function () {
      if (!p || !rows.length) {
        return '<div class="h-panel h-plist"><div class="h-eb" style="left:16px;top:16px">FOLK OF NOTE YOU HAVE MET</div></div>' +
          '<div class="h-panel h-story"><div class="h-empty" style="top:200px">Nobody of note yet. Ask anyone “Who are the folk of note here?”</div></div>';
      }
      var out = '<div class="h-panel h-plist"><div class="h-eb" style="left:16px;top:16px">FOLK OF NOTE YOU HAVE MET</div>';
      var y = 36, town = null;
      for (var k = Math.min(ui.scroll, rows.length - 1); k < rows.length && y < 450; k++) {
        var r = rows[k];
        if (r.town !== town) {
          town = r.town;
          var ended = true;
          for (var e = k; e < rows.length && rows[e].town === town; e++) if (!rows[e].ended) ended = false;
          if (k > 0) y += 6;
          out += '<div class="h-eb" style="left:16px;top:' + y + 'px">' + esc(upper(town) + (ended ? "  ·  ENDED" : "")) + "</div>";
          y += 12;
        }
        var key = r.id || r.name;
        out += '<div class="h-prow' + (key === ui.person ? " sel" : "") + '" data-person="' + esc(key) + '" style="top:' + y + 'px">' +
          '<div class="h-well fw">' + (r.portrait ? face(r, 28, "left:3px;top:3px") : "") + "</div>" +
          '<div class="nm flow" style="width:' + (r.yours ? 186 : 240) + 'px"><span class="n">' + esc(r.name) + '</span><span class="r">' + esc(r.role) + "</span></div>" +
          '<div class="st">' + esc(r.story) + "</div>" +
          (r.yours ? dotPill("wary", "you moved it", "right:8px;top:4px;height:20px") : '<div class="' + opinionTone(r.opinion) + '" style="right:8px;top:5px;font-size:11px">' + sgn(r.opinion) + "</div>") + "</div>";
        y += 54;
      }
      return out + "</div>" + storyCard(sel);
    });
    part(w, "hq-foot", JSON.stringify(["p", p && [p.met, p.towns, p.moved]]), function () {
      return foot((p ? p.met : 0) + " MET  ·  " + (p ? p.towns : 0) + " TOWNS  ·  " + plural(p ? p.moved : 0, "STORY", "STORIES") + " YOU MOVED  ·  ASK ANYONE “WHO ARE THE FOLK OF NOTE HERE?”", "people");
    });
  }

  function storyCard(r) {
    var out = '<div class="h-panel h-story">' +
      '<div class="h-well" style="left:20px;top:20px;width:96px;height:96px;border-radius:14px">' + (r.portrait ? face(r, 80, "left:7px;top:7px") : '<span style="left:24px;top:24px">' + glyph("talk", 48, "#626569") + "</span>") + "</div>" +
      '<div class="h-eb" style="left:132px;top:22px">' + esc(upper(r.town) + "  ·  " + upper(r.role)) + "</div>" +
      '<div style="left:132px;top:38px;font-size:22px;color:#EDEEEF;white-space:nowrap">' + esc(r.name) + "</div>";
    var traits = [];
    for (var i = 0; i < (r.traits || []).length && i < 3; i++) traits.push(lower(r.traits[i].name));
    out += '<div style="left:132px;top:68px;width:406px;font-size:11.5px;color:#9A9DA1;white-space:pre;overflow:hidden">' +
      esc((r.job ? r.job + (r.place ? " at the " + lower(r.place) : "") : r.role) + (traits.length ? "  ·  " + traits.join(", ") : "")) + "</div>" +
      '<div class="h-eb" style="left:132px;top:92px">OPINION OF YOU</div>' +
      '<div class="' + opinionTone(r.opinion) + '" style="right:20px;top:90px;font-size:11.5px;white-space:pre">' + esc(upperFirst(r.opinionWord || "") + "  " + sgn(r.opinion)) + "</div>" +
      opinionBar(r.opinion || 0, 132, 106) +
      '<div class="h-hair" style="left:20px;top:132px;width:518px"></div>';
    var beats = r.beats || [];
    var nowAt = beats.length;
    for (var b = 0; b < beats.length; b++) if (beats[b].now) nowAt = b + 1;
    out += '<div class="h-eb" style="left:20px;top:146px">' + esc("THE STORY" + (beats.length ? "  ·  BEAT " + nowAt + " OF " + Math.max(beats.length, nowAt + (r.next && r.next.length && !r.ended ? 1 : 0)) : "")) + "</div>";
    // The beats so far, the player's (or someone's) choice before the one now, at most five lines.
    var lines = [];
    for (var k = 0; k < beats.length; k++) {
      if (beats[k].now && r.chose) lines.push({choice: true});
      lines.push({beat: beats[k]});
    }
    if (!beats.length) lines.push({beat: {text: r.story, now: true}});
    if (lines.length > 5) lines = lines.slice(lines.length - 5);
    var y = 166;
    for (var l = 0; l < lines.length; l++, y += 26) {
      var ln = lines[l], last = l === lines.length - 1;
      var on = ln.choice || ln.beat.now;
      out += '<div class="h-beat' + (on ? " on" : "") + '" style="left:26px;top:' + (y + 3) + 'px"></div>' +
        (!last || !r.ended ? '<div class="h-thread" style="left:29.5px;top:' + (y + 13) + 'px;height:' + (last ? 14 : 18) + 'px"></div>' : "");
      if (ln.choice) {
        var ch = r.chose;
        out += '<div class="flow h-cline" style="left:46px;top:' + y + 'px"><span>' + esc((ch.yours ? "You chose: " : (ch.by || "Someone") + " chose: ") + ch.label) + "</span>" + (ch.costCoins ? smallPrice(ch.costCoins) : "") + "</div>" +
          dotPill("wary", ch.yours ? "your choice" : "their choice", "right:20px;top:" + (y - 3) + "px;height:20px");
      } else {
        out += '<div style="left:46px;top:' + y + 'px;width:400px;font-size:11.5px;color:' + (ln.beat.now ? "#EDEEEF" : "#9A9DA1") + ';white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + esc(ln.beat.text) + "</div>" +
          (ln.beat.now ? dotPill(r.ended ? "ink" : "wary", r.ended ? "ended" : "now", "right:20px;top:" + (y - 3) + "px;height:20px") : "");
      }
    }
    var ny = Math.max(274, y + 4);
    if (!r.ended && r.next && r.next.length) {
      out += '<div class="h-eb" style="left:46px;top:' + ny + 'px">WHERE IT MAY GO</div>';
      for (var n = 0; n < r.next.length && n < 2; n++) {
        var nx = r.next[n];
        out += '<div class="h-end' + (nx.via === "time" ? " time" : "") + '" style="left:' + (46 + n * 252) + "px;top:" + (ny + 16) + 'px"><div class="tx">' +
          esc(nx.text) + "</div></div>";
      }
    } else if (r.ended) {
      out += '<div style="left:46px;top:' + ny + 'px;width:492px;font-size:11.5px;color:#626569">This story has ended.</div>';
    }
    out += '<div class="h-hair" style="left:20px;top:358px;width:518px"></div><div class="h-eb" style="left:20px;top:372px">RELATIONS YOU TOUCHED</div><div class="h-pills flow" style="left:20px;top:388px;width:518px;height:25px;overflow:hidden">';
    var rel = r.relations || [];
    for (var q = 0; q < rel.length && q < 3; q++) out += dotPill(rel[q].opinion > 0 ? "good" : rel[q].opinion < 0 ? "bad" : "ink", rel[q].name + ", " + rel[q].bond + "  ·  " + sgn(rel[q].opinion), "height:25px");
    if (!rel.length) out += '<div class="h-pill">Nobody else in it yet</div>';
    var find = r.find || [];
    var where = [];
    for (var f = 0; f < find.length && f < 3; f++) where.push(find[f].text || (find[f].fromText + "–" + find[f].toText + " " + find[f].what));
    var following = H.personTrack && r.id && H.personTrack.id === r.id;
    out += '</div><div class="h-eb" style="left:20px;top:424px">FIND</div>' +
      '<div style="left:84px;top:421px;width:370px;font-size:11.5px;color:#9A9DA1;white-space:pre;overflow:hidden;text-overflow:ellipsis">' + esc(where.join("  ·  ") || r.home || "") + "</div>" +
      (r.id ? (following ? '<div class="h-pill ink" data-op="untrackPerson" style="right:20px;top:418px;height:25px"><div class="h-dot violet"></div><span>Following  ·  stop</span></div>'
        : '<div class="h-pill ink" data-op="trackPerson" data-arg="' + esc(r.id) + '" style="right:20px;top:418px;height:25px">Waypoint</div>') : "");
    return out + "</div>";
  }

  // ---- F1: a stranger on the road (708:43645) ------------------------------------------------------------------------------
  /** The hooded figure (no face, no name), pixel art drawn as SVG cells so it stays crisp at any zoom. */
  function hoodArt() {
    var rows = ["...HHHHHH...", "..HHHHHHHH..", "..HDDDDDDH..", "..HDYDDYDH..", "..HDDDDDDH..", "..HHDDDDHH..", ".HHHHHHHHHH.",
      "HHHHHHHHHHHH", "HHHBHHHHHHHH", "HHHHBHHHHHHH", "HHHHHBHHHHHH", "HHHHHHBHHHHH"];
    var col = {H: "#4A3A2E", D: "#2A2019", Y: "#E3C47E", B: "#7A5A3C"};
    var h = '<svg width="160" height="160" viewBox="0 0 12 12" shape-rendering="crispEdges">';
    for (var y = 0; y < rows.length; y++) for (var x = 0; x < 12; x++) {
      var ch = rows[y].charAt(x);
      if (col[ch]) h += '<rect x="' + x + '" y="' + y + '" width="1" height="1" fill="' + col[ch] + '"/>';
    }
    return h + "</svg>";
  }

  function shareWords(s) {
    return s >= 0.99 ? "ALL YOU CARRY" : Math.abs(s - 0.5) < 0.01 ? "HALF OF WHAT YOU CARRY" : Math.abs(s - 0.25) < 0.01 ? "A QUARTER OF WHAT YOU CARRY"
      : Math.abs(s - 0.1) < 0.01 ? "A TENTH OF WHAT YOU CARRY" : Math.round(s * 100) + " % OF WHAT YOU CARRY";
  }

  function menaceWord(m) { return m < 25 ? "Easy pickings" : m < 50 ? "Worth watching" : m < 75 ? "Dangerous" : "Fearsome"; }

  function strangerChip(o, s) {
    var bandit = s.kind === "bandit";
    if (o.cost && o.cost.total > 0 && (o.kind === "bribe" || o.id === "pay")) return '<div class="h-mchip flow"><div class="h-dot wary"></div>' + smallPrice(o.cost) + "</div>";
    if (bandit && o.kind === "fight") return '<div class="h-mchip flow"><div class="h-dot bad"></div><span>' + esc(s.band > 1 ? s.band + " of them" : "One of them") + "</span></div>";
    if (bandit && (o.kind === "leave" || o.kind === "run")) return '<div class="h-mchip flow"><div class="h-dot bad"></div><span>' + esc(s.bows ? (s.bows === 1 ? "They have one bow" : "They have " + s.bows + " bows") : "No bows among them") + "</span></div>";
    if (o.odds >= 0 && o.kind === "intimidate") {
      return '<div class="h-mchip flow"><div class="h-dot ' + oddsTone(o.odds) + '"></div><span>' + esc("Menace  ·  " + o.odds + " %" + (s.bladeDrawn ? "" : "  ·  a drawn blade helps")) + "</span></div>";
    }
    return optionChip(o);
  }

  function talkStranger(t) {
    var w = win();
    var r = t.resident, s = t.stranger;
    var bandit = s.kind === "bandit";
    var place = placeLine(t);
    part(w, "ht-head", JSON.stringify(["s", place, s.band, s.inSight, bandit]), function () {
      return chip() +
        '<div class="h-eb' + (bandit ? " bad" : "") + '" style="left:335px;top:31px;font-size:9.5px;letter-spacing:1.3px;' + (bandit ? "" : "color:#9A9DA1") + '">' + esc(upper((bandit ? ["waylaid"] : []).concat(place).join("  ·  "))) + "</div>" +
        (bandit && s.band ? dotPill("bad", "Band of " + s.band + "  ·  " + (s.inSight || 0) + " in sight", "right:28px;top:25px;height:25px") : '<div class="h-pill" data-ui="close" style="left:893px;top:24px">Esc  leave</div>');
    });
    part(w, "ht-card", JSON.stringify(["s", r.name, r.job, r.portrait, s, H.player && H.player.purse]), function () {
      var pills = [];
      if (s.armed) pills.push("armed  ·  " + s.armed);
      if (s.bladeDrawn) pills.push("blade drawn");
      for (var i = 0; i < r.traits.length && pills.length < 3; i++) pills.push(lower(r.traits[i].name));
      var tp = "";
      for (var k = 0; k < pills.length; k++) tp += '<div class="h-pill">' + esc(pills[k]) + "</div>";
      var out = '<div class="h-panel h-res">' +
        '<div class="h-well pw">' + (s.hooded || !r.portrait ? '<span style="left:62px;top:8px">' + hoodArt() + "</span>" : face(r, 168, "left:57.5px;top:15px")) +
        (s.hooded ? '<div class="h-eb" style="right:12px;top:162px;color:#9A9DA1">HOODED  ·  NO FACE, NO NAME</div>' : "") + "</div>" +
        '<div class="nm">' + esc(r.name) + "</div>" +
        '<div class="job">' + esc((s.kindName || r.job) + "  ·  " + (s.bound ? "bound for " + s.bound : "from the road")) + "</div>" +
        '<div class="h-traits flow" style="left:15px;top:266px">' + tp + "</div>" +
        '<div class="h-hair" style="left:15px;top:304px;width:285px"></div>';
      if (bandit) {
        var cells = "";
        var n = Math.min(10, s.band || 0);
        for (var b = 0; b < n; b++) cells += '<i class="' + (b < (s.inSight || 0) ? "bad" : "") + '" style="left:' + (b * 28) + 'px;width:22px;height:22px;border-radius:4px"></i>';
        out += '<div class="h-eb" style="left:15px;top:318px">THE BAND</div>' +
          '<div class="bad" style="right:15px;top:316px;font-size:11.5px;white-space:pre">' + esc((s.band || 0) + "  ·  " + (s.inSight || 0) + " in sight") + "</div>" +
          '<div class="h-cel" style="left:15px;top:336px;width:' + Math.max(22, n * 28) + 'px;height:22px">' + cells + "</div>" +
          '<div style="left:15px;top:366px;width:285px;font-size:11px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' +
          esc(Math.max(0, (s.band || 0) - (s.inSight || 0)) > 0 ? plural(Math.max(0, s.band - s.inSight), "more", "more") + " out of sight" + (s.bows ? "  ·  " + plural(s.bows, "bow", "bows") : "") : "All of them in front of you") + "</div>";
      } else {
        out += '<div class="h-eb" style="left:15px;top:318px">ON THE ROAD</div>' +
          '<div style="left:15px;top:336px;width:285px;font-size:12px;color:#EDEEEF;white-space:pre;overflow:hidden">' + esc((s.partySize > 1 ? "a party of " + s.partySize : "alone") + (s.bound ? "  ·  bound for " + s.bound : "")) + "</div>" +
          '<div style="left:15px;top:356px;width:285px;font-size:11px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' + esc(s.resident ? "Lives in these parts" : "A traveller, not from here") + "</div>";
      }
      var purse = s.carriedCoins || (H.player && H.player.purse);
      out += '<div class="h-hair" style="left:15px;top:386px;width:285px"></div>' +
        '<div class="h-eb" style="left:15px;top:398px">YOU CARRY</div>' +
        (purse ? price(purse, "", "position:absolute;left:15px;top:414px") + '<div style="right:15px;top:414px;font-size:11px;color:#626569">' + num(purse.total) + " bits</div>" : "");
      if (bandit) {
        out += '<div class="h-eb" style="left:15px;top:438px">MENACE  ·  HOW YOU LOOK TO THEM</div>' +
          '<div style="left:15px;top:452px;width:285px;font-size:11.5px;color:#EDEEEF;white-space:pre;overflow:hidden">' +
          esc(menaceWord(s.menace || 0) + "  ·  armour " + (s.armour || 0) + ", blade " + (s.bladeDrawn ? "drawn" : "sheathed")) + "</div>";
      }
      return out + "</div>";
    });
    var demand = bandit && s.demandCoins && s.demandCoins.total > 0;
    part(w, "ht-speech", JSON.stringify(["s", t.line, t.lastCheck, demand && s.demandCoins, s.share]), function () {
      var out = '<div class="h-speech"><div class="h-eb">THEY SAY</div><div class="line">“' + esc(t.line) + "”</div>" +
        (t.lastCheck && !demand ? '<div class="check">' + esc(t.lastCheck) + "</div>" : "") + "</div>";
      if (demand) {
        var d = s.demandCoins;
        out += '<div class="h-eb bad" style="left:369px;top:138px">THE DEMAND  ·  ' + esc(shareWords(s.share || 0.25)) + "</div>" +
          '<div class="h-dmd flow" style="left:369px;top:156px">' + dmd("crown", d.crowns) + dmd("mark", d.marks) + dmd("bit", d.bits) + "</div>" +
          '<div style="right:28px;top:160px;font-size:11px;color:#9A9DA1">= ' + num(d.total) + " bits</div>" +
          '<div class="h-hair" style="left:368px;top:200px;width:590px"></div>';
      } else {
        out += '<div class="h-hair" style="left:368px;top:179px;width:590px"></div>';
      }
      return out;
    });
    var opts = talkOptions(t), rows = demand ? 5 : 8, top = talkWindow(opts.length, rows);
    part(w, "ht-opts", JSON.stringify(["s", opts, ui.focus, top, demand, s.band, s.bows, s.bladeDrawn]), function () {
      var out = '<div class="h-opts flow" style="top:' + (demand ? 208 : 189) + 'px">';
      for (var i = top; i < opts.length && i < top + rows; i++) {
        var o = opts[i];
        var g = o.locked ? "lock" : KIND_GLYPH[o.kind] || "talk";
        out += '<div class="h-opt flow' + (o.locked ? " locked" : "") + (i === ui.focus ? " focus" : "") + '" data-opt="' + i + '" data-kind="' + esc(o.kind) + '" style="width:598px">' +
          '<div class="k">' + (o.key >= 0 ? o.key : "") + "</div>" + glyph(g, 16) + '<div class="l">' + esc(optionLabel(o)) + "</div>" + strangerChip(o, s) + "</div>";
      }
      out += "</div>" + talkBar(opts.length, rows, top, 964, demand ? 208 : 189, rows * 38 - 2);
      if (bandit) {
        out += '<div style="left:377px;top:404px;width:560px;font-size:11px;color:#626569;line-height:15px">Odds from Speech and Menace (Mettle) on Hearth’s factors: your armour and blade, the band’s size, how lean the winter has been. Paying is a sure thing; they keep the share, not the purse.</div>';
        var oc = s.outcomes;
        if (oc && oc.talked) {
          out += '<div class="h-panel" style="left:369px;top:456px;width:590px;height:96px;border-radius:16px">' +
            '<div class="h-eb" style="left:16px;top:16px">IF YOU TALK AND IT WORKS</div>' +
            '<div style="left:16px;top:34px;width:558px;font-size:14px;color:#EDEEEF;white-space:nowrap;overflow:hidden">“' + esc(oc.talked) + "”</div>" +
            '<div style="left:16px;top:60px;width:558px;font-size:11px;color:#9A9DA1;white-space:nowrap;overflow:hidden">' +
            esc((oc.talkFailed ? "If it fails: “" + oc.talkFailed + "”" : "") + (oc.scared ? "  ·  Threatened and scared: “" + oc.scared + "”" : "")) + "</div></div>";
        }
      }
      return out;
    });
    part(w, "ht-foot", "s" + opts.length + bandit, function () {
      return foot("1–" + Math.max(1, Math.min(opts.length, 9)) + " CHOOSE  ·  " + (bandit ? "ESC DOES NOTHING HERE: THEY ARE WAITING" : "0 OR ESC LEAVES"), "talk  ·  stranger");
    });
  }

  function dmd(type, n) {
    var name = type === "crown" ? "CROWNS" : type === "mark" ? "MARKS" : "BITS";
    return '<div class="d">' + coin(type, 20) + '<span class="c">' + n + '</span><span class="n">' + name + "</span></div>";
  }

  // ---- F5: retinue ranks and burgher status on the reputation page (712:47881) ---------------------------------------------
  function anyBurgher(r) {
    for (var i = 0; i < r.towns.length; i++) if (r.towns[i].burgher) return true;
    return false;
  }

  var LADDER5 = [["stranger", "Stranger"], ["known", "Known"], ["friend", "Friend"], ["burgher", "Burgher"], ["hero", "Hero"]];

  function townCard(r, s) {
    var b = s.burgher;
    var out = '<div class="h-panel h-tcard">' +
      '<div class="h-eb" style="left:20px;top:18px">' + esc(upper(s.name) + "  ·  " + upper(s.tier) + (s.culture ? "  ·  " + upper(s.culture) : "") + (b ? "  ·  SWORN " + daysAgo(b.swornDays) : "")) + "</div>" +
      '<div style="left:20px;top:34px;font-size:22px;color:#EDEEEF;white-space:nowrap">' + esc((b ? "Burgher" : s.titleName) + " of " + s.name) + "</div>" +
      '<div class="' + (b ? "good" : titleTone(s.title)) + '" style="right:20px;top:34px;font-size:22px">' + sgn(s.value) + "</div>" +
      '<div class="h-traits flow" style="left:20px;top:70px">';
    for (var k = 0; k < LADDER5.length; k++) {
      var on = LADDER5[k][0] === s.title;
      out += '<div class="h-pill' + (on ? " on" : "") + '" style="height:25px">' + (on ? '<div class="h-dot ' + (b || s.title === "hero" ? "good" : "violet") + '"></div>' : "") + "<span>" + LADDER5[k][1] + "</span></div>";
    }
    out += '</div><div class="h-hair" style="left:20px;top:104px;width:558px"></div>';
    if (b) {
      out += '<div class="h-eb" style="left:20px;top:116px">A BURGHER’S RIGHTS</div><div class="h-pills flow" style="left:20px;top:132px;height:25px;overflow:hidden">';
      for (var i = 0; i < b.rights.length; i++) out += dotPill("good", b.rights[i].text, "height:25px");
      var cn = b.council || {};
      var when = cn.sitting ? "SITTING NOW" : "SITS ON THE REST DAY" + (cn.dueFrom > b.today ? " IN " + plural(cn.dueFrom - b.today, "DAY", "DAYS") : " NEXT");
      out += '</div><div class="h-eb" style="left:20px;top:166px">' + esc("COUNCIL  ·  " + when) + '</div><div class="h-pills flow" style="left:20px;top:180px;height:23px;overflow:hidden">';
      var table = cn.table || [];
      for (var t = 0; t < table.length && t < 4; t++) out += table[t].yours ? dotPill("wary", table[t].name + "  ·  your voice", "height:23px") : '<div class="h-pill" style="height:23px">' + esc(table[t].name) + "</div>";
      if (!table.length) out += '<div class="h-pill" style="height:23px">' + esc(cn.lastChoice ? "Last chosen: " + cn.lastChoice : "Nothing on the table yet") + "</div>";
      out += "</div>";
    } else {
      out += '<div class="h-eb" style="left:20px;top:116px">WHAT FOLK NOTICE</div><div class="h-pills flow" style="left:20px;top:132px;height:25px;overflow:hidden">';
      for (var n = 0; n < r.looks.length && n < 5; n++) out += dotPill("ink", r.looks[n], "height:25px");
      if (!r.looks.length) out += '<div class="h-pill" style="height:25px">Nothing about you stands out</div>';
      out += '</div><div class="h-eb" style="left:20px;top:166px">FAME  ·  HOW YOU COME ACROSS</div>' +
        '<div style="left:20px;top:182px;width:558px;font-size:12px;color:#EDEEEF;white-space:pre;overflow:hidden">' + esc(r.fameWord + "  ·  Charisma " + r.charismaWord + "  ·  Menace " + r.menaceWord) + "</div>";
    }
    return out + "</div>";
  }

  function retinueCard(r, c) {
    if (!c) {
      return '<div class="h-panel h-rcard"><div class="h-eb" style="left:20px;top:18px">YOUR COMPANY</div>' +
        '<div style="left:20px;top:36px;width:558px;font-size:12px;color:' + (r.company ? "#EDEEEF" : "#626569") + '">' + esc(r.company ? r.company + (r.members.length ? "  ·  " + r.members.join(", ") : "") : "You travel alone. Standing is per player; a company shares a part.") + "</div>" +
        '<div class="h-hair" style="left:20px;top:76px;width:558px"></div><div class="h-eb" style="left:20px;top:90px">NO LORD’S SERVICE</div>' +
        '<div style="left:20px;top:108px;width:558px;font-size:11.5px;color:#626569;line-height:16px">Petition a lord at his seat for a hearing. Commissions done for him raise you through his retinue, to a knighthood at the top.</div></div>';
    }
    var ranks = c.ranks && c.ranks.length ? c.ranks : [{id: c.rankId, name: c.rankName, commissions: c.commissions, reached: true, current: true}];
    var out = '<div class="h-panel h-rcard">' +
      '<div class="h-eb" style="left:20px;top:18px">' + esc("RETINUE  ·  " + upper(c.lord || "an empty seat") + " OF " + upper(c.seat)) + "</div>" +
      '<div style="left:20px;top:34px;font-size:17px;color:#EDEEEF;white-space:nowrap">' + esc(c.rankName + (c.lord ? " to " + c.lord : "")) + "</div>" +
      (c.whyText ? dotPill("wary", c.whyText, "right:20px;top:14px;height:25px;max-width:300px") : c.why === "top" ? dotPill("good", "the top of his household", "right:20px;top:14px;height:25px") : "");
    for (var i = 0; i < ranks.length && i < 4; i++) {
      var k = ranks[i];
      var pips = "";
      for (var p = 0; p < ranks.length && p < 4; p++) pips += '<i class="' + (p <= i ? (k.current ? "brass" : k.reached ? "on" : "") : "") + '" style="left:' + p * 11 + 'px"></i>';
      out += '<div class="h-rank' + (k.current ? " on" : "") + '" style="left:' + (20 + i * 139) + 'px;top:66px">' +
        '<div class="pips">' + pips + '</div><div class="nm">' + esc(k.name) + '</div><div class="sb">' + esc(k.commissions > 0 ? plural(k.commissions, "commission", "commissions") : "a hearing") + "</div></div>";
    }
    out += '<div class="h-hair" style="left:20px;top:138px;width:558px"></div><div class="h-eb" style="left:20px;top:150px">YOUR RIGHTS NOW</div><div class="h-pills flow" style="left:20px;top:166px;height:25px;overflow:hidden">';
    for (var g = 0; g < (c.rights || []).length; g++) out += dotPill("brass", c.rights[g].text || c.rights[g], "height:25px");
    if (!(c.rights || []).length) out += '<div class="h-pill" style="height:25px">None yet</div>';
    out += '</div><div class="h-eb" style="left:20px;top:202px">' + esc(c.next ? "TO BE " + upper(c.next) : "AT THE TOP OF THE HOUSEHOLD") + (c.demoted ? "  ·  ONE RANK DOWN UNTIL YOU PLEDGE AGAIN" : "") + "</div>";
    if (c.next) {
      var need = Math.max(1, Math.min(12, c.needed || 1));
      out += '<div class="h-eb" style="left:20px;top:222px">COMMISSIONS</div>' + barCells(need, Math.min(need, c.commissions || 0), 12, 12, 16, 96, 221, "brass") +
        '<div style="left:' + (100 + need * 16) + 'px;top:219px;font-size:11px;color:#9A9DA1;white-space:pre">' + (c.commissions || 0) + " / " + (c.needed || 0) + "</div>";
      if (c.knighthood) {
        var ok = c.standing >= c.knightStanding;
        out += '<div class="h-eb" style="left:310px;top:222px">STANDING</div><div class="' + (ok ? "good" : "wary") + '" style="left:368px;top:219px;font-size:11px;white-space:pre">' + c.standing + " of " + c.knightStanding + (ok ? "  ✓" : "") + "</div>" +
          '<div class="h-eb" style="left:440px;top:222px">A FEAT</div><div class="' + (c.feat ? "good" : "") + '" style="left:484px;top:219px;width:94px;font-size:11px;color:' + (c.feat ? "" : "#9A9DA1") + ';white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + (c.feat ? "done  ✓" : "a tournament won") + "</div>";
      }
    }
    return out + "</div>";
  }

  function reputationLw(w, r) {
    part(w, "hq-body", JSON.stringify(["r2", r, ui.town, ui.lord]), function () {
      var towns = r.towns;
      if (ui.town < 0 || ui.town >= towns.length) { ui.town = 0; for (var i = 0; i < towns.length; i++) if (towns[i].here) ui.town = i; }
      var ret = r.retinue || [];
      if (ui.lord < 0 || ui.lord >= ret.length) ui.lord = 0;
      var shown = ret.length ? 4 : 6;
      var h = '<div class="h-panel" style="left:27px;top:75px;width:317px;height:482px"><div class="h-eb" style="left:15px;top:15px">STANDING  ·  PER TOWN</div><div class="h-eb" style="right:15px;top:15px">−100 · 0 · +100</div>';
      for (var t = 0; t < towns.length && t < shown; t++) {
        var tw = towns[t], y = 31 + t * 72;
        h += '<div class="h-town' + (t === ui.town ? " sel" : "") + '" data-town="' + t + '" style="top:' + y + 'px"></div>' +
          '<div style="left:15px;top:' + (y + 8) + 'px;font-size:13px;color:#EDEEEF;white-space:nowrap">' + esc(tw.name) + "</div>" +
          '<div style="right:15px;top:' + (y + 9) + 'px;font-size:12px;color:#9A9DA1">' + sgn(tw.value) + "</div>" +
          standingCells(tw.value, 15, y + 30, 14, 5, tw.burgher ? "good" : tw.title === "hero" ? "brass" : null) +
          '<div class="h-eb2" style="left:15px;top:' + (y + 47) + 'px">' + esc(upper((tw.burgher ? "Burgher" : tw.titleName) + " " + (tw.burgher ? "of " : "in ") + tw.name) + (tw.owns ? "  ·  OWNS PROPERTY" : "")) + "</div>";
      }
      if (ret.length) {
        h += '<div class="h-hair" style="left:15px;top:330px;width:285px"></div><div class="h-eb" style="left:15px;top:344px">' + esc("YOUR SERVICE  ·  " + plural(ret.length, "LORD", "LORDS")) + "</div>";
        for (var l = 0; l < ret.length && l < 2; l++) {
          var c = ret[l], ly = 364 + l * 44;
          h += '<div class="h-lord' + (l === ui.lord ? " sel" : "") + '" data-lord="' + l + '" style="top:' + (ly - 4) + 'px"></div>' +
            '<div class="h-well" style="left:15px;top:' + ly + 'px;width:32px;height:32px;border-radius:9px"><span style="left:7px;top:7px">' + glyph("tower", 16) + "</span></div>" +
            '<div style="left:57px;top:' + (ly + 1) + 'px;font-size:13px;color:#EDEEEF;white-space:nowrap">' + esc(c.lord || "An empty seat") + "</div>" +
            '<div style="left:57px;top:' + (ly + 17) + 'px;font-size:11px;color:#9A9DA1;white-space:nowrap">' + esc("seat: " + c.seat) + "</div>" +
            '<div class="h-pill ink" style="right:15px;top:' + (ly + 4) + 'px;height:25px">' + esc(c.rankName) + "</div>";
        }
        h += '<div style="left:15px;top:448px;width:285px;font-size:10.5px;color:#626569;white-space:nowrap;overflow:hidden">Ranks are per lord; an heir keeps you a rank lower.</div>';
      }
      h += "</div>";
      var s = towns[ui.town];
      return h + (s ? townCard(r, s) : '<div class="h-panel h-tcard"><div class="h-empty" style="top:90px">No town knows you yet.</div></div>') + retinueCard(r, ret[ui.lord]);
    });
    part(w, "hq-foot", JSON.stringify(["r2", r.towns.length, (r.retinue || []).length]), function () {
      var owns = 0, sworn = 0;
      for (var i = 0; i < r.towns.length; i++) { if (r.towns[i].owns) owns++; if (r.towns[i].burgher) sworn++; }
      return foot(r.towns.length + " TOWNS  ·  " + owns + " WITH PROPERTY  ·  BURGHER OF " + sworn + "  ·  SERVING " + plural((r.retinue || []).length, "LORD", "LORDS"), "standing");
    });
  }

  // ---- render ---------------------------------------------------------------------------------------------------------------------------
  function active() { return kind === "hearth" || kind === "quests"; }

  function render() {
    if (!active()) return;
    var name = kind === "quests" ? "quests" : screen;
    var style = H && H.settings ? H.settings.conversationStyle : "card";
    var frame = name + "|" + (name === "talk" ? style + (H && H.talk && H.talk.stranger ? "s" : "") : "") + "|" + (lay ? lay.vw + "x" + lay.vh : "");
    if (frame !== drawn) {
      drawn = frame;
      clear();
    }
    var cine = name === "talk" && style === "cinematic" && !(H && H.talk && H.talk.stranger);
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
    else if (name === "gear" && H.household) gearPage(H.household);
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
    }
    if (kind === "hearth" || kind === "quests") {
      // Item wells: market goods, pockets, a companion's gear (the Household segment and the gear screen).
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
    if (screen === "gear" && H && H.household) {
      var acts = gearActions(H.household, companionById(H.household, H.household.gearFor));
      for (var i = 0; i < acts.length; i++) acts[i].key = i + 1;
      return acts;
    }
    return [];
  }

  function pickOption(i) {
    var list = options();
    var o = list[i];
    if (!o) return;
    if (screen === "talk") { if (!o.locked) act("choose", o.id); }
    else if (screen === "gear") {
      var c = H && H.household ? companionById(H.household, H.household.gearFor) : null;
      if (c && c.near) act(o.op, o.arg);
    } else if (o.enabled) act("confront", o.id);
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
    if ((el = attr(t, "data-member"))) { ui.member = el.getAttribute("data-member"); render(); return; }
    if ((el = attr(t, "data-person"))) { ui.person = el.getAttribute("data-person"); render(); return; }
    if ((el = attr(t, "data-lord"))) { ui.lord = +el.getAttribute("data-lord"); render(); return; }
    if ((el = attr(t, "data-hand"))) { ui.hand = +el.getAttribute("data-hand"); render(); return; }
    if ((el = attr(t, "data-quest"))) { ui.quest = el.getAttribute("data-quest"); ui.pop = ""; render(); return; }
    if ((el = attr(t, "data-town"))) { ui.town = +el.getAttribute("data-town"); render(); return; }
    if ((el = attr(t, "data-plot"))) { ui.plot = +el.getAttribute("data-plot"); render(); return; }
    if ((el = attr(t, "data-ui"))) { uiAction(el.getAttribute("data-ui")); return; }
    if (ui.pop && !attr(t, "class")) { ui.pop = ""; render(); }
  }

  function uiAction(a) {
    var c = a.indexOf(":"), k = c < 0 ? a : a.slice(0, c), v = c < 0 ? "" : a.slice(c + 1);
    if (k === "close") { send({type: "close"}); return; }
    if (k === "seg") { seg = v; ui.scroll = 0; ui.pop = ""; if (v === "reputation") ui.asked = false; if (v === "household") ui.askedHouse = false; }
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
        var n = list.length;
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
    if (screen === "talk" && H && H.talk) ui.focus = -1, ui.scroll = Math.max(0, Math.min(Math.max(0, talkOptions(H.talk).length - (ui.talkRows || 10)), ui.scroll + dy * (ui.talkStep || 1)));
    else if (screen === "market" && H && H.market) ui.scroll = Math.max(0, Math.min(Math.max(0, marketRows(H.market).length - 8), ui.scroll + dy));
    else if (kind === "quests" && seg === "people" && H && H.people) ui.scroll = Math.max(0, Math.min(Math.max(0, H.people.rows.length - 7), ui.scroll + dy));
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
        if (m.screen !== screen) { ui.focus = -1; ui.pop = ""; ui.offer = null; ui.scroll = 0; ui.pslot = -1; ui.bankTab = ""; ui.plot = -1; ui.marketTab = "sells"; ui.hand = -1; }
        screen = m.screen;
        if (m.seg) { if (m.seg !== seg) ui.scroll = 0; seg = m.seg; ui.asked = false; ui.askedHouse = false; ui.pop = ""; }
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
