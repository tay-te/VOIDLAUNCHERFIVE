package dev.voidmc.fray.motion;

import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * The humanoid rig {@code fray:humanoid} (ANIMATION.md §4.1): bone names, which vanilla {@code HumanoidModel} part each
 * drives, the rest pose of an adult humanoid (verified against {@code HumanoidModel.createMesh} and
 * {@code PlayerModel.createMesh} in 26.3), the hip pivot of the virtual torso and the root's pivot.
 *
 * <p>Model space is vanilla's: pixels, Y down, the model's front is -Z; the neck is at the origin and the feet at Y 24.
 * The server never sees a {@code ModelPart}: it reads these numbers to place locators (the bake) and hit volumes.
 */
public final class HumanoidRig {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("fray", "humanoid");

	public static final int ROOT = 0;
	public static final int TORSO = 1;
	public static final int HEAD = 2;
	public static final int BODY = 3;
	public static final int RIGHT_ARM = 4;
	public static final int LEFT_ARM = 5;
	public static final int RIGHT_LEG = 6;
	public static final int LEFT_LEG = 7;
	public static final int ITEM_R = 8;
	public static final int ITEM_L = 9;
	public static final int CAMERA = 10;
	public static final int BONES = 11;

	public static final List<String> NAMES = List.of("root", "torso", "head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
		"item_r", "item_l", "camera");

	/** The real model parts, in bone order HEAD..LEFT_LEG. */
	public static final int FIRST_PART = HEAD;
	public static final int LAST_PART = LEFT_LEG;

	/** The torso's pivot: the hips, in model pixels. */
	public static final float HIP_X = 0, HIP_Y = 12, HIP_Z = 0;
	/** Root rotations (rolls, kneels) turn the whole body about this point (the hips, the body's centre of mass). */
	public static final float ROOT_PIVOT_X = 0, ROOT_PIVOT_Y = 12, ROOT_PIVOT_Z = 0;
	/** Vanilla's {@code EntityModel.MODEL_Y_OFFSET}: the model origin sits this many blocks above the feet. */
	public static final float MODEL_Y_OFFSET = 1.501F;
	/** {@code AvatarRenderer.scale}: players and mannequins are drawn at 15/16. */
	public static final float PLAYER_SCALE = 0.9375F;

	private HumanoidRig() {
	}

	public static int bone(String name) {
		int i = NAMES.indexOf(name);
		if (i < 0) {
			throw new IllegalArgumentException("fray:humanoid has no bone '" + name + "' (bones: " + NAMES + ")");
		}
		return i;
	}

	/** The rest pose of an adult humanoid: a fresh array, one entry per bone (virtual bones at zero). */
	public static PartPose[] restPose() {
		PartPose[] rest = new PartPose[BONES];
		for (int i = 0; i < BONES; i++) {
			rest[i] = new PartPose();
		}
		rest[RIGHT_ARM].setPos(-5, 2, 0);
		rest[LEFT_ARM].setPos(5, 2, 0);
		rest[RIGHT_LEG].setPos(-1.9F, 12, 0);
		rest[LEFT_LEG].setPos(1.9F, 12, 0);
		return rest;
	}
}
