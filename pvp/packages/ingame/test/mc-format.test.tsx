/**
 * The `§` parser, and the scoreboard widget that draws with it.
 *
 * The parser's cases are the ones that break a naive one: a colour after a style (vanilla resets
 * the style), `§r`, a stray `§` at the end of a line, an unknown code, and the empty spacer lines
 * every server sends.
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';

import { MC_PALETTE, parseMc, stripMc } from '@/hud/mc-format';
import { HudScoreboard, SAMPLE_SCOREBOARD } from '@/hud/scoreboard';
import { connectBridge } from '@/bridge/connect';
import { resetDerivedState, useVoidStore } from '@/store/store';

const plain = { color: null, bold: false, italic: false, underline: false, strike: false };

describe('parseMc', () => {
  it('parses an empty line to no runs', () => {
    expect(parseMc('')).toEqual([]);
    expect(stripMc('')).toBe('');
  });

  it('leaves text with no codes alone', () => {
    expect(parseMc('Red: 2')).toEqual([{ text: 'Red: 2', ...plain }]);
  });

  it('reads colour codes, case-insensitively', () => {
    expect(parseMc('§cRed §AGreen')).toEqual([
      { text: 'Red ', ...plain, color: 'c' },
      { text: 'Green', ...plain, color: 'a' },
    ]);
  });

  it('adds bold to the current colour, and a colour code resets it — as vanilla does', () => {
    expect(parseMc('§c§lR §fRed')).toEqual([
      { text: 'R ', ...plain, color: 'c', bold: true },
      { text: 'Red', ...plain, color: 'f' },
    ]);
    // Style first, colour second: the colour wins and the bold is gone.
    expect(parseMc('§l§cnot bold')).toEqual([{ text: 'not bold', ...plain, color: 'c' }]);
  });

  it('stacks italic, underline and strike', () => {
    expect(parseMc('§o§n§mall')).toEqual([
      { text: 'all', ...plain, italic: true, underline: true, strike: true },
    ]);
  });

  it('resets everything on §r', () => {
    expect(parseMc('§e§lBED§r WARS')).toEqual([
      { text: 'BED', ...plain, color: 'e', bold: true },
      { text: ' WARS', ...plain },
    ]);
  });

  it('draws §k as plain text rather than cycling it', () => {
    expect(parseMc('§kabc')).toEqual([{ text: 'abc', ...plain }]);
  });

  it('drops a stray § at the end, and skips an unknown code with its character', () => {
    expect(parseMc('end§')).toEqual([{ text: 'end', ...plain }]);
    expect(parseMc('a§zb')).toEqual([{ text: 'ab', ...plain }]);
    expect(stripMc('§')).toBe('');
  });

  it('merges runs that end up in the same style', () => {
    expect(parseMc('§aone§atwo')).toEqual([{ text: 'onetwo', ...plain, color: 'a' }]);
  });

  it('has an ink for every colour code', () => {
    expect(Object.keys(MC_PALETTE).sort()).toEqual([...'0123456789abcdef'].sort());
  });
});

describe('the scoreboard widget', () => {
  let dispose: () => void;

  beforeEach(() => {
    resetDerivedState();
    ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
  });

  afterEach(() => {
    cleanup();
    dispose();
  });

  it('takes the board the bridge pushes, and draws one row per line', () => {
    // The fake bridge pushes its Bed Wars board with the rest of the opening state.
    expect(useVoidStore.getState().scoreboard.visible).toBe(true);
    const { container } = render(<HudScoreboard variant="compact" />);
    expect(container.querySelectorAll('.hud-board__line')).toHaveLength(SAMPLE_SCOREBOARD.lines.length);
    expect(container.querySelector('.hud-board__title')!.textContent).toBe('BED WARS');
  });

  it('draws nothing in game when there is no sidebar, and the sample on the page', () => {
    act(() => {
      useVoidStore.getState().applyScoreboard({ visible: false, title: '', lines: [] });
    });
    expect(render(<HudScoreboard variant="compact" />).container.innerHTML).toBe('');
    cleanup();
    expect(render(<HudScoreboard variant="editor" sample />).container.querySelector('.hud-board')).not.toBeNull();
  });

  it('is absent in game while hidden, and ghosted where it is configured', () => {
    act(() => {
      useVoidStore.getState().setSetting('scoreboard', 'hidden', true);
    });
    expect(render(<HudScoreboard variant="compact" />).container.innerHTML).toBe('');
    cleanup();
    const editor = render(<HudScoreboard variant="editor" />).container;
    expect(editor.querySelector('.hud-board--hidden')).not.toBeNull();
    act(() => {
      useVoidStore.getState().setSetting('scoreboard', 'hidden', false);
    });
  });

  it('colours the server codes only while server_colors is on', () => {
    const coloured = () =>
      [...document.querySelectorAll<HTMLElement>('.hud-board span')].some((el) => el.style.color !== '');
    render(<HudScoreboard variant="compact" />);
    expect(coloured()).toBe(true);
    act(() => {
      useVoidStore.getState().setSetting('scoreboard', 'server_colors', false);
    });
    expect(coloured()).toBe(false);
    act(() => {
      useVoidStore.getState().setSetting('scoreboard', 'server_colors', true);
    });
  });
});
