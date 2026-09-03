/**
 * The dropdown the dock's two pills open.
 *
 * The design's pills carry a chevron-down, so they are menus — but the menu *surface*
 * is not in the component inventory (the in-game Party pane's picker opens the
 * Loadouts panel instead of a list, which is why `@void/ui` ships the pill and not a
 * popover). So the pill is `LoadoutPicker` / `VersionPicker` from the package and only
 * the list below it is local.
 */

import { Icon } from '@void/ui';
import { useEffect, useRef, useState, type ReactElement } from 'react';

/** One row of the dropdown. */
export interface MenuItem {
  /** Stable id handed back to `onSelect`. */
  id: string;
  /** The visible label. */
  label: string;
  /** Greyed out and unselectable. */
  disabled?: boolean;
  /** A trailing mono note, e.g. `not yet`. */
  hint?: string;
}

/** Props for {@link Menu}. */
export interface MenuProps {
  /** The pill, given the `open` flag so it can take its accent border. */
  trigger: (open: boolean, toggle: () => void) => ReactElement;
  /** The rows. */
  items: readonly MenuItem[];
  /** Label of the row that is current — it gets a check. */
  current?: string;
  /** Called with the picked row's id. */
  onSelect: (id: string) => void;
}

export function Menu({ trigger, items, current, onSelect }: MenuProps): ReactElement {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (event: MouseEvent): void => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('mousedown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="menu" ref={ref}>
      {trigger(open, () => setOpen((o) => !o))}
      {open ? (
        <ul className="menu__list" role="listbox">
          {items.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                role="option"
                aria-selected={item.label === current}
                disabled={item.disabled}
                className={`menu__item${item.label === current ? ' is-current' : ''}`}
                onClick={() => {
                  onSelect(item.id);
                  setOpen(false);
                }}
              >
                <span>{item.label}</span>
                {item.hint ? <span className="menu__hint">{item.hint}</span> : null}
                {item.label === current ? <Icon name="check" size={13} /> : null}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
