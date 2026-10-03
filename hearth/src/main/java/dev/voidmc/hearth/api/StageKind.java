package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * The phases a blueprint is built in, in order. A builder finishes one before starting the next, so a half-built
 * house always looks like one a person is building: footings, then a frame, then walls, then a roof.
 */
public enum StageKind implements StringRepresentable {
	/** Clear the volume: vegetation, snow, loose blocks, anything in the way. */
	CLEAR("clear"),
	/** Level, terrace, fill and retain the ground; piles and stilts down to firm ground. */
	EARTHWORK("earthwork"),
	FOUNDATION("foundation"),
	/** Posts, beams, floors' joists: the skeleton. */
	FRAME("frame"),
	WALLS("walls"),
	FLOORS("floors"),
	ROOF("roof"),
	/** Doors, windows, shutters, glass. */
	OPENINGS("openings"),
	/** Furniture, beds, workstations, lights inside. */
	INTERIOR("interior"),
	/** Gardens, paths, fences, lanterns, flower boxes, signs outside. */
	EXTERIOR("exterior");

	public static final StringRepresentable.EnumCodec<StageKind> CODEC = StringRepresentable.fromEnum(StageKind::values);

	private final String name;

	StageKind(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
