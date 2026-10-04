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
 * @param speciality an item id for a land's rarity (Expanse amber, prismite, sulfur...), or null
 * @param plot       the extraction plot working it (a quarry pit, a mine, a woodlot), or {@link PlotView#NONE}
 */
public record ResourceSite(int id, Kind kind, BlockPos center, BoundingBox area, int capacity, int remaining, float regenPerDay,
	float[] yields, @Nullable Identifier speciality, int plot) {

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
