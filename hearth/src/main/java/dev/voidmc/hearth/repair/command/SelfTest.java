package dev.voidmc.hearth.repair.command;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.Damage;
import dev.voidmc.hearth.repair.DamageRules;
import dev.voidmc.hearth.repair.FireWatch;
import dev.voidmc.hearth.repair.Inspector;
import dev.voidmc.hearth.repair.PlotLocator;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairQueue;
import dev.voidmc.hearth.repair.RepairRuntime;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * The dev self-test ({@code /hearth repair selftest}, or {@code -Dhearth.dev.repair.selftest=true} on a dev server): on
 * real buildings of a real settlement, breaks blocks the ways the game breaks them — {@code destroyBlock}, an explosion,
 * a fire left to burn, and blocks taken silently that only an inspection can find — then checks that each was recorded
 * with the right cause, lets abstract repair mend them and the applier write them back, and verifies every block against
 * the blueprint. It logs {@code [hearth repair]} lines with counts and timings; {@code -Dhearth.dev.repair.stop=true}
 * stops the server when it is done.
 */
public final class SelfTest {
	private enum Phase { WAIT_SETTLEMENT, LOAD, BASELINE, DAMAGE, BURN, INSPECT, MEND, APPLY, VERIFY, DONE }

	private static @Nullable SelfTest running;

	private final ServerLevel level;
	private final long settlementId;
	private final List<Integer> plots = new ArrayList<>();
	private final LongOpenHashSet forced = new LongOpenHashSet();
	private Phase phase;
	private long phaseStart;
	private final LongArrayList baseline = new LongArrayList();
	private final IntArrayList baselineStates = new IntArrayList();
	private final LongArrayList broken = new LongArrayList();
	private int destroyed;
	private int exploded;
	private int silent;
	private int fires;
	private long recordedBefore;
	private int fireGameRule = Integer.MIN_VALUE;

	private SelfTest(ServerLevel level, long settlementId, Phase phase, long now) {
		this.level = level;
		this.settlementId = settlementId;
		this.phase = phase;
		this.phaseStart = now;
	}

	/** Starts the dev self-test from a system property, once a settlement with buildings exists. */
	public static void maybeStart(MinecraftServer server) {
		if (Boolean.getBoolean("hearth.dev.repair.selftest") && running == null) {
			running = new SelfTest(server.overworld(), 0, Phase.WAIT_SETTLEMENT, 0);
			log("waiting for a settlement with buildings");
		}
	}

	/** Starts the test in a settlement; returns why not, or null. */
	public static @Nullable String start(RepairRuntime rt, ServerLevel level, HearthWorld world, Settlement s) {
		if (running != null && running.phase != Phase.DONE && running.phase != Phase.WAIT_SETTLEMENT) {
			return "a self-test is already running";
		}
		SelfTest t = new SelfTest(level, s.id(), Phase.LOAD, rt.now());
		String why = t.choose(rt, world, s);
		if (why != null) {
			return why;
		}
		running = t;
		return null;
	}

	public static void tick(RepairRuntime rt) {
		SelfTest t = running;
		if (t == null || t.phase == Phase.DONE) {
			return;
		}
		try {
			t.step(rt);
		} catch (RuntimeException e) {
			log("failed: " + e);
			RepairModule.LOG.error("[hearth repair] self-test failed", e);
			t.finish(rt);
		}
	}

	private static void log(String line) {
		RepairModule.LOG.info("[hearth repair] {}", line);
	}

	private @Nullable String choose(RepairRuntime rt, HearthWorld world, Settlement s) {
		PlotLocator.Source source = PlotLocator.services(world);
		List<PlotView> candidates = new ArrayList<>();
		for (PlotView p : s.plots()) {
			String path = p.spec().archetype().getPath();
			if (p.state() == PlotState.BUILT && p.owner() == null && !path.startsWith("infra/") && !path.startsWith("landscape/") && !path.startsWith("farm/")
				&& !path.startsWith("extract/")) {
				candidates.add(p);
			}
		}
		candidates.sort(Comparator.comparingDouble(p -> p.spec().origin().distSqr(s.center())));
		for (PlotView p : candidates) {
			int[] solid = {0};
			rt.locator.forEachCell(s, source, p, (pos, stage, expected) -> {
				if (!DamageRules.volatileBlock(expected)) {
					solid[0]++;
				}
			});
			if (solid[0] >= 40) {
				this.plots.add(p.id());
				if (this.plots.size() == 3) {
					break;
				}
			}
		}
		if (this.plots.isEmpty()) {
			return s.name() + " has no built building of 40 blocks or more to test on";
		}
		for (int id : this.plots) {
			PlotView p = s.plot(id);
			var o = p.spec().origin();
			for (int cx = SectionPos.blockToSectionCoord(o.getX() - 4); cx <= SectionPos.blockToSectionCoord(o.getX() + p.spec().sizeX() + 4); cx++) {
				for (int cz = SectionPos.blockToSectionCoord(o.getZ() - 4); cz <= SectionPos.blockToSectionCoord(o.getZ() + p.spec().sizeZ() + 4); cz++) {
					if (this.forced.add(dev.voidmc.hearth.repair.command.SelfTest.key(cx, cz))) {
						this.level.setChunkForced(cx, cz, true);
					}
				}
			}
		}
		log(String.format(Locale.ROOT, "testing in %s (#%d) on plots %s; %d chunks forced", s.name(), s.id(), this.plots, this.forced.size()));
		return null;
	}

	static long key(int x, int z) {
		return x & 0xFFFF_FFFFL | (z & 0xFFFF_FFFFL) << 32;
	}

	private void next(Phase p, long now) {
		this.phase = p;
		this.phaseStart = now;
	}

	private void step(RepairRuntime rt) {
		long now = rt.now();
		HearthWorld world = HearthWorld.of(this.level);
		if (world == null) {
			return;
		}
		if (this.phase == Phase.WAIT_SETTLEMENT) {
			if (now % 100 != 0) {
				return;
			}
			BlockPos spawn = this.level.getRespawnData().pos();
			Settlement best = null;
			for (Settlement s : world.settlements()) {
				boolean built = s.plots().stream().anyMatch(p -> p.state() == PlotState.BUILT && p.spec().archetype().getPath().startsWith("house/"));
				if (built && (best == null || s.center().distSqr(spawn) < best.center().distSqr(spawn))) {
					best = s;
				}
			}
			if (best != null) {
				String why = start(rt, this.level, world, best);
				if (why != null) {
					log(why);
				}
			}
			return;
		}
		Settlement s = world.settlement(this.settlementId);
		if (s == null) {
			log("the settlement is gone");
			finish(rt);
			return;
		}
		PlotLocator.Source source = PlotLocator.services(world);
		RepairQueue q = s.data(RepairModule.QUEUE);
		switch (this.phase) {
			case LOAD -> {
				// the sim's applier writes whatever construction the chunks were missing
				if (now - this.phaseStart >= 200) {
					next(Phase.BASELINE, now);
				}
			}
			case BASELINE -> {
				for (int id : this.plots) {
					PlotView p = s.plot(id);
					rt.locator.forEachCell(s, source, p, (pos, stage, expected) -> {
						BlockPos bp = BlockPos.of(pos);
						if (!DamageRules.volatileBlock(expected) && this.level.isLoaded(bp) && DamageRules.sameBlock(expected, this.level.getBlockState(bp))) {
							this.baseline.add(pos);
							this.baselineStates.add(Block.getId(expected));
						}
					});
				}
				benchLookups(rt, world, s);
				log(String.format(Locale.ROOT, "baseline: %d blueprint blocks stand as built; queue holds %d already", this.baseline.size(), q.size()));
				next(Phase.DAMAGE, now);
			}
			case DAMAGE -> {
				damage(rt, world, s, q);
				next(Phase.BURN, now);
			}
			case BURN -> {
				FireWatch.Episode e = rt.fires.episode(s.id());
				long waited = now - this.phaseStart;
				if (this.fires == 0 || waited > 200 && e == null || waited > 1800) {
					int byFire = 0;
					for (Damage d : q.all()) {
						byFire += d.cause == dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause.FIRE ? 1 : 0;
					}
					log(String.format(Locale.ROOT, "fire: %d flames set, after %d ticks %s; %d blocks queued as burned; watch: %s", this.fires, waited,
						e == null ? "out" : "still burning (" + e.flames() + " flames)", byFire, rt.fires.stats()));
					// put out whatever still burns, so the rest of the test is not raced by it
					if (e != null) {
						e.douse(e.origin, 32);
					}
					restoreFireRule();
					next(Phase.INSPECT, now);
				}
			}
			case INSPECT -> {
				int before = q.size();
				int found = 0;
				long t0 = System.nanoTime();
				int cells = 0;
				for (int id : this.plots) {
					PlotView p = s.plot(id);
					int[] counts = new int[3];
					int added = Inspector.inspectNow(rt, this.level, world, s, p, counts);
					cells += counts[0];
					found += Math.max(0, added);
				}
				double ms = (System.nanoTime() - t0) / 1e6;
				log(String.format(Locale.ROOT, "inspection: %d cells in %.2f ms (%.2f µs a cell); found %d the events missed (%d taken silently); queue %d -> %d",
					cells, ms, cells == 0 ? 0 : ms * 1000 / cells, found, this.silent, before, q.size()));
				next(Phase.MEND, now);
			}
			case MEND -> {
				int mended = rt.abstracts.mendAll(world, s, true);
				log("abstract repair: " + mended + " blocks mended in the record; the applier writes them into the loaded chunks");
				next(Phase.APPLY, now);
			}
			case APPLY -> {
				boolean waiting = q.all().stream().anyMatch(d -> d.has(Damage.MENDED));
				if (!waiting || now - this.phaseStart > 600) {
					next(Phase.VERIFY, now);
				}
			}
			case VERIFY -> {
				int ok = 0;
				int wrong = 0;
				List<String> samples = new ArrayList<>();
				for (int i = 0; i < this.baseline.size(); i++) {
					BlockPos p = BlockPos.of(this.baseline.getLong(i));
					BlockState expected = Block.stateById(this.baselineStates.getInt(i));
					BlockState now2 = this.level.getBlockState(p);
					if (DamageRules.sameBlock(expected, now2)) {
						ok++;
					} else {
						wrong++;
						if (samples.size() < 6) {
							samples.add(p.toShortString() + " wants " + expected.getBlock().getName().getString() + " has " + now2.getBlock().getName().getString());
						}
					}
				}
				int brokenBack = 0;
				for (int i = 0; i < this.broken.size(); i++) {
					long p = this.broken.getLong(i);
					int at = this.baseline.indexOf(p);
					if (at >= 0 && DamageRules.sameBlock(Block.stateById(this.baselineStates.getInt(at)), this.level.getBlockState(BlockPos.of(p)))) {
						brokenBack++;
					}
				}
				log(String.format(Locale.ROOT, "verify: %d of %d baseline blocks stand as the blueprint has them (%d wrong%s); %d of %d blocks the test broke are back; queue left %d",
					ok, this.baseline.size(), wrong, samples.isEmpty() ? "" : ": " + String.join("; ", samples), brokenBack, this.broken.size(), q.size()));
				for (String line : RepairCommands.lines(rt)) {
					log(line);
				}
				finish(rt);
			}
			default -> {
			}
		}
	}

	private void damage(RepairRuntime rt, HearthWorld world, Settlement s, RepairQueue q) {
		RandomSource random = RandomSource.create(20261003L);
		this.recordedBefore = q.size();
		List<Long> a = baselineOf(s, this.plots.getFirst(), world, rt);
		List<Long> b = baselineOf(s, this.plots.get(Math.min(1, this.plots.size() - 1)), world, rt);
		List<Long> c = baselineOf(s, this.plots.getLast(), world, rt);
		// 1. destroyBlock (a wither's bite, a command, a torch losing its wall): cause OTHER
		java.util.Collections.shuffle(a, new java.util.Random(1));
		int qa = q.size();
		for (int i = 0; i < Math.min(12, a.size()); i++) {
			BlockPos p = BlockPos.of(a.get(i));
			if (this.level.destroyBlock(p, false)) {
				this.destroyed++;
				this.broken.add(p.asLong());
			}
		}
		log(String.format(Locale.ROOT, "destroyBlock: %d broken, %d queued", this.destroyed, q.size() - qa));
		// 2. taken silently (no event sees it): only an inspection can find these
		int qs = q.size();
		for (int i = 12; i < Math.min(18, a.size()); i++) {
			BlockPos p = BlockPos.of(a.get(i));
			if (!this.level.getBlockState(p).isAir()) {
				this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				this.silent++;
				this.broken.add(p.asLong());
			}
		}
		log(String.format(Locale.ROOT, "silent: %d taken with no event, %d queued (expected 0)", this.silent, q.size() - qs));
		// 3. an explosion against a wall of the second building
		int qe = q.size();
		if (!b.isEmpty()) {
			BlockPos at = BlockPos.of(b.get(b.size() / 2));
			LongArrayList near = new LongArrayList();
			for (long p : b) {
				if (BlockPos.of(p).distSqr(at) <= 9) {
					near.add(p);
				}
			}
			long t0 = System.nanoTime();
			this.level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2.5F, Level.ExplosionInteraction.TNT);
			double ms = (System.nanoTime() - t0) / 1e6;
			for (int i = 0; i < near.size(); i++) {
				BlockPos p = BlockPos.of(near.getLong(i));
				if (this.level.getBlockState(p).isAir()) {
					this.exploded++;
					this.broken.add(p.asLong());
				}
			}
			log(String.format(Locale.ROOT, "explosion at %s: about %d building blocks gone, %d queued, %.2f ms for the blast with tracking", at.toShortString(),
				this.exploded, q.size() - qe, ms));
		}
		// 4. fire against the timber of the third building (fire spreads without a player near for the test)
		this.fireGameRule = this.level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		this.level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, this.level.getServer());
		for (long p : c) {
			if (this.fires >= 5) {
				break;
			}
			BlockPos pos = BlockPos.of(p);
			if (!this.level.getBlockState(pos).ignitedByLava()) {
				continue;
			}
			for (Direction d : Direction.values()) {
				BlockPos f = pos.relative(d);
				if (this.level.getBlockState(f).isAir()) {
					BlockState fire = BaseFireBlock.getState(this.level, f);
					if (fire.canSurvive(this.level, f)) {
						this.level.setBlock(f, fire, Block.UPDATE_ALL);
						this.fires++;
						break;
					}
				}
			}
		}
		log(String.format(Locale.ROOT, "fire: %d flames set against timber", this.fires));
		if (this.fires == 0) {
			restoreFireRule();
		}
	}

	private List<Long> baselineOf(Settlement s, int plotId, HearthWorld world, RepairRuntime rt) {
		PlotView p = s.plot(plotId);
		List<Long> out = new ArrayList<>();
		if (p == null) {
			return out;
		}
		LongOpenHashSet base = new LongOpenHashSet(this.baseline);
		rt.locator.forEachCell(s, PlotLocator.services(world), p, (pos, stage, expected) -> {
			if (base.contains(pos) && !this.broken.contains(pos)) {
				out.add(pos);
			}
		});
		out.sort(Long::compare);
		return out;
	}

	/** How fast the position index answers, cold and warm, over random positions in and round the test buildings. */
	private void benchLookups(RepairRuntime rt, HearthWorld world, Settlement s) {
		RandomSource random = RandomSource.create(7);
		var b = s.bounds();
		int n = 20000;
		BlockPos[] ps = new BlockPos[n];
		for (int i = 0; i < n; i++) {
			ps[i] = new BlockPos(b.minX() + random.nextInt(Math.max(1, b.getXSpan())), s.center().getY() - 4 + random.nextInt(24),
				b.minZ() + random.nextInt(Math.max(1, b.getZSpan())));
		}
		rt.locator.invalidate(s.id());
		long t0 = System.nanoTime();
		int hits = 0;
		for (BlockPos p : ps) {
			hits += rt.locator.locate(world, p, rt.now()) != null ? 1 : 0;
		}
		long cold = System.nanoTime() - t0;
		t0 = System.nanoTime();
		for (BlockPos p : ps) {
			rt.locator.locate(world, p, rt.now());
		}
		long warm = System.nanoTime() - t0;
		log(String.format(Locale.ROOT, "index: %d lookups over the town, %d on a building block; cold %.2f µs, warm %.3f µs a lookup; %s", n, hits,
			cold / 1e3 / n, warm / 1e3 / n, rt.locator.stats()));
	}

	private void restoreFireRule() {
		if (this.fireGameRule != Integer.MIN_VALUE) {
			this.level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, this.fireGameRule, this.level.getServer());
			this.fireGameRule = Integer.MIN_VALUE;
		}
	}

	private void finish(RepairRuntime rt) {
		restoreFireRule();
		for (long k : this.forced) {
			this.level.setChunkForced((int) k, (int) (k >>> 32), false);
		}
		this.forced.clear();
		this.phase = Phase.DONE;
		log("done");
		if (Boolean.getBoolean("hearth.dev.repair.stop")) {
			MinecraftServer server = rt.server;
			server.execute(() -> server.halt(false));
		}
	}
}
