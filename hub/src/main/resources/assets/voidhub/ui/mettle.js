// VOID Mettle in the hub: the Gear page, the Classes page (overview and skill trees), the
// inventory's LOAD row. Loaded before hub.js, which calls these hooks:
//
//   hubMettle.layout(l)   every layout message (page switch: kind "gear" / "classes")
//   hubMettle.state(s)    every state message (s.mettle: Mettle is installed; s.inactive: pockets off)
//   hubMettle.apply(m)    {type: "mettle", state} and {type: "icons", map}
//
// Mettle's state message is mettle/docs/OVERLAY.md "the mettle state". The Classes page is
// rebuilt only when that state or the selection changes; nothing here animates.
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var ICONS = {};
  var M = null;          // the last mettle state
  var kind = "inventory";
  var view = {skill: null, perk: null, tab: 0};
  var built = "";        // what the Classes page last drew (state + selection)

  function send(msg) {
    try {
      if (typeof window.__void_native === "function") return window.__void_native(JSON.stringify(msg));
    } catch (e) { /* never throw into the host */ }
    return null;
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function text(el, s) { if (el && el.textContent !== s) el.textContent = s; }
  function toggle(el, cls, on) { if (el && el.classList.contains(cls) !== on) el.classList.toggle(cls, on); }

  // A pixel-art texture from Mettle at w x h CSS px (nearest-neighbour), or nothing until icons arrive.
  function img(id, w, h) {
    var url = ICONS[id];
    return url ? '<img class="m-img" src="' + url + '" style="width:' + w + "px;height:" + (h || w) + 'px">' : "";
  }

  // A row of n cells, the first `lit` on and the last of those live (violet).
  function cells(n, lit, live, w, h, gap, x, y) {
    var s = '<div class="m-cells" style="left:' + x + "px;top:" + y + "px;width:" + (n * (w + gap) - gap) + "px;height:" + h + 'px">';
    for (var i = 0; i < n; i++) {
      var c = live && i === lit - 1 ? "live" : i < lit ? "on" : "";
      s += '<i class="' + c + '" style="left:' + i * (w + gap) + "px;width:" + w + "px;height:" + h + 'px"></i>';
    }
    return s + "</div>";
  }

  function upper(s) { return String(s || "").toUpperCase(); }
  function fmt(n) { return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ","); }
  function kg(n) { return (Math.round(n * 10) / 10).toFixed(1); }

  // ---- static parts, added once ------------------------------------------------------------------
  function build() {
    var win = $("win");
    var segs = $("segments");
    var classes = segs.querySelector('[data-seg="classes"]');
    var gear = document.createElement("div");
    gear.className = "segment seg-mettle";
    gear.setAttribute("data-seg", "gear");
    gear.innerHTML = 'Gear <span class="count" id="seg-gear-count">0</span>';
    segs.insertBefore(gear, classes);
    toggle(classes, "idle", false);
    var dot = document.createElement("span");
    dot.className = "pts m-only";
    dot.id = "seg-classes-pts";
    dot.innerHTML = "<b></b><span>0</span>";
    classes.appendChild(dot);

    var g = document.createElement("div");
    g.className = "page-gear";
    g.innerHTML =
      '<div class="m-source"><span class="modmark big"><i></i><i></i><i></i><i></i><i></i><i></i></span>from Mettle</div>' +
      '<div class="eyebrow" id="g-pack-eb" style="left:28px;top:109px">PACK</div>' +
      '<div class="actions" style="right:497px;top:100px"><div class="action" data-move="inventory"><span class="moveicon"><i></i><i></i><i></i><i></i><i></i><i></i></span>Move all to inventory</div>' +
      '<div class="action" data-sort="container"><span class="sorticon"><i></i><i></i><i></i></span>Sort</div></div>' +
      '<div class="m-eb m-ink3 g-hint" id="g-hint"></div>' +
      '<div class="eyebrow" style="left:28px;top:309px">INVENTORY</div>' +
      '<div class="actions" style="right:497px;top:300px"><div class="action" data-move="container"><span class="moveicon"><i></i><i></i><i></i><i></i><i></i><i></i></span>Move all to pack</div>' +
      '<div class="action" data-sort="storage"><span class="sorticon"><i></i><i></i><i></i></span>Sort</div></div>' +
      '<div class="m-card" id="g-load" style="left:514px;top:100px;width:445px;height:97px"></div>' +
      '<div class="m-card" id="g-worn" style="left:514px;top:209px;width:445px;height:212px"></div>';
    win.appendChild(g);

    var c = document.createElement("div");
    c.className = "page-classes";
    c.id = "classes";
    win.appendChild(c);

  }

  var BANDS = {light: "MOVING FREELY", burdened: "BURDENED", overburdened: "OVERBURDENED", pinned: "PINNED"};

  // ---- inventory: the Status card's load (armour and level are the hub's) -------------------------
  var last = {};
  function readout(s) {
    var el = $("status-load");
    if (!el || !M || !M.burden || M.burden.capacity == null) return;
    var b = M.burden;
    var cap = Math.max(1, b.capacity);
    var key = JSON.stringify(b);
    if (last.readout === key) return;
    last.readout = key;
    var lit = Math.min(32, Math.round(b.load / cap * 26));
    var h = '<div style="left:0;top:0"><span class="m-eb">LOAD</span></div>';
    h += '<div class="m-tag" style="right:0;top:-3px">' + (b.exempt ? "CREATIVE" : BANDS[b.band] || "") + "</div>";
    h += '<div style="left:0;top:20px"><span class="m-h2">' + kg(b.load) + '</span><span class="m-num">  of ' + Math.round(cap) + " kg</span></div>";
    h += cells(32, lit, true, 9, 10, 3.25, 0, 56);
    h += '<div class="m-eb m-ink3" style="left:0;top:76px">' + (b.heaviest ? esc(upper("Heaviest: " + b.heaviest.text)) : "NOTHING HEAVY") + "</div>";
    el.innerHTML = h;
  }

  // ---- Gear page ---------------------------------------------------------------------------------

  function gearCards() {
    if (!M || !M.gear) return;
    var key = JSON.stringify([M.burden, M.gear]);
    if (last.gear === key) return;
    last.gear = key;
    var b = M.burden || {};
    var cap = Math.max(1, b.capacity || 1);
    // 32 cells are the capacity; the meter runs on to 125 % (Pinned).
    var lit = Math.min(41, Math.round((b.load || 0) / cap * 32));
    var h = '<div style="left:18px;top:14px"><span class="m-h2">' + kg(b.load || 0) + '</span><span class="m-num">  of ' + Math.round(cap) + " kg</span></div>";
    h += '<div class="m-tag" style="right:18px;top:18px">' + (b.exempt ? "CREATIVE" : BANDS[b.band] || "") + "</div>";
    h += cells(41, lit, true, 7, 8, 3, 18, 56);
    var marks = [[b.burdenedAt, "BURDENED"], [b.overburdenedAt, "OVERBURDENED"], [b.pinnedAt, "PINNED"]];
    for (var i = 0; i < marks.length; i++) {
      if (!(marks[i][0] > 0)) continue;
      var x = 18 + Math.round(marks[i][0] * 32) * 10 - 2;
      if (x > 430) continue;
      h += '<div class="g-mark" style="left:' + x + 'px;top:52px"></div><div class="m-eb tiny" style="left:' + (x - 30) + 'px;top:72px">' + marks[i][1] + "</div>";
    }
    $("g-load").innerHTML = h;

    var g = M.gear;
    var w = '<div class="m-row" style="left:18px;right:18px;top:15px"><span class="m-eb">WORN</span><span class="m-eb m-ink3">' +
      (b.heaviest ? esc(upper("Heaviest: " + b.heaviest.text)) : "") + "</span></div>";
    var rows = [["back", "Back", "NOTHING ON YOUR BACK"], ["belt", "Belt", "NO BELT"], ["quiver", "Quiver", "NO QUIVER"]];
    for (var r = 0; r < 3; r++) {
      var it = g[rows[r][0]];
      var y = 48 + r * 56;
      w += '<div style="left:72px;top:' + y + 'px"><div class="g-name">' + esc(it ? it.name : rows[r][1]) + "</div>" +
        '<div class="m-eb m-ink3" style="margin-top:4px">' + (it ? (r === 0 ? it.pockets + " POCKETS  ·  " + it.used + " USED" : it.pockets + " POCKETS") : rows[r][2]) + "</div></div>";
    }
    $("g-worn").innerHTML = w;
    var back = g.back;
    text($("g-pack-eb"), back ? upper(back.name) + "  ·  " + back.pockets + " POCKETS" : "NO PACK WORN");
    text($("g-hint"), !back ? "WEAR A SATCHEL OR PACK TO CARRY MORE" : back.pockets < 27 ? "A BIGGER PACK ADDS MORE ROWS" : "");
  }

  function gearCount() {
    var back = M && M.gear ? M.gear.back : null;
    text($("seg-gear-count"), String(back ? back.pockets : 0));
  }

  // ---- Classes page: the gallery and the skill tree, each skill in its own colour (Figma 468:9108, O) -
  // Hue = which skill (from Mettle's skill data); glow = how far you have come (locked dark, available
  // ringed, learned lit); gold-champagne = earned perks. The gallery shows all skills; a card opens its
  // tree: a rail of skills, the tree, and the selected perk in an action bar along the bottom.
  var ROMAN = ["", "I", "II", "III", "IV"];
  var KIND = {root: "ROOT", path: "PATH", mastery: "MASTERY", feat: "FEAT", synergy: "SYNERGY", teaching: "TEACHING"};
  var CHAMP = "#E8DDBC", PANEL = "#191A1C", WELL = "#131315";

  function rgbOf(h) { h = h.replace("#", ""); return [parseInt(h.substr(0, 2), 16), parseInt(h.substr(2, 2), 16), parseInt(h.substr(4, 2), 16)]; }
  function rgba(h, a) { var c = rgbOf(h); return "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + a + ")"; }
  function mix(a, b, t) {
    var A = rgbOf(a), B = rgbOf(b), s = "#";
    for (var i = 0; i < 3; i++) { var v = Math.round(A[i] + (B[i] - A[i]) * t); s += (v < 16 ? "0" : "") + v.toString(16); }
    return s;
  }
  function hueOf(s) { return s && s.colour ? s.colour : "#9A9DA1"; }
  function lightOf(h) { return mix(h, "#FFFFFF", 0.42); }

  function bg() {
    if (!M || !M.character) return null;
    for (var i = 0; i < M.backgrounds.length; i++) if (M.backgrounds[i].id === M.character.background) return M.backgrounds[i];
    return null;
  }
  function skillsShown() {
    var out = [];
    for (var i = 0; i < M.skills.length; i++) if (M.skills[i].released) out.push(M.skills[i]);
    return out;
  }
  function skillById(id) {
    for (var i = 0; i < M.skills.length; i++) if (M.skills[i].id === id) return M.skills[i];
    return null;
  }
  function attrName(id) {
    for (var i = 0; i < M.attributes.length; i++) if (M.attributes[i].id === id) return M.attributes[i].name;
    return id;
  }
  function findPerk(id) {
    for (var i = 0; i < M.perks.length; i++) if (M.perks[i].id === id) return M.perks[i];
    return null;
  }
  function frac(s) { return s.level >= 20 ? 1 : Math.max(0, Math.min(1, (s.xp - s.levelXp) / Math.max(1, s.nextXp - s.levelXp))); }
  function earnedKind(p) { return p.kind === "feat" || p.kind === "synergy" || p.kind === "teaching"; }
  function buyable(p) { return !earnedKind(p) && !p.locked && p.rank < p.maxRank; }
  function perksOf(skill) {
    var out = [];
    for (var i = 0; i < M.perks.length; i++) {
      var p = M.perks[i];
      if (p.skill === skill && p.id.indexOf(":general/") < 0) out.push(p);
    }
    out.sort(function (a, b) { return a.order - b.order; });
    return out;
  }

  // A row of coloured cells: lit ones in the hue with a glow, the current one brighter.
  function hcells(n, lit, w, h, gap, hue) {
    var light = lightOf(hue), s = '<span class="h-cells">';
    for (var i = 0; i < n; i++) {
      var st = i === lit - 1 ? "background:" + light + ";box-shadow:0 0 8px " + rgba(hue, 0.85)
        : i < lit ? "background:" + hue + ";box-shadow:0 0 5px " + rgba(hue, 0.45) : "background:" + mix(WELL, hue, 0.3);
      s += '<i style="width:' + w + "px;height:" + h + "px;margin-right:" + gap + "px;" + st + '"></i>';
    }
    return s + "</span>";
  }

  // ---- the background picker (first join, or an operator's reset) ---------------------------------------
  // Nine backgrounds as cards on the left, the chosen one in full on the right with Begin. A background's
  // accent is its first favoured skill's colour. The Wanderer places free points and picks skills to favour.
  var pick = {bg: null, spent: {}, fav: []};

  function bgAccent(b) {
    var s = b.favoured && b.favoured.length ? skillById(b.favoured[0]) : null;
    return s ? hueOf(s) : CHAMP;
  }

  function picker() {
    var list = M.backgrounds, h = "";
    if (!pick.bg || !findBg(pick.bg)) pick.bg = list.length ? list[0].id : null;
    var b = findBg(pick.bg);
    h += '<div style="left:28px;top:96px"><div class="m-eb">' + (M.pickerByOperator ? "AN OPERATOR RESET YOUR BACKGROUND  ·  YOUR SKILLS STAY" : "A NEW CHARACTER") +
      '</div><div class="m-h1" style="margin-top:4px">Choose your background</div><div class="m-eb m-ink2" style="margin-top:5px">EVERY SKILL CAN BE LEARNED. THIS IS ONLY WHERE YOU START.</div></div>';
    for (var i = 0; i < list.length && i < 9; i++) {
      var g = list[i], hue = bgAccent(g), on = g.id === pick.bg;
      var st = "left:" + (28 + (i % 3) * 156) + "px;top:" + (166 + Math.floor(i / 3) * 132) + "px;background:" + mix(PANEL, hue, on ? 0.10 : 0.04) + ";border:" +
        (on ? "2px solid " + hue + ";box-shadow:0 0 0 3px " + rgba(hue, 0.12) + ", 0 0 24px " + rgba(hue, 0.4) : "1px solid " + rgba(hue, 0.25));
      h += '<div class="p-card" data-bg="' + g.id + '" style="' + st + '"><div class="p-art" style="background:' + mix(WELL, hue, 0.08) + '">' + img(g.art, 96, 48) + "</div>";
      h += '<div class="m-h4" style="left:12px;top:72px">' + esc(g.name) + '</div><div class="m-eb" style="left:12px;top:92px;color:' + rgba(hue, 0.75) + '">' + esc(upper(bonusText(g))) + "</div></div>";
    }
    if (!b) return h;
    var hue = bgAccent(b), light = lightOf(hue);
    h += '<div class="p-panel" style="background:' + mix(PANEL, hue, 0.05) + ";border:1px solid " + rgba(hue, 0.3) + '">';
    h += '<div class="p-big" style="background:' + mix(WELL, hue, 0.10) + '"><div class="o-halo" style="left:46px;top:-30px;width:180px;height:180px;border-radius:90px;background:radial-gradient(circle,' +
      rgba(hue, 0.35) + " 0%," + rgba(hue, 0) + ' 70%)"></div><div style="left:40px;top:8px">' + img(b.art, 192, 96) + "</div></div>";
    h += '<div style="left:292px;top:20px;right:18px"><div class="m-h1">' + esc(b.name) + '</div><div class="p-tag">' + esc(b.tagline) + "</div></div>";
    var y = 130;
    if (b.freePoints > 0 || b.freeFavoured > 0) {
      h += wandererControls(b, hue, y);
    } else {
      h += '<div class="m-eb" style="left:18px;top:' + y + 'px">STARTS WITH</div><div class="p-chips" style="top:' + (y + 16) + 'px">';
      for (var a in b.bonus) h += '<span class="p-chip" style="border-color:' + rgba(hue, 0.4) + '">' + esc(attrName(a)) + " +" + b.bonus[a] + "</span>";
      h += "</div>";
      h += '<div class="m-eb" style="left:18px;top:' + (y + 54) + 'px">FAVOURED SKILLS  ·  +25 % XP</div><div class="p-chips" style="top:' + (y + 70) + 'px">';
      for (var f = 0; f < b.favoured.length; f++) {
        var fs = skillById(b.favoured[f]);
        if (fs) h += '<span class="p-chip" style="color:' + lightOf(hueOf(fs)) + ";border-color:" + rgba(hueOf(fs), 0.55) + ";background:" + mix(WELL, hueOf(fs), 0.15) + '">' + img(fs.icon, 16) + esc(fs.name) + "</span>";
      }
      h += "</div>";
      if (b.signature) {
        h += '<div class="p-sig" style="top:' + (y + 108) + "px;border-color:" + rgba(CHAMP, 0.5) + '"><div class="m-eb" style="color:' + CHAMP + '">SIGNATURE PERK  ·  ALWAYS YOURS</div><div class="m-h4" style="margin-top:4px">' +
          esc(b.signature.name) + '</div><div class="p-desc">' + esc(b.signature.description) + "</div></div>";
      }
    }
    h += '<div class="m-eb" style="left:18px;bottom:72px">KIT  <span class="m-ink2">' + esc(upper(b.kitSummary || "")) + "</span></div>";
    var r = M.result && !M.result.ok && M.result.text ? M.result.text : "";
    var ready = pickerReady(b);
    h += r ? '<div class="p-err">' + esc(r) + "</div>" : "";
    h += '<div class="p-begin' + (ready ? "" : " off") + '"' + (ready ? ' data-begin="1" style="background:' + hue + ";box-shadow:0 0 22px " + rgba(hue, 0.5) + '"' : "") + ">" +
      (ready ? "Begin as " + esc(b.name) : "Place your points and favoured skills") + "</div>";
    h += "</div>";
    h += '<div class="footer" style="top:594px">CHOOSE ONCE  ·  AN OPERATOR CAN RESET IT  ·  SKILLS ARE NEVER LOST</div>';
    return h;
  }

  function findBg(id) {
    for (var i = 0; i < M.backgrounds.length; i++) if (M.backgrounds[i].id === id) return M.backgrounds[i];
    return null;
  }

  function bonusText(b) {
    if (b.freePoints > 0) return b.freePoints + " points to place";
    var out = [];
    for (var a in b.bonus) out.push(attrName(a).slice(0, 3) + " +" + b.bonus[a]);
    return out.join(" · ");
  }

  function spentTotal() { var n = 0; for (var a in pick.spent) n += pick.spent[a]; return n; }

  function pickerReady(b) {
    if (b.freePoints > 0 && spentTotal() !== b.freePoints) return false;
    if (b.freeFavoured > 0 && pick.fav.length !== b.freeFavoured) return false;
    return true;
  }

  function wandererControls(b, hue, y) {
    var h = '<div class="m-eb" style="left:18px;top:' + y + 'px">PLACE ' + b.freePoints + " POINTS  ·  " + (b.freePoints - spentTotal()) + " LEFT</div>";
    for (var i = 0; i < M.attributes.length; i++) {
      var a = M.attributes[i], v = 3 + (b.bonus[a.id] || 0) + (pick.spent[a.id] || 0);
      h += '<div class="p-step" style="left:' + (18 + i * 84) + "px;top:" + (y + 16) + 'px"><div class="m-eb">' + esc(upper(a.name).slice(0, 3)) + '</div><div class="m-h3">' + v + "</div>" +
        '<div class="p-pm" data-pm="' + a.id + ':-1">−</div><div class="p-pm" data-pm="' + a.id + ':1" style="left:44px">+</div></div>';
    }
    h += '<div class="m-eb" style="left:18px;top:' + (y + 86) + 'px">FAVOUR ' + b.freeFavoured + " SKILLS  ·  +25 % XP</div><div class=\"p-chips wrap\" style=\"top:" + (y + 102) + 'px">';
    var list = skillsShown();
    for (var k = 0; k < list.length; k++) {
      var s = list[k], on = pick.fav.indexOf(s.id) >= 0, sh = hueOf(s);
      h += '<span class="p-chip" data-fav="' + s.id + '" style="' + (on ? "color:" + lightOf(sh) + ";border-color:" + sh + ";background:" + mix(WELL, sh, 0.22) + ";box-shadow:0 0 10px " + rgba(sh, 0.35)
        : "border-color:" + rgba(sh, 0.3)) + '">' + img(s.icon, 16) + esc(s.name) + "</span>";
    }
    return h + "</div>";
  }

  // ---- the gallery ------------------------------------------------------------------------------------
  function gallery() {
    var c = M.character, b = bg();
    var h = '<div class="m-source"><span class="modmark big"><i></i><i></i><i></i><i></i><i></i><i></i></span>from Mettle</div>';
    if (M.phase !== "chosen" || !b) {
      return picker();
    }
    // Character strip: background, level, the five attributes (+ where points wait).
    h += '<div class="o-strip"><div class="o-art">' + img(b.art, 96, 48) + "</div>";
    h += '<div style="left:122px;top:9px"><div class="m-eb">BACKGROUND</div><div class="m-h3" style="margin-top:3px">' + esc(b.name) + "</div></div>";
    h += '<div style="left:212px;top:9px"><div class="m-eb">CHARACTER LEVEL</div><div class="o-lv">' + c.level + "</div></div>";
    h += '<div class="o-sep" style="left:326px"></div>';
    for (var k = 0; k < M.attributes.length; k++) {
      var at = M.attributes[k];
      h += '<div class="o-at" style="left:' + (338 + k * 94) + 'px" title="' + esc(at.perPoint) + '"><div class="m-eb" style="left:10px;top:6px">' + esc(upper(at.name).slice(0, 3)) +
        '</div><div class="o-av">' + c.attributes[at.id] + "</div>" + (c.points > 0 && c.attributes[at.id] < c.maxAttribute ? '<div class="o-plus" data-attr="' + at.id + '">+</div>' : "") + "</div>";
    }
    if (c.points > 0) h += '<div class="pts big" style="right:12px;top:11px"><b></b>' + c.points + " to place</div>";
    h += "</div>";
    // The cards.
    var list = skillsShown();
    for (var i = 0; i < list.length && i < 8; i++) h += skillCard(list[i], 28 + (i % 4) * 237, 156 + Math.floor(i / 4) * 216);
    var learned = 0, earned = 0;
    for (var p = 0; p < M.perks.length; p++) if (M.perks[p].rank > 0) earnedKind(M.perks[p]) ? earned++ : learned++;
    h += '<div class="footer" style="top:594px">' + list.length + " SKILLS  ·  " + learned + (learned === 1 ? " PERK" : " PERKS") + "  ·  " + earned + " EARNED  ·  GLOWING CARDS HAVE POINTS TO SPEND</div>";
    h += '<div class="footer right" style="top:594px">CLICK A CARD TO OPEN IT</div>';
    return h;
  }

  function skillCard(s, x, y) {
    var hue = hueOf(s), light = lightOf(hue), pts = s.points > 0;
    var style = "left:" + x + "px;top:" + y + "px;background:" + mix(PANEL, hue, pts ? 0.075 : 0.05) + ";border:1.5px solid " + rgba(hue, pts ? 0.85 : 0.28) + ";" +
      (pts ? "box-shadow:0 0 0 3px " + rgba(hue, 0.10) + ", 0 0 26px " + rgba(hue, 0.34) + ", inset 0 0 34px " + rgba(hue, 0.07) : "box-shadow:inset 0 0 26px " + rgba(hue, 0.04)) + ";";
    var h = '<div class="o-card" data-skill="' + s.id + '" style="' + style + '">';
    h += '<div class="o-plate" style="background:' + mix(WELL, hue, 0.09) + '">';
    h += '<div class="o-halo" style="background:radial-gradient(circle,' + rgba(hue, 0.5) + " 0%," + rgba(hue, 0.16) + " 45%," + rgba(hue, 0) + ' 72%)"></div>';
    h += '<div style="left:12px;top:9px">' + img(s.icon, 64) + "</div>";
    h += '<div class="o-num" style="color:' + light + '">' + s.level + '</div><div class="m-eb" style="right:14px;top:56px;color:' + rgba(hue, 0.7) + '">LEVEL</div></div>';
    h += '<div class="m-h3" style="left:14px;top:100px">' + esc(s.name) + "</div>";
    if (pts) h += '<div class="o-pill" style="right:12px;top:101px;background:' + mix(WELL, hue, 0.22) + ";border:1px solid " + rgba(hue, 0.6) + ";color:" + light + ";box-shadow:0 0 10px " + rgba(hue, 0.35) +
      '"><b style="background:' + hue + '"></b>' + s.points + "</div>";
    h += '<div class="o-title" style="color:' + mix("#9A9DA1", hue, 0.35) + '">' + esc(s.level > 0 ? s.title : "Untried") + "</div>";
    h += '<div style="left:14px;top:144px">' + hcells(16, s.level >= 20 ? 16 : Math.max(1, Math.round(frac(s) * 16)), 10, 6, 2, hue) + "</div>";
    h += '<div class="o-how' + (s.level === 0 ? " always" : "") + '" style="color:' + mix("#B5B8BC", hue, 0.25) + '"><span class="m-eb" style="color:' + rgba(hue, 0.85) + '">HOW IT LEVELS</span><br>' + esc(s.levelsBy || "") + "</div>";
    if (s.level > 0) h += '<div class="m-eb o-xpt" style="left:14px;top:160px;color:' + mix("#626569", hue, 0.3) + '">' + (s.level >= 20 ? "MASTERED" : fmt(s.xp) + " / " + fmt(s.nextXp) + " XP") + "</div>";
    // The perks learned so far, then earned ones in champagne.
    var ps = perksOf(s.id), row = "", shown = 0, extra = 0;
    for (var i = 0; i < ps.length; i++) {
      var p = ps[i];
      if (p.rank <= 0) continue;
      if (shown < 6) {
        row += earnedKind(p) ? '<div class="o-perk" style="background:#17150F;border:1.5px solid ' + rgba(CHAMP, 0.8) + '">' + img(p.icon, 20) + "</div>"
          : '<div class="o-perk" style="background:' + mix(WELL, hue, 0.16) + ";border:1px solid " + rgba(hue, 0.38) + '">' + img(p.icon, 20) + "</div>";
        shown++;
      } else {
        extra++;
      }
    }
    if (extra) row += '<div class="o-more">+' + extra + "</div>";
    if (!shown) row = '<span class="m-eb">NO PERKS YET</span>';
    if (s.level === 0) row = "";
    h += '<div class="o-perks">' + row + "</div>";
    return h + "</div>";
  }

  // ---- the tree ----------------------------------------------------------------------------------------
  var TOP = {4: 49, 3: 164, 2: 234, 1: 304};

  function layoutTree(s) {
    var ps = perksOf(s.id), g = {root: [], a2: [], b2: [], a3: [], b3: [], mastery: [], earned: []};
    for (var i = 0; i < ps.length; i++) {
      var p = ps[i];
      if (p.kind === "root") g.root.push(p);
      else if (p.kind === "path") (g[(p.path === "b" ? "b" : "a") + (p.tier >= 3 ? 3 : 2)]).push(p);
      else if (p.kind === "mastery") g.mastery.push(p);
      else g.earned.push(p);
    }
    // Masteries left to right: path A's, either path's, path B's.
    var rank = {a: 0, any: 1, b: 2};
    g.mastery.sort(function (x, y) { return (rank[x.side] || 1) - (rank[y.side] || 1); });
    g.earned.sort(function (x, y) { return ({teaching: 0, synergy: 1, feat: 2}[x.kind]) - ({teaching: 0, synergy: 1, feat: 2}[y.kind]); });
    var pos = {};
    function put(p, x, t) { if (p) pos[p.id] = {x: x, y: TOP[t], p: p}; }
    put(g.root[0], 277, 1); put(g.root[1], 603, 1);
    put(g.a2[0], 207, 2); put(g.a2[1], 347, 2); put(g.b2[0], 533, 2); put(g.b2[1], 673, 2);
    put(g.a3[0], 207, 3); put(g.a3[1], 347, 3); put(g.b3[0], 533, 3); put(g.b3[1], 673, 3);
    put(g.mastery[0], 277, 4); put(g.mastery[1], 440, 4); put(g.mastery[2], 603, 4);
    var links = [];
    function link(a, b) { if (a && b) links.push([a, b]); }
    link(g.root[0], g.a2[0]); link(g.root[0], g.a2[1]); link(g.root[1], g.b2[0]); link(g.root[1], g.b2[1]);
    link(g.a2[0], g.a3[0]); link(g.a2[1], g.a3[1]); link(g.b2[0], g.b3[0]); link(g.b2[1], g.b3[1]);
    link(g.a3[0], g.mastery[0]); link(g.a3[1], g.mastery[1]); link(g.b3[0], g.mastery[1]); link(g.b3[1], g.mastery[2]);
    return {g: g, pos: pos, links: links};
  }

  function stateOf(p) {
    if (earnedKind(p)) return p.rank > 0 ? "earned" : "hidden";
    if (p.rank > 0) return "learned";
    if (!p.locked) return "open";
    return p.kind === "mastery" ? "mastery" : "locked";
  }

  function tile(p, x, y, hue, sel, big) {
    var st = stateOf(p), light = lightOf(hue), pips = p.maxRank > 1 && !earnedKind(p);
    var size = 56, style = "", glow = 0, ga = 0;
    if (st === "learned") {
      style = "background:radial-gradient(circle at 50% 45%," + rgba(hue, 0.38) + " 0%," + rgba(hue, 0.10) + " 70%)," + mix("#1B1710", hue, 0.12) + ";border:2px solid " + (sel ? light : hue) + ";";
      glow = sel ? 22 : 16; ga = 0.5;
    } else if (st === "open") {
      style = "background:radial-gradient(circle at 50% 45%," + rgba(hue, sel ? 0.34 : 0.18) + " 0%," + rgba(hue, 0.04) + " 70%)," + mix("#17150F", hue, 0.04) + ";border:" + (sel ? "2px solid " + light : "1.5px solid " + rgba(hue, 0.85)) + ";";
      glow = sel ? 22 : 10; ga = sel ? 0.6 : 0.28;
    } else if (sel) {
      style = "border:2px solid #EDEEEF;";
    }
    var h = '<div class="t-w" data-perk="' + p.id + '" style="left:' + (x - 28) + "px;top:" + y + 'px">';
    if (glow) h += halo(hue, ga, glow, 56, 14);
    if (st === "open" || (st === "learned" && buyable(p))) {
      var o1 = sel ? 6 : 5, o2 = sel ? 11 : 9;
      h += '<div class="t-ring" style="left:-' + o1 + "px;top:-" + o1 + "px;width:" + (56 + 2 * o1) + "px;height:" + (56 + 2 * o1) + "px;border:1.5px solid " + rgba(hue, sel ? 0.55 : 0.42) + ";border-radius:" + (14 + o1) + 'px"></div>';
      h += '<div class="t-ring" style="left:-' + o2 + "px;top:-" + o2 + "px;width:" + (56 + 2 * o2) + "px;height:" + (56 + 2 * o2) + "px;border:1px solid " + rgba(hue, sel ? 0.22 : 0.16) + ";border-radius:" + (14 + o2) + 'px"></div>';
    }
    h += '<div class="t-tile ' + st + '" style="' + style + (pips ? "padding-bottom:10px;" : "") + '">';
    h += st === "hidden" ? '<span class="m-q">?</span>' : img(p.icon, pips ? 34 : 36);
    if (buyable(p)) h += '<div class="t-dot" style="background:' + (sel ? light : hue) + ";box-shadow:0 0 6px " + rgba(hue, 0.8) + '"></div>';
    if (pips) {
      h += '<div class="t-pips">';
      for (var k = 0; k < p.maxRank; k++) h += '<i style="' + (k < p.rank ? "background:" + hue + ";box-shadow:0 0 5px " + rgba(hue, 0.7) : "background:" + mix("#1B1710", hue, 0.25)) + '"></i>';
      h += "</div>";
    }
    return h + "</div></div>";
  }

  // A glow as a gradient behind the box: Ultralight blurs a box-shadow on every repaint it touches
  // (a perk click repainted ~50 ms of blur); a gradient is a few ms. Looks the same at these sizes.
  function halo(hue, a, r, size, radius) {
    var d = size + 2 * r;
    return '<div class="t-halo" style="left:-' + r + "px;top:-" + r + "px;width:" + d + "px;height:" + d + "px;border-radius:" + (radius + r) +
      "px;background:radial-gradient(closest-side," + rgba(hue, a) + " " + Math.round(size / d * 100) + "%," + rgba(hue, a * 0.35) + " " + Math.round((size / d * 100 + 100) / 2) + "%," + rgba(hue, 0) + ' 100%)"></div>';
  }

  function gateText(p) {
    var s = skillById(p.skill), t = M.tiers[p.tier - 1];
    if (!s || !t) return p.locked;
    if (s.level < t.skillLevel) return "Needs " + s.name + " " + t.skillLevel;
    if (M.character.attributes[s.attribute] < t.attribute) return "Needs " + attrName(s.attribute) + " " + t.attribute;
    return p.locked;
  }

  function rail(cur) {
    var list = skillsShown(), h = '<div class="o-rail">';
    for (var i = 0; i < list.length; i++) {
      var s = list[i], hue = hueOf(s), on = s.id === cur.id;
      var st = on ? "background:" + mix(WELL, hue, 0.22) + ";border:2px solid " + hue + ";box-shadow:0 0 18px " + rgba(hue, 0.55)
        : "background:" + mix(WELL, hue, 0.10) + ";border:1px solid " + rgba(hue, 0.32);
      h += '<div class="o-rb" data-skill="' + s.id + '" style="' + st + '">' + img(s.icon, 28) + '<div class="o-lvb" style="background:' + mix("#1B1C1E", hue, 0.3) + ";border:1px solid " + rgba(hue, 0.5) +
        ";color:" + lightOf(hue) + '">' + s.level + "</div>" + (s.points > 0 ? '<div class="o-vd" style="background:' + hue + ";box-shadow:0 0 6px " + rgba(hue, 0.8) + '"></div>' : "") + "</div>";
    }
    return h + "</div>";
  }

  function tree() {
    var s = skillById(view.skill);
    if (!s) { view.skill = null; return gallery(); }
    var hue = hueOf(s), light = lightOf(hue), L = layoutTree(s), g = L.g;
    if (!view.perk || !L.pos[view.perk] && indexOf(g.earned, view.perk) < 0) {
      view.perk = null;
      var order = g.root.concat(g.a2, g.b2, g.a3, g.b3, g.mastery);
      for (var i = 0; i < order.length; i++) if (buyable(order[i])) { view.perk = order[i].id; break; }
      if (!view.perk && order.length) view.perk = order[0].id;
    }
    var h = '<div class="m-source back" data-back="1">‹  All skills</div>' + rail(s);
    // The tree panel.
    h += '<div class="o-panel" style="background:' + mix(PANEL, hue, 0.03) + ";border:1px solid " + rgba(hue, 0.2) + '">';
    h += '<div class="o-atmo" style="background:radial-gradient(ellipse,' + rgba(hue, 0.10) + " 0%," + rgba(hue, 0) + ' 70%)"></div>';
    h += '<div class="m-row" style="left:18px;top:7px;height:30px;gap:12px;justify-content:flex-start"><div class="m-h1">' + esc(s.name) + '</div><div class="m-eb" style="color:' + rgba(hue, 0.78) + '">' +
      esc(upper(s.title)) + "  ·  LEVEL " + s.level + "</div></div>";
    h += '<div class="m-row" style="right:16px;top:7px;height:30px;gap:12px"><span class="m-eb">' + (s.level >= 20 ? "MASTERED" : fmt(s.xp) + " / " + fmt(s.nextXp) + " XP TO " + (s.level + 1)) + "</span>" +
      hcells(20, s.level >= 20 ? 20 : Math.max(1, Math.round(frac(s) * 20)), 9, 6, 3, hue) +
      (s.points > 0 ? '<div class="o-pill big" style="background:' + mix(WELL, hue, 0.2) + ";border:1px solid " + rgba(hue, 0.65) + ";color:" + light + ";box-shadow:0 0 14px " + rgba(hue, 0.32) + '"><b style="background:' + hue + '"></b>' +
        s.points + " point" + (s.points > 1 ? "s" : "") + "</div>" : "") + "</div>";
    var hb = "background:" + mix(WELL, hue, 0.025) + ";border:1px solid " + rgba(hue, 0.10);
    h += '<div class="o-half" style="left:124px;top:44px;width:632px;height:68px;' + hb + '"></div>';
    h += '<div class="o-half" style="left:124px;top:120px;width:306px;height:246px;' + hb + '"></div>';
    h += '<div class="o-half" style="left:450px;top:120px;width:306px;height:246px;' + hb + '"></div>';
    h += '<div class="o-half" style="left:768px;top:44px;width:67px;height:322px"></div>';
    var attr = M.character.attributes[s.attribute];
    for (var t = 4; t >= 1; t--) {
      var gate = M.tiers[t - 1], reached = s.level >= gate.skillLevel && attr >= gate.attribute;
      var gl = upper(s.name) + " " + gate.skillLevel + (gate.attribute ? "<br>" + upper(attrName(s.attribute)) + " " + gate.attribute : "");
      h += '<div class="o-tier" style="top:' + (TOP[t] + 28 - (gate.attribute ? 24 : 18)) + 'px"><div class="m-h2" style="line-height:18px;color:' + (reached ? hue : "#4A4C50") + '">' + ROMAN[t] +
        '</div><div class="m-eb" style="margin-top:3px;line-height:11px">' + gl + "</div></div>";
    }
    h += '<div class="m-eb" style="left:140px;top:49px;color:#7A7C80">MASTERY  ·  PICK ONE</div>';
    var pa = 0, pb = 0;
    for (var q = 0; q < 2; q++) { if (g.a2[q] && g.a2[q].rank > 0) pa++; if (g.a3[q] && g.a3[q].rank > 0) pa++; if (g.b2[q] && g.b2[q].rank > 0) pb++; if (g.b3[q] && g.b3[q].rank > 0) pb++; }
    h += '<div style="left:142px;top:132px"><div class="m-h4">' + esc(s.paths.a || "") + '</div><div class="m-eb" style="margin-top:3px">' + (pa ? '<span style="color:' + hue + '">' + pa + " / 4</span>" : "0 / 4") + "</div></div>";
    h += '<div style="left:468px;top:132px"><div class="m-h4">' + esc(s.paths.b || "") + '</div><div class="m-eb" style="margin-top:3px">' + (pb ? '<span style="color:' + hue + '">' + pb + " / 4</span>" : "0 / 4") + "</div></div>";
    // Links: lit where both ends are learned, live where the parent is learned, dashed otherwise.
    var svg = '<svg class="o-svg" width="851" height="372">', lit = "", live = "", idle = "";
    for (var k = 0; k < L.links.length; k++) {
      var a = L.links[k][0], c = L.links[k][1], A = L.pos[a.id], C = L.pos[c.id];
      if (!A || !C) continue;
      var ay = A.y, cy = C.y + 56, ym = Math.round((ay + cy) / 2);
      var d = A.x === C.x ? "M" + A.x + " " + ay + " V" + cy : "M" + A.x + " " + ay + " V" + ym + " H" + C.x + " V" + cy;
      if (a.rank > 0 && c.rank > 0) {
        lit += '<path d="' + d + '" stroke="' + hue + '" stroke-opacity="0.2" stroke-width="9" fill="none" stroke-linejoin="round" stroke-dasharray="none"/>' +
          '<path d="' + d + '" stroke="' + hue + '" stroke-opacity="0.35" stroke-width="5" fill="none" stroke-linejoin="round" stroke-dasharray="none"/>' +
          '<path d="' + d + '" stroke="' + light + '" stroke-width="2.5" fill="none" stroke-linejoin="round" stroke-dasharray="none"/>';
      } else if (a.rank > 0) {
        live += '<path d="' + d + '" stroke="' + hue + '" stroke-opacity="0.12" stroke-width="6" fill="none" stroke-linejoin="round" stroke-dasharray="none"/>' +
          '<path d="' + d + '" stroke="' + hue + '" stroke-opacity="0.7" stroke-width="2" fill="none" stroke-linejoin="round" stroke-dasharray="none"/>';
      } else {
        idle += '<path d="' + d + '" stroke="#2E3034" stroke-width="2" fill="none" stroke-linejoin="round" stroke-dasharray="4 5"/>';
      }
    }
    h += svg + lit + live + idle + "</svg>";
    drawn = {skill: s.id, hue: hue, at: {}};
    for (var id in L.pos) {
      drawn.at[id] = {x: L.pos[id].x, y: L.pos[id].y, hue: hue};
      h += tile(L.pos[id].p, L.pos[id].x, L.pos[id].y, hue, view.perk === id);
    }
    // Earned column.
    h += '<div class="m-eb" style="left:768px;top:52px;width:67px;text-align:center;color:' + CHAMP + '">EARNED</div>';
    for (var e = 0; e < g.earned.length && e < 4; e++) {
      var ep = g.earned[e], ey = 74 + e * 72;
      drawn.at[ep.id] = {x: 801, y: ey, hue: CHAMP};
      h += tile(ep, 801, ey, CHAMP, view.perk === ep.id);
      h += '<div class="m-eb" style="left:768px;top:' + (ey + 61) + 'px;width:67px;text-align:center;' + (ep.rank > 0 ? "color:" + CHAMP : "") + '">' + KIND[ep.kind] + "</div>";
    }
    h += "</div>";
    h += actionBar(findPerk(view.perk), s, hue);
    var spent = 0;
    for (var z = 0; z < M.perks.length; z++) if (M.perks[z].skill === s.id && !earnedKind(M.perks[z])) spent += M.perks[z].rank * M.perks[z].cost;
    h += '<div class="footer" style="top:594px">' + spent + (spent === 1 ? " POINT SPENT  ·  " : " POINTS SPENT  ·  ") + (s.points ? '<span style="color:' + light + '">' + s.points + " LEFT</span>" : "0 LEFT") +
      "  ·  " + esc(upper(s.paths.a || "")) + " " + pa + " / 4  ·  " + esc(upper(s.paths.b || "")) + " " + pb + " / 4</div>";
    h += '<div class="footer right" style="top:594px">LEVELS BY  ·  ' + esc(upper(s.levelsBy || "")) + "</div>";
    return h;
  }

  function indexOf(list, id) {
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return i;
    return -1;
  }

  function actionBar(p, s, hue) {
    if (!p) return "";
    var light = lightOf(hue), ph = earnedKind(p) ? CHAMP : hue, st = stateOf(p);
    var h = '<div class="o-bar" style="background:' + mix(PANEL, ph, 0.04) + ";border:1px solid " + rgba(ph, 0.30) + '">';
    h += '<div class="o-barglow" style="background:radial-gradient(ellipse,' + rgba(ph, 0.12) + " 0%," + rgba(ph, 0) + ' 70%)"></div>';
    h += tile(p, 46, 18, ph, true).replace('data-perk="' + p.id + '"', "");
    var label = KIND[p.kind] + (p.tier && !earnedKind(p) ? "  ·  TIER " + ROMAN[p.tier] : "") + (p.kind === "path" && s.paths[p.path] ? "  ·  " + upper(s.paths[p.path]) : "");
    var hidden = st === "hidden" && p.kind === "feat";
    h += '<div style="left:96px;top:13px;width:200px"><div class="m-eb" style="color:' + rgba(ph, 0.75) + '">' + label + '</div><div class="m-h2" style="margin-top:3px">' + esc(hidden ? "Hidden feat" : p.name) + "</div>";
    if (p.maxRank > 1 && !earnedKind(p)) {
      h += '<div class="m-row" style="margin-top:8px;gap:8px;justify-content:flex-start">' + hcells(p.maxRank, p.rank, 22, 6, 4, hue) + '<span class="m-eb" style="color:' + rgba(hue, 0.6) + '">RANK ' + p.rank + " / " + p.maxRank + "</span></div>";
    } else {
      h += '<div class="m-eb" style="margin-top:8px;color:' + rgba(ph, 0.6) + '">' + (earnedKind(p) ? (p.rank > 0 ? "EARNED" : "NOT YET EARNED") : p.rank > 0 ? "LEARNED" : "ONE RANK") + "</div>";
    }
    h += "</div>";
    var atRank = earnedKind(p) ? (p.rank > 0 ? "WHAT IT DOES" : "HOW TO EARN IT") : p.rank >= p.maxRank ? "LEARNED IN FULL" : "AT RANK " + (p.rank + 1);
    var main = hidden ? "“" + (p.hint || "Keep playing; it will find you.") + "”" : earnedKind(p) && p.rank === 0 ? (p.hint || p.description) : p.description;
    var ranks = p.rankValues && p.rankValues.length > 1 ? p.rankValues.map(function (v, i) { return "Rank " + (i + 1) + ": " + v; }).join("     ") : "";
    h += '<div style="left:318px;top:13px;width:262px"><div class="m-eb">' + atRank + '</div><div class="o-eff">' + esc(main) + '</div><div class="o-eff2">' + esc(ranks) + "</div></div>";
    // Requirements and the button.
    var req = "", btn;
    if (!earnedKind(p)) {
      var t = M.tiers[p.tier - 1], attr = M.character.attributes[s.attribute];
      if (t) {
        req += reqLine(s.level >= t.skillLevel, s.name + " " + t.skillLevel, hue);
        if (t.attribute) req += reqLine(attr >= t.attribute, attrName(s.attribute) + " " + t.attribute + (attr < t.attribute ? "  ·  " + attr + "/" + t.attribute : ""), hue);
      }
      if (p.rank < p.maxRank) req += '<div class="o-req"><span class="m-ink3">Cost</span>&nbsp;<span style="color:' + light + '">' + p.cost + " of " + s.points + " pts</span></div>";
      btn = buyable(p) ? '<div class="o-learnhalo" style="background:radial-gradient(closest-side,' + rgba(hue, 0.45) + " 55%," + rgba(hue, 0) + ' 100%)"></div><div class="o-learn" data-learn="' + p.id + '" style="background:' + hue + ";box-shadow:0 0 0 3px " + rgba(hue, 0.16) + '">' + (p.kind === "mastery" ? "Choose" : p.rank > 0 ? "Rank up" : "Learn") + "</div>"
        : '<div class="o-learn off">' + (p.rank >= p.maxRank ? "Learned" : "Locked") + "</div>";
    } else {
      req = '<div class="o-req m-ink3">Earned perks never cost points</div>';
      btn = '<div class="o-learn off" style="' + (p.rank > 0 ? "color:" + CHAMP + ";border-color:" + rgba(CHAMP, 0.5) : "") + '">' + (p.rank > 0 ? "Earned" : "Not yet") + "</div>";
    }
    h += '<div style="left:606px;top:13px;width:118px"><div class="m-eb">REQUIRES</div>' + req + "</div>";
    h += '<div style="left:728px;top:24px">' + btn + "</div>";
    return h + "</div>";
  }

  function reqLine(ok, txt, hue) {
    return '<div class="o-req">' + (ok ? '<span style="color:' + hue + '">✓</span>' : '<span class="m-ink3">×</span>') + "&nbsp; " + '<span' + (ok ? "" : ' class="m-ink2"') + ">" + esc(txt) + "</span></div>";
  }

  var ranks = null;  // perk id -> rank last drawn, to light up a perk that was just learned
  var drawn = null;  // the tree on screen: skill, hue, and where each perk's tile is

  function stateKey() {
    return JSON.stringify([view, pick, M.character, M.skills, M.perks, M.phase, M.result]);
  }

  // Selecting a perk redraws the old and new tiles and the action bar only: rebuilding the whole tree repaints
  // every glow (about 80 ms in Ultralight); two tiles and the bar are a few.
  function select(id) {
    var el = $("classes"), old = view.perk;
    view.perk = id;
    if (!drawn || !el || !view.skill || drawn.skill !== view.skill) { classes(); return; }
    var s = skillById(view.skill), p = findPerk(id);
    if (!s || !p || !drawn.at[id]) { classes(); return; }
    var ids = [old, id];
    for (var i = 0; i < ids.length; i++) {
      var at = ids[i] && drawn.at[ids[i]], q = ids[i] && findPerk(ids[i]);
      var node = at && el.querySelector('.o-panel > [data-perk="' + ids[i] + '"]');
      if (node && q) node.outerHTML = tile(q, at.x, at.y, at.hue, ids[i] === id);
    }
    var bar = el.querySelector(".o-bar");
    if (bar) bar.outerHTML = actionBar(p, s, drawn.hue);
    built = stateKey();
  }

  function classes() {
    var el = $("classes");
    if (!el || !M || kind !== "classes") return;
    var key = stateKey();
    if (key === built) { toast(); return; }
    built = key;
    drawn = null;
    el.innerHTML = view.skill && M.phase === "chosen" ? tree() : gallery();
    // A perk whose rank went up since the last draw pulses once.
    var now = {};
    for (var i = 0; i < M.perks.length; i++) now[M.perks[i].id] = M.perks[i].rank;
    if (ranks) {
      for (var id in now) {
        if (now[id] > (ranks[id] || 0)) {
          var tiles = el.querySelectorAll('[data-perk="' + id + '"] .t-tile');
          for (var k = 0; k < tiles.length; k++) tiles[k].classList.add("t-just");
        }
      }
    }
    ranks = now;
    toast();
  }

  function toast() {
    var t = $("m-toast");
    var f = M && M.feedback && M.feedback.length ? M.feedback[M.feedback.length - 1] : null;
    var s = f ? "✓  " + upper(f.text) : "";
    if (!t) {
      if (!s) return;
      t = document.createElement("div");
      t.id = "m-toast";
      t.className = "m-toast";
    }
    if (t.parentNode !== $("classes")) $("classes").appendChild(t);
    text(t, s);
    toggle($("classes"), "has-toast", !!s);
  }

  function segPoints() {
    if (!M || !M.character) return;
    var n = M.character.points || 0;
    for (var i = 0; i < M.skills.length; i++) if (M.skills[i].released) n += M.skills[i].points || 0;
    var el = $("seg-classes-pts");
    if (!el) return;
    text(el.lastChild, String(n));
    el.style.visibility = n > 0 ? "visible" : "hidden";
  }

  // ---- input ------------------------------------------------------------------------------------------
  document.addEventListener("click", function (ev) {
    var t = ev.target;
    while (t && t !== document.body) {
      if (t.getAttribute) {
        var v;
        if ((v = t.getAttribute("data-learn"))) { send({type: "mettle", op: "learnPerk", arg: v}); return; }
        if ((v = t.getAttribute("data-bg"))) { pick = {bg: v, spent: {}, fav: []}; classes(); return; }
        if ((v = t.getAttribute("data-pm"))) {
          var pm = v.split(":"), b0 = findBg(pick.bg), cur = pick.spent[pm[0]] || 0, next = cur + (+pm[1]);
          var base = 3 + ((b0 && b0.bonus[pm[0]]) || 0);
          if (b0 && next >= 0 && base + next <= M.character.maxAttribute && spentTotal() - cur + next <= b0.freePoints) { pick.spent[pm[0]] = next; classes(); }
          return;
        }
        if ((v = t.getAttribute("data-fav"))) {
          var b1 = findBg(pick.bg), at = pick.fav.indexOf(v);
          if (at >= 0) pick.fav.splice(at, 1); else if (b1 && pick.fav.length < b1.freeFavoured) pick.fav.push(v);
          classes();
          return;
        }
        if (t.getAttribute("data-begin")) {
          var sp = {};
          for (var k in pick.spent) if (pick.spent[k] > 0) sp[k] = pick.spent[k];
          send({type: "mettleChoose", background: pick.bg, spent: sp, favoured: pick.fav});
          return;
        }
        if ((v = t.getAttribute("data-attr"))) { send({type: "mettle", op: "spendAttribute", arg: v}); return; }
        if ((v = t.getAttribute("data-perk"))) { if (v !== view.perk) select(v); return; }
        if ((v = t.getAttribute("data-skill"))) { view.skill = v; view.perk = null; classes(); return; }
        if (t.getAttribute("data-back")) { view.skill = null; view.perk = null; classes(); return; }
      }
      t = t.parentNode;
    }
  });

  document.addEventListener("keydown", function (ev) {
    if (kind !== "classes" || !view.skill || ev.keyCode !== 13 || !view.perk) return;
    var p = findPerk(view.perk);
    if (p && buyable(p)) send({type: "mettle", op: "learnPerk", arg: p.id});
  });

  window.hubMettle = {
    placeholders: {
      back: ["..####..", ".#....#.", "#.####.#", "#.#..#.#", "#.####.#", "########"],
      belt: ["........", "########", "#..##..#", "########"],
      quiver: [".....##.", "....##..", "...##...", "..##....", ".##.....", "##......"]
    },
    layout: function (l) {
      kind = l.kind;
      toggle(document.body, "gear", kind === "gear");
      toggle(document.body, "classes", kind === "classes");
      built = "";
      if (kind === "classes") classes();
      if (kind === "gear") { last.gear = null; gearCards(); }
    },
    state: function (s) {
      toggle(document.body, "mettle", !!s.mettle);
      if (s.inactive && window.hub && window.hub.wells) {
        var off = {};
        for (var i = 0; i < s.inactive.length; i++) off[s.inactive[i]] = true;
        var wells = window.hub.wells();
        for (var id in wells) {
          var r = wells[id].getAttribute("data-role");
          if (r === "pack" || r === "belt_pocket" || r === "quiver_pocket") toggle(wells[id], "absent", !!off[id]);
        }
      }
      if (kind === "inventory") readout(s);
      if (kind === "gear") {
        text($("footer-left"), "PACK  ·  BELT  ·  QUIVER  ·  " + s.used + " OF 36 INVENTORY SLOTS USED");
        text($("footer-right"), "SHIFT-CLICK SENDS TO THE PACK FIRST  ·  R USES THE BELT");
      } else if (kind === "classes") {
        text($("footer-left"), "");
        text($("footer-right"), "");
      }
    },
    apply: function (m) {
      if (m.type === "icons") {
        for (var k in m.map) ICONS[k] = m.map[k];
        built = "";
        classes();
      } else if (m.type === "mettle") {
        M = m.state;
        segPoints();
        gearCount();
        if (kind === "classes") classes();
        if (kind === "gear") gearCards();
      }
    }
  };

  build();
})();
