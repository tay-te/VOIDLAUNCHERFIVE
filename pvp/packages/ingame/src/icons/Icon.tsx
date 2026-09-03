/**
 * The overlay icon set.
 *
 * ultralight-notes.md §7 rates inline SVG **[risky]** in Ultralight and asks for
 * a PNG sprite sheet at 2×/3×. That sprite is a design deliverable that does not
 * exist yet, so these are deliberately conservative SVGs: single `<path>` or
 * primitive shapes, uniform `stroke-width`, `stroke-linecap="round"`,
 * `stroke-linejoin="round"`, no gradients, no masks, no `vector-effect`,
 * no filters. Every icon in the bundle goes through this one component, so
 * swapping to `background-position` off a sprite is a change to this file only.
 */

import type { CSSProperties } from 'react';

export type IconName =
  | 'gauge'
  | 'keyboard'
  | 'click'
  | 'footprints'
  | 'crosshair'
  | 'zoom'
  | 'sun'
  | 'box'
  | 'shield'
  | 'flask'
  | 'wifi'
  | 'compass'
  | 'sword'
  | 'heart'
  | 'move'
  | 'check'
  | 'layers'
  | 'rotate-ccw'
  | 'x'
  | 'search'
  | 'settings'
  | 'monitor'
  | 'eye'
  | 'chevron-down'
  | 'arrow-left'
  | 'users'
  | 'play'
  | 'plus';

const PATHS: Record<IconName, string> = {
  gauge: 'M12 14l4-4M20.5 15a9 9 0 1 0-17 0',
  keyboard:
    'M3 6h18v12H3zM7 10h.01M11 10h.01M15 10h.01M17 10h.01M7 14h10',
  click:
    'M9 9l3 9 1.5-4.5L18 12zM6 3v2M3 6h2M3.5 11.5l1.5-1M11.5 3.5l-1 1.5',
  footprints:
    'M4 16v-2.4a2.6 2.6 0 1 1 5.2 0V16zM4 16h5.2v2.5A2.6 2.6 0 0 1 4 18.5zM14.8 12V9.6a2.6 2.6 0 1 1 5.2 0V12zM14.8 12H20v2.5a2.6 2.6 0 0 1-5.2 0z',
  crosshair: 'M12 3v4M12 17v4M3 12h4M17 12h4',
  zoom: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-3.8-3.8M8.5 11h5M11 8.5v5',
  sun: 'M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  box: 'M20.5 7.5L12 3 3.5 7.5v9L12 21l8.5-4.5zM3.5 7.5L12 12M12 12l8.5-4.5M12 12v9',
  shield: 'M12 3l7 3v5.5c0 4.2-2.9 7.6-7 9.5-4.1-1.9-7-5.3-7-9.5V6z',
  flask: 'M10 3h4M10.5 3v6L5.5 18a2 2 0 0 0 1.7 3h9.6a2 2 0 0 0 1.7-3l-5-9V3M8 14h8',
  wifi: 'M2.5 9a15 15 0 0 1 19 0M6 12.7a10 10 0 0 1 12 0M9.5 16.4a5 5 0 0 1 5 0M12 20h.01',
  compass:
    'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5.5-5.5 2 2-5.5z',
  sword: 'M18.5 3H21v2.5L11 15.5 8.5 13zM8.5 13L5 16.5 7.5 19 11 15.5M4 19l1.5 1.5',
  heart:
    'M12 20s-7-4.4-7-9.3A3.9 3.9 0 0 1 8.9 7c1.4 0 2.5.7 3.1 1.7A3.6 3.6 0 0 1 15.1 7 3.9 3.9 0 0 1 19 10.7c0 4.9-7 9.3-7 9.3z',
  move: 'M12 3v18M3 12h18M12 3l-2.5 2.5M12 3l2.5 2.5M12 21l-2.5-2.5M12 21l2.5-2.5M3 12l2.5-2.5M3 12l2.5 2.5M21 12l-2.5-2.5M21 12l-2.5 2.5',
  check: 'M4.5 12.5l5 5 10-11',
  layers: 'M12 3l9 4.5-9 4.5-9-4.5zM3 12.5L12 17l9-4.5M3 16.8L12 21.3l9-4.5',
  'rotate-ccw': 'M4 5v5h5M4.6 14a8 8 0 1 0 1-6.4L4 10',
  x: 'M6 6l12 12M18 6L6 18',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4',
  settings:
    'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM19.4 14.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.1a2 2 0 1 1-4 0v-.2a1.7 1.7 0 0 0-2.9-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H3a2 2 0 1 1 0-4h.2a1.7 1.7 0 0 0 1.1-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 2.9-1.2V3a2 2 0 1 1 4 0v.2a1.7 1.7 0 0 0 2.9 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0 1.2 2.9H21a2 2 0 1 1 0 4h-.2a1.7 1.7 0 0 0-1.4 1z',
  monitor: 'M3 4.5h18v11H3zM8.5 20h7M12 15.5V20',
  eye: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  'chevron-down': 'M6 9.5l6 6 6-6',
  'arrow-left': 'M19 12H5M11 6l-6 6 6 6',
  users:
    'M16 20v-1.6a3.4 3.4 0 0 0-3.4-3.4H6.4A3.4 3.4 0 0 0 3 18.4V20M9.5 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM21 20v-1.6a3.4 3.4 0 0 0-2.6-3.3M15.5 4.7a3.4 3.4 0 0 1 0 6.6',
  play: 'M7 4.5l12 7.5-12 7.5z',
  plus: 'M12 5v14M5 12h14',
};

/** Icons that read better filled than stroked. */
const FILLED = new Set<IconName>(['play']);

export interface IconProps {
  name: IconName;
  /** Box size in px. The design uses 13 / 14 / 16 / 22. */
  size?: number;
  /** Stroke colour. Defaults to the inherited text colour. */
  color?: string;
  strokeWidth?: number;
  className?: string;
  style?: CSSProperties;
}

export function Icon({
  name,
  size = 16,
  color = 'currentColor',
  strokeWidth = 1.75,
  className,
  style,
}: IconProps) {
  const filled = FILLED.has(name);
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? color : 'none'}
      stroke={filled ? 'none' : color}
      strokeWidth={filled ? undefined : strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={{ display: 'block', flex: '0 0 auto', ...style }}
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
