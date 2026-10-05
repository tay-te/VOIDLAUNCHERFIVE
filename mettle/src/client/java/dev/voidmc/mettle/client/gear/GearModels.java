package dev.voidmc.mettle.client.gear;

import dev.voidmc.mettle.Mettle;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The worn gear's small cuboid models, in the player body's pixel space (body x −4..4, y 0..12, z −2..2; the back is
 * +z). Each model is four parts: on the back or on the front (straps), and dyeable (leather, canvas) or fixed (buckles,
 * wood, arrows). Textures are 128×64 material atlases made from the item sprites' colours: every cube of a material
 * maps onto the same 32×16 cell, so cube sizes never matter.
 */
public final class GearModels {
	/** Material cells in the atlas, as (u, v). */
	private static final int[] LEATHER = {0, 0};
	private static final int[] STRAP = {32, 0};
	private static final int[] METAL = {64, 0};
	private static final int[] ACCENT = {96, 0};
	private static final int[] SHAFT = {0, 16};
	private static final int[] WOOD = {32, 16};
	private static final int[] CANVAS = {64, 16};
	private static final int[] ROLL = {96, 16};
	private static final int[] LEATHER_LIGHT = {0, 32};

	/** One worn model. {@code depth} is how far it sticks out from the back (a quiver sits on top of a pack). */
	public record Model(Identifier texture, Identifier dyedTexture, @Nullable ModelPart backDye, @Nullable ModelPart backFixed,
		@Nullable ModelPart frontDye, @Nullable ModelPart frontFixed, float depth) {
	}

	private static final Map<String, Model> MODELS = new HashMap<>();

	private GearModels() {
	}

	/**
	 * The model for a gear item by its id path; unknown gear (data-made) borrows the closest shipped look. Belts are
	 * built per armour layer they must wrap around: 0 bare, 1 leggings, 2 chest armour.
	 */
	public static Model get(String path, String fallback, int beltLayer) {
		String key = switch (path) {
			case "satchel", "leather_pack", "frame_pack", "belt", "tool_belt", "quiver", "hunters_quiver" -> path;
			default -> fallback;
		};
		String cacheKey = key.endsWith("belt") ? key + "#" + beltLayer : key;
		return MODELS.computeIfAbsent(cacheKey, k -> build(key, beltLayer));
	}

	private static Model build(String key, int beltLayer) {
		Parts back = new Parts();
		Parts front = new Parts();
		float depth = 0f;
		switch (key) {
			case "satchel" -> {
				back.dye(LEATHER, -3f, 6f, 2f, 6f, 5f, 3f);
				back.dye(LEATHER_LIGHT, -3.2f, 5.5f, 1.9f, 6.4f, 2.5f, 3.4f);
				back.fixed(METAL, -0.5f, 7.2f, 5f, 1f, 1.2f, 0.4f);
				back.dye(STRAP, 1.5f, 0f, 2f, 1f, 6f, 0.6f);
				front.dye(STRAP, 1.5f, 0f, -2.6f, 1f, 9f, 0.6f);
				front.dye(STRAP, 1.5f, -0.6f, -2.6f, 1f, 0.6f, 5.2f);
				depth = 3f;
			}
			case "leather_pack" -> {
				back.dye(LEATHER, -3.5f, 1f, 2f, 7f, 8f, 4f);
				back.dye(LEATHER_LIGHT, -3.7f, 0.6f, 1.9f, 7.4f, 3f, 4.4f);
				back.dye(LEATHER, -4.5f, 5f, 3f, 1f, 3f, 2.5f);
				back.dye(LEATHER, 3.5f, 5f, 3f, 1f, 3f, 2.5f);
				back.fixed(METAL, -2.2f, 3f, 6f, 1f, 1.2f, 0.3f);
				back.fixed(METAL, 1.2f, 3f, 6f, 1f, 1.2f, 0.3f);
				shoulderStraps(front);
				depth = 4f;
			}
			case "frame_pack" -> {
				back.fixed(WOOD, -4.5f, -2f, 2f, 1f, 14f, 1f);
				back.fixed(WOOD, 3.5f, -2f, 2f, 1f, 14f, 1f);
				back.fixed(WOOD, -4.5f, -2f, 2.1f, 9f, 1f, 0.8f);
				back.fixed(WOOD, -4.5f, 10f, 2.1f, 9f, 1f, 0.8f);
				back.dye(CANVAS, -3.5f, 1f, 3f, 7f, 9f, 4f);
				back.dye(LEATHER, -3.7f, 0.5f, 2.9f, 7.4f, 2.5f, 4.4f);
				back.fixed(METAL, -0.6f, 4f, 7f, 1.2f, 1.2f, 0.3f);
				back.fixed(ROLL, -5f, -3.5f, 3f, 10f, 2.5f, 2.5f);
				shoulderStraps(front);
				depth = 5f;
			}
			case "belt", "tool_belt" -> {
				float d = beltLayer == 2 ? 1.15f : beltLayer == 1 ? 0.65f : 0.3f;
				back.dyeDeformed(LEATHER, -4f, 10f, -2f, 8f, 1.5f, 4f, d);
				front.fixed(METAL, -1f, 9.8f, -2f - d - 0.3f, 2f, 1.9f, 0.4f);
				if (key.equals("tool_belt")) {
					front.dye(LEATHER_LIGHT, 2f, 11f, -2f - d - 1.1f, 2f, 2.5f, 1.1f);
					back.fixed(WOOD, -4f - d - 0.6f, 11f, -0.3f, 0.6f, 4f, 0.6f);
					back.fixed(METAL, -4f - d - 0.8f, 10.4f, -1.2f, 1f, 1f, 2.4f);
				}
			}
			default -> {
				boolean hunter = key.equals("hunters_quiver");
				float w = hunter ? 3.5f : 3f;
				float h = hunter ? 12f : 11f;
				PartPose pose = PartPose.offsetAndRotation(0f, 5.5f, 2f, 0f, 0f, 0.55f);
				back.dyePosed(LEATHER, -w / 2, -h / 2, 0f, w, h, w, pose);
				back.dyePosed(LEATHER_LIGHT, -w / 2 - 0.2f, -h / 2 - 0.4f, -0.2f, w + 0.4f, 1.4f, w + 0.4f, pose);
				back.dyePosed(STRAP, -w / 2 - 0.1f, h / 2 - 3f, -0.1f, w + 0.2f, 0.8f, w + 0.2f, pose);
				for (int i = 0; i < (hunter ? 4 : 3); i++) {
					float x = -w / 2 + 0.4f + i * (w - 1.2f) / (hunter ? 3 : 2);
					float z = 0.5f + (i % 2) * (w - 1.6f);
					back.fixedPosed(SHAFT, x, -h / 2 - 2.5f, z, 0.5f, 2.5f, 0.5f, pose);
					back.fixedPosed(ACCENT, x - 0.25f, -h / 2 - 4.5f, z - 0.25f, 1f, 2f, 1f, pose);
				}
				if (hunter) {
					back.fixedPosed(METAL, -w / 2 - 0.05f, -1f, -0.05f, w + 0.1f, 0.6f, w + 0.1f, pose);
				}
				front.dyePosed(STRAP, -0.5f, -7f, 0f, 1f, 14f, 0.6f, PartPose.offsetAndRotation(0f, 6f, -2.6f, 0f, 0f, -0.6f));
			}
		}
		Identifier texture = Mettle.id("textures/entity/gear/" + key + ".png");
		Identifier dyed = Mettle.id("textures/entity/gear/" + key + "_dye.png");
		return new Model(texture, dyed, back.bakeDye(), back.bakeFixed(), front.bakeDye(), front.bakeFixed(), depth);
	}

	private static void shoulderStraps(Parts front) {
		front.dye(STRAP, -3f, 0f, -2.6f, 1.2f, 9f, 0.6f);
		front.dye(STRAP, 1.8f, 0f, -2.6f, 1.2f, 9f, 0.6f);
		front.dye(STRAP, -3f, -0.6f, -2.6f, 1.2f, 0.6f, 4.8f);
		front.dye(STRAP, 1.8f, -0.6f, -2.6f, 1.2f, 0.6f, 4.8f);
	}

	/** Collects cubes into a dyeable and a fixed mesh. */
	private static final class Parts {
		private final MeshDefinition dyeMesh = new MeshDefinition();
		private final MeshDefinition fixedMesh = new MeshDefinition();
		private int dyeCount;
		private int fixedCount;

		void dye(int[] uv, float x, float y, float z, float w, float h, float d) {
			dyePosed(uv, x, y, z, w, h, d, PartPose.ZERO);
		}

		void dyeDeformed(int[] uv, float x, float y, float z, float w, float h, float d, float grow) {
			add(dyeMesh.getRoot(), "c" + dyeCount++, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d, new CubeDeformation(grow)), PartPose.ZERO);
		}

		void dyePosed(int[] uv, float x, float y, float z, float w, float h, float d, PartPose pose) {
			add(dyeMesh.getRoot(), "c" + dyeCount++, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d), pose);
		}

		void fixed(int[] uv, float x, float y, float z, float w, float h, float d) {
			fixedPosed(uv, x, y, z, w, h, d, PartPose.ZERO);
		}

		void fixedPosed(int[] uv, float x, float y, float z, float w, float h, float d, PartPose pose) {
			add(fixedMesh.getRoot(), "c" + fixedCount++, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d), pose);
		}

		private static void add(PartDefinition root, String name, CubeListBuilder cube, PartPose pose) {
			root.addOrReplaceChild(name, cube, pose);
		}

		@Nullable ModelPart bakeDye() {
			return dyeCount == 0 ? null : LayerDefinition.create(dyeMesh, 128, 64).bakeRoot();
		}

		@Nullable ModelPart bakeFixed() {
			return fixedCount == 0 ? null : LayerDefinition.create(fixedMesh, 128, 64).bakeRoot();
		}
	}
}
