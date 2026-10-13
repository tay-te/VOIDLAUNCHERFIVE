/**
 * Coming soon — Figma v1 Ship `71:154` (Modpacks), `71:1503` (Servers), `71:2805` (Quests),
 * in motion: section `122:276` (frames `125:1629`, `125:2929`, `125:4230`, spec `133:37164`).
 *
 * Rule 1: the three are in the navbar, muted with SOON, and each opens this screen. The
 * art is the frames' own illustration, inlined so its layers can be animated; the timings
 * live in `local/soon.css`. *Notify me* records the wish locally and says so — there is no
 * announcement list to subscribe to yet.
 */

import { useEffect, useRef, useState, type CSSProperties } from 'react';

import modpacksArt from '../assets/soon/modpacks.svg?raw';
import questsArt from '../assets/soon/quests.svg?raw';
import serversArt from '../assets/soon/servers.svg?raw';
import type { SoonScreen } from '../stores/ui';

const COPY: Record<SoonScreen, { art: string; title: string; line: string }> = {
  modpacks: {
    art: modpacksArt,
    title: 'Your modpacks, one click away',
    line: 'Install, share and play modded packs with your friends — with every mod checked before you launch.',
  },
  servers: {
    art: serversArt,
    title: 'Host a server in a minute',
    line: 'Spin up a private server for your friends. It sleeps when nobody is on, so you only pay for play.',
  },
  quests: {
    art: questsArt,
    title: 'Play, earn, unlock',
    line: 'Daily and weekly PvP quests with rewards — verified from real matches, never from the client.',
  },
};

const NOTIFY_KEY = 'void.notify.v1';

function readNotify(): string[] {
  try {
    const raw = localStorage.getItem(NOTIFY_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

/** Eight burst cells, spread round the pill: 30–48px out, alternating. */
const BURST = Array.from({ length: 8 }, (_, k) => ({ a: `${k * 45 + 22}deg`, r: `${k % 2 ? 48 : 34}px` }));

/**
 * Notify me — the Figma component `125:602` (state × hue). The hue is the scene's, set on
 * `.soon`; hover lifts and rings the bell, press gives, a click widens the pill, draws the
 * check, bursts eight cells and sends one ripple. Reduced motion swaps states instantly.
 */
function NotifyMe({ which }: { which: SoonScreen }) {
  const [asked, setAsked] = useState(() => readNotify().includes(which));
  const [just, setJust] = useState(false);
  return (
    <button
      type="button"
      className="notify"
      data-state={asked ? 'done' : 'idle'}
      data-just={just ? '' : undefined}
      aria-disabled={asked}
      onClick={() => {
        if (asked) return;
        try {
          localStorage.setItem(NOTIFY_KEY, JSON.stringify([...new Set([...readNotify(), which])]));
        } catch {
          /* still say yes for this session */
        }
        setJust(true);
        setAsked(true);
      }}
    >
      <span className="notify__ripple" aria-hidden="true" />
      <span className="notify__burst" aria-hidden="true">
        {BURST.map((b) => (
          <i key={b.a} style={{ '--a': b.a, '--r': b.r } as CSSProperties} />
        ))}
      </span>
      <span className="notify__icon" aria-hidden="true">
        <svg className="notify__bell" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3.2 10.2h7.6M4 10.2V6.4a3 3 0 0 1 6 0v3.8M5.9 12.2h2.2" />
        </svg>
        <svg className="notify__check" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M2.8 7.4 5.7 10.2 11.2 4" pathLength={1} />
        </svg>
      </span>
      <span>{asked ? 'We’ll tell you here' : 'Notify me'}</span>
    </button>
  );
}

export function ComingSoonScreen({ which }: { which: SoonScreen }) {
  const copy = COPY[which];
  const root = useRef<HTMLDivElement>(null);

  // Every loop pauses when the view is hidden or the window loses focus (spec: Performance).
  useEffect(() => {
    const el = root.current;
    if (!el) return undefined;
    const sync = () => {
      if (document.hidden || !document.hasFocus()) el.setAttribute('data-paused', '');
      else el.removeAttribute('data-paused');
    };
    sync();
    document.addEventListener('visibilitychange', sync);
    window.addEventListener('blur', sync);
    window.addEventListener('focus', sync);
    return () => {
      document.removeEventListener('visibilitychange', sync);
      window.removeEventListener('blur', sync);
      window.removeEventListener('focus', sync);
    };
  }, []);

  return (
    <div className="soon" data-which={which} ref={root}>
      <div className="home__field" aria-hidden="true" />
      <section className="soon__stage">
        <div className="soon__glow" aria-hidden="true" />
        {/* The art is the generated, trusted SVG from tools/build-soon-art.py. */}
        <div aria-hidden="true" dangerouslySetInnerHTML={{ __html: copy.art }} />
        <div className="soon__copy">
          <p className="eyebrow2 soon__eyebrow">Coming soon</p>
          <h1 className="soon__title" aria-label={copy.title}>
            {copy.title.split(' ').map((w, i) => (
              <span key={`${w}-${i}`} className="w" style={{ '--i': i } as CSSProperties} aria-hidden="true">
                {w}
              </span>
            ))}
          </h1>
          <p className="soon__line">{copy.line}</p>
          <NotifyMe which={which} />
        </div>
      </section>
    </div>
  );
}
