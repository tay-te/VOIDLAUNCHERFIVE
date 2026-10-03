package dev.voidmc.hearth.npc.dev;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.embody.StrayHomes;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.place.PoiScanner;
import dev.voidmc.hearth.npc.work.ScaffoldLedger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * In-memory settlements for trying hearthfolk out before (and without) the sim: a village scanned from blocks a player
 * built (beds become homes, workstations become trades), single folk spawned for a look at them, and a wanderers'
 * camp for strays. Registered with the embodiment manager as an extra settlement source, so everything else — routines,
 * jobs, conversation — runs exactly as it will with real settlements.
 */
public final class NpcSandbox {
	private static final Map<ResourceKey<Level>, List<SandboxSettlement>> BY_LEVEL = new HashMap<>();
	private static long nextId = -1000;

	private NpcSandbox() {
	}

	public static void init() {
		SettlementSources.addExtra(NpcSandbox::settlements);
		StrayHomes.setFallback((level, pos) -> camp(level, pos, Culture.TEMPERATE));
	}

	public static Collection<SandboxSettlement> settlements(ServerLevel level) {
		return BY_LEVEL.getOrDefault(level.dimension(), List.of());
	}

	public static void clear(ServerLevel level) {
		for (SandboxSettlement s : new ArrayList<>(settlements(level))) {
			remove(level, s);
		}
	}

	public static void remove(ServerLevel level, SandboxSettlement s) {
		NpcModule.embodiment().onSettlementAbstract(level, s.id());
		ScaffoldLedger ledger = s.data(ScaffoldLedger.KEY);
		ledger.clearOrphans(level, java.util.Set.of());
		BY_LEVEL.getOrDefault(level.dimension(), new ArrayList<>()).remove(s);
	}

	public static void clearAll() {
		BY_LEVEL.clear();
	}

	private static SandboxSettlement create(ServerLevel level, String name, Culture culture, BlockPos centre) {
		SandboxSettlement s = new SandboxSettlement(nextId--, name, culture, centre, NpcTime.today() - 30);
		BY_LEVEL.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(s);
		return s;
	}

	/**
	 * A village from what is built round {@code centre}: a home for every bed, a trade for every workstation, couples
	 * and their children, the rest of the bed count filled with neighbours.
	 */
	public static SandboxSettlement village(ServerLevel level, BlockPos centre, int radius, Culture culture, RandomSource random) {
		BoundingBox box = new BoundingBox(centre.getX() - radius, centre.getY() - 10, centre.getZ() - radius, centre.getX() + radius, centre.getY() + 14,
			centre.getZ() + radius);
		SandboxSettlement s = create(level, townName(culture, random), culture, centre);
		s.addPlot(SandboxBlueprints.area(box), PlotState.BUILT);
		List<Poi> pois = PoiScanner.scan(level, box);
		List<Poi> beds = new ArrayList<>();
		List<Poi> sites = new ArrayList<>();
		for (Poi p : pois) {
			if (p.type() == PoiType.BED) {
				beds.add(p);
			} else if (p.type() == PoiType.JOB_SITE || p.type() == PoiType.LECTERN || p.type() == PoiType.ALTAR) {
				sites.add(p);
			}
		}
		int people = Math.max(4, Math.min(beds.size(), 32));
		// Couples first, then children for some of them, elders and single folk to fill the beds.
		List<Resident> adults = new ArrayList<>();
		int i = 0;
		while (i < people) {
			BlockPos bed = i < beds.size() ? beds.get(i).pos() : null;
			AgeStage age = i % 7 == 6 ? AgeStage.ELDER : AgeStage.ADULT;
			Resident r;
			if (adults.size() >= 2 && i % 5 == 4) {
				Resident a = adults.get(adults.size() - 2);
				Resident b = adults.get(adults.size() - 1);
				r = HearthServices.residents().create(s, AgeStage.CHILD, random, a, b);
			} else {
				r = HearthServices.residents().create(s, age, random, null, null);
				if (age == AgeStage.ADULT) {
					adults.add(r);
				}
			}
			NpcData d = NpcData.of(r);
			d.setAnchor(bed != null ? bed : centre.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			if (bed != null) {
				d.setBed(bed);
			}
			s.addResident(r);
			i++;
		}
		// Trades: a workstation each, then builders, guards and farmers for the rest.
		List<Resident> workers = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.CHILD) {
				workers.add(r);
			}
		}
		int w = 0;
		for (Poi site : sites) {
			if (w >= workers.size()) {
				break;
			}
			Resident r = workers.get(w++);
			r.job = site.job() != null ? site.job() : site.type() == PoiType.LECTERN ? JobType.SCHOLAR : JobType.PRIEST;
			NpcData.of(r).setWorkPos(site.pos());
		}
		JobType[] rest = {JobType.BUILDER, JobType.GUARD, JobType.FARMER, JobType.MERCHANT, JobType.BUILDER, JobType.NONE, JobType.FORESTER};
		for (int k = 0; w < workers.size(); w++, k++) {
			workers.get(w).job = rest[k % rest.length];
		}
		return s;
	}

	/** A camp of one culture near a spot (for spawning single folk); reused while the player stays near it. */
	public static SandboxSettlement camp(ServerLevel level, BlockPos pos, Culture culture) {
		for (SandboxSettlement s : settlements(level)) {
			if (s.culture() == culture && s.name().startsWith("Camp") && s.center().distSqr(pos) < 96 * 96) {
				return s;
			}
		}
		BoundingBox box = new BoundingBox(pos.getX() - 16, pos.getY() - 6, pos.getZ() - 16, pos.getX() + 16, pos.getY() + 10, pos.getZ() + 16);
		SandboxSettlement s = create(level, "Camp " + townName(culture, level.getRandom()), culture, pos);
		s.addPlot(SandboxBlueprints.area(box), PlotState.BUILT);
		return s;
	}

	/** Adds one resident with a trade to a sandbox settlement, living where they stand. */
	public static Resident addFolk(SandboxSettlement s, BlockPos at, JobType job, @Nullable AgeStage age, RandomSource random) {
		Resident r = HearthServices.residents().create(s, age != null ? age : AgeStage.ADULT, random, null, null);
		r.job = job;
		NpcData.of(r).setAnchor(at);
		s.addResident(r);
		return r;
	}

	/** Stakes out a building for the sandbox's builders: a cottage or a watchtower. */
	public static SandboxSettlement.SimplePlot build(ServerLevel level, SandboxSettlement s, BlockPos origin, String kind, RandomSource random) {
		Blueprint bp = kind.equals("tower") ? SandboxBlueprints.tower(origin) : SandboxBlueprints.cottage(origin);
		SandboxSettlement.SimplePlot plot = s.addPlot(bp, PlotState.BUILDING);
		boolean hasBuilder = false;
		for (Resident r : s.residents()) {
			if (r.job == JobType.BUILDER && r.age != AgeStage.CHILD) {
				hasBuilder = true;
				break;
			}
		}
		if (!hasBuilder) {
			addFolk(s, origin.offset(-3, 0, -3), JobType.BUILDER, AgeStage.ADULT, random);
		}
		return plot;
	}

	public static @Nullable SandboxSettlement nearest(ServerLevel level, BlockPos pos, int maxDistance) {
		SandboxSettlement best = null;
		double bestD = (double) maxDistance * maxDistance;
		for (SandboxSettlement s : settlements(level)) {
			double d = s.center().distSqr(pos);
			if (d < bestD) {
				best = s;
				bestD = d;
			}
		}
		return best;
	}

	/** A name in the culture's tongue, built from its family names: "Halvorsen's Rest", "Fujimori Crossing". */
	private static String townName(Culture culture, RandomSource random) {
		String root = NameBank.of(culture).family(random);
		String[] suffixes = {" Green", " Crossing", "'s Rest", " Hollow", " Ford", " Hill", " End", " Brook"};
		return root + suffixes[random.nextInt(suffixes.length)];
	}

	public static Settlement any(ServerLevel level) {
		Collection<SandboxSettlement> list = settlements(level);
		return list.isEmpty() ? null : list.iterator().next();
	}
}
