package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.burden.WeightDeriver.Rule;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The leaf table: raw materials whose weight is set by hand (BALANCE.md §5.2). Everything else is derived from recipes.
 * Entries are an item id or a {@code #tag}; later entries win over earlier ones, so specific items follow their tags.
 * Missing items (Hearth's coins without Hearth) are skipped.
 */
public final class Leaves {
	/** One row: a target and its kg, or another item's weight plus kg when {@code plus} is set. */
	public record Entry(String target, float kg, String plus) {
		static Entry of(String target, float kg) {
			return new Entry(target, kg, "");
		}
	}

	/** Spawn eggs, debug and technical items weigh nothing (BURDEN.md §1). */
	public static final Set<String> TECHNICAL = Set.of("minecraft:debug_stick", "minecraft:command_block", "minecraft:chain_command_block",
		"minecraft:repeating_command_block", "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void",
		"minecraft:jigsaw", "minecraft:barrier", "minecraft:light", "minecraft:knowledge_book", "minecraft:test_block", "minecraft:test_instance_block",
		"minecraft:air");

	public static final TagKey<Item> C_ORES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"));

	public static final List<Entry> TABLE = List.of(
		// Coins (Hearth)
		Entry.of("hearth:copper_bit", 0.01f), Entry.of("hearth:iron_mark", 0.01f), Entry.of("hearth:gold_crown", 0.01f),
		// Gems and dusts
		Entry.of("diamond", 0.05f), Entry.of("emerald", 0.05f), Entry.of("quartz", 0.05f), Entry.of("amethyst_shard", 0.05f),
		Entry.of("lapis_lazuli", 0.02f), Entry.of("redstone", 0.02f), Entry.of("glowstone_dust", 0.02f),
		Entry.of("coal", 0.1f), Entry.of("charcoal", 0.08f),
		// Metals
		Entry.of("iron_ingot", 0.5f), Entry.of("gold_ingot", 0.6f), Entry.of("copper_ingot", 0.4f), Entry.of("netherite_scrap", 0.8f),
		Entry.of("raw_iron", 0.6f), Entry.of("raw_gold", 0.7f), Entry.of("raw_copper", 0.5f),
		// Stone-like (ores are added below: their stone + 0.1)
		Entry.of("cobblestone", 0.25f), Entry.of("stone", 0.25f), Entry.of("andesite", 0.25f), Entry.of("diorite", 0.25f),
		Entry.of("granite", 0.25f), Entry.of("tuff", 0.25f), Entry.of("calcite", 0.25f),
		Entry.of("deepslate", 0.3f), Entry.of("cobbled_deepslate", 0.3f), Entry.of("basalt", 0.3f), Entry.of("blackstone", 0.3f),
		Entry.of("netherrack", 0.15f), Entry.of("end_stone", 0.2f), Entry.of("obsidian", 0.6f), Entry.of("crying_obsidian", 0.6f),
		// Earth
		Entry.of("dirt", 0.2f), Entry.of("grass_block", 0.2f), Entry.of("sand", 0.2f), Entry.of("red_sand", 0.2f), Entry.of("mud", 0.2f),
		Entry.of("gravel", 0.25f), Entry.of("clay_ball", 0.05f), Entry.of("snowball", 0.02f), Entry.of("ice", 0.15f),
		// Wood: planks derive to 0.2, sticks to 0.1
		Entry.of("#minecraft:logs", 0.8f), Entry.of("bamboo", 0.05f),
		// Plants
		Entry.of("#minecraft:saplings", 0.05f), Entry.of("#minecraft:flowers", 0.05f), Entry.of("#minecraft:leaves", 0.05f),
		Entry.of("wheat_seeds", 0.005f), Entry.of("beetroot_seeds", 0.005f), Entry.of("melon_seeds", 0.005f), Entry.of("pumpkin_seeds", 0.005f),
		Entry.of("torchflower_seeds", 0.005f), Entry.of("pitcher_pod", 0.005f),
		Entry.of("sweet_berries", 0.02f), Entry.of("glow_berries", 0.02f),
		// Food (cooked weighs the same as raw)
		Entry.of("wheat", 0.05f), Entry.of("apple", 0.15f), Entry.of("carrot", 0.1f), Entry.of("beetroot", 0.1f),
		Entry.of("potato", 0.15f), Entry.of("baked_potato", 0.15f),
		Entry.of("beef", 0.3f), Entry.of("porkchop", 0.3f), Entry.of("mutton", 0.3f), Entry.of("cod", 0.3f), Entry.of("salmon", 0.3f),
		Entry.of("tropical_fish", 0.3f), Entry.of("pufferfish", 0.3f),
		Entry.of("cooked_beef", 0.3f), Entry.of("cooked_porkchop", 0.3f), Entry.of("cooked_mutton", 0.3f), Entry.of("cooked_cod", 0.3f),
		Entry.of("cooked_salmon", 0.3f),
		Entry.of("chicken", 0.4f), Entry.of("cooked_chicken", 0.4f), Entry.of("#minecraft:eggs", 0.06f), Entry.of("egg", 0.06f),
		Entry.of("melon_slice", 0.1f), Entry.of("pumpkin", 2.0f), Entry.of("melon", 1.8f),
		// Animal
		Entry.of("leather", 0.3f), Entry.of("rabbit_hide", 0.1f), Entry.of("string", 0.01f), Entry.of("feather", 0.01f), Entry.of("paper", 0.01f),
		Entry.of("#minecraft:wool", 0.15f), Entry.of("bone", 0.1f), Entry.of("shulker_shell", 0.3f),
		// Liquids: a bucket plus what is in it
		new Entry("water_bucket", 1.0f, "bucket"), new Entry("milk_bucket", 1.0f, "bucket"), new Entry("lava_bucket", 1.5f, "bucket"),
		new Entry("powder_snow_bucket", 1.0f, "bucket"),
		new Entry("cod_bucket", 0.3f, "water_bucket"), new Entry("salmon_bucket", 0.3f, "water_bucket"),
		new Entry("pufferfish_bucket", 0.3f, "water_bucket"), new Entry("tropical_fish_bucket", 0.3f, "water_bucket"),
		new Entry("axolotl_bucket", 0.5f, "water_bucket"), new Entry("tadpole_bucket", 0.05f, "water_bucket"),
		new Entry("sulfur_cube_bucket", 0.5f, "water_bucket"),
		Entry.of("potion", 0.35f), Entry.of("splash_potion", 0.35f), Entry.of("lingering_potion", 0.35f), Entry.of("glass", 0.2f),
		// Not on the balance sheet: natural blocks and finds no recipe makes, which the 4 kg ÷ stack fallback would make
		// absurdly light (a podzol block at a quarter of dirt) or heavy (a music disc at 4 kg). Tuned by the burden module.
		Entry.of("podzol", 0.2f), Entry.of("mycelium", 0.2f), Entry.of("rooted_dirt", 0.2f), Entry.of("dirt_path", 0.2f), Entry.of("farmland", 0.2f),
		Entry.of("soul_sand", 0.2f), Entry.of("soul_soil", 0.2f), Entry.of("suspicious_sand", 0.2f), Entry.of("suspicious_gravel", 0.25f),
		Entry.of("crimson_nylium", 0.15f), Entry.of("warped_nylium", 0.15f), Entry.of("moss_block", 0.1f), Entry.of("pale_moss_block", 0.1f),
		Entry.of("sculk", 0.2f), Entry.of("mangrove_roots", 0.4f), Entry.of("cactus", 0.2f), Entry.of("sugar_cane", 0.05f), Entry.of("kelp", 0.02f),
		Entry.of("bedrock", 0.3f), Entry.of("reinforced_deepslate", 0.3f), Entry.of("gilded_blackstone", 0.3f), Entry.of("budding_amethyst", 0.25f),
		Entry.of("pointed_dripstone", 0.06f), Entry.of("sponge", 0.1f), new Entry("wet_sponge", 1.0f, "sponge"), Entry.of("cobweb", 0.01f),
		Entry.of("brown_mushroom", 0.02f), Entry.of("red_mushroom", 0.02f), Entry.of("brown_mushroom_block", 0.1f), Entry.of("red_mushroom_block", 0.1f),
		Entry.of("mushroom_stem", 0.1f), Entry.of("shroomlight", 0.1f), Entry.of("rabbit", 0.2f),
		Entry.of("heavy_core", 3.0f), Entry.of("bell", 2.0f), Entry.of("end_portal_frame", 1.0f), Entry.of("dragon_egg", 3.0f), Entry.of("spawner", 5.0f),
		Entry.of("map", 0.1f), Entry.of("golden_apple", 0.3f), Entry.of("goat_horn", 0.5f), Entry.of("heart_of_the_sea", 0.3f), Entry.of("nautilus_shell", 0.1f),
		Entry.of("#minecraft:creeper_drop_music_discs", 0.2f), Entry.of("#minecraft:decorated_pot_sherds", 0.1f));

	/** Name patterns for whole families no tag gathers: music discs, banner patterns, corals. */
	private static final List<Entry> PATTERNS = List.of(
		Entry.of("music_disc_", 0.2f), Entry.of("_banner_pattern", 0.01f), Entry.of("_coral_block", 0.25f), Entry.of("_coral_fan", 0.05f),
		Entry.of("_coral", 0.05f), Entry.of("_froglight", 0.15f), Entry.of("_amethyst_bud", 0.05f));

	private Leaves() {
	}

	/** The table as deriver rules. {@code tags} lists a tag's items (bound registry tags on a server, JSON in tests). */
	public static Map<Item, Rule> rules(Function<TagKey<Item>, Collection<Item>> tags) {
		Map<Item, Rule> rules = new LinkedHashMap<>();
		// Ores: their stone + 0.1, from the ore's name, so modded ores fit in too.
		for (Item item : BuiltInRegistries.ITEM) {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			if (path.endsWith("_ore") || tags.apply(C_ORES).contains(item)) {
				Item stone = path.startsWith("deepslate_") ? Items.DEEPSLATE : path.startsWith("nether_") ? Items.NETHERRACK : Items.STONE;
				rules.put(item, new Rule.Plus(stone, 0.1f, "leaf"));
			}
		}
		for (Item item : BuiltInRegistries.ITEM) {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			for (Entry p : PATTERNS) {
				if (p.target().endsWith("_") ? path.startsWith(p.target()) : path.endsWith(p.target())) {
					rules.put(item, new Rule.Fixed(p.kg(), "leaf"));
					break;
				}
			}
		}
		for (Entry e : TABLE) {
			Rule rule;
			if (e.plus().isEmpty()) {
				rule = new Rule.Fixed(e.kg(), "leaf");
			} else {
				Optional<Item> base = item(e.plus());
				if (base.isEmpty()) {
					continue;
				}
				rule = new Rule.Plus(base.get(), e.kg(), "leaf");
			}
			for (Item item : targets(e.target(), tags)) {
				rules.put(item, rule);
			}
		}
		rules.put(Items.ANCIENT_DEBRIS, new Rule.Fixed(1.0f, "leaf"));
		rules.put(Items.AMETHYST_CLUSTER, new Rule.Fixed(0.05f, "leaf"));
		return rules;
	}

	public static boolean technical(Item item) {
		return item instanceof SpawnEggItem || TECHNICAL.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	/** Items an entry names: one item, or a tag's items. Unknown ids give none. */
	public static Collection<Item> targets(String target, Function<TagKey<Item>, Collection<Item>> tags) {
		if (target.startsWith("#")) {
			Identifier id = Identifier.tryParse(target.substring(1));
			return id == null ? List.of() : tags.apply(TagKey.create(Registries.ITEM, id));
		}
		return item(target).map(List::of).orElse(List.of());
	}

	static Optional<Item> item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
	}
}
