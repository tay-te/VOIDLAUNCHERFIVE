package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.ResourceSite;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a prospector looks for when a town asks for a new quarry, ore vein, clay bank or stand of timber (a site spent,
 * docs/LIVING_WORLD.md §7.2): enough of the right blocks round where the player stands — a rock face, ore showing in a cave
 * wall, clay in a river bed. Only the blocks in a small cube round the player are read (loaded, since the player is
 * there), once per check.
 *
 * @see dev.voidmc.hearth.quest.service.QuestTracker
 */
public final class Prospecting {
	/**
	 * @param radius  half the side of the cube read round the player (its height runs from {@code radius} below to half of it
	 *                above)
	 * @param need    blocks of the resource that make a find
	 * @param exposed of those, how many must touch air (a face that can be worked, not rock deep in a hill)
	 */
	public record Rule(int radius, Predicate<BlockState> matches, int need, int exposed) {
	}

	private static final Map<ResourceSite.Kind, Rule> RULES = new EnumMap<>(ResourceSite.Kind.class);
	private static final Identifier SALT = Identifier.fromNamespaceAndPath("expanse", "salt_block");
	private static final Identifier LIMESTONE = Identifier.fromNamespaceAndPath("expanse", "limestone");
	private static final Identifier OPAL_SANDSTONE = Identifier.fromNamespaceAndPath("expanse", "opal_sandstone");
	private static final Identifier OPAL_SAND = Identifier.fromNamespaceAndPath("expanse", "opal_sand");
	private static final Identifier CORAL_SAND = Identifier.fromNamespaceAndPath("expanse", "coral_sand");
	private static final Identifier AMBER = Identifier.fromNamespaceAndPath("expanse", "amber");
	private static final Identifier PRISMITE = Identifier.fromNamespaceAndPath("expanse", "prismite_block");
	private static final Identifier GLOWCAP = Identifier.fromNamespaceAndPath("expanse", "glowcap");

	static {
		RULES.put(ResourceSite.Kind.STONE, new Rule(6, Prospecting::rock, 120, 16));
		RULES.put(ResourceSite.Kind.ORE, new Rule(8, s -> s.is(BlockTags.ORES), 5, 1));
		RULES.put(ResourceSite.Kind.CLAY, new Rule(8, s -> s.is(Blocks.CLAY) || s.is(Blocks.MUD), 8, 2));
		RULES.put(ResourceSite.Kind.SAND, new Rule(7, s -> s.is(BlockTags.SAND) || is(s, OPAL_SAND) || is(s, CORAL_SAND), 60, 12));
		RULES.put(ResourceSite.Kind.SALT, new Rule(8, s -> is(s, SALT), 10, 3));
		RULES.put(ResourceSite.Kind.WOODS, new Rule(10, s -> s.is(BlockTags.LOGS), 40, 20));
		RULES.put(ResourceSite.Kind.SPECIALTY, new Rule(8, s -> is(s, AMBER) || is(s, PRISMITE) || is(s, GLOWCAP) || s.is(Blocks.AMETHYST_CLUSTER)
			|| s.is(Blocks.BUDDING_AMETHYST), 3, 1));
	}

	private Prospecting() {
	}

	/** Whether a player can find this kind of site by looking (fields, pastures and fishing waters are the sim's to judge). */
	public static boolean prospectable(ResourceSite.Kind kind) {
		return RULES.containsKey(kind);
	}

	public static @Nullable Rule rule(ResourceSite.Kind kind) {
		return RULES.get(kind);
	}

	/** Whether counts read round the player make a find. */
	public static boolean found(Rule rule, int matching, int exposed) {
		return matching >= rule.need() && exposed >= rule.exposed();
	}

	/** Natural rock a quarry could work: the overworld's base stones, sandstones, and Expanse's limestone and opal sandstone. */
	private static boolean rock(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE)
			|| s.is(Blocks.BASALT) || is(s, LIMESTONE) || is(s, OPAL_SANDSTONE);
	}

	private static boolean is(BlockState s, Identifier id) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(id);
	}
}
