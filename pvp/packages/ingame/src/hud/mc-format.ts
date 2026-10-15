/**
 * Minecraft's `§` formatting codes, parsed into styled runs.
 *
 * The `scoreboard` channel sends text **raw** (`bridge.json`, `scoreboard_payload`): the page owns
 * how a code is drawn, and `scoreboard.server_colors` decides whether colour is drawn at all. So
 * this is the one place a `§` is read, and it is pure — no React, no store — so it can be tested
 * against the cases that break naive parsers: a stray `§` at the end of a line, an unknown code,
 * a colour after a bold.
 *
 * ## The rules, which are 1.8's `FontRenderer#renderStringAtPos`
 *
 * - `§0`-`§f` set the colour **and reset every style**. That is vanilla's behaviour, not a
 *   simplification: `§l§cRed` is red and *not* bold, and servers write `§c§lR` for that reason.
 * - `§l` bold, `§o` italic, `§n` underline, `§m` strikethrough add to the current style.
 * - `§k` (obfuscated) is accepted and drawn as plain text. Cycling glyphs would be the one
 *   animation on the HUD that a setting did not cause, and §4's budget is zero paints a second.
 * - `§r` resets colour and style.
 * - A `§` followed by any other character skips both, exactly as vanilla does; a `§` with
 *   nothing after it is dropped.
 * - Codes are case-insensitive (`§A` is `§a`).
 */

/** One of the sixteen colour codes, as its code character. */
export type McColorCode =
  | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7'
  | '8' | '9' | 'a' | 'b' | 'c' | 'd' | 'e' | 'f';

/** A run of text drawn in one style. */
export interface McSpan {
  text: string;
  /** `null` is "no code given" — the widget's own ink. */
  color: McColorCode | null;
  bold: boolean;
  italic: boolean;
  underline: boolean;
  strike: boolean;
}

const COLOR_CODES = new Set('0123456789abcdef');

/**
 * The sixteen colours, legible on the HUD's dark chip.
 *
 * Vanilla's palette (`#0000AA`, `#55FF55`, …) is saturated to the limit and was designed for
 * Minecraft's own drop-shadowed font over the world. On a flat chip with no text shadow two of
 * them fail outright — `§0` black and `§1` dark blue vanish into the ground — and the bright half
 * vibrates. So each is the same *hue* pulled toward the HUD's grey: black is lifted to a dark
 * grey you can still read, the darks are brightened, the lights are softened. A player who knows
 * the codes still reads every one as itself, which is the whole job — on team modes the colour
 * is the information.
 */
export const MC_PALETTE: Readonly<Record<McColorCode, string>> = {
  '0': '#6a6c70',
  '1': '#6170cc',
  '2': '#55a85f',
  '3': '#4fa7a7',
  '4': '#c25652',
  '5': '#a862b0',
  '6': '#e0a64c',
  '7': '#b3b5b9',
  '8': '#808286',
  '9': '#8090ee',
  a: '#80d887',
  b: '#80d8d8',
  c: '#ec7d75',
  d: '#e48ce0',
  e: '#e8dc80',
  f: '#edeeef',
};

const PLAIN = { color: null, bold: false, italic: false, underline: false, strike: false } as const;

function sameStyle(a: Omit<McSpan, 'text'>, b: Omit<McSpan, 'text'>): boolean {
  return (
    a.color === b.color &&
    a.bold === b.bold &&
    a.italic === b.italic &&
    a.underline === b.underline &&
    a.strike === b.strike
  );
}

/**
 * Parse a raw string into styled runs.
 *
 * Adjacent runs in the same style are merged, and empty runs are never emitted, so an empty
 * string (a blank scoreboard line — servers use them as spacers) parses to `[]`.
 */
export function parseMc(raw: string): McSpan[] {
  const out: McSpan[] = [];
  let style: Omit<McSpan, 'text'> = { ...PLAIN };
  let text = '';

  const flush = () => {
    if (text === '') return;
    const last = out[out.length - 1];
    if (last && sameStyle(last, style)) last.text += text;
    else out.push({ text, ...style });
    text = '';
  };

  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i]!;
    if (ch !== '§') {
      text += ch;
      continue;
    }
    // A code is always two characters; the second is consumed whether or not it means anything.
    const code = (raw[i + 1] ?? '').toLowerCase();
    i += 1;
    if (code === '') break;
    if (COLOR_CODES.has(code)) {
      flush();
      style = { ...PLAIN, color: code as McColorCode };
    } else if (code === 'r') {
      flush();
      style = { ...PLAIN };
    } else if (code === 'l') {
      flush();
      style = { ...style, bold: true };
    } else if (code === 'o') {
      flush();
      style = { ...style, italic: true };
    } else if (code === 'n') {
      flush();
      style = { ...style, underline: true };
    } else if (code === 'm') {
      flush();
      style = { ...style, strike: true };
    }
    // `k` and anything unknown: skipped, the text after it drawn as it is.
  }
  flush();
  return out;
}

/** The text with every code removed — what the line *says*. */
export function stripMc(raw: string): string {
  return parseMc(raw)
    .map((span) => span.text)
    .join('');
}
