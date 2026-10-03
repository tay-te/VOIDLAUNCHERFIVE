package dev.voidmc.hearth.plan.model;

import net.minecraft.util.StringRepresentable;

/**
 * The {@code kind} param of {@code hearth:infra/road}: how grand a road is, which the infrastructure blueprints turn
 * into surface, edging and lamps. Widths are the planner's defaults (PLANNING.md); every width is odd.
 */
public enum RoadKind implements StringRepresentable {
	/** A road leading out of town between settlements: verges, milestones, few lamps. */
	HIGHWAY("highway"),
	/** A main street: paved, curbs, lamps by tier. */
	STREET("street"),
	/** A side street. */
	LANE("lane"),
	/** A narrow passage between rows in a dense core. */
	ALLEY("alley"),
	/** An unpaved footpath or farm track: field lanes, the way to a farmstead, a garden path to a door. */
	PATH("path");

	public static final StringRepresentable.EnumCodec<RoadKind> CODEC = StringRepresentable.fromEnum(RoadKind::values);

	private final String name;

	RoadKind(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public static RoadKind byName(String name) {
		for (RoadKind k : values()) {
			if (k.name.equals(name)) {
				return k;
			}
		}
		return LANE;
	}
}
