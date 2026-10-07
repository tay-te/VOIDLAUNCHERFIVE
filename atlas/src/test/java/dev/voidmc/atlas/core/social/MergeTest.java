package dev.voidmc.atlas.core.social;

import static dev.voidmc.atlas.core.social.TileFixtures.DIM;
import static dev.voidmc.atlas.core.social.TileFixtures.SERVER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.social.SharedTiles.Inbox;
import dev.voidmc.atlas.core.social.SharedTiles.Merged;
import dev.voidmc.atlas.core.social.SharedTiles.Message;
import dev.voidmc.atlas.core.social.SharedTiles.Outbox;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The rule that matters most: shared terrain fills only what the player has not seen, and never changes their own map. */
class MergeTest {
	private final NamePalette blocks = TileFixtures.palette(TileFixtures.BLOCKS);
	private final NamePalette biomes = TileFixtures.palette(TileFixtures.BIOMES);

	/** A shared layer for a region, filled from a seeded random where {@code known} says. */
	private SharedLayer shared(int rx, int rz, long seed, double fraction, String sender) {
		SharedLayer layer = new SharedLayer(DIM, rx, rz);
		int slot = layer.senderSlot(sender);
		Random r = new Random(seed);
		for (int i = 0; i < RegionRaw.AREA; i++) {
			if (r.nextDouble() < fraction) {
				layer.put(i, (short) (r.nextInt(500) - 100), (char) (1 + r.nextInt(6)), (char) (1 + r.nextInt(4)), r.nextInt(20),
					r.nextBoolean() ? RegionRaw.ROCK : 0, r.nextInt(3), slot);
			}
		}
		return layer;
	}

	private record Copy(short[] height, char[] block, byte[] depth, char[] biome, byte[] flags, long version) {
		static Copy of(RegionRaw r) {
			return new Copy(r.height.clone(), r.block.clone(), r.depth.clone(), r.biome.clone(), r.flags.clone(), r.version());
		}

		static Copy of(SharedLayer s) {
			return new Copy(s.height.clone(), s.block.clone(), s.depth.clone(), s.biome.clone(), s.flags.clone(), s.version());
		}

		void assertSame(RegionRaw r) {
			assertArrayEquals(this.height, r.height);
			assertArrayEquals(this.block, r.block);
			assertArrayEquals(this.depth, r.depth);
			assertArrayEquals(this.biome, r.biome);
			assertArrayEquals(this.flags, r.flags);
			assertEquals(this.version, r.version());
		}

		void assertSame(SharedLayer s) {
			assertArrayEquals(this.height, s.height);
			assertArrayEquals(this.block, s.block);
			assertArrayEquals(this.depth, s.depth);
			assertArrayEquals(this.biome, s.biome);
			assertArrayEquals(this.flags, s.flags);
			assertEquals(this.version, s.version());
		}
	}

	/** Checks every column of a merge against the rule. */
	private static void assertMerged(RegionRaw own, SharedLayer shared, Merged m) {
		int friend = 0;
		for (int i = 0; i < RegionRaw.AREA; i++) {
			boolean mine = own != null && own.known(i);
			if (mine) {
				assertFalse(m.fromFriend(i), "own column " + i + " marked as a friend's");
				assertEquals(own.height[i], m.raw().height[i], "height " + i);
				assertEquals(own.block[i], m.raw().block[i], "block " + i);
				assertEquals(own.depth[i], m.raw().depth[i], "depth " + i);
				assertEquals(own.biome[i], m.raw().biome[i], "biome " + i);
				assertEquals(own.flags[i], m.raw().flags[i], "flags " + i);
				assertNull(m.sender(i));
			} else if (shared != null && shared.known(i)) {
				assertTrue(m.fromFriend(i));
				assertEquals(shared.height[i], m.raw().height[i]);
				assertEquals(shared.block[i], m.raw().block[i]);
				assertEquals(shared.depth[i], m.raw().depth[i]);
				assertEquals(shared.biome[i], m.raw().biome[i]);
				assertEquals(RegionRaw.KNOWN | (shared.flags[i] & RegionRaw.ROCK), m.raw().flags[i]);
				assertTrue(m.raw().known(i));
				assertEquals(shared.sender(i), m.sender(i));
				friend++;
			} else {
				assertFalse(m.fromFriend(i));
				assertFalse(m.raw().known(i));
				if (own != null) {
					// unknown own columns keep whatever they held (normally nothing)
					assertEquals(own.height[i], m.raw().height[i]);
					assertEquals(own.flags[i], m.raw().flags[i]);
				} else {
					assertEquals(RegionRaw.UNKNOWN, m.raw().height[i]);
				}
			}
		}
		assertEquals(friend, m.friendColumns());
	}

	@Test
	void ownColumnsAreNeverOverwrittenAcrossManyRandomMaps() {
		for (long seed = 0; seed < 12; seed++) {
			Random r = new Random(seed);
			double ownShare = r.nextDouble();
			RegionRaw own = TileFixtures.region(4, -7, this.blocks, this.biomes, seed, (x, z) -> r.nextDouble() < ownShare ? 1 : 0);
			SharedLayer shared = shared(4, -7, seed + 100, r.nextDouble(), "alice");
			Copy ownBefore = Copy.of(own);
			Copy sharedBefore = Copy.of(shared);
			Merged m = SharedTiles.merge(own, shared);
			assertMerged(own, shared, m);
			ownBefore.assertSame(own);
			sharedBefore.assertSame(shared);
		}
	}

	@Test
	void ownColumnsWinEvenWhenTheyLookEmpty() {
		// a known column at height 0 with no block and no biome is still the player's own knowledge
		RegionRaw own = new RegionRaw(0, 0);
		own.set(10, 10, 0, NamePalette.NONE, 0, NamePalette.NONE, RegionRaw.KNOWN);
		own.set(11, 10, -64, NamePalette.NONE, 0, NamePalette.NONE, RegionRaw.KNOWN | RegionRaw.ROCK);
		SharedLayer shared = shared(0, 0, 1, 1.0, "alice");
		Merged m = SharedTiles.merge(own, shared);
		assertMerged(own, shared, m);
		int i = RegionRaw.index(10, 10);
		assertEquals(0, m.raw().height[i]);
		assertEquals(NamePalette.NONE, m.raw().block[i]);
		assertEquals(RegionRaw.KNOWN | RegionRaw.ROCK, m.raw().flags[RegionRaw.index(11, 10)]);
		assertEquals(RegionRaw.AREA - 2, m.friendColumns());
	}

	@Test
	void aFullyWalkedRegionTakesNothingFromFriends() {
		RegionRaw own = TileFixtures.full(1, 1, this.blocks, this.biomes, 3);
		Merged m = SharedTiles.merge(own, shared(1, 1, 4, 1.0, "alice"));
		assertEquals(0, m.friendColumns());
		assertArrayEquals(own.height, m.raw().height);
		assertArrayEquals(own.block, m.raw().block);
		assertArrayEquals(own.flags, m.raw().flags);
	}

	@Test
	void mergingWithNothingIsACopyAndAnUnvisitedRegionIsAllFriends() {
		RegionRaw own = TileFixtures.region(0, 0, this.blocks, this.biomes, 2, (x, z) -> x < 100 ? 1 : 0);
		Merged alone = SharedTiles.merge(own, null);
		assertMerged(own, null, alone);
		assertEquals(0, alone.friendColumns());
		alone.raw().set(0, 0, 1, 1, 1, 1, RegionRaw.KNOWN);
		assertFalse(own.height[0] == 1 && own.block[0] == 1 && own.biome[0] == 1, "the merge is a copy, not a view");

		SharedLayer shared = shared(0, 0, 5, 0.5, "bob");
		Merged unvisited = SharedTiles.merge(null, shared);
		assertMerged(null, shared, unvisited);
		assertEquals(shared.knownCount(), unvisited.friendColumns());

		assertThrows(IllegalArgumentException.class, () -> SharedTiles.merge(null, null));
		assertThrows(IllegalArgumentException.class, () -> SharedTiles.merge(own, shared(0, 1, 5, 0.1, "bob")));
	}

	@Test
	void sharingEndToEndLeavesTheReceiversWalkedColumnsAlone() {
		Roster roster = TileFixtures.party("alice", "bob");
		NamePalette aliceBlocks = TileFixtures.palette();
		NamePalette aliceBiomes = TileFixtures.palette();
		// alice has seen the whole region; bob has walked a diagonal band of it, and sees different blocks there
		RegionRaw alice = TileFixtures.full(0, 0, aliceBlocks, aliceBiomes, 21);
		RegionRaw bob = TileFixtures.region(0, 0, this.blocks, this.biomes, 22, (x, z) -> Math.abs(x - z) < 40 ? 1 : 0);
		Copy bobBefore = Copy.of(bob);

		Outbox out = TileFixtures.outbox("alice", aliceBlocks, aliceBiomes).bytesPerSecond(Integer.MAX_VALUE);
		out.changedRegion(DIM, alice);
		Inbox in = TileFixtures.inbox("bob", roster, this.blocks, this.biomes);
		for (Message msg : TileFixtures.drainAll(out, 1000)) {
			assertEquals(Received.ACCEPTED, in.receive("alice", msg.topic(), msg.payload()));
		}
		bobBefore.assertSame(bob);
		SharedLayer layer = in.layer(DIM, 0, 0);
		assertEquals(RegionRaw.AREA, layer.knownCount());

		Merged m = SharedTiles.merge(bob, layer);
		assertMerged(bob, layer, m);
		int walked = 0;
		for (int i = 0; i < RegionRaw.AREA; i++) {
			walked += bob.known(i) ? 1 : 0;
		}
		assertEquals(RegionRaw.AREA - walked, m.friendColumns());
		int band = RegionRaw.index(200, 210);
		assertFalse(m.fromFriend(band));
		assertEquals(bob.height[band], m.raw().height[band]);
		int far = RegionRaw.index(500, 3);
		assertEquals("alice", m.sender(far));
		assertEquals(aliceBlocks.name(alice.block[far]), this.blocks.name(m.raw().block[far]));

		// bob walks somewhere alice had shared: from then on that column is his
		bob.set(500, 3, 12, this.blocks.id("minecraft:snow"), 0, this.biomes.id("minecraft:plains"), RegionRaw.KNOWN);
		Merged later = SharedTiles.merge(bob, layer);
		assertMerged(bob, layer, later);
		assertFalse(later.fromFriend(far));
		assertEquals(12, later.raw().height[far]);
		assertEquals(m.friendColumns() - 1, later.friendColumns());

		// and alice sharing the same ground again changes nothing he has walked
		out.changedRegion(DIM, alice);
		for (Message msg : TileFixtures.drainAll(out, 1000)) {
			in.receive("alice", msg.topic(), msg.payload());
		}
		assertMerged(bob, layer, SharedTiles.merge(bob, layer));
		assertEquals(12, bob.height[far]);
	}

	@Test
	void friendColumnsKeepRockAndUseAParallelMarkNotAFlagBit() {
		SharedLayer layer = new SharedLayer(DIM, 0, 0);
		int slot = layer.senderSlot("alice");
		layer.put(0, (short) 5, (char) 1, (char) 1, 0, RegionRaw.ROCK | 0x40, 0, slot);
		Merged m = SharedTiles.merge(new RegionRaw(0, 0), layer);
		assertEquals(RegionRaw.KNOWN | RegionRaw.ROCK, m.raw().flags[0], "only KNOWN and ROCK, no unknown bits");
		assertTrue(m.fromFriend(0));
		assertEquals("alice", m.sender(0));
	}
}
