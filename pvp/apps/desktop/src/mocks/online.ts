/**
 * The online half of the mock backend: what `void-online` + Supabase would answer, held in
 * memory, so the Store, the Friends drawer and Home's "friends on this server" can be walked in
 * a plain browser and tested in vitest.
 *
 * It keeps to the same rules as the rest of the mock: command names and shapes are exactly
 * `src-tauri/src/ipc.rs`'s, rejections are the same sentences Rust produces, and the one place
 * it knowingly invents something is said out loud — a purchase "completes" by itself a couple of
 * seconds after checkout opens, standing in for Stripe's webhook; and every valid username is a
 * VOID player except `Ghost`, which exists to show the *nobody by that name* error.
 */

import { CATALOG, FREE_ITEMS, findItem } from '../local/catalog';
import type {
  Account,
  CheckoutSession,
  CosmeticsState,
  EquippedSet,
  FriendDto,
  FriendRequestDto,
  FriendsList,
  OnlineStatus,
} from '../local/protocol';

const SLOTS = ['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge'] as const;
type Slot = (typeof SLOTS)[number];

/** The launcher catalog's kinds onto the contract's slots (see stores/cosmetics `slotFor`). */
function slotOf(kind: string): Slot | 'emotes' | null {
  if ((SLOTS as readonly string[]).includes(kind)) return kind as Slot;
  if (kind === 'emote') return 'emotes';
  if (kind === 'nameplate') return 'badge';
  return null;
}

export const SIGNED_OUT_ERROR = 'Sign in with a Microsoft account to use online features.';

const emptySet = (uuid: string): EquippedSet => ({
  uuid,
  cape: null,
  back: null,
  head: null,
  face: null,
  halo: null,
  trail: null,
  badge: null,
  emotes: [],
});

/** A stable, fake, dashed UUID for a name — the mock's stand-in for a Mojang profile. */
export function fakeUuid(name: string): string {
  let h = 2166136261;
  const hex: string[] = [];
  for (let round = 0; round < 4; round++) {
    for (const c of `${round}:${name.toLowerCase()}`) h = Math.imul(h ^ c.charCodeAt(0), 16777619) >>> 0;
    hex.push(h.toString(16).padStart(8, '0'));
  }
  const s = hex.join('');
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20, 32)}`;
}

const dashed = (u: string) =>
  u.includes('-') ? u : `${u.slice(0, 8)}-${u.slice(8, 12)}-${u.slice(12, 16)}-${u.slice(16, 20)}-${u.slice(20)}`;

interface Server {
  owned: string[];
  equipped: EquippedSet;
  friends: FriendDto[];
  incoming: FriendRequestDto[];
  outgoing: FriendRequestDto[];
}

export interface OnlineMock {
  status(account: Account | null): OnlineStatus;
  commands(account: () => Account | null): Record<string, (args: Record<string, unknown>) => unknown>;
  reset(): void;
  /** The preview's starting state (Figma v1 Ship): its friends and its Store frame. */
  seed(seed: { owned: string[]; equipped: Record<string, string>; friends: FriendDto[]; incoming: string[] }): void;
}

export function createOnlineMock(
  emit: (event: string, payload: unknown) => void,
  later: (fn: () => void, ms: number) => void,
): OnlineMock {
  let server: Server = fresh('');

  function fresh(uuid: string): Server {
    return { owned: [...FREE_ITEMS], equipped: emptySet(uuid), friends: [], incoming: [], outgoing: [] };
  }

  const isOnline = (a: Account | null): a is Account => a !== null && a.kind === 'microsoft';

  const status = (a: Account | null): OnlineStatus =>
    isOnline(a) ? { state: 'online', uuid: dashed(a.uuid), name: a.name } : { state: 'signed_out' };

  const list = (): FriendsList => structuredClone({ friends: server.friends, incoming: server.incoming, outgoing: server.outgoing });

  const cosmetics = (a: Account | null): CosmeticsState =>
    isOnline(a)
      ? { online: true, owned: [...server.owned], equipped: { ...structuredClone(server.equipped), uuid: dashed(a.uuid) } }
      : { online: false, owned: [], equipped: null };

  const same = (x: string, y: string) => x.toLowerCase() === y.toLowerCase();

  function commands(account: () => Account | null): Record<string, (args: Record<string, unknown>) => unknown> {
    const need = (): Account => {
      const a = account();
      if (!isOnline(a)) throw SIGNED_OUT_ERROR;
      return a;
    };
    const friendsChanged = (): FriendsList => {
      const l = list();
      emit('online:friends', l);
      return l;
    };

    return {
      online_status: () => status(account()),

      cosmetics_state: () => cosmetics(account()),

      cosmetics_equip: ({ slot, id }) => {
        const a = need();
        const s = String(slot);
        if (!(SLOTS as readonly string[]).includes(s)) throw `${s} is not a slot.`;
        if (id !== null && id !== undefined) {
          const item = findItem(String(id));
          if (!item) throw 'That item doesn’t exist.';
          if (slotOf(item.kind) !== s) throw `${item.id} is not a ${s} item.`;
          if (!server.owned.includes(item.id)) throw `You don’t own ${item.id}.`;
        }
        server.equipped = { ...server.equipped, [s]: (id as string | null) ?? null };
        emit('online:cosmetics', cosmetics(a));
        return { ...structuredClone(server.equipped), uuid: dashed(a.uuid) };
      },

      cosmetics_set_emotes: ({ ids }) => {
        const a = need();
        const list = Array.isArray(ids) ? ids.map(String) : [];
        if (list.length > 8) throw 'The wheel holds eight emotes.';
        for (const id of list) {
          if (!server.owned.includes(id)) throw `You don’t own ${id}.`;
        }
        server.equipped = { ...server.equipped, emotes: list };
        emit('online:cosmetics', cosmetics(a));
        return { ...structuredClone(server.equipped), uuid: dashed(a.uuid) };
      },

      store_checkout: ({ item }): CheckoutSession => {
        const a = need();
        const it = findItem(String(item));
        if (!it) throw 'That item doesn’t exist.';
        if (server.owned.includes(it.id)) throw `You already own ${it.name}.`;
        if (it.price_cents <= 0) throw `${it.name} isn’t for sale.`;
        const session_id = `cs_test_preview_${it.id}`;
        // Stands in for the player paying and Stripe's webhook landing.
        later(() => {
          if (!server.owned.includes(it.id)) server.owned = [...server.owned, it.id];
          emit('online:cosmetics', cosmetics(a));
        }, 2500);
        return { url: `https://checkout.stripe.com/c/pay/${session_id}`, session_id };
      },

      friends_list: () => {
        need();
        return list();
      },

      friends_request: ({ name }) => {
        const a = need();
        const n = String(name ?? '').trim();
        if (!/^[A-Za-z0-9_]{3,16}$/.test(n)) throw 'Usernames are 3–16 letters, numbers or underscores.';
        if (same(n, 'Ghost')) throw `Nobody called ${n} has signed in to VOID yet.`;
        if (same(n, a.name)) throw 'That’s you.';
        const friend = server.friends.find((f) => same(f.name, n));
        if (friend) throw `You’re already friends with ${friend.name}.`;
        const sent = server.outgoing.find((r) => same(r.name, n));
        if (sent) throw `You already sent ${sent.name} a request.`;
        const theirs = server.incoming.find((r) => same(r.name, n));
        if (theirs) {
          server.incoming = server.incoming.filter((r) => r !== theirs);
          server.friends = [...server.friends, { uuid: theirs.uuid, name: theirs.name, presence: { state: 'offline' } }];
          return friendsChanged();
        }
        server.outgoing = [{ uuid: fakeUuid(n), name: n, at: Date.now() }, ...server.outgoing];
        return friendsChanged();
      },

      friends_accept: ({ uuid }) => {
        need();
        const r = server.incoming.find((x) => x.uuid === uuid);
        if (!r) throw 'That request is gone.';
        server.incoming = server.incoming.filter((x) => x !== r);
        server.friends = [...server.friends, { uuid: r.uuid, name: r.name, presence: { state: 'offline' } }];
        return friendsChanged();
      },

      friends_decline: ({ uuid }) => {
        need();
        server.incoming = server.incoming.filter((x) => x.uuid !== uuid);
        return friendsChanged();
      },

      friends_cancel: ({ uuid }) => {
        need();
        server.outgoing = server.outgoing.filter((x) => x.uuid !== uuid);
        return friendsChanged();
      },

      friends_remove: ({ uuid }) => {
        need();
        server.friends = server.friends.filter((x) => x.uuid !== uuid);
        return friendsChanged();
      },
    };
  }

  return {
    status,
    commands,
    reset: () => {
      server = fresh('');
    },
    seed: ({ owned, equipped, friends, incoming }) => {
      const set = emptySet('');
      for (const id of Object.values(equipped)) {
        const item = findItem(id);
        const slot = item ? slotOf(item.kind) : null;
        if (slot === 'emotes') set.emotes.push(id);
        else if (slot) set[slot] = id;
      }
      server = {
        owned: [...new Set([...FREE_ITEMS, ...owned.filter((id) => CATALOG.some((i) => i.id === id))])],
        equipped: set,
        friends: structuredClone(friends),
        incoming: incoming.map((name) => ({ uuid: fakeUuid(name), name, at: Date.now() - 3_600_000 })),
        outgoing: [],
      };
    },
  };
}
