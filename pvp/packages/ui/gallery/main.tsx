/**
 * The component gallery — the visual acceptance surface for `@void/ui`.
 *
 * Every component in the design README's inventory, in every state that README lists,
 * rendered on the `ground` background. The renderer toggle at the top switches
 * `data-renderer` on `<html>`, so the same page shows the launcher treatment (real
 * backdrop blur, the grain layer, a dashed selection box) and the in-game treatment
 * (baked surfaces, no blur, a solid selection box) side by side in time.
 *
 * Copy is taken verbatim from the frames wherever the frames have copy.
 */
import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

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
  FilterTabs,
  FpsChip,
  FriendsOnline,
  GroupCaption,
  HintBar,
  Hotbar,
  Icon,
  IconButton,
  IconWell,
  Kbd,
  KeybindChip,
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
  Slider,
  Sparkline,
  StatTile,
  StatusDot,
  StatusPill,
  Swatches,
  Tag,
  Toggle,
  Tool,
  TopNav,
  VersionPicker,
  formatSelectionReadout,
  setRenderer,
  type Renderer,
} from '@void/ui';
import { MOD_ICONS } from '@void/ui';
import { createFakeVoid, MOD_REGISTRY, type FakeVoid, type KeysPayload } from '@void/protocol';

import '../src/tokens.css';
import '../src/fonts.css';
import '../src/noise.css';
import '../src/styles/01-base.css';
import '../src/styles/02-primitives.css';
import '../src/styles/03-chrome.css';
import '../src/styles/04-mods.css';
import '../src/styles/05-cards.css';
import '../src/styles/06-hud.css';
import '../src/styles/07-hud-editor.css';
import './gallery.css';

/* -------------------------------------------------------------------------- */
/* Layout helpers                                                             */
/* -------------------------------------------------------------------------- */

function Specimen({
  name,
  state,
  dark = false,
  game = false,
  children,
}: {
  name: string;
  state?: string;
  dark?: boolean;
  game?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="g-specimen">
      <div className="g-specimen__head">
        <span className="g-specimen__name">{name}</span>
        {state ? <span className="g-specimen__state">{state}</span> : null}
      </div>
      <div
        className={[
          'g-specimen__body',
          dark ? 'g-specimen__body--dark' : '',
          game ? 'g-game' : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {children}
      </div>
    </div>
  );
}

const SECTIONS = [
  ['chrome', 'Shell chrome'],
  ['dock', 'Launcher dock'],
  ['panels', 'Panels & tabs'],
  ['buttons', 'Buttons & atoms'],
  ['mods', 'Mods'],
  ['settings', 'Mod settings'],
  ['cards', 'Cards & panes'],
  ['hud', 'HUD widgets'],
  ['editor', 'HUD editor'],
] as const;

function Section({
  id,
  title,
  note,
  children,
}: {
  id: string;
  title: string;
  note?: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="g-section">
      <h2>{title}</h2>
      {note ? <p>{note}</p> : null}
      {children}
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* Live data — the same fake bridge the in-game harness runs against           */
/* -------------------------------------------------------------------------- */

function useFakeVoid(): { keys: KeysPayload; fps: number; ping: number } {
  const [state, setState] = useState({
    keys: { w: 1, a: 0, s: 0, d: 1, lmb: 1, rmb: 0, space: 0, shift: 0 } as KeysPayload,
    fps: 142,
    ping: 42,
  });

  useEffect(() => {
    const fake: FakeVoid = createFakeVoid({ seed: 42, attachKeyboard: false });
    const offKeys = fake.on('keys', (keys) => setState((s) => ({ ...s, keys })));
    const offTick = fake.on('tick', (tick) =>
      setState((s) => ({
        ...s,
        fps: tick.fps ?? s.fps,
        ping: tick.ping ?? s.ping,
      })),
    );
    fake.start();
    return () => {
      offKeys();
      offTick();
      fake.destroy();
    };
  }, []);

  return state;
}

/* -------------------------------------------------------------------------- */
/* Gallery                                                                    */
/* -------------------------------------------------------------------------- */

const TILES = [
  ['fps', 'FPS display', 'HUD', true],
  ['keystrokes', 'Keystrokes', 'HUD', true],
  ['cps', 'CPS counter', 'HUD', true],
  ['toggle_sprint', 'Toggle sprint', 'PVP', true],
  ['crosshair', 'Crosshair', 'VISUAL', true],
  ['zoom', 'Zoom', 'UTILITY', true],
  ['fullbright', 'Fullbright', 'VISUAL', false],
  ['hitboxes', 'Hitboxes', 'PVP', false],
  ['armor_status', 'Armor status', 'HUD', true],
  ['potion_effects', 'Potion effects', 'HUD', true],
  ['ping', 'Ping display', 'HUD', false],
  ['coordinates', 'Coordinates', 'HUD', false],
] as const;

function Gallery() {
  const [renderer, setRendererState] = useState<Renderer>('webview');
  const [nav, setNav] = useState('play');
  const [tab, setTab] = useState('all');
  const [selectedMod, setSelectedMod] = useState('keystrokes');
  const [on, setOn] = useState<Record<string, boolean>>(
    Object.fromEntries(TILES.map(([id, , , enabled]) => [id, enabled])),
  );
  const [scale, setScale] = useState(1);
  const [opacity, setOpacity] = useState(0.85);
  const [radius, setRadius] = useState(8);
  const [keyColour, setKeyColour] = useState('shell');
  const [pressedColour, setPressedColour] = useState('accent');
  const [position, setPosition] = useState('bottom-left');
  const [keybind, setKeybind] = useState('R-Shift');
  const [launch, setLaunch] = useState<'idle' | 'launching' | 'running'>('idle');
  const [snap, setSnap] = useState(true);
  const [grid, setGrid] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const live = useFakeVoid();

  useEffect(() => setRenderer(renderer), [renderer]);

  const keys = {
    w: live.keys.w === 1,
    a: live.keys.a === 1,
    s: live.keys.s === 1,
    d: live.keys.d === 1,
    lmb: live.keys.lmb === 1,
    rmb: live.keys.rmb === 1,
    space: live.keys.space === 1,
  };

  return (
    <div className="g-root v-app">
      <header className="g-bar">
        <span className="g-bar__title">VOID PVP · components</span>
        <span className="g-bar__note">
          every entry in the design inventory, in every listed state, on `ground`
        </span>
        <span className="v-spacer" />
        <span className="g-bar__note">renderer</span>
        <span className="g-toggle">
          <button
            type="button"
            aria-pressed={renderer === 'webview'}
            onClick={() => setRendererState('webview')}
          >
            webview
          </button>
          <button
            type="button"
            aria-pressed={renderer === 'ultralight'}
            onClick={() => setRendererState('ultralight')}
          >
            ultralight
          </button>
        </span>
      </header>

      <div className="g-body">
        <nav className="g-nav">
          {SECTIONS.map(([id, label]) => (
            <a key={id} href={`#${id}`}>
              {label}
            </a>
          ))}
        </nav>

        <main className="g-main">
          {/* ------------------------------------------------------ chrome */}
          <Section
            id="chrome"
            title="Shell chrome"
            note="TopNav is 1300 × 62 with a 6px gutter and 16px side padding. NavItem has two states: active (surface-3 fill, hairline border, raised bevel) and default."
          >
            <Specimen name="TopNav" state="Play active" dark>
              <div style={{ width: 1300 }}>
                <TopNav
                  right={
                    <>
                      <SearchBar placeholder="Ask VOID anything" />
                      <span style={{ width: 6 }} />
                      <IconButton icon="settings" label="Settings" />
                      <Avatar name="Searge" size={32} />
                    </>
                  }
                >
                  {(['Play', 'Mods', 'Cosmetics', 'Servers', 'Friends'] as const).map((label) => (
                    <NavItem
                      key={label}
                      active={nav === label.toLowerCase()}
                      icon={
                        (
                          {
                            Play: 'play',
                            Mods: 'layers',
                            Cosmetics: 'sparkle',
                            Servers: 'box',
                            Friends: 'users',
                          } as const
                        )[label]
                      }
                      onClick={() => setNav(label.toLowerCase())}
                    >
                      {label}
                    </NavItem>
                  ))}
                </TopNav>
              </div>
            </Specimen>

            <Specimen name="NavItem" state="active · default · hover (hover me)">
              <NavItem active icon="play">
                Play
              </NavItem>
              <NavItem icon="layers">Mods</NavItem>
              <NavItem icon="users">Friends</NavItem>
            </Specimen>

            <Specimen name="SearchBar" state="nav (⌘K hint) · panel · panel narrow">
              <SearchBar placeholder="Ask VOID anything" />
              <SearchBar variant="panel" placeholder="Search" />
              <SearchBar variant="panel" placeholder="Search or paste an address" />
              <SearchBar variant="panel" narrow placeholder="Find a friend" />
            </Specimen>

            <Specimen name="StatusPill" state="ok · warn (not Hypixel-ready)">
              <StatusPill>VOID PVP&nbsp;&nbsp;·&nbsp;&nbsp;1.8.9&nbsp;&nbsp;·&nbsp;&nbsp;HYPIXEL-READY</StatusPill>
              <StatusPill tone="warn">
                VOID PVP&nbsp;&nbsp;·&nbsp;&nbsp;1.8.9&nbsp;&nbsp;·&nbsp;&nbsp;2 GREY MODS ON
              </StatusPill>
            </Specimen>
          </Section>

          {/* -------------------------------------------------------- dock */}
          <Section
            id="dock"
            title="Launcher dock"
            note="The dock is r24 with 12px padding and a 12px gutter. Children, left to right: identity, divider, LoadoutPicker, VersionPicker, LaunchButton, divider, FriendsOnline, settings."
          >
            <Specimen name="Dock" state="the whole assembly" dark>
              <Dock>
                <PlayerChip name="Searge" level="Lvl 42" />
                <Divider />
                <LoadoutPicker
                  value="Sword PvP"
                  open={pickerOpen}
                  onClick={() => setPickerOpen((v) => !v)}
                />
                <VersionPicker value="1.8.9" />
                <LaunchButton state={launch} onClick={() => setLaunch(next(launch))} />
                <Divider />
                <FriendsOnline
                  friends={[{ name: 'marrow' }, { name: 'pilot_ash' }, { name: 'nine' }]}
                />
                <IconButton icon="settings" size="dock" label="Launcher settings" />
              </Dock>
            </Specimen>

            <Specimen name="LaunchButton" state="idle · launching · running · disabled">
              <LaunchButton state="idle" />
              <LaunchButton state="launching" />
              <LaunchButton state="running" />
              <LaunchButton disabled />
            </Specimen>

            <Specimen name="LoadoutPicker / VersionPicker" state="default · open · in-game row">
              <LoadoutPicker value="Sword PvP" />
              <LoadoutPicker value="Sword PvP" open />
              <VersionPicker value="1.8.9" />
              <div style={{ width: 276 }}>
                <LoadoutPicker value="Sword PvP" row eyebrow="LOADOUT" />
              </div>
            </Specimen>

            <Specimen name="PlayerChip · FriendsOnline">
              <PlayerChip name="Searge" level="Lvl 42" />
              <FriendsOnline
                friends={[{ name: 'marrow' }, { name: 'pilot_ash' }, { name: 'nine' }]}
              />
              <FriendsOnline friends={[{ name: 'nine' }]} total={1} />
            </Specimen>
          </Section>

          {/* ------------------------------------------------------ panels */}
          <Section
            id="panels"
            title="Panels & tabs"
            note="960 × 596 in the launcher, 960 × 600 in the overlay — the overlay adds a close X. The footer hint is 10.5px DM Mono."
          >
            <Specimen name="Panel" state="overlay, with close · animated open" dark>
              <Panel
                surface="overlay"
                title="Mods"
                headerRight={
                  <>
                    <SearchBar variant="panel" placeholder="Search" />
                    <FilterTabs
                      tabs={[
                        { id: 'all', label: 'All' },
                        { id: 'hud', label: 'HUD' },
                        { id: 'pvp', label: 'PvP' },
                        { id: 'visual', label: 'Visual' },
                        { id: 'utility', label: 'Utility' },
                      ]}
                      value={tab}
                      onChange={setTab}
                    />
                    <span className="v-spacer" />
                  </>
                }
                footer="R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search"
                onClose={() => undefined}
                animate
              >
                <ModGrid>
                  {TILES.map(([id, name, category]) => (
                    <ModTile
                      key={id}
                      name={name}
                      category={category}
                      icon={MOD_ICONS[id]}
                      on={on[id] ?? false}
                      selected={selectedMod === id}
                      onSelect={() => setSelectedMod(id)}
                      onToggle={(next) => setOn((s) => ({ ...s, [id]: next }))}
                    />
                  ))}
                </ModGrid>
                <ModSettingsPanel
                  title="Keystrokes"
                  on={on['keystrokes'] ?? true}
                  onToggle={(next) => setOn((s) => ({ ...s, keystrokes: next }))}
                >
                  <KeystrokesPreview keys={keys} />
                  <Slider
                    label="Scale"
                    readout={`${scale.toFixed(1)}×`}
                    value={scale}
                    min={0.25}
                    max={4}
                    step={0.1}
                    onChange={setScale}
                  />
                  <Slider
                    label="Opacity"
                    readout={`${Math.round(opacity * 100)}%`}
                    value={opacity}
                    min={0}
                    max={1}
                    step={0.01}
                    onChange={setOpacity}
                  />
                  <ModSettingsRow label="Keybind">
                    <KeybindChip
                      value={keybind}
                      onCapture={() => Promise.resolve('V')}
                      onChange={setKeybind}
                    />
                  </ModSettingsRow>
                  <span className="v-spacer" />
                  <EditPositionButton />
                </ModSettingsPanel>
              </Panel>
            </Specimen>

            <Specimen name="FilterTabs" state="selected · default · with counts">
              <FilterTabs
                tabs={[
                  { id: 'all', label: 'All' },
                  { id: 'hud', label: 'HUD' },
                  { id: 'pvp', label: 'PvP' },
                  { id: 'visual', label: 'Visual' },
                  { id: 'utility', label: 'Utility' },
                ]}
                value={tab}
                onChange={setTab}
              />
              <FilterTabs
                tabs={[
                  { id: 'online', label: 'Online', count: 3 },
                  { id: 'all', label: 'All', count: 8 },
                  { id: 'requests', label: 'Requests', count: 2, countTone: 'ok' },
                ]}
                value="online"
              />
            </Specimen>
          </Section>

          {/* ----------------------------------------------------- buttons */}
          <Section id="buttons" title="Buttons & atoms">
            <Specimen name="Button" state="accent · raised · ghost · chip · chip-accent · text · disabled">
              <Button variant="accent">Queue with party</Button>
              <Button variant="raised" icon="plus">
                New loadout
              </Button>
              <Button variant="ghost" icon="star">
                Favourited
              </Button>
              <Button variant="chip">Invite</Button>
              <Button variant="chip-accent">Join</Button>
              <Button variant="text">Leave party</Button>
              <Button variant="accent" disabled>
                Launch
              </Button>
            </Specimen>

            <Specimen name="Button" state="block — the card and pane CTA">
              <div style={{ width: 256 }}>
                <Button variant="accent" block>
                  Switch to Bedwars
                </Button>
              </div>
              <div style={{ width: 256 }}>
                <Button variant="raised" block icon="check" disabled>
                  Active
                </Button>
              </div>
            </Specimen>

            <Specimen name="IconButton" state="default 34 · close 32 · dock 44">
              <IconButton icon="settings" label="Settings" />
              <IconButton icon="close" size="close" label="Close" />
              <IconButton icon="settings" size="dock" label="Launcher settings" />
            </Specimen>

            <Specimen name="Kbd · Tag · Badge · StatusDot">
              <Kbd flavour="nav">⌘K</Kbd>
              <span
                style={{
                  display: 'inline-flex',
                  padding: '6px 10px',
                  borderRadius: 8,
                  background: 'var(--accent)',
                }}
              >
                <Kbd flavour="accent">⌘↵</Kbd>
              </span>
              <Kbd flavour="palette">↵</Kbd>
              <Tag>HUD</Tag>
              <Tag>UTILITY</Tag>
              <Badge>Active</Badge>
              <Badge>Leader</Badge>
              <Badge tone="ok">Ready</Badge>
              <Badge tone="solid">New</Badge>
              <StatusDot tone="ok" />
              <StatusDot tone="warn" />
              <StatusDot tone="muted" />
              <StatusDot tone="accent" size={11} />
            </Specimen>

            <Specimen name="Avatar" state="32 · 36 with presence · 44 · offline">
              <Avatar name="Searge" size={32} />
              <Avatar name="marrow" size={36} presence="online" />
              <Avatar name="pilot_ash" size={36} presence="away" />
              <Avatar name="Searge" size={44} />
              <Avatar name="doorframe" size={36} presence="offline" dimmed />
            </Specimen>

            <Specimen name="IconWell" state="24 · 30 · 34 off · 34 on · 44 solid">
              <IconWell icon="sword" size={24} />
              <IconWell icon="zoom" size={30} />
              <IconWell icon="sun" size={34} />
              <IconWell icon="keyboard" size={34} on />
              <IconWell icon="sword" size={44} solid />
            </Specimen>

            <Specimen name="Card" state="default · selected">
              <Card style={{ width: 200, height: 72 }} />
              <Card selected style={{ width: 200, height: 72 }} />
            </Specimen>

            <Specimen name="Icon" state="the whole set at 16px">
              {(
                [
                  'play',
                  'settings',
                  'chevron-down',
                  'arrow-left',
                  'search',
                  'close',
                  'check',
                  'plus',
                  'move',
                  'layers',
                  'reset',
                  'users',
                  'star',
                  'eye',
                  'sword',
                  'box',
                  'bed',
                  'heart',
                  'gauge',
                  'keyboard',
                  'cursor-click',
                  'crosshair',
                  'zoom',
                  'sun',
                  'shield',
                  'flask',
                  'wifi',
                  'compass',
                  'footprints',
                  'cube',
                  'sparkle',
                ] as const
              ).map((name) => (
                <span key={name} title={name} style={{ color: 'var(--text-secondary)' }}>
                  <Icon name={name} size={16} />
                </span>
              ))}
            </Specimen>
          </Section>

          {/* -------------------------------------------------------- mods */}
          <Section
            id="mods"
            title="Mods"
            note="ModTile is 200 × 96. On/off is carried by the switch and the icon-well tint; selection is carried by the border alone, with no fill change."
          >
            <Specimen name="ModTile" state="on · off · selected · hover me">
              <ModTile name="FPS display" category="HUD" icon="gauge" on onToggle={() => undefined} />
              <ModTile
                name="Fullbright"
                category="VISUAL"
                icon="sun"
                on={false}
                onToggle={() => undefined}
              />
              <ModTile
                name="Keystrokes"
                category="HUD"
                icon="keyboard"
                on
                selected
                onToggle={() => undefined}
              />
            </Specimen>

            <Specimen name="ModGrid" state="3 × 4, the full registry">
              <ModGrid>
                {TILES.map(([id, name, category]) => (
                  <ModTile
                    key={id}
                    name={name}
                    category={category}
                    icon={MOD_ICONS[id]}
                    on={on[id] ?? false}
                    selected={selectedMod === id}
                    onSelect={() => setSelectedMod(id)}
                    onToggle={(next) => setOn((s) => ({ ...s, [id]: next }))}
                  />
                ))}
              </ModGrid>
            </Specimen>

            <Specimen name="Toggle" state="S / M / L, on and off, plus disabled">
              <Toggle checked size="s" label="S on" onChange={() => undefined} />
              <Toggle checked={false} size="s" label="S off" onChange={() => undefined} />
              <Toggle checked size="m" label="M on" onChange={() => undefined} />
              <Toggle checked={false} size="m" label="M off" onChange={() => undefined} />
              <Toggle checked size="l" label="L on" onChange={() => undefined} />
              <Toggle checked={false} size="l" label="L off" onChange={() => undefined} />
              <Toggle checked disabled size="l" label="disabled" />
            </Specimen>

            <Specimen name="KeystrokesPreview" state="live, from the fake bridge">
              <KeystrokesPreview keys={keys} />
              <KeystrokesPreview keys={{ w: true, d: true, lmb: true }} />
              <KeystrokesPreview keys={{}} />
            </Specimen>
          </Section>

          {/* ---------------------------------------------------- settings */}
          <Section
            id="settings"
            title="Mod settings"
            note="ModSettingsPanel is the 278px pane beside the grid; SettingsGroup is the 448 × 290 card on the full mod-settings frame."
          >
            <Specimen name="ModSettingsPanel" state="the Keystrokes pane">
              <ModSettingsPanel title="Keystrokes" on onToggle={() => undefined}>
                <KeystrokesPreview keys={keys} />
                <Slider
                  label="Scale"
                  readout={`${scale.toFixed(1)}×`}
                  value={scale}
                  min={0.25}
                  max={4}
                  step={0.1}
                  onChange={setScale}
                />
                <Slider
                  label="Opacity"
                  readout={`${Math.round(opacity * 100)}%`}
                  value={opacity}
                  min={0}
                  max={1}
                  step={0.01}
                  onChange={setOpacity}
                />
                <ModSettingsRow label="Keybind">
                  <KeybindChip
                    value={keybind}
                    onCapture={() =>
                      new Promise((resolve) => setTimeout(() => resolve('V'), 1200))
                    }
                    onChange={setKeybind}
                  />
                </ModSettingsRow>
                <span className="v-spacer" />
                <EditPositionButton />
              </ModSettingsPanel>
            </Specimen>

            <Specimen name="SettingsGroup" state="APPEARANCE · BEHAVIOUR">
              <SettingsGroup caption="Appearance">
                <SettingsRow title="Scale" value={`${scale.toFixed(1)}×`}>
                  <Slider
                    variant="wide"
                    hideLabels
                    ariaLabel="Scale"
                    value={scale}
                    min={0.25}
                    max={4}
                    step={0.1}
                    onChange={setScale}
                  />
                </SettingsRow>
                <SettingsRow seam title="Opacity" value={`${Math.round(opacity * 100)}%`}>
                  <Slider
                    variant="wide"
                    hideLabels
                    ariaLabel="Opacity"
                    value={opacity}
                    min={0}
                    max={1}
                    step={0.01}
                    onChange={setOpacity}
                  />
                </SettingsRow>
                <SettingsRow seam title="Corner radius" value={`${radius} px`}>
                  <Slider
                    variant="wide"
                    hideLabels
                    ariaLabel="Corner radius"
                    value={radius}
                    min={0}
                    max={20}
                    step={1}
                    onChange={setRadius}
                  />
                </SettingsRow>
                <SettingsRow seam title="Key colour" sub="Background of an unpressed key">
                  <Swatches
                    value={keyColour}
                    onChange={setKeyColour}
                    swatches={[
                      { id: 'shell', color: 'var(--bg-shell)' },
                      { id: 'raised', color: 'var(--surface-2)' },
                      { id: 'pill', color: 'var(--surface-3)' },
                      { id: 'sky', color: 'var(--sky)' },
                      { id: 'teal', color: 'var(--teal)' },
                    ]}
                  />
                </SettingsRow>
                <SettingsRow
                  seam
                  title="Pressed colour"
                  sub="Follows the loadout accent by default"
                >
                  <Swatches
                    value={pressedColour}
                    onChange={setPressedColour}
                    swatches={[
                      { id: 'accent', color: 'var(--accent)' },
                      { id: 'sky', color: 'var(--sky)' },
                      { id: 'warn', color: 'var(--warn)' },
                      { id: 'fear', color: 'var(--danger)' },
                      { id: 'teal', color: 'var(--teal)' },
                    ]}
                  />
                </SettingsRow>
              </SettingsGroup>

              <SettingsGroup caption="Behaviour">
                <SettingsRow title="Show mouse buttons" sub="LMB and RMB under the arrows">
                  <Toggle checked size="l" label="Show mouse buttons" onChange={() => undefined} />
                </SettingsRow>
                <SettingsRow seam title="Show CPS" sub="Clicks per second for both buttons">
                  <Toggle checked size="l" label="Show CPS" onChange={() => undefined} />
                </SettingsRow>
                <SettingsRow seam title="Show space bar" sub="A wide key under the block">
                  <Toggle checked={false} size="l" label="Show space bar" onChange={() => undefined} />
                </SettingsRow>
                <SettingsRow seam title="Show sneak key">
                  <Toggle checked={false} size="l" label="Show sneak key" onChange={() => undefined} />
                </SettingsRow>
                <SettingsRow seam title="Position">
                  <PositionChips
                    value={position}
                    onChange={setPosition}
                    options={[
                      { id: 'top-left', label: 'Top left' },
                      { id: 'top-right', label: 'Top right' },
                      { id: 'bottom-left', label: 'Bottom left' },
                      { id: 'bottom-right', label: 'Bottom right' },
                    ]}
                  />
                </SettingsRow>
              </SettingsGroup>
            </Specimen>

            <Specimen name="Slider" state="compact · wide · disabled">
              <Slider
                label="Scale"
                readout={`${scale.toFixed(1)}×`}
                value={scale}
                min={0.25}
                max={4}
                step={0.1}
                onChange={setScale}
              />
              <Slider
                variant="wide"
                label="Opacity"
                readout={`${Math.round(opacity * 100)}%`}
                value={opacity}
                min={0}
                max={1}
                step={0.01}
                onChange={setOpacity}
              />
              <Slider label="Locked" readout="1.0×" value={1} min={0} max={4} disabled />
            </Specimen>

            <Specimen name="KeybindChip" state="idle · capturing (click it) · unbound · read-only">
              <KeybindChip
                value={keybind}
                onCapture={() => new Promise((resolve) => setTimeout(() => resolve('C'), 1500))}
                onChange={setKeybind}
              />
              <KeybindChip value="NONE" onCapture={() => Promise.resolve(null)} />
              <KeybindChip value="R-Shift" />
            </Specimen>

            <Specimen name="EditPositionButton">
              <div style={{ width: 246 }}>
                <EditPositionButton />
              </div>
            </Specimen>
          </Section>

          {/* ------------------------------------------------------- cards */}
          <Section
            id="cards"
            title="Cards & panes"
            note="LoadoutCard is 292 × 428. The active card takes a 1.5px accent border, the ACTIVE badge and a disabled Active button — switching is instant, so there is nothing to press on the loadout you already have."
          >
            <Specimen name="LoadoutCard" state="active · inactive · inactive">
              <LoadoutCard
                name="Sword PvP"
                icon="sword"
                active
                meta="24 mods on   ·   Hypixel  ·  1.8.9"
                includes={[
                  { label: 'Keystrokes' },
                  { label: 'CPS' },
                  { label: 'Toggle sprint' },
                  { label: 'Crosshair' },
                  { label: 'Zoom' },
                  { label: 'Armor status' },
                ]}
                moreCount={18}
                stats={[
                  { value: '142', unit: 'fps avg' },
                  { value: '4h 20m', unit: 'played' },
                ]}
              />
              <LoadoutCard
                name="Bedwars"
                icon="bed"
                meta="19 mods on   ·   Hypixel  ·  1.8.9"
                includes={[
                  { label: 'Keystrokes' },
                  { label: 'Armor status' },
                  { label: 'Potion effects' },
                  { label: 'Fullbright' },
                  { label: 'Ping' },
                ]}
                moreCount={14}
                stats={[
                  { value: '—', unit: 'fps avg' },
                  { value: '2h 05m', unit: 'played' },
                ]}
              />
              <LoadoutCard
                name="UHC"
                icon="heart"
                meta="16 mods on   ·   Minemen  ·  1.8.9"
                includes={[
                  { label: 'Armor status' },
                  { label: 'Potion effects' },
                  { label: 'Coordinates' },
                  { label: 'Hitboxes' },
                  { label: 'Zoom' },
                ]}
                moreCount={11}
                stats={[
                  { value: '—', unit: 'fps avg' },
                  { value: '48m', unit: 'played' },
                ]}
              />
            </Specimen>

            <Specimen name="Pane · StatTile · Sparkline · GroupCaption">
              <Pane heading="Hypixel">
                <div style={{ display: 'flex', gap: 8 }}>
                  <StatTile value="42 ms" unit="ping" />
                  <StatTile value="24,118" unit="online" />
                  <StatTile value="37" unit="wins here" />
                </div>
                <span className="v-caption">Ping&nbsp;&nbsp;·&nbsp;&nbsp;Last 12 h</span>
                <Sparkline
                  values={[18, 20, 16, 22, 18, 32, 25, 18, 16, 18, 17, 18]}
                  outliers={[5]}
                />
                <GroupCaption label="Online" count="·  3" />
                <Button variant="accent" block>
                  Join with Sword PvP
                </Button>
                <Button variant="ghost" block icon="star">
                  Favourited
                </Button>
              </Pane>
            </Specimen>
          </Section>

          {/* --------------------------------------------------------- HUD */}
          <Section
            id="hud"
            title="HUD widgets"
            note="Drawn over live game pixels. Every readout sits on its own chip: Ultralight drops text-shadow, so the design solves legibility structurally instead."
          >
            <Specimen name="HUD chips" state="editor variant, over the game" game>
              <div className="g-col">
                <FpsChip variant="editor" fps={live.fps} onePercentLow={96} />
                <PingChip variant="editor" ping={live.ping} host="Hypixel" />
                <CoordsChip variant="editor" x={118} y={64} z={-212} direction="NE" />
                <CpsChip variant="editor" left={12} right={9} />
              </div>
              <PotionList
                effects={[
                  { name: 'Speed II', time: '1:24', color: '#7aebb5' },
                  { name: 'Strength', time: '0:48', color: '#d9a93a' },
                ]}
              />
              <ArmorList
                rows={[
                  { label: 'Helmet', remaining: 231, max: 363 },
                  { label: 'Chestplate', remaining: 412, max: 528 },
                  { label: 'Leggings', remaining: 188, max: 495 },
                  { label: 'Boots', remaining: 341, max: 429 },
                ]}
              />
            </Specimen>

            <Specimen name="HUD chips" state="compact variant · dimmed (panel open)" game>
              <FpsChip fps={142} />
              <PingChip ping={42} host="Hypixel" />
              <PingChip ping={112} host="Minemen" />
              <PingChip ping={-1} host="Singleplayer" />
              <CoordsChip x={118} y={64} z={-212} direction="NE" />
              <CpsChip left={12} right={9} />
              <CpsChip left={12} mode="left" />
              <FpsChip fps={142} dimmed />
            </Specimen>

            <Specimen name="KeystrokesWidget" state="live · static · with CPS · with space bar" game>
              <KeystrokesWidget keys={keys} />
              <KeystrokesWidget keys={{ w: true, d: true, lmb: true }} />
              <KeystrokesWidget keys={keys} cps={{ left: 12, right: 9 }} />
              <KeystrokesWidget keys={keys} showSpacebar />
              <KeystrokesWidget keys={keys} showMouse={false} />
            </Specimen>

            <Specimen name="Crosshair · Hotbar" game>
              <Crosshair />
              <Hotbar
                slots={[
                  'rgba(158,158,168,0.9)',
                  'rgba(140,92,51,0.9)',
                  'rgba(217,51,51,0.9)',
                  'rgba(77,153,229,0.9)',
                  null,
                  null,
                  null,
                  null,
                  null,
                ]}
              />
            </Specimen>

            <Specimen name="ArmorList" state="ok · warn · nearly broken">
              <ArmorList
                rows={[
                  { label: 'Helmet', remaining: 350, max: 363 },
                  { label: 'Chestplate', remaining: 200, max: 528 },
                  { label: 'Boots', remaining: 20, max: 429 },
                ]}
              />
            </Specimen>
          </Section>

          {/* ------------------------------------------------------ editor */}
          <Section
            id="editor"
            title="HUD editor"
            note="Toolbar, dashed selection with four grips and a live readout, and the mono hint chip. Flip the renderer toggle: the selection border is dashed in the launcher and solid in the overlay, because dash phase on rounded corners is inconsistent in Ultralight."
          >
            <Specimen name="EditorToolbar" state="Snap active · Grid off" game>
              <EditorToolbar
                snap={snap}
                onSnapChange={setSnap}
                grid={grid}
                onGridChange={setGrid}
              />
            </Specimen>

            <Specimen name="Tool" state="mode · default · active · primary">
              <span className="v-toolbar">
                <Tool kind="mode" icon="move">
                  HUD layout
                </Tool>
                <Divider toolbar />
                <Tool kind="active">Snap</Tool>
                <Tool icon="layers">Grid</Tool>
                <Tool icon="reset">Reset</Tool>
                <Tool kind="primary" hideIcon>
                  Done
                </Tool>
              </span>
            </Specimen>

            <Specimen name="SelectionFrame" state="around a live keystrokes widget" game>
              <div className="g-stage" style={{ height: 260 }}>
                <div style={{ position: 'absolute', left: 40, top: 60 }}>
                  <KeystrokesWidget keys={keys} />
                </div>
                <SelectionFrame
                  name="Keystrokes"
                  readout={formatSelectionReadout(32, 580, 1)}
                  style={{ left: 32, top: 52, width: 146, height: 146 }}
                />
              </div>
            </Specimen>

            <Specimen name="HintBar" game>
              <HintBar hints={['Drag to move', '⌥ drag to scale', 'Esc to exit']} />
            </Specimen>
          </Section>

          <footer style={{ padding: '24px 0 60px' }}>
            <span className="v-hint">
              {Object.keys(MOD_REGISTRY).length} mods in the registry · renderer:{' '}
              {renderer} · fake bridge seed 42
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}

function next(state: 'idle' | 'launching' | 'running'): 'idle' | 'launching' | 'running' {
  return state === 'idle' ? 'launching' : state === 'launching' ? 'running' : 'idle';
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Gallery />
  </StrictMode>,
);
