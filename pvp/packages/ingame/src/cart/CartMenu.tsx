/**
 * The Right-Shift menu, Cartridge (Figma 559:25183 "★ v2 · Launch refresh", section B 623:20).
 *
 *   top     the tab island: VOID · 1 Mods · 2 Loadouts · 3 Keybinds · 4 Friends · 5 Cosmetics · R-Shift
 *   centre  the active tab's panel, 1180 × 784, centred; with a mod's settings open it moves to
 *           the left edge and the bench takes the right (597:2)
 *   bottom  the status strip: mods on and the loadout over per-shelf stepped meters, where you
 *           are, and HUD layout (H). Hidden in the 720p compact frame (606:2).
 *
 * The whole layer is laid out at 1920 × 1080 and zoomed to the window (cart/scale.ts).
 *
 * In a live match (v1 Ship rule 9, `store.context`) the menu is the quick panel alone: the
 * loadout's pins as numbered cartridges that 1–8 flip, no dim, closed by the first hit taken.
 *
 * Keys owned here: 1–5 jump to a section, H opens the HUD layout. Escape is MenuLayer's.
 */

import { useEffect, useMemo, type CSSProperties, type ReactNode } from 'react';

import { HUD_MOD_IDS, type HUDModId, type ModId } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { MENU_TABS, isModOn, modsOnCount, quickPins, tabOf, useVoidStore, type MenuTab } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { keybindLabel } from '@/menu/settings-format';
import { shortHost } from '@/hud/format';
import { stripMc } from '@/hud/mc-format';
import { cx } from '@/ui';
import { Sprite, hasSprite } from './Sprite';
import { CATEGORIES, Kc, Led, Seg, Switch, catClass, categoryOf } from './prims';
import { SHELF_MODS, SHELVES } from './shelves';
import { layerStyle, useCartScale, type CartScale } from './scale';
import { ModsTab } from './ModsTab';
import { Bench } from './Bench';
import { LoadoutsTab } from './LoadoutsTab';
import { KeybindsTab } from './KeybindsTab';
import { CosmeticsTab } from './CosmeticsTab';
import { useDevState } from './dev';
import { CrescentWheel } from './CrescentWheel';
import { useCosmetics } from './cosmetics-store';

const TAB_LABEL: Record<MenuTab, string> = {
  mods: 'Mods',
  loadouts: 'Loadouts',
  keybinds: 'Keybinds',
  friends: 'Friends',
  cosmetics: 'Cosmetics',
};

const PANEL_W = 1180;
const PANEL_H = 784;
const EDGE = 48;
const BENCH_GAP = 20;
const ISLAND_TOP = 28;
const ISLAND_H = 50;

/** Where the panel and the bench sit, in design px, for a layer of this size. */
export function panelGeometry(scale: CartScale, bench: boolean) {
  const height = Math.min(PANEL_H, scale.height - 2 * (ISLAND_TOP + ISLAND_H + 12));
  const top = Math.max(ISLAND_TOP + ISLAND_H + 12, Math.round((scale.height - height) / 2) + 2);
  if (!bench) {
    const width = Math.min(PANEL_W, scale.width - 2 * EDGE);
    return { panel: { left: Math.round((scale.width - width) / 2), top, width, height }, bench: null };
  }
  const avail = scale.width - 2 * EDGE - BENCH_GAP;
  const benchW = Math.max(480, Math.min(624, avail - PANEL_W));
  const width = avail - benchW;
  return {
    panel: { left: EDGE, top, width, height },
    bench: { left: EDGE + width + BENCH_GAP, top, width: benchW, height },
  };
}

/* -------------------------------------------------------------------------- */

function TabIsland({ tab }: { tab: MenuTab | null }) {
  const setRoute = useVoidStore((s) => s.setRoute);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const menuKey = useVoidStore((s) => s.globals.menuKey);
  return (
    <nav className="c-island c-island--tabs" aria-label="Menu">
      <span className="c-brand">
        {hasSprite('logo') ? <Sprite name="logo" size={20} /> : null}
        <span className="c-brand__word">VOID</span>
      </span>
      <span className="c-island__sep" />
      {MENU_TABS.map((t, i) => (
        <button
          key={t}
          type="button"
          className={cx('c-tab', tab === t && 'is-on')}
          aria-current={tab === t ? 'page' : undefined}
          onClick={() => setRoute({ name: t })}
        >
          <Kc tone={tab === t ? 'lt' : 'idle'}>{i + 1}</Kc>
          {TAB_LABEL[t]}
        </button>
      ))}
      <span className="c-island__sep" />
      <button type="button" className="c-tab c-tab--close" onClick={closeMenu} aria-label="Close the menu">
        <Kc>{keybindLabel(menuKey)}</Kc>
      </button>
    </nav>
  );
}

function StatusStrip() {
  const loadout = useVoidStore((s) => s.loadout);
  const server = useVoidStore((s) => s.server);
  const ping = useVoidStore((s) => s.ping);
  const board = useVoidStore((s) => (s.scoreboard.visible ? stripMc(s.scoreboard.title) : ''));
  const setRoute = useVoidStore((s) => s.setRoute);
  const on = modsOnCount(loadout);
  const where = server.connected ? shortHost(server.host) : 'Singleplayer';
  const mode = server.connected ? (board ? board.charAt(0) + board.slice(1).toLowerCase() : 'Lobby') : 'Local world';
  return (
    <div className="c-island c-island--strip">
      <div className="c-strip__mods">
        <span className="c-mono c-strip__count">
          {on} / {SHELF_MODS.length} on{loadout ? ` · ${loadout.name}` : ''}
        </span>
        <span className="c-strip__meters">
          {CATEGORIES.map((c) => (
            <span key={c} className={cx('c-strip__meter', catClass(c))}>
              <Seg n={SHELVES[c].length} on={SHELVES[c].filter((id) => isModOn(loadout, id)).length} w={6} h={16} gap={2} color="var(--c-hue)" />
            </span>
          ))}
        </span>
      </div>
      <span className="c-island__sep c-island__sep--tall" />
      <div className="c-strip__where">
        <span className={cx('c-mono c-strip__live', server.connected && 'is-on')}>
          ● {server.connected ? `Online${ping >= 0 ? ` ${ping} ms` : ''}` : 'Offline'}
        </span>
        <span className="c-strip__name">
          {where} · {mode}
        </span>
      </div>
      <button type="button" className="c-btn c-btn--s" onClick={() => setRoute({ name: 'hud-editor' })}>
        HUD layout <Kc>H</Kc>
      </button>
    </div>
  );
}

/** Friends: the bridge carries no friend, presence or party data, so it says so. */
function FriendsTab() {
  const session = useVoidStore((s) => s.session);
  const name = session?.name?.trim() ?? '';
  return (
    <div className="c-soon">
      <div className="c-soon__slots" aria-hidden="true">
        {(['steve', 'alex', 'zombie'] as const).map((head, i) => (
          <span key={head} className={cx('c-empty__slot', i === 1 && 'c-empty__slot--lit')}>
            {hasSprite(head) ? <Sprite name={head} size={i === 1 ? 64 : 40} dim={i === 1 ? undefined : 0.35} /> : null}
          </span>
        ))}
      </div>
      <span className="c-mono">Not connected yet</span>
      <div className="c-title">Friends</div>
      <p className="c-empty__line">
        Who is online, who is in a match, and joining them — once the friends service is connected. Until then your list
        lives in the launcher.
      </p>
      <span className="c-soon__who c-mono">
        {name ? `Signed in as ${name} · ${session?.kind === 'microsoft' ? 'Microsoft' : 'offline'}` : 'Not signed in'}
      </span>
    </div>
  );
}

function TabBody({ tab, benchId }: { tab: MenuTab; benchId: ModId | null }): ReactNode {
  switch (tab) {
    case 'mods':
      return <ModsTab benchId={benchId} />;
    case 'loadouts':
      return <LoadoutsTab />;
    case 'keybinds':
      return <KeybindsTab />;
    case 'friends':
      return <FriendsTab />;
    case 'cosmetics':
      return <CosmeticsTab />;
  }
}

/* -------------------------------------------------------------------------- */
/* In a match                                                                 */
/* -------------------------------------------------------------------------- */

function QuickPanel() {
  const loadout = useVoidStore((s) => s.loadout);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const menuKey = useVoidStore((s) => s.globals.menuKey);
  const own = useVoidStore((s) => s.loadout?.quick);
  const pins = useMemo(() => quickPins(own ? { quick: own } : null), [own]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const n = Number(e.key);
      if (!Number.isInteger(n) || n < 1 || n > pins.length) return;
      e.preventDefault();
      const id = pins[n - 1]!;
      toggleMod(id, !isModOn(useVoidStore.getState().loadout, id));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [toggleMod, pins]);

  return (
    <div className="c-quickwrap">
      <section className="c-island c-quick" aria-label="Quick toggles">
        <header className="c-quick__head">
          <span className="c-quick__titles">
            <span className="c-label">Quick{loadout ? ` · ${loadout.name}` : ''}</span>
            <span className="c-mono">Closes when you take a hit</span>
          </span>
          <button type="button" className="c-btn c-btn--g c-btn--sm" aria-label="Close" onClick={closeMenu}>
            <Kc>{keybindLabel(menuKey)}</Kc>
          </button>
        </header>
        <div className="c-quick__pins">
          {pins.length === 0 ? <p className="c-none">Nothing pinned — pin mods with P in the lobby.</p> : null}
          {pins.map((id, i) => {
            const on = isModOn(loadout, id);
            return (
              <div key={id} className={cx('c-pin', catClass(categoryOf(id)), !on && 'is-off')}>
                <Kc>{i + 1}</Kc>
                <span className="c-pin__bed">{hasSprite(id) ? <Sprite name={id} size={32} dim={on ? undefined : 0.4} /> : null}</span>
                <span className="c-pin__name">{modLabel(id)}</span>
                <Led on={on} />
                <Switch checked={on} label={`${modLabel(id)} — ${on ? 'on' : 'off'}`} onChange={(next) => toggleMod(id, next)} />
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

/* -------------------------------------------------------------------------- */

export function CartMenu() {
  const route = useVoidStore((s) => s.route);
  const context = useVoidStore((s) => s.context);
  const setRoute = useVoidStore((s) => s.setRoute);
  const setEditorTarget = useVoidStore((s) => s.setEditorTarget);
  const scale = useCartScale();
  const dev = useDevState();
  const wheelOpen = useCosmetics((s) => s.wheelOpen);
  const tab = tabOf(route) ?? 'mods';
  const benchId = route.name === 'mod' ? route.id : null;
  const geo = panelGeometry(scale, benchId !== null && context === 'lobby');

  // The wheel belongs to this open of the menu; closing the menu puts it away.
  useEffect(() => () => useCosmetics.getState().setWheelOpen(false), []);

  // 1–5 sections, H the HUD layout. Not while typing, and not in a match (1–8 are the pins).
  useEffect(() => {
    if (context !== 'lobby') return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (hasTextFocus() || useVoidStore.getState().paletteOpen) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const n = Number(e.key);
      if (Number.isInteger(n) && n >= 1 && n <= MENU_TABS.length && e.key.length === 1) {
        e.preventDefault();
        setRoute({ name: MENU_TABS[n - 1]! });
        return;
      }
      if (e.key === 'b' || e.key === 'B') {
        // The emote wheel, over the world. (Holding B with the menu shut is Java's to surface.)
        e.preventDefault();
        useCosmetics.getState().setWheelOpen(!useCosmetics.getState().wheelOpen);
        return;
      }
      if (e.key === 'h' || e.key === 'H') {
        e.preventDefault();
        const r = useVoidStore.getState().route;
        if (r.name === 'mod' && (HUD_MOD_IDS as readonly string[]).includes(r.id)) setEditorTarget(r.id as HUDModId);
        setRoute({ name: 'hud-editor' });
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [context, setRoute, setEditorTarget]);

  if (context === 'match') {
    return (
      <div className="c-layer c-layer--match" style={layerStyle(scale)}>
        <QuickPanel />
      </div>
    );
  }

  const box = (b: { left: number; top: number; width: number; height: number }): CSSProperties => b;

  if (wheelOpen) {
    return (
      <div className="c-layer c-layer--wheel" style={layerStyle(scale)}>
        <CrescentWheel width={scale.width} height={scale.height} />
      </div>
    );
  }

  return (
    <div className={cx('c-layer', scale.compact && 'is-compact')} style={layerStyle(scale)} data-tab={tab}>
      <TabIsland tab={tab} />
      <section
        className={cx('c-panel', `c-panel--${tab}`, benchId && 'has-bench')}
        style={box(geo.panel)}
        aria-label={TAB_LABEL[tab]}
      >
        <TabBody tab={tab} benchId={benchId} />
      </section>
      {benchId && geo.bench ? (
        <div className="c-benchwrap" style={box(geo.bench)}>
          <Bench key={benchId} id={benchId} captureInitial={dev.captureInitial} />
        </div>
      ) : null}
      {!scale.compact ? <StatusStrip /> : null}
    </div>
  );
}
