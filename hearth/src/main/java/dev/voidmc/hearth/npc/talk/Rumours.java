package dev.voidmc.hearth.npc.talk;

import com.mojang.datafixers.util.Pair;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

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
	private static long lastSearch = Long.MIN_VALUE;

	private Rumours() {
	}

	/** One rumour line for this conversation. */
	public static String tell(ServerLevel level, Settlement settlement, Resident resident, ServerPlayer player, BlockPos at, RandomSource random) {
		DialogueTemplates.Context ctx = Greetings.context(level, settlement, resident, player, at);
		Map<String, String> v = Greetings.values(settlement, resident, player);
		boolean curious = ctx.traits().contains(Trait.CURIOUS) || ctx.traits().contains(Trait.GOSSIP);
		List<String> options = new ArrayList<>();
		// Other settlements, nearest first.
		List<Settlement> others = new ArrayList<>();
		for (Settlement s : SettlementSources.all(level)) {
			if (s.id() != settlement.id() && s.center().distSqr(settlement.center()) < 4000.0 * 4000.0) {
				others.add(s);
			}
		}
		others.sort(Comparator.comparingDouble(s -> s.center().distSqr(settlement.center())));
		for (int i = 0; i < Math.min(others.size(), curious ? 4 : 2); i++) {
			Settlement o = others.get(i);
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
				Optional<BlockPos> pos = found.get(lm[0]);
				if (pos != null && pos.isPresent()) {
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

	/** Looks for one landmark kind this settlement has not looked for yet, if the server-wide search budget allows. */
	private static void searchOne(ServerLevel level, Settlement settlement, Map<String, Optional<BlockPos>> found, RandomSource random) {
		long now = level.getGameTime();
		if (now - lastSearch < SEARCH_INTERVAL) {
			return;
		}
		Optional<Registry<Structure>> registry = level.registryAccess().lookup(Registries.STRUCTURE);
		if (registry.isEmpty()) {
			return;
		}
		List<String[]> todo = new ArrayList<>();
		for (String[] lm : LANDMARKS) {
			if (!found.containsKey(lm[0])) {
				todo.add(lm);
			}
		}
		if (todo.isEmpty()) {
			return;
		}
		lastSearch = now;
		String[] lm = todo.get(random.nextInt(todo.size()));
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("expanse", lm[0]));
		Optional<Holder.Reference<Structure>> holder = registry.get().get(key);
		if (holder.isEmpty()) {
			found.put(lm[0], Optional.empty());
			return;
		}
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(holder.get()), settlement.center(), SEARCH_RADIUS_CHUNKS, false);
		found.put(lm[0], hit == null ? Optional.empty() : Optional.of(hit.getFirst()));
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

	public static @Nullable BlockPos known(long settlementId, String landmark) {
		Map<String, Optional<BlockPos>> m = FOUND.get(settlementId);
		return m == null ? null : m.getOrDefault(landmark, Optional.empty()).orElse(null);
	}
}
