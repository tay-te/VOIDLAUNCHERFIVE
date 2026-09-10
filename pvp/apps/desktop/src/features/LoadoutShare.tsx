/**
 * Share and import a loadout by code — `docs/mod-roster.md` §5's last row.
 *
 * ## Why this is in the launcher and not in game
 *
 * The roster's own note says "pure product work, no game code", and reading the two surfaces
 * says the same thing more precisely. A share code has to be *copied out* and *pasted in*, and
 * in game neither is available: `bridge.json` is a closed surface of five events and eight calls
 * with no clipboard among them, and Ultralight receives no keyboard input while the HUD is up.
 * Building it there means a new bridge call, a paste path through our own synthetic input, and a
 * text field in an engine that has no native one — to reach a code that arrives over Discord,
 * which is where the launcher already is.
 *
 * ## What the code carries
 *
 * Everything: which mods are on, every setting each carries — including the shared HUD chrome
 * block, which is the half a player tuned by eye and the half a naive codec forgets — and where
 * every HUD item sits. `@void/protocol`'s `share.ts` states the guarantee and its test walks the
 * registry to enforce it.
 *
 * ## Copying, and the fallback that is not optional
 *
 * `navigator.clipboard` needs a secure context, and the launcher's webview is not one everywhere
 * — Tauri's custom protocol is treated as secure on some platforms and not on others, and this
 * is the one action in the app whose whole value is that it puts text on the clipboard. So the
 * textarea-and-`execCommand` path is a real fallback rather than a legacy branch, and the button
 * reports what actually happened rather than assuming.
 */

import { useState } from 'react';

import { looksLikeShareCode } from '@void/protocol';

import { useLoadouts } from '../stores/loadouts';

/** How long the "Copied" / result line stays up before the panel goes quiet again. */
const NOTICE_MS = 4000;

/**
 * Put text on the clipboard, by whichever route this webview actually has.
 *
 * Returns whether it worked, because a silent failure here is a player pasting the last thing
 * they copied into a friend's chat and wondering why the loadout is wrong.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // Falls through: a rejected promise here is a permissions or secure-context refusal, and
    // the older path does not go through either.
  }
  try {
    const field = document.createElement('textarea');
    field.value = text;
    // Off-screen rather than hidden: `execCommand('copy')` needs a selectable, rendered node,
    // and `display: none` makes the selection empty and the copy a no-op that returns true.
    field.style.position = 'fixed';
    field.style.top = '-1000px';
    field.setAttribute('readonly', 'readonly');
    document.body.appendChild(field);
    field.select();
    const done = document.execCommand('copy');
    document.body.removeChild(field);
    return done;
  } catch {
    return false;
  }
}

export function LoadoutShare() {
  const active = useLoadouts((s) => s.active);
  const shareCode = useLoadouts((s) => s.shareCode);
  const importCode = useLoadouts((s) => s.importCode);

  const [open, setOpen] = useState(false);
  const [pasted, setPasted] = useState('');
  const [notice, setNotice] = useState<{ tone: 'ok' | 'bad'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  function say(tone: 'ok' | 'bad', text: string) {
    setNotice({ tone, text });
    window.setTimeout(() => setNotice(null), NOTICE_MS);
  }

  async function onCopy() {
    const code = shareCode();
    if (code === null) {
      say('bad', 'No loadout to share yet.');
      return;
    }
    if (await copyText(code)) {
      say('ok', `Copied ${active?.name ?? 'loadout'} — ${code.length} characters.`);
    } else {
      // Not a shrug: the code goes into the field below either way, so there is always a way to
      // get it out by hand.
      say('bad', 'Could not reach the clipboard — select the code below instead.');
    }
    setPasted(code);
    setOpen(true);
  }

  async function onImport() {
    setBusy(true);
    const result = await importCode(pasted);
    setBusy(false);
    if (!result.ok) {
      say('bad', result.message);
      return;
    }
    setPasted('');
    // Three different successes, and they are not the same news. A clean import needs one line;
    // a foreign one is still correct but its defaults may not match; a lossy one has genuinely
    // arrived with less in it than the sender had, and saying so is the difference between a
    // share feature and a share feature people trust.
    if (result.dropped.length > 0) {
      say(
        'bad',
        `Imported ${result.name}, without ${result.dropped.length} setting${
          result.dropped.length === 1 ? '' : 's'
        } this version does not have: ${result.dropped.slice(0, 3).join(', ')}.`,
      );
    } else if (result.foreign) {
      say('ok', `Imported ${result.name} from a different VOID version — check it over.`);
    } else {
      say('ok', `Imported ${result.name}.`);
    }
  }

  return (
    <section className="share" aria-label="Share loadouts">
      <div className="share__row">
        <button type="button" className="share__btn" onClick={() => void onCopy()}>
          Copy code
        </button>
        <button
          type="button"
          className={`share__btn${open ? ' is-on' : ''}`}
          aria-expanded={open}
          onClick={() => setOpen((was) => !was)}
        >
          Import
        </button>
      </div>

      {open ? (
        <>
          <textarea
            className="share__field"
            value={pasted}
            spellCheck={false}
            placeholder="Paste a VOID share code"
            aria-label="Share code"
            onChange={(event) => setPasted(event.target.value)}
          />
          <button
            type="button"
            className="share__btn share__btn--wide"
            // Disabled on anything that is not a code at all, so the common mistake — pasting a
            // Discord message with the code inside it — is caught before it becomes an error
            // message. A code that *looks* right and is corrupt still has to be tried, and the
            // decoder is what says so.
            disabled={busy || !looksLikeShareCode(pasted)}
            onClick={() => void onImport()}
          >
            {busy ? 'Importing…' : 'Import as a new loadout'}
          </button>
        </>
      ) : null}

      {notice === null ? null : (
        <p className={`share__notice share__notice--${notice.tone}`} role="status">
          {notice.text}
        </p>
      )}
    </section>
  );
}
