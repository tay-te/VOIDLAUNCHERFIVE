/**
 * Notices — the `toast` channel and the page's own (`store.pushToast`), drawn as Cartridge
 * toasts (Figma 630:2 / 632:2): a sprite in an art bed, the line and its detail, the key that
 * did it, and a stepped segment timer that drains left to right over `TOAST_MS`.
 *
 * Top right, left of where the effects module sits, over both layers. At most three; each goes
 * on one `setTimeout`. The drain is a CSS animation stepped in 30 — it never needs a frame loop.
 */

import { useEffect } from 'react';

import { cx } from '@/ui';
import { TOAST_MS, useVoidStore, type Toast } from '@/store/store';
import { Sprite, hasSprite, type SpriteName } from '@/cart/Sprite';
import { Kc, Seg } from '@/cart/prims';
import { loadoutEmblem } from '@/cart/LoadoutsTab';
import { keybindLabel } from '@/menu/settings-format';

function mark(toast: Toast, state: ReturnType<typeof useVoidStore.getState>): SpriteName {
  if (toast.kind === 'mod' && toast.mod && hasSprite(toast.mod)) return toast.mod;
  if (toast.kind === 'loadout') {
    const named = state.library.find((l) => l.name === toast.title) ?? state.loadout;
    return named ? loadoutEmblem(named) : 'sword';
  }
  return 'logo';
}

const SEGS = 30;

function ToastRow({ toast }: { toast: Toast }): React.ReactElement {
  const dismiss = useVoidStore((s) => s.dismissToast);
  const cycleKey = useVoidStore((s) => s.globals.cycleLoadoutKey);
  const sprite = mark(toast, useVoidStore.getState());
  useEffect(() => {
    const left = Math.max(0, toast.at + TOAST_MS - Date.now());
    const timer = setTimeout(() => dismiss(toast.id), left);
    return () => clearTimeout(timer);
  }, [toast.id, toast.at, dismiss]);
  const key = toast.kind === 'loadout' && cycleKey && cycleKey !== 'NONE' ? keybindLabel(cycleKey) : null;
  return (
    <div className={cx('vtoast', `vtoast--${toast.kind}`)} role="status">
      <span className="vtoast__mark" aria-hidden="true">
        <Sprite name={sprite} size={28} />
      </span>
      <span className="vtoast__text">
        <span className="vtoast__title">{toast.title}</span>
        {toast.detail ? <span className="vtoast__detail">{toast.detail}</span> : null}
      </span>
      {key ? <Kc>{key}</Kc> : null}
      <span className="vtoast__timer" aria-hidden="true">
        <Seg n={SEGS} on={0} w={8.6} h={3} gap={1.6} off="rgba(255,255,255,0.08)" />
        <span className="vtoast__drain" style={{ animationDuration: `${TOAST_MS}ms` }}>
          <Seg n={SEGS} on={SEGS} w={8.6} h={3} gap={1.6} />
        </span>
      </span>
    </div>
  );
}

export function ToastStack(): React.ReactElement | null {
  const toasts = useVoidStore((s) => s.toasts);
  if (toasts.length === 0) return null;
  return (
    <div className="vtoasts" aria-live="polite">
      {/* HUD scale, not menu scale: a toast sits beside the effects module and must line up
          with it, and HUD modules are laid out in the view's own CSS px. */}
      <div className="vtoasts__in">
        {toasts.map((t) => (
          <ToastRow key={t.id} toast={t} />
        ))}
      </div>
    </div>
  );
}
