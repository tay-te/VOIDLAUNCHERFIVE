/**
 * The shadow parser. It turns an authored `--shadow-*-gl` token into the numbers the GL pass
 * draws, so a token shape it misreads would put a wrong shadow on screen rather than none.
 */
import { describe, expect, it } from 'vitest';
import { parseShadow } from '@/effects/surfaces';

describe('parseShadow', () => {
  it('reads the authored panel shadow', () => {
    // Verbatim from design/tokens.css via --shadow-panel-gl.
    expect(parseShadow('0 30px 70px -20px rgba(0,0,0,0.60)')).toEqual({
      dx: 0,
      dy: 30,
      blur: 70,
      spread: -20,
      color: [0, 0, 0, 0.6],
    });
  });

  it('defaults a missing spread to zero', () => {
    expect(parseShadow('0 1px 2px rgba(0,0,0,0.30)')).toEqual({
      dx: 0,
      dy: 1,
      blur: 2,
      spread: 0,
      color: [0, 0, 0, 0.3],
    });
  });

  it('takes the first layer of a two-layer shadow', () => {
    // --shadow-cta is ambient plus an accent glow; the ambient one is the shadow.
    const parsed = parseShadow(
      '0 6px 14px -4px rgba(0,0,0,0.35), 0 10px 28px -6px rgba(159,139,255,0.42)',
    );
    expect(parsed?.blur).toBe(14);
    expect(parsed?.color[3]).toBeCloseTo(0.35);
  });

  it('normalises colour channels to 0-1 for the shader', () => {
    const parsed = parseShadow('0 4px 12px -2px rgba(159,139,255,0.45)');
    expect(parsed?.color[0]).toBeCloseTo(159 / 255);
    expect(parsed?.color[1]).toBeCloseTo(139 / 255);
    expect(parsed?.color[2]).toBeCloseTo(255 / 255);
  });

  it('returns null rather than guessing at anything it does not understand', () => {
    // Every one of these would otherwise become a wrong shadow, which is worse than no shadow.
    expect(parseShadow('none')).toBeNull();
    expect(parseShadow('')).toBeNull();
    expect(parseShadow('   ')).toBeNull();
    expect(parseShadow('inset 0 1px 0 0 rgba(0,0,0,0.2)')).toBeNull(); // inset is not a drop shadow
    expect(parseShadow('0 2px red')).toBeNull(); // named colours are not authored
    expect(parseShadow('rgba(0,0,0,0.5)')).toBeNull(); // no geometry
  });
});
