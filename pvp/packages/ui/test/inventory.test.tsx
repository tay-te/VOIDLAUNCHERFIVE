/**
 * Coverage of the design README's component inventory.
 *
 * These are smoke tests in the literal sense: every component in the inventory is
 * rendered in every state that README lists, and the states that are supposed to be
 * distinguishable are asserted to be distinguishable. They are what stops a rename or a
 * dropped modifier from reaching `packages/ingame` and `apps/desktop`, which import
 * these names directly.
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import * as UI from '../src/index.js';
import {
  ArmorList,
  Avatar,
  Badge,
  Button,
  Card,
  CoordsChip,
  CpsChip,
  Crosshair,
  Divider,
  Dock,
  EditPositionButton,
  EditorToolbar,
  FpsChip,
  FriendsOnline,
  GroupCaption,
  HintBar,
  Hotbar,
  Icon,
  IconButton,
  IconWell,
  Kbd,
  KeystrokesPreview,
  KeystrokesWidget,
  LaunchButton,
  LoadoutCard,
  LoadoutPicker,
  ModGrid,
  ModSettingsPanel,
  ModSettingsRow,
  ModTile,
  NavItem,
  Pane,
  Panel,
  PingChip,
  PlayerChip,
  PositionChips,
  PotionList,
  SearchBar,
  SelectionFrame,
  SettingsGroup,
  SettingsRow,
  Sparkline,
  StatTile,
  StatusDot,
  StatusPill,
  Swatches,
  Tag,
  Tool,
  TopNav,
  VersionPicker,
  formatAmplifier,
  formatPotionTime,
  formatSelectionReadout,
  ICON_NAMES,
  MOD_ICONS,
  cx,
} from '../src/index.js';

/* -------------------------------------------------------------------------- */
/* The exported surface                                                       */
/* -------------------------------------------------------------------------- */

/**
 * Names other packages import. Renaming any of these is a breaking change for
 * `packages/ingame` and `apps/desktop`; if one has to go, keep an alias.
 */
const PUBLIC_API = [
  // tokens + renderer
  'TOKEN_NAMES',
  'RENDERERS',
  'setRenderer',
  'getRenderer',
  'setGlBlur',
  'cx',
  // icons
  'Icon',
  'setIconRenderer',
  'getIconRenderer',
  'ICON_NAMES',
  'MOD_ICONS',
  'resolveLoadoutIcon',
  // primitives
  'Button',
  'IconButton',
  'Card',
  'Panel',
  'Kbd',
  'Tag',
  'Badge',
  'Avatar',
  'IconWell',
  'Divider',
  'StatusDot',
  'StatusPill',
  // controls
  'Toggle',
  'Slider',
  'KeybindChip',
  'FilterTabs',
  // chrome
  'TopNav',
  'NavItem',
  'SearchBar',
  'Dock',
  'PlayerChip',
  'LoadoutPicker',
  'VersionPicker',
  'LaunchButton',
  'FriendsOnline',
  // mods
  'ModGrid',
  'ModTile',
  'ModSettingsPanel',
  'ModSettingsRow',
  'KeystrokesPreview',
  'EditPositionButton',
  'SettingsGroup',
  'SettingsRow',
  'Swatches',
  'PositionChips',
  // cards
  'LoadoutCard',
  'Pane',
  'StatTile',
  'Sparkline',
  'GroupCaption',
  'BackButton',
  // HUD
  'FpsChip',
  'PingChip',
  'CoordsChip',
  'CpsChip',
  'PotionList',
  'ArmorList',
  'KeystrokesWidget',
  'Crosshair',
  'Hotbar',
  'formatPotionTime',
  'formatAmplifier',
  // HUD editor
  'EditorToolbar',
  'Tool',
  'SelectionFrame',
  'HintBar',
  'formatSelectionReadout',
] as const;

describe('the public API', () => {
  it.each(PUBLIC_API)('exports %s', (name) => {
    expect(UI[name as keyof typeof UI]).toBeDefined();
  });

  it('exports nothing that is undefined', () => {
    for (const [name, value] of Object.entries(UI)) {
      expect(value, `${name} is exported but undefined`).toBeDefined();
    }
  });
});

/* -------------------------------------------------------------------------- */
/* Shell chrome                                                               */
/* -------------------------------------------------------------------------- */

describe('shell chrome', () => {
  it('TopNav lays out the five nav items with one active', () => {
    render(
      <TopNav right={<IconButton icon="settings" label="Settings" />}>
        <NavItem active icon="play">
          Play
        </NavItem>
        <NavItem icon="layers">Mods</NavItem>
        <NavItem icon="sparkle">Cosmetics</NavItem>
        <NavItem icon="box">Servers</NavItem>
        <NavItem icon="users">Friends</NavItem>
      </TopNav>,
    );
    expect(screen.getByRole('navigation')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Play/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: /Mods/ })).not.toHaveAttribute('aria-current');
  });

  it('SearchBar shows the ⌘K hint in the nav variant and drops it in the panel one', () => {
    const { rerender, container } = render(<SearchBar placeholder="Ask VOID anything" />);
    expect(screen.getByText('⌘K')).toBeInTheDocument();
    expect(container.querySelector('.v-searchbar__dot')).not.toBeNull();

    rerender(<SearchBar variant="panel" placeholder="Search" />);
    expect(screen.queryByText('⌘K')).not.toBeInTheDocument();
    expect(container.querySelector('.v-searchbar--panel')).not.toBeNull();
  });

  it('SearchBar is controllable', () => {
    const onChange = vi.fn();
    render(<SearchBar value="hypixel" onChange={onChange} />);
    expect(screen.getByRole('searchbox')).toHaveValue('hypixel');
  });

  it('StatusPill carries the live status LED', () => {
    const { container } = render(<StatusPill tone="warn">VOID PVP · 1.8.9</StatusPill>);
    expect(container.querySelector('.v-dot--warn')).not.toBeNull();
    expect(screen.getByText('VOID PVP · 1.8.9')).toBeInTheDocument();
  });
});

/* -------------------------------------------------------------------------- */
/* Launcher dock                                                              */
/* -------------------------------------------------------------------------- */

describe('launcher dock', () => {
  it('assembles identity, pickers, CTA and friends', () => {
    render(
      <Dock>
        <PlayerChip name="Searge" level="Lvl 42" />
        <Divider />
        <LoadoutPicker value="Sword PvP" />
        <VersionPicker value="1.8.9" />
        <LaunchButton />
        <Divider />
        <FriendsOnline friends={[{ name: 'marrow' }, { name: 'nine' }, { name: 'ash' }]} />
        <IconButton icon="settings" size="dock" label="Launcher settings" />
      </Dock>,
    );
    expect(screen.getByText('Searge')).toBeInTheDocument();
    expect(screen.getByText('Lvl 42')).toBeInTheDocument();
    expect(screen.getByText('LOADOUT')).toBeInTheDocument();
    expect(screen.getByText('Sword PvP')).toBeInTheDocument();
    expect(screen.getByText('VERSION')).toBeInTheDocument();
    expect(screen.getByText('1.8.9')).toBeInTheDocument();
    expect(screen.getByText('Launch')).toBeInTheDocument();
    expect(screen.getByText('⌘↵')).toBeInTheDocument();
    expect(screen.getByText('3 online')).toBeInTheDocument();
  });

  it('LaunchButton reads differently in each of its three states', () => {
    const { rerender } = render(<LaunchButton state="idle" />);
    expect(screen.getByText('Launch')).toBeInTheDocument();
    expect(screen.getByText('⌘↵')).toBeInTheDocument();

    rerender(<LaunchButton state="launching" />);
    expect(screen.getByText('Launching…')).toBeInTheDocument();
    expect(screen.getByRole('button')).toHaveAttribute('aria-busy', 'true');
    // The keyboard hint is only meaningful while the shortcut does something.
    expect(screen.queryByText('⌘↵')).not.toBeInTheDocument();

    rerender(<LaunchButton state="running" />);
    expect(screen.getByText('Playing')).toBeInTheDocument();
  });

  it('LaunchButton can be disabled', () => {
    render(<LaunchButton disabled />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('the pickers announce that they open a menu, and their open state', () => {
    const { rerender } = render(<LoadoutPicker value="Sword PvP" />);
    const pill = screen.getByRole('button');
    expect(pill).toHaveAttribute('aria-haspopup', 'menu');
    expect(pill).toHaveAttribute('aria-expanded', 'false');
    rerender(<LoadoutPicker value="Sword PvP" open />);
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true');
  });

  it('FriendsOnline draws at most three heads but counts them all', () => {
    const { container } = render(
      <FriendsOnline
        total={9}
        friends={[{ name: 'a' }, { name: 'b' }, { name: 'c' }, { name: 'd' }]}
      />,
    );
    expect(container.querySelectorAll('.v-friends__head')).toHaveLength(3);
    expect(screen.getByText('9 online')).toBeInTheDocument();
  });
});

/* -------------------------------------------------------------------------- */
/* Panels                                                                     */
/* -------------------------------------------------------------------------- */

describe('Panel', () => {
  it('renders title, subtitle, header controls and the footer hint', () => {
    render(
      <Panel
        title="Loadouts"
        subtitle="A loadout is which mods are on, their settings and HUD layout."
        headerRight={<Button variant="raised">New loadout</Button>}
        footer="Switching applies instantly"
      >
        body
      </Panel>,
    );
    expect(screen.getByRole('heading', { name: 'Loadouts' })).toBeInTheDocument();
    expect(screen.getByText(/A loadout is which mods are on/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New loadout' })).toBeInTheDocument();
    expect(screen.getByText('Switching applies instantly')).toBeInTheDocument();
  });

  it('only the overlay panel gets a close button, and it calls back', async () => {
    const onClose = vi.fn();
    const { rerender } = render(<Panel title="Mods">body</Panel>);
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();

    rerender(
      <Panel title="Mods" surface="overlay" onClose={onClose}>
        body
      </Panel>,
    );
    screen.getByRole('button', { name: 'Close' }).click();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('takes the overlay height modifier', () => {
    const { container } = render(<Panel surface="overlay">body</Panel>);
    expect(container.querySelector('.v-panel--overlay')).not.toBeNull();
  });
});

/* -------------------------------------------------------------------------- */
/* Buttons and atoms                                                          */
/* -------------------------------------------------------------------------- */

describe('buttons and atoms', () => {
  it.each([
    ['accent', 'v-btn--accent'],
    ['raised', 'v-btn--raised'],
    ['ghost', 'v-btn--ghost'],
    ['chip', 'v-btn--chip'],
    ['text', 'v-btn--text'],
  ] as const)('Button variant %s maps to %s', (variant, className) => {
    const { container } = render(<Button variant={variant}>Go</Button>);
    expect(container.querySelector(`.${className}`)).not.toBeNull();
  });

  it('the chip-accent variant carries both classes', () => {
    const { container } = render(<Button variant="chip-accent">Join</Button>);
    expect(container.querySelector('.v-btn--chip.v-btn--accent')).not.toBeNull();
  });

  it('IconButton always has an accessible name', () => {
    render(<IconButton icon="close" size="close" label="Close" />);
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
  });

  it('Card marks selection with the accent border modifier', () => {
    const { container, rerender } = render(<Card />);
    expect(container.querySelector('.v-card--selected')).toBeNull();
    rerender(<Card selected />);
    expect(container.querySelector('.v-card--selected')).not.toBeNull();
  });

  it('Kbd has three flavours', () => {
    const { container } = render(
      <>
        <Kbd flavour="nav">⌘K</Kbd>
        <Kbd flavour="accent">⌘↵</Kbd>
        <Kbd flavour="palette">↵</Kbd>
      </>,
    );
    expect(container.querySelector('.v-kbd--nav')).not.toBeNull();
    expect(container.querySelector('.v-kbd--accent')).not.toBeNull();
    expect(container.querySelector('.v-kbd--palette')).not.toBeNull();
  });

  it('Badge has an accent, an ok and a solid tone', () => {
    const { container } = render(
      <>
        <Badge>Active</Badge>
        <Badge tone="ok">Ready</Badge>
        <Badge tone="solid">New</Badge>
      </>,
    );
    expect(container.querySelectorAll('.v-badge')).toHaveLength(3);
    expect(container.querySelector('.v-badge--ok')).not.toBeNull();
    expect(container.querySelector('.v-badge--solid')).not.toBeNull();
  });

  it('Avatar falls back to initials and can carry a presence dot', () => {
    const { container } = render(<Avatar name="marrow" size={36} presence="online" />);
    expect(screen.getByText('ma')).toBeInTheDocument();
    expect(container.querySelector('.v-avatar__presence--online')).not.toBeNull();
  });

  it('Avatar renders an image when given one', () => {
    render(<Avatar name="Searge" src="/searge.png" size={44} />);
    expect(screen.getByRole('img', { name: 'Searge' })).toHaveAttribute('src', '/searge.png');
  });

  it('IconWell tints for on and fills solid for active', () => {
    const { container } = render(
      <>
        <IconWell icon="sun" size={34} />
        <IconWell icon="keyboard" size={34} on />
        <IconWell icon="sword" size={44} solid />
      </>,
    );
    expect(container.querySelectorAll('.v-icon-well--on')).toHaveLength(1);
    expect(container.querySelectorAll('.v-icon-well--solid')).toHaveLength(1);
    expect(container.querySelectorAll('.v-icon-well--44')).toHaveLength(1);
  });

  it('StatusDot has the four tones', () => {
    const { container } = render(
      <>
        <StatusDot tone="ok" />
        <StatusDot tone="warn" />
        <StatusDot tone="muted" />
        <StatusDot tone="accent" />
      </>,
    );
    expect(container.querySelector('.v-dot--ok')).not.toBeNull();
    expect(container.querySelector('.v-dot--warn')).not.toBeNull();
    expect(container.querySelector('.v-dot--accent')).not.toBeNull();
  });

  it('Tag and Divider render', () => {
    const { container } = render(
      <>
        <Tag>HUD</Tag>
        <Divider />
        <Divider toolbar />
      </>,
    );
    expect(screen.getByText('HUD')).toBeInTheDocument();
    expect(container.querySelectorAll('.v-divider')).toHaveLength(2);
    expect(container.querySelectorAll('.v-divider--toolbar')).toHaveLength(1);
  });
});

/* -------------------------------------------------------------------------- */
/* Icons                                                                      */
/* -------------------------------------------------------------------------- */

describe('Icon', () => {
  it.each(ICON_NAMES)('draws %s', (name) => {
    const { container } = render(<Icon name={name} size={16} />);
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg).toHaveAttribute('width', '16');
    expect(svg?.querySelectorAll('path').length).toBeGreaterThan(0);
  });

  it('has an icon for every one of the 12 mods', () => {
    for (const [mod, icon] of Object.entries(MOD_ICONS)) {
      expect(ICON_NAMES, `${mod} -> ${icon}`).toContain(icon);
    }
    expect(Object.keys(MOD_ICONS)).toHaveLength(12);
  });

  it('is hidden from assistive tech — every icon has a labelled parent', () => {
    const { container } = render(<Icon name="sword" />);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('can be swapped for a sprite-sheet renderer', () => {
    UI.setIconRenderer(({ name }) => <span data-sprite={name} />);
    const { container } = render(<Icon name="sword" />);
    expect(container.querySelector('[data-sprite="sword"]')).not.toBeNull();
    expect(container.querySelector('svg')).toBeNull();
    UI.setIconRenderer(null);
  });

  it('resolves a loadout icon name, falling back to box', () => {
    expect(UI.resolveLoadoutIcon('sword')).toBe('sword');
    expect(UI.resolveLoadoutIcon('bed')).toBe('bed');
    expect(UI.resolveLoadoutIcon('not-a-real-icon')).toBe('box');
  });
});

/* -------------------------------------------------------------------------- */
/* Mods                                                                       */
/* -------------------------------------------------------------------------- */

describe('mods', () => {
  it('ModTile shows the name, the tag, the switch and the well tint', () => {
    const { container } = render(
      <ModGrid>
        <ModTile name="Keystrokes" category="HUD" icon="keyboard" on />
      </ModGrid>,
    );
    expect(screen.getByText('Keystrokes')).toBeInTheDocument();
    expect(screen.getByText('HUD')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Keystrokes enabled' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(container.querySelector('.v-icon-well--on')).not.toBeNull();
  });

  it('ModTile off drops the well tint', () => {
    const { container } = render(
      <ModTile name="Fullbright" category="VISUAL" icon="sun" on={false} />,
    );
    expect(container.querySelector('.v-icon-well--on')).toBeNull();
  });

  it('ModTile selection is a border, never a fill change', () => {
    const { container, rerender } = render(<ModTile name="Zoom" category="UTILITY" on />);
    expect(container.querySelector('.v-modtile--selected')).toBeNull();
    rerender(<ModTile name="Zoom" category="UTILITY" on selected />);
    expect(container.querySelector('.v-modtile--selected')).not.toBeNull();
    expect(screen.getByRole('button', { name: /Zoom/ })).toHaveAttribute('aria-pressed', 'true');
  });

  it('ModTile separates selecting the tile from flipping its switch', () => {
    const onSelect = vi.fn();
    const onToggle = vi.fn();
    render(
      <ModTile name="Zoom" category="UTILITY" on onSelect={onSelect} onToggle={onToggle} />,
    );
    screen.getByRole('switch').click();
    expect(onToggle).toHaveBeenCalledWith(false);
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('KeystrokesPreview lights only the pressed caps', () => {
    const { container } = render(<KeystrokesPreview keys={{ w: true, d: true, lmb: true }} />);
    const pressed = [...container.querySelectorAll('.v-kspreview__key--pressed')].map(
      (el) => el.textContent,
    );
    expect(pressed.sort()).toEqual(['D', 'LMB', 'W']);
    expect(container.querySelectorAll('.v-kspreview__key')).toHaveLength(6);
  });

  it('ModSettingsPanel puts the M switch in its header', () => {
    const { container } = render(
      <ModSettingsPanel title="Keystrokes" on>
        <ModSettingsRow label="Keybind">chip</ModSettingsRow>
      </ModSettingsPanel>,
    );
    expect(screen.getByText('Keystrokes')).toBeInTheDocument();
    expect(container.querySelector('.v-toggle--m')).not.toBeNull();
    expect(screen.getByText('Keybind')).toBeInTheDocument();
  });

  it('EditPositionButton is a real button with the move glyph', () => {
    const onClick = vi.fn();
    const { container } = render(<EditPositionButton onClick={onClick} />);
    const button = screen.getByRole('button', { name: /Edit position/ });
    button.click();
    expect(onClick).toHaveBeenCalledOnce();
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('SettingsGroup and SettingsRow draw the caption, sub lines and seams', () => {
    const { container } = render(
      <SettingsGroup caption="Behaviour">
        <SettingsRow title="Show mouse buttons" sub="LMB and RMB under the arrows" />
        <SettingsRow seam title="Show CPS" sub="Clicks per second for both buttons" />
      </SettingsGroup>,
    );
    expect(screen.getByText('Behaviour')).toBeInTheDocument();
    expect(screen.getByText('LMB and RMB under the arrows')).toBeInTheDocument();
    expect(container.querySelectorAll('.v-seam')).toHaveLength(1);
  });

  it('SettingsRow puts a value in the right-hand column', () => {
    const { container } = render(<SettingsRow title="Scale" value="1.0×" />);
    expect(container.querySelector('.v-group__value')).toHaveTextContent('1.0×');
  });

  it('Swatches is a radiogroup that reports its pick', () => {
    const onChange = vi.fn();
    render(
      <Swatches
        value="shell"
        onChange={onChange}
        swatches={[
          { id: 'shell', color: '#0a0b0c', label: 'Shell' },
          { id: 'sky', color: '#4d87cd', label: 'Sky' },
        ]}
      />,
    );
    expect(screen.getByRole('radio', { name: 'Shell' })).toBeChecked();
    screen.getByRole('radio', { name: 'Sky' }).click();
    expect(onChange).toHaveBeenCalledWith('sky');
  });

  it('PositionChips marks exactly one chip selected', () => {
    const { container } = render(
      <PositionChips
        value="bottom-left"
        options={[
          { id: 'top-left', label: 'Top left' },
          { id: 'top-right', label: 'Top right' },
          { id: 'bottom-left', label: 'Bottom left' },
          { id: 'bottom-right', label: 'Bottom right' },
        ]}
      />,
    );
    expect(container.querySelectorAll('.v-chip--selected')).toHaveLength(1);
    expect(screen.getByRole('radio', { checked: true })).toHaveTextContent('Bottom left');
  });
});

/* -------------------------------------------------------------------------- */
/* Cards                                                                      */
/* -------------------------------------------------------------------------- */

describe('LoadoutCard', () => {
  const includes = [
    { label: 'Keystrokes' },
    { label: 'CPS' },
    { label: 'Toggle sprint' },
  ];

  it('the active card badges itself and offers no switch action', () => {
    const { container } = render(
      <LoadoutCard
        name="Sword PvP"
        icon="sword"
        active
        meta="24 mods on   ·   Hypixel  ·  1.8.9"
        includes={includes}
        moreCount={18}
        stats={[
          { value: '142', unit: 'fps avg' },
          { value: '4h 20m', unit: 'played' },
        ]}
      />,
    );
    expect(container.querySelector('.v-loadoutcard--active')).not.toBeNull();
    expect(screen.getByText('Active', { selector: '.v-badge' })).toBeInTheDocument();
    // The button on the current loadout is a state, not an action.
    expect(screen.getByRole('button')).toBeDisabled();
    expect(screen.getByText('142')).toBeInTheDocument();
    expect(screen.getByText('4h 20m')).toBeInTheDocument();
    expect(screen.getByText('+ 18 more')).toBeInTheDocument();
    expect(container.querySelectorAll('.v-includes-chip')).toHaveLength(3);
  });

  it('an inactive card offers Switch to <name>', () => {
    const onSwitch = vi.fn();
    render(<LoadoutCard name="Bedwars" icon="bed" includes={includes} onSwitch={onSwitch} />);
    const button = screen.getByRole('button', { name: 'Switch to Bedwars' });
    button.click();
    expect(onSwitch).toHaveBeenCalledOnce();
  });

  it('falls back to the box icon for an unknown loadout icon name', () => {
    const { container } = render(<LoadoutCard name="Custom" icon="not-real" />);
    expect(container.querySelector('svg')).not.toBeNull();
  });
});

describe('panes and stats', () => {
  it('Pane renders its heading and aside', () => {
    render(
      <Pane heading="Your party" headingAside={<span>2 / 4</span>}>
        rows
      </Pane>,
    );
    expect(screen.getByText('Your party')).toBeInTheDocument();
    expect(screen.getByText('2 / 4')).toBeInTheDocument();
  });

  it('StatTile shows value over unit', () => {
    render(<StatTile value="42 ms" unit="ping" />);
    expect(screen.getByText('42 ms')).toBeInTheDocument();
    expect(screen.getByText('ping')).toBeInTheDocument();
  });

  it('Sparkline draws one div per bar, marking the current one and the outliers', () => {
    const { container } = render(
      <Sparkline values={[18, 20, 16, 22, 18, 32, 25, 18, 16, 18, 17, 18]} outliers={[5]} />,
    );
    expect(container.querySelectorAll('.v-sparkline__bar')).toHaveLength(12);
    expect(container.querySelectorAll('.v-sparkline__bar--current')).toHaveLength(1);
    expect(container.querySelectorAll('.v-sparkline__bar--outlier')).toHaveLength(1);
    // Never a canvas: Ultralight has no WebGL and its 2D canvas is slow.
    expect(container.querySelector('canvas')).toBeNull();
  });

  it('GroupCaption pairs a caption with a count', () => {
    render(<GroupCaption label="Online" count="· 3" />);
    expect(screen.getByText('Online')).toBeInTheDocument();
    expect(screen.getByText('· 3')).toBeInTheDocument();
  });
});

/* -------------------------------------------------------------------------- */
/* HUD widgets                                                                */
/* -------------------------------------------------------------------------- */

describe('HUD widgets', () => {
  it('FpsChip shows the number, the unit and the 1% low', () => {
    render(<FpsChip fps={142} onePercentLow={96} />);
    expect(screen.getByText('142')).toBeInTheDocument();
    expect(screen.getByText('fps')).toBeInTheDocument();
    expect(screen.getByText(/1% low 96/)).toBeInTheDocument();
  });

  it('FpsChip can drop the label, as show_label: false asks', () => {
    render(<FpsChip fps={142} showLabel={false} />);
    expect(screen.queryByText('fps')).not.toBeInTheDocument();
  });

  it('PingChip colours the dot from the mod thresholds', () => {
    const { container, rerender } = render(<PingChip ping={42} host="Hypixel" />);
    expect(container.querySelector('.v-dot--ok')).not.toBeNull();
    rerender(<PingChip ping={112} host="Minemen" />);
    expect(container.querySelector('.v-dot--warn')).not.toBeNull();
  });

  it('PingChip shows an em dash for the -1 the schema uses for "unknown"', () => {
    render(<PingChip ping={-1} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('CoordsChip prints the three axes and the cardinal direction', () => {
    render(<CoordsChip x={118} y={64} z={-212} direction="NE" />);
    expect(screen.getByText('X 118')).toBeInTheDocument();
    expect(screen.getByText('Y 64')).toBeInTheDocument();
    expect(screen.getByText('Z -212')).toBeInTheDocument();
    expect(screen.getByText('NE')).toBeInTheDocument();
  });

  it('CoordsChip honours the decimals setting', () => {
    render(<CoordsChip x={118.25} y={64} z={-212} decimals={1} />);
    expect(screen.getByText('X 118.3')).toBeInTheDocument();
  });

  it('CpsChip renders both, left-only and right-only', () => {
    const { rerender } = render(<CpsChip left={12} right={9} />);
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('9')).toBeInTheDocument();
    expect(screen.getByText('|')).toBeInTheDocument();

    rerender(<CpsChip left={12} mode="left" />);
    expect(screen.queryByText('|')).not.toBeInTheDocument();
  });

  it('PotionList draws a swatch, a name and a timer per effect', () => {
    const { container } = render(
      <PotionList
        effects={[
          { name: 'Speed II', time: '1:24', color: '#7aebb5' },
          { name: 'Strength', time: '0:48', color: '#d9a93a' },
        ]}
      />,
    );
    expect(container.querySelectorAll('.v-potionlist__row')).toHaveLength(2);
    expect(screen.getByText('Speed II')).toBeInTheDocument();
    expect(screen.getByText('0:48')).toBeInTheDocument();
  });

  it('ArmorList fills each bar to the durability fraction and warns when low', () => {
    const { container } = render(
      <ArmorList
        rows={[
          { label: 'Helmet', remaining: 231, max: 363 },
          { label: 'Leggings', remaining: 188, max: 495 },
        ]}
      />,
    );
    const fills = container.querySelectorAll<HTMLElement>('.v-armorlist__fill');
    expect(fills).toHaveLength(2);
    expect(fills[0]!.style.width).toBe(`${(231 / 363) * 100}%`);
    expect(fills[0]!.className).not.toMatch(/warn/);
    // 188 / 495 is 38%, under the 50% threshold.
    expect(fills[1]!.className).toMatch(/warn/);
    expect(screen.getByText('231 / 363')).toBeInTheDocument();
  });

  it('KeystrokesWidget lights only the pressed caps and respects show_mouse', () => {
    const { container, rerender } = render(
      <KeystrokesWidget keys={{ w: true, d: true, lmb: true }} />,
    );
    expect(container.querySelectorAll('.v-keystrokes__key')).toHaveLength(6);
    expect(container.querySelectorAll('.v-keystrokes__key--pressed')).toHaveLength(3);

    rerender(<KeystrokesWidget keys={{ w: true }} showMouse={false} />);
    expect(container.querySelectorAll('.v-keystrokes__key')).toHaveLength(4);
  });

  it('KeystrokesWidget adds the space bar and the CPS readouts on request', () => {
    const { container } = render(
      <KeystrokesWidget keys={{ space: true }} showSpacebar cps={{ left: 12, right: 9 }} />,
    );
    expect(container.querySelector('.v-keystrokes__key--space')).not.toBeNull();
    expect(container.querySelectorAll('.v-keystrokes__cps')).toHaveLength(2);
  });

  it('HUD chips take the editor variant and the dimmed state', () => {
    const { container } = render(
      <>
        <FpsChip fps={142} variant="editor" />
        <FpsChip fps={142} dimmed />
      </>,
    );
    expect(container.querySelectorAll('.v-hudchip--editor')).toHaveLength(1);
    expect(container.querySelectorAll('.v-hudchip--dimmed')).toHaveLength(1);
  });

  it('Crosshair and Hotbar render', () => {
    const { container } = render(
      <>
        <Crosshair />
        <Hotbar slots={['#aaa', '#8c5c33', null, null, null, null, null, null, null]} />
      </>,
    );
    expect(container.querySelector('.v-crosshair')).not.toBeNull();
    expect(container.querySelectorAll('.v-hotbar__slot')).toHaveLength(9);
    expect(container.querySelectorAll('.v-hotbar__item')).toHaveLength(2);
  });

  it('formats potion durations and amplifiers the way the HUD prints them', () => {
    expect(formatPotionTime(84_000)).toBe('1:24');
    expect(formatPotionTime(48_000)).toBe('0:48');
    expect(formatPotionTime(0)).toBe('0:00');
    expect(formatAmplifier(0)).toBe('');
    expect(formatAmplifier(1)).toBe('II');
    expect(formatAmplifier(2)).toBe('III');
  });
});

/* -------------------------------------------------------------------------- */
/* HUD editor                                                                 */
/* -------------------------------------------------------------------------- */

describe('HUD editor', () => {
  it('EditorToolbar draws the mode label and the four tools', () => {
    render(<EditorToolbar />);
    expect(screen.getByText('HUD layout')).toBeInTheDocument();
    for (const label of ['Snap', 'Grid', 'Reset', 'Done']) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    }
  });

  it('Snap is active by default, and reports being turned off', () => {
    const onSnapChange = vi.fn();
    const { container } = render(<EditorToolbar onSnapChange={onSnapChange} />);
    expect(container.querySelector('.v-tool--active')).toHaveTextContent('Snap');
    screen.getByRole('button', { name: 'Snap' }).click();
    expect(onSnapChange).toHaveBeenCalledWith(false);
  });

  it('Done is the primary tool and calls back', () => {
    const onDone = vi.fn();
    const { container } = render(<EditorToolbar onDone={onDone} />);
    expect(container.querySelector('.v-tool--primary')).toHaveTextContent('Done');
    screen.getByRole('button', { name: 'Done' }).click();
    expect(onDone).toHaveBeenCalledOnce();
  });

  it('Tool has the four kinds; the mode label is not a button', () => {
    const { container } = render(
      <>
        <Tool kind="mode">HUD layout</Tool>
        <Tool>Grid</Tool>
        <Tool kind="active">Snap</Tool>
        <Tool kind="primary" hideIcon>
          Done
        </Tool>
      </>,
    );
    expect(container.querySelectorAll('.v-tool')).toHaveLength(4);
    expect(screen.getAllByRole('button')).toHaveLength(3);
  });

  it('SelectionFrame draws four grips and the live readout', () => {
    const { container } = render(
      <SelectionFrame name="Keystrokes" readout={formatSelectionReadout(32, 580, 1)} />,
    );
    expect(container.querySelectorAll('.v-selection__handle')).toHaveLength(4);
    expect(screen.getByText('Keystrokes')).toBeInTheDocument();
    // Compared on textContent, not getByText: the readout is spelled with non-breaking
    // spaces and Testing Library's default normalizer collapses those.
    expect(container.querySelector('.v-selection__readout')?.textContent).toBe(
      formatSelectionReadout(32, 580, 1),
    );
  });

  it('SelectionFrame can drop its grips and its label', () => {
    const { container } = render(<SelectionFrame name="Fps" hideHandles hideLabel />);
    expect(container.querySelectorAll('.v-selection__handle')).toHaveLength(0);
    expect(container.querySelector('.v-selection__label')).toBeNull();
  });

  it('SelectionFrame reports which grip was grabbed', () => {
    const onHandlePointerDown = vi.fn();
    const { container } = render(
      <SelectionFrame name="Keystrokes" onHandlePointerDown={onHandlePointerDown} />,
    );
    const se = container.querySelector('.v-selection__handle--se')!;
    se.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    expect(onHandlePointerDown).toHaveBeenCalledWith('se', expect.anything());
  });

  it('formats the position readout the way the frames print it', () => {
    // Non-breaking spaces around the dots, so the readout never wraps in the pill.
    const sep = '\u00a0\u00a0\u00b7\u00a0\u00a0';
    expect(formatSelectionReadout(32, 580)).toBe(`x 32${sep}y 580${sep}1.0\u00d7`);
    expect(formatSelectionReadout(-20.4, 0, 1.25)).toBe(`x -20${sep}y 0${sep}1.3\u00d7`);
  });

  it('HintBar joins its hints with a separator', () => {
    const { container } = render(
      <HintBar hints={['Drag to move', '⌥ drag to scale', 'Esc to exit']} />,
    );
    expect(screen.getByText('Drag to move')).toBeInTheDocument();
    expect(screen.getByText('Esc to exit')).toBeInTheDocument();
    expect(container.querySelectorAll('.v-hintbar__sep')).toHaveLength(2);
  });
});

/* -------------------------------------------------------------------------- */
/* cx                                                                         */
/* -------------------------------------------------------------------------- */

describe('cx', () => {
  it('joins truthy parts and drops everything else', () => {
    expect(cx('a', false, null, undefined, 'b', '')).toBe('a b');
    expect(cx()).toBe('');
  });
});
