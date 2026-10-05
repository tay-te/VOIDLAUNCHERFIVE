package dev.voidmc.hearth.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A place in a settlement's territory the land gives something (docs/LIVING_WORLD.md §7.1): a wood, a rock face, an ore
 * hill, a clay bank, a fishing water, fertile flats. Found by the sim module's survey from the terrain probe and the biome
 * data, without loading chunks. Sites deplete as they are worked and regenerate (forests replanted, pasture rested, fish
 * returning); a settlement thrives on its sites and declines when they run out.
 *
 * @param area       the ground the site covers, world coordinates
 * @param capacity   units of its goods the site holds when full
 * @param remaining  units left now
 * @param regenPerDay units it recovers per sim day (0 for ore and stone, which do not come back)
 * @param yields     the goods it gives (by {@link Goods#ordinal()}), units per worker-day at full capacity
 * @param material   the block or item it gives: a wood's log ({@code minecraft:birch_log}), a quarry face's rock
 *                   ({@code expanse:limestone}), a bank's clay or sand, the main raw ore, a rarity's item; null for fertile
 *                   land, pasture, fish and salt. The planner resolves {@code local} materials and {@code rock} from it.
 * @param speciality an item id for a land's rarity (Expanse amber, prismite, sulfur...) on {@link Kind#SPECIALTY} sites,
 *                   else null
 * @param plot       the extraction plot working it (a quarry pit, a mine, a woodlot), or {@link PlotView#NONE}
 * @param oreGrade   ore per block of rock cut here (the survey's estimate from depth, biome and caverns; a mine that follows
 *                   the seams finds the sim's {@code seam_factor} times more)
 * @param coalGrade  coal (fuel) per block of rock cut here
 */
public record ResourceSite(int id, Kind kind, BlockPos center, BoundingBox area, int capacity, int remaining, float regenPerDay,
	float[] yields, @Nullable Identifier material, @Nullable Identifier speciality, int plot, float oreGrade, float coalGrade) {

	/** A site without grades; {@code material} is also its speciality when it is a {@link Kind#SPECIALTY} site. */
	public ResourceSite(int id, Kind kind, BlockPos center, BoundingBox area, int capacity, int remaining, float regenPerDay, float[] yields,
		@Nullable Identifier material, int plot) {
		this(id, kind, center, area, capacity, remaining, regenPerDay, yields, material, kind == Kind.SPECIALTY ? material : null, plot, 0, 0);
	}

	public boolean depleted() {
		return this.remaining <= 0 && this.regenPerDay <= 0;
	}

	public enum Kind implements StringRepresentable {
		WOODS("woods"),
		STONE("stone"),
		ORE("ore"),
		CLAY("clay"),
		SAND("sand"),
		SALT("salt"),
		FERTILE("fertile"),
		PASTURE("pasture"),
		FISH("fish"),
		/** A land's rarity: an amber seam, a prismite outcrop, sulfur vents, a glowcap grotto. */
		SPECIALTY("specialty");

		public static final StringRepresentable.EnumCodec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
