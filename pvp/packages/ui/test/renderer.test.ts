/**
 * The renderer contract.
 *
 * `design/ultralight-notes.md` is a list of things the in-game renderer cannot do. A
 * component test cannot catch a violation — jsdom happily parses `text-shadow` — so
 * these tests read the shipped stylesheets and assert the rules directly. They are the
 * reason a `text-shadow` or a `mix-blend-mode` cannot quietly reach the JAR.
 */
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it, beforeEach } from 'vitest';

import { RENDERERS, TOKEN_NAMES, getRenderer, setGlBlur, setRenderer } from '../src/index.js';

const srcDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src');
const designDir = path.resolve(srcDir, '../../../design');

const tokensCss = readFileSync(path.join(srcDir, 'tokens.css'), 'utf8');
const noiseCss = readFileSync(path.join(srcDir, 'noise.css'), 'utf8');
const fontsCss = readFileSync(path.join(srcDir, 'fonts.css'), 'utf8');
const styleFiles = readdirSync(path.join(srcDir, 'styles')).filter((f) => f.endsWith('.css'));
const componentCss = styleFiles
  .map((name) => readFileSync(path.join(srcDir, 'styles', name), 'utf8'))
  .join('\n');

/** A CSS declaration, ignoring anything inside a comment. */
function declarations(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, '');
}

const componentDecls = declarations(componentCss);
const tokenDecls = declarations(tokensCss);

/* -------------------------------------------------------------------------- */
/* Tokens are copied, never forked                                            */
/* -------------------------------------------------------------------------- */

describe('tokens.css', () => {
  const authored = readFileSync(path.join(designDir, 'tokens.css'), 'utf8');

  it('contains the authored :root block verbatim', () => {
    // design/ is read-only reference material and nothing imports from it at build
    // time, so the values are copied here — but they must be copied, not retyped.
    expect(tokensCss).toContain(authored);
  });

  it('declares every token the design declares', () => {
    const authoredNames = new Set(
      [...authored.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gim)].map((m) => m[1]!),
    );
    expect(authoredNames.size).toBeGreaterThan(120);
    for (const name of authoredNames) expect(TOKEN_NAMES).toContain(name);
  });

  it('defines both renderer layers and a default', () => {
    expect(tokensCss).toMatch(/\[data-renderer='ultralight'\]\s*\{/);
    expect(tokensCss).toMatch(/\[data-renderer='webview'\]\s*\{/);
    expect(RENDERERS).toEqual(['ultralight', 'webview']);
  });
});

/* -------------------------------------------------------------------------- */
/* The Ultralight fallbacks                                                   */
/* -------------------------------------------------------------------------- */

/** Everything between `[data-renderer='ultralight'] {` and its closing brace. */
function ultralightLayer(): string {
  const start = tokenDecls.indexOf("[data-renderer='ultralight'] {");
  expect(start).toBeGreaterThan(-1);
  const end = tokenDecls.indexOf('}', start);
  return tokenDecls.slice(start, end);
}

describe('the ultralight layer applies the prescribed fallbacks', () => {
  const layer = ultralightLayer();

  it('§1 replaces the blurred panel with a semi-opaque solid', () => {
    // 0.97 when the host GL blur pass is not running…
    expect(layer).toMatch(/--panel-bg:\s*rgba\(10, 11, 12, 0\.97\)/);
    // …0.94 when it is, switched by the host's data-glblur attribute.
    expect(tokenDecls).toMatch(
      /\[data-renderer='ultralight'\]\[data-glblur='on'\][\s\S]*?--panel-bg:\s*rgba\(10, 11, 12, 0\.94\)/,
    );
    expect(layer).toMatch(/--palette-bg:\s*rgba\(10, 11, 12, 0\.96\)/);
    expect(layer).toMatch(/--dim-palette:\s*rgba\(10, 11, 12, 0\.62\)/);
  });

  it('§1 zeroes every blur radius rather than branching on @supports', () => {
    for (const token of ['--blur-panel', '--blur-dock', '--blur-dim']) {
      expect(layer).toMatch(new RegExp(`${token}:\\s*0px`));
    }
    // "Never branch on @supports (backdrop-filter: …) — Ultralight may claim support
    // and still no-op."
    expect(tokenDecls + componentDecls).not.toMatch(/@supports[^{]*backdrop-filter/);
  });

  it('§2 bakes the noise into the base hexes and switches the grain off', () => {
    expect(layer).toMatch(/--surface-1:\s*#1a1d21/);
    expect(layer).toMatch(/--surface-2:\s*#23272c/);
    for (const token of [
      '--noise-opacity-frame',
      '--noise-opacity-canvas',
      '--noise-opacity-card',
      '--noise-opacity-raised',
      '--noise-opacity-accent',
      '--noise-opacity-tint',
      '--noise-opacity-chrome',
    ]) {
      expect(layer).toMatch(new RegExp(`${token}:\\s*0`));
    }
  });

  it('§7 falls back to a solid selection border', () => {
    expect(layer).toMatch(/--selection-border-style:\s*solid/);
    expect(tokenDecls).toMatch(
      /\[data-renderer='webview'\][\s\S]*?--selection-border-style:\s*dashed/,
    );
    // …and the component reads the token rather than hard-coding either value.
    expect(componentDecls).toMatch(/border:\s*1\.5px var\(--selection-border-style\) var\(--accent\)/);
  });
});

/* -------------------------------------------------------------------------- */
/* Things the overlay cannot render must not appear at all                    */
/* -------------------------------------------------------------------------- */

describe('the component stylesheet stays inside what Ultralight can render', () => {
  it('§3 never uses text-shadow or -webkit-text-stroke', () => {
    expect(componentDecls).not.toMatch(/text-shadow\s*:/);
    expect(componentDecls).not.toMatch(/-webkit-text-stroke/);
    expect(tokenDecls).not.toMatch(/text-shadow\s*:/);
  });

  it('§4 never uses a 3D transform, perspective or preserve-3d', () => {
    expect(componentDecls).not.toMatch(/rotate[XY]\(/);
    expect(componentDecls).not.toMatch(/rotate3d\(/);
    expect(componentDecls).not.toMatch(/translateZ|translate3d/);
    expect(componentDecls).not.toMatch(/perspective\s*:/);
    expect(componentDecls).not.toMatch(/transform-style\s*:\s*preserve-3d/);
    expect(componentDecls).not.toMatch(/backface-visibility/);
  });

  it('§7 never uses CSS Grid — the design is flex and absolute positioning throughout', () => {
    expect(componentDecls).not.toMatch(/display\s*:\s*(inline-)?grid/);
    expect(componentDecls).not.toMatch(/grid-template/);
  });

  it('§7 never uses position: sticky', () => {
    expect(componentDecls).not.toMatch(/position\s*:\s*sticky/);
  });

  it('§7 never uses filter: drop-shadow — every shadow is a box-shadow', () => {
    expect(componentDecls).not.toMatch(/filter\s*:\s*drop-shadow/);
  });

  it('§2 confines mix-blend-mode to the webview-only grain layer', () => {
    expect(componentDecls).not.toMatch(/mix-blend-mode/);
    expect(componentDecls).not.toMatch(/background-blend-mode/);
    // The one occurrence in the package is the noise layer, and it is scoped to the
    // launcher renderer.
    const noiseDecls = declarations(noiseCss);
    expect(noiseDecls.match(/mix-blend-mode/g)).toHaveLength(1);
    expect(noiseDecls).toMatch(/\[data-renderer='webview'\] \.v-noise::after/);
    expect(noiseDecls).toMatch(/:root:not\(\[data-renderer\]\) \.v-noise::after/);
    expect(noiseDecls).not.toMatch(/\[data-renderer='ultralight'\][^{]*\{[^}]*mix-blend-mode/);
  });

  it('§6 never uses video or animated media', () => {
    expect(componentDecls).not.toMatch(/<video|url\([^)]*\.(mp4|webm|gif)/);
  });

  it('§1 only ever reads a blur radius through a token', () => {
    for (const match of componentDecls.matchAll(/backdrop-filter\s*:\s*([^;]+);/g)) {
      expect(match[1]).toMatch(/blur\(var\(--blur-/);
    }
  });

  it('§7 drops font-variation-settings — the bundled faces are static instances', () => {
    expect(componentDecls).not.toMatch(/font-variation-settings/);
    expect(declarations(fontsCss)).not.toMatch(/font-variation-settings/);
  });

  it('animates only opacity, 2D transform, colour and size', () => {
    const animated = [...componentDecls.matchAll(/transition:\s*([^;]+);/g)].map((m) => m[1]!);
    expect(animated.length).toBeGreaterThan(5);
    for (const value of animated) {
      for (const property of value.split(',').map((part) => part.trim().split(/\s+/)[0]!)) {
        expect(
          [
            'opacity',
            'transform',
            'color',
            'background-color',
            'border-color',
            'box-shadow',
            'width',
            'height',
            'left',
            'top',
          ],
          `unexpected transitioned property: ${property}`,
        ).toContain(property);
      }
    }
  });
});

/* -------------------------------------------------------------------------- */
/* Every var() resolves                                                       */
/* -------------------------------------------------------------------------- */

describe('every custom property the stylesheet reads is declared', () => {
  /** Properties this package declares itself, outside the copied token block. */
  const LOCAL = new Set([
    '--selection-border-style', // set by both renderer layers
    '--noise-image', // noise.css
    '--v-noise-opacity', // per-surface override, always used with a fallback
  ]);

  const declared = new Set<string>([
    ...TOKEN_NAMES,
    ...LOCAL,
    ...[...tokenDecls.matchAll(/(--[a-z0-9-]+)\s*:/gi)].map((m) => m[1]!),
  ]);

  it.each([
    ['styles/*.css', componentDecls, 60],
    ['noise.css', declarations(noiseCss), 3],
  ] as const)('%s reads no undeclared token', (_name, css, atLeast) => {
    const used = new Set([...css.matchAll(/var\((--[a-z0-9-]+)/gi)].map((m) => m[1]!));
    expect(used.size).toBeGreaterThanOrEqual(atLeast);
    const missing = [...used].filter((name) => !declared.has(name));
    expect(missing, `undeclared: ${missing.join(', ')}`).toEqual([]);
  });

  it('the Tailwind preset maps tokens by reference, never by copied literal', () => {
    const preset = declarations(readFileSync(path.join(srcDir, 'tailwind-preset.css'), 'utf8'));
    const body = preset.slice(preset.indexOf('@theme'));
    for (const [, value] of body.matchAll(/^\s*--[a-z0-9-]+:\s*([^;]+);/gim)) {
      // A baked hex here would freeze the launcher's colours into the in-game bundle.
      expect(value!.trim(), 'theme values must be var(--token)').toMatch(/^var\(--[a-z0-9-]+\)$/);
    }
  });
});

/* -------------------------------------------------------------------------- */
/* fonts.css                                                                  */
/* -------------------------------------------------------------------------- */

describe('fonts.css', () => {
  it('declares the six faces the design uses, all font-display: block', () => {
    const faces = fontsCss.match(/@font-face\s*\{[^}]*\}/g) ?? [];
    expect(faces.length).toBeGreaterThanOrEqual(6);
    for (const face of faces) {
      expect(face).toMatch(/font-display:\s*block/);
      expect(face).toMatch(/format\('woff2'\)/);
    }
  });

  it('references only bundled, relative font URLs — the in-game bundle has no network', () => {
    const urls = [...fontsCss.matchAll(/url\('([^']+)'\)/g)].map((m) => m[1]!);
    expect(urls.length).toBeGreaterThanOrEqual(6);
    for (const url of urls) {
      expect(url.startsWith('./fonts/')).toBe(true);
      expect(url.endsWith('.woff2')).toBe(true);
    }
  });

  it('ships every referenced file, plus its OFL licence', () => {
    const bundled = readdirSync(path.join(srcDir, 'fonts'));
    for (const url of [...fontsCss.matchAll(/url\('\.\/fonts\/([^']+)'\)/g)].map((m) => m[1]!)) {
      expect(bundled, `${url} is referenced but not bundled`).toContain(url);
    }
    for (const family of ['outfit', 'dmmono', 'bricolagegrotesque']) {
      expect(bundled).toContain(`OFL-${family}.txt`);
    }
  });
});

/* -------------------------------------------------------------------------- */
/* setRenderer / getRenderer / setGlBlur                                      */
/* -------------------------------------------------------------------------- */

describe('setRenderer', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-renderer');
    document.documentElement.removeAttribute('data-glblur');
  });

  it('stamps the attribute the token layers select on', () => {
    setRenderer('ultralight');
    expect(document.documentElement.getAttribute('data-renderer')).toBe('ultralight');
    setRenderer('webview');
    expect(document.documentElement.getAttribute('data-renderer')).toBe('webview');
  });

  it('reads back, defaulting to webview when nothing is set', () => {
    expect(getRenderer()).toBe('webview');
    setRenderer('ultralight');
    expect(getRenderer()).toBe('ultralight');
  });

  it('can stamp an element other than the document root', () => {
    const element = document.createElement('div');
    setRenderer('ultralight', element);
    expect(getRenderer(element)).toBe('ultralight');
    expect(getRenderer()).toBe('webview');
  });

  it('setGlBlur adds and removes the host flag', () => {
    setGlBlur(true);
    expect(document.documentElement.getAttribute('data-glblur')).toBe('on');
    setGlBlur(false);
    expect(document.documentElement.hasAttribute('data-glblur')).toBe(false);
  });
});
