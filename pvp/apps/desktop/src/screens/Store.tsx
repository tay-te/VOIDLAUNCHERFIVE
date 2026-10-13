/**
 * Store — Figma v1 Ship `73:211` (gallery) and `75:236` (item detail).
 *
 * Rule 5: the Store is a gallery. Items are sold for real money through secure checkout;
 * cosmetic only, no gameplay advantage (Mojang's commercial guidelines). **Buying always
 * confirms, and no key shortcut spends money** — the Buy button opens a confirmation, the
 * confirmation's own button is the only thing that continues, and neither has a shortcut.
 * Rule 8: cosmetics are visible to other VOID players only, and the Store says so.
 *
 * TODO(integrate): there is no checkout yet. The confirmation says so in plain words
 * rather than pretending — it is the step where the checkout URL would open in the
 * system browser (`@tauri-apps/plugin-opener`), with the receipt going by email.
 */

import { useEffect, useMemo, useState } from 'react';

import capeIcon from '../assets/icons/cape.svg';
import smileIcon from '../assets/icons/smile.svg';
import sparklesIcon from '../assets/icons/sparkles.svg';
import tagIcon from '../assets/icons/tag.svg';
import { Character, CosmeticArt } from '../features/art';
import {
  CATALOG,
  CATEGORIES,
  KIND_LABEL,
  findItem,
  formatPrice,
  type CosmeticItem,
  type CosmeticKind,
} from '../local/catalog';
import { useCosmetics } from '../stores/cosmetics';
import { useUi } from '../stores/ui';

const TAB_ICONS: Partial<Record<CosmeticKind, string>> = {
  cape: capeIcon,
  trail: sparklesIcon,
  emote: smileIcon,
  nameplate: tagIcon,
};

function ItemState({ item }: { item: CosmeticItem }) {
  const owned = useCosmetics((s) => s.owned.includes(item.id));
  const equipped = useCosmetics((s) => s.equipped[item.kind] === item.id);
  if (equipped) return <span className="sitem__state">✓ Equipped</span>;
  if (owned) return <span className="sitem__state">Owned</span>;
  return <span className="sitem__price tnum">{formatPrice(item.price_cents)}</span>;
}

/**
 * The accent ring marks the selected item (§1): the one on the sheet while a sheet is
 * open, else the equipped cape — which is how the two frames draw it.
 */
function GalleryItem({ item, selected, sheetOpen }: { item: CosmeticItem; selected: boolean; sheetOpen: boolean }) {
  const openStoreItem = useUi((s) => s.openStoreItem);
  const equipped = useCosmetics((s) => s.equipped[item.kind] === item.id);
  const ring = sheetOpen ? selected : equipped && item.kind === 'cape';
  return (
    <button
      type="button"
      className={`sitem${ring ? ' is-ring' : ''}`}
      aria-pressed={selected}
      onClick={() => openStoreItem(item.id)}
    >
      <span className="sitem__art">
        <CosmeticArt item={item} />
      </span>
      <span className="sitem__meta">
        <span className="sitem__text">
          <span className="sitem__name">{item.name}</span>
          <span className="sitem__kind">
            {KIND_LABEL[item.kind]}
            {item.isNew ? ' · new' : ''}
          </span>
        </span>
        <ItemState item={item} />
      </span>
    </button>
  );
}

function ItemSheet({ item }: { item: CosmeticItem }) {
  const close = useUi((s) => s.closeStoreItem);
  const owned = useCosmetics((s) => s.owned.includes(item.id));
  const equipped = useCosmetics((s) => s.equipped[item.kind] === item.id);
  const equip = useCosmetics((s) => s.equip);
  const wornCape = useCosmetics((s) => findItem(s.equipped.cape));
  const [view, setView] = useState<'front' | 'back'>('back');
  const [confirming, setConfirming] = useState(false);
  const [noCheckout, setNoCheckout] = useState(false);

  // A new item starts from the top of the flow.
  useEffect(() => {
    setConfirming(false);
    setNoCheckout(false);
    setView('back');
  }, [item.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (confirming) setConfirming(false);
      else close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [confirming, close]);

  // A cape is tried on; anything else is shown as itself, with the worn cape behind it.
  const preview =
    item.kind === 'cape' ? (
      <>
        <span className="sheet__figure">
          <Character cape={item.art} view={view} scale={0.9} />
        </span>
        <span className="segmented sheet__views" role="group" aria-label="View">
          {(['front', 'back'] as const).map((v) => (
            <button key={v} type="button" className={view === v ? 'is-on' : undefined} aria-pressed={view === v} onClick={() => setView(v)}>
              {v === 'front' ? 'Front' : 'Back'}
            </button>
          ))}
        </span>
      </>
    ) : (
      <span className="sheet__art">
        <CosmeticArt item={item} />
      </span>
    );

  return (
    <aside className="sheet sheet--item" aria-label={item.name}>
      <header className="sheet__head">
        <h2 className="sheet__title">{item.name}</h2>
        <button type="button" className="btn2 btn2--secondary" onClick={close}>
          Close
        </button>
      </header>

      <div className="sheet__preview">{preview}</div>

      <div className="sheet__meta">
        <p className="sheet__price tnum">{owned ? (equipped ? 'Equipped' : 'Owned') : formatPrice(item.price_cents)}</p>
        <p className="sheet__blurb">
          {KIND_LABEL[item.kind]} · {item.blurb}
        </p>
        {item.kind !== 'cape' && wornCape ? (
          <p className="sheet__blurb sheet__blurb--muted">Shown with your {wornCape.name}.</p>
        ) : null}
      </div>

      <span className="sheet__spacer" />

      {owned ? (
        <button type="button" className="btn2 btn2--primary btn2--block" onClick={() => equip(item.id)}>
          {equipped ? 'Take off' : 'Equip'}
        </button>
      ) : confirming ? (
        <div className="confirm" role="alertdialog" aria-labelledby="confirm-title">
          <p id="confirm-title" className="confirm__title">
            Buy {item.name} for {formatPrice(item.price_cents)}?
          </p>
          <p className="confirm__body">
            {noCheckout
              ? 'Checkout isn’t connected in this build yet, so nothing was charged. The item stays in the Store.'
              : 'You’ll finish in a secure checkout in your browser. Cosmetic only — no gameplay advantage.'}
          </p>
          <div className="confirm__actions">
            <button type="button" className="btn2 btn2--secondary" onClick={() => setConfirming(false)}>
              {noCheckout ? 'Close' : 'Cancel'}
            </button>
            {!noCheckout ? (
              <button type="button" className="btn2 btn2--primary" onClick={() => setNoCheckout(true)}>
                Continue to checkout
              </button>
            ) : null}
          </div>
        </div>
      ) : (
        <button type="button" className="btn2 btn2--primary btn2--block sheet__buy" onClick={() => setConfirming(true)}>
          Buy{'  ·  '}
          <span className="tnum">{formatPrice(item.price_cents)}</span>
        </button>
      )}
      <p className="sheet__note">
        {owned ? 'Visible to other VOID players' : 'Opens secure checkout · receipt by email'}
      </p>
    </aside>
  );
}

export function StoreScreen() {
  const storeItem = useUi((s) => s.storeItem);
  const [tab, setTab] = useState<'all' | CosmeticKind>('all');
  const counts = useMemo(() => {
    const c: Record<string, number> = { all: CATALOG.length };
    for (const item of CATALOG) c[item.kind] = (c[item.kind] ?? 0) + 1;
    return c;
  }, []);
  const items = tab === 'all' ? CATALOG : CATALOG.filter((i) => i.kind === tab);
  const open = findItem(storeItem);

  return (
    <div className={`store${open ? ' has-sheet' : ''}`}>
      <h1 className="display2 store__title">Store</h1>

      <div className="store__bar">
        <div className="segmented segmented--tabs" role="tablist" aria-label="Category">
          {CATEGORIES.map((c) => (
            <button
              key={c.id}
              type="button"
              role="tab"
              aria-selected={tab === c.id}
              className={tab === c.id ? 'is-on' : undefined}
              onClick={() => setTab(c.id)}
            >
              {c.id !== 'all' && TAB_ICONS[c.id] ? <img src={TAB_ICONS[c.id]} width={16} height={16} alt="" /> : null}
              <span>{c.label}</span>
              <span className="segmented__count tnum">{counts[c.id] ?? 0}</span>
            </button>
          ))}
        </div>
        <span className="store__note">Cosmetic only · visible to other VOID players</span>
      </div>

      <div className="store__grid">
        {items.map((item) => (
          <GalleryItem key={item.id} item={item} selected={item.id === open?.id} sheetOpen={Boolean(open)} />
        ))}
      </div>

      {open ? <ItemSheet item={open} /> : null}
    </div>
  );
}
