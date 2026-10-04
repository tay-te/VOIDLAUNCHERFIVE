package dev.voidmc.hearth.plan.resource;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.terrain.Ground;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Towns build with what they gather (LIVING_WORLD.md §7.2): the {@code local} param, role → material, read from the
 * territory's woods and rock. A town by a birch wood frames in birch, a town over limestone founds on limestone, a desert
 * town builds in its own sandstone; materials from outside come only by trade.
 *
 * <p>Values use the build module's material syntax (a block id, {@code wood:<namespace>:<species>} for a wood family,
 * {@code a|b} for a fallback chain where the first that exists wins), so an Expanse wood falls back to a vanilla one when
 * Expanse is absent. Roles: {@code foundation} (rough stone), {@code plinth} and {@code paving} (dressed stone),
 * {@code frame}, {@code beam} and {@code floor} (the wood), and {@code wall} where the culture's walls are of the wood
 * (northern, tundra, bayou) or of the stone (moorland; desert and mesa when the rock is their sandstone). The species
 * and rock come from the biome at the site's centre (the survey's own reading when the api gives one; see the plan
 * module's requests), else from its climate.
 */
public final class Materials {
	private Materials() {
	}

	/** The {@code local} compound for a settlement, or null when the land says nothing (no sites, no biome). */
	public static @Nullable CompoundTag local(PlanContext ctx, Territory t) {
		Ground g = ctx.ground;
		CompoundTag out = new CompoundTag();
		// the wood: the nearest wood the town works, else the land round the heart if it is wooded
		String wood = null;
		List<ResourceSite> woods = t.workable(ResourceSite.Kind.WOODS);
		if (!woods.isEmpty()) {
			ResourceSite w = woods.get(0);
			wood = wood(g.probe().biome(w.center().getX(), w.center().getZ()), g.probe().temperature(w.center().getX(), w.center().getZ()),
				hash(w.id(), 3));
		} else if (!t.surveyed()) {
			ResourceKey<Biome> b = g.probe().biome(ctx.heartX(), ctx.heartZ());
			wood = b == null ? null : wood(b, g.probe().temperature(ctx.heartX(), ctx.heartZ()), 0.5);
		}
		// the stone: the nearest rock the town quarries or mines
		String[] rock = null;
		List<ResourceSite> stone = t.workable(ResourceSite.Kind.STONE);
		if (stone.isEmpty()) {
			stone = t.workable(ResourceSite.Kind.ORE);
		}
		if (!stone.isEmpty()) {
			ResourceSite s = stone.get(0);
			int x = s.center().getX();
			int z = s.center().getZ();
			rock = rock(g.probe().biome(x, z), g.ground(x, z), g.seaLevel(), g.probe().temperature(x, z), hash(s.id(), 5));
		} else if (!t.surveyed()) {
			ResourceKey<Biome> b = g.probe().biome(ctx.heartX(), ctx.heartZ());
			rock = b == null ? null : rock(b, ctx.state.heartY, g.seaLevel(), g.probe().temperature(ctx.heartX(), ctx.heartZ()), 0.5);
		}
		Culture c = ctx.culture;
		if (rock != null) {
			if (c != Culture.BAYOU && c != Culture.TROPICAL) {
				out.putString("foundation", rock[0]);
			}
			out.putString("plinth", rock[1]);
			out.putString("paving", rock[1]);
			boolean sandy = rock[0].contains("sandstone") || rock[0].contains("terracotta");
			if (c == Culture.MOORLAND || (c == Culture.DESERT || c == Culture.MESA) && sandy) {
				out.putString("wall", c == Culture.MOORLAND ? rock[0] : rock[1]);
			}
		}
		if (wood != null) {
			out.putString("frame", wood);
			out.putString("beam", wood);
			out.putString("floor", wood);
			if (c == Culture.NORTHERN || c == Culture.TUNDRA || c == Culture.BAYOU) {
				out.putString("wall", wood);
			}
		}
		return out.isEmpty() ? null : out;
	}

	/**
	 * The wood of a biome, as a wood family with a vanilla fallback: the species its trees are (redwood in the giants'
	 * valley, birch on a birch hillside), else one that suits its climate. Null where no trees grow.
	 */
	public static @Nullable String wood(@Nullable ResourceKey<Biome> biome, float temperature, double hash) {
		String path = biome == null ? "" : biome.identifier().getPath();
		String ns = biome == null ? "" : biome.identifier().getNamespace();
		if (ns.equals("expanse")) {
			switch (path) {
				case "redwood_giants":
					return "wood:expanse:redwood|wood:minecraft:spruce";
				case "wisteria_vale":
					return "wood:expanse:wisteria|wood:minecraft:cherry";
				case "palm_coast", "coral_coast":
					return "wood:expanse:palm|wood:minecraft:jungle";
				case "aspen_parkland":
					return "wood:expanse:aspen|wood:minecraft:birch";
				case "bluebell_woods":
					return "wood:expanse:beech|wood:minecraft:oak";
				case "maple_highlands":
					return "wood:expanse:maple|wood:minecraft:oak";
				case "olive_groves":
					return "wood:expanse:olive|wood:minecraft:oak";
				case "pine_heath":
					return "wood:expanse:pine|wood:minecraft:spruce";
				case "kapok_rainforest":
					return "wood:expanse:kapok|wood:minecraft:jungle";
				case "monsoon_forest":
					return "wood:expanse:teak|wood:minecraft:jungle";
				case "ghost_gum_outback":
					return "wood:expanse:eucalyptus|wood:minecraft:acacia";
				case "willow_bayou":
					return "wood:expanse:willow|wood:minecraft:mangrove";
				case "lumen_grove":
					return "wood:minecraft:birch"; // lumen wood glows: the surface stays natural
				case "woodland_glades":
					return "wood:minecraft:oak";
				case "ancient_oakwood":
					return "wood:minecraft:dark_oak|wood:minecraft:oak";
				case "amber_steppe":
					return "wood:expanse:baobab|wood:minecraft:acacia";
				case "jade_karst":
					return "wood:minecraft:bamboo";
				case "tepui", "cloud_forest":
					return "wood:minecraft:jungle";
				case "boreal_muskeg", "larch_taiga", "fjordlands", "frostbloom_tundra", "lichen_tundra", "verdant_peaks", "prismatic_peaks", "dolomite_peaks",
					"heather_moor":
					return "wood:minecraft:spruce";
				case "opal_dunes", "red_dunes", "saguaro_flats", "salt_flats", "painted_canyons", "volcanic_highlands", "sulfur_seeps":
					return null;
				default:
					break;
			}
		}
		if (!path.isEmpty()) {
			if (path.contains("birch")) {
				return "wood:minecraft:birch";
			}
			if (path.contains("dark_forest")) {
				return "wood:minecraft:dark_oak";
			}
			if (path.contains("pale_garden")) {
				return "wood:minecraft:pale_oak";
			}
			if (path.contains("cherry")) {
				return "wood:minecraft:cherry";
			}
			if (path.contains("mangrove")) {
				return "wood:minecraft:mangrove";
			}
			if (path.contains("bamboo")) {
				return "wood:minecraft:bamboo";
			}
			if (path.contains("jungle")) {
				return "wood:minecraft:jungle";
			}
			if (path.contains("savanna")) {
				return "wood:minecraft:acacia";
			}
			if (path.contains("taiga") || path.contains("grove") || path.contains("snowy") || path.contains("windswept") || path.contains("frozen")) {
				return "wood:minecraft:spruce";
			}
			if (path.contains("desert") || path.contains("badlands") || path.contains("ocean") || path.contains("beach") || path.contains("peaks")) {
				return null;
			}
			if (path.contains("forest") || path.contains("plains") || path.contains("meadow") || path.contains("swamp") || path.contains("river")) {
				return hash < 0.3 && path.contains("forest") ? "wood:minecraft:birch" : "wood:minecraft:oak";
			}
		}
		// by climate alone (synthetic land, modded biomes)
		if (temperature < 0.3F) {
			return "wood:minecraft:spruce";
		}
		if (temperature > 1.5F) {
			return "wood:minecraft:acacia";
		}
		if (temperature > 0.9F) {
			return "wood:minecraft:jungle";
		}
		return hash < 0.35 ? "wood:minecraft:birch" : hash < 0.55 ? "wood:minecraft:dark_oak" : "wood:minecraft:oak";
	}

	/**
	 * The rock of a column as {rough, dressed}: Expanse limestone in the karst, opal sandstone in the dunes, sandstone in
	 * the deserts, red sandstone in the badlands, tuff and blackstone on the volcanoes, deepslate where the ground lies
	 * deep, andesite, diorite or granite in the mountains, plain stone elsewhere.
	 */
	public static String[] rock(@Nullable ResourceKey<Biome> biome, int groundY, int seaLevel, float temperature, double hash) {
		String path = biome == null ? "" : biome.identifier().getPath();
		String ns = biome == null ? "" : biome.identifier().getNamespace();
		if (ns.equals("expanse")) {
			switch (path) {
				case "jade_karst":
					return new String[]{"expanse:limestone|minecraft:calcite", "expanse:limestone_bricks|minecraft:stone_bricks"};
				case "opal_dunes":
					return new String[]{"expanse:opal_sandstone|minecraft:sandstone", "expanse:cut_opal_sandstone|minecraft:cut_sandstone"};
				case "painted_canyons", "red_dunes":
					return new String[]{"minecraft:red_sandstone", "minecraft:cut_red_sandstone"};
				case "saguaro_flats", "salt_flats":
					return new String[]{"minecraft:sandstone", "minecraft:cut_sandstone"};
				case "volcanic_highlands":
					return new String[]{"minecraft:tuff", "minecraft:tuff_bricks"};
				case "sulfur_seeps":
					return new String[]{"minecraft:blackstone", "minecraft:polished_blackstone_bricks"};
				case "prismatic_peaks", "dolomite_peaks":
					return new String[]{"minecraft:calcite", "minecraft:polished_diorite"};
				case "willow_bayou":
					return new String[]{"minecraft:packed_mud", "minecraft:mud_bricks"};
				default:
					break;
			}
		}
		if (path.contains("badlands")) {
			return new String[]{"minecraft:red_sandstone", "minecraft:cut_red_sandstone"};
		}
		if (path.contains("desert") || path.contains("beach") && temperature > 1.5F) {
			return new String[]{"minecraft:sandstone", "minecraft:cut_sandstone"};
		}
		if (path.contains("mangrove") || path.contains("swamp")) {
			return new String[]{"minecraft:packed_mud", "minecraft:mud_bricks"};
		}
		if (path.isEmpty() && temperature > 1.5F) {
			return new String[]{"minecraft:sandstone", "minecraft:cut_sandstone"};
		}
		if (groundY < 0) {
			return new String[]{"minecraft:cobbled_deepslate", "minecraft:deepslate_bricks"};
		}
		boolean high = groundY > seaLevel + 60 || path.contains("peaks") || path.contains("windswept") || path.contains("stony");
		if (high) {
			return hash < 0.34 ? new String[]{"minecraft:andesite", "minecraft:polished_andesite"}
				: hash < 0.6 ? new String[]{"minecraft:granite", "minecraft:polished_granite"}
				: hash < 0.75 ? new String[]{"minecraft:diorite", "minecraft:polished_diorite"}
				: new String[]{"minecraft:cobblestone", "minecraft:stone_bricks"};
		}
		return hash < 0.18 ? new String[]{"minecraft:andesite", "minecraft:polished_andesite"} : new String[]{"minecraft:cobblestone", "minecraft:stone_bricks"};
	}

	/**
	 * The {@code local} materials of a deep town, from its realm's character: the realm's own rock for walls and
	 * foundations, and its glow — glowcaps in the wilds, amber in the amber hollows, prismite in the crystal realms — for
	 * light (LIVING_WORLD.md §8.2: lit by glowcaps and amber).
	 */
	public static CompoundTag realm(String character) {
		String ch = character.toLowerCase(Locale.ROOT);
		String rough = "minecraft:cobbled_deepslate";
		String dressed = "minecraft:deepslate_bricks";
		String light = "expanse:glowcap|minecraft:shroomlight";
		if (ch.contains("amber")) {
			light = "expanse:amber|minecraft:shroomlight";
			dressed = "minecraft:polished_deepslate";
		} else if (ch.contains("crystal")) {
			rough = "minecraft:calcite";
			dressed = "minecraft:tuff_bricks";
			light = "expanse:prismite_block|minecraft:sea_lantern";
		} else if (ch.contains("ember") || ch.contains("brimstone")) {
			rough = "minecraft:blackstone";
			dressed = "minecraft:polished_blackstone_bricks";
			light = "minecraft:shroomlight";
		} else if (ch.contains("fossil")) {
			rough = "minecraft:sandstone";
			dressed = "minecraft:cut_sandstone";
			light = "expanse:amber|minecraft:shroomlight";
		} else if (ch.contains("drip")) {
			rough = "minecraft:dripstone_block";
			dressed = "minecraft:polished_deepslate";
		} else if (ch.contains("tidal") || ch.contains("mere")) {
			rough = "minecraft:calcite";
			dressed = "minecraft:deepslate_tiles";
			light = "minecraft:sea_lantern";
		} else if (ch.contains("frozen")) {
			dressed = "minecraft:deepslate_tiles";
			light = "minecraft:sea_lantern";
		} else if (ch.contains("root") || ch.contains("lush")) {
			rough = "minecraft:mossy_cobblestone";
			light = "expanse:glowcap|minecraft:shroomlight";
		}
		CompoundTag out = new CompoundTag();
		out.putString("foundation", rough);
		out.putString("wall", dressed);
		out.putString("plinth", dressed);
		out.putString("paving", dressed);
		out.putString("light", light);
		return out;
	}

	private static double hash(int id, int salt) {
		long h = (id * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
