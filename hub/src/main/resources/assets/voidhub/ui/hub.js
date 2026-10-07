// VOID Hub page. Java owns the state and the layout; this file only draws what it is told.
//
//   Java -> page:  hub.apply({type: "layout" | "state" | "detail", ...})   (View.evaluateScript)
//   page -> Java:  window.__void_native(JSON.stringify({type: ...}))        (the binding's bridge)
//
// Every update touches only the elements that changed, so a hover repaints two wells and the
// card, and an unchanged page repaints nothing.
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var wellsRoot = $("wells");
  var wells = {};        // slot index -> element
  var roles = {};        // slot index -> role
  var prev = {};         // last applied state, for diffing
  var layout = null;

  function send(msg) {
    try {
      if (typeof window.__void_native === "function") {
        return window.__void_native(JSON.stringify(msg));
      }
    } catch (e) { /* the page must never throw into the host */ }
    return null;
  }

  // A row of up to `max` cells: `n` of them shown (right-aligned), the first `lit` of those on and,
  // optionally, the last lit one live (violet). Built once as three static layers — off cells,
  // on cells, one live cell — and updated by resizing/moving those layers: an update restyles at
  // most three boxes and never adds or removes elements. Ultralight repaints the whole view
  // (~50 ms at 1920x1080) when one frame invalidates more than about ten separate boxes, which
  // restyling cells one by one, rebuilding them, or toggling visibility on a subtree all did.
  function meter(el, max, n, lit, live, size, pitch, radius) {
    if (el.__max !== max) {
      el.innerHTML = "";
      el.__max = max;
      el.__k = undefined;
      el.classList.add("meter");
      var off = document.createElement("span");
      var on = document.createElement("span");
      var lv = document.createElement("i");
      off.className = "m-off";
      on.className = "m-on";
      lv.className = "m-live";
      for (var i = 0; i < max; i++) {
        off.appendChild(meterCell("right", i, size, pitch, radius));
        on.appendChild(meterCell("left", i, size, pitch, radius));
      }
      lv.style.width = lv.style.height = size + "px";
      lv.style.borderRadius = radius + "px";
      el.style.width = (max - 1) * pitch + size + "px";
      el.style.height = size + "px";
      el.appendChild(off);
      el.appendChild(on);
      el.appendChild(lv);
      el.__off = off;
      el.__on = on;
      el.__live = lv;
    }
    n = Math.max(0, Math.min(max, n));
    lit = Math.max(0, Math.min(n, lit));
    var key = n + ":" + lit + ":" + (live ? 1 : 0);
    if (el.__k === key) return;
    el.__k = key;
    var first = (max - n) * pitch;
    el.__off.style.width = (n > 0 ? (n - 1) * pitch + size : 0) + "px";
    el.__on.style.left = first + "px";
    el.__on.style.width = (lit > 0 ? (lit - 1) * pitch + size : 0) + "px";
    el.__live.style.left = (live && lit > 0 ? first + (lit - 1) * pitch : -100000) + "px";
  }

  function meterCell(side, i, size, pitch, radius) {
    var c = document.createElement("i");
    c.style[side] = Math.round(i * pitch * 4) / 4 + "px";
    c.style.width = c.style.height = size + "px";
    c.style.borderRadius = radius + "px";
    return c;
  }

  function text(el, s) {
    if (el.textContent !== s) el.textContent = s;
  }

  function toggle(el, cls, on) {
    if (el.classList.contains(cls) !== on) el.classList.toggle(cls, on);
  }

  // ---- pixel art (design cell sprites) ---------------------------------------------------------
  function sprite(el, rows, size, gap, alpha, colour) {
    el.innerHTML = "";
    for (var y = 0; y < rows.length; y++) {
      for (var x = 0; x < rows[y].length; x++) {
        var ch = rows[y].charAt(x);
        if (ch === ".") continue;
        var i = document.createElement("i");
        i.style.left = (x * (size + gap)) + "px";
        i.style.top = (y * (size + gap)) + "px";
        i.style.width = size + "px";
        i.style.height = size + "px";
        var a = ch === "#" ? alpha[0] : ch === "+" ? alpha[1] : alpha[2];
        i.style.background = "rgba(" + colour + "," + a + ")";
        el.appendChild(i);
      }
    }
  }

  var RING = ["..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.."];
  (function ring() {
    var el = $("ring");
    for (var y = 0; y < RING.length; y++) {
      for (var x = 0; x < RING[y].length; x++) {
        if (RING[y].charAt(x) !== "#") continue;
        var i = document.createElement("i");
        i.style.left = (x * 4) + "px";
        i.style.top = (y * 4) + "px";
        el.appendChild(i);
      }
    }
  })();

  var GLYPH = ["#####", "#...#", "##.##", "#...#", "#####"];
  (function glyph() {
    var el = $("chest-glyph");
    for (var y = 0; y < GLYPH.length; y++) {
      for (var x = 0; x < GLYPH[y].length; x++) {
        if (GLYPH[y].charAt(x) !== "#") continue;
        var i = document.createElement("i");
        i.style.left = (7.5 + x * 4.8) + "px";
        i.style.top = (7.5 + y * 4.8) + "px";
        el.appendChild(i);
      }
    }
  })();

  // The Map stand-in: a folded map with a route and a pin, at the cards' quiet ink.
  var MAPART = [
    "#########",
    "#...#...#",
    "#.+.#...#",
    "#..+#.o.#",
    "#...++..#",
    "#...#...#",
    "#########"
  ];
  sprite($("standin-art"), MAPART, 4.2, 1.2, ["0.22", "0.5", "0.92"], "237,238,239");

  var COIN = [".####.", "#++++#", "#+##+#", "#+##+#", "#++++#", ".####."];
  function coins() {
    var defs = [["copper", "217,160,126"], ["iron", "196,201,207"], ["gold", "227,196,126"]];
    var els = document.querySelectorAll(".coinart");
    for (var k = 0; k < els.length; k++) {
      var el = els[k];
      for (var d = 0; d < defs.length; d++) {
        if (el.classList.contains(defs[d][0])) {
          var small = el.classList.contains("small");
          var s = small ? 2.16 : 3, g = small ? 0.36 : 0.5;
          sprite(el, COIN, s, g, ["0.62", "0.9", "0.3"], defs[d][1]);
          var off = small ? 2.7 : 3.75;
          for (var c = 0; c < el.children.length; c++) {
            el.children[c].style.left = (parseFloat(el.children[c].style.left) + off) + "px";
            el.children[c].style.top = (parseFloat(el.children[c].style.top) + off) + "px";
          }
        }
      }
    }
  }
  coins();

  // Faint placeholders in empty armour / offhand wells (design: cell art at 14% ink).
  var PLACEHOLDER = {
    head: ["..####..", ".######.", "##....##", "##....##"],
    chest: ["##....##", "########", ".######.", ".######.", ".######.", ".######."],
    legs: ["########", "###..###", "##....##", "##....##", "##....##"],
    feet: [".##..##.", ".##..##.", "###..###"],
    offhand: ["########", "#.####.#", "#.####.#", ".#.##.#.", "..####.."]
  };

  // ---- layout ---------------------------------------------------------------------------------
  function applyLayout(l) {
    layout = l;
    var body = document.body;
    toggle(body, "inventory", l.kind === "inventory");
    toggle(body, "chest", l.kind === "chest");
    toggle(body, "map", l.kind === "map");
    toggle(body, "scrolls", l.maxScroll > 0);
    var win = $("win");
    win.style.left = l.win.x + "px";
    win.style.top = l.win.y + "px";
    // The map window's size is the layout's (it fills the space between the islands); the
    // inventory and chest windows keep the design's fixed size from the stylesheet.
    win.style.width = l.kind === "map" ? l.win.w + "px" : "";
    win.style.height = l.kind === "map" ? l.win.h + "px" : "";

    var seen = {};
    for (var k = 0; k < l.slots.length; k++) {
      var s = l.slots[k];
      var el = wells[s.i];
      if (!el) {
        el = document.createElement("div");
        el.className = "well";
        wellsRoot.appendChild(el);
        wells[s.i] = el;
      }
      if (roles[s.i] !== s.r) {
        roles[s.i] = s.r;
        el.setAttribute("data-role", s.r);
        el.innerHTML = "";
        if (PLACEHOLDER[s.r]) {
          var ph = document.createElement("div");
          ph.className = "ph";
          sprite(ph, PLACEHOLDER[s.r], 3, 0.5, ["0.14", "0.14", "0.14"], "237,238,239");
          el.appendChild(ph);
        }
      }
      el.style.left = s.x + "px";
      el.style.top = s.y + "px";
      el.style.width = s.w + "px";
      el.style.height = s.h + "px";
      toggle(el, "hidden", false);
      seen[s.i] = true;
    }
    for (var id in wells) {
      if (!seen[id]) toggle(wells[id], "hidden", true);
    }
    if (l.maxScroll > 0) {
      var track = 150, rows = l.rows, vis = 3;
      var h = track * vis / rows;
      var thumb = $("thumb");
      thumb.style.height = h + "px";
      thumb.style.top = ((track - h) * l.scroll / l.maxScroll) + "px";
    }
    // Slot numbers mean different wells in another layout: drop every state class and let the
    // next state message re-apply them.
    for (var w in wells) {
      toggle(wells[w], "hover", false);
      toggle(wells[w], "held", false);
      toggle(wells[w], "drag", false);
    }
    prev = {};
  }

  // ---- state ----------------------------------------------------------------------------------
  function applyState(s) {
    var p = prev;
    var i, el;
    if (p.hover !== s.hover) {
      if (wells[p.hover]) toggle(wells[p.hover], "hover", false);
      if (wells[s.hover]) toggle(wells[s.hover], "hover", true);
    }
    var heldSlot = heldWell(s.held);
    if (p.heldSlot !== heldSlot) {
      if (wells[p.heldSlot]) toggle(wells[p.heldSlot], "held", false);
      if (wells[heldSlot]) toggle(wells[heldSlot], "held", true);
    }
    var drag = s.drag.join(",");
    if (p.drag !== drag) {
      var old = p.dragList || [];
      for (i = 0; i < old.length; i++) if (wells[old[i]]) toggle(wells[old[i]], "drag", false);
      for (i = 0; i < s.drag.length; i++) if (wells[s.drag[i]]) toggle(wells[s.drag[i]], "drag", true);
    }
    var filled = s.filled.join(",");
    if (p.filled !== filled) {
      var set = {};
      for (i = 0; i < s.filled.length; i++) set[s.filled[i]] = true;
      for (var id in wells) toggle(wells[id], "filled", !!set[id]);
    }
    if (p.filter !== s.filter) {
      var chips = document.querySelectorAll(".chip");
      for (i = 0; i < chips.length; i++) toggle(chips[i], "active", chips[i].getAttribute("data-filter") === s.filter);
    }
    text($("world-status"), "WORLD  ·  DAY " + s.day);
    text($("world-name"), s.world);
    if (p.page !== s.page) {
      var pages = document.querySelectorAll("#pages .page");
      for (i = 0; i < pages.length; i++) toggle(pages[i], "active", pages[i].getAttribute("data-page") === s.page);
    }
    toggle(document.body, "atlas", !!s.atlas);
    toggle($("pill"), "off", !s.atlas);
    text($("pill-text"), s.pill || "Waypoint here");

    var total = s.purse.copper + s.purse.iron * 10 + s.purse.gold * 100;
    text($("coin-copper"), String(s.purse.copper));
    text($("coin-iron"), String(s.purse.iron));
    text($("coin-gold"), String(s.purse.gold));
    text($("mini-copper"), String(s.purse.copper));
    text($("mini-iron"), String(s.purse.iron));
    text($("mini-gold"), String(s.purse.gold));
    text($("purse-total"), total + " BITS CARRIED  ·  1 BIT = 1 COPPER");
    text($("purse-compact-total"), total + " BITS CARRIED");
    text($("purse-bank"), s.purse.hearth ? "" : "HEARTH NOT LOADED");

    if (layout && layout.kind === "inventory") {
      text($("footer-left"), "36 SLOTS  ·  " + s.used + " USED  ·  PLAYER INVENTORY (VANILLA CONTAINER)");
      text($("footer-right"), "SHIFT-CLICK MOVES  ·  DOUBLE-CLICK GATHERS");
      var armourLit = Math.round(Math.min(20, s.armor) / 20 * 7);
      meter($("armour-cells"), 7, 7, armourLit, false, 6, 9, 1.8);
      text($("armour-value"), String(s.armor));
      var xpLit = Math.floor(s.xp * 7);
      meter($("level-cells"), 7, 7, s.level > 0 ? xpLit + 1 : 0, s.level > 0, 6, 9, 1.8);
      text($("level-value"), String(s.level));
    } else if (layout && layout.kind === "chest") {
      var title = s.title || "Chest";
      text($("chest-title"), title);
      text($("chest-coords"), s.coords || "");
      text($("chest-eyebrow"), title.toUpperCase());
      text($("seg-chest-count"), String(s.total));
      // 27 cells fit the card; a double chest shows its fill proportionally.
      var fn = Math.min(27, s.total);
      meter($("chest-fill"), 27, fn, s.total > 27 ? Math.round(s.used * 27 / s.total) : s.used, false, 8.5, 12.7, 2.55);
      text($("chest-fill-text"), s.used + " / " + s.total);
      text($("footer-left"), title.toUpperCase() + "  ·  " + s.total + " SLOTS  ·  " + s.used + " USED");
      text($("footer-right"), "EVERY CLICK GOES TO THE REAL CONTAINER");
    }

    prev = {hover: s.hover, heldSlot: heldSlot, drag: drag, dragList: s.drag, filled: filled, filter: s.filter, page: s.page};
  }

  function heldWell(n) {
    for (var id in roles) {
      if (roles[id] === "hotbar" && layout) {
        for (var k = 0; k < layout.slots.length; k++) {
          var s = layout.slots[k];
          if (s.i == id && s.n === n) return s.i;
        }
      }
    }
    return -1;
  }

  // ---- the hovered-item card ------------------------------------------------------------------
  // Fixed rows, filled in place: text, classes and visibility only (see index.html).
  var durCells = $("durability-cells");

  function show(el, on) {
    toggle(el, "hidden-v", !on);
  }

  function fillRow(el, row) {
    show(el, !!row);
    if (!row) return;
    toggle(el, "ench", row.kind === "ench");
    text(el.children[0], row.k);
    text(el.children[2], row.v);
    if (row.kind === "ench") {
      meter(el.children[1], 5, row.cells, row.lit, false, 7, 10, 2.1);
    }
  }

  function applyDetail(d) {
    toggle($("card-detail"), "has", !!d.has);
    if (!d.has) return;
    text($("detail-label"), d.label);
    text($("detail-name"), d.count > 1 ? d.name + "  ×" + d.count : d.name);
    text($("detail-meta"), d.meta);

    var lines = [];
    for (var j = 0; j < d.lines.length; j++) lines.push({kind: "line", k: d.lines[j].k, v: d.lines[j].v});
    // First row: durability, else the first fact line.
    show($("detail-durability"), !!d.durability);
    if (d.durability) {
      text($("durability-text"), d.durability.text);
      var lit = d.durability.lit;
      meter(durCells, 24, 24, lit, true, 10.5, 398.5 / 23, 3);
      fillRow($("row-r1"), null);
    } else {
      fillRow($("row-r1"), lines.shift() || null);
    }
    // Then enchantments (at most three rows; the third says how many more), else more facts.
    var rows = [];
    if (d.ench.length) {
      for (var e = 0; e < d.ench.length && rows.length < 3; e++) {
        var en = d.ench[e];
        if (rows.length === 2 && d.ench.length > 3) {
          rows.push({kind: "line", k: "+" + (d.ench.length - 2) + " more enchantments", v: ""});
        } else {
          rows.push({kind: "ench", k: en.name, v: en.roman, cells: Math.min(5, en.cells), lit: Math.min(5, en.lit)});
        }
      }
    } else {
      rows = lines.slice(0, 3);
    }
    show($("ench-eyebrow"), d.ench.length > 0);
    fillRow($("row-0"), rows[0] || null);
    fillRow($("row-1"), rows[1] || null);
    fillRow($("row-2"), rows[2] || null);
  }

  // ---- input from the page --------------------------------------------------------------------
  document.addEventListener("click", function (ev) {
    var t = ev.target;
    while (t && t !== document.body) {
      if (t.getAttribute) {
        if (t.getAttribute("data-filter")) { send({type: "filter", value: t.getAttribute("data-filter")}); return; }
        if (t.getAttribute("data-sort")) { send({type: "sort", region: t.getAttribute("data-sort")}); return; }
        if (t.getAttribute("data-move")) { send({type: "moveAll", to: t.getAttribute("data-move")}); return; }
        if (t.getAttribute("data-act") === "close") { send({type: "close"}); return; }
        if (t.getAttribute("data-act") === "waypoint") { send({type: "waypoint"}); return; }
        if (t.getAttribute("data-page")) { send({type: "page", value: t.getAttribute("data-page")}); return; }
        if (t.getAttribute("data-seg")) { send({type: "segment", value: t.getAttribute("data-seg")}); return; }
      }
      t = t.parentNode;
    }
  });

  window.hub = {
    apply: function (m) {
      try {
        if (m.type === "layout") applyLayout(m);
        else if (m.type === "state") applyState(m);
        else if (m.type === "detail") applyDetail(m);
      } catch (e) {
        return "error: " + e;
      }
      return "ok";
    },
    // Where the page actually drew each well (CSS px) — the in-game layout agreement check.
    wellRects: function () {
      var out = [];
      for (var id in wells) {
        if (wells[id].classList.contains("hidden")) continue;
        var r = wells[id].getBoundingClientRect();
        out.push({i: +id, x: r.left, y: r.top, w: r.width, h: r.height});
      }
      return JSON.stringify(out);
    }
  };

  send({type: "ready"});
})();
