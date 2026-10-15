/**
 * Tab 5 — Cosmetics. Phase 1 placeholder: the wardrobe lands with the cosmetics bridge.
 */

import { cx } from '@/ui';
import { Sprite, hasSprite } from './Sprite';

export function CosmeticsTab() {
  return (
    <div className="c-soon">
      <div className="c-soon__slots" aria-hidden="true">
        {(['cos_cape', 'cos_head', 'cos_halo'] as const).map((slot, i) => (
          <span key={slot} className={cx('c-empty__slot', i === 1 && 'c-empty__slot--lit')}>
            {hasSprite(slot) ? <Sprite name={slot} size={i === 1 ? 64 : 40} dim={i === 1 ? undefined : 0.35} /> : null}
          </span>
        ))}
      </div>
      <span className="c-mono">Not connected yet</span>
      <div className="c-title">Cosmetics</div>
      <p className="c-empty__line">
        Your capes, wings, hats and emotes, worn in game for other VOID players to see — once cosmetics are connected.
        Until then, pick them in the launcher’s Store.
      </p>
    </div>
  );
}
