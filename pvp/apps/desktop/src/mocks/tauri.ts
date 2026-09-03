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
  MOCK_ACCOUNT,
  MOCK_LOG_SCRIPT,
  MOCK_SERVERS,
  MOCK_SETTINGS,
  MOCK_SYSTEM,
  SWORD_PVP,
} from './fixtures';
import type {
  Account,
  Loadout,
  LoadoutPatch,
  LoadoutSummary,
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
  log: string[];
  timers: ReturnType<typeof setTimeout>[];
}

const state: MockState = {
  account: null,
  loadouts: [structuredClone(SWORD_PVP), structuredClone(BEDWARS)],
  settings: structuredClone(MOCK_SETTINGS),
  running: false,
  log: [],
  timers: [],
};

/** Reset between tests. Not used by the browser preview. */
export function __resetMock(): void {
  state.timers.forEach(clearTimeout);
  state.timers = [];
  state.account = null;
  state.loadouts = [structuredClone(SWORD_PVP), structuredClone(BEDWARS)];
  state.settings = structuredClone(MOCK_SETTINGS);
  state.running = false;
  state.log = [];
  handlers.clear();
}

/** Sign in up front, for tests that are not about auth. */
export function __signIn(): Account {
  state.account = structuredClone(MOCK_ACCOUNT);
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
    const stages = [
      { stage: 'pending', message: 'Waiting for you to finish signing in…' },
      { stage: 'xbox', message: 'Authenticating with Xbox Live…' },
      { stage: 'minecraft', message: 'Fetching your Minecraft profile…' },
      {
        stage: 'failed',
        message:
          'Microsoft sign-in needs void-core’s auth pipeline, which is not implemented yet. ' +
          'Use "Play offline" in the meantime.',
      },
    ];
    stages.forEach((s, i) => later(() => emit('auth:status', s), 700 * (i + 1)));
    return {
      user_code: 'VOID-SETUP',
      verification_uri: 'https://www.microsoft.com/link',
      expires_in_s: 900,
      interval_s: 5,
    };
  },

  auth_logout: () => {
    state.account = null;
  },

  auth_current: () => state.account,

  auth_offline: ({ name }) => {
    const n = String(name ?? '').trim();
    if (!n || n.length > 16 || !/^[A-Za-z0-9_]+$/.test(n)) {
      throw 'A Minecraft name is 1–16 characters: letters, digits and underscores.';
    }
    state.account = { ...structuredClone(MOCK_ACCOUNT), name: n };
    return state.account;
  },

  // --- prepare / launch ---------------------------------------------------
  prepare: ({ loadoutId }) => {
    const loadout = find(String(loadoutId));
    if (!loadout) throw `No loadout called \`${loadoutId}\`.`;

    const steps: [string, number][] = [
      ['manifest', 2 * 1024 * 1024],
      ['libraries', 48 * 1024 * 1024],
      ['assets', 160 * 1024 * 1024],
      ['fabric', 6 * 1024 * 1024],
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

    return {
      loadout: loadout.id,
      bytes_downloaded: 0,
      duration_ms: 25 * (tick + 1),
      java_path: '/usr/lib/jvm/java-8/bin/java',
      from_cache: false,
    };
  },

  launch: ({ loadoutId }) => {
    const loadout = find(String(loadoutId));
    if (!loadout) throw `No loadout called \`${loadoutId}\`.`;
    if (!state.account) throw 'Not signed in. Sign in with your Microsoft account to launch.';
    if (state.running) throw 'Minecraft is already running. Close it or use Force quit first.';

    state.running = true;
    state.log = [];
    const port = 51000 + Math.floor(Math.random() * 4000);

    later(() => emit('game:started', { pid: 0, loadout: loadout.id, bridge_port: port, simulated: true }), 10);

    MOCK_LOG_SCRIPT.forEach((line, i) => {
      later(() => {
        state.log.push(line);
        emit('game:log', { stream: 'stdout', line, ts_ms: Date.now() });
      }, 400 * (i + 1));
    });

    const after = 400 * (MOCK_LOG_SCRIPT.length + 1);
    later(() => emit('bridge:server', { t: 'server', host: 'mc.hypixel.net', connected: true }), after);
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
          server: 'mc.hypixel.net',
          loadout: loadout.id,
        }),
      after + 200,
    );
    later(() => {
      state.running = false;
      emit('game:closed', {
        code: 0,
        loadout: loadout.id,
        played_ms: 812_000,
        fps_avg: 142,
        server: 'mc.hypixel.net',
      });
    }, after + 400);

    return { pid: 0, bridge_port: port, loadout: loadout.id };
  },

  game_kill: () => {
    if (!state.running) throw 'Minecraft is not running.';
    state.running = false;
    emit('game:closed', { code: 143, loadout: state.settings.active_loadout, played_ms: 0, fps_avg: 0 });
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
    if (p.mods !== undefined) l.mods = { ...l.mods, ...p.mods };
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
    };
    return structuredClone(state.settings);
  },

  // --- system -------------------------------------------------------------
  system_info: () => structuredClone(MOCK_SYSTEM),

  java_status: () => ({
    found: true,
    path: '/usr/lib/jvm/java-8-openjdk/bin/java',
    version: '1.8.0_412',
    source: 'system',
  }),

  server_ping: ({ host }) => {
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

  open_data_dir: () => '~/Library/Application Support/void-pvp',

  updater_check: () => ({
    available: false,
    current_version: MOCK_SYSTEM.app_version,
    version: null,
    notes: null,
    date: null,
    error: 'The updater endpoint is a placeholder (updates.void.invalid).',
  }),

  // --- window -------------------------------------------------------------
  window_minimize: () => undefined,
  window_toggle_maximize: () => undefined,
  window_close: () => undefined,
};
