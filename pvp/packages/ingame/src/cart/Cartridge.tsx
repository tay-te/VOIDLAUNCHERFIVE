/**
 * One mod as a collectible cartridge (Figma 594:2, states on the tokens frame 593:2).
 *
 *   ┌ K ─────────────── ● ┐   strip: the mod's key as a keycap (or —), and its LED
 *   │ ┌─────────────────┐ │
 *   │ │      sprite      │ │   bed: the 16 × 16 pixel sprite at 5×, a notch at the bottom
 *   │ └───────╨─────────┘ │
 *   │ Keystrokes           │   name
 *   │ 142 fps              │   live value, in the category hue (or `off` / `▲ Grey on Hypixel`)
 *   │ [≡]           [▮▮ ] │   settings button, slide switch
 *   └──────────────────────┘
 *
 * States are fill steps, never scale: rest #191c20, hover +1 (the art lifts 6px in two pixel
 * steps), pressed +2 (the keycap loses its lip), off (LED dark, art at 40%), grey-on-Hypixel
 * (art at 25%, a warning line) and selected (1.5px ink ring — the cartridge whose bench is open).
 *
 * The card body opens the mod's settings bench and the switch toggles it in place — the same
 * contract the v1 tiles had, so the muscle memory carries over.
 */

import { memo, type ReactNode } from 'react';

import { KEYBIND_SETTINGS, MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { isModOn, modSettings, quickPins, useVoidStore } from '@/store/store';
import { keybindLabel } from '@/menu/settings-format';
import { cx } from '@/ui';
import { Sprite, hasSprite } from './Sprite';
import { Kc, Led, SlidersGlyph, Switch, catClass, categoryOf } from './prims';
import { useCartValue } from './values';

/** The setting that holds a mod's own key, if it has one (`keybind`, `key`, `start_key`…). */
export function modKeySetting(id: ModId): string | null {
  const path = KEYBIND_SETTINGS.find((p) => p.startsWith(`${id}.`));
  return path ? path.slice(id.length + 1) : null;
}

/** The mod's bound key as a keycap label, or null when it has none / is unbound. */
export function useModKey(id: ModId): string | null {
  const setting = modKeySetting(id);
  const raw = useVoidStore((s) => (setting ? modSettings(s.loadout, id)[setting] : null));
  if (!setting || typeof raw !== 'string' || raw === '' || raw === 'NONE') return null;
  return keybindLabel(raw);
}

/** Whether the player is on Hypixel, where `grey` mods are tolerated rather than endorsed. */
export function useOnHypixel(): boolean {
  return useVoidStore((s) => s.server.connected && /(^|\.)hypixel\.(net|io)$/i.test(s.server.host.replace(/:\d+$/, '')));
}

/** `name` with the first case-insensitive match of `query` marked. */
export function highlight(name: string, query: string): ReactNode {
  const q = query.trim();
  if (!q) return name;
  const at = name.toLowerCase().indexOf(q.toLowerCase());
  if (at < 0) return name;
  return (
    <>
      {name.slice(0, at)}
      <mark className="c-hl">{name.slice(at, at + q.length)}</mark>
      {name.slice(at + q.length)}
    </>
  );
}

export interface CartridgeProps {
  id: ModId;
  /** The keyboard / pointer cursor is on it: hover look. */
  cursor?: boolean;
  /** Its settings bench is open: the ink ring. */
  selected?: boolean;
  /** Search query to mark in the name. */
  query?: string;
  onCursor?: (id: ModId) => void;
  onOpen?: (id: ModId) => void;
}

export const Cartridge = memo(function Cartridge({ id, cursor, selected, query = '', onCursor, onOpen }: CartridgeProps) {
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const pinned = useVoidStore((s) => quickPins(s.loadout).includes(id));
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const key = useModKey(id);
  const value = useCartValue(id);
  const hypixel = useOnHypixel();
  const grey = MOD_REGISTRY[id]?.hypixel_safe === 'grey';
  const warned = grey && hypixel;
  const blocked = warned && !on;
  const category = categoryOf(id);
  const name = modLabel(id);

  let line: ReactNode;
  if (warned) {
    line = <span className="c-cart__warn">▲ Grey on Hypixel</span>;
  } else if (!on) {
    line = <span className="c-cart__off">off</span>;
  } else if (value) {
    line = (
      <>
        <span className="c-cart__v">{value.v || 'on'}</span>
        {value.unit && value.unit.length < 7 ? <span className="c-cart__u">{value.unit}</span> : null}
      </>
    );
  } else {
    line = <span className="c-cart__v">on</span>;
  }

  return (
    <div
      className={cx(
        'c-cart',
        catClass(category),
        !on && 'is-off',
        blocked && 'is-blocked',
        cursor && 'is-hover',
        selected && 'is-sel',
      )}
      data-mod-id={id}
      onMouseEnter={onCursor ? () => onCursor(id) : undefined}
    >
      {/* The whole body is the open target; the switch and the settings button sit above it. */}
      <button
        type="button"
        className="c-cart__hit"
        aria-label={`${name} settings`}
        onClick={() => onOpen?.(id)}
      />
      <div className="c-cart__strip">
        {key ? <Kc tone={cursor ? 'lt' : 'idle'}>{key}</Kc> : <span className="c-cart__nokey">—</span>}
        <span className="c-cart__marks">
          {pinned ? <Sprite name={hasSprite('pin') ? 'pin' : 'logo'} size={12} className="c-cart__pin" /> : null}
          <Led on={on && !blocked} />
        </span>
      </div>
      <div className="c-cart__bed">
        <span className="c-cart__art">
          {hasSprite(id) ? <Sprite name={id} size={80} dim={blocked ? 0.25 : on ? undefined : 0.4} /> : null}
        </span>
        <i className="c-cart__notch" />
      </div>
      <div className="c-cart__name">{highlight(name, query)}</div>
      <div className="c-cart__val">{line}</div>
      <div className="c-cart__bot">
        <button
          type="button"
          className="c-cart__gear"
          aria-label={`Open ${name} settings`}
          onClick={(event) => {
            event.stopPropagation();
            onOpen?.(id);
          }}
        >
          <SlidersGlyph />
        </button>
        <Switch checked={on} label={`${name} — ${on ? 'on' : 'off'}`} onChange={(next) => toggleMod(id, next)} />
      </div>
    </div>
  );
});

/** The `+N more` cartridge that closes a shelf's row and opens the shelf. */
export function MoreCartridge({
  count,
  cursor,
  onOpen,
  onCursor,
}: {
  count: number;
  cursor?: boolean;
  onOpen: () => void;
  onCursor?: () => void;
}) {
  return (
    <button
      type="button"
      className={cx('c-cart c-cart--more', cursor && 'is-hover')}
      onClick={onOpen}
      onMouseEnter={onCursor}
      aria-label={`${count} more — view the shelf`}
    >
      <span className="c-cart__morenum">+{count}</span>
      <span className="c-mono">more</span>
    </button>
  );
}
