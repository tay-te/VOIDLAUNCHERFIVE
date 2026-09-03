/**
 * The seven HUD widgets of §3.
 *
 * Each one subscribes to the narrowest slice of the store it can, so a `keys`
 * push repaints one keycap and a `tick` push repaints one number. There is no
 * animation frame loop: the widgets are pure functions of store state and the
 * 20 Hz tick is the only clock.
 */

import { memo } from 'react';
import { useVoidStore, modSettings } from '@/store/store';
import { armorRow, cardinal, duration, potionMeta, roman } from './format';
import type { KeysPayload } from '@/bridge/protocol';

/* -------------------------------------------------------------------- FPS */

export const FpsChip = memo(function FpsChip() {
  const fps = useVoidStore((s) => s.fps);
  const low = useVoidStore((s) => s.fpsLow);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'fps').show_label !== false);
  return (
    <div className="hud-chip">
      <span className="hud-num">{fps}</span>
      {showLabel && <span className="hud-unit">fps</span>}
      {low > 0 && <span className="hud-fps__low">·&nbsp; 1% low {low}</span>}
    </div>
  );
});

/* ------------------------------------------------------------------- ping */

export const PingChip = memo(function PingChip() {
  const ping = useVoidStore((s) => s.ping);
  const host = useVoidStore((s) => s.server.host);
  const settings = useVoidStore((s) => modSettings(s.loadout, 'ping'));
  const good = Number(settings.good_ms ?? 60);
  const bad = Number(settings.bad_ms ?? 150);
  const colour =
    ping < 0 ? 'var(--text-muted)' : ping <= good ? 'var(--ok)' : ping <= bad ? 'var(--warn)' : 'var(--danger)';
  return (
    <div className="hud-chip">
      <span
        style={{ width: 7, height: 7, borderRadius: 999, background: colour, flex: '0 0 auto' }}
      />
      <span className="hud-num">{ping < 0 ? '—' : ping}</span>
      {settings.show_label !== false && <span className="hud-unit">ms</span>}
      {host && <span className="hud-ping__host">{shortHost(host)}</span>}
    </div>
  );
});

/** `mc.hypixel.net` reads as `Hypixel` in the frames. */
function shortHost(host: string): string {
  const parts = host.split('.').filter(Boolean);
  const core = parts.length >= 2 ? parts[parts.length - 2]! : parts[0] ?? host;
  return core.charAt(0).toUpperCase() + core.slice(1);
}

/* ----------------------------------------------------------------- coords */

export const CoordsChip = memo(function CoordsChip() {
  const pos = useVoidStore((s) => s.pos);
  const settings = useVoidStore((s) => modSettings(s.loadout, 'coordinates'));
  if (!pos) return null;
  const decimals = Number(settings.decimals ?? 1);
  const stacked = settings.layout === 'stacked';
  const n = (v: number) => v.toFixed(decimals);
  return (
    <div className={`hud-chip hud-coords${stacked ? ' hud-coords--stacked' : ''}`}>
      <span>X {n(pos.x)}</span>
      <span>Y {n(pos.y)}</span>
      <span>Z {n(pos.z)}</span>
      {settings.show_direction !== false && (
        <>
          {!stacked && <span className="hud-coords__sep">·</span>}
          <span>{cardinal(pos.yaw)}</span>
        </>
      )}
    </div>
  );
});

/* ---------------------------------------------------------------- potions */

export const PotionList = memo(function PotionList() {
  const fx = useVoidStore((s) => s.fx);
  const settings = useVoidStore((s) => modSettings(s.loadout, 'potion_effects'));
  const visible = settings.hide_ambient ? fx.filter((f) => !f.ambient) : fx;
  if (visible.length === 0) return null;
  return (
    <div className="hud-chip hud-potions">
      {visible.map((effect) => {
        const meta = potionMeta(effect);
        const level = settings.show_amplifier === false ? '' : roman(effect.amplifier);
        return (
          <div className="hud-potion" key={effect.id}>
            <span className="hud-potion__swatch" style={{ background: meta.color }} />
            <span className="hud-potion__name">
              {meta.label}
              {level && ` ${level}`}
            </span>
            {settings.show_duration !== false && (
              <span className="hud-potion__time">{duration(effect.duration_ms)}</span>
            )}
          </div>
        );
      })}
    </div>
  );
});

/* ------------------------------------------------------------------ armor */

export const ArmorList = memo(function ArmorList() {
  const armor = useVoidStore((s) => s.armor);
  const settings = useVoidStore((s) => modSettings(s.loadout, 'armor_status'));
  const rows = armor
    .filter((slot) => settings.show_held_item !== false || slot.slot !== 'held')
    .map(armorRow)
    .filter((row): row is NonNullable<typeof row> => row !== null);
  if (rows.length === 0) return null;
  return (
    <div className="hud-chip hud-armor">
      {rows.map((row) => (
        <div className="hud-armor__row" key={row.slot}>
          <span className="hud-armor__icon" />
          <span className="hud-armor__body">
            <span className="hud-armor__line">
              <span className="hud-armor__label">{row.label}</span>
              {settings.show_durability !== false && (
                <span className="hud-armor__value">
                  {row.remaining} / {row.max}
                </span>
              )}
            </span>
            <span className="hud-armor__bar">
              <span
                className="hud-armor__fill"
                style={{
                  width: `${Math.round(row.ratio * 100)}%`,
                  background: row.tone === 'warn' ? 'var(--warn)' : 'var(--ok)',
                }}
              />
            </span>
          </span>
        </div>
      ))}
    </div>
  );
});

/* ------------------------------------------------------------- keystrokes */

/**
 * One keycap, subscribed to exactly one field of `keys`. This is the whole
 * point of the edge-triggered `keys` channel: pressing W re-renders the W cap
 * and nothing else (§9, bridge.json `keys_payload`).
 */
export const Keycap = memo(function Keycap({
  field,
  label,
  wide,
  space,
}: {
  field: keyof KeysPayload;
  label: string;
  wide?: boolean;
  space?: boolean;
}) {
  const pressed = useVoidStore((s) => s.keys[field]);
  return (
    <span
      className={[
        'hud-key',
        wide ? 'hud-key--wide' : '',
        space ? 'hud-key--space' : '',
        pressed ? 'hud-key--on' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {label}
    </span>
  );
});

export const KeystrokesWidget = memo(function KeystrokesWidget() {
  const settings = useVoidStore((s) => modSettings(s.loadout, 'keystrokes'));
  const radius = settings.corner_radius;
  const style =
    typeof radius === 'number'
      ? ({ ['--radius-control' as string]: `${radius}px` } as React.CSSProperties)
      : undefined;
  return (
    <div className="hud-keys" style={style}>
      <div className="hud-keys__row">
        <Keycap field="w" label="W" />
      </div>
      <div className="hud-keys__row">
        <Keycap field="a" label="A" />
        <Keycap field="s" label="S" />
        <Keycap field="d" label="D" />
      </div>
      {settings.show_mouse !== false && (
        <div className="hud-keys__row">
          <Keycap field="lmb" label="LMB" wide />
          <Keycap field="rmb" label="RMB" wide />
        </div>
      )}
      {settings.show_spacebar === true && (
        <div className="hud-keys__row">
          <Keycap field="space" label="" space />
        </div>
      )}
    </div>
  );
});

/* -------------------------------------------------------------------- CPS */

export const CpsChip = memo(function CpsChip() {
  const left = useVoidStore((s) => s.cpsLeft);
  const right = useVoidStore((s) => s.cpsRight);
  const mode = useVoidStore((s) => modSettings(s.loadout, 'cps').mode ?? 'left');
  return (
    <div className="hud-chip hud-cps">
      {mode !== 'right' && <span className="hud-cps__left">{left}</span>}
      {mode === 'both' && <span className="hud-cps__bar">|</span>}
      {mode !== 'left' && <span className="hud-cps__right">{right}</span>}
      <span className="hud-cps__unit">CPS</span>
    </div>
  );
});

/* ------------------------------------------------------------- crosshair */

/**
 * The crosshair is GL, not HTML (§3 — it must sit at the exact pixel centre and
 * is 20 lines of code). This is the harness stand-in so the `?debug` view
 * matches the frames; it never renders when the real bridge is attached.
 */
export function DebugCrosshair() {
  return (
    <div className="hud-crosshair">
      <span />
      <span />
    </div>
  );
}
