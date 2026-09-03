/**
 * Play — `244:3`.
 *
 * Hero states which loadout is active; the eyebrow pill is a live readout, not a
 * decoration; the stats row is the three numbers the frame prints, each from a real
 * source where one exists:
 *
 *   `N mods on`      — counted from the active loadout against the registry defaults
 *   `fps avg`        — the loadout's accumulated `stats.fps_avg` (from `session`)
 *   `ms to Hypixel`  — a live `server_ping` SLP round trip
 *
 * The pill is `@void/ui`'s `StatusPill`; the hero type ramp is launcher-only — 104px
 * display over a recessed canvas is a thing only this bundle has.
 */

import { StatusPill } from '@void/ui';
import { useEffect } from 'react';

import { hypixelReady } from '../local/hypixelReady';
import { enabledCount } from '../local/registry';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useServers } from '../stores/servers';

/** The server the Play screen quotes a ping for: the active loadout's, else Hypixel. */
const FALLBACK_HOST = 'mc.hypixel.net';

export function PlayScreen() {
  const active = useLoadouts((s) => s.active);
  const pings = useServers((s) => s.pings);
  const ping = useServers((s) => s.ping);
  const servers = useServers((s) => s.servers);
  const liveServer = useLaunch((s) => s.server);

  const host =
    servers.find((s) => s.name.toLowerCase() === (active?.server ?? '').toLowerCase())?.host ??
    FALLBACK_HOST;
  const shortName = host.split('.').slice(-2, -1)[0] ?? host;
  const label = shortName.charAt(0).toUpperCase() + shortName.slice(1);

  // One ping on mount and every 30 s — often enough to be live, rare enough that a
  // launcher left open overnight is not hammering anyone's status port.
  useEffect(() => {
    void ping(host);
    const t = setInterval(() => void ping(host), 30_000);
    return () => clearInterval(t);
  }, [host, ping]);

  if (!active) {
    return (
      <div className="hero">
        <p className="hero__meta">Loading your library…</p>
      </div>
    );
  }

  const readiness = hypixelReady(active);
  const mods = enabledCount(active);
  const fpsAvg = active.stats?.fps_avg ?? 0;
  const fps = fpsAvg > 0 ? `${Math.round(fpsAvg)} fps avg` : 'no sessions yet';
  const pingState = pings[host];
  const pingText =
    pingState?.status === 'ok' && pingState.result
      ? `${pingState.result.latency_ms} ms to ${label}`
      : pingState?.status === 'error'
        ? `${label} unreachable`
        : `pinging ${label}…`;

  return (
    <div className="hero">
      <StatusPill tone={readiness.ready ? 'ok' : 'warn'}>
        VOID PVP &nbsp;·&nbsp; {active.mc} &nbsp;·&nbsp; {readiness.label}
      </StatusPill>

      <div className="hero__body">
        <p className="hero__kicker">ACTIVE LOADOUT</p>
        <h1 className="hero__title">{active.name}</h1>
        <p className="hero__meta">
          {mods} mods on &nbsp;·&nbsp; {fps} &nbsp;·&nbsp; {pingText}
        </p>
        {!readiness.ready ? <p className="hero__warn">{readiness.detail}</p> : null}
        {liveServer?.connected ? (
          <p className="hero__live">In game on {liveServer.host}</p>
        ) : null}
      </div>
    </div>
  );
}
