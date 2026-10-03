package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.person.Trait;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * What folk know of the wider world: the other settlements (by name, compass direction and distance) and, when VOID
 * Expanse is loaded, its landmarks — a lighthouse up the coast, a pagoda among the karst, a stone circle on the moor.
 *
 * <p>Landmarks are found with the structure locator, which is not free, so each settlement looks for one landmark kind
 * at a time, at most one search per {@link #SEARCH_INTERVAL} ticks across the server, and remembers what it found.
 */
public final class Rumours {
	private static final int SEARCH_INTERVAL = 200;
	private static final int SEARCH_RADIUS_CHUNKS = 48;
	/** Expanse landmarks worth telling a traveller about, with the words folk use for them. */
	private static final String[][] LANDMARKS = {
		{"lighthouse", "lighthouse"}, {"karst_pagoda", "pagoda"}, {"cloud_monastery", "monastery in the clouds"},
		{"sand_colossus", "great statue in the sands"}, {"stone_circle", "stone circle"}, {"wisteria_manor", "manor among the wisteria"},
		{"ruined_watchtower", "ruined watchtower"}, {"summit_cairn", "cairn on the summit"}, {"mammoth_dig", "mammoth dig"},
		{"fairy_ring", "fairy ring"}, {"tea_garden", "tea garden"}, {"caravanserai", "caravanserai"}, {"lumen_shrine", "glowing shrine"},
		{"sun_shrine", "sun shrine"}, {"deepstone_ruins", "deep ruins"}, {"treehouse", "treehouse village"}, {"pilgrim_shrine", "pilgrims' shrine"},
		{"graveyard", "old graveyard"}, {"frost_outpost", "frozen outpost"}, {"palm_harbour", "palm harbour"}
	};

	private static final Map<Long, Map<String, Optional<BlockPos>>> FOUND = new HashMap<>();
	private static long lastSearch = -1_000_000_000L;

	private Rumours() {
	}

	/** One rumour line for this conversation. */
	public static String tell(ServerLevel level, Settlement settlement, Resident resident, ServerPlayer player, BlockPos at, RandomSource random) {
		DialogueTemplates.Context ctx = Greetings.context(level, settlement, resident, player, at);
		Map<String, String> v = Greetings.values(settlement, resident, player);
		boolean curious = ctx.traits().contains(Trait.CURIOUS) || ctx.traits().contains(Trait.GOSSIP);
		List<String> options = new ArrayList<>();
		// Other settlements, nearest first: live ones, and those the atlas knows of that nobody has visited yet.
		List<Place> others = others(level, settlement);
		for (int i = 0; i < Math.min(others.size(), curious ? 4 : 2); i++) {
			Place o = others.get(i);
			Map<String, String> vv = new HashMap<>(v);
			vv.put("other", o.name());
			vv.put("direction", direction(settlement.center(), o.center()));
			vv.put("distance", Integer.toString(round(Math.sqrt(o.center().distSqr(settlement.center())))));
			String line = DialogueTemplates.pick("rumour.settlement", ctx, random);
			if (line != null) {
				options.add(DialogueTemplates.fill(line, vv));
			}
		}
		// Landmarks.
		if (NpcModule.config().talk().landmarkRumours() && FabricLoader.getInstance().isModLoaded("expanse")) {
			Map<String, Optional<BlockPos>> found = FOUND.computeIfAbsent(settlement.id(), k -> new HashMap<>());
			searchOne(level, settlement, found, random);
			for (String[] lm : LANDMARKS) {
				Optional<BlockPos> pos = located(settlement, lm[0]);
				if (pos.isPresent()) {
					Map<String, String> vv = new HashMap<>(v);
					vv.put("landmark", lm[1]);
					vv.put("direction", direction(settlement.center(), pos.get()));
					vv.put("distance", Integer.toString(round(Math.sqrt(pos.get().distSqr(settlement.center())))));
					String line = DialogueTemplates.pick("rumour.landmark", ctx, random);
					if (line != null) {
						options.add(DialogueTemplates.fill(line, vv));
					}
				}
			}
		}
		if (options.isEmpty()) {
			String line = DialogueTemplates.pick("rumour.none", ctx, random);
			return DialogueTemplates.fill(line != null ? line : "I couldn't say.", v);
		}
		return options.get(random.nextInt(options.size()));
	}

	/** How far folk have heard of other settlements. */
	private static final int RUMOUR_RADIUS = 2500;

	private record Place(String name, BlockPos center) {
	}

	/**
	 * Other settlements folk have heard of, nearest first: from the sim's atlas ({@code HearthWorld.knownSites}: live
	 * towns and historic ones not yet visited; regions not yet planned are planned off-thread and known next time), plus
	 * the dev sandbox's camps.
	 */
	private static List<Place> others(ServerLevel level, Settlement settlement) {
		List<Place> out = new ArrayList<>();
		dev.voidmc.hearth.api.HearthWorld world = dev.voidmc.hearth.api.HearthWorld.of(level);
		java.util.Set<Long> seen = new java.util.HashSet<>();
		seen.add(settlement.id());
		if (world != null) {
			for (dev.voidmc.hearth.api.HearthWorld.SiteInfo site : world.knownSites(settlement.center(), RUMOUR_RADIUS)) {
				if (seen.add(site.settlementId())) {
					out.add(new Place(site.name(), site.center()));
				}
			}
		}
		for (Settlement s : SettlementSources.all(level)) {
			if (seen.add(s.id()) && s.center().distSqr(settlement.center()) < (double) RUMOUR_RADIUS * RUMOUR_RADIUS) {
				out.add(new Place(s.name(), s.center()));
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.center().distSqr(settlement.center())));
		return out;
	}

	/**
	 * Asks for one landmark kind this settlement has not asked about yet, if the server-wide budget allows. The search is
	 * the quest module's {@code StructureLocator}: candidates from the placement maths on a worker thread, confirmed by
	 * asynchronous chunk loads, the answer kept with the settlement — never vanilla's locate, which generates structure
	 * starts on the server thread and can stall it for a second.
	 */
	private static void searchOne(ServerLevel level, Settlement settlement, Map<String, Optional<BlockPos>> asked, RandomSource random) {
		long now = level.getGameTime();
		if (now - lastSearch < SEARCH_INTERVAL) {
			return;
		}
		List<String[]> todo = new ArrayList<>();
		for (String[] lm : LANDMARKS) {
			if (!asked.containsKey(lm[0]) && dev.voidmc.hearth.quest.service.QuestStore.board(settlement)
				.target(Identifier.fromNamespaceAndPath("expanse", lm[0])) == null) {
				todo.add(lm);
			}
		}
		if (todo.isEmpty()) {
			return;
		}
		lastSearch = now;
		String[] lm = todo.get(random.nextInt(todo.size()));
		asked.put(lm[0], Optional.empty());
		dev.voidmc.hearth.quest.world.StructureLocator.request(level, settlement, List.of(Identifier.fromNamespaceAndPath("expanse", lm[0])),
			SEARCH_RADIUS_CHUNKS * 16, NpcTime.today(), id -> { });
	}

	/** Where the landmark is, if a search from this settlement found it. */
	private static Optional<BlockPos> located(Settlement settlement, String landmark) {
		var t = dev.voidmc.hearth.quest.service.QuestStore.board(settlement).target(Identifier.fromNamespaceAndPath("expanse", landmark));
		return t != null && t.found() ? Optional.of(t.pos()) : Optional.empty();
	}

	/** "north", "south-east"... from one place to another. */
	public static String direction(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		// Minecraft's north is -z.
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
		int i = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return names[i];
	}

	private static int round(double d) {
		if (d < 200) {
			return (int) (Math.round(d / 10) * 10);
		}
		return (int) (Math.round(d / 50) * 50);
	}

	public static void forget(long settlementId) {
		FOUND.remove(settlementId);
	}

	/** The server stopped: what was asked belongs to that world. */
	public static void clear() {
		FOUND.clear();
		lastSearch = -1_000_000_000L;
	}
}
