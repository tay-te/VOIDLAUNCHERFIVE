/**
 * `@void/ui` — the shared React components and design tokens for VOID PVP.
 *
 * One React codebase, two bundles (§9): `apps/desktop` bundles this for the launcher's
 * system webview, `packages/ingame` bundles it for Ultralight inside the JVM. The
 * in-game renderer is the constraint, not the launcher — nothing here depends on
 * `backdrop-filter`, `mix-blend-mode`, `text-shadow`, a 3D transform, WebGL or video in
 * a way that breaks when they are unavailable.
 *
 * ## Stylesheets
 *
 * The components ship no runtime CSS-in-JS. Import the CSS once in your entry:
 *
 * ```ts
 * import '@void/ui/tokens.css';   // the design tokens + the renderer layers
 * import '@void/ui/fonts.css';    // the three bundled OFL families
 * import '@void/ui/styles.css';   // the component styles
 * ```
 *
 * …and put `data-renderer="webview"` or `data-renderer="ultralight"` on `<html>`; see
 * {@link setRenderer}. Everything under the root needs the `v-app` class, which is
 * where the reset and the type ramp live.
 *
 * @see README.md for the full consumption guide.
 */

/* --------------------------------------------------------------- tokens */

export { TOKEN_NAMES, RENDERERS } from './tokens.js';
export type { TokenName, Renderer } from './tokens.js';
export { setRenderer, getRenderer, setGlBlur } from './renderer.js';

/* ------------------------------------------------------------- utilities */

export { cx } from './lib/cx.js';

/* ----------------------------------------------------------------- icons */

export { Icon, setIconRenderer, getIconRenderer, ICON_NAMES, MOD_ICONS, resolveLoadoutIcon } from './components/Icon.js';
export type { IconName, IconProps, IconRenderer } from './components/Icon.js';

/* ------------------------------------------------------------ primitives */

export {
  Button,
  IconButton,
  Card,
  Panel,
  Kbd,
  Tag,
  Badge,
  Avatar,
  IconWell,
  Divider,
  StatusDot,
  StatusPill,
} from './components/primitives.js';
export type {
  ButtonProps,
  ButtonVariant,
  IconButtonProps,
  CardProps,
  PanelProps,
  KbdProps,
  BadgeProps,
  AvatarProps,
  IconWellProps,
  StatusDotProps,
  StatusPillProps,
} from './components/primitives.js';

/* -------------------------------------------------------------- controls */

export { Toggle, Slider, KeybindChip, FilterTabs } from './components/controls.js';
export type {
  ToggleProps,
  ToggleSize,
  SliderProps,
  KeybindChipProps,
  FilterTabsProps,
  FilterTab,
} from './components/controls.js';

/* ---------------------------------------------------------------- chrome */

export {
  TopNav,
  NavItem,
  SearchBar,
  Dock,
  PlayerChip,
  LoadoutPicker,
  VersionPicker,
  LaunchButton,
  FriendsOnline,
} from './components/chrome.js';
export type {
  TopNavProps,
  NavItemProps,
  SearchBarProps,
  PlayerChipProps,
  PickerProps,
  LaunchButtonProps,
  LaunchState,
  FriendsOnlineProps,
  FriendHead,
} from './components/chrome.js';

/* ------------------------------------------------------------------ mods */

export {
  ModGrid,
  ModTile,
  ModSettingsPanel,
  ModSettingsRow,
  KeystrokesPreview,
  EditPositionButton,
  SettingsGroup,
  SettingsRow,
  Swatches,
  PositionChips,
} from './components/mods.js';
export type {
  ModTileProps,
  ModSettingsPanelProps,
  ModSettingsRowProps,
  KeystrokesPreviewProps,
  EditPositionButtonProps,
  SettingsGroupProps,
  SettingsRowProps,
  Swatch,
  SwatchesProps,
  PositionChipsProps,
} from './components/mods.js';

/* ----------------------------------------------------------------- cards */

export {
  LoadoutCard,
  Pane,
  StatTile,
  Sparkline,
  GroupCaption,
  BackButton,
} from './components/cards.js';
export type {
  LoadoutCardProps,
  IncludesChip,
  LoadoutStat,
  PaneProps,
  StatTileProps,
  SparklineProps,
  GroupCaptionProps,
} from './components/cards.js';

/* ------------------------------------------------------------ HUD widgets */

export {
  FpsChip,
  PingChip,
  CoordsChip,
  CpsChip,
  PotionList,
  ArmorList,
  KeystrokesWidget,
  Crosshair,
  Hotbar,
  formatPotionTime,
  formatAmplifier,
} from './components/hud.js';
export type {
  HudVariant,
  HudChipProps,
  FpsChipProps,
  PingChipProps,
  CoordsChipProps,
  CpsChipProps,
  PotionListProps,
  PotionRow,
  ArmorListProps,
  ArmorRow,
  KeystrokesWidgetProps,
  KeystrokesState,
  HotbarProps,
} from './components/hud.js';

/* ------------------------------------------------------------- HUD editor */

export {
  EditorToolbar,
  Tool,
  SelectionFrame,
  HintBar,
  formatSelectionReadout,
} from './components/hud-editor.js';
export type {
  EditorToolbarProps,
  ToolProps,
  ToolKind,
  SelectionFrameProps,
  SelectionHandle,
  HintBarProps,
} from './components/hud-editor.js';
