/**
 * Store tests, run against the same mock backend the browser preview uses.
 *
 * That is the point of the alias in `vitest.config.ts`: a green test here means
 * `pnpm dev:web` behaves the same way, and the mock stays honest about the command
 * names and payload shapes in `src-tauri/src/ipc.rs`.
 */

import { beforeEach, describe, expect, it } from 'vitest';

import { __resetMock, __setSpeed, __signIn, emit } from '../mocks/tauri';
import { hypixelReady } from '../local/hypixelReady';
import { effectiveState, enabledCount, isOn, matchesTab } from '../local/registry';
import { useLaunch, wireLaunchEvents, formatBytes, stepLabel } from './launch';
import { useLoadouts, wireLoadoutEvents } from './loadouts';
import { useServers, nameForHost, pingTone } from './servers';
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

  it('adds and removes favourites, and derives a display name', () => {
    const before = useServers.getState().servers.length;
    useServers.getState().add('play.example.net');
    expect(useServers.getState().servers.length).toBe(before + 1);
    expect(useServers.getState().selected).toBe('play.example.net');

    useServers.getState().remove('play.example.net');
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
    expect(readiness.greyMods).toEqual(['fullbright', 'hitboxes']);
    expect(readiness.detail).toBe(
      'Fullbright and Hitboxes are not Hypixel-safe. Turn them off before joining ranked.',
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
