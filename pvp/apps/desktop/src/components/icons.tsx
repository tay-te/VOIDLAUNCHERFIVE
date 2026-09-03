/**
 * Inline SVG icons, lucide-shaped, at the sizes the Figma uses (13/14/16/22 px).
 *
 * TODO(integrate): `@void/ui` owns the shared icon set. These are launcher-local
 * stand-ins with the same names, so the swap is an import change.
 *
 * Note the split `design/ultralight-notes.md` §7 calls out: inline SVG is fine *here*
 * (Chromium-class webview) but unreliable in Ultralight, where the in-game bundle must
 * ship a PNG sprite instead. Keeping these in the launcher rather than in a shared
 * module is therefore not laziness — it is the renderer boundary.
 */

import type { ComponentType, SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Svg({ size = 16, children, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const PlayIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 4l14 8-14 8V4z" fill="currentColor" stroke="none" />
  </Svg>
);

export const LayersIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 7h16M4 12h16M4 17h16" />
  </Svg>
);

export const ShirtIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="8" />
    <path d="M7 12h10" />
  </Svg>
);

export const ServerIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="7" cy="7" r="1.6" fill="currentColor" />
    <circle cx="17" cy="7" r="1.6" fill="currentColor" />
    <circle cx="7" cy="17" r="1.6" fill="currentColor" />
    <circle cx="17" cy="17" r="1.6" fill="currentColor" />
    <path d="M9 7h6M7 9v6M17 9v6M9 17h6" />
  </Svg>
);

export const UsersIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="8" cy="8" r="1.8" fill="currentColor" />
    <circle cx="16" cy="8" r="1.8" fill="currentColor" />
    <circle cx="8" cy="16" r="1.8" fill="currentColor" />
    <circle cx="16" cy="16" r="1.8" fill="currentColor" />
  </Svg>
);

export const SearchIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="M20 20l-3.6-3.6" />
  </Svg>
);

export const GearIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="3.2" />
    <path d="M12 2.8v2.4M12 18.8v2.4M4.5 7.5l2 1.2M17.5 15.3l2 1.2M4.5 16.5l2-1.2M17.5 8.7l2-1.2" />
  </Svg>
);

export const SlidersIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 8h10M18 8h2M4 16h4M12 16h8" />
    <circle cx="16" cy="8" r="2" />
    <circle cx="10" cy="16" r="2" />
  </Svg>
);

export const ChevronDown = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 9.5l6 6 6-6" />
  </Svg>
);

export const SwordIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M14.5 3.5L20.5 3.5L20.5 9.5L10 20L4 14L14.5 3.5Z" />
    <path d="M7 17l-3 3" />
  </Svg>
);

export const BedIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 18v-8h13a4 4 0 014 4v4" />
    <path d="M3 14h18" />
    <circle cx="7" cy="12" r="1.6" fill="currentColor" />
  </Svg>
);

export const MoveIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3v18M3 12h18M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3" />
  </Svg>
);

export const XIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 6l12 12M18 6L6 18" />
  </Svg>
);

export const MinusIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 12h14" />
  </Svg>
);

export const SquareIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="5.5" y="5.5" width="13" height="13" rx="2.5" />
  </Svg>
);

export const TerminalIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 7l4 4-4 4M12 16h7" />
  </Svg>
);

export const CheckIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 12.5l4.5 4.5L19 7" />
  </Svg>
);

export const StarIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3.5l2.6 5.6 6 .8-4.4 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6L3.4 9.9l6-.8L12 3.5z" />
  </Svg>
);

export const PlusIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);

export const TrashIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" />
  </Svg>
);

export const EyeIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z" />
    <circle cx="12" cy="12" r="2.6" />
  </Svg>
);

export const SunIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M19 5l-1.5 1.5M6.5 17.5L5 19" />
  </Svg>
);

export const CrosshairIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="7" />
    <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
  </Svg>
);

export const GaugeIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 15a8 8 0 1116 0" />
    <path d="M12 15l4-4" />
  </Svg>
);

export const KeyboardIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3" y="6.5" width="18" height="11" rx="2.5" />
    <path d="M7 10h.01M11 10h.01M15 10h.01M8 14h8" />
  </Svg>
);

export const MouseIcon = (p: IconProps) => (
  <Svg {...p}>
    <rect x="7.5" y="3.5" width="9" height="17" rx="4.5" />
    <path d="M12 7v3" />
  </Svg>
);

export const ShieldIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6l7-3z" />
  </Svg>
);

export const FlaskIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M10 3v6l-5 9a2 2 0 001.8 3h10.4a2 2 0 001.8-3l-5-9V3" />
    <path d="M9 3h6" />
  </Svg>
);

export const WifiIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 9a12 12 0 0116 0M7 13a7 7 0 0110 0" />
    <circle cx="12" cy="17.5" r="1.2" fill="currentColor" />
  </Svg>
);

export const CompassIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="8" />
    <path d="M15 9l-2 4-4 2 2-4 4-2z" />
  </Svg>
);

export const BoxIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z" />
    <path d="M4 7.5l8 4.5 8-4.5M12 12v9" />
  </Svg>
);

export const ZoomIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="11" cy="11" r="6.5" />
    <path d="M20 20l-3.6-3.6M8.5 11h5M11 8.5v5" />
  </Svg>
);

export const RunIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="14" cy="5" r="1.8" fill="currentColor" />
    <path d="M9 21l3-5-2.5-3 1-5 3.5 2 2.5 1M6 12l3-2" />
  </Svg>
);

/** The 12 mod glyphs, by mod id. */
export const MOD_ICONS: Record<string, ComponentType<IconProps>> = {
  fps: GaugeIcon,
  keystrokes: KeyboardIcon,
  cps: MouseIcon,
  ping: WifiIcon,
  coordinates: CompassIcon,
  armor_status: ShieldIcon,
  potion_effects: FlaskIcon,
  toggle_sprint: RunIcon,
  fullbright: SunIcon,
  hitboxes: BoxIcon,
  zoom: ZoomIcon,
  crosshair: CrosshairIcon,
};

/** Loadout card / picker glyphs, by the loadout's `icon` name. */
export const LOADOUT_ICONS: Record<string, ComponentType<IconProps>> = {
  sword: SwordIcon,
  bed: BedIcon,
  box: BoxIcon,
  shield: ShieldIcon,
};
