/**
 * Notices — the `toast` channel, drawn over the game for a moment and then gone.
 *
 * Java raises them for what happens with the menu closed, where nothing else on screen would
 * say so: the cycle key landed on a loadout, the launcher switched it, a mod's own key flipped
 * it. The page raises its own for what it did itself — an import, a delete, a copied code —
 * through `store.pushToast`, without a round trip. Both land in `store.toasts`.
 *
 * Drawn by `App` above both layers, so a notice raised from the Loadouts screen shows over the
 * menu and one raised by the cycle key shows over the game. The look is the islands' own card
 * (`islands.css`): flat, 1px hairline, rounded, no shadow — a small island at the top centre,
 * clear of the tabs island (which only exists while the menu is up) by sitting just under it.
 *
 * **Timing.** Each notice removes itself after {@link TOAST_MS} with one `setTimeout`, not a
 * frame loop (`check-ultralight.mjs` bans `requestAnimationFrame`; the 20 Hz tick is the only
 * clock, and a notice must also leave with the menu open and the game paused, when no tick
 * arrives). The view is repainted while the entry animation runs and once when the node goes,
 * which is the whole cost.
 */

import { useEffect } from 'react';

import { Icon, MOD_ICONS, type IconName, cx } from '@/ui';
import { TOAST_MS, useVoidStore, type Toast } from '@/store/store';

function glyph(toast: Toast): IconName {
  if (toast.kind === 'mod' && toast.mod && MOD_ICONS[toast.mod]) return MOD_ICONS[toast.mod];
  if (toast.kind === 'loadout') return 'layers';
  return 'check';
}

function ToastRow({ toast }: { toast: Toast }): React.ReactElement {
  const dismiss = useVoidStore((s) => s.dismissToast);
  useEffect(() => {
    const left = Math.max(0, toast.at + TOAST_MS - Date.now());
    const timer = setTimeout(() => dismiss(toast.id), left);
    return () => clearTimeout(timer);
  }, [toast.id, toast.at, dismiss]);
  return (
    <div className={cx('vtoast', `vtoast--${toast.kind}`)} role="status">
      <span className="vtoast__mark" aria-hidden="true">
        <Icon name={glyph(toast)} size={14} />
      </span>
      <span className="vtoast__text">
        <span className="vtoast__title">{toast.title}</span>
        {toast.detail ? <span className="vtoast__detail">{toast.detail}</span> : null}
      </span>
    </div>
  );
}

/** Every notice on screen, newest at the bottom of the stack. */
export function ToastStack(): React.ReactElement | null {
  const toasts = useVoidStore((s) => s.toasts);
  if (toasts.length === 0) return null;
  return (
    <div className="vtoasts" aria-live="polite">
      {toasts.map((t) => (
        <ToastRow key={t.id} toast={t} />
      ))}
    </div>
  );
}
