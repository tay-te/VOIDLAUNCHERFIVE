/**
 * Store tests, run against the same mock backend the browser preview uses.
 *
 * That is the point of the alias in `vitest.config.ts`: a green test here means
 * `pnpm dev:web` behaves the same way, and the mock stays honest about the command
 * names and payload shapes in `src-tauri/src/ipc.rs`.
 */

import { beforeEach, describe, expect, it } from 'vitest';

import { __resetMock, __setSpeed, __signIn, emit } from '../mocks/tauri';
import { listen } from '../local/tauri';
import { hypixelReady } from '../local/hypixelReady';
import { effectiveState, enabledCount, isOn, matchesTab } from '../local/registry';
import { useLaunch, wireLaunchEvents, formatBytes, stepLabel } from './launch';
import { useLoadouts, wireLoadoutEvents } from './loadouts';
import { useServers, lastPlayed, nameForHost, playedTime, pingTone } from './servers';
import { useSession, wireSessionEvents } from './session';
import { useUi } from './ui';

/** Wait until `predicate` holds, or fail the test rather than hang it. */
async function until(predicate: () => boolean, label: string, ms = 2000): Promise<void> {
  const deadline = Date.now() + ms;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${label}`);
    await new Promise((r) => setTimeout(r, 5));
  }
}

beforeEach(() => {
  __resetMock();
  __setSpeed(0); // no artificial delays; every scheduled callback runs inline
  useSession.setState({ account: null, system: null, java: null, deviceCode: null, error: null });
  useLoadouts.setState({ library: [], active: null, settings: null, error: null });
  useLaunch.setState({ phase: 'idle', progress: null, log: [], error: null, lastSession: null, server: null });
  useUi.setState({ screen: 'play', paletteOpen: false, settingsOpen: false, logOpen: false });
});

describe('session store', () => {
  it('hydrates the account, the machine and the Java status in one pass', async () => {
    await useSession.getState().hydrate();
    const s = useSession.getState();
    expect(s.loading).toBe(false);
    expect(s.account).toBeNull(); // nobody signed in yet
    expect(s.system?.ram_total_mb).toBeGreaterThan(0);
    expect(s.java?.found).toBe(true);
  });

  it('signs in offline and out again', async () => {
    await useSession.getState().loginOffline('Searge');
    expect(useSession.getState().account?.name).toBe('Searge');
    expect(useSession.getState().account?.kind).toBe('offline');

    await useSession.getState().logout();
    expect(useSession.getState().account).toBeNull();
  });

  it('surfaces the backend’s own sentence when a name is rejected', async () => {
    await useSession.getState().loginOffline('not a name');
    expect(useSession.getState().error).toMatch(/1–16 characters/);
    expect(useSession.getState().account).toBeNull();
  });

  it('takes the account from the auth:status event, not from the login return', async () => {
    // This one keeps the mock's timings (scaled down): the point of the device flow is
    // that the code is shown *while* the exchange runs, which needs the two to be
    // ordered in time rather than collapsed into one tick.
    __setSpeed(0.01);
    const stop = await wireSessionEvents();
    await useSession.getState().loginMicrosoft();
    // The command returns a device code; the account only arrives on `complete`.
    expect(useSession.getState().deviceCode?.user_code).toBeTruthy();
    await until(() => useSession.getState().account !== null, 'the account to arrive');
    expect(useSession.getState().account?.kind).toBe('microsoft');
    expect(useSession.getState().deviceCode).toBeNull();
    stop();
  });
});

describe('joining a server directly', () => {
  /**
   * `--server` / `--port` are 1.8.9's own launch arguments — `net.minecraft.client.main.Main`
   * parses them next to `username` and `accessToken` — so joining a server is a launcher
   * capability that needs nothing from the mod. These assert the launcher half: the host
   * reaches the command, and an address that would be read as a flag never does.
   */
  it('sends the game to the server it was asked for', async () => {
    await useSession.getState().loginOffline('Searge');
    await useLoadouts.getState().hydrate();
    const id = useLoadouts.getState().active!.id;

    // Captured from the event rather than read off the store at the end. At `__setSpeed(0)` the
    // mock runs its whole session inline — connect, play, close — so by the time `start` returns
    // the game has already exited and `server` is back to null. The event is the reading; the
    // store field is a live value with a lifetime.
    const seen: string[] = [];
    const stop = await listen('bridge:server', (msg) => seen.push(msg.host));
    wireLaunchEvents();
    await useLaunch.getState().start(id, { host: 'na.minemen.club' });
    stop();
    // Reported back through `bridge:server`, which is the only evidence the launcher ever has
    // that the argument landed: the client does not acknowledge it, it just connects.
    expect(seen).toContain('na.minemen.club');
  });

  it('refuses an address that would be read as a flag, and does not launch', async () => {
    await useSession.getState().loginOffline('Searge');
    await useLoadouts.getState().hydrate();
    const id = useLoadouts.getState().active!.id;

    // The realistic version of this is not an attack, it is a paste: a player copies a line out
    // of a forum post and it starts with a dash. `--server -Xmx1G` makes the client's own parser
    // read the next argument as the host, and the launch either fails oddly or joins nothing.
    await useLaunch.getState().start(id, { host: '-Xmx1G' });
    expect(useLaunch.getState().phase).toBe('idle');
    expect(useLaunch.getState().error).toContain('not a server address');
  });
});

describe('loadout share codes, end to end through the mock backend', () => {
  /**
   * The codec's own tests prove the string round-trips. These prove the *launcher* does: that
   * `loadouts_create` + `loadouts_update` reassemble the loadout the code described, through the
   * same merge Rust performs. The codec emits a delta on the assumption that the receiver merges
   * it over the registry defaults, and this is the only place that assumption is actually tested
   * against the thing that does the merging.
   */
  it('exports the active loadout and imports it back as a separate one', async () => {
    await useLoadouts.getState().hydrate();
    const original = useLoadouts.getState().active!;
    expect(original).not.toBeNull();

    // Something tuned by eye, in the block a naive codec forgets.
    await useLoadouts.getState().setMod('fps', {
      on: true,
      scale: 1.75,
      opacity: 0.6,
      background: 'solid',
      border: true,
      padding: 'wide',
    });
    const tuned = useLoadouts.getState().active!;

    const code = useLoadouts.getState().shareCode()!;
    expect(code.startsWith('VOID1.')).toBe(true);

    const outcome = await useLoadouts.getState().importCode(code);
    expect(outcome.ok).toBe(true);
    if (!outcome.ok) return;
    expect(outcome.dropped).toEqual([]);
    expect(outcome.foreign).toBe(false);

    const imported = useLoadouts.getState().active!;
    // A separate loadout, not an overwrite: the sender's id never travels, so importing your
    // own code twice cannot eat the original.
    expect(imported.id).not.toBe(tuned.id);
    expect(effectiveState(imported, 'fps')).toMatchObject({
      on: true,
      scale: 1.75,
      opacity: 0.6,
      background: 'solid',
      border: true,
      padding: 'wide',
    });
  });

  it('names the copy rather than colliding with the loadout it came from', async () => {
    await useLoadouts.getState().hydrate();
    const before = useLoadouts.getState().active!;
    const outcome = await useLoadouts.getState().importCode(useLoadouts.getState().shareCode()!);
    expect(outcome.ok).toBe(true);
    if (!outcome.ok) return;
    expect(outcome.name).toBe(`${before.name} (2)`);
    expect(useLoadouts.getState().library.some((entry) => entry.name === before.name)).toBe(true);
  });

  it('refuses a bad code in the words the fix needs, and changes nothing', async () => {
    await useLoadouts.getState().hydrate();
    const count = useLoadouts.getState().library.length;

    // Three different mistakes with three different fixes: they pasted the wrong thing, the
    // chat client ate the tail, or there is nothing there at all. One message for all three
    // would send a player back to the sender for a problem they could fix themselves.
    expect(await useLoadouts.getState().importCode('')).toMatchObject({ ok: false });
    expect(await useLoadouts.getState().importCode('here is my loadout lol')).toMatchObject({
      ok: false,
      message: expect.stringContaining('does not look like'),
    });
    const truncated = useLoadouts.getState().shareCode()!.slice(0, -10);
    expect(await useLoadouts.getState().importCode(truncated)).toMatchObject({
      ok: false,
      message: expect.stringContaining('incomplete'),
    });

    expect(useLoadouts.getState().library).toHaveLength(count);
  });
});

describe('loadout store', () => {
  it('hydrates the library, the active loadout and the settings together', async () => {
    await useLoadouts.getState().hydrate();
    const s = useLoadouts.getState();
    expect(s.library.length).toBeGreaterThan(1);
    expect(s.active?.id).toBe(s.settings?.active_loadout);
  });

  it('switching moves both the active loadout and the settings pointer', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().switchTo('bedwars');
    const s = useLoadouts.getState();
    expect(s.active?.id).toBe('bedwars');
    expect(s.settings?.active_loadout).toBe('bedwars');
  });

  it('a mod toggle writes through and takes the returned loadout as truth', async () => {
    await useLoadouts.getState().hydrate();
    expect(isOn(useLoadouts.getState().active!, 'fullbright')).toBe(false);

    await useLoadouts.getState().setMod('fullbright', { on: true });
    expect(isOn(useLoadouts.getState().active!, 'fullbright')).toBe(true);
  });

  it('a partial setting patch keeps the rest of that mod’s settings', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().setMod('zoom', { fov_divisor: 6 });
    const zoom = effectiveState(useLoadouts.getState().active!, 'zoom');
    expect(zoom.fov_divisor).toBe(6);
    expect(zoom.key).toBeDefined(); // not clobbered
    expect(zoom.on).toBe(true);
  });

  it('applies a bridge:state patch from a running game', async () => {
    await useLoadouts.getState().hydrate();
    const id = useLoadouts.getState().active!.id;
    useLoadouts.getState().applyStatePatch(id, { 'mods.fullbright.on': true });
    expect(isOn(useLoadouts.getState().active!, 'fullbright')).toBe(true);
  });

  it('ignores a bridge:state patch aimed at a different loadout', async () => {
    await useLoadouts.getState().hydrate();
    const before = useLoadouts.getState().active;
    useLoadouts.getState().applyStatePatch('some-other-loadout', { 'mods.fullbright.on': true });
    expect(useLoadouts.getState().active).toBe(before);
  });

  it('follows a loadout:switched event from the tray', async () => {
    await useLoadouts.getState().hydrate();
    const stop = await wireLoadoutEvents();
    await useLoadouts.getState().switchTo('bedwars');
    await until(() => useLoadouts.getState().active?.id === 'bedwars', 'the switch');
    expect(useLoadouts.getState().settings?.active_loadout).toBe('bedwars');
    stop();
  });

  it('reports the backend’s error rather than throwing', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().switchTo('does-not-exist');
    expect(useLoadouts.getState().error).toMatch(/No loadout/);
  });
});

describe('launch store', () => {
  it('walks idle → preparing → launching → running → idle', async () => {
    __signIn();
    const stop = await wireLaunchEvents();
    await useLoadouts.getState().hydrate();

    const seen: string[] = [];
    const unsub = useLaunch.subscribe((s) => {
      if (seen[seen.length - 1] !== s.phase) seen.push(s.phase);
    });

    await useLaunch.getState().start('sword-pvp');
    await until(() => useLaunch.getState().lastSession !== null, 'the session to end');

    expect(seen).toContain('preparing');
    expect(seen).toContain('launching');
    expect(seen).toContain('running');
    expect(useLaunch.getState().phase).toBe('idle');
    expect(useLaunch.getState().lastSession?.code).toBe(0);
    expect(useLaunch.getState().log.length).toBeGreaterThan(0);

    unsub();
    stop();
  });

  it('refuses to launch without an account, and says so', async () => {
    const stop = await wireLaunchEvents();
    await useLoadouts.getState().hydrate();
    await useLaunch.getState().start('sword-pvp');
    expect(useLaunch.getState().phase).toBe('idle');
    expect(useLaunch.getState().error).toMatch(/Not signed in/);
    stop();
  });

  it('a second start while busy is ignored rather than queued', async () => {
    __signIn();
    useLaunch.setState({ phase: 'running' });
    await useLaunch.getState().start('sword-pvp');
    expect(useLaunch.getState().phase).toBe('running');
  });

  it('caps the log ring and keeps the newest lines', async () => {
    const stop = await wireLaunchEvents();
    for (let i = 0; i < 2100; i += 1) {
      emit('game:log', { stream: 'stdout', line: `line ${i}`, ts_ms: 0 });
    }
    const log = useLaunch.getState().log;
    expect(log.length).toBe(2000);
    expect(log[log.length - 1]?.line).toBe('line 2099');
    stop();
  });

  it('surfaces a non-zero exit as an error the drawer can act on', async () => {
    const stop = await wireLaunchEvents();
    emit('game:closed', { code: 1, loadout: 'sword-pvp', played_ms: 10, fps_avg: 0 });
    expect(useLaunch.getState().error).toMatch(/exited with code 1/);
    stop();
  });

  it('tracks server presence from the bridge', async () => {
    const stop = await wireLaunchEvents();
    emit('bridge:server', { t: 'server', host: 'mc.hypixel.net', connected: true });
    expect(useLaunch.getState().server).toEqual({ host: 'mc.hypixel.net', connected: true });
    stop();
  });

  it('names every prepare step and humanises bytes', () => {
    for (const step of ['manifest', 'libraries', 'assets', 'java', 'mod', 'done'] as const) {
      expect(stepLabel(step)).toBeTruthy();
    }
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(25 * 1024 * 1024)).toBe('25.0 MB');
  });
});

describe('servers store', () => {
  it('starts empty and takes the book from Rust', async () => {
    // It used to read `localStorage` at module scope, which is how a desktop app ends up with
    // its own data in a webview's cache — `docs/launcher-roster.md` §2.
    expect(useServers.getState().servers).toHaveLength(0);
    await useServers.getState().hydrate();
    expect(useServers.getState().servers.length).toBeGreaterThan(0);
    expect(useServers.getState().selected).not.toBeNull();
  });

  it('does not move the player off the row they are reading when it re-hydrates', async () => {
    // Hydrate runs again after every session, and after a session the server just played has
    // jumped to the top of the list. Following the list there would scroll the player off
    // whatever they had open.
    await useServers.getState().hydrate();
    useServers.getState().select('pvp.land');
    await useServers.getState().hydrate();
    expect(useServers.getState().selected).toBe('pvp.land');
  });

  it('forgets a server outright, playtime included', async () => {
    // "Forget" that kept the hours would be a button that hides a row and leaves the data,
    // which is the shape of every privacy control nobody trusts.
    await useServers.getState().hydrate();
    await useServers.getState().remove('pvp.land');
    expect(useServers.getState().servers.some((s) => s.host === 'pvp.land')).toBe(false);
    await useServers.getState().hydrate();
    expect(useServers.getState().servers.some((s) => s.host === 'pvp.land')).toBe(false);
  });

  it('says nothing rather than zero for a server nobody has played', () => {
    // Two different states with one obvious wrong answer between them: a starred server never
    // joined has no playtime, and a server joined and quit has almost none. `0m` would say the
    // first about the second, and both about neither.
    expect(playedTime(0)).toBe('—');
    expect(playedTime(30_000)).toBe('—');
    expect(playedTime(41 * 60_000)).toBe('41m');
    expect(playedTime((2 * 60 + 41) * 60_000)).toBe('2h 41m');

    const now = Date.UTC(2026, 8, 10, 12, 0, 0);
    expect(lastPlayed(0, now)).toBe('—');
    expect(lastPlayed(now - 3600_000, now)).toBe('today');
    expect(lastPlayed(now - 86_400_000, now)).toBe('yesterday');
    expect(lastPlayed(now - 5 * 86_400_000, now)).toBe('5d');
    expect(lastPlayed(now - 21 * 86_400_000, now)).toBe('3w');
  });

  it('pings a known host and keeps a bounded history', async () => {
    for (let i = 0; i < 15; i += 1) {
      await useServers.getState().ping('mc.hypixel.net');
    }
    const state = useServers.getState().pings['mc.hypixel.net'];
    expect(state?.status).toBe('ok');
    expect(state?.history.length).toBe(12);
    expect(state?.result?.online).toBeGreaterThan(0);
  });

  it('records a failed ping without losing the history', async () => {
    await useServers.getState().ping('mc.hypixel.net');
    await useServers.getState().ping('unreachable.invalid');
    const bad = useServers.getState().pings['unreachable.invalid'];
    expect(bad?.status).toBe('error');
    expect(bad?.error).toMatch(/Could not reach/);
    expect(useServers.getState().pings['mc.hypixel.net']?.status).toBe('ok');
  });

  it('adds and removes favourites, and derives a display name', async () => {
    // The list is Rust's now, so the store has to be hydrated before it has one — and every
    // mutation is a round trip whose answer is the truth, because the book is trimmed and
    // re-sorted on write.
    await useServers.getState().hydrate();
    const before = useServers.getState().servers.length;
    await useServers.getState().add('play.example.net');
    expect(useServers.getState().servers.length).toBe(before + 1);
    expect(useServers.getState().selected).toBe('play.example.net');

    await useServers.getState().remove('play.example.net');
    expect(useServers.getState().servers.length).toBe(before);

    expect(nameForHost('mc.hypixel.net')).toBe('Hypixel');
    expect(nameForHost('pvp.land')).toBe('Pvp');
  });

  it('adding a host that is already listed selects it rather than duplicating it', () => {
    const before = useServers.getState().servers.length;
    useServers.getState().add('mc.hypixel.net');
    expect(useServers.getState().servers.length).toBe(before);
    expect(useServers.getState().selected).toBe('mc.hypixel.net');
  });

  it('classifies latency the way the ping chips colour it', () => {
    expect(pingTone(42)).toBe('ok');
    expect(pingTone(112)).toBe('warn');
    expect(pingTone(400)).toBe('bad');
  });
});

describe('hypixel readiness (§11)', () => {
  it('is ready when every enabled mod is safe', async () => {
    await useLoadouts.getState().hydrate();
    const readiness = hypixelReady(useLoadouts.getState().active!);
    expect(readiness.ready).toBe(true);
    expect(readiness.label).toBe('HYPIXEL-READY');
    expect(readiness.greyMods).toEqual([]);
  });

  it('names the grey mods that disqualify a loadout', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().setMod('fullbright', { on: true });
    await useLoadouts.getState().setMod('hitboxes', { on: true });

    const readiness = hypixelReady(useLoadouts.getState().active!);
    expect(readiness.ready).toBe(false);
    expect(readiness.label).toBe('REVIEW MODS');
    // Grid order, which `Launcher-Mods.png` sets: Hitboxes (Reach display) is the
    // ninth card and Fullbright the tenth.
    expect(readiness.greyMods).toEqual(['hitboxes', 'fullbright']);
    expect(readiness.detail).toBe(
      'Hitboxes and Fullbright are not Hypixel-safe. Turn them off before joining ranked.',
    );
  });

  it('a disabled grey mod does not disqualify it', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().setMod('fullbright', { on: true });
    expect(hypixelReady(useLoadouts.getState().active!).ready).toBe(false);
    await useLoadouts.getState().setMod('fullbright', { on: false });
    expect(hypixelReady(useLoadouts.getState().active!).ready).toBe(true);
  });
});

describe('registry helpers', () => {
  it('counts enabled mods against the registry defaults', async () => {
    await useLoadouts.getState().hydrate();
    const active = useLoadouts.getState().active!;
    expect(enabledCount(active)).toBeGreaterThan(0);
    expect(enabledCount(active)).toBeLessThanOrEqual(12);
  });

  it('a loadout that omits a mod still reports its registry default', async () => {
    await useLoadouts.getState().hydrate();
    await useLoadouts.getState().switchTo('bedwars');
    const bedwars = useLoadouts.getState().active!;
    // Bedwars stores four mods; FPS is not one of them, and its default is on.
    expect(bedwars.mods.fps).toBeUndefined();
    expect(isOn(bedwars, 'fps')).toBe(true);
  });

  it('filter tabs partition the grid', () => {
    expect(matchesTab('fps', 'All')).toBe(true);
    expect(matchesTab('fps', 'HUD')).toBe(true);
    expect(matchesTab('fps', 'PvP')).toBe(false);
    expect(matchesTab('toggle_sprint', 'PvP')).toBe(true);
    expect(matchesTab('zoom', 'Utility')).toBe(true);
    expect(matchesTab('fullbright', 'Visual')).toBe(true);
  });
});

describe('ui store', () => {
  it('navigating closes the palette', () => {
    useUi.getState().openPalette();
    useUi.getState().go('mods');
    expect(useUi.getState().screen).toBe('mods');
    expect(useUi.getState().paletteOpen).toBe(false);
  });

  it('opening settings closes the palette too', () => {
    useUi.getState().openPalette();
    useUi.getState().openSettings();
    expect(useUi.getState().settingsOpen).toBe(true);
    expect(useUi.getState().paletteOpen).toBe(false);
  });
});
