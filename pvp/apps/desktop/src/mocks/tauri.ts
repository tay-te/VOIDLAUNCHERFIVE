/**
 * A mock `@tauri-apps/api` — enough of `core.invoke` and `event.listen` that every
 * screen renders and every flow can be walked in a plain browser.
 *
 * `vite.config.ts` aliases `@tauri-apps/api/{core,event}` here whenever
 * `TAURI_ENV_PLATFORM` is unset, so `pnpm dev:web` needs no Rust toolchain, no
 * webview, and no `if (mock)` branches in the app. `vitest.config.ts` uses the same
 * alias, which means the store tests exercise the same code path the preview does.
 *
 * The rules it keeps to, because a mock that lies is worse than no mock:
 *
 * - Command names and payload shapes are exactly `src-tauri/src/ipc.rs`'s.
 * - Errors are the same `String` rejections Rust produces, including the wording, so
 *   the error surfaces can be reviewed.
 * - Async work takes time and emits the same events in the same order, so the launch
 *   button's idle → preparing → launching → running walk is real.
 * - `server_ping` cannot reach a socket from a browser, so it returns the fixture
 *   latency with jitter. That is the one place the preview is knowingly fictional, and
 *   it says so in the response's `motd`.
 */

import {
  BEDWARS,
  DEFAULT_LOADOUT,
  MOCK_ACCOUNT,
  MOCK_LOG_SCRIPT,
  MOCK_SERVERS,
  MOCK_SETTINGS,
  MOCK_SYSTEM,
  PREVIEW_ACCOUNT,
  PREVIEW_COSMETICS,
  PREVIEW_FRIENDS,
  PREVIEW_LAST_SESSION,
  SWORD_PVP,
} from './fixtures';
import { resolveModSettings } from '@void/protocol';
import { createOnlineMock, fakeUuid } from './online';
import type {
  Account,
  Loadout,
  LoadoutPatch,
  LoadoutSummary,
  ModId,
  Settings,
  SettingsPatch,
} from '../local/protocol';

// --------------------------------------------------------------------- event bus

type Handler = (event: { payload: unknown }) => void;
const handlers = new Map<string, Set<Handler>>();

export function listen(event: string, handler: Handler): Promise<() => void> {
  const set = handlers.get(event) ?? new Set();
  set.add(handler);
  handlers.set(event, set);
  return Promise.resolve(() => set.delete(handler));
}

export function emit(event: string, payload: unknown): void {
  handlers.get(event)?.forEach((h) => h({ payload }));
}

// ------------------------------------------------------------------------- state

interface MockState {
  account: Account | null;
  loadouts: Loadout[];
  settings: Settings;
  running: boolean;
  startedAt: number;
  log: string[];
  timers: ReturnType<typeof setTimeout>[];
}

const state: MockState = {
  account: null,
  loadouts: [
    structuredClone(SWORD_PVP),
    structuredClone(BEDWARS),
    structuredClone(DEFAULT_LOADOUT),
  ],
  settings: structuredClone(MOCK_SETTINGS),
  running: false,
  startedAt: 0,
  log: [],
  timers: [],
};

// ------------------------------------------------------------- browser preview

/**
 * The browser preview's starting state, and its `?preview=` switches.
 *
 * The real launcher keeps friends, cosmetics and the last session in `localStorage`
 * (see `stores/friends`, `stores/cosmetics`, `stores/launch`) and a real install starts
 * with none of them. The preview starts where the v1 Ship frames do instead — signed in
 * as Kestrel, the frame's friends, Cell cape on, 48 minutes on Hypixel yesterday — by
 * writing those keys before the stores hydrate. This module is only ever aliased in when
 * Tauri is not driving the build (`vite.config.ts`), so none of it can reach an installer.
 *
 * One frame per switch, so each can be reviewed on its own URL:
 *
 *   ?preview=first        no session yet — "WELCOME TO VOID", *No sessions yet*
 *   ?preview=title        the play bar's server is Title screen
 *   ?preview=offline      `navigator.onLine` is false
 *   ?preview=unreachable  Hypixel's ping fails, so it retries every 10 s
 *   ?preview=crash        the next launch exits with code 1 after a few seconds
 *   ?preview=empty        no friends and no requests
 *   ?preview=signedout    no account
 *
 * The storage keys are written out rather than imported: the stores import this module
 * (through `local/tauri`), so importing them back would be a cycle.
 */
const PREVIEW: string | null = (() => {
  if (import.meta.env.MODE === 'test' || typeof window === 'undefined') return null;
  try {
    return new URLSearchParams(window.location.search).get('preview') ?? '';
  } catch {
    return '';
  }
})();

function seedPreview(): void {
  if (PREVIEW === null) return;
  const now = Date.now();
  const put = (key: string, value: unknown, force: boolean): void => {
    try {
      if (force || localStorage.getItem(key) === null) localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* a blocked storage just means an empty preview */
    }
  };
  // A switch is a fresh look at one frame, so it overwrites; the plain preview only fills
  // what is missing, so whatever a reviewer did last time is still there.
  const force = PREVIEW !== '';

  state.account = PREVIEW === 'signedout' ? null : structuredClone(PREVIEW_ACCOUNT);

  // Friends and cosmetics live on the (mock) server now, as they do on the real one: the
  // stores fetch them once signed in and keep only an offline copy in localStorage. The
  // backend knows who is online, on which server, and when they were last seen — not which
  // game mode they are in — so the preview shows exactly that much.
  online.seed(
    PREVIEW === 'empty'
      ? { owned: PREVIEW_COSMETICS.owned, equipped: PREVIEW_COSMETICS.equipped, friends: [], incoming: [] }
      : {
          owned: PREVIEW_COSMETICS.owned,
          equipped: PREVIEW_COSMETICS.equipped,
          incoming: PREVIEW_FRIENDS.incoming,
          friends: PREVIEW_FRIENDS.friends.map((f) => {
            const p = f.presence as { state: 'online' | 'offline'; server?: string; lastSeen?: number };
            return {
              uuid: fakeUuid(f.name),
              name: f.name,
              presence: {
                state: p.state,
                ...(p.state === 'online' && p.server ? { server: p.server } : {}),
                ...(typeof p.lastSeen === 'number' ? { last_seen: now + p.lastSeen } : {}),
              },
            };
          }),
        },
  );
  if (force) {
    try {
      localStorage.removeItem('void.friends.v1');
      localStorage.removeItem('void.cosmetics.v1');
    } catch {
      /* nothing cached */
    }
  }

  if (PREVIEW === 'first') {
    try {
      localStorage.removeItem('void.lastSession.v1');
    } catch {
      /* nothing to remove */
    }
    state.loadouts.forEach((l) => {
      l.stats = { played_ms: 0, fps_avg: 0 };
    });
  } else {
    put('void.lastSession.v1', { ...PREVIEW_LAST_SESSION, ended_at: now + PREVIEW_LAST_SESSION.ended_at }, force);
  }

  const servers = [
    { host: 'mc.hypixel.net', name: 'Hypixel' },
    { host: 'na.minemen.club', name: 'Minemen Club NA' },
    { host: 'play.cubecraft.net', name: 'CubeCraft' },
    { host: 'pvp.land', name: 'PvP Land' },
  ];
  if (force) put('void.servers.v2', { servers, target: PREVIEW === 'title' ? null : 'mc.hypixel.net' }, true);

  if (PREVIEW === 'offline') {
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, get: () => false });
  }
}

const online = createOnlineMock(emit, later);

seedPreview();

/** Reset between tests. Not used by the browser preview. */
export function __resetMock(): void {
  state.timers.forEach(clearTimeout);
  state.timers = [];
  state.account = null;
  state.loadouts = [
    structuredClone(SWORD_PVP),
    structuredClone(BEDWARS),
    structuredClone(DEFAULT_LOADOUT),
  ];
  state.settings = structuredClone(MOCK_SETTINGS);
  state.running = false;
  state.log = [];
  handlers.clear();
  online.reset();
}

/** Sign in up front, for tests that are not about auth. */
export function __signIn(): Account {
  state.account = structuredClone(MOCK_ACCOUNT);
  return state.account;
}

/** Give the mock server some state — owned items, friends, requests — for tests. */
export function __seedOnline(seed: Parameters<typeof online.seed>[0]): void {
  online.seed(seed);
}

/** Sign in with a Microsoft account, so online features answer — for the Store and friends tests. */
export function __signInOnline(): Account {
  state.account = structuredClone(PREVIEW_ACCOUNT);
  return state.account;
}

/** Speed knob: tests set 0 so a launch is instant; the preview keeps real timings. */
let speed = 1;
export function __setSpeed(multiplier: number): void {
  speed = multiplier;
}

function later(fn: () => void, ms: number): void {
  if (speed === 0) {
    fn();
    return;
  }
  const t = setTimeout(fn, ms * speed);
  state.timers.push(t);
}

const summary = (l: Loadout): LoadoutSummary => ({
  id: l.id,
  name: l.name,
  icon: l.icon,
  server: l.server,
  stats: l.stats,
});

const find = (id: string): Loadout | undefined => state.loadouts.find((l) => l.id === id);

const slugify = (name: string): string =>
  name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

// ----------------------------------------------------------------------- invoke

type Args = Record<string, unknown>;

export function invoke(cmd: string, args: Args = {}): Promise<unknown> {
  const handler = commands[cmd];
  if (!handler) {
    return Promise.reject(
      `The mock backend does not implement \`${cmd}\`. Add it to src/mocks/tauri.ts ` +
        `alongside the real command in src-tauri/src/ipc.rs.`,
    );
  }
  try {
    return Promise.resolve(handler(args));
  } catch (e) {
    return Promise.reject(typeof e === 'string' ? e : String(e));
  }
}

const commands: Record<string, (args: Args) => unknown> = {
  // --- auth ---------------------------------------------------------------
  auth_login: () => {
    // The real command needs an Azure application id, which is per-publisher and
    // deliberately not compiled in (`void_core::Config::ms_client_id`). The preview
    // walks the stages it would emit and then reports the same missing-config error,
    // so the sign-in panel's every state is reviewable without an Azure tenant.
    const stages = [
      { stage: 'pending', message: 'Waiting for you to finish signing in…' },
      { stage: 'xbox', message: 'Authenticating with Xbox Live…' },
      { stage: 'minecraft', message: 'Fetching your Minecraft profile…' },
      {
        stage: 'complete',
        account: { ...structuredClone(MOCK_ACCOUNT), kind: 'microsoft' },
      },
    ];
    stages.forEach((s, i) => {
      later(() => {
        if (s.stage === 'complete') state.account = (s as { account: Account }).account;
        emit('auth:status', s);
        if (s.stage === 'complete') emit('online:status', online.status(state.account));
      }, 700 * (i + 1));
    });
    return {
      user_code: 'VOID-PREVIEW',
      verification_uri: 'https://www.microsoft.com/link',
      expires_in_s: 900,
      interval_s: 5,
      message: 'Browser preview: this code is a fixture and Microsoft has never seen it.',
    };
  },

  auth_logout: () => {
    state.account = null;
    emit('online:status', online.status(null));
  },

  auth_current: () => state.account,

  auth_offline: ({ name }) => {
    const n = String(name ?? '').trim();
    if (!n || n.length > 16 || !/^[A-Za-z0-9_]+$/.test(n)) {
      throw 'A Minecraft name is 1–16 characters: letters, digits and underscores.';
    }
    state.account = { ...structuredClone(MOCK_ACCOUNT), name: n };
    emit('online:status', online.status(state.account));
    return state.account;
  },

  // --- prepare / launch ---------------------------------------------------
  prepare: ({ loadoutId }) => {
    const loadout = find(String(loadoutId));
    if (!loadout) throw `No loadout called \`${loadoutId}\`.`;

    // The same phases `commands::launch::prepare` names, weighted the way a cold
    // 1.8.9 + Legacy Fabric + Temurin 8 install actually falls out.
    const steps: [string, number][] = [
      ['manifest', 2 * 1024 * 1024],
      ['libraries', 48 * 1024 * 1024],
      ['assets', 160 * 1024 * 1024],
      ['java', 95 * 1024 * 1024],
      ['mod', 25 * 1024 * 1024],
    ];
    const total = steps.reduce((acc, [, b]) => acc + b, 0);

    let done = 0;
    let tick = 0;
    for (const [step, weight] of steps) {
      const chunk = weight / 20;
      for (let i = 0; i < 20; i += 1) {
        done += chunk;
        const at = Math.round(done);
        tick += 1;
        later(
          () =>
            emit('prepare:progress', {
              step,
              done: at,
              total,
              bytes_per_sec: 24 * 1024 * 1024,
              ...(i === 0 ? { detail: step } : {}),
            }),
          25 * tick,
        );
      }
    }
    later(
      () => emit('prepare:progress', { step: 'done', done: total, total, bytes_per_sec: 24 * 1024 * 1024 }),
      25 * (tick + 1),
    );

    // …and only *then* resolve. `commands::launch::prepare` verifies ~3,000 objects
    // before it returns, so a mock that resolved while its own progress events were
    // still in flight would put the store into `launching` on the first frame and the
    // CTA's progress bar would never render — the one launch state a reviewer most
    // wants to see. `later` runs inline when the speed knob is 0, so the store tests
    // still resolve synchronously.
    return new Promise((resolve) => {
      later(
        () =>
          resolve({
            loadout: loadout.id,
            version_id: '1.8.9',
            files: 3184,
            downloaded_bytes: total,
            duration_ms: 25 * (tick + 1),
            java_path: '/usr/lib/jvm/java-8/bin/java',
            java_version: '1.8.0_412',
          }),
        25 * (tick + 2),
      );
    });
  },

  launch: ({ loadoutId, server }) => {
    const loadout = find(String(loadoutId));
    const host = typeof server === 'string' && server.trim() ? server.trim() : null;
    if (!loadout) throw `No loadout called \`${loadoutId}\`.`;
    if (!state.account) throw 'Not signed in. Sign in with your Microsoft account to launch.';
    if (state.running) throw 'Minecraft is already running. Close it or use Force quit first.';

    state.running = true;
    state.startedAt = Date.now();
    state.log = [];
    const port = 51000 + Math.floor(Math.random() * 4000);

    later(() => emit('game:started', { pid: 4242, loadout: loadout.id, bridge_port: port }), 10);

    MOCK_LOG_SCRIPT.forEach((line, i) => {
      later(() => {
        state.log.push(line);
        emit('game:log', { stream: 'stdout', line, ts_ms: Date.now() });
      }, 400 * (i + 1));
    });

    const after = 400 * (MOCK_LOG_SCRIPT.length + 1);
    if (host) later(() => emit('bridge:server', { t: 'server', host, connected: true }), after);
    later(
      () =>
        emit('bridge:state', {
          t: 'state',
          loadout: loadout.id,
          patch: { 'mods.fullbright.on': true },
        }),
      after + 100,
    );
    later(
      () =>
        emit('bridge:session', {
          t: 'session',
          fps_avg: 142,
          played_ms: 812_000,
          server: host,
          loadout: loadout.id,
        }),
      after + 200,
    );
    // The preview keeps the game "running" until Stop, so the Playing frame can be looked
    // at; `?preview=crash` exits non-zero a few seconds in instead. Tests (PREVIEW null)
    // end the session at once, as they always did.
    const crash = PREVIEW === 'crash';
    if (PREVIEW === null || crash) {
      later(
        () => {
          if (!state.running) return;
          state.running = false;
          emit('game:closed', {
            code: crash ? 1 : 0,
            loadout: loadout.id,
            played_ms: crash ? 132_000 : 812_000,
            fps_avg: 142,
            ping_avg: 22,
            ...(host ? { server: host } : {}),
            ...(crash
              ? {
                  crash_tail: [
                    '[Client thread/ERROR]: Unreported exception thrown!',
                    'java.lang.OutOfMemoryError: Java heap space (preview fixture)',
                  ],
                }
              : {}),
          });
        },
        crash ? 6000 : after + 400,
      );
    }

    return { pid: 4242, bridge_port: port, loadout: loadout.id };
  },

  game_kill: () => {
    if (!state.running) throw 'Minecraft is not running.';
    state.running = false;
    emit('game:closed', {
      code: 143,
      loadout: state.settings.active_loadout,
      played_ms: Math.max(0, Date.now() - state.startedAt),
      fps_avg: 142,
      ping_avg: 22,
    });
  },

  game_log_tail: ({ lines }) => state.log.slice(-Number(lines ?? 500)),

  // --- loadouts -----------------------------------------------------------
  loadouts_list: () => state.loadouts.map(summary),

  loadouts_get: ({ id }) => {
    const l = find(String(id));
    if (!l) throw `No loadout called \`${id}\`.`;
    return structuredClone(l);
  },

  loadouts_active: () => {
    const l = find(state.settings.active_loadout);
    if (!l) throw `No loadout called \`${state.settings.active_loadout}\`.`;
    return structuredClone(l);
  },

  loadouts_create: ({ name, icon }) => {
    const id = slugify(String(name ?? ''));
    if (!id) throw `\`${name}\` is not a valid loadout id: use lower-case letters, digits and single hyphens.`;
    if (find(id)) throw `A loadout called \`${id}\` already exists.`;
    const created: Loadout = {
      id,
      name: String(name).trim(),
      icon: String(icon ?? 'sword'),
      server: null,
      mc: '1.8.9',
      mods: {},
      hud: [],
      stats: { played_ms: 0, fps_avg: 0 },
    };
    state.loadouts.push(created);
    return structuredClone(created);
  },

  loadouts_update: ({ id, patch }) => {
    const l = find(String(id));
    if (!l) throw `No loadout called \`${id}\`.`;
    const p = (patch ?? {}) as LoadoutPatch;
    if (p.name !== undefined) l.name = p.name;
    if (p.icon !== undefined) l.icon = p.icon;
    if (p.server !== undefined) l.server = p.server;
    if (p.hud !== undefined) l.hud = p.hud;
    if (p.mods !== undefined) {
      // Rust merges each named mod over its *effective* settings, so a partial write
      // ({gamma}) keeps `on`. The preview does the same or the Mods pane would appear
      // to clear settings it never touched.
      for (const [id, incoming] of Object.entries(p.mods)) {
        const key = id as ModId;
        l.mods[key] = { ...resolveModSettings(l, key), ...incoming } as never;
      }
    }
    return structuredClone(l);
  },

  loadouts_delete: ({ id }) => {
    const i = state.loadouts.findIndex((l) => l.id === id);
    if (i < 0) throw `No loadout called \`${id}\`.`;
    if (state.loadouts.length === 1) throw 'The last loadout cannot be deleted.';
    state.loadouts.splice(i, 1);
    const first = state.loadouts[0];
    if (state.settings.active_loadout === id && first) state.settings.active_loadout = first.id;
    return state.loadouts.map(summary);
  },

  loadouts_switch: ({ id }) => {
    const l = find(String(id));
    if (!l) throw `No loadout called \`${id}\`.`;
    state.settings.active_loadout = l.id;
    emit('loadout:switched', structuredClone(l));
    return structuredClone(l);
  },

  // --- settings -----------------------------------------------------------
  settings_get: () => structuredClone(state.settings),

  settings_set: ({ patch }) => {
    const p = (patch ?? {}) as SettingsPatch;
    const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
    state.settings = {
      ...state.settings,
      ...p,
      ...(p.ram_mb !== undefined ? { ram_mb: clamp(p.ram_mb, 1024, 32768) } : {}),
      ...(p.ui_scale !== undefined ? { ui_scale: clamp(p.ui_scale, 0.5, 3) } : {}),
      ...(p.hud_editor_grid !== undefined
        ? { hud_editor_grid: clamp(p.hud_editor_grid, 0, 64) }
        : {}),
      // Turning auto-detect back on forgets the configured path, as Rust does.
      ...(p.java_auto === true ? { java_path: null } : {}),
    };
    return structuredClone(state.settings);
  },

  // --- system -------------------------------------------------------------
  system_info: () => structuredClone(MOCK_SYSTEM),

  java_status: () => ({
    found: true,
    path: '/usr/lib/jvm/java-8-openjdk/bin/java',
    version: '1.8.0_412',
    major: 8,
    source: 'system',
  }),

  server_ping: ({ host }) => {
    if (PREVIEW === 'unreachable' && host === 'mc.hypixel.net') {
      throw `Could not reach ${host}: no response within 5 s.`;
    }
    const known = MOCK_SERVERS.find((s) => s.host === host);
    if (!known) {
      throw `Could not reach ${host}: the browser preview cannot open a TCP socket.`;
    }
    const jitter = Math.round((Math.random() - 0.5) * 8);
    return {
      host: known.host,
      port: 25565,
      latency_ms: Math.max(1, known.latency_ms + jitter),
      online: known.online,
      max: 200_000,
      version: '1.8.9',
      motd: `${known.name} — preview fixture, not a live ping`,
    };
  },

  open_data_dir: () => MOCK_SYSTEM.data_dir,

  updater_check: () => ({
    available: false,
    current_version: MOCK_SYSTEM.app_version,
    version: null,
    notes: null,
    date: null,
    error: 'The updater endpoint is a placeholder (updates.void.invalid) until release signing exists.',
  }),

  // --- window -------------------------------------------------------------
  window_minimize: () => undefined,
  window_toggle_maximize: () => undefined,
  window_close: () => undefined,

  // --- online (mocks/online.ts) -----------------------------------------------
  ...online.commands(() => state.account),
};
