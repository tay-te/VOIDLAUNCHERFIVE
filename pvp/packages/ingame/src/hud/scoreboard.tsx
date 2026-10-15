/**
 * Scoreboard — the server's sidebar, redrawn as a HUD chip.
 *
 * Its own module, as every widget since `watermark.tsx` is: what a single mod draws lives with
 * that mod. The `§` codes are read in `./mc-format.ts` and nowhere else.
 *
 * ## What it draws
 *
 * The board exactly as the server set it — title, then the lines top to bottom — with three
 * choices on top: whether the title is drawn (`show_title`), whether each line's score is drawn
 * at its right edge (`show_numbers`, off by default because on nearly every server the score is a
 * sort key), and whether the server's colour codes are honoured (`server_colors`). Off is the
 * HUD's own ink; bold, italic, underline and strike survive either way, because they are emphasis
 * the server chose rather than colour.
 *
 * ## `hidden`
 *
 * Hides the board entirely — this one and vanilla's — so in game the widget draws **nothing**.
 * The HUD editor and the mod page are different: a widget you cannot see is a widget you cannot
 * place, and a preview of nothing is the §15 failure. There it draws the board ghosted, which is
 * the honest picture of "this is here, and switched off".
 *
 * ## No board
 *
 * `visible: false` — no sidebar objective — draws nothing on the HUD. The editor and the page
 * stand on a sample board instead, as `hud/memory.tsx` stands on a sample heap: a real board
 * always wins.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are not read here: `HudSlot` and
 * `PreviewZoom` apply the shared chrome block around the widget (`hud/chrome.ts`).
 */

import { memo, type CSSProperties } from 'react';

import { FAKE_SCOREBOARD, type ScoreboardPayload } from '@/bridge/protocol';
import { modSettings, useVoidStore } from '@/store/store';
import { MC_PALETTE, parseMc, type McSpan } from './mc-format';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const SCOREBOARD_ID = 'scoreboard';

/**
 * The board a page stands on before a server sends one — the fake bridge's, which is
 * `bridge.json`'s own Bed Wars example line for line. One copy, so the harness and the preview
 * agree about what the sample board says.
 *
 * Every line earns its place, because each setting needs something to move: coloured team
 * letters (`server_colors`), a bold title (`show_title`), descending sort-key scores
 * (`show_numbers`), and the blank spacer lines servers send, which are the easiest thing for a
 * board to draw wrong.
 */
export const SAMPLE_SCOREBOARD: ScoreboardPayload = FAKE_SCOREBOARD;

/** The board in game, or the sample where the widget must draw something. */
export function useScoreboard(standIn: boolean): ScoreboardPayload | null {
  const live = useVoidStore((s) => s.scoreboard);
  if (live.visible) return live;
  return standIn ? SAMPLE_SCOREBOARD : null;
}

/** Vanilla draws the scores in `§c`; with server colours off they take the muted ink. */
const SCORE_CODE = 'c';

function spanStyle(span: McSpan, colors: boolean): CSSProperties | undefined {
  const style: CSSProperties = {};
  if (colors && span.color !== null) style.color = MC_PALETTE[span.color];
  if (span.bold) style.fontWeight = 600;
  if (span.italic) style.fontStyle = 'italic';
  if (span.underline || span.strike) {
    style.textDecoration = [span.underline ? 'underline' : null, span.strike ? 'line-through' : null]
      .filter(Boolean)
      .join(' ');
  }
  return Object.keys(style).length === 0 ? undefined : style;
}

/** One raw string, drawn as its styled runs. A blank line keeps its height. */
export function McText({ raw, colors }: { raw: string; colors: boolean }): React.ReactElement {
  const spans = parseMc(raw);
  if (spans.length === 0) return <>{' '}</>;
  return (
    <>
      {spans.map((span, i) => (
        <span key={i} style={spanStyle(span, colors)}>
          {span.text}
        </span>
      ))}
    </>
  );
}

export const HudScoreboard = memo(function HudScoreboard({ variant, sample, className }: HudWidgetProps) {
  const editor = variant === 'editor';
  const hidden = useVoidStore((s) => modSettings(s.loadout, SCOREBOARD_ID).hidden === true);
  const showNumbers = useVoidStore((s) => modSettings(s.loadout, SCOREBOARD_ID).show_numbers === true);
  const showTitle = useVoidStore((s) => modSettings(s.loadout, SCOREBOARD_ID).show_title !== false);
  const colors = useVoidStore((s) => modSettings(s.loadout, SCOREBOARD_ID).server_colors !== false);
  // The editor stands on the sample too: unlike a readout, a board that is absent in the editor
  // cannot be dragged to where it will be needed once a server sends one.
  const board = useScoreboard(sample === true || editor);
  if (board === null) return null;
  // In game, hidden is hidden. Anywhere it is being configured, it is drawn as a ghost.
  if (hidden && !editor && !sample) return null;
  return (
    <div
      className={[
        'v-hudchip',
        editor ? 'v-hudchip--editor' : null,
        'hud-board',
        hidden ? 'hud-board--hidden' : null,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {showTitle ? (
        <span className="hud-board__title">
          <McText raw={board.title} colors={colors} />
        </span>
      ) : null}
      {board.lines.map((line, i) => (
        <span key={i} className="hud-board__line">
          <span className="hud-board__text">
            <McText raw={line.text} colors={colors} />
          </span>
          {showNumbers ? (
            <span
              className="hud-board__score tnum"
              style={colors ? { color: MC_PALETTE[SCORE_CODE] } : undefined}
            >
              {line.score}
            </span>
          ) : null}
        </span>
      ))}
    </div>
  );
});
