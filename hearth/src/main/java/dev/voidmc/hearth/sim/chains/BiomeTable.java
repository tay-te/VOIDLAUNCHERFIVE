package dev.voidmc.hearth.sim.chains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * What the land of a biome gives, beyond what the survey reads from the biome itself (its trees from its vegetation
 * features, its climate, its tags): the surface rock a quarry finds, the sand and clay of its banks, how fertile and how
 * open it is, and its rarities — Expanse's amber and prismite, vanilla's bamboo and cocoa. Data in
 * {@code hearth_sim/land/*.json}, by biome id and by biome tag (the id wins).
 */
public record BiomeTable(Map<Identifier, Entry> biomes, Map<Identifier, Entry> tags) {
	public static final BiomeTable EMPTY = new BiomeTable(Map.of(), Map.of());
	public static final Codec<BiomeTable> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Identifier.CODEC, Entry.CODEC).optionalFieldOf("biomes", Map.of()).forGetter(BiomeTable::biomes),
		Codec.unboundedMap(Identifier.CODEC, Entry.CODEC).optionalFieldOf("tags", Map.of()).forGetter(BiomeTable::tags)
	).apply(i, BiomeTable::new));

	public int size() {
		return this.biomes.size() + this.tags.size();
	}

	BiomeTable merge(BiomeTable o) {
		Map<Identifier, Entry> b = new LinkedHashMap<>(this.biomes);
		o.biomes.forEach((k, v) -> b.merge(k, v, Entry::over));
		Map<Identifier, Entry> t = new LinkedHashMap<>(this.tags);
		o.tags.forEach((k, v) -> t.merge(k, v, Entry::over));
		return new BiomeTable(b, t);
	}

	/**
	 * Every field is optional: what is left out comes from tags, then from the biome's own climate and features.
	 *
	 * @param rock        the surface rock a quarry cuts ({@code expanse:limestone})
	 * @param sand        the sand of its beaches and dunes
	 * @param clay        what its banks give for brick ({@code minecraft:clay}, {@code minecraft:mud})
	 * @param wood        the log its woods give, where the vegetation features do not say clearly
	 * @param trees       tree cover 0–1, overriding what the features give
	 * @param fertility   0–1: how good the ground is for fields
	 * @param pasture     0–1: how good for grazing
	 * @param rocky       bare rock at the surface (peaks, badlands, stony shores)
	 * @param salt        a salt pan
	 * @param ore         multipliers for the ore estimate ({@code {"gold": 3}} in the badlands)
	 * @param specialties the land's rarities
	 */
	public record Entry(Optional<Identifier> rock, Optional<Identifier> sand, Optional<Identifier> clay, Optional<Identifier> wood,
		Optional<Double> trees, Optional<Double> fertility, Optional<Double> pasture, Optional<Boolean> rocky, Optional<Boolean> salt,
		Map<String, Double> ore, List<Specialty> specialties) {
		public static final Entry NONE = new Entry(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of());
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("rock").forGetter(Entry::rock),
			Identifier.CODEC.optionalFieldOf("sand").forGetter(Entry::sand),
			Identifier.CODEC.optionalFieldOf("clay").forGetter(Entry::clay),
			Identifier.CODEC.optionalFieldOf("wood").forGetter(Entry::wood),
			Codec.doubleRange(0, 1).optionalFieldOf("trees").forGetter(Entry::trees),
			Codec.doubleRange(0, 1).optionalFieldOf("fertility").forGetter(Entry::fertility),
			Codec.doubleRange(0, 1).optionalFieldOf("pasture").forGetter(Entry::pasture),
			Codec.BOOL.optionalFieldOf("rocky").forGetter(Entry::rocky),
			Codec.BOOL.optionalFieldOf("salt").forGetter(Entry::salt),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("ore", Map.of()).forGetter(Entry::ore),
			Specialty.CODEC.listOf().optionalFieldOf("specialties", List.of()).forGetter(Entry::specialties)
		).apply(i, Entry::new));

		/** This entry, with what it leaves out taken from {@code under}. */
		public Entry over(Entry under) {
			Map<String, Double> ore = new LinkedHashMap<>(under.ore);
			ore.putAll(this.ore);
			List<Specialty> sp = new ArrayList<>(this.specialties);
			for (Specialty s : under.specialties) {
				if (sp.stream().noneMatch(x -> x.item.equals(s.item))) {
					sp.add(s);
				}
			}
			return new Entry(this.rock.or(under::rock), this.sand.or(under::sand), this.clay.or(under::clay), this.wood.or(under::wood),
				this.trees.or(under::trees), this.fertility.or(under::fertility), this.pasture.or(under::pasture), this.rocky.or(under::rocky),
				this.salt.or(under::salt), ore, sp);
		}
	}

	/**
	 * A land's rarity: who gathers it and how much.
	 *
	 * @param item    what it is ({@code expanse:amber}); also the site's material
	 * @param goods   the goods it counts as in the stores
	 * @param job     the trade that gathers it (a miner digs amber, a forester cuts bamboo)
	 * @param yield   units per worker-day at full density
	 * @param density how much of the biome's ground holds it, 0–1
	 * @param regen   share of capacity regrown a day (0 for minerals)
	 */
	public record Specialty(Identifier item, Goods goods, JobType job, double yield, double density, double regen) {
		public static final Codec<Specialty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(Specialty::item),
			Goods.CODEC.optionalFieldOf("goods", Goods.SPECIALTY).forGetter(Specialty::goods),
			JobType.CODEC.optionalFieldOf("job", JobType.NONE).forGetter(Specialty::job),
			Codec.doubleRange(0, 10000).optionalFieldOf("yield", 6.0).forGetter(Specialty::yield),
			Codec.doubleRange(0, 1).optionalFieldOf("density", 0.5).forGetter(Specialty::density),
			Codec.doubleRange(0, 1).optionalFieldOf("regen", 0.0).forGetter(Specialty::regen)
		).apply(i, Specialty::new));
	}
}
