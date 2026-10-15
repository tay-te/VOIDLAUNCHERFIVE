// 16 x 16 pixel grid with small shape helpers and an auto outline.
// ESM port of the `G` class the approved "Cartridge" design was drawn with.
// Colours are '#rrggbb' strings; empty cells are null.

export class G {
  constructor(n = 16) {
    this.n = n;
    this.d = Array.from({ length: n }, () => Array(n).fill(null));
  }
  /** One pixel. */
  p(x, y, c) {
    x = Math.round(x);
    y = Math.round(y);
    if (x >= 0 && y >= 0 && x < this.n && y < this.n) this.d[y][x] = c;
    return this;
  }
  /** Filled rectangle. */
  r(x, y, w, h, c) {
    for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.p(i, j, c);
    return this;
  }
  /** Bresenham line. */
  l(x0, y0, x1, y1, c) {
    let dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, e = dx + dy;
    for (;;) {
      this.p(x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * e;
      if (e2 >= dy) { e += dy; x0 += sx; }
      if (e2 <= dx) { e += dx; y0 += sy; }
    }
    return this;
  }
  /** Filled disc (pixel centres within r), optionally masked by only(x, y). */
  c(cx, cy, r, c, only) {
    for (let y = 0; y < this.n; y++) for (let x = 0; x < this.n; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (d <= r && (!only || only(x, y))) this.p(x, y, c);
    }
    return this;
  }
  /** One-pixel ring of radius r. */
  ring(cx, cy, r, c) {
    for (let y = 0; y < this.n; y++) for (let x = 0; x < this.n; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (d <= r && d > r - 1.1) this.p(x, y, c);
    }
    return this;
  }
  /** Filled polygon (even-odd, pixel centres). */
  poly(pts, c) {
    for (let y = 0; y < this.n; y++) for (let x = 0; x < this.n; x++) if (inPoly(x + 0.5, y + 0.5, pts)) this.p(x, y, c);
    return this;
  }
  /** Stamp rows of characters through a palette; '.' and ' ' are skipped. */
  rows(rows, pal, ox = 0, oy = 0) {
    rows.forEach((row, j) => [...row].forEach((ch, i) => {
      if (ch !== '.' && ch !== ' ') this.p(ox + i, oy + j, pal[ch]);
    }));
    return this;
  }
  clear(x, y) {
    if (this.d[y]) this.d[y][x] = null;
    return this;
  }
  /** Recolour filled pixels: fn returns a colour, or undefined to keep. */
  map(fn) {
    for (let y = 0; y < this.n; y++) for (let x = 0; x < this.n; x++) if (this.d[y][x]) {
      const v = fn(x, y, this.d[y][x]);
      if (v !== undefined) this.d[y][x] = v;
    }
    return this;
  }
  /** Outline: every empty pixel 4-adjacent to a non-outline pixel becomes c. */
  ol(c) {
    const add = [];
    for (let y = 0; y < this.n; y++) for (let x = 0; x < this.n; x++) {
      if (this.d[y][x]) continue;
      const nb = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => this.d[y + b] && this.d[y + b][x + a] && this.d[y + b][x + a] !== c);
      if (nb) add.push([x, y]);
    }
    add.forEach(([x, y]) => (this.d[y][x] = c));
    return this;
  }
}

export function inPoly(x, y, pts) {
  let ins = false;
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j];
    if (((yi > y) !== (yj > y)) && (x < ((xj - xi) * (y - yi)) / (yj - yi) + xi)) ins = !ins;
  }
  return ins;
}
