// Layered stills: one transparent PNG per layer, plus full.png and layers.json.
//
// A layer keeps the elements its selectors match (with everything inside them) and hides
// every other element with `visibility: hidden`. Visibility, unlike display or opacity,
// leaves layout alone and lets a visible child of a hidden parent still paint, so a kept
// element sits at exactly the pixels it has in the full frame. The page background is
// overridden to transparent.
//
// Hiding elements changes how Chrome splits the page into compositor layers, and content
// rasterised in a different layer can come out a little different (software raster: up
// to 14 levels at antialiased edges on Home, when hiding the Friends sheet un-composited
// the navbar and play bar). So every claimed root is promoted to its own compositor layer
// (`will-change: opacity`) in full.png and in every layer capture; then the layers
// re-composite to full.png within 2 levels. The promotion makes each root a stacking
// context and nothing else; `promote: ''` turns it off.
//
// `stripAncestors` is a second guard against the same effect: the kept elements'
// ancestors stay visible and only lose their own paint (background, border, shadow,
// outline, ::before/::after, direct text), so the layer tree above a kept element never
// changes. Without promotion it fixed a 1px shift of an SVG icon under .screen-view (which
// has a view-transition-name) on the GPU path.
//
// The special value "*rest" keeps everything the other layers do not claim (the panel,
// the shell fill...), so a spec can always composite back to the full frame.

import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { alphaBounds, crop, decodePNG, encodePNG } from './png.mjs';

const KEEP = 'data-webcap-keep';
const DROP = 'data-webcap-drop';
const CHAIN = 'data-webcap-chain';
const PROMOTE = 'data-webcap-promote';

/** {name: selectors} or [{name, sel}] -> [{name, sel: string[] | '*rest'}] in paint order. */
export function normaliseSpec(spec) {
  const list = Array.isArray(spec) ? spec.map((l) => [l.name, l.sel ?? l.selectors]) : Object.entries(spec);
  return list.map(([name, sel]) => {
    if (!/^[\w.-]+$/.test(name)) throw new Error(`layer name "${name}" must be a plain file name`);
    if (sel === '*rest') return { name, sel };
    return { name, sel: Array.isArray(sel) ? sel : [sel] };
  });
}

export async function captureLayers(s, spec, outDir, { crop: doCrop = false, stripAncestors = false, promote = 'opacity', meta = {} } = {}) {
  const layers = normaliseSpec(spec);
  const claimed = layers.filter((l) => l.sel !== '*rest').flatMap((l) => l.sel);

  await s.setTransparent(true);
  if (promote) {
    // Every claimed root gets its own compositor layer in full.png and in every layer, so
    // hiding the others cannot move it into a different layer (see the note at the top).
    await s.eval(`(() => {
      for (const sel of ${JSON.stringify(claimed)}) for (const el of document.querySelectorAll(sel)) el.setAttribute('${PROMOTE}', '');
    })()`);
    await s.addStyle('webcap-promote', `[${PROMOTE}] { will-change: ${promote} !important; }`);
  }
  await s.eval('__webcap.freezeNew()');
  const fullPng = await s.screenshot();
  writeFileSync(join(outDir, 'full.png'), fullPng);
  const full = decodePNG(fullPng);

  const out = [];
  for (const [order, layer] of layers.entries()) {
    const rest = layer.sel === '*rest';
    const info = await s.eval(`(() => {
      const rest = ${rest}, plain = ${!stripAncestors}, sels = ${JSON.stringify(rest ? claimed : layer.sel)};
      const style = document.createElement('style');
      style.id = 'webcap-layer';
      const els = [];
      for (const sel of sels) for (const el of document.querySelectorAll(sel)) els.push(el);
      let box = null;
      const missing = sels.filter((sel) => !document.querySelector(sel));
      const kept = [];
      for (const el of els) {
        // Only what is visible in the full frame is kept; a naturally hidden match stays hidden.
        if (!rest && getComputedStyle(el).visibility !== 'visible') continue;
        el.setAttribute(rest ? '${DROP}' : '${KEEP}', '');
        if (rest) continue;
        kept.push(el);
        const b = el.getBoundingClientRect();
        if (!b.width && !b.height) continue;
        box = box ? { l: Math.min(box.l, b.left), t: Math.min(box.t, b.top), r: Math.max(box.r, b.right), b: Math.max(box.b, b.bottom) }
                  : { l: b.left, t: b.top, r: b.right, b: b.bottom };
      }
      if (!rest && !plain) {
        for (const el of kept) {
          el.style.setProperty('--webcap-color', getComputedStyle(el).color);
          for (let p = el.parentElement; p; p = p.parentElement) {
            if (!p.hasAttribute('${KEEP}')) p.setAttribute('${CHAIN}', '');
          }
        }
        // A kept element inside another kept element is part of it, not a chain link.
        for (const el of document.querySelectorAll('[${KEEP}] [${CHAIN}]')) el.removeAttribute('${CHAIN}');
      }
      style.textContent = rest
        ? '[${DROP}], [${DROP}] * { visibility: hidden !important; }'
        : plain
        ? '[${KEEP}] { visibility: visible !important; } *:not([${KEEP}]):not([${KEEP}] *) { visibility: hidden !important; }'
        : '*:not([${KEEP}]):not([${KEEP}] *):not([${CHAIN}]) { visibility: hidden !important; }' +
          '[${KEEP}] { visibility: visible !important; color: var(--webcap-color) !important; }' +
          '[${CHAIN}] { visibility: visible !important; background: none !important; border-color: transparent !important;' +
          ' box-shadow: none !important; outline-color: transparent !important; color: transparent !important; text-shadow: none !important; }' +
          '[${CHAIN}]::before, [${CHAIN}]::after, [${CHAIN}]::marker { visibility: hidden !important; }';
      document.head.append(style);
      return { count: els.length, missing, box, frozen: __webcap.freezeNew() };
    })()`);
    if (info.missing.length) s.log?.(`warning: layer "${layer.name}": nothing matches ${info.missing.join(', ')}`);

    const png = await s.screenshot();
    await s.eval(`(() => {
      document.getElementById('webcap-layer')?.remove();
      for (const el of document.querySelectorAll('[${KEEP}], [${DROP}], [${CHAIN}]')) {
        el.removeAttribute('${KEEP}'); el.removeAttribute('${DROP}'); el.removeAttribute('${CHAIN}');
        el.style.removeProperty('--webcap-color');
        if (el.getAttribute('style') === '') el.removeAttribute('style');
      }
      return __webcap.freezeNew();
    })()`);

    const img = decodePNG(png);
    const file = `${layer.name}.png`;
    writeFileSync(join(outDir, file), png);
    const bbox = alphaBounds(img);
    const entry = {
      name: layer.name,
      order,
      file,
      selectors: layer.sel,
      elements: info.count,
      // Pixel bounds of everything this layer paints (alpha > 0), in output pixels.
      bbox,
      // Union of the kept elements' border boxes, in output pixels (shadows/overflow excluded).
      domBox: info.box
        ? {
            x: Math.round(info.box.l * s.scale),
            y: Math.round(info.box.t * s.scale),
            w: Math.round((info.box.r - info.box.l) * s.scale),
            h: Math.round((info.box.b - info.box.t) * s.scale),
          }
        : null,
    };
    if (doCrop && bbox) {
      entry.crop = `${layer.name}.crop.png`;
      writeFileSync(join(outDir, entry.crop), encodePNG(crop(img, bbox)));
    }
    out.push(entry);
  }

  if (promote) {
    await s.removeStyle('webcap-promote');
    await s.eval(`document.querySelectorAll('[${PROMOTE}]').forEach((el) => el.removeAttribute('${PROMOTE}'))`);
  }

  const doc = {
    ...meta,
    width: full.width,
    height: full.height,
    scale: s.scale,
    viewport: [s.width, s.height],
    full: 'full.png',
    order: 'bottom to top; composite each layer "over" the ones before it',
    layers: out,
  };
  writeFileSync(join(outDir, 'layers.json'), JSON.stringify(doc, null, 2));
  return doc;
}
