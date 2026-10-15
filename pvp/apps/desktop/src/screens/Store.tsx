/**
 * Store — Figma v1 Ship `73:211` (gallery) and `75:236` (item detail).
 *
 * Rule 5: the Store is a gallery. Items are sold for real money through secure checkout;
 * cosmetic only, no gameplay advantage (Mojang's commercial guidelines). **Buying always
 * confirms, and no key shortcut spends money** — the Buy button opens a confirmation, the
 * confirmation's own button is the only thing that continues, and neither has a shortcut.
 * Rule 8: cosmetics are visible to other VOID players only, and the Store says so.
 *
 * Checkout: the confirmation's button asks Rust for a Stripe Checkout Session
 * (`store_checkout`), and Rust opens it in the system browser (plugin-opener). Nothing is
 * granted by the launcher: Stripe's webhook grants the item on the backend, which pushes it
 * back as `online:cosmetics`, and the sheet turns into *Owned* by itself. Offline, the
 * confirmation says buying needs a Microsoft account rather than pretending.
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
import { isWorn, useCosmetics } from '../stores/cosmetics';
import { useUi } from '../stores/ui';

const TAB_ICONS: Partial<Record<CosmeticKind, string>> = {
  cape: capeIcon,
  trail: sparklesIcon,
  emote: smileIcon,
  badge: tagIcon,
};

function ItemState({ item }: { item: CosmeticItem }) {
  const owned = useCosmetics((s) => s.owned.includes(item.id));
  const equipped = useCosmetics((s) => isWorn(s, item));
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
  const equipped = useCosmetics((s) => isWorn(s, item));
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
  const equipped = useCosmetics((s) => isWorn(s, item));
  const equip = useCosmetics((s) => s.equip);
  const buy = useCosmetics((s) => s.buy);
  const clearCheckout = useCosmetics((s) => s.clearCheckout);
  const checkout = useCosmetics((s) => (s.checkout?.item === item.id ? s.checkout : null));
  const equipError = useCosmetics((s) => s.error);
  const dismissError = useCosmetics((s) => s.dismissError);
  const wornCape = useCosmetics((s) => findItem(s.equipped.cape));
  const [view, setView] = useState<'front' | 'back'>('back');
  const [confirming, setConfirming] = useState(false);

  // A new item starts from the top of the flow.
  useEffect(() => {
    setConfirming(false);
    setView('back');
    dismissError();
  }, [item.id, dismissError]);

  // The purchase landed: the sheet is an owned item now.
  useEffect(() => {
    if (owned && confirming) setConfirming(false);
  }, [owned, confirming]);

  const stopConfirming = () => {
    setConfirming(false);
    if (checkout && checkout.phase !== 'waiting') clearCheckout();
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (confirming) stopConfirming();
      else close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const confirmBody = (() => {
    switch (checkout?.phase) {
      case 'opening':
        return 'Opening secure checkout in your browser…';
      case 'waiting':
        return `Finish paying in your browser. ${item.name} appears here as soon as the payment is confirmed — usually a few seconds. The receipt comes by email.`;
      case 'failed':
        return checkout.message ?? 'Checkout couldn’t open. Nothing was charged.';
      default:
        return 'You’ll finish in a secure checkout in your browser. Cosmetic only — no gameplay advantage.';
    }
  })();

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
        <button type="button" className="btn2 btn2--primary btn2--block" onClick={() => void equip(item.id)}>
          {equipped ? 'Take off' : 'Equip'}
        </button>
      ) : confirming ? (
        <div className="confirm" role="alertdialog" aria-labelledby="confirm-title" aria-busy={checkout?.phase === 'opening'}>
          <p id="confirm-title" className="confirm__title">
            Buy {item.name} for {formatPrice(item.price_cents)}?
          </p>
          <p className="confirm__body">{confirmBody}</p>
          <div className="confirm__actions">
            <button type="button" className="btn2 btn2--secondary" onClick={stopConfirming}>
              {checkout ? 'Close' : 'Cancel'}
            </button>
            {!checkout || checkout.phase === 'opening' ? (
              <button
                type="button"
                className="btn2 btn2--primary"
                disabled={checkout?.phase === 'opening'}
                onClick={() => void buy(item.id)}
              >
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
      {equipError ? (
        <p className="sheet__note" role="alert">
          {equipError}
        </p>
      ) : (
        <p className="sheet__note">
          {owned
            ? checkout?.phase === 'done'
              ? 'Purchased · visible to other VOID players'
              : 'Visible to other VOID players'
            : 'Opens secure checkout · receipt by email'}
        </p>
      )}
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
