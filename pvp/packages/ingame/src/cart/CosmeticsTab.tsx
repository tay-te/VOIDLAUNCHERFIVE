/**
 * Tab 5 — Cosmetics: the wardrobe (Figma 295:2, section 562:24911, direction B "Paper doll"
 * 569:27919; emote loadout 574:31781; states 574:33116), drawn in Cartridge.
 *
 *   Wardrobe     equipped slots · the paper doll with its seven slot cards · the slot's items
 *   Emote wheel  the cosmetics mod's switches and the selected slot · the ring around the doll ·
 *                the emotes you own
 *
 * The doll is pixel art from the sprite sheet (`doll_front` / `doll_back`) with each worn item's
 * own sprite laid where it is worn — a CSS paper doll, as the Figma kit draws it; the bridge
 * exposes no GL-rendered preview texture. What it shows is `self` from the `cosmetics` push,
 * which is exactly what other VOID players are sent.
 *
 * Prices are US dollars (`price_cents`), never a coin. Nothing is bought in game: a locked item
 * says "Get in launcher Store", which brings the launcher forward on that item's Store sheet
 * (`openStore`). With no launcher behind the game, the notice says where to go instead.
 */

import { useState, type ReactNode } from 'react';

import type { CosmeticCatalogItem, CosmeticId, CosmeticSlot } from '@/bridge/protocol';
import { useModSettings, useVoidStore } from '@/store/store';
import { cx } from '@/ui';
import { Sprite, hasSprite, type SpriteName } from './Sprite';
import { Kc, Led, Switch } from './prims';
import {
  COSMETIC_SLOTS,
  EMOTE_WHEEL_SIZE,
  RARITY_HUE,
  SLOT_LABEL,
  SLOT_NOUN,
  findCosmetic,
  itemsFor,
  priceLabel,
  useCosmetics,
} from './cosmetics-store';
import { FooterKeys } from './ModsTab';
import { useDevState } from './dev';
import { useModKey } from './Cartridge';

type View = 'wardrobe' | 'emotes';

/** The sprite for an item, or its slot's glyph when the sheet has no cell for it yet. */
export function itemSprite(id: string | null | undefined, kind?: CosmeticSlot | 'emote'): SpriteName {
  if (id && hasSprite(id)) return id;
  const item = findCosmetic(id);
  const slot = kind ?? item?.kind ?? 'emote';
  const glyph = `cos_${slot === 'emote' ? 'emote' : slot}`;
  return hasSprite(glyph) ? glyph : 'logo';
}

function rarityLine(item: CosmeticCatalogItem): ReactNode {
  return (
    <span className="c-cos__rarity" style={{ color: RARITY_HUE[item.rarity] }}>
      {item.rarity}
    </span>
  );
}

/**
 * "Get in launcher Store": the launcher comes forward on the item's Store sheet, never a purchase
 * here. The toast says which happened — opened, or, with no launcher link, where to go.
 */
function useStoreNotice() {
  const pushToast = useVoidStore((s) => s.pushToast);
  const openStore = useCosmetics((s) => s.openStore);
  return (item: CosmeticCatalogItem) => {
    const title = `${item.name} · ${priceLabel(item)}`;
    if (openStore(item.id)) {
      pushToast({ kind: 'info', title, detail: 'Opened in the VOID launcher’s Store — no purchases in game' });
    } else {
      pushToast({ kind: 'info', title, detail: 'Get it in the VOID launcher’s Store — no purchases in game' });
    }
  };
}

/* -------------------------------------------------------------------------- */
/* The paper doll                                                             */
/* -------------------------------------------------------------------------- */

const CELL = 20; // one art pixel of the 16 × 16 doll, in design px

interface Placed {
  slot: CosmeticSlot;
  /** Centre x and top y in doll cells, and the drawn edge in cells. */
  x: number;
  y: number;
  size: number;
  /** Drawn behind the body. */
  behind?: boolean;
}

function placements(facing: 'front' | 'back'): Placed[] {
  return [
    { slot: 'back', x: 8, y: 1.5, size: 15, behind: facing === 'front' },
    { slot: 'cape', x: 8, y: 3.6, size: 8.5, behind: facing === 'front' },
    { slot: 'trail', x: 3.2, y: 10.5, size: 6 },
    { slot: 'head', x: 8, y: -3.3, size: 6.2 },
    { slot: 'face', x: 8, y: 0.2, size: 4.2, behind: facing === 'back' },
    { slot: 'halo', x: 8, y: -5.6, size: 6 },
  ];
}

export function PaperDoll({ facing, emote }: { facing: 'front' | 'back'; emote?: CosmeticId | null }) {
  const self = useCosmetics((s) => s.self);
  const name = useVoidStore((s) => s.session?.name?.trim() || 'You');
  const parts = placements(facing);
  const worn = (slot: CosmeticSlot) => (self ? (self[slot] as CosmeticId | null) : null);
  const tintOf = (slot: CosmeticSlot) => (self?.tints as Record<string, string> | undefined)?.[slot];
  const layer = (p: Placed) => {
    const id = worn(p.slot);
    if (!id) return null;
    const size = p.size * CELL;
    const tint = tintOf(p.slot);
    return (
      <span
        key={p.slot}
        className={cx('c-doll__item', `c-doll__item--${p.slot}`)}
        style={{ left: p.x * CELL - size / 2, top: p.y * CELL, width: size, height: size }}
        data-slot={p.slot}
      >
        <Sprite name={itemSprite(id, p.slot)} size={size} />
        {tint ? <i className="c-doll__tint" style={{ background: tint }} /> : null}
      </span>
    );
  };
  const badge = worn('badge');
  const bodyName: string = facing === 'front' ? 'doll_front' : 'doll_back';
  const body: SpriteName = hasSprite(bodyName) ? bodyName : 'steve';
  return (
    <div className={cx('c-doll', `c-doll--${facing}`)} style={{ width: 16 * CELL, height: 16 * CELL }}>
      <i className="c-doll__shadow" />
      {parts.filter((p) => p.behind).map(layer)}
      <Sprite name={body} size={16 * CELL} className="c-doll__body" />
      {parts.filter((p) => !p.behind).map(layer)}
      <span className="c-doll__tag" style={{ top: (worn('halo') ? -7.4 : worn('head') ? -5.2 : -3.4) * CELL }}>
        {badge ? <Sprite name={itemSprite(badge, 'badge')} size={16} /> : null}
        {name}
      </span>
      {emote ? (
        <span className="c-doll__emote">
          <Sprite name={itemSprite(emote, 'emote')} size={20} />
          {findCosmetic(emote)?.name} · playing
        </span>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Item cards                                                                 */
/* -------------------------------------------------------------------------- */

function ItemCard({
  item,
  owned,
  wearing,
  onWear,
  note,
}: {
  item: CosmeticCatalogItem;
  owned: boolean;
  wearing: boolean;
  onWear: () => void;
  note?: string;
}) {
  const store = useStoreNotice();
  return (
    <div className={cx('c-cos__card', wearing && 'is-wearing', !owned && 'is-locked')} data-cosmetic-id={item.id}>
      <button
        type="button"
        className="c-cos__hit"
        aria-label={owned ? `${wearing ? 'Take off' : 'Wear'} ${item.name}` : `${item.name} — get it in the launcher Store`}
        onClick={() => (owned ? onWear() : store(item))}
      />
      <span className="c-cos__bed">
        <Sprite name={itemSprite(item.id, item.kind)} size={64} dim={owned ? undefined : 0.35} />
        {item.is_new ? <span className="c-cos__new c-mono">new</span> : null}
        {!owned ? <Sprite name="lock" size={16} className="c-cos__lock" /> : null}
      </span>
      <span className="c-cos__name">{item.name}</span>
      <span className="c-cos__meta">
        {rarityLine(item)}
        {!owned ? <span className="c-cos__price">{priceLabel(item)}</span> : note ? <span className="c-cos__note">{note}</span> : null}
      </span>
      <span className="c-cos__act">
        {owned ? (
          <span className={cx('c-cos__pill', wearing && 'is-on')}>{wearing ? 'Wearing' : item.kind === 'emote' ? 'Owned' : 'Wear'}</span>
        ) : (
          <span className="c-cos__pill c-cos__pill--store">Get in launcher Store ↗</span>
        )}
      </span>
    </div>
  );
}

const TINTS: readonly string[] = ['#eceef1', '#e8d9b0', '#c9d6e6', '#b8a8ff', '#ffb3c1', '#a8e6c6', '#7a7f88', '#2a2d33'];

function SlotPicker({ slot }: { slot: CosmeticSlot }) {
  const self = useCosmetics((s) => s.self);
  const owned = useCosmetics((s) => s.owned);
  const equip = useCosmetics((s) => s.equip);
  const tint = useCosmetics((s) => s.tint);
  const store = useStoreNotice();
  const [filter, setFilter] = useState<'owned' | 'all'>('owned');
  const items = itemsFor(slot);
  const mine = items.filter((i) => owned.includes(i.id));
  const shown = filter === 'owned' ? mine : items;
  const worn = self ? (self[slot] as CosmeticId | null) : null;
  const wornItem = findCosmetic(worn);
  const currentTint = (self?.tints as Record<string, string> | undefined)?.[slot] ?? null;
  const locked = items.filter((i) => !owned.includes(i.id));

  return (
    <div className="c-cos__picker">
      <div className="c-cos__phead">
        <span className="c-title">{SLOT_LABEL[slot]}</span>
        <span className="c-mono">
          {mine.length} owned · {locked.length} in store
        </span>
      </div>
      <div className="c-cos__seg" role="tablist" aria-label="Show">
        {(['owned', 'all'] as const).map((f) => (
          <button key={f} type="button" role="tab" aria-selected={filter === f} className={cx('c-chip', filter === f && 'is-on')} onClick={() => setFilter(f)}>
            {f === 'owned' ? 'Owned' : 'All'}
            <i>{f === 'owned' ? mine.length : items.length}</i>
          </button>
        ))}
      </div>
      {shown.length === 0 ? (
        <div className="c-cos__empty">
          <span className="c-cos__emptybed">
            <Sprite name={itemSprite(null, slot)} size={48} dim={0.5} />
          </span>
          <span className="c-label">Nothing for your {SLOT_LABEL[slot].toLowerCase()} yet</span>
          <span className="c-cos__emptyline">
            {SLOT_NOUN[slot].charAt(0).toUpperCase() + SLOT_NOUN[slot].slice(1)} you get in the launcher Store appear here, already on you.
          </span>
          {locked[0] ? (
            <button type="button" className="c-btn c-btn--p c-btn--sm" onClick={() => store(locked[0]!)}>
              Get in launcher Store ↗
            </button>
          ) : null}
          <span className="c-mono">No purchases in game</span>
        </div>
      ) : (
        <div className="c-cos__grid">
          {shown.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              owned={owned.includes(item.id)}
              wearing={worn === item.id}
              onWear={() => equip(slot, worn === item.id ? null : item.id)}
            />
          ))}
        </div>
      )}
      {wornItem?.tintable ? (
        <div className="c-cos__tints">
          <span className="c-mono">Tint · {wornItem.name}</span>
          <div className="c-swatches" role="radiogroup" aria-label={`${wornItem.name} tint`}>
            {TINTS.map((hex) => (
              <button
                key={hex}
                type="button"
                role="radio"
                aria-checked={currentTint === hex}
                aria-label={hex}
                className={cx('c-swatch', currentTint === hex && 'is-on')}
                style={{ background: hex }}
                onClick={() => tint(slot, currentTint === hex ? null : hex)}
              />
            ))}
          </div>
          <span className="c-mono c-cos__tintnote">Tints save instantly · free with the item</span>
        </div>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Wardrobe view                                                              */
/* -------------------------------------------------------------------------- */

const LEFT_SLOTS: readonly CosmeticSlot[] = ['badge', 'head', 'back', 'trail'];
const RIGHT_SLOTS: readonly CosmeticSlot[] = ['halo', 'face', 'cape'];

function SlotCard({ slot, selected, onSelect }: { slot: CosmeticSlot; selected: boolean; onSelect: () => void }) {
  const id = useCosmetics((s) => (s.self ? (s.self[slot] as CosmeticId | null) : null));
  const item = findCosmetic(id);
  return (
    <button
      type="button"
      className={cx('c-slot', selected && 'is-sel', !id && 'is-empty')}
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`${SLOT_LABEL[slot]}${item ? ` — ${item.name}` : ' — empty'}`}
      data-slot={slot}
    >
      <span className="c-slot__bed">
        {id ? <Sprite name={itemSprite(id, slot)} size={48} /> : <span className="c-slot__plus">+</span>}
        {item ? <i className="c-slot__led" style={{ background: RARITY_HUE[item.rarity] }} /> : null}
      </span>
      <span className="c-mono c-slot__label">{SLOT_LABEL[slot]}</span>
    </button>
  );
}

function WheelMini({ onEdit }: { onEdit: () => void }) {
  const holdKey = useModKey('cosmetics');
  const wheel = useCosmetics((s) => s.self?.emotes ?? []);
  const setWheelOpen = useCosmetics((s) => s.setWheelOpen);
  return (
    <div className="c-blk c-cos__wheelmini">
      <div className="c-cos__ring" aria-hidden="true">
        {Array.from({ length: EMOTE_WHEEL_SIZE }, (_, i) => {
          const a = (i / EMOTE_WHEEL_SIZE) * Math.PI * 2 - Math.PI / 2;
          const id = wheel[i] as CosmeticId | undefined;
          return (
            <span key={i} className={cx('c-cos__ringslot', !id && 'is-empty')} style={{ left: 62 + Math.cos(a) * 52, top: 62 + Math.sin(a) * 52 }}>
              {id ? <Sprite name={itemSprite(id, 'emote')} size={20} /> : null}
            </span>
          );
        })}
        <span className="c-cos__ringkey">
          <Kc>{holdKey ?? 'B'}</Kc>
        </span>
      </div>
      <div className="c-cos__wheeltext">
        <span className="c-label">
          {wheel.length} of {EMOTE_WHEEL_SIZE}
        </span>
        <span className="c-mono">{holdKey ? `Hold ${holdKey}` : 'No key · menu only'}</span>
        <button type="button" className="c-btn c-btn--s c-btn--sm" onClick={onEdit}>
          Edit
        </button>
        <button type="button" className="c-btn c-btn--g c-btn--sm" onClick={() => setWheelOpen(true)}>
          Try it
        </button>
      </div>
    </div>
  );
}

function Wardrobe({ onEmotes }: { onEmotes: () => void }) {
  const [slot, setSlot] = useState<CosmeticSlot>('cape');
  const [facing, setFacing] = useState<'front' | 'back'>('back');
  const self = useCosmetics((s) => s.self);
  const playing = useCosmetics((s) => s.playing);

  if (!self) {
    return (
      <div className="c-cos__loading">
        <div className="c-cos__skel">
          {Array.from({ length: 4 }, (_, i) => (
            <span key={i} className="c-cos__skelcard" />
          ))}
        </div>
        <span className="c-label">Fetching your wardrobe…</span>
        <span className="c-mono">Your doll keeps what you wear while this loads</span>
      </div>
    );
  }

  return (
    <div className="c-cos">
      <div className="c-cos__left">
        <span className="c-mono c-col__cap">Wearing</span>
        <div className="c-cos__worn">
          {COSMETIC_SLOTS.map((s) => {
            const item = findCosmetic(self[s] as CosmeticId | null);
            return (
              <button key={s} type="button" className={cx('c-row2 c-cos__wornrow', slot === s && 'is-sel', !item && 'is-off')} onClick={() => setSlot(s)}>
                <span className="c-cos__wornname">
                  <span className="c-cos__wornbed">{item ? <Sprite name={itemSprite(item.id, s)} size={24} /> : null}</span>
                  <span className="c-cos__worntext">
                    <span>{item ? item.name : 'Empty'}</span>
                    <span className="c-mono">{SLOT_LABEL[s]}</span>
                  </span>
                </span>
                {item ? <Led on style={{ background: RARITY_HUE[item.rarity] }} /> : null}
              </button>
            );
          })}
        </div>
        <span className="c-mono c-col__cap">Emote wheel</span>
        <WheelMini onEdit={onEmotes} />
      </div>

      <div className="c-cos__stage">
        <span className="c-cos__others">
          <Led on className="c-led--live" />
          <span className="c-mono">What other VOID players see</span>
        </span>
        <div className="c-cos__slots c-cos__slots--l">
          {LEFT_SLOTS.map((s) => (
            <SlotCard key={s} slot={s} selected={slot === s} onSelect={() => setSlot(s)} />
          ))}
        </div>
        <div className="c-cos__dollwrap">
          <PaperDoll facing={facing} emote={playing} />
        </div>
        <div className="c-cos__slots c-cos__slots--r">
          {RIGHT_SLOTS.map((s) => (
            <SlotCard key={s} slot={s} selected={slot === s} onSelect={() => setSlot(s)} />
          ))}
        </div>
        <div className="c-cos__turn" role="radiogroup" aria-label="Facing">
          <button type="button" className="c-cos__arrow" aria-label="Turn" onClick={() => setFacing(facing === 'front' ? 'back' : 'front')}>
            ‹
          </button>
          {(['back', 'front'] as const).map((f) => (
            <button key={f} type="button" role="radio" aria-checked={facing === f} className={cx('c-prev__biome', facing === f && 'is-on')} onClick={() => setFacing(f)}>
              {f === 'back' ? 'Back' : 'Front'}
            </button>
          ))}
          <button type="button" className="c-cos__arrow" aria-label="Turn" onClick={() => setFacing(facing === 'front' ? 'back' : 'front')}>
            ›
          </button>
        </div>
      </div>

      <SlotPicker key={slot} slot={slot} />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Emote wheel editor                                                         */
/* -------------------------------------------------------------------------- */

const COSMETICS_SWITCHES: readonly { key: string; label: string }[] = [
  { key: 'others', label: 'Show other players’ cosmetics' },
  { key: 'emote_camera', label: 'Camera orbits while you emote' },
  { key: 'glow', label: 'Glow on halos, wings and trails' },
  { key: 'own_first_person', label: 'Own trail in first person' },
];

/** `cosmetics.reactive`: how much cosmetics answer the fight (the mod's own schema). */
const REACTIVE_LEVELS = ['off', 'subtle', 'full'] as const;

function EmoteEditor() {
  const holdKey = useModKey('cosmetics');
  const self = useCosmetics((s) => s.self);
  const owned = useCosmetics((s) => s.owned);
  const setWheel = useCosmetics((s) => s.setWheel);
  const play = useCosmetics((s) => s.play);
  const playing = useCosmetics((s) => s.playing);
  const settings = useModSettings('cosmetics');
  const setSetting = useVoidStore((s) => s.setSetting);
  const reactive = typeof settings.reactive === 'string' ? settings.reactive : 'full';
  const [sel, setSel] = useState(0);
  const wheel = (self?.emotes ?? []) as CosmeticId[];
  const emotes = itemsFor('emote');
  const selId = wheel[sel] ?? null;
  const selItem = findCosmetic(selId);

  const put = (id: CosmeticId) => {
    // Place on the selected slot; an emote already elsewhere on the wheel moves here.
    const next = wheel.filter((w) => w !== id);
    const at = Math.min(sel, next.length);
    next.splice(at, wheel[sel] && wheel[sel] !== id ? 1 : 0, id);
    setWheel(next.slice(0, EMOTE_WHEEL_SIZE));
  };
  const clear = () => {
    if (!selId) return;
    setWheel(wheel.filter((w) => w !== selId));
  };

  return (
    <div className="c-cos">
      <div className="c-cos__left">
        <span className="c-mono c-col__cap">Emote wheel</span>
        <div className="c-row2 c-cos__hold">
          <span className="c-cos__worntext">
            <span>Hold to open</span>
            <span className="c-mono">Flick to a slot, release to play · 1–8 pick</span>
          </span>
          <Kc>{holdKey ?? 'None'}</Kc>
        </div>
        <span className="c-mono c-col__cap">Play</span>
        {COSMETICS_SWITCHES.map((sw) => (
          <div key={sw.key} className={cx('c-row2', settings[sw.key] !== true && 'is-off')}>
            <span className="c-row2__label">{sw.label}</span>
            <Switch checked={settings[sw.key] === true} label={sw.label} onChange={(next) => setSetting('cosmetics', sw.key, next)} />
          </div>
        ))}
        <div className={cx('c-row2', reactive === 'off' && 'is-off')}>
          <span className="c-row2__label">Fight reactions</span>
          <span className="c-prev__biomes c-cos__levels" role="radiogroup" aria-label="Fight reactions">
            {REACTIVE_LEVELS.map((lv) => (
              <button key={lv} type="button" role="radio" aria-checked={reactive === lv} className={cx('c-prev__biome', reactive === lv && 'is-on')} onClick={() => setSetting('cosmetics', 'reactive', lv)}>
                {lv === 'off' ? 'Off' : lv === 'subtle' ? 'Subtle' : 'Full'}
              </button>
            ))}
          </span>
        </div>
        <span className="c-mono c-col__cap">Selected slot</span>
        <div className="c-blk c-cos__selslot">
          <span className="c-cos__wornbed c-cos__wornbed--lg">{selId ? <Sprite name={itemSprite(selId, 'emote')} size={40} /> : <span className="c-slot__plus">+</span>}</span>
          <span className="c-cos__worntext">
            <span className="c-label">{selItem ? selItem.name : 'Empty slot'}</span>
            <span className="c-mono">
              Slot {sel + 1}
              {selItem ? ` · ${selItem.rarity}${selItem.loop ? ' · loops' : ''}` : ''}
            </span>
            <span className="c-cos__selacts">
              <button type="button" className="c-btn c-btn--g c-btn--sm" disabled={!selId} onClick={clear}>
                Clear
              </button>
              <button type="button" className="c-btn c-btn--s c-btn--sm" disabled={!selId} onClick={() => selId && play(selId)}>
                Preview
              </button>
            </span>
          </span>
        </div>
      </div>

      <div className="c-cos__stage c-cos__stage--ring">
        <span className="c-cos__others">
          <Led on className="c-led--live" />
          <span className="c-mono">{selItem ? `Slot ${sel + 1} · ${selItem.name}` : `Slot ${sel + 1} · empty`}</span>
        </span>
        <div className="c-cos__bigring">
          {Array.from({ length: EMOTE_WHEEL_SIZE }, (_, i) => {
            const a = (i / EMOTE_WHEEL_SIZE) * Math.PI * 2 - Math.PI / 2;
            const id = wheel[i] as CosmeticId | undefined;
            return (
              <button
                key={i}
                type="button"
                className={cx('c-cos__wslot', sel === i && 'is-sel', !id && 'is-empty', playing && id === playing && 'is-playing')}
                style={{ left: 230 + Math.cos(a) * 205 - 34, top: 230 + Math.sin(a) * 205 - 34 }}
                onClick={() => setSel(i)}
                aria-label={`Slot ${i + 1}${id ? ` — ${findCosmetic(id)?.name}` : ' — empty'}`}
              >
                {id ? <Sprite name={itemSprite(id, 'emote')} size={36} /> : <span className="c-slot__plus">+</span>}
                <span className="c-cos__wnum">{i + 1}</span>
              </button>
            );
          })}
          <div className="c-cos__ringdoll">
            <PaperDoll facing="front" emote={playing} />
          </div>
        </div>
        <span className="c-mono c-cos__ringfoot">Pick a slot, then an emote · the ring is the wheel you hold in game</span>
      </div>

      <div className="c-cos__picker">
        <div className="c-cos__phead">
          <span className="c-title">Emotes</span>
          <span className="c-mono">
            {emotes.filter((e) => owned.includes(e.id)).length} owned · {emotes.filter((e) => !owned.includes(e.id)).length} in store
          </span>
        </div>
        <div className="c-cos__grid">
          {emotes.map((item) => {
            const on = wheel.indexOf(item.id);
            return (
              <ItemCard
                key={item.id}
                item={item}
                owned={owned.includes(item.id)}
                wearing={on >= 0}
                note={on >= 0 ? `slot ${on + 1}` : `to slot ${sel + 1}`}
                onWear={() => put(item.id)}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */

export function CosmeticsTab() {
  const dev = useDevState();
  const [view, setView] = useState<View>(dev.cosmeticsView ?? 'wardrobe');
  const owned = useCosmetics((s) => s.owned.length);
  return (
    <>
      <header className="c-head">
        <div className="c-head__title">
          <span className="c-h1">Cosmetics</span>
          <span className="c-mono">{owned} owned</span>
        </div>
        <div className="c-chips" role="tablist" aria-label="Wardrobe">
          {(['wardrobe', 'emotes'] as const).map((v) => (
            <button key={v} type="button" role="tab" aria-selected={view === v} className={cx('c-chip', view === v && 'is-on')} onClick={() => setView(v)}>
              {v === 'wardrobe' ? 'Wardrobe' : 'Emote wheel'}
            </button>
          ))}
        </div>
        <span className="c-cos__store c-mono">USD · bought in the launcher</span>
      </header>
      <div className="c-cos__body">{view === 'wardrobe' ? <Wardrobe onEmotes={() => setView('emotes')} /> : <EmoteEditor />}</div>
      <footer className="c-foot">
        <span className="c-mono">Only VOID players see cosmetics · vanilla players see your normal skin</span>
        <FooterKeys
          keys={[
            ['B', 'emote wheel'],
            ['Click', 'wear'],
            ['Esc', 'back'],
          ]}
        />
      </footer>
    </>
  );
}

