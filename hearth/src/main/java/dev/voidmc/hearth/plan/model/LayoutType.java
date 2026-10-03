package dev.voidmc.hearth.plan.model;

import net.minecraft.util.StringRepresentable;

/**
 * The shape of a settlement, chosen at founding from its culture, its land and chance (PLANNING.md, "Layout types").
 * The numbers are each layout's character: how big its heart is, how wide and how far apart its streets run, how
 * tightly it packs its houses and how readily it builds up.
 *
 * <p>{@code coreScale} sets the dense core's radius as {@code coreScale · √population} blocks; {@code gaps} are the
 * side gaps between detached buildings in the core, the inner town and the outskirts.
 */
public enum LayoutType implements StringRepresentable {
	/** Expansive: a small hamlet heart and farmsteads strung for hundreds of blocks along tracks. */
	FARMSTEADS("farmsteads", true, false, 5.0, 5, 3, RoadKind.PATH, 34, 70, new int[]{2, 4, 8}, 0.2, false),
	/** A cross of main streets with a square where they meet, lanes branching into blocks. */
	CROSSROADS("crossroads", false, false, 3.4, 7, 5, RoadKind.STREET, 26, 56, new int[]{1, 2, 4}, 0.8, true),
	/** One long main street along a river, a coast or a ridge, lanes down to the water. */
	LINEAR("linear", false, false, 3.6, 6, 5, RoadKind.STREET, 24, 44, new int[]{1, 2, 4}, 0.8, true),
	/** Contour streets stepping up a slope, joined by stair streets; plots on terraces. */
	HILLSIDE("hillside", false, true, 3.0, 5, 3, RoadKind.STREET, 22, 60, new int[]{0, 1, 3}, 1.2, true),
	/** Tight and tall inside a wall ring: rows of townhouses on narrow frontages, alleys, a market square. */
	WALLED("walled", false, true, 2.5, 6, 5, RoadKind.STREET, 20, 40, new int[]{0, 1, 2}, 1.6, true),
	/** Boardwalks over the water, stilt houses along them, docks; the heart on the shore. */
	STILT("stilt", false, false, 3.2, 4, 3, RoadKind.LANE, 22, 48, new int[]{1, 2, 3}, 0.6, false),
	/** Stacked up a cliff or a stone stack: sills, stair streets and walkways, a town below and one on top. */
	CLIFF("cliff", false, true, 2.8, 4, 3, RoadKind.STREET, 20, 50, new int[]{0, 1, 2}, 1.4, true),
	/** A road climbing a mountainside in hairpins, houses on terraces at each bend, a hold at the top. */
	MOUNTAIN("mountain", false, true, 3.0, 4, 3, RoadKind.STREET, 20, 56, new int[]{0, 1, 3}, 1.2, true),
	/** Rings of houses round a market ring, spokes between them. */
	RING("ring", false, false, 3.4, 9, 5, RoadKind.STREET, 22, 40, new int[]{1, 2, 4}, 0.4, false);

	public static final StringRepresentable.EnumCodec<LayoutType> CODEC = StringRepresentable.fromEnum(LayoutType::values);

	private final String name;
	public final boolean expansive;
	public final boolean dense;
	public final double coreScale;
	public final int plazaHalf;
	public final int armWidth;
	public final RoadKind armKind;
	public final int laneSpacing;
	public final int laneLength;
	private final int[] gaps;
	public final double verticalBias;
	public final boolean terraces;

	LayoutType(String name, boolean expansive, boolean dense, double coreScale, int plazaHalf, int armWidth, RoadKind armKind,
		int laneSpacing, int laneLength, int[] gaps, double verticalBias, boolean terraces) {
		this.name = name;
		this.expansive = expansive;
		this.dense = dense;
		this.coreScale = coreScale;
		this.plazaHalf = plazaHalf;
		this.armWidth = armWidth;
		this.armKind = armKind;
		this.laneSpacing = laneSpacing;
		this.laneLength = laneLength;
		this.gaps = gaps;
		this.verticalBias = verticalBias;
		this.terraces = terraces;
	}

	/** The side gap between detached buildings in a zone (0 core, 1 inner, 2 outskirts and beyond). */
	public int gap(int zone) {
		return this.gaps[Math.max(0, Math.min(this.gaps.length - 1, zone))];
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public String translationKey() {
		return "hearth.plan.layout." + this.name;
	}

	public static LayoutType byName(String name) {
		for (LayoutType t : values()) {
			if (t.name.equals(name)) {
				return t;
			}
		}
		return CROSSROADS;
	}
}
