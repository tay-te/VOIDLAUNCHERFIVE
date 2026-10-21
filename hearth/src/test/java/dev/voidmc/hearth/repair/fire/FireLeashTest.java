package dev.voidmc.hearth.repair.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * The fire leash (docs/LIVING_WORLD_2.md §3.8, {@code FireLeashTest}): a fire lit on the roof of a timber house in a row
 * of timber houses, spreading as vanilla fire does (to air beside fuel, up to four above and one below, a few times a
 * minute; eating its fuel), never leaves the plot's box + 2 in 2 000 ticks with the leash on — and without it, does.
 */
class FireLeashTest {
	static final int W = 64;
	static final int H = 24;

	/** A street of timber houses touching wall to wall, and a fire on one roof. */
	static final class Street implements FireLeash.Cells {
		final boolean[] fuel = new boolean[W * H * W];
		final Map<Long, Integer> fire = new HashMap<>();
		final Set<Long> burnt = new HashSet<>();
		final Random random;
		int snuffed;
		int sectionsLooked;

		Street(long seed) {
			this.random = new Random(seed);
			for (int[] house : new int[][]{{10, 19}, {20, 29}, {32, 41}, {0, 9}}) {
				for (int x = house[0]; x <= house[1]; x++) {
					for (int z = 10; z <= 19; z++) {
						for (int y = 0; y <= 8; y++) {
							this.fuel[i(x, y, z)] = true;
						}
					}
				}
			}
		}

		static int i(int x, int y, int z) {
			return (x * H + y) * W + z;
		}

		static boolean in(int x, int y, int z) {
			return x >= 0 && x < W && y >= 0 && y < H && z >= 0 && z < W;
		}

		boolean fuelAt(int x, int y, int z) {
			return in(x, y, z) && this.fuel[i(x, y, z)];
		}

		boolean nextToFuel(int x, int y, int z) {
			return fuelAt(x + 1, y, z) || fuelAt(x - 1, y, z) || fuelAt(x, y + 1, z) || fuelAt(x, y - 1, z) || fuelAt(x, y, z + 1) || fuelAt(x, y, z - 1);
		}

		void light(int x, int y, int z) {
			this.fire.put(BlockPos.asLong(x, y, z), this.random.nextInt(10));
		}

		/** One tick of fire: each flame acts every 30–40 ticks of its life (as the fire block's own tick), then burns out. */
		void tick() {
			Map<Long, Integer> next = new HashMap<>();
			for (Map.Entry<Long, Integer> e : this.fire.entrySet()) {
				int age = e.getValue() + 1;
				long p = e.getKey();
				int x = BlockPos.getX(p);
				int y = BlockPos.getY(p);
				int z = BlockPos.getZ(p);
				if (age > 160 + this.random.nextInt(80) || !nextToFuel(x, y, z)) {
					continue; // burnt out, or nothing left to burn
				}
				next.put(p, age);
				if (age % (30 + (int) (p & 7)) != 0) {
					continue;
				}
				// eat a neighbour
				for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					int nx = x + d[0];
					int ny = y + d[1];
					int nz = z + d[2];
					if (fuelAt(nx, ny, nz) && this.random.nextInt(4) == 0) {
						this.fuel[i(nx, ny, nz)] = false;
						this.burnt.add(BlockPos.asLong(nx, ny, nz));
						next.putIfAbsent(BlockPos.asLong(nx, ny, nz), 0);
					}
				}
				// spread to air beside fuel: one block round, one below, up to four above
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dy = -1; dy <= 4; dy++) {
							int nx = x + dx;
							int ny = y + dy;
							int nz = z + dz;
							if (in(nx, ny, nz) && !fuelAt(nx, ny, nz) && nextToFuel(nx, ny, nz) && this.random.nextInt(dy > 1 ? 6 : 3) == 0) {
								next.putIfAbsent(BlockPos.asLong(nx, ny, nz), 0);
							}
						}
					}
				}
			}
			this.fire.clear();
			this.fire.putAll(next);
		}

		@Override
		public boolean mayBurn(int sx, int sy, int sz) {
			this.sectionsLooked++;
			for (long p : this.fire.keySet()) {
				if (BlockPos.getX(p) >> 4 == sx && BlockPos.getY(p) >> 4 == sy && BlockPos.getZ(p) >> 4 == sz) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean fire(int x, int y, int z) {
			return this.fire.containsKey(BlockPos.asLong(x, y, z));
		}

		@Override
		public void snuff(int x, int y, int z) {
			if (this.fire.remove(BlockPos.asLong(x, y, z)) != null) {
				this.snuffed++;
			}
		}
	}

	static final BoundingBox PLOT = new BoundingBox(10, 0, 10, 19, 9, 19);

	static int outside(Street s, BoundingBox box) {
		int n = 0;
		for (long p : s.fire.keySet()) {
			if (!box.isInside(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) {
				n++;
			}
		}
		return n;
	}

	static int burntOutside(Street s, BoundingBox box) {
		int n = 0;
		for (long p : s.burnt) {
			if (!box.isInside(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) {
				n++;
			}
		}
		return n;
	}

	@Test
	void aFireNeverLeavesItsBoxAndTwoIn2000Ticks() {
		BoundingBox allowed = FireLeash.allowed(PLOT);
		BoundingBox charred = PLOT.inflatedBy(FireLeash.MARGIN + 1);
		for (long seed = 1; seed <= 20; seed++) {
			Street s = new Street(seed);
			s.light(14, 9, 14);
			s.light(15, 9, 15);
			s.light(13, 9, 16);
			for (int t = 1; t <= 2000; t++) {
				s.tick();
				if (t % FireLeash.PASS_TICKS == 0) {
					FireLeash.pass(s, PLOT);
					assertEquals(0, outside(s, allowed), "seed " + seed + " tick " + t + ": a flame beyond the box + 2 after the leash's pass");
				}
				// a stray flame is caught before it can act: it never reaches past the leash's own reach
				assertEquals(0, outside(s, PLOT.inflatedBy(FireLeash.MARGIN + FireLeash.REACH)), "seed " + seed + " tick " + t);
			}
			assertEquals(0, burntOutside(s, charred), "seed " + seed + ": the next house burnt");
		}
	}

	@Test
	void withoutTheLeashItSpreadsToTheNextHouse() {
		int spread = 0;
		for (long seed = 1; seed <= 20; seed++) {
			Street s = new Street(seed);
			s.light(14, 9, 14);
			s.light(15, 9, 15);
			s.light(13, 9, 16);
			for (int t = 1; t <= 2000; t++) {
				s.tick();
			}
			if (burntOutside(s, PLOT.inflatedBy(FireLeash.MARGIN + 1)) > 0) {
				spread++;
			}
		}
		assertTrue(spread >= 10, "the test street must be one a loose fire burns through: " + spread + " of 20");
	}

	@Test
	void aPassLooksOnlyWhereThePalettesSayFire() {
		Street s = new Street(3);
		FireLeash.pass(s, PLOT);
		assertEquals(0, s.snuffed);
		assertTrue(s.sectionsLooked <= 2 * 2 * 2 * 2, "sections looked at: " + s.sectionsLooked);
		s.light(25, 3, 25);
		s.light(14, 9, 14);
		assertEquals(1, FireLeash.pass(s, PLOT));
		assertFalse(s.fire(25, 3, 25));
		assertTrue(s.fire(14, 9, 14));
		assertEquals(1, FireLeash.putOut(s, PLOT));
	}
}
