package dev.voidmc.hearth.plan.model;

import net.minecraft.util.StringRepresentable;

/**
 * A quarter of a town (town and city tiers): a seed point and a weight. A column belongs to the district whose seed is
 * nearest after dividing the distance by the weight, so quarters have organic, terrain-free boundaries that stay put
 * as the town grows.
 */
public record District(Kind kind, int x, int z, double weight) {
	public double score(int px, int pz) {
		double dx = px - this.x;
		double dz = pz - this.z;
		return Math.sqrt(dx * dx + dz * dz) / this.weight;
	}

	public enum Kind implements StringRepresentable {
		/** The square and the streets round it: shops, the market hall, the inn, the bank. */
		MARKET("market"),
		/** The town hall, the library and school, on high or central ground. */
		CIVIC("civic"),
		/** Workshops by the water or at the edge: smithy, tannery, sawmill, masons. */
		CRAFTS("crafts"),
		/** The rich houses on the hill: manors and large town houses. */
		RICH("rich"),
		RESIDENTIAL("residential"),
		/** The farmland outside the walls. */
		FARMS("farms"),
		/** Quays, warehouses and fisheries along the water. */
		HARBOUR("harbour");

		public static final StringRepresentable.EnumCodec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public static Kind byName(String name) {
			for (Kind k : values()) {
				if (k.name.equals(name)) {
					return k;
				}
			}
			return RESIDENTIAL;
		}
	}
}
