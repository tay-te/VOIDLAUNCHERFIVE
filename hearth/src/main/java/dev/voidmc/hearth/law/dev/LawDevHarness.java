package dev.voidmc.hearth.law.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawServiceImpl;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.LawDays;
import dev.voidmc.hearth.law.justice.Justice;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.own.OwnerRules;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code -Dhearth.dev.law=true}: a self-check of the law on a dedicated server's real settlements, logged as
 * {@code [hearth law]} lines. It waits for live settlements (run it with the sim's harness, e.g. {@code found=4 generate=true}),
 * waits {@code hearth.dev.law.delay} ticks (1200) more, then for each: who owns its plots, how fast ownership is answered, every door and chest in loaded chunks with its
 * lock, the cells and pillories it has; and a crime against a made-up offender filed, spread to the neighbours by a
 * day's passing, shown on the posters, and pardoned again.
 */
public final class LawDevHarness {
	private static final UUID OFFENDER = UUID.fromString("00000000-1a57-0000-0000-00000000dead");
	private static int waited;
	private static int sinceFound = -1;
	private static boolean done;

	private LawDevHarness() {
	}

	public static void init() {
		if (!Boolean.getBoolean("hearth.dev.law")) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(LawDevHarness::tick);
	}

	private static void tick(MinecraftServer server) {
		if (done || ++waited % 100 != 0) {
			return;
		}
		HearthWorld world = HearthWorld.of(server.overworld());
		if (world == null || world.settlements().isEmpty()) {
			if (waited > 20 * 600) {
				Hearth.LOG.info("[hearth law] no live settlements after 10 minutes; nothing to check");
				done = true;
			}
			return;
		}
		// give the sim's own harness time to generate and apply its towns first
		if (sinceFound < 0) {
			sinceFound = waited;
		}
		if (waited - sinceFound < Integer.getInteger("hearth.dev.law.delay", 1200)) {
			return;
		}
		done = true;
		try {
			run(server, world);
		} catch (RuntimeException e) {
			Hearth.LOG.error("[hearth law] self-check failed", e);
		}
	}

	private static void run(MinecraftServer server, HearthWorld world) {
		ServerLevel level = world.level();
		List<Settlement> towns = new ArrayList<>(world.settlements());
		Hearth.LOG.info("[hearth law] {} live settlements; mode {}", towns.size(), Law.config().mode().getSerializedName());
		int i = 0;
		for (Settlement s : towns.subList(0, Math.min(6, towns.size()))) {
			ownership(level, s);
			locks(level, s, i++ < 2);
		}
		justice(server, world, towns.get(0));
	}

	private static void ownership(ServerLevel level, Settlement s) {
		Map<Owner.Kind, Integer> kinds = new EnumMap<>(Owner.Kind.class);
		int publicPlots = 0;
		for (PlotView p : s.plots()) {
			if (p.state() == PlotState.PLANNED) {
				continue;
			}
			Owner o = OwnerRules.owner(s, p);
			if (o == null) {
				publicPlots++;
			} else {
				kinds.merge(o.kind(), 1, Integer::sum);
			}
		}
		BoundingBox b = s.bounds();
		Random random = new Random(s.id());
		int samples = 20_000;
		int owned = 0;
		long t0 = System.nanoTime();
		for (int i = 0; i < samples; i++) {
			BlockPos pos = new BlockPos(b.minX() + random.nextInt(Math.max(1, b.getXSpan())), b.minY() + random.nextInt(Math.max(1, b.getYSpan())),
				b.minZ() + random.nextInt(Math.max(1, b.getZSpan())));
			if (Ownership.owner(level, pos) != null) {
				owned++;
			}
		}
		double us = (System.nanoTime() - t0) / 1000.0 / samples;
		Hearth.LOG.info("[hearth law] {} ({} souls, {} plots): owners {} public {}; ownership lookups {} µs avg, {}% of the bounds owned",
			s.name(), s.population(), s.plots().size(), kinds, publicPlots, String.format(java.util.Locale.ROOT, "%.2f", us), owned * 100 / samples);
	}

	/** @param load load the plots' chunks (dev only: synchronous) rather than looking only at those already loaded */
	private static void locks(ServerLevel level, Settlement s, boolean load) {
		int[] tiers = new int[6];
		int doors = 0;
		int chests = 0;
		int lockedNow = 0;
		int scanned = 0;
		long t0 = System.nanoTime();
		Map<ArchetypeCategory, Integer> byCategory = new EnumMap<>(ArchetypeCategory.class);
		for (PlotView p : s.plots()) {
			if (p.state() != PlotState.BUILT) {
				continue;
			}
			var spec = p.spec();
			for (int x = spec.origin().getX(); x < spec.origin().getX() + spec.sizeX(); x++) {
				for (int z = spec.origin().getZ(); z < spec.origin().getZ() + spec.sizeZ(); z++) {
					BlockPos column = new BlockPos(x, spec.floorY(), z);
					if (load) {
						level.getChunkAt(column);
					} else if (!level.isLoaded(column)) {
						continue;
					}
					for (int y = spec.floorY() - 2; y < spec.floorY() + Math.min(spec.maxHeight(), 24); y++) {
						BlockPos pos = new BlockPos(x, y, z);
						BlockState state = level.getBlockState(pos);
						if (!Locks.lockable(state)) {
							continue;
						}
						scanned++;
						Locks.Lock lock = Locks.at(level, pos, state);
						if (lock == null) {
							continue;
						}
						tiers[lock.tier()]++;
						if (lock.door()) {
							doors++;
						} else {
							chests++;
						}
						if (lock.locked()) {
							lockedNow++;
						}
						byCategory.merge(OwnerRules.category(spec.archetype()), 1, Integer::sum);
					}
				}
			}
		}
		double ms = (System.nanoTime() - t0) / 1.0e6;
		Hearth.LOG.info("[hearth law] {}: {} lockable blocks in {} chunks, {} doors and {} chests with locks (tiers 1-5: {} {} {} {} {}), {} locked "
				+ "at minute {} of the day, by building {}; cells {}, pillories {}, guard posts {} ({} ms)",
			s.name(), scanned, load ? "all its" : "loaded", doors, chests, tiers[1], tiers[2], tiers[3], tiers[4], tiers[5], lockedNow, Law.minuteOfDay(level), byCategory,
			s.pois(PoiType.CELL).size(), s.pois(PoiType.PILLORY).size(), s.pois(PoiType.GUARD_POST).size(), String.format(java.util.Locale.ROOT, "%.1f", ms));
	}

	private static void justice(MinecraftServer server, HearthWorld world, Settlement s) {
		LawService law = dev.voidmc.hearth.api.HearthServices.law();
		long t0 = System.nanoTime();
		CrimeDesk.report(server, new Crime(Offence.THEFT, OFFENDER, s.id(), s.center(), null, 40, true, server.overworld().getGameTime()));
		long fileUs = (System.nanoTime() - t0) / 1000;
		Hearth.LOG.info("[hearth law] filed a theft in {}: wanted {}, bounty {} ({} µs)", s.name(), law != null && law.wanted(OFFENDER, s.id()),
			law == null ? -1 : law.bounty(OFFENDER, s.id()), fileUs);
		// age the warrant past the spread delay, then let a day pass
		var w = Law.state(s).warrant(OFFENDER);
		long day = world.day();
		if (w != null) {
			w.reduce(0, day - Law.config().justice().spreadDays());
		}
		long t1 = System.nanoTime();
		LawDays.dayPassed(world, s, day);
		long dayUs = (System.nanoTime() - t1) / 1000;
		int reached = 0;
		for (Settlement other : world.settlements()) {
			if (other.id() != s.id() && Law.state(other).warrant(OFFENDER) != null) {
				reached++;
				Hearth.LOG.info("[hearth law]   word reached {} ({} blocks away): bounty {}", other.name(),
					(int) Math.sqrt(other.center().distSqr(s.center())), Law.state(other).warrant(OFFENDER).bounty());
			}
		}
		Hearth.LOG.info("[hearth law] a day passed in {} ({} µs): the warrant reached {} neighbouring settlements; posters there: {}", s.name(), dayUs,
			reached, LawServiceImpl.posters(s).size());
		for (Settlement other : world.settlements()) {
			Justice.pardon(other, OFFENDER);
		}
		Hearth.LOG.info("[hearth law] pardoned everywhere: wanted {}", law != null && law.wanted(OFFENDER, s.id()));
	}
}
