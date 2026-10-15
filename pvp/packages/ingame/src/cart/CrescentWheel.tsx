/**
 * The Crescent emote wheel (Figma 295:2, EW-B 588:35340, states 589:36570), in Cartridge.
 *
 * Eight emote cartridges on a crescent under the crosshair. Pointing picks the nearest card (a
 * flick works — the pick is by angle from the centre, not by hitting a card), 1–8 pick and play
 * at once, a click plays the picked one. Playing folds the arc and leaves a chip at the
 * crosshair with a stepped bar; Escape or B closes without playing.
 *
 * Cosmetic surface, so the picked card may glow (CONTRACTS: gamified surfaces may use richer
 * colour and glow) — a box-shadow, which this engine draws; nothing else here does.
 *
 * Two ways up. From the menu (B, or "Try it" in the wardrobe) it takes the pointer and clicks.
 * Held in the world (`cosmetics.emote_key`, default B) it is drawn over the HUD layer and driven
 * by the `wheel` channel: Java freezes mouse look and reports the travel as `aim`, which picks a
 * card by its angle from the crosshair, 1-8 arrive as `key`, and `release` plays the pick.
 */

import { useEffect, useRef, useState } from 'react';

import { useModKey } from './Cartridge';

import type { CosmeticId } from '@/bridge/protocol';
import { cx } from '@/ui';
import { Sprite } from './Sprite';
import { Kc, Seg } from './prims';
import { EMOTE_WHEEL_SIZE, RARITY_HUE, findCosmetic, useCosmetics } from './cosmetics-store';
import { itemSprite } from './CosmeticsTab';

const CX = 960;
const CY = 400;
const R = 390;
const FROM = 152;
const TO = 28;

/** Card centres on the crescent, left to right. */
export function crescent(n = EMOTE_WHEEL_SIZE): { x: number; y: number; deg: number }[] {
  return Array.from({ length: n }, (_, i) => {
    const deg = FROM + ((TO - FROM) * i) / Math.max(1, n - 1);
    const rad = (deg * Math.PI) / 180;
    return { x: CX + Math.cos(rad) * R, y: CY + Math.sin(rad) * R, deg };
  });
}

/** The card a mouse travel points at, by angle from the crosshair; null while it is too short. */
export function pickByAim(aim: { x: number; y: number } | null, n = EMOTE_WHEEL_SIZE): number | null {
  if (!aim || Math.hypot(aim.x, aim.y) < 12) return null;
  const deg = (Math.atan2(aim.y, aim.x) * 180) / Math.PI;
  let best = 0;
  let bestD = Infinity;
  crescent(n).forEach((s, i) => {
    // Angles from the crosshair (960, 540), where the flick starts — not from the arc's centre.
    const cardDeg = (Math.atan2(s.y - 540, s.x - 960) * 180) / Math.PI;
    const d = Math.abs(((deg - cardDeg + 540) % 360) - 180);
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  });
  return best;
}

export function CrescentWheel({ width, height, world = false }: { width: number; height: number; world?: boolean }) {
  const wheel = useCosmetics((s) => (s.self?.emotes ?? []) as CosmeticId[]);
  const aim = useCosmetics((s) => s.wheelAim);
  const keyPick = useCosmetics((s) => s.wheelKey);
  const release = useCosmetics((s) => s.wheelRelease);
  const holdKey = useModKey('cosmetics') ?? 'B';
  const play = useCosmetics((s) => s.play);
  const setWheelOpen = useCosmetics((s) => s.setWheelOpen);
  const [pick, setPick] = useState<number>(Math.min(5, Math.max(0, wheel.length - 1)));
  const [played, setPlayed] = useState<CosmeticId | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const spots = crescent();
  const ox = (width - 1920) / 2;
  const oy = (height - 1080) / 2;

  const fire = (i: number) => {
    if (played) return; // the chip is up and closes itself
    const id = wheel[i];
    if (!id) {
      if (world) setWheelOpen(false);
      return;
    }
    if (play(id)) {
      setPlayed(id);
      window.setTimeout(() => setWheelOpen(false), 1400);
    } else if (world) {
      setWheelOpen(false);
    }
  };

  // The world wheel: Java's input, through the store.
  const firstRelease = useRef(release);
  const firstKey = useRef(keyPick?.seq ?? 0);
  useEffect(() => {
    const at = pickByAim(aim);
    if (world && at !== null) setPick(at);
  }, [aim, world]);
  useEffect(() => {
    if (!world || !keyPick || keyPick.seq === firstKey.current) return;
    firstKey.current = keyPick.seq;
    setPick(keyPick.slot);
    fire(keyPick.slot);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyPick, world]);
  useEffect(() => {
    if (!world || release === firstRelease.current) return;
    firstRelease.current = release;
    fire(pick);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [release, world]);

  useEffect(() => {
    // The world wheel takes no page input at all (the HUD layer has none); Java sends it.
    if (world) return undefined;
    const onKey = (e: KeyboardEvent) => {
      const n = Number(e.key);
      if (Number.isInteger(n) && n >= 1 && n <= EMOTE_WHEEL_SIZE && e.key.length === 1) {
        e.preventDefault();
        e.stopImmediatePropagation();
        setPick(n - 1);
        fire(n - 1);
      } else if (e.key === 'b' || e.key === 'B') {
        e.preventDefault();
        e.stopImmediatePropagation();
        setWheelOpen(false);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        fire(pick);
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  });

  // Pick by angle from the crosshair, so a flick lands without hitting a card.
  const onMove = (e: React.MouseEvent) => {
    if (world) return;
    const box = root.current?.getBoundingClientRect();
    if (!box || box.width === 0) return;
    const k = width / box.width;
    const x = (e.clientX - box.left) * k - ox;
    const y = (e.clientY - box.top) * k - oy;
    if (Math.hypot(x - 960, y - 540) < 40) return;
    let best = 0;
    let bestD = Infinity;
    spots.forEach((s, i) => {
      const d = Math.hypot(s.x - x, s.y - y);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    });
    if (best !== pick) setPick(best);
  };

  const picked = wheel[pick] ? findCosmetic(wheel[pick]) : undefined;

  return (
    <div className={world ? 'c-wheel c-wheel--world' : 'c-wheel'} ref={root} onMouseMove={onMove} role="dialog" aria-label="Emote wheel">
      <div className="c-wheel__in" style={{ left: ox, top: oy }}>
        <div className="c-wheel__hint">
          <Kc>{world ? holdKey : 'B'}</Kc>
          <span>{world ? 'Flick or press 1–8 · release to play · you can still move' : 'Point or press 1–8 · you can still move'}</span>
        </div>
        <i className="c-wheel__cross" />
        {played ? (
          <div className="c-wheel__playing" role="status">
            <Sprite name={itemSprite(played, 'emote')} size={20} />
            <span>
              {findCosmetic(played)?.name} · playing
            </span>
            <span className="c-wheel__bar">
              <Seg n={16} on={6} w={7} h={3} gap={2} off="rgba(255,255,255,0.12)" />
            </span>
          </div>
        ) : (
          <>
            <div className="c-wheel__label">
              {picked ? (
                <>
                  <span className="c-wheel__name">{picked.name}</span>
                  <span className="c-mono" style={{ color: RARITY_HUE[picked.rarity] }}>
                    {picked.rarity} · {world ? 'release' : 'click'} or press {pick + 1}
                  </span>
                </>
              ) : (
                <>
                  <span className="c-wheel__name">Empty slot</span>
                  <span className="c-mono">Fill it in Cosmetics · Emote wheel</span>
                </>
              )}
            </div>
            {spots.map((s, i) => {
              const id = wheel[i];
              const item = id ? findCosmetic(id) : undefined;
              return (
                <button
                  key={i}
                  type="button"
                  className={cx('c-wheel__card', pick === i && 'is-pick', !id && 'is-empty')}
                  style={{ left: s.x - 42, top: s.y - 48 - (pick === i ? 18 : 0) }}
                  onMouseEnter={() => setPick(i)}
                  onClick={() => fire(i)}
                  aria-label={`${i + 1}${item ? ` — ${item.name}` : ' — empty'}`}
                >
                  {item ? <i className="c-wheel__led" style={{ background: RARITY_HUE[item.rarity] }} /> : null}
                  {id ? <Sprite name={itemSprite(id, 'emote')} size={44} /> : <span className="c-slot__plus">+</span>}
                  <span className="c-wheel__num">{i + 1}</span>
                </button>
              );
            })}
          </>
        )}
      </div>
    </div>
  );
}
