/**
 * The armour widget with a held item that takes no damage.
 *
 * In-game audit `armor-no-held` vs the baseline: with `show_held_item` at its default `true`
 * and a block in hand, the whole widget drew nothing — four worn pieces and all — and turning
 * the setting *off* brought them back. This is that state, in jsdom.
 */
import { describe, expect, it, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

import { HudArmorStatus } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';

const WORN = [
  { slot: 'helmet', item: 'diamond_helmet', damage: 0, max_damage: 363, count: 1, enchanted: false },
  { slot: 'chestplate', item: 'diamond_chestplate', damage: 0, max_damage: 528, count: 1, enchanted: false },
  { slot: 'leggings', item: 'diamond_leggings', damage: 0, max_damage: 495, count: 1, enchanted: false },
  { slot: 'boots', item: 'diamond_boots', damage: 0, max_damage: 429, count: 1, enchanted: false },
];
/** A grass block: an item with no durability, which is what the audit had in hand. */
const HELD_BLOCK = { slot: 'held', item: 'grass', damage: 0, max_damage: 0, count: 1, enchanted: false };
/** Nothing in hand at all — the sensor sends the slot with a null item. */
const HELD_EMPTY = { slot: 'held', item: null, damage: 0, max_damage: 0, count: 0, enchanted: false };

beforeEach(() => {
  useVoidStore.setState({ armor: [] as never });
});

describe('HudArmorStatus with a held item', () => {
  it('draws the worn pieces when nothing is held', () => {
    useVoidStore.setState({ armor: WORN as never });
    const { container } = render(<HudArmorStatus />);
    expect(container.querySelectorAll('.v-armorlist__row')).toHaveLength(4);
  });

  it('draws the worn pieces AND the held block', () => {
    useVoidStore.setState({ armor: [...WORN, HELD_BLOCK] as never });
    const { container } = render(<HudArmorStatus />);
    expect(container.querySelectorAll('.v-armorlist__row')).toHaveLength(5);
  });

  it('draws the worn pieces when the held slot is empty', () => {
    useVoidStore.setState({ armor: [...WORN, HELD_EMPTY] as never });
    const { container } = render(<HudArmorStatus />);
    expect(container.querySelectorAll('.v-armorlist__row')).toHaveLength(4);
  });
});
