package dev.voidmc.hearth.repair.command;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.Damage;
import dev.voidmc.hearth.repair.DamageRules;
import dev.voidmc.hearth.repair.FireWatch;
import dev.voidmc.hearth.repair.Inspector;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.repair.RepairConfig;
import dev.voidmc.hearth.repair.Litter;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
	private enum Phase { WAIT_SETTLEMENT, LOAD, BASELINE, DAMAGE, BURN, FIRE_OUT, INSPECT, MEND, APPLY, VERIFY, FOLK_MEND, FOLK_FIRE, FOLK_INSPECT, DONE }

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
	private int playerBroke;
	// the folk part: stand-in folk driven by the repair behaviours (only with no NPC module present)
	private final List<DevFolk> folk = new ArrayList<>();
	private final LongArrayList folkTargets = new LongArrayList();
	private @Nullable RepairConfig savedConfig;
	private boolean embodiedByTest;
	private int maxScaffold;
	private boolean sawFolkFire;
	private int doused;
	private long litterAt = Long.MIN_VALUE;
	/** Events seen while a test runs (DAMAGED events and their blocks, fires started and put out, plots repaired). */
	private static final int[] EVENTS = new int[5];

	private SelfTest(ServerLevel level, long settlementId, Phase phase, long now) {
		this.level = level;
		this.settlementId = settlementId;
		this.phase = phase;
		this.phaseStart = now;
	}

	/** Counts the module's events while a test runs (registered once, at mod init; idle otherwise). */
	public static void listen() {
		dev.voidmc.hearth.api.SettlementEvents.DAMAGED.register((w, s, p, cause, n) -> {
			if (running != null && running.phase != Phase.DONE) {
				EVENTS[0]++;
				EVENTS[1] += n;
			}
		});
		dev.voidmc.hearth.repair.RepairEvents.FIRE_STARTED.register((w, s, at) -> EVENTS[2]++);
		dev.voidmc.hearth.repair.RepairEvents.FIRE_OUT.register((w, s, burned, helpers) -> EVENTS[3]++);
		dev.voidmc.hearth.repair.RepairEvents.REPAIRED.register((w, s, plot, blocks) -> EVENTS[4]++);
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
						byFire += d.cause == Cause.FIRE ? 1 : 0;
					}
					log(String.format(Locale.ROOT, "fire: %d flames set, after %d ticks %s; %d blocks queued as burned; watch: %s", this.fires, waited,
						e == null ? "out" : "still burning (" + e.flames() + " flames)", byFire, rt.fires.stats()));
					// put out whatever still burns, so the rest of the test is not raced by it; then wait for the watch to call it out
					if (e != null) {
						e.douse(e.origin, 32);
					}
					restoreFireRule();
					next(Phase.FIRE_OUT, now);
				}
			}
			case FIRE_OUT -> {
				FireWatch.Episode e = rt.fires.episode(s.id());
				if (e != null && now - this.phaseStart < 400) {
					if (now % 20 == 0 && e.flames() > 0) {
						e.douse(e.origin, 32);
					}
					return;
				}
				log(String.format(Locale.ROOT, "fire watch: %s after %d ticks; events: %d FIRE_STARTED, %d FIRE_OUT", e == null ? "called out" : "still burning",
					now - this.phaseStart, EVENTS[2], EVENTS[3]));
				next(Phase.INSPECT, now);
			}
			case INSPECT -> {
				int before = q.size();
				int found = 0;
				long t0 = System.nanoTime();
				int cells = 0;
				long stepNanos = 0;
				for (int id : this.plots) {
					PlotView p = s.plot(id);
					Inspector.Round r = Inspector.begin(rt, world, s, p);
					if (r == null) {
						continue;
					}
					r.trustChunks = true;
					long t1 = System.nanoTime();
					while (!r.done()) {
						Inspector.step(rt, this.level, world, s, r, 20);
					}
					stepNanos += System.nanoTime() - t1;
					cells += r.size();
					found += Math.max(0, Inspector.commit(rt, this.level, world, s, r));
				}
				double ms = (System.nanoTime() - t0) / 1e6;
				log(String.format(Locale.ROOT,
					"inspection: %d cells, %.2f ms in all, comparing %.2f µs a cell (20 a tick: %.3f ms a tick); found %d the events missed (%d taken silently); queue %d -> %d",
					cells, ms, cells == 0 ? 0 : stepNanos / 1e3 / cells, cells == 0 ? 0 : stepNanos / 1e6 / cells * 20, found, this.silent, before, q.size()));
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
				if (this.litterAt != Long.MIN_VALUE) {
					log("litter in the doorway " + (this.level.getBlockState(BlockPos.of(this.litterAt)).isAir() ? "cleared" : "STILL THERE"));
				}
				log(String.format(Locale.ROOT, "events: %d DAMAGED for %d blocks, %d REPAIRED", EVENTS[0], EVENTS[1], EVENTS[4]));
				for (String line : RepairCommands.lines(rt)) {
					log(line);
				}
				if (HearthServices.npc() == null && !Boolean.getBoolean("hearth.dev.repair.nofolk")) {
					startFolk(rt, world, s, q, now);
				} else {
					finish(rt);
				}
			}
			case FOLK_MEND -> {
				tickFolk(List.of(RepairModule.BRIGADE, RepairModule.MEND, RepairModule.INSPECT));
				if ((now - this.phaseStart) % 200 == 0) {
					DevFolk b = this.folk.getFirst();
					log("  builder at " + b.entity().blockPosition().toShortString() + ": " + RepairModule.MEND.describe(b.resident().id));
				}
				this.maxScaffold = Math.max(this.maxScaffold, q.scaffoldCount());
				int left = 0;
				for (int i = 0; i < this.folkTargets.size(); i++) {
					left += q.get(this.folkTargets.getLong(i)) != null ? 1 : 0;
				}
				long waited = now - this.phaseStart;
				if (left == 0 && this.folk.getFirst().running() == null || waited > 3600) {
					int back = 0;
					for (int i = 0; i < this.folkTargets.size(); i++) {
						long p = this.folkTargets.getLong(i);
						int at = this.baseline.indexOf(p);
						back += at >= 0 && DamageRules.sameBlock(Block.stateById(this.baselineStates.getInt(at)), this.level.getBlockState(BlockPos.of(p))) ? 1 : 0;
					}
					log(String.format(Locale.ROOT, "folk mending: %d of %d blocks put back by the builder in %d ticks (%d still queued); most scaffold up at once %d, left up %d; said %s",
						back, this.folkTargets.size(), waited, left, this.maxScaffold, q.scaffoldCount(), this.folk.getFirst().said));
					startFolkFire(rt, world, s, now);
				}
			}
			case FOLK_FIRE -> {
				tickFolk(List.of(RepairModule.BRIGADE, RepairModule.MEND));
				FireWatch.Episode e = rt.fires.episode(s.id());
				long waited = now - this.phaseStart;
				if (e != null) {
					this.sawFolkFire = true;
					this.doused = Math.max(this.doused, RepairModule.BRIGADE.running());
					if (waited % 200 == 0) {
						StringBuilder b = new StringBuilder();
						for (DevFolk f : this.folk) {
							b.append(f.resident().givenName).append(": ").append(RepairModule.BRIGADE.describe(f.resident().id)).append("; ");
						}
						log(String.format(Locale.ROOT, "  fire: %d flames known, %d water sources; %s", e.flames(), e.water(RepairModule.config().fire().waterSearch()).size(), b));
					}
				}
				// the watch starts the episode on the first flame's tick, then calls it out after a quiet spell
				if (this.sawFolkFire && e == null || !this.sawFolkFire && waited > 400 || waited > 2400) {
					int lines = 0;
					for (DevFolk f : this.folk) {
						lines += f.said.size();
					}
					log(String.format(Locale.ROOT, "bucket brigade: fire %s after %d ticks (%d folk near, %d in the brigade at most, %d lines said in all); %s",
						!this.sawFolkFire ? "NEVER TOOK HOLD" : e == null ? "out" : "STILL BURNING", waited, this.folk.size(), this.doused, lines, rt.fires.stats()));
					if (e != null) {
						e.douse(e.origin, 32);
					}
					restoreFireRule();
					startFolkInspect(rt, world, s, now);
				}
			}
			case FOLK_INSPECT -> {
				DevFolk builder = this.folk.getFirst();
				tickFolk(List.of(RepairModule.MEND, RepairModule.INSPECT));
				if ((now - this.phaseStart) % 200 == 0) {
					var running = builder.running();
					log("  builder at " + builder.entity().blockPosition().toShortString() + ": " + (running == null ? "routine" : running.id().getPath()) + "; "
						+ RepairModule.MEND.describe(builder.resident().id) + "; queue " + q.size());
				}
				long waited = now - this.phaseStart;
				int back = 0;
				for (int i = 0; i < this.folkTargets.size(); i++) {
					long p = this.folkTargets.getLong(i);
					int at = this.baseline.indexOf(p);
					back += at >= 0 && DamageRules.sameBlock(Block.stateById(this.baselineStates.getInt(at)), this.level.getBlockState(BlockPos.of(p))) ? 1 : 0;
				}
				if (back == this.folkTargets.size() && builder.running() == null || waited > 4800) {
					log(String.format(Locale.ROOT, "inspection round: %d of %d silently taken blocks found and put back in %d ticks; queue left %d; said %s", back,
						this.folkTargets.size(), waited, q.size(), builder.said));
					finish(rt);
				}
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
		// 2b. a player breaks blocks, puts one back, and leaves cobblestone in a doorway
		players(rt, world, s, q, a);
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

	private void players(RepairRuntime rt, HearthWorld world, Settlement s, RepairQueue q, List<Long> a) {
		FakePlayer player = FakePlayer.get(this.level, new GameProfile(new java.util.UUID(0x5EA1L, 0x7E57L), "RepairTester"));
		int qp = q.size();
		List<BlockPos> broke = new ArrayList<>();
		for (int i = 18; i < Math.min(22, a.size()); i++) {
			BlockPos p = BlockPos.of(a.get(i));
			player.setPos(p.getX() + 0.5, p.getY() + 1, p.getZ() + 2.5);
			if (player.gameMode.destroyBlock(p)) {
				broke.add(p);
				this.playerBroke++;
				this.broken.add(p.asLong());
			}
		}
		int byPlayer = 0;
		for (BlockPos p : broke) {
			Damage d = q.get(p.asLong());
			if (d != null && d.cause == Cause.PLAYER && player.getUUID().equals(d.offender)) {
				byPlayer++;
			}
		}
		// the player puts the first one back with the right block
		boolean putBack = false;
		if (!broke.isEmpty()) {
			BlockPos p = broke.getFirst();
			int at = this.baseline.indexOf(p.asLong());
			BlockState want = Block.stateById(this.baselineStates.getInt(at));
			ItemStack stack = new ItemStack(want.getBlock().asItem());
			if (stack.getItem() instanceof BlockItem item) {
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false)));
				putBack = q.get(p.asLong()) == null && DamageRules.sameBlock(want, this.level.getBlockState(p));
			}
		}
		log(String.format(Locale.ROOT, "player: %d broken, %d queued with the player as offender; one put back by hand: %s", this.playerBroke, byPlayer,
			putBack ? "mended, out of the queue" : "NOT counted"));
		// litter: cobblestone in a way in
		PlotLocator.Source source = PlotLocator.services(world);
		for (int id : this.plots) {
			PlotView plot = s.plot(id);
			long[] spot = {Long.MIN_VALUE};
			rt.locator.forEachCell(s, source, plot, (pos, stage, expected) -> {
				BlockPos bp = BlockPos.of(pos);
				if (spot[0] == Long.MIN_VALUE && expected.isAir() && this.level.getBlockState(bp).isAir() && Litter.blocksWay(rt.locator, s, source, plot, bp)) {
					spot[0] = pos;
				}
			});
			if (spot[0] != Long.MIN_VALUE) {
				BlockPos p = BlockPos.of(spot[0]);
				ItemStack stack = new ItemStack(Items.COBBLESTONE);
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
					new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false)));
				Damage d = q.get(p.asLong());
				this.litterAt = p.asLong();
				log("litter: cobblestone left at " + p.toShortString() + " in a way in of plot " + id + ": "
					+ (d != null && d.has(Damage.LITTER) ? "queued to clear" : "NOT queued"));
				break;
			}
		}
		log(String.format(Locale.ROOT, "player phase: queue %d -> %d", qp, q.size()));
	}

	private void tickFolk(List<dev.voidmc.hearth.api.NpcService.FolkBehaviour> behaviours) {
		for (DevFolk f : this.folk) {
			f.tick(behaviours);
		}
	}

	private void command(String command) {
		MinecraftServer server = this.level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withLevel(this.level), command);
	}

	/** A spot a folk can stand on near a column (the surface). */
	private BlockPos surface(int x, int z) {
		int y = this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos(x, y, z);
	}

	private void startFolk(RepairRuntime rt, HearthWorld world, Settlement s, RepairQueue q, long now) {
		// working hours, the settlement embodied, abstract repair held off so only the folk mend
		command("time set 1500");
		if (!s.embodied()) {
			command("hearth sim embody " + s.id());
			this.embodiedByTest = true;
		}
		RepairConfig c = RepairModule.config();
		this.savedConfig = c;
		RepairConfig.Abstract a = c.abstractRepair();
		RepairModule.setConfig(new RepairConfig(c.tracking(), c.queue(), new RepairConfig.Abstract(false, a.blocksPerBuilderHour(), a.intervalTicks(),
			a.maxCatchUpDays(), a.applyMaxBlocks(), a.applyBudgetMs()), c.builders(), c.inspection(), c.fire(), c.law(), c.owners()));
		// the builder's work: the highest blocks of the first building (a scaffold job) and a few more
		List<Long> cells = baselineOf(s, this.plots.getFirst(), world, rt);
		cells.sort(Comparator.comparingInt((Long p) -> BlockPos.getY(p)).reversed());
		List<Long> picks = new ArrayList<>(cells.subList(0, Math.min(3, cells.size())));
		for (int i = cells.size() / 2; i < cells.size() && picks.size() < 8; i += 3) {
			picks.add(cells.get(i));
		}
		for (long p : picks) {
			if (this.level.destroyBlock(BlockPos.of(p), false)) {
				this.folkTargets.add(p);
			}
		}
		PlotView plot = s.plot(this.plots.getFirst());
		BlockPos o = plot.spec().origin();
		DevFolk builder = DevFolk.spawn(this.level, s, dev.voidmc.hearth.api.JobType.BUILDER, surface(o.getX() - 2, o.getZ() - 2), "Wynn");
		if (builder == null) {
			log("could not spawn a stand-in builder");
			finish(rt);
			return;
		}
		this.folk.add(builder);
		log(String.format(Locale.ROOT, "folk: a stand-in builder at %s, %d blocks broken for them (highest at y %d, floor %d); queue %d",
			builder.entity().blockPosition().toShortString(), this.folkTargets.size(), picks.isEmpty() ? 0 : BlockPos.getY(picks.getFirst()), o.getY(), q.size()));
		next(Phase.FOLK_MEND, now);
	}

	private void startFolkFire(RepairRuntime rt, HearthWorld world, Settlement s, long now) {
		PlotView plot = s.plot(this.plots.getLast());
		this.fireGameRule = this.level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		this.level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, this.level.getServer());
		int set = 0;
		BlockPos fireAt = null;
		for (long p : baselineOf(s, plot.id(), world, rt)) {
			BlockPos pos = BlockPos.of(p);
			if (set >= 3 || !this.level.getBlockState(pos).ignitedByLava()) {
				continue;
			}
			for (Direction d : Direction.values()) {
				BlockPos f = pos.relative(d);
				if (this.level.getBlockState(f).isAir() && BaseFireBlock.getState(this.level, f).canSurvive(this.level, f)) {
					this.level.setBlock(f, BaseFireBlock.getState(this.level, f), Block.UPDATE_ALL);
					fireAt = f;
					set++;
					break;
				}
			}
		}
		if (fireAt != null) {
			for (int i = 0; i < 3; i++) {
				DevFolk f = DevFolk.spawn(this.level, s, dev.voidmc.hearth.api.JobType.FARMER, surface(fireAt.getX() + 6 - i * 4, fireAt.getZ() + 6), "Brigade" + i);
				if (f != null) {
					this.folk.add(f);
				}
			}
		}
		log(String.format(Locale.ROOT, "folk fire: %d flames set at plot %d, %d folk near", set, plot.id(), this.folk.size()));
		next(Phase.FOLK_FIRE, now);
	}

	private void startFolkInspect(RepairRuntime rt, HearthWorld world, Settlement s, long now) {
		// whatever the fire took is the builder's to mend too; first the silent damage an inspection must find
		this.folkTargets.clear();
		List<Long> cells = baselineOf(s, this.plots.getFirst(), world, rt);
		java.util.Collections.shuffle(cells, new java.util.Random(5));
		for (long p : cells) {
			if (this.folkTargets.size() >= 3) {
				break;
			}
			BlockPos bp = BlockPos.of(p);
			if (!this.level.getBlockState(bp).isAir()) {
				this.level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				this.folkTargets.add(p);
			}
		}
		PlotView plot = s.plot(this.plots.getFirst());
		DevFolk builder = this.folk.getFirst();
		builder.entity().teleportTo(plot.spec().origin().getX() - 1.5, surface(plot.spec().origin().getX() - 2, plot.spec().origin().getZ() - 2).getY(),
			plot.spec().origin().getZ() - 1.5);
		// the round goes to this plot: every other one was looked at just now
		var schedule = RepairModule.INSPECT.schedule();
		for (PlotView other : s.plots()) {
			if (other.id() != plot.id()) {
				schedule.lastSeen(s.id()).put(other.id(), now);
			}
		}
		schedule.lastSeen(s.id()).remove(plot.id());
		schedule.dueNow(s.id());
		log("folk inspection: " + this.folkTargets.size() + " blocks of plot " + plot.id() + " taken with no event; the builder makes a round");
		next(Phase.FOLK_INSPECT, now);
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
		for (DevFolk f : this.folk) {
			f.discard();
		}
		this.folk.clear();
		if (this.savedConfig != null) {
			RepairModule.setConfig(this.savedConfig);
			this.savedConfig = null;
		}
		if (this.embodiedByTest) {
			command("hearth sim embody " + this.settlementId);
			this.embodiedByTest = false;
		}
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
