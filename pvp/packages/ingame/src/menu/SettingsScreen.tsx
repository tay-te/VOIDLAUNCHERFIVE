/**
 * The Settings page — what the gear in the bar opens.
 *
 * ## Why a gear, and why it replaced the profile chip
 *
 * The chip was the widest thing in a bar the user has called crowded three times, and what it
 * spent that width on was **identity the player already has**. In the launcher a chip earns the
 * slot because it is the way into Settings; in game the account is settled — you cannot switch
 * accounts mid-match — so the same object was carrying no decision. A gear carries one.
 *
 * The account did not disappear with it. It is the last card on this page, at the size a thing
 * you read once deserves, which is also where it stops competing with the controls.
 *
 * ## Why a page and not a popover
 *
 * Pages are the pattern here as of an hour ago: a mod's properties are a page, drawn in the same
 * panel box the grid uses (`ModsScreen` owns that box and hands it over unchanged). Escape
 * already means "up one level" and every page in the overlay is one level above the grid, so a
 * settings *page* costs no new idea — it inherits the back control, the `esc` cap's placement,
 * `keepsEscape()` and the exit animation, all of which already work.
 *
 * A popover would have had to invent every one of those, and would have had to invent them
 * *differently*: a dismiss that is not Escape-means-back, a light-dismiss region over a panel
 * that is already a light-dismiss region, and a stacking level above a bar that is the only
 * thing on the screen it could overlap. It also could not hold the keybind capture, which takes
 * the keyboard away from the page for as long as it is open.
 *
 * ## Every row does something
 *
 * That is the whole point of replacing a control that did not. `menu_key` needed a contract
 * change to be true at all — it lives in `LiveState` and Rust persists it, and until now the
 * page could neither read nor write it — so `bridge.json` gained the `settings` channel and the
 * `setGlobal` call, and this page is what asked for them.
 *
 * ```
 * Menu key           captures a key and rebinds the menu, live   setGlobal('menu_key')
 * VOID watermark     the `watermark` HUD mod's own switch        setModSetting / the loadout
 * Watermark settings that mod's page                             route
 * HUD layout         the layout editor                           route
 * Account            who is playing                              the `session` channel
 * ```
 *
 * Four rows, so §8's flat structure: no section labels, and no groups. It is a short page and
 * that is better than a padded one — `GlobalSettings` genuinely holds `menu_key`, a `theme`
 * nothing reads, and `ui_scale`, which is not here (see below).
 *
 * The watermark's *other* settings (style, scale, opacity) are deliberately **not** repeated
 * here: it is a mod, it has a mod page, and `Watermark settings ›` goes to it. A switch is the
 * one control worth a second home, because turning the mark off is the thing a player wants
 * without wanting to think about the mark at all.
 *
 * ## `ui_scale` is not on this page, and that is deliberate
 *
 * It was, for about an hour, as a meter writing `setGlobal('ui_scale', …)`. Two reasons it is
 * gone, and the second is the one that matters:
 *
 *   · `VoidClient.pumpUi` already fits the overlay to the window on every frame. A manual
 *     multiplier on top of automatic fitting mostly makes things worse, and the launcher is the
 *     right place for an override a player sets once.
 *   · **It is a control that resizes the surface it lives on.** Observed in game: the scale
 *     walked 1.05, 1.35, 1.6, 1.9 to the 3.0 clamp, 76 `in-game UI resized` lines in fourteen
 *     seconds, and the overlay was left at three times its size. Two clients were sharing the
 *     run directory at that moment, so that is not proof of a runaway — somebody may simply
 *     have been dragging it. The shape of the risk is real either way and is why the control is
 *     gone: the relayout moves the meter's cells under a stationary pointer, that arrives as a
 *     mouse move, and `MouseEvent.buttons` reads 0 for an entire drag in this engine
 *     (rendering-invariants §13), so a drag cannot be told apart from a hover by button state.
 *     A control that can feed its own input is not one to leave on the surface it resizes.
 *
 * The **plumbing is untouched** and must stay that way: `LiveState.uiScale` is clamped 0.5–3,
 * `setGlobal('ui_scale', …)` still stores it, and `pumpUi` still multiplies it into the view
 * scale every frame. The launcher writes it over the bridge and that path has to keep working.
 * Nothing in game writes it any more, which is the note `store.ts` carries where it is read.
 */

import { useState } from 'react';

import { KeybindChip, Toggle, cx } from '@/ui';
import { MOD_ORDER, modLabel } from '@/registry';
import { isModOn, modsOnCount, useVoidStore } from '@/store/store';
import { Watermark } from '@/hud/watermark';
import { keybindLabel } from './settings-format';

/** Bottom-left hint on the Settings page. */
export const SETTINGS_HINT =
  '‹ Mods returns to the grid   ·   changes apply immediately   ·   ⌘K search';

/** The mod that draws the mark over the game. Its switch has a second home here. */
const WATERMARK = 'watermark';

/**
 * One settings row. Deliberately the mod page's `.mprop`, not a new object: a label on the left
 * and its control on the right is the same row in both places, and giving Settings its own
 * would mean two rules to keep in step for one appearance.
 */
function Row({
  label,
  sub,
  children,
}: {
  label: string;
  sub?: string;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <div className="mprop">
      <span className="mprop__labels">
        <span className="mprop__label">{label}</span>
        {sub ? <span className="mprop__sub">{sub}</span> : null}
      </span>
      <span className="mprop__control">{children}</span>
    </div>
  );
}

export function SettingsScreen(): React.ReactElement {
  const globals = useVoidStore((s) => s.globals);
  const setGlobal = useVoidStore((s) => s.setGlobal);
  const captureMenuKey = useVoidStore((s) => s.captureMenuKey);
  const session = useVoidStore((s) => s.session);
  const setRoute = useVoidStore((s) => s.setRoute);
  const openMod = useVoidStore((s) => s.openMod);
  const toggleMod = useVoidStore((s) => s.toggleMod);
  // The watermark is an ordinary mod, so its switch reads the loadout like every other switch.
  // Guarded because a host whose registry predates it will not have the id.
  const markOn = useVoidStore((s) => isModOn(s.loadout, WATERMARK as never));
  const enabled = useVoidStore((s) => modsOnCount(s.loadout));

  // The arrival animation, dropped the moment it has run — same discipline as `ModPage` and
  // `.menu-layer`: an element carrying a standing animation is one the engine may keep on a
  // composited layer, and this page is full of text.
  const [entering, setEntering] = useState(true);

  const name = session?.name?.trim() ?? '';

  return (
    <div
      className={cx('oset', entering && 'oset--enter')}
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget && event.animationName === 'void-page-in') {
          setEntering(false);
        }
      }}
    >
      <div className="oset__head">
        <span className="oset__title">Settings</span>
        <span className="oset__meta">
          {`CLIENT   ·   ${keybindLabel(globals.menuKey).toUpperCase()} OPENS   ·   ${enabled} OF ${MOD_ORDER.length} MODS ON`}
        </span>
        <span className="oset__desc">
          The client itself, not a mod. Every change here applies immediately and is kept with
          your account.
        </span>
      </div>

      <div className="oset__body">
        {/* §8: two to four properties is a flat list with no section labels. It was two captioned
            groups while `UI scale` was here; one row under `Overlay` and three under `Heads-up
            display` is a group of one, which §8 says is not a group at all. */}
        <div className="oset__col">
          <div className="oset__group">
            <div className="oset__rows">
              <Row label="Menu key" sub="Opens and closes this menu.">
                <KeybindChip
                  value={keybindLabel(globals.menuKey)}
                  onCapture={captureMenuKey}
                  onChange={(key) => setGlobal('menu_key', key)}
                />
              </Row>
              {/* The mark is a mod, and this is that mod's switch — not a second flag that could
                  disagree with it. No hue on it: §4's toggle is monochrome in every state, and a
                  mark is not a live value anyway. */}
              <Row label="VOID watermark" sub="The mark, drawn over the game.">
                <Toggle
                  checked={markOn}
                  size="l"
                  label="VOID watermark enabled"
                  onChange={(next) => toggleMod(WATERMARK as never, next)}
                />
              </Row>
              {/* The switch is here because turning the mark off is a thing a player wants
                  without wanting to think about the mark; everything else about it belongs to
                  the mod, on the mod's own page, and this is the way there rather than a second
                  copy of those controls. */}
              <Row label="Watermark options" sub="Style, size, opacity and placement.">
                <button
                  type="button"
                  className="oset__link"
                  onClick={() => openMod(WATERMARK as never)}
                >
                  {`${modLabel(WATERMARK as never)} settings`}
                </button>
              </Row>
              {/* The editor has been reachable only from the quick palette, which means only by
                  someone who already knew it existed. */}
              <Row label="HUD layout" sub="Drag every chip where you want it.">
                <button
                  type="button"
                  className="oset__link"
                  onClick={() => setRoute({ name: 'hud-editor' })}
                >
                  Open the layout editor
                </button>
              </Row>
            </div>
          </div>
        </div>

        <div className="oset__col oset__col--side">
          {/* What is left of the chip. Read-only and says so by having no control on it: the
              account is settled for the life of the process — you cannot sign in from inside a
              match — so a button here would be the same lie the chip was. */}
          <div className="oset__card">
            <div className="oset__cap">Account</div>
            <div className="oset__player">
              {/* The initial, not a skin: the page has no network at all (`check-ultralight.mjs`
                  rejects fetch, XHR and WebSocket), so a real avatar would have to arrive as
                  pixels over the bridge. Monochrome per §1 — an avatar is neither a live value
                  nor a selection, and a hue keyed to the uuid is exactly the accent leak the
                  palette was audited for. */}
              <span className="oset__initial" aria-hidden="true">
                {name === '' ? '?' : name.charAt(0).toUpperCase()}
              </span>
              <span className="oset__ident">
                <span className="oset__name">{name === '' ? 'Not signed in' : name}</span>
                <span className="oset__kind">
                  {session === null
                    ? 'No session'
                    : session.kind === 'microsoft'
                      ? 'Microsoft account'
                      : 'Offline account'}
                </span>
              </span>
            </div>
          </div>

          {/* Not decoration, and not a second copy of the mod page's preview either: it is the
              answer to "what am I turning on", drawn beside the switch that turns it on. The
              real widget at its real settings — style, scale and opacity all come out of the
              loadout — so what is in this card is what will be over the game. */}
          <div className={cx('oset__card', 'oset__mark', !markOn && 'oset__mark--off')}>
            <div className="oset__cap">
              <span>The mark</span>
              <span>{markOn ? 'On screen' : 'Hidden'}</span>
            </div>
            <div className="oset__markwell">
              {/* Bigger than the HUD's 3px cell, and the word scales with it in CSS rather than
                  under a `transform` — §10, and the reason nothing in this overlay is scaled.
                  A preview is allowed to be larger than the thing it previews; what it may not
                  be is a *different* thing, and this is the same component reading the same
                  `style` setting. */}
              <Watermark cell={5} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
