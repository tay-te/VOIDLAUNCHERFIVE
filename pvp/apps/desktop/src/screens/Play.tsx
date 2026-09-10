/**
 * Play — the shell's content panel with the active loadout in it.
 *
 * The frame (`design/screens/launcher/Launcher-Play.png`, 1600 × 980) is a *stage*, and
 * its composition is the point of the screen. Four things, and nothing else:
 *
 *   1. the dot-matrix field over the whole stage (`local/watermark`), which is what
 *      keeps 1536 × 768 of `--bg-base` from reading as an empty box;
 *   2. a 300 × 30 status chip in the top-left corner at (64, 112);
 *   3. the loadout block hard against the bottom-left — `ACTIVE LOADOUT`, the name at
 *      **88px**, and one line carrying the three numbers;
 *   4. the floating dock card, which belongs to the shell and lives in `features/Dock`.
 *
 * That 88 is the whole screen. The previous pass drew it at 40 — the contract's first
 * draft aliased `--text-hero` onto `--text-display-lg` — and the port read as cramped
 * because of it.
 *
 * Two things an earlier pass carried are gone, because neither is in the frame and
 * both sat in the middle of the stage's air: the three stat cards (MODS ON / AVERAGE
 * FPS / PING) and the "IN THIS LOADOUT" chip row. Nothing was lost with them — the
 * numbers are the meta line now, and the mods are the Mods screen, which the ⌘K palette
 * also reaches by name.
 *
 * The numbers are real where a source exists:
 *
 *   `N mods on`      — counted from the active loadout against the registry defaults
 *   `fps avg`        — the loadout's accumulated `stats.fps_avg` (from `session`)
 *   `ms to Hypixel`  — a live `server_ping` SLP round trip
 *
 * Colour follows the accent rule (§1): the readiness dot is `--ok` only when the
 * loadout *is* ready. Nothing else on the screen is tinted.
 */

import { useEffect, type CSSProperties } from 'react';

import { hypixelReady } from '../local/hypixelReady';
import { enabledCount } from '../local/registry';
import {
  WM_ALPHA,
  WM_CELL,
  WM_MARK_CELL,
  WM_MARK_GAIN,
  WM_MARK_ROWS,
  WM_ORIGIN,
  WM_ROWS,
  WM_STEP,
} from '../local/watermark';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import {
  lastPlayed,
  nameForHost,
  playedTime,
  resumeTarget,
  serverName,
  useServers,
} from '../stores/servers';

/** The server the Play screen quotes a ping for: the active loadout's, else Hypixel. */
const FALLBACK_HOST = 'mc.hypixel.net';

/**
 * The stage's field, built once at module scope.
 *
 * Two layers: the 536-cell scatter at 10px, and the 37-cell VOID ring at 28px over it.
 * That is more DOM than a background would be, but the alpha ramps per column and both
 * patterns are sparse, so neither a repeating gradient nor a tiled image can draw them —
 * a gradient cannot skip cells and a tile cannot ramp. They are inert `<i>` elements
 * under `pointer-events: none`, built once at module scope and never re-rendered, so a
 * ping landing does not touch them.
 */
/** The alpha a cell in column `x` is drawn at — the field ramps left to right. */
const alphaAt = (x: number, columns: number): number =>
  WM_ALPHA.from + (WM_ALPHA.to - WM_ALPHA.from) * (x / (columns - 1));

function layer(rows: readonly string[], size: number, gain: number, radius: number) {
  return rows.flatMap((row, y) =>
    [...row].flatMap((bit, x) =>
      bit === '1'
        ? [
            {
              key: `${size}:${x}:${y}`,
              style: {
                left: WM_ORIGIN.x + x * WM_STEP - (size - WM_CELL) / 2,
                top: WM_ORIGIN.y + y * WM_STEP - (size - WM_CELL) / 2,
                width: size,
                height: size,
                borderRadius: radius,
                opacity: alphaAt(x, row.length) * gain,
              } as CSSProperties,
            },
          ]
        : [],
    ),
  );
}

const FIELD = [
  ...layer(WM_ROWS, WM_CELL, 1, 3),
  ...layer(WM_MARK_ROWS, WM_MARK_CELL, WM_MARK_GAIN, 8),
];

function StageField() {
  return (
    <div className="play__field" aria-hidden="true">
      {FIELD.map((cell) => (
        <i key={cell.key} className="play__cell" style={cell.style} />
      ))}
    </div>
  );
}

export function PlayScreen() {
  const active = useLoadouts((s) => s.active);
  const pings = useServers((s) => s.pings);
  const ping = useServers((s) => s.ping);
  const servers = useServers((s) => s.servers);
  const liveServer = useLaunch((s) => s.server);

  // Where you last actually were, then the loadout's advisory slug, then a constant.
  //
  // The slug match used to be first, and it was a name comparison standing in for a record:
  // `serverName(entry)` against `loadout.server`, which agrees for `hypixel` by coincidence and
  // picks whichever of `na.` and `eu.minemen.club` happens to sort first for `minemen`. Now that
  // the launcher keeps a record of where the player has been, the record answers — and the slug
  // stays as the fallback for a first run, which is the only case it was ever right about.
  const resume = resumeTarget(servers);
  const host =
    resume?.host ??
    servers.find((s) => serverName(s).toLowerCase() === (active?.server ?? '').toLowerCase())
      ?.host ??
    FALLBACK_HOST;
  const label = resume ? serverName(resume) : nameForHost(host);

  // One ping on mount and every 30 s — often enough to be live, rare enough that a
  // launcher left open overnight is not hammering anyone's status port.
  useEffect(() => {
    void ping(host);
    const t = setInterval(() => void ping(host), 30_000);
    return () => clearInterval(t);
  }, [host, ping]);

  if (!active) {
    return (
      <div className="play">
        <StageField />
        <p className="play__detail">Loading your library…</p>
      </div>
    );
  }

  const readiness = hypixelReady(active);
  const mods = enabledCount(active);
  const fpsAvg = active.stats?.fps_avg ?? 0;
  const pingState = pings[host];
  const pingValue =
    pingState?.status === 'ok' && pingState.result ? `${pingState.result.latency_ms}` : null;

  return (
    <div className="play">
      <StageField />

      {/* The status chip. Its dot is the one accent on the stage, and only when ready. */}
      <p className="play__state eyebrow">
        <span className={`dot cell${readiness.ready ? ' is-ok' : ''}`} aria-hidden="true" />
        VOID PVP · {active.mc} · {readiness.ready ? 'Hypixel-ready' : 'Review mods'}
      </p>

      <header className="play__head">
        <p className="eyebrow">Active loadout</p>
        <h1 className="hero">{active.name}</h1>
        <p className="play__meta">
          <span className="tnum">{mods} mods on</span>
          <span className="play__dash">·</span>
          <span className="tnum">{fpsAvg > 0 ? `${Math.round(fpsAvg)} fps avg` : 'no sessions yet'}</span>
          <span className="play__dash">·</span>
          <span className="tnum">
            {pingValue ? `${pingValue} ms to ${label}` : `pinging ${label}`}
          </span>
          {/* What the game reported, not what the launcher assumed. Only when there is one —
              a fresh install has played nowhere, and `0m` there would be a figure nobody
              earned (`playedTime` draws the same distinction). */}
          {resume && playedTime(resume.played_ms) !== '—' ? (
            <>
              <span className="play__dash">·</span>
              <span className="tnum">
                {playedTime(resume.played_ms)} there, {lastPlayed(resume.last_played_ms)}
              </span>
            </>
          ) : null}
          {liveServer?.connected ? (
            <>
              <span className="play__dash">·</span>
              <span className="play__live">in game on {liveServer.host}</span>
            </>
          ) : null}
        </p>
      </header>
    </div>
  );
}
