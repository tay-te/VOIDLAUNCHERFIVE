package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.Privacy;
import static dev.voidmc.atlas.core.social.TileFixtures.DIM;
import static dev.voidmc.atlas.core.social.TileFixtures.SERVER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.social.SharedTiles.Delta;
import dev.voidmc.atlas.core.social.SharedTiles.Inbox;
import dev.voidmc.atlas.core.social.SharedTiles.Message;
import dev.voidmc.atlas.core.social.SharedTiles.Outbox;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Shared tiles: the delta format, name tables, the outbox's budget and size cap, the inbox's filters, hostile input. */
class SharedTilesTest {
	private final NamePalette aliceBlocks = TileFixtures.palette();
	private final NamePalette aliceBiomes = TileFixtures.palette();

	private static Delta decode(byte[] b) {
		try {
			return SharedTiles.decode(b);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private Delta capture(RegionRaw raw, int level, int x0, int z0, int w, int h) {
		return SharedTiles.capture("alice", SERVER, DIM, raw, this.aliceBlocks, this.aliceBiomes, level, x0, z0, w, h);
	}

	private static void assertSameDelta(Delta a, Delta b) {
		assertEquals(a.sender(), b.sender());
		assertEquals(a.server(), b.server());
		assertEquals(a.dimension(), b.dimension());
		assertEquals(a.rx(), b.rx());
		assertEquals(a.rz(), b.rz());
		assertEquals(a.version(), b.version());
		assertEquals(a.level(), b.level());
		assertEquals(a.x0(), b.x0());
		assertEquals(a.z0(), b.z0());
		assertEquals(a.width(), b.width());
		assertEquals(a.height(), b.height());
		assertEquals(a.known(), b.known());
		for (int s = a.known().nextSetBit(0); s >= 0; s = a.known().nextSetBit(s + 1)) {
			assertEquals(a.heights()[s], b.heights()[s]);
			assertEquals(a.blocks()[s], b.blocks()[s]);
			assertEquals(a.biomes()[s], b.biomes()[s]);
			assertEquals(a.depths()[s], b.depths()[s]);
			assertEquals(a.flags()[s], b.flags()[s]);
		}
	}

	// ---- the format ----

	@Test
	void aDeltaRoundTrips() {
		RegionRaw raw = TileFixtures.region(3, -2, this.aliceBlocks, this.aliceBiomes, 1, (x, z) -> (x + z) % 3 == 0 ? 0 : 1);
		Delta d = capture(raw, 0, 64, 128, 64, 64);
		assertTrue(d.knownCount() > 2000 && d.knownCount() < d.samples());
		byte[] msg = SharedTiles.encode(d);
		assertTrue(msg.length <= SharedTiles.MAX_MESSAGE_BYTES);
		Delta back = decode(msg);
		assertSameDelta(d, back);
		assertEquals("tiles:minecraft:overworld:3,-2", back.topic());
		assertEquals(raw.version(), back.version());
		int i = RegionRaw.index(64 + 1, 128);
		assertEquals(raw.height[i], back.heights()[1]);
		assertEquals(this.aliceBlocks.name(raw.block[i]), back.blocks()[1]);
	}

	@Test
	void aMessageBuiltByHandFromTheDocumentedLayoutReads() throws IOException {
		String[] names = {"minecraft:stone", "minecraft:plains", ""};
		// a 3 x 2 rectangle at (10, 20); samples 0, 2 and 5 known
		byte[] body = TileFixtures.body(names, 3, 2, new int[] {0, 2, 5},
			new short[] {64, -12, 300}, new int[] {0, 2, 0}, new int[] {1, 1, 2}, new int[] {0, 4, 255}, new int[] {0, 2, 0});
		Delta d = SharedTiles.decode(TileFixtures.message(0, 10, 20, 3, 2, body));
		assertEquals("alice", d.sender());
		assertEquals(3, d.rx());
		assertEquals(-2, d.rz());
		assertEquals(7, d.version());
		assertEquals(BitSet.valueOf(new long[] {0b100101}), d.known());
		assertEquals(-12, d.heights()[2]);
		assertEquals("", d.blocks()[2]);
		assertEquals("minecraft:stone", d.blocks()[5]);
		assertEquals("", d.biomes()[5]);
		assertEquals((byte) 255, d.depths()[5]);
		assertEquals(RegionRaw.ROCK, d.flags()[2]);
	}

	@Test
	void nameTablesCarryNamesNeverPaletteIds() {
		// alice's palette and bob's give the same names different ids
		NamePalette bobBlocks = TileFixtures.palette("x:unrelated", "minecraft:snow", "minecraft:stone", "y:other");
		NamePalette bobBiomes = TileFixtures.palette("minecraft:river", "z:biome", "minecraft:plains");
		RegionRaw raw = new RegionRaw(0, 0);
		raw.set(0, 0, 70, this.aliceBlocks.id("minecraft:stone"), 0, this.aliceBiomes.id("minecraft:plains"), RegionRaw.KNOWN);
		raw.set(1, 0, 71, this.aliceBlocks.id("minecraft:snow"), 0, this.aliceBiomes.id("minecraft:river"), RegionRaw.KNOWN);
		raw.set(2, 0, 72, NamePalette.NONE, 0, this.aliceBiomes.id("minecraft:plains"), RegionRaw.KNOWN);
		raw.set(3, 0, 73, this.aliceBlocks.id("minecraft:stone"), 0, this.aliceBiomes.id("minecraft:plains"), RegionRaw.KNOWN);
		Delta d = capture(raw, 0, 0, 0, 4, 1);
		assertEquals(List.of("minecraft:stone", "minecraft:plains", "minecraft:snow", "minecraft:river", ""),
			new ArrayList<>(SharedTiles.nameTable(d).keySet()), "each distinct name once, blocks and biomes in one table");
		assertEquals(5, SharedTiles.nameTable(d).size());

		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), bobBlocks, bobBiomes);
		assertEquals(Received.ACCEPTED, bob.receive("alice", SharedTiles.encode(d)));
		SharedLayer layer = bob.layer(DIM, 0, 0);
		assertEquals("minecraft:stone", bobBlocks.name(layer.block[0]));
		assertEquals(bobBlocks.id("minecraft:stone"), layer.block[0]);
		assertEquals("minecraft:snow", bobBlocks.name(layer.block[1]));
		assertEquals(NamePalette.NONE, layer.block[2]);
		assertEquals("minecraft:river", bobBiomes.name(layer.biome[1]));
		assertEquals("minecraft:plains", bobBiomes.name(layer.biome[3]));
		assertEquals(4, bobBlocks.size() - 1, "no new names were needed");
	}

	@Test
	void namesThatCannotBeIdsAreSentAsNone() {
		NamePalette blocks = TileFixtures.palette("Bad Name!", "minecraft:stone");
		RegionRaw raw = new RegionRaw(0, 0);
		raw.set(0, 0, 1, blocks.id("Bad Name!"), 0, 0, RegionRaw.KNOWN);
		Delta d = SharedTiles.capture("alice", SERVER, DIM, raw, blocks, this.aliceBiomes, 0, 0, 0, 1, 1);
		assertEquals("", d.blocks()[0]);
		assertTrue(SharedTiles.validName("minecraft:oak_log"));
		assertTrue(SharedTiles.validName("mod:dir/sub.thing-1"));
		assertFalse(SharedTiles.validName("Minecraft:Stone"));
		assertFalse(SharedTiles.validName("nocolon"));
		assertFalse(SharedTiles.validName("a:b:c"));
	}

	@Test
	void anInboxAddsOnlyALimitedNumberOfNewNames() {
		String[] names = TileFixtures.manyNames(SharedTiles.Inbox.MAX_NEW_NAMES + 500);
		NamePalette blocks = TileFixtures.palette(names);
		RegionRaw raw = new RegionRaw(0, 0);
		for (int k = 0; k < names.length; k++) {
			raw.set(k % 512, k / 512, 64, blocks.id(names[k]), 0, 0, RegionRaw.KNOWN);
		}
		Outbox out = TileFixtures.outbox("alice", blocks, this.aliceBiomes).bytesPerSecond(Integer.MAX_VALUE);
		out.changedRegion(DIM, raw);
		NamePalette bobBlocks = new NamePalette();
		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), bobBlocks, new NamePalette());
		for (Message m : TileFixtures.drainAll(out, 1000)) {
			assertEquals(Received.ACCEPTED, bob.receive("alice", m.topic(), m.payload()));
		}
		assertEquals(SharedTiles.Inbox.MAX_NEW_NAMES + 1, bobBlocks.size());
		assertEquals(names.length, bob.layer(DIM, 0, 0).knownCount(), "the columns still arrive, just without a block name");
	}

	// ---- sending: chunking, size cap, budget, coalescing ----

	@Test
	void aWholeRegionGoesInPiecesNeverAtOnce() {
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 3);
		Outbox out = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes).bytesPerSecond(Integer.MAX_VALUE);
		out.changedRegion(DIM, raw);
		assertEquals(64, out.pendingPieces(), "a 512 x 512 region is 8 x 8 pieces of 64 x 64");
		List<Message> first = out.drain(0);
		assertEquals(Outbox.MAX_ENCODES_PER_DRAIN, first.size(), "one drain does a bounded amount of work");
		List<Message> all = new ArrayList<>(first);
		all.addAll(TileFixtures.drainAll(out, 1000));
		assertEquals(64, all.size());
		BitSet covered = new BitSet(RegionRaw.AREA);
		for (Message m : all) {
			Delta d = decode(m.payload());
			assertTrue(d.width() <= SharedTiles.PIECE && d.height() <= SharedTiles.PIECE);
			assertEquals("tiles:minecraft:overworld:0,0", m.topic());
			for (int s = 0; s < d.samples(); s++) {
				covered.set(RegionRaw.index(d.x0() + s % d.width(), d.z0() + s / d.width()));
			}
		}
		assertEquals(RegionRaw.AREA, covered.cardinality());
	}

	@Test
	void everyMessageFitsTheCapEvenForTheWorstTerrain() {
		String[] names = TileFixtures.manyNames(3000);
		NamePalette blocks = TileFixtures.palette(names);
		RegionRaw raw = TileFixtures.noisy(-1, 5, blocks, this.aliceBiomes, names, 9);
		Outbox out = TileFixtures.outbox("alice", blocks, this.aliceBiomes).bytesPerSecond(Integer.MAX_VALUE);
		out.changedRegion(DIM, raw);
		List<Message> all = TileFixtures.drainAll(out, 1000);
		assertTrue(all.size() > 64, "pieces with too many names or bytes were split");

		// bob already knows every name, in another order, so nothing is lost to the new-name limit
		List<String> shuffled = new ArrayList<>(Arrays.asList(names));
		Collections.shuffle(shuffled, new Random(4));
		NamePalette bobBlocks = TileFixtures.palette(shuffled.toArray(String[]::new));
		NamePalette bobBiomes = TileFixtures.palette(TileFixtures.BIOMES);
		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), bobBlocks, bobBiomes);
		for (Message m : all) {
			assertTrue(m.payload().length <= SharedTiles.MAX_MESSAGE_BYTES, m.payload().length + " bytes");
			assertTrue(SharedTiles.nameTable(decode(m.payload())).size() <= SharedTiles.MAX_NAMES);
			assertEquals(Received.ACCEPTED, bob.receive("alice", m.topic(), m.payload()));
		}
		SharedLayer layer = bob.layer(DIM, -1, 5);
		assertEquals(RegionRaw.AREA, layer.knownCount());
		for (int i = 0; i < RegionRaw.AREA; i++) {
			assertEquals(raw.height[i], layer.height[i]);
			assertEquals(blocks.name(raw.block[i]), bobBlocks.name(layer.block[i]));
			assertEquals(this.aliceBiomes.name(raw.biome[i]), bobBiomes.name(layer.biome[i]));
			assertEquals(raw.depth[i], layer.depth[i]);
			assertEquals(raw.flags[i], layer.flags[i]);
			assertEquals("alice", layer.sender(i));
		}
	}

	@Test
	void theByteBudgetIsKept() {
		String[] names = TileFixtures.manyNames(300);
		NamePalette blocks = TileFixtures.palette(names);
		int rate = 64 * 1024;
		Outbox out = TileFixtures.outbox("alice", blocks, this.aliceBiomes);
		out.changedRegion(DIM, TileFixtures.noisy(0, 0, blocks, this.aliceBiomes, names, 1));
		out.changedRegion(DIM, TileFixtures.noisy(1, 0, blocks, this.aliceBiomes, names, 2));
		List<long[]> sent = new ArrayList<>();
		long total = 0;
		long now = 0;
		for (; now <= 120_000 && (out.pendingPieces() > 0 || out.heldMessages() > 0 || now == 0); now += 50) {
			for (Message m : out.drain(now)) {
				sent.add(new long[] {now, m.payload().length});
				total += m.payload().length;
			}
			if (now == 0) {
				assertTrue(total <= rate, "the first burst is at most one second of budget: " + total);
			}
		}
		assertEquals(0, out.pendingPieces() + out.heldMessages(), "everything goes in the end");
		assertEquals(total, out.sentBytes());
		assertTrue(total > 4 * rate, "enough data to test the budget: " + total);
		double capacity = Math.max(rate, SharedTiles.MAX_MESSAGE_BYTES);
		for (int i = 0; i < sent.size(); i++) {
			long bytes = 0;
			for (int j = i; j < sent.size(); j++) {
				bytes += sent.get(j)[1];
				double allowed = capacity + rate * (sent.get(j)[0] - sent.get(i)[0]) / 1000.0;
				assertTrue(bytes <= allowed + 1e-6, "from " + sent.get(i)[0] + " to " + sent.get(j)[0] + " ms: " + bytes + " > " + allowed);
			}
		}
		// and the rate is used, not wasted: the whole lot takes about total / rate seconds
		assertTrue(now / 1000.0 < total / (double) rate + 3, "took " + now + " ms for " + total + " bytes");
	}

	@Test
	void aSlowBudgetStillLetsTheLargestMessageThrough() {
		String[] names = TileFixtures.manyNames(300);
		NamePalette blocks = TileFixtures.palette(names);
		Outbox out = TileFixtures.outbox("alice", blocks, this.aliceBiomes).bytesPerSecond(4096);
		out.changed(DIM, TileFixtures.noisy(0, 0, blocks, this.aliceBiomes, names, 1), 0, 0, 128, 64);
		List<Message> all = TileFixtures.drainAll(out, 1000);
		assertTrue(all.size() >= 2);
		assertTrue(all.stream().anyMatch(m -> m.payload().length > 4096));
	}

	@Test
	void repeatedChangesToOneAreaGoAsOneMessage() {
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 5);
		Outbox out = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes);
		for (int k = 0; k < 100; k++) {
			out.changed(DIM, raw, 16, 16, 16, 16);
		}
		out.changed(DIM, raw, 40, 20, 4, 4);
		assertEquals(1, out.pendingPieces());
		List<Message> sent = out.drain(0);
		assertEquals(1, sent.size());
		Delta d = decode(sent.get(0).payload());
		assertEquals(16, d.x0());
		assertEquals(16, d.z0());
		assertEquals(44 - 16, d.width(), "the union of what changed");
		assertEquals(32 - 16, d.height());
		assertTrue(out.drain(10_000).isEmpty());

		out.changed(DIM, raw, 0, 0, 1, 1);
		out.changed(DIM, raw, 100, 0, 1, 1);
		assertEquals(2, out.drain(20_000).size(), "different pieces are different messages");
	}

	@Test
	void aPieceIsReadWhenItIsSentSoItCarriesTheNewestColumns() {
		RegionRaw raw = new RegionRaw(0, 0);
		raw.set(5, 5, 60, this.aliceBlocks.id("minecraft:stone"), 0, 0, RegionRaw.KNOWN);
		Outbox out = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes);
		out.changed(DIM, raw, 5, 5, 1, 1);
		raw.set(5, 5, 99, this.aliceBlocks.id("minecraft:snow"), 0, 0, RegionRaw.KNOWN);
		Delta d = decode(out.drain(0).get(0).payload());
		assertEquals(99, d.heights()[0]);
		assertEquals("minecraft:snow", d.blocks()[0]);
	}

	@Test
	void nothingIsSentWithoutSharingOrAServer() {
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 5);
		Outbox out = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes);
		out.changedRegion(DIM, raw);
		out.local(SERVER, Privacy.NOBODY);
		assertEquals(0, out.pendingPieces(), "turning sharing off drops what waited");
		out.changedRegion(DIM, raw);
		assertTrue(out.drain(0).isEmpty());
		out.local(null, Privacy.PARTY);
		out.changedRegion(DIM, raw);
		assertTrue(out.drain(1).isEmpty());

		out.local(SERVER, Privacy.PARTY);
		out.changed(DIM, new RegionRaw(0, 0), 0, 0, 512, 512);
		assertTrue(TileFixtures.drainAll(out, 1000).isEmpty(), "unknown columns are never sent");

		out.changed(DIM, raw, 0, 0, 8, 8);
		assertEquals(0, out.pump(NoopTransport.INSTANCE, 5_000));
		assertEquals(1, out.pendingPieces(), "an unavailable transport keeps everything waiting");
	}

	@Test
	void lowerDetailLevelsAreSharedAndNeverReplaceFinerDetail() {
		RegionRaw coarse = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 6);
		Outbox out = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes).level(2).bytesPerSecond(Integer.MAX_VALUE);
		out.changedRegion(DIM, coarse);
		List<Message> all = TileFixtures.drainAll(out, 1000);
		assertEquals(4, all.size(), "at level 2 a region is 128 x 128 samples: four pieces");

		Roster roster = TileFixtures.party("alice", "bob", "carol");
		Inbox carol = TileFixtures.inbox("carol", roster, TileFixtures.palette(), TileFixtures.palette());
		// bob's fine columns first, then alice's coarse ones over the same ground
		NamePalette bobBlocks = TileFixtures.palette();
		RegionRaw fine = TileFixtures.region(0, 0, bobBlocks, this.aliceBiomes, 7, (x, z) -> x < 10 && z < 10 ? 1 : 0);
		Delta bobDelta = SharedTiles.capture("bob", SERVER, DIM, fine, bobBlocks, this.aliceBiomes, 0, 0, 0, 10, 10);
		assertEquals(Received.ACCEPTED, carol.receive("bob", SharedTiles.encode(bobDelta)));
		for (Message m : all) {
			assertEquals(Received.ACCEPTED, carol.receive("alice", m.topic(), m.payload()));
		}
		SharedLayer layer = carol.layer(DIM, 0, 0);
		assertEquals(RegionRaw.AREA, layer.knownCount());
		int i = RegionRaw.index(3, 3);
		assertEquals(0, layer.level(i));
		assertEquals("bob", layer.sender(i));
		assertEquals(fine.height[i], layer.height[i]);
		// a coarse sample spreads over its 4 x 4 cell
		int j = RegionRaw.index(13, 14);
		assertEquals(2, layer.level(j));
		assertEquals("alice", layer.sender(j));
		assertEquals(coarse.height[RegionRaw.index(12, 12)], layer.height[j]);
		assertEquals(List.of("bob", "alice"), layer.senders());
	}

	@Test
	void aCoarseSampleIsTheFirstKnownColumnOfItsCell() {
		RegionRaw raw = new RegionRaw(0, 0);
		raw.set(3, 2, 77, this.aliceBlocks.id("minecraft:sand"), 0, 0, RegionRaw.KNOWN);
		raw.set(1, 3, 55, this.aliceBlocks.id("minecraft:stone"), 0, 0, RegionRaw.KNOWN);
		Delta d = capture(raw, 2, 0, 0, 2, 1);
		assertTrue(d.known().get(0));
		assertFalse(d.known().get(1));
		assertEquals(77, d.heights()[0]);
		assertEquals("minecraft:sand", d.blocks()[0]);
	}

	// ---- receiving: privacy, server, sender ----

	@Test
	void privacyFiltersTilesForEachScope() {
		Roster roster = PrivacyTest.roster();
		String[] peers = {"friend", "member", "both", "stranger"};
		boolean[][] allowed = {{true, true, true, false}, {false, true, true, false}, {false, false, false, false}};
		Privacy[] scopes = {Privacy.FRIENDS, Privacy.PARTY, Privacy.NOBODY};
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 8);
		for (int s = 0; s < scopes.length; s++) {
			Inbox me = new Inbox("me", roster, TileFixtures.palette(), TileFixtures.palette());
			me.local(SERVER, scopes[s]);
			for (int p = 0; p < peers.length; p++) {
				Delta d = SharedTiles.capture(peers[p], SERVER, DIM, raw, this.aliceBlocks, this.aliceBiomes, 0, p, 0, 1, 1);
				Received r = me.receive(peers[p], SharedTiles.encode(d));
				assertEquals(allowed[s][p] ? Received.ACCEPTED : Received.NOT_ALLOWED, r, scopes[s] + " " + peers[p]);
			}
		}
	}

	@Test
	void tilesFromAnotherServerOrSenderOrMyselfAreDropped() {
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 8);
		Roster roster = TileFixtures.party("alice", "bob", "carol");
		Inbox bob = TileFixtures.inbox("bob", roster, TileFixtures.palette(), TileFixtures.palette());
		Delta other = SharedTiles.capture("alice", ServerHash.of("elsewhere.net"), DIM, raw, this.aliceBlocks, this.aliceBiomes, 0, 0, 0, 4, 4);
		assertEquals(Received.OTHER_SERVER, bob.receive("alice", SharedTiles.encode(other)));
		byte[] alices = SharedTiles.encode(capture(raw, 0, 0, 0, 4, 4));
		assertEquals(Received.WRONG_SENDER, bob.receive("carol", alices));
		assertEquals(Received.MALFORMED, bob.receive("alice", AtlasSocialTransport.tilesTopic(DIM, 9, 9), alices), "on the wrong topic");
		Delta mine = SharedTiles.capture("bob", SERVER, DIM, raw, this.aliceBlocks, this.aliceBiomes, 0, 0, 0, 4, 4);
		assertEquals(Received.FROM_SELF, bob.receive("bob", SharedTiles.encode(mine)));
		assertNull(bob.layer(DIM, 0, 0), "nothing was kept");

		assertEquals(Received.ACCEPTED, bob.receive("alice", alices));
		bob.local(ServerHash.of("elsewhere.net"), Privacy.PARTY);
		assertNull(bob.layer(DIM, 0, 0), "moving server forgets what was shared on the old one");
		bob.local(null, Privacy.PARTY);
		assertEquals(Received.OTHER_SERVER, bob.receive("alice", alices));
	}

	@Test
	void threePlayersShareOverLoopbackWithAttribution() throws Exception {
		LoopbackTransport.Hub hub = new LoopbackTransport.Hub();
		Roster roster = TileFixtures.party("alice", "bob", "carol");
		NamePalette bobBlocks = TileFixtures.palette();
		NamePalette bobBiomes = TileFixtures.palette();
		RegionRaw aliceRaw = TileFixtures.region(2, 2, this.aliceBlocks, this.aliceBiomes, 11, (x, z) -> x < 256 ? 1 : 0);
		RegionRaw bobRaw = TileFixtures.region(2, 2, bobBlocks, bobBiomes, 12, (x, z) -> x >= 256 ? 1 : 0);

		LoopbackTransport aliceEnd = hub.join("alice");
		LoopbackTransport bobEnd = hub.join("bob");
		LoopbackTransport carolEnd = hub.join("carol");
		NamePalette carolBlocks = TileFixtures.palette();
		NamePalette carolBiomes = TileFixtures.palette();
		Inbox carol = TileFixtures.inbox("carol", roster, carolBlocks, carolBiomes);
		carol.listen(carolEnd);
		Inbox aliceIn = TileFixtures.inbox("alice", roster, TileFixtures.palette(), TileFixtures.palette());
		aliceIn.listen(aliceEnd);

		Outbox aliceOut = TileFixtures.outbox("alice", this.aliceBlocks, this.aliceBiomes);
		Outbox bobOut = TileFixtures.outbox("bob", bobBlocks, bobBiomes);
		aliceOut.changedRegion(DIM, aliceRaw);
		bobOut.changedRegion(DIM, bobRaw);
		for (long now = 0; now < 60_000 && aliceOut.pendingPieces() + aliceOut.heldMessages() + bobOut.pendingPieces() + bobOut.heldMessages() > 0; now += 100) {
			aliceOut.pump(aliceEnd, now);
			bobOut.pump(bobEnd, now);
			hub.deliver();
		}
		hub.deliver();
		assertTrue(hub.bytesPublished("alice") > 0 && hub.bytesPublished("bob") > 0);

		SharedLayer layer = carol.layer(DIM, 2, 2);
		assertNotNull(layer);
		assertEquals(RegionRaw.AREA, layer.knownCount());
		int a = RegionRaw.index(10, 300);
		int b = RegionRaw.index(400, 300);
		assertEquals("alice", layer.sender(a));
		assertEquals("bob", layer.sender(b));
		assertEquals(this.aliceBlocks.name(aliceRaw.block[a]), carolBlocks.name(layer.block[a]));
		assertEquals(bobBlocks.name(bobRaw.block[b]), carolBlocks.name(layer.block[b]));
		assertEquals(bobRaw.height[b], layer.height[b]);

		// alice gets only bob's half: her own messages never come back to her
		SharedLayer atAlice = aliceIn.layer(DIM, 2, 2);
		assertEquals(RegionRaw.AREA / 2, atAlice.knownCount());
		assertEquals(List.of("bob"), atAlice.senders());

		assertEquals(RegionRaw.AREA / 2, carol.forget("bob"));
		assertNull(layer.sender(b));
		assertFalse(layer.known(b));
	}

	@Test
	void theInboxKeepsALimitedNumberOfRegions() {
		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), TileFixtures.palette(), TileFixtures.palette()).maxRegions(2);
		for (int rx = 0; rx < 3; rx++) {
			RegionRaw raw = TileFixtures.full(rx, 0, this.aliceBlocks, this.aliceBiomes, rx);
			assertEquals(Received.ACCEPTED, bob.receive("alice", SharedTiles.encode(capture(raw, 0, 0, 0, 2, 2))));
		}
		assertNull(bob.layer(DIM, 0, 0));
		assertNotNull(bob.layer(DIM, 1, 0));
		assertNotNull(bob.layer(DIM, 2, 0));
		assertEquals(2, bob.layers(DIM).size());
		assertTrue(bob.layers("minecraft:the_end").isEmpty());
	}

	// ---- hostile and damaged messages ----

	private byte[] small() {
		RegionRaw raw = TileFixtures.region(3, -2, this.aliceBlocks, this.aliceBiomes, 13, (x, z) -> (x * z) % 5 == 0 ? 0 : 1);
		return SharedTiles.encode(capture(raw, 0, 20, 30, 12, 10));
	}

	@Test
	void everyFlippedBitIsCaught() {
		byte[] good = small();
		Delta original = decode(good);
		for (int bit = 0; bit < good.length * 8; bit++) {
			byte[] bad = good.clone();
			bad[bit >> 3] ^= (byte) (1 << (bit & 7));
			try {
				Delta d = SharedTiles.decode(bad);
				// only a deflate padding bit can change without changing a single byte of what it means
				assertSameDelta(original, d);
			} catch (IOException expected) {
				// refused, as it should be
			}
		}
	}

	@Test
	void everyTruncationIsRefused() {
		byte[] good = small();
		for (int n = 0; n < good.length; n++) {
			byte[] cut = Arrays.copyOf(good, n);
			assertThrows(IOException.class, () -> SharedTiles.decode(cut), "cut to " + n);
		}
		byte[] longer = Arrays.copyOf(good, good.length + 1);
		assertThrows(IOException.class, () -> SharedTiles.decode(longer), "bytes after the stream");
	}

	@Test
	void randomBytesAreRefused() {
		Random r = new Random(17);
		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), TileFixtures.palette(), TileFixtures.palette());
		for (int k = 0; k < 2000; k++) {
			byte[] junk = new byte[r.nextInt(400)];
			r.nextBytes(junk);
			if (k % 2 == 0 && junk.length >= 4) {
				junk[0] = 0x41;
				junk[1] = 0x54;
				junk[2] = 0x53;
				junk[3] = 0x54;
			}
			if (k % 4 == 0 && junk.length >= 5) {
				junk[4] = 1;
			}
			assertEquals(Received.MALFORMED, bob.receive("alice", junk));
		}
		assertNull(bob.layer(DIM, 3, -2));
	}

	@Test
	void oversizedMessagesAreNotEvenRead() {
		byte[] huge = new byte[SharedTiles.MAX_MESSAGE_BYTES + 1];
		System.arraycopy(small(), 0, huge, 0, 64);
		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), TileFixtures.palette(), TileFixtures.palette());
		assertEquals(Received.TOO_LARGE, bob.receive("alice", huge));
		assertThrows(IOException.class, () -> SharedTiles.decode(huge));
	}

	@Test
	void aSmallMessageCannotInflateIntoALargeOne() {
		byte[] zeros = new byte[20 * 1024 * 1024];
		byte[] bomb = TileFixtures.deflate(zeros);
		assertTrue(bomb.length < SharedTiles.MAX_MESSAGE_BYTES);
		byte[] body = Arrays.copyOf(zeros, 1000);
		// claims 1000 bytes, inflates to 20 MB
		byte[] m1 = TileFixtures.message(1, 0, "alice", SERVER, DIM, 0, 0, 1, 0, 0, 4, 4, body, 1000, bomb, null);
		assertThrows(IOException.class, () -> SharedTiles.decode(m1));
		// claims more than any body may be
		byte[] m2 = TileFixtures.message(1, 0, "alice", SERVER, DIM, 0, 0, 1, 0, 0, 4, 4, body, SharedTiles.MAX_BODY_BYTES + 1, bomb, null);
		assertThrows(IOException.class, () -> SharedTiles.decode(m2));
		byte[] m3 = TileFixtures.message(1, 0, "alice", SERVER, DIM, 0, 0, 1, 0, 0, 4, 4, body, 0xFFFFFFFFL, bomb, null);
		assertThrows(IOException.class, () -> SharedTiles.decode(m3));
	}

	@Test
	void wellChecksummedNonsenseIsStillRefused() {
		String[] names = {"minecraft:stone", "minecraft:plains"};
		byte[] okBody = TileFixtures.body(names, 2, 1, new int[] {0}, new short[] {64}, new int[] {0}, new int[] {1}, new int[] {0}, new int[] {0});
		assertEquals(64, decode(TileFixtures.message(0, 0, 0, 2, 1, okBody)).heights()[0]);

		List<byte[]> bad = new ArrayList<>();
		// a name index past the table
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(names, 2, 1, new int[] {0}, new short[] {64}, new int[] {2}, new int[] {1}, new int[] {0}, new int[] {0})));
		// names that are not ids
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(new String[] {"Stone!", "x:y"}, 2, 1, new int[] {0}, new short[] {64}, new int[] {0}, new int[] {1}, new int[] {0}, new int[] {0})));
		// a known bit past the rectangle
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(names, 2, 1, new int[] {0, 5}, new short[] {64, 64}, new int[] {0, 0}, new int[] {1, 1}, new int[] {0, 0}, new int[] {0, 0})));
		// fewer columns than known bits
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(names, 2, 1, new int[] {0, 1}, new short[] {64}, new int[] {0}, new int[] {1}, new int[] {0}, new int[] {0})));
		// more columns than known bits
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(names, 2, 1, new int[] {0}, new short[] {64, 1}, new int[] {0, 0}, new int[] {1, 1}, new int[] {0, 0}, new int[] {0, 0})));
		// a known column with the unknown height
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(names, 2, 1, new int[] {0}, new short[] {RegionRaw.UNKNOWN}, new int[] {0}, new int[] {1}, new int[] {0}, new int[] {0})));
		// rectangles outside the region, empty, or too big
		bad.add(TileFixtures.message(0, 511, 0, 2, 1, okBody));
		bad.add(TileFixtures.message(2, 127, 0, 2, 1, okBody));
		bad.add(TileFixtures.message(0, 0, 0, 0, 1, okBody));
		bad.add(TileFixtures.message(0, 0, 0, 512, 512, okBody));
		// a level past the coarsest
		bad.add(TileFixtures.message(5, 0, 0, 2, 1, okBody));
		// a newer format, or an older one
		bad.add(TileFixtures.message(2, 0, "alice", SERVER, DIM, 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, null));
		bad.add(TileFixtures.message(0, 0, "alice", SERVER, DIM, 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, null));
		// no sender, a bad dimension, a region outside the world
		bad.add(TileFixtures.message(1, 0, "", SERVER, DIM, 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, null));
		bad.add(TileFixtures.message(1, 0, "alice", SERVER, "Over World", 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, null));
		bad.add(TileFixtures.message(1, 0, "alice", SERVER, DIM, 1 << 20, 0, 7, 0, 0, 2, 1, okBody, okBody.length, null, null));
		// the right length, bytes after the deflate stream
		bad.add(TileFixtures.message(1, 0, "alice", SERVER, DIM, 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, new byte[] {1, 2, 3}));
		// too many names
		String[] many = TileFixtures.manyNames(SharedTiles.MAX_NAMES + 1);
		bad.add(TileFixtures.message(0, 0, 0, 2, 1, TileFixtures.body(many, 2, 1, new int[] {0}, new short[] {64}, new int[] {0}, new int[] {1}, new int[] {0}, new int[] {0})));
		// a bad magic
		byte[] magic = TileFixtures.message(0, 0, 0, 2, 1, okBody);
		magic[0] = 'X';
		bad.add(magic);
		// sender not UTF-8
		byte[] utf = TileFixtures.message(1, 0, "alice", SERVER, DIM, 3, -2, 7, 0, 0, 2, 1, okBody, okBody.length, null, null);
		utf[9] = (byte) 0xFF;
		bad.add(utf);

		Inbox bob = TileFixtures.inbox("bob", TileFixtures.party("alice", "bob"), TileFixtures.palette(), TileFixtures.palette());
		for (int k = 0; k < bad.size(); k++) {
			byte[] b = bad.get(k);
			assertThrows(IOException.class, () -> SharedTiles.decode(b), "case " + k);
			Received r = bob.receive("alice", b);
			assertTrue(r == Received.MALFORMED || r == Received.WRONG_SENDER, "case " + k + ": " + r);
		}
		assertNull(bob.layer(DIM, 3, -2));
	}

	@Test
	void theEncoderRefusesWhatTheDecoderWould() {
		RegionRaw raw = TileFixtures.full(0, 0, this.aliceBlocks, this.aliceBiomes, 1);
		assertThrows(IllegalArgumentException.class, () -> SharedTiles.encode(capture(raw, 0, 0, 0, 512, 512)));
		assertThrows(IllegalArgumentException.class, () -> capture(raw, 0, 500, 0, 13, 1));
		assertThrows(IllegalArgumentException.class, () -> capture(raw, 5, 0, 0, 1, 1));
		String[] many = TileFixtures.manyNames(SharedTiles.MAX_NAMES + 10);
		NamePalette blocks = TileFixtures.palette(many);
		RegionRaw noisy = new RegionRaw(0, 0);
		for (int k = 0; k < many.length; k++) {
			noisy.set(k % 64, k / 64, 1, blocks.id(many[k]), 0, 0, RegionRaw.KNOWN);
		}
		Delta d = SharedTiles.capture("alice", SERVER, DIM, noisy, blocks, this.aliceBiomes, 0, 0, 0, 64, 64);
		assertThrows(IllegalArgumentException.class, () -> SharedTiles.encode(d));
		byte[] text = "{\"t\":\"x\"}".getBytes(StandardCharsets.UTF_8);
		assertThrows(IOException.class, () -> SharedTiles.decode(text));
		assertArrayEquals(SharedTiles.encode(capture(raw, 0, 0, 0, 3, 3)), SharedTiles.encode(capture(raw, 0, 0, 0, 3, 3)), "encoding is deterministic");
	}
}
