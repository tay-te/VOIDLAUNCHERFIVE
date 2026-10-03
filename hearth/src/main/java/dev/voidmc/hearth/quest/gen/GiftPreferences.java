package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * What a resident would like as a gift, from their traits (written by the NPC module in {@code extra.npc.traits}, read
 * defensively), their trade and their age: a baker wants cocoa and sugar, a scholar a book, a child cookies or a flower.
 */
public final class GiftPreferences {
	/** A gift: the item and how many make a proper present. */
	public record Gift(Identifier item, int count) {
	}

	private static final Map<String, List<Gift>> BY_TRAIT = new HashMap<>();
	private static final Map<JobType, List<Gift>> BY_JOB = new EnumMap<>(JobType.class);
	private static final List<Gift> CHILD = List.of(g("cookie", 6), g("poppy", 3), g("cake", 1), g("slime_ball", 2), g("glow_berries", 4));
	private static final List<Gift> ELDER = List.of(g("pumpkin_pie", 2), g("lily_of_the_valley", 3), g("honey_bottle", 2), g("book", 1));
	private static final List<Gift> ANYONE = List.of(g("honey_bottle", 2), g("cake", 1), g("sweet_berries", 12), g("cornflower", 4));

	static {
		trait("sweet_tooth", g("cake", 1), g("cookie", 8), g("pumpkin_pie", 3), g("honey_bottle", 2));
		trait("bookish", g("book", 3), g("writable_book", 1), g("ink_sac", 4));
		trait("curious", g("spyglass", 1), g("compass", 1), g("amethyst_shard", 6));
		trait("romantic", g("rose_bush", 2), g("peony", 3), g("lilac", 2), g("cake", 1));
		trait("devout", g("candle", 6), g("honeycomb", 4), g("lily_of_the_valley", 4));
		trait("green_thumb", g("flowering_azalea", 2), g("bone_meal", 16), g("cherry_sapling", 2));
		trait("musical", g("note_block", 1), g("jukebox", 1), g("goat_horn", 1));
		trait("adventurous", g("map", 2), g("compass", 1), g("spyglass", 1));
		trait("vain", g("amethyst_shard", 8), g("gold_ingot", 2), g("pink_dye", 6));
		trait("hardworking", g("iron_ingot", 6), g("lantern", 2), g("cooked_beef", 8));
		trait("cheerful", g("glow_berries", 8), g("sunflower", 3), g("cake", 1));
		trait("grumpy", g("coal", 16), g("cooked_porkchop", 8), g("pumpkin_pie", 2));
		trait("shy", g("lily_of_the_valley", 3), g("cornflower", 3), g("candle", 3));
		trait("brave", g("iron_ingot", 6), g("arrow", 32), g("shield", 1));
		trait("greedy", g("gold_ingot", 3), g("emerald", 4), g("gold_nugget", 18));
		trait("kind", g("bread", 12), g("poppy", 4), g("honey_bottle", 2));

		job(JobType.FARMER, g("bone_meal", 24), g("pumpkin_pie", 3), g("melon_seeds", 8));
		job(JobType.BUILDER, g("lantern", 3), g("ladder", 16), g("iron_ingot", 4));
		job(JobType.FORESTER, g("cherry_sapling", 2), g("iron_axe", 1), g("dark_oak_sapling", 4));
		job(JobType.QUARRIER, g("amethyst_shard", 6), g("lapis_lazuli", 8), g("torch", 32));
		job(JobType.FISHER, g("string", 12), g("fishing_rod", 1), g("dried_kelp_block", 4));
		job(JobType.HERDER, g("wheat", 24), g("lead", 2), g("carrot", 16));
		job(JobType.HUNTER, g("arrow", 32), g("leather", 8), g("feather", 16));
		job(JobType.CARPENTER, g("honeycomb", 6), g("cherry_log", 8), g("iron_axe", 1));
		job(JobType.MASON, g("amethyst_shard", 8), g("quartz", 12), g("chiseled_stone_bricks", 8));
		job(JobType.SMITH, g("gold_ingot", 3), g("lava_bucket", 1), g("copper_ingot", 12));
		job(JobType.BAKER, g("sugar", 16), g("cocoa_beans", 12), g("honey_bottle", 3));
		job(JobType.BREWER, g("nether_wart", 8), g("sweet_berries", 24), g("glistering_melon_slice", 2));
		job(JobType.INNKEEPER, g("honey_bottle", 4), g("sweet_berries", 24), g("candle", 6));
		job(JobType.MERCHANT, g("emerald", 4), g("gold_nugget", 18), g("bundle", 1));
		job(JobType.MINTER, g("gold_ingot", 3), g("copper_ingot", 16), g("raw_gold", 4));
		job(JobType.GUARD, g("iron_ingot", 6), g("arrow", 32), g("cooked_beef", 8));
		job(JobType.PRIEST, g("candle", 8), g("honeycomb", 4), g("lily_of_the_valley", 4));
		job(JobType.SCHOLAR, g("book", 3), g("ink_sac", 6), g("feather", 8), g("amethyst_shard", 4));
		job(JobType.COURIER, g("leather_boots", 1), g("compass", 1), g("bread", 12));
		job(JobType.TAILOR, g("purple_dye", 6), g("string", 16), g("white_wool", 16));
	}

	private GiftPreferences() {
	}

	/** A gift this resident would be glad of, among items that exist. */
	public static Gift pick(Resident resident, RandomSource random, Predicate<Identifier> exists) {
		List<Gift> pool = new ArrayList<>();
		for (String trait : traits(resident)) {
			pool.addAll(BY_TRAIT.getOrDefault(trait, List.of()));
		}
		if (resident.age == AgeStage.CHILD) {
			pool.addAll(CHILD);
		} else {
			pool.addAll(BY_JOB.getOrDefault(resident.job, List.of()));
			if (resident.age == AgeStage.ELDER) {
				pool.addAll(ELDER);
			}
		}
		if (pool.isEmpty()) {
			pool.addAll(ANYONE);
		}
		pool.removeIf(gift -> !exists.test(gift.item));
		return pool.isEmpty() ? ANYONE.getFirst() : pool.get(random.nextInt(pool.size()));
	}

	/** The resident's traits as the NPC module wrote them: {@code extra.npc.traits}, a list of strings or a comma string. */
	public static List<String> traits(Resident resident) {
		CompoundTag npc = resident.extra.getCompoundOrEmpty("npc");
		List<String> out = new ArrayList<>();
		Tag tag = npc.get("traits");
		if (tag instanceof ListTag list) {
			for (Tag t : list) {
				t.asString().ifPresent(s -> out.add(s.toLowerCase(Locale.ROOT)));
			}
		} else if (tag != null) {
			tag.asString().ifPresent(s -> {
				for (String part : s.split(",")) {
					if (!part.isBlank()) {
						out.add(part.trim().toLowerCase(Locale.ROOT));
					}
				}
			});
		}
		return out;
	}

	private static Gift g(String path, int count) {
		return new Gift(Identifier.withDefaultNamespace(path), count);
	}

	private static void trait(String trait, Gift... gifts) {
		BY_TRAIT.put(trait, List.of(gifts));
	}

	private static void job(JobType job, Gift... gifts) {
		BY_JOB.put(job, List.of(gifts));
	}
}
