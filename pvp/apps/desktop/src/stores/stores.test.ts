// @vitest-environment jsdom
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
import { useServers, nameForHost, normaliseHost, pingTone } from './servers';
import { useFriends, USERNAME_ERROR, friendsOn, lastSeenText } from './friends';
import { useCosmetics } from './cosmetics';
import { CATALOG, FREE_ITEMS } from '../local/catalog';
import { countsFor, TOTAL_MODS } from '../local/categories';
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
  useLaunch.setState({ phase: 'idle', progress: null, log: [], error: null, lastSession: null, crash: null, server: null, launchedServer: null });
  useUi.setState({ screen: 'home', paletteOpen: false, settingsOpen: false, logOpen: false, friendsOpen: false, storeItem: null });
  localStorage.clear();
  useFriends.getState().hydrate();
  useCosmetics.getState().hydrate();
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

    await useLaunch.getState().start('sword-pvp', 'mc.hypixel.net');
    await until(() => useLaunch.getState().lastSession !== null, 'the session to end');

    expect(seen).toContain('preparing');
    expect(seen).toContain('launching');
    expect(seen).toContain('running');
    expect(useLaunch.getState().phase).toBe('idle');
    expect(useLaunch.getState().crash).toBeNull();
    expect(useLaunch.getState().lastSession?.server).toBe('mc.hypixel.net');
    expect(useLaunch.getState().lastSession?.played_ms).toBeGreaterThan(0);
    expect(useLaunch.getState().log.length).toBeGreaterThan(0);

    unsub();
    stop();
  });

  it('refuses to launch without an account, and says so', async () => {
    const stop = await wireLaunchEvents();
    await useLoadouts.getState().hydrate();
    await useLaunch.getState().start('sword-pvp', null);
    expect(useLaunch.getState().phase).toBe('idle');
    expect(useLaunch.getState().error).toMatch(/Not signed in/);
    stop();
  });

  it('a second start while busy is ignored rather than queued', async () => {
    __signIn();
    useLaunch.setState({ phase: 'running' });
    await useLaunch.getState().start('sword-pvp', null);
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

  it('turns a non-zero exit into a crash card, and Stop into nothing of the kind', async () => {
    const stop = await wireLaunchEvents();
    emit('game:closed', { code: 1, loadout: 'sword-pvp', played_ms: 120_000, fps_avg: 90, server: 'mc.hypixel.net', crash_tail: ['boom'] });
    expect(useLaunch.getState().crash).toEqual({ code: 1, played_ms: 120_000, server: 'mc.hypixel.net', tail: ['boom'] });
    expect(useLaunch.getState().error).toBeNull();
    // It still counts as the last session: the player did play.
    expect(useLaunch.getState().lastSession?.played_ms).toBe(120_000);
    useLaunch.getState().dismissCrash();
    emit('game:closed', { code: 143, loadout: 'sword-pvp', played_ms: 5_000, fps_avg: 0 });
    expect(useLaunch.getState().crash).toBeNull();
    stop();
  });

  it('keeps the last session across a restart, and only a real one', async () => {
    const stop = await wireLaunchEvents();
    emit('game:closed', { code: 0, loadout: 'sword-pvp', played_ms: 2_880_000, fps_avg: 142, ping_avg: 22, server: 'mc.hypixel.net' });
    const saved = JSON.parse(localStorage.getItem('void.lastSession.v1') ?? 'null');
    expect(saved).toMatchObject({ played_ms: 2_880_000, fps_avg: 142, ping_avg: 22, server: 'mc.hypixel.net' });
    // A game killed before the mod reported anything leaves the previous one in place.
    emit('game:closed', { code: 143, loadout: 'sword-pvp', played_ms: 0, fps_avg: 0 });
    expect(useLaunch.getState().lastSession?.played_ms).toBe(2_880_000);
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
  it('pings a known host with a real-looking SLP answer', async () => {
    await useServers.getState().ping('mc.hypixel.net');
    const state = useServers.getState().pings['mc.hypixel.net'];
    expect(state?.status).toBe('ok');
    expect(state?.result?.online).toBeGreaterThan(0);
    expect(state?.at).toBeGreaterThan(0);
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
    expect(useServers.getState().add('play.example.net')).toBe(true);
    expect(useServers.getState().servers.length).toBe(before + 1);
    expect(useServers.getState().target).toBe('play.example.net');

    useServers.getState().remove('play.example.net');
    expect(useServers.getState().servers.length).toBe(before);

    expect(nameForHost('mc.hypixel.net')).toBe('Hypixel');
    expect(nameForHost('pvp.land')).toBe('Pvp');
  });

  it('adding a host that is already listed selects it rather than duplicating it', () => {
    const before = useServers.getState().servers.length;
    useServers.getState().add('mc.hypixel.net');
    expect(useServers.getState().servers.length).toBe(before);
    expect(useServers.getState().target).toBe('mc.hypixel.net');
  });

  it('accepts what the multiplayer screen accepts, and refuses the rest', () => {
    expect(normaliseHost(' Play.Example.NET ')).toBe('play.example.net');
    expect(normaliseHost('localhost:25566')).toBe('localhost:25566');
    expect(normaliseHost('not a host')).toBeNull();
    expect(normaliseHost('')).toBeNull();
    expect(useServers.getState().add('not a host')).toBe(false);
  });

  it('Title screen is a choice of its own, and survives a restart', () => {
    useServers.getState().choose(null);
    expect(useServers.getState().target).toBeNull();
    expect(JSON.parse(localStorage.getItem('void.servers.v2') ?? '{}').target).toBeNull();
  });

  it('colours a ping the way the server menu does: green under 60, grey above', () => {
    expect(pingTone(22)).toBe('ok');
    expect(pingTone(48)).toBe('ok');
    expect(pingTone(71)).toBe('muted');
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

describe('friends store (rule 7)', () => {
  it('a real install starts empty', () => {
    const s = useFriends.getState();
    expect(s.friends).toEqual([]);
    expect(s.incoming).toEqual([]);
    expect(s.outgoing).toEqual([]);
  });

  it('adds by Minecraft username only: 3–16 letters, digits and underscores', () => {
    expect(useFriends.getState().request('x!')).toBe(USERNAME_ERROR);
    expect(useFriends.getState().request('ab')).toBe(USERNAME_ERROR);
    expect(useFriends.getState().request('a_very_long_name_x')).toBe(USERNAME_ERROR);
    expect(useFriends.getState().request('nine')).toBeNull();
    expect(useFriends.getState().outgoing).toEqual(['nine']);
    expect(useFriends.getState().request('NINE')).toMatch(/already sent/);
  });

  it('answering a request in either direction makes a friend', () => {
    useFriends.setState({ incoming: ['marrow'], friends: [], outgoing: [] });
    useFriends.getState().accept('marrow');
    expect(useFriends.getState().incoming).toEqual([]);
    expect(useFriends.getState().friends.map((f) => f.name)).toEqual(['marrow']);
    expect(useFriends.getState().request('Marrow')).toMatch(/already friends/);

    useFriends.setState({ incoming: ['tavi'] });
    expect(useFriends.getState().request('tavi')).toBeNull();
    expect(useFriends.getState().friends.map((f) => f.name)).toContain('tavi');
  });

  it('knows who is on the server Launch joins', () => {
    const friends = [
      { name: 'Moss', presence: { state: 'online' as const, server: 'mc.hypixel.net', game: 'Duels' } },
      { name: 'Rin_', presence: { state: 'online' as const, lobby: true } },
      { name: 'Otto', presence: { state: 'offline' as const, server: 'mc.hypixel.net' } },
    ];
    expect(friendsOn(friends, 'MC.HYPIXEL.NET').map((f) => f.name)).toEqual(['Moss']);
    expect(friendsOn(friends, null)).toEqual([]);
  });

  it('says when an offline friend was last seen', () => {
    const now = 1_000_000_000_000;
    expect(lastSeenText(now - 4 * 3_600_000, now)).toBe('Last seen 4 h ago');
    expect(lastSeenText(now - 26 * 3_600_000, now)).toBe('Last seen yesterday');
  });
});

describe('cosmetics store (rules 5, 8)', () => {
  it('owns the free items and nothing else to begin with', () => {
    expect(useCosmetics.getState().owned.sort()).toEqual([...FREE_ITEMS].sort());
    expect(useCosmetics.getState().equipped.cape).toBe('origin-cape');
  });

  it('equips only what is owned, one per kind, and a second press takes it off', () => {
    useCosmetics.getState().equip('void-cape');
    expect(useCosmetics.getState().equipped.cape).toBe('origin-cape');
    useCosmetics.getState().equip('plain-nameplate');
    expect(useCosmetics.getState().equipped.nameplate).toBe('plain-nameplate');
    useCosmetics.getState().equip('plain-nameplate');
    expect(useCosmetics.getState().equipped.nameplate).toBeUndefined();
  });

  it('the catalog matches the frame’s counts: 6 capes, 4 trails, 8 emotes, 5 nameplates', () => {
    const count = (kind: string) => CATALOG.filter((i) => i.kind === kind).length;
    expect([count('cape'), count('trail'), count('emote'), count('nameplate')]).toEqual([6, 4, 8, 5]);
    expect(CATALOG.length).toBe(23);
  });
});

describe('category counts (Home strip)', () => {
  it('splits the registry the way the frame does: 11 · 11 · 4 · 3', () => {
    expect(TOTAL_MODS).toBe(29);
    expect(countsFor(null).map((c) => c.total)).toEqual([11, 11, 4, 3]);
  });
});
