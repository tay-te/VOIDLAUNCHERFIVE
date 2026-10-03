package dev.voidmc.hearth.plan.model;

import net.minecraft.nbt.CompoundTag;

/**
 * What the site survey learned about the land round a settlement's heart (PLANNING.md, "Site analysis"). Angles are
 * in radians in the x–z plane ({@code (cos a, sin a)} is the direction), heights are walking levels.
 *
 * @param gradX       mean rise per block toward +x over the inner site (a plane fitted to the ground)
 * @param gradZ       the same toward +z
 * @param relief      highest minus lowest ground within 64 blocks
 * @param water       what water is near: none, a river, the sea's coast or a lake
 * @param waterX      the nearest water column to the heart (when there is water)
 * @param waterAxis   the direction along the river or shore at that point
 * @param rise        a local high point within the town's reach: the chapel's hill
 * @param summit      the highest point within reach of a long road: the landmark's or the hold's crag
 * @param cliff       a sharp drop near the heart (a cliff face, a canyon wall, a stack's flank)
 * @param cliffAngle  the downhill direction across the cliff
 * @param buildable   the fraction of the surrounding land that is dry and gentle enough to build on
 * @param headland    a point of land with water on most sides (lighthouses), or none
 */
public record Site(int heartX, int heartY, int heartZ, float gradX, float gradZ, int relief, WaterKind water, int waterX, int waterZ,
	float waterAxis, int waterDistance, int riseX, int riseY, int riseZ, int summitX, int summitY, int summitZ, boolean cliff,
	float cliffAngle, int cliffDrop, float buildable, boolean hasHeadland, int headlandX, int headlandZ) {

	public double slope() {
		return Math.sqrt(this.gradX * this.gradX + this.gradZ * this.gradZ);
	}

	/** The direction along the contour (perpendicular to the fall line), or +x on flat ground. */
	public double contourAngle() {
		if (slope() < 1e-4) {
			return 0;
		}
		return StrictMath.atan2(this.gradX, -this.gradZ);
	}

	/** The uphill direction. */
	public double uphillAngle() {
		return StrictMath.atan2(this.gradZ, this.gradX);
	}

	public boolean hasWater() {
		return this.water != WaterKind.NONE;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putIntArray("heart", new int[]{this.heartX, this.heartY, this.heartZ});
		t.putFloat("grad_x", this.gradX);
		t.putFloat("grad_z", this.gradZ);
		t.putInt("relief", this.relief);
		t.putString("water", this.water.name());
		t.putIntArray("water_at", new int[]{this.waterX, this.waterZ, this.waterDistance});
		t.putFloat("water_axis", this.waterAxis);
		t.putIntArray("rise", new int[]{this.riseX, this.riseY, this.riseZ});
		t.putIntArray("summit", new int[]{this.summitX, this.summitY, this.summitZ});
		t.putBoolean("cliff", this.cliff);
		t.putFloat("cliff_angle", this.cliffAngle);
		t.putInt("cliff_drop", this.cliffDrop);
		t.putFloat("buildable", this.buildable);
		if (this.hasHeadland) {
			t.putIntArray("headland", new int[]{this.headlandX, this.headlandZ});
		}
		return t;
	}

	public static Site load(CompoundTag t) {
		int[] heart = Nbt.ints(t, "heart").orElse(new int[3]);
		int[] water = Nbt.ints(t, "water_at").orElse(new int[3]);
		int[] rise = Nbt.ints(t, "rise").orElse(new int[3]);
		int[] summit = Nbt.ints(t, "summit").orElse(new int[3]);
		int[] headland = Nbt.ints(t, "headland").orElse(null);
		WaterKind kind;
		try {
			kind = WaterKind.valueOf(t.getStringOr("water", "NONE"));
		} catch (IllegalArgumentException e) {
			kind = WaterKind.NONE;
		}
		return new Site(heart[0], heart[1], heart[2], t.getFloatOr("grad_x", 0), t.getFloatOr("grad_z", 0), t.getIntOr("relief", 0), kind,
			water[0], water[1], t.getFloatOr("water_axis", 0), water[2], rise[0], rise[1], rise[2], summit[0], summit[1], summit[2],
			t.getBooleanOr("cliff", false), t.getFloatOr("cliff_angle", 0), t.getIntOr("cliff_drop", 0), t.getFloatOr("buildable", 1),
			headland != null && headland.length >= 2, headland != null && headland.length >= 2 ? headland[0] : 0,
			headland != null && headland.length >= 2 ? headland[1] : 0);
	}

	public enum WaterKind {
		NONE, RIVER, COAST, LAKE
	}
}
