package dev.voidmc.fray.motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A rig (ANIMATION.md §4): named bones, their hierarchy and rest pose, and for custom rigs the geometry the client
 * builds its model from. Two kinds:
 * <ul>
 * <li>{@link #HUMANOID}, {@code fray:humanoid}: vanilla's {@code HumanoidModel} parts plus the virtual {@code root},
 * {@code torso}, {@code item_r}/{@code item_l} and {@code camera} bones; posed by {@link HumanoidSolver} over whatever
 * vanilla's {@code setupAnim} produced. Players and vanilla humanoid mobs keep Minecraft's shape.</li>
 * <li>custom rigs (VOID's own creatures): any hierarchy with real elbows, knees, spines and chains, authored in
 * Blockbench and converted by {@code bbImport} into a rig file (cubes, UVs, pivots in vanilla's model space, exactly
 * what Blockbench's own Java export writes); posed by {@link RigSolver} over the rest pose.</li>
 * </ul>
 * Model space is vanilla's everywhere: pixels, Y down, the model's front is -Z, a root bone's offset measured from the
 * model origin (which sits {@link #modelYOffset} blocks above the feet).
 *
 * <p>Common code: the bake places hit volumes with it, the client draws with it; the dedicated server never needs it
 * (it reads baked timelines).
 */
public final class Rig {
	/** One box: vanilla's {@code CubeListBuilder.texOffs(u, v).addBox(x, y, z, dx, dy, dz, new CubeDeformation(inflate))}. */
	public record Cube(float x, float y, float z, float dx, float dy, float dz, int u, int v, float inflate, boolean mirror) {
	}

	/** A named point pair on a bone (model pixels in the bone's frame): a capsule from a to b with a radius in blocks. */
	public record Locator(String name, int bone, float[] a, float[] b, float radius) {
	}

	public final Identifier id;
	public final List<String> names;
	/** Parent bone index per bone, -1 for top-level bones. Parents come before their children. */
	public final int[] parent;
	/** Rest pose per bone, relative to the parent (model pixels, radians): vanilla's {@code PartPose}. */
	public final PartPose[] rest;
	public final boolean humanoid;
	/** Bones a clip may not animate (cube rotation helpers, Blockbench's {@code <cube>_r1} groups). */
	public final boolean[] helper;
	public final List<Cube>[] cubes;
	public final @Nullable Identifier texture;
	public final int textureWidth;
	public final int textureHeight;
	/** The renderer's model scale (1 for most; players and mannequins draw at 15/16). */
	public final float modelScale;
	public final float shadowRadius;
	public final Map<String, Locator> locators;
	/** SHA-256 of the rig file's bytes ("" for the built-in humanoid): part of the clip-set hash for custom rigs. */
	public final String hash;

	/** Vanilla's {@code EntityModel.MODEL_Y_OFFSET} as applied by {@code LivingEntityRenderer}: model origin above the feet. */
	public static final float MODEL_Y_OFFSET = 1.501F;

	public static final Rig HUMANOID = humanoid();

	@SuppressWarnings("unchecked")
	public Rig(Identifier id, List<String> names, int[] parent, PartPose[] rest, boolean humanoid, boolean[] helper, List<Cube>[] cubes,
		@Nullable Identifier texture, int textureWidth, int textureHeight, float modelScale, float shadowRadius, Map<String, Locator> locators, String hash) {
		this.id = id;
		this.names = List.copyOf(names);
		this.parent = parent;
		this.rest = rest;
		this.humanoid = humanoid;
		this.helper = helper;
		this.cubes = cubes;
		this.texture = texture;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.modelScale = modelScale;
		this.shadowRadius = shadowRadius;
		this.locators = Collections.unmodifiableMap(new LinkedHashMap<>(locators));
		this.hash = hash;
		for (int i = 0; i < parent.length; i++) {
			if (parent[i] >= i) {
				throw new IllegalArgumentException(id + ": bone " + names.get(i) + " comes before its parent");
			}
		}
	}

	public int bones() {
		return this.names.size();
	}

	/** The bone's index, or -1. */
	public int find(String name) {
		return this.names.indexOf(name);
	}

	public int bone(String name) {
		int i = this.names.indexOf(name);
		if (i < 0) {
			throw new IllegalArgumentException(this.id + " has no bone '" + name + "' (bones: " + this.names + ")");
		}
		return i;
	}

	public String name(int bone) {
		return this.names.get(bone);
	}

	/** A fresh copy of the rest pose, one entry per bone. */
	public PartPose[] restPose() {
		PartPose[] out = new PartPose[this.rest.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = new PartPose().set(this.rest[i]);
		}
		return out;
	}

	/** The left/right twin of a bone name ({@code right_arm} ↔ {@code left_arm}, {@code r_hand} ↔ {@code l_hand}), or the name. */
	public static String mirrorName(String n) {
		if (n.contains("right")) {
			return n.replace("right", "left");
		}
		if (n.contains("left")) {
			return n.replace("left", "right");
		}
		if (n.endsWith("_r")) {
			return n.substring(0, n.length() - 2) + "_l";
		}
		if (n.endsWith("_l")) {
			return n.substring(0, n.length() - 2) + "_r";
		}
		if (n.startsWith("r_")) {
			return "l_" + n.substring(2);
		}
		if (n.startsWith("l_")) {
			return "r_" + n.substring(2);
		}
		return n;
	}

	@SuppressWarnings("unchecked")
	private static Rig humanoid() {
		List<String> names = HumanoidRig.NAMES;
		int n = names.size();
		int[] parent = new int[n];
		java.util.Arrays.fill(parent, -1);
		List<Cube>[] cubes = new List[n];
		for (int i = 0; i < n; i++) {
			cubes[i] = new ArrayList<>();
		}
		// Vanilla's HumanoidModel.createMesh boxes (for the bake's ground and first-person checks; the client draws vanilla's).
		cubes[HumanoidRig.HEAD].add(new Cube(-4, -8, -4, 8, 8, 8, 0, 0, 0, false));
		cubes[HumanoidRig.BODY].add(new Cube(-4, 0, -2, 8, 12, 4, 16, 16, 0, false));
		cubes[HumanoidRig.RIGHT_ARM].add(new Cube(-3, -2, -2, 4, 12, 4, 40, 16, 0, false));
		cubes[HumanoidRig.LEFT_ARM].add(new Cube(-1, -2, -2, 4, 12, 4, 40, 16, 0, true));
		cubes[HumanoidRig.RIGHT_LEG].add(new Cube(-2, 0, -2, 4, 12, 4, 0, 16, 0, false));
		cubes[HumanoidRig.LEFT_LEG].add(new Cube(-2, 0, -2, 4, 12, 4, 0, 16, 0, true));
		return new Rig(HumanoidRig.ID, names, parent, HumanoidRig.restPose(), true, new boolean[n], cubes, null, 64, 64, 1, 0.5F, Map.of(), "");
	}
}
