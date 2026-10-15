// Friends, presence, emotes, server addresses, and Realtime channel authorization.

import { before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { asAdmin, asService, asUser, createPlayer, freshDb, one, q, rejects, seedCatalog, U } from './db.mjs';

let db;

before(async () => {
  db = await freshDb();
  await seedCatalog(db);
  await createPlayer(db, U.kestrel, 'Kestrel');
  await createPlayer(db, U.moss, 'Moss');
  await createPlayer(db, U.tavi, 'tavi');
  await createPlayer(db, U.rin, 'Rin_');
});

async function messages(topic) {
  await asAdmin(db);
  return q(db, `select event, payload from realtime.messages where topic = $1 order by id`, [topic]);
}

async function clearMessages() {
  await asAdmin(db);
  await db.exec('delete from realtime.messages');
}

describe('server addresses', () => {
  // The same table is in crates/void-online (normalize_server); keep them in step.
  const cases = [
    ['mc.hypixel.net', 'mc.hypixel.net'],
    ['  MC.Hypixel.NET  ', 'mc.hypixel.net'],
    ['mc.hypixel.net.', 'mc.hypixel.net'],
    ['mc.hypixel.net:25565', 'mc.hypixel.net'],
    ['mc.hypixel.net.:25565', 'mc.hypixel.net'],
    ['mc.hypixel.net..', 'mc.hypixel.net'],
    ['play.example.org:25566', 'play.example.org:25566'],
    ['127.0.0.1:25565', '127.0.0.1'],
    ['', null],
  ];
  for (const [input, want] of cases) {
    it(`normalises ${JSON.stringify(input)}`, async () => {
      await asAdmin(db);
      assert.equal(await one(db, `select public.normalize_server($1)`, [input]), want);
    });
  }

  it('refuses something that is not an address', async () => {
    await asAdmin(db);
    await rejects(db.query(`select public.normalize_server('mc hypixel net')`), 'bad_server', 400);
    await rejects(db.query(`select public.normalize_server('a/b')`), 'bad_server', 400);
  });

  it('topics are stable, opaque and 32 hex', async () => {
    await asAdmin(db);
    const t = await one(db, `select public.server_topic('mc.hypixel.net')`);
    assert.match(t, /^server:[0-9a-f]{32}$/);
    assert.equal(await one(db, `select public.server_topic('mc.hypixel.net')`), t);
    assert.notEqual(await one(db, `select public.server_topic('hypixel.net')`), t);
  });
});

describe('friends', () => {
  it('a request, then accepting it, makes friends both ways and notifies the sender', async () => {
    await clearMessages();
    await asService(db);
    let list = await one(db, `select public.friend_request($1, 'moss')`, [U.kestrel]);
    assert.deepEqual(list.outgoing.map((r) => r.name), ['Moss']);
    assert.equal(typeof list.outgoing[0].at, 'number');
    assert.deepEqual((await messages(`user:${U.moss}`)).map((m) => m.payload), [{ kind: 'request', uuid: U.kestrel }]);

    await asService(db);
    list = await one(db, `select public.friends_list($1)`, [U.moss]);
    assert.deepEqual(list.incoming.map((r) => r.uuid), [U.kestrel]);

    list = await one(db, `select public.friend_accept($1, $2)`, [U.moss, U.kestrel]);
    assert.deepEqual(list.friends.map((f) => f.name), ['Kestrel']);
    assert.deepEqual(list.incoming, []);
    assert.deepEqual((await messages(`user:${U.kestrel}`)).map((m) => m.payload), [{ kind: 'accepted', uuid: U.moss }]);

    await asService(db);
    list = await one(db, `select public.friends_list($1)`, [U.kestrel]);
    assert.deepEqual(list.friends.map((f) => f.uuid), [U.moss]);
    assert.equal(list.friends[0].presence.state, 'offline');
  });

  it('refuses the obvious mistakes with the right codes', async () => {
    await asService(db);
    await rejects(db.query(`select public.friend_request($1, 'Moss')`, [U.kestrel]), 'already_friends', 409);
    await rejects(db.query(`select public.friend_request($1, 'Kestrel')`, [U.kestrel]), 'self', 400);
    await rejects(db.query(`select public.friend_request($1, 'nobody_here')`, [U.kestrel]), 'unknown_player', 404);
    await rejects(db.query(`select public.friend_request($1, 'x!')`, [U.kestrel]), 'invalid_name', 400);
    await one(db, `select public.friend_request($1, 'tavi')`, [U.kestrel]);
    await rejects(db.query(`select public.friend_request($1, 'TAVI')`, [U.kestrel]), 'already_sent', 409);
    await rejects(db.query(`select public.friend_accept($1, $2)`, [U.kestrel, U.rin]), 'no_request', 404);
  });

  it('asking someone who already asked you is accepting', async () => {
    await asService(db);
    const list = await one(db, `select public.friend_request($1, 'Kestrel')`, [U.tavi]);
    assert.deepEqual(list.friends.map((f) => f.name), ['Kestrel']);
    assert.deepEqual(list.outgoing, []);
  });

  it('decline is silent, cancel and remove notify', async () => {
    await clearMessages();
    await asService(db);
    await one(db, `select public.friend_request($1, 'Rin_')`, [U.moss]);
    await one(db, `select public.friend_decline($1, $2)`, [U.rin, U.moss]);
    assert.deepEqual((await messages(`user:${U.moss}`)), [], 'the sender is not told about a decline');

    await asService(db);
    await one(db, `select public.friend_request($1, 'Rin_')`, [U.moss]);
    await one(db, `select public.friend_cancel($1, $2)`, [U.moss, U.rin]);
    assert.deepEqual((await messages(`user:${U.rin}`)).map((m) => m.payload.kind), ['request', 'request', 'removed']);

    await asService(db);
    const list = await one(db, `select public.friend_remove($1, $2)`, [U.kestrel, U.tavi]);
    assert.deepEqual(list.friends.map((f) => f.name), ['Moss']);
    assert.deepEqual((await messages(`user:${U.tavi}`)).map((m) => m.payload.kind), ['removed']);
  });

  it('players read only their own friendships and requests', async () => {
    await asUser(db, U.rin);
    assert.equal(await one(db, `select count(*)::int from public.friendships`), 0);
    await asUser(db, U.kestrel);
    assert.equal(await one(db, `select count(*)::int from public.friendships`), 1);
    await rejects(db.query(`insert into public.friendships (a, b) values ($1, $2)`, [U.kestrel, U.rin].sort()), '42501');
  });
});

describe('presence', () => {
  beforeEach(clearMessages);

  it('a heartbeat puts the player online on a server and tells their friends', async () => {
    await asUser(db, U.moss);
    const hb = await one(db, `select public.presence_heartbeat('MC.Hypixel.net:25565')`);
    assert.equal(hb.server, 'mc.hypixel.net');
    assert.match(hb.topic, /^server:[0-9a-f]{32}$/);
    assert.deepEqual((await messages(`user:${U.kestrel}`)).map((m) => m.payload), [{ kind: 'presence', uuid: U.moss }]);

    await asService(db);
    const list = await one(db, `select public.friends_list($1)`, [U.kestrel]);
    const moss = list.friends.find((f) => f.uuid === U.moss);
    assert.equal(moss.presence.state, 'online');
    assert.equal(moss.presence.server, 'mc.hypixel.net');
    assert.equal(typeof moss.presence.last_seen, 'number');
  });

  it('a repeat heartbeat on the same server tells nobody', async () => {
    await asUser(db, U.moss);
    await one(db, `select public.presence_heartbeat('mc.hypixel.net')`);
    assert.deepEqual(await messages(`user:${U.kestrel}`), []);
  });

  it('friends can see each other’s presence rows; strangers cannot', async () => {
    await asUser(db, U.kestrel);
    assert.equal(await one(db, `select count(*)::int from public.presence where player = $1`, [U.moss]), 1);
    await asUser(db, U.rin);
    assert.equal(await one(db, `select count(*)::int from public.presence where player = $1`, [U.moss]), 0);
  });

  it('a heartbeat older than the TTL reads as offline, with last_seen kept', async () => {
    await asAdmin(db);
    await db.query(`update public.presence set updated_at = now() - interval '5 minutes' where player = $1`, [U.moss]);
    await asService(db);
    const moss = (await one(db, `select public.friends_list($1)`, [U.kestrel])).friends.find((f) => f.uuid === U.moss);
    assert.equal(moss.presence.state, 'offline');
    assert.equal(moss.presence.server, undefined);
    assert.ok(moss.presence.last_seen > 0);
  });

  it('presence_offline clears the server and notifies', async () => {
    await asUser(db, U.moss);
    await one(db, `select public.presence_heartbeat('mc.hypixel.net')`);
    await clearMessages();
    await asUser(db, U.moss);
    await db.query(`select public.presence_offline()`);
    assert.equal((await messages(`user:${U.kestrel}`)).length, 1);
    await asAdmin(db);
    assert.equal(await one(db, `select server_topic from public.presence where player = $1`, [U.moss]), null);
  });
});

describe('emotes', () => {
  it('needs a server, an owned emote, and patience; then broadcasts the stamped sender', async () => {
    await clearMessages();
    await asUser(db, U.kestrel);
    await rejects(db.query(`select public.emote('emote-wave')`), 'no_server', 409);
    const { topic } = await one(db, `select public.presence_heartbeat('mc.hypixel.net')`);
    await rejects(db.query(`select public.emote('emote-gg')`), 'not_owned', 403);
    await rejects(db.query(`select public.emote('void-cape-origin')`), 'unknown_item', 404);
    const res = await one(db, `select public.emote('emote-wave')`);
    assert.equal(typeof res.at, 'number');
    await rejects(db.query(`select public.emote('emote-wave')`), 'too_fast', 429);
    const sent = await messages(topic);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].event, 'emote');
    assert.deepEqual(sent[0].payload, { uuid: U.kestrel, id: 'emote-wave', at: res.at });
  });
});

describe('realtime authorization', () => {
  let topic;

  before(async () => {
    await asUser(db, U.moss);
    topic = (await one(db, `select public.presence_heartbeat('mc.hypixel.net')`)).topic;
    await asUser(db, U.kestrel);
    await one(db, `select public.presence_heartbeat('mc.hypixel.net')`);
    await asAdmin(db);
    await db.exec(`delete from realtime.messages`);
    await db.query(`select realtime.send('{"x":1}', 'emote', $1, true)`, [topic]);
    await db.query(`select realtime.send('{"x":2}', 'friends', $1, true)`, [`user:${U.kestrel}`]);
  });

  it('a player on the server may listen and track presence on its topic', async () => {
    await asUser(db, U.kestrel, topic);
    assert.equal(await one(db, `select count(*)::int from realtime.messages where topic = realtime.topic()`), 1);
    await db.query(`insert into realtime.messages (topic, extension) values ($1, 'presence')`, [topic]);
  });

  it('a player not on that server may not listen or track', async () => {
    await asUser(db, U.rin, topic);
    assert.equal(await one(db, `select count(*)::int from realtime.messages where topic = realtime.topic()`), 0);
    await rejects(db.query(`insert into realtime.messages (topic, extension) values ($1, 'presence')`, [topic]), '42501');
  });

  it('nobody may broadcast on a server topic — emotes come from the database', async () => {
    await asUser(db, U.kestrel, topic);
    await rejects(db.query(`insert into realtime.messages (topic, extension, event, payload) values ($1, 'broadcast', 'emote', '{"uuid":"spoof"}')`, [topic]), '42501');
  });

  it('a user topic is readable by its owner only', async () => {
    await asUser(db, U.kestrel, `user:${U.kestrel}`);
    assert.equal(await one(db, `select count(*)::int from realtime.messages where topic = realtime.topic()`), 1);
    await asUser(db, U.moss, `user:${U.kestrel}`);
    assert.equal(await one(db, `select count(*)::int from realtime.messages where topic = realtime.topic()`), 0);
  });
});
