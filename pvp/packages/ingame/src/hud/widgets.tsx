/**
 * The seven HUD mods of §3, bound to the store.
 *
 * Every widget here is a thin adapter: it reads the narrowest slice of state it
 * can and hands it to the corresponding presentational component in `@void/ui`.
 * The drawing, the geometry and the Ultralight-safe treatment all live there;
 * what lives here is which bridge field feeds which prop, and which of the mod's
 * settings gates it.
 *
 * Each widget subscribes only to what it draws, so a `tick` push that changes
 * the ping does not re-render the FPS chip, and a `keys` push repaints one
 * keycap's class and nothing else.
 */

import { memo } from 'react';
import {
  ArmorList,
  CoordsChip,
  CpsChip,
  Crosshair,
  FpsChip,
  KeystrokesWidget,
  PingChip,
  PotionList,
  formatAmplifier,
  formatPotionTime,
  type HudVariant,
} from '@/ui';
import { cardinalFromYaw } from '@/bridge/protocol';
import { modSettings, useModSettings, useVoidStore } from '@/store/store';
import { armorRow, potionMeta, shortHost } from './format';

export interface HudWidgetProps {
  /** `compact` in game, `editor` on the HUD-layout frame. */
  variant?: HudVariant;
}

/* -------------------------------------------------------------------- FPS */

export const HudFps = memo(function HudFps({ variant }: HudWidgetProps) {
  const fps = useVoidStore((s) => s.fps);
  const low = useVoidStore((s) => s.fpsLow);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'fps').show_label !== false);
  return (
    <FpsChip
      variant={variant}
      fps={fps}
      showLabel={showLabel}
      onePercentLow={low > 0 ? low : undefined}
    />
  );
});

/* ------------------------------------------------------------------- ping */

export const HudPing = memo(function HudPing({ variant }: HudWidgetProps) {
  const ping = useVoidStore((s) => s.ping);
  const host = useVoidStore((s) => s.server.host);
  const good = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').good_ms ?? 60));
  const bad = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').bad_ms ?? 150));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'ping').show_label !== false);
  return (
    <PingChip
      variant={variant}
      ping={ping}
      goodMs={good}
      badMs={bad}
      showLabel={showLabel}
      host={host ? shortHost(host) : undefined}
    />
  );
});

/* ----------------------------------------------------------------- coords */

export const HudCoordinates = memo(function HudCoordinates({ variant }: HudWidgetProps) {
  const pos = useVoidStore((s) => s.pos);
  const settings = useModSettings('coordinates');
  if (!pos) return null;
  return (
    <CoordsChip
      variant={variant}
      x={pos.x}
      y={pos.y}
      z={pos.z}
      decimals={Number(settings.decimals ?? 0)}
      direction={settings.show_direction === false ? undefined : cardinalFromYaw(pos.yaw)}
    />
  );
});

/* ---------------------------------------------------------------- potions */

export const HudPotionEffects = memo(function HudPotionEffects() {
  const fx = useVoidStore((s) => s.fx);
  const settings = useModSettings('potion_effects');
  const visible = settings.hide_ambient ? fx.filter((f) => !f.ambient) : fx;
  if (visible.length === 0) return null;
  return (
    <PotionList
      effects={visible.map((effect) => {
        const meta = potionMeta(effect);
        const level = settings.show_amplifier === false ? '' : formatAmplifier(effect.amplifier);
        return {
          color: meta.color,
          name: level ? `${meta.label} ${level}` : meta.label,
          time:
            settings.show_duration === false ? undefined : formatPotionTime(effect.duration_ms),
        };
      })}
    />
  );
});

/* ------------------------------------------------------------------ armor */

export const HudArmorStatus = memo(function HudArmorStatus() {
  const armor = useVoidStore((s) => s.armor);
  const settings = useModSettings('armor_status');
  const rows = armor
    .filter((slot) => settings.show_held_item !== false || slot.slot !== 'held')
    .map(armorRow)
    .filter((row): row is NonNullable<typeof row> => row !== null);
  if (rows.length === 0) return null;
  return (
    <ArmorList
      rows={rows}
      orientation={settings.orientation === 'vertical' ? 'vertical' : 'horizontal'}
    />
  );
});

/* ------------------------------------------------------------- keystrokes */

export const HudKeystrokes = memo(function HudKeystrokes() {
  const keys = useVoidStore((s) => s.keys);
  const settings = useModSettings('keystrokes');
  const cpsLeft = useVoidStore((s) => s.cpsLeft);
  const cpsRight = useVoidStore((s) => s.cpsRight);
  const radius = settings.corner_radius;
  return (
    <KeystrokesWidget
      // `corner_radius` is on the Mod settings frame but not in mods.json; it is
      // applied by overriding the token the keycaps read.
      style={
        typeof radius === 'number'
          ? ({ ['--radius-control' as string]: `${radius}px` } as React.CSSProperties)
          : undefined
      }
      keys={{
        w: keys.w === 1,
        a: keys.a === 1,
        s: keys.s === 1,
        d: keys.d === 1,
        lmb: keys.lmb === 1,
        rmb: keys.rmb === 1,
        space: keys.space === 1,
        shift: keys.shift === 1,
      }}
      showMouse={settings.show_mouse !== false}
      showSpacebar={settings.show_spacebar === true}
      cps={settings.show_cps === true ? { left: cpsLeft, right: cpsRight } : undefined}
    />
  );
});

/* -------------------------------------------------------------------- CPS */

export const HudCps = memo(function HudCps({ variant }: HudWidgetProps) {
  const left = useVoidStore((s) => s.cpsLeft);
  const right = useVoidStore((s) => s.cpsRight);
  const mode = useVoidStore((s) => String(modSettings(s.loadout, 'cps').mode ?? 'left'));
  return (
    <CpsChip
      variant={variant}
      left={left}
      right={right}
      mode={mode === 'right' ? 'right' : mode === 'both' ? 'both' : 'left'}
    />
  );
});

/* ------------------------------------------------------------- crosshair */

/**
 * The crosshair is GL, not HTML (§3 — it must sit at the exact pixel centre and
 * is 20 lines of code). This is the harness stand-in so the `?debug` view
 * matches the frames; it never renders when the real bridge is attached.
 */
export { Crosshair as DebugCrosshair };
