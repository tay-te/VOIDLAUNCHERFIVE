package dev.voidmc.hearth.client.npc.travel;

import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The carts of the roads (docs/LIVING_WORLD_2.md §1.3), modelled in code as boxes over one 128 × 128 texture per kind:
 * a covered four-wheel wagon, a two-wheel cart with shafts for a mule or an ox and its load on the bed (sacks, barrels,
 * crates, logs, stone), a pedlar's handcart with its crates. Model space: y down, the ground at y 24, the front (shafts)
 * toward -z.
 */
public class CartModel extends EntityModel<CartRenderState> {
	private final List<ModelPart> wheels = new ArrayList<>();
	private final float wheelRadius;
	private final ModelPart[] loads = new ModelPart[CartEntity.Load.values().length];

	public CartModel(ModelPart root, CartEntity.Kind kind) {
		super(root);
		for (String name : new String[]{"wheel_fl", "wheel_fr", "wheel_bl", "wheel_br", "wheel_l", "wheel_r"}) {
			if (root.hasChild(name)) {
				this.wheels.add(root.getChild(name));
			}
		}
		this.wheelRadius = switch (kind) {
			case WAGON -> 7 / 16F;
			case CART -> 8 / 16F;
			case HANDCART -> 5 / 16F;
		};
		for (CartEntity.Load l : CartEntity.Load.values()) {
			String name = "load_" + l.name().toLowerCase(java.util.Locale.ROOT);
			if (root.hasChild(name)) {
				this.loads[l.ordinal()] = root.getChild(name);
			}
		}
	}

	@Override
	public void setupAnim(CartRenderState state) {
		super.setupAnim(state);
		float spin = state.rolled / this.wheelRadius;
		for (ModelPart w : this.wheels) {
			w.xRot = -spin;
		}
		// the load shown: its own part, or the nearest there is (produce rides as crates, bedrolls as sacks)
		CartEntity.Load shown = state.load;
		if (shown == CartEntity.Load.PRODUCE && this.loads[shown.ordinal()] == null) {
			shown = CartEntity.Load.CRATES;
		}
		if (shown == CartEntity.Load.BEDROLLS && this.loads[shown.ordinal()] == null) {
			shown = CartEntity.Load.SACKS;
		}
		for (CartEntity.Load l : CartEntity.Load.values()) {
			ModelPart p = this.loads[l.ordinal()];
			if (p != null) {
				// a handcart always carries its crates
				p.visible = l == shown || state.kind == CartEntity.Kind.HANDCART;
			}
		}
	}

	private static void wheel(PartDefinition root, String name, int u, int v, int radius, float x, float y, float z) {
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-1, -radius, -radius, 2, radius * 2, radius * 2), PartPose.offset(x, y, z));
	}

	public static LayerDefinition wagon() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 0).addBox(-12, 0, -20, 24, 3, 40), PartPose.offset(0, 13, 0));
		root.addOrReplaceChild("canvas", CubeListBuilder.create().texOffs(0, 43).addBox(-11, -12, -17, 22, 12, 34), PartPose.offset(0, 13, 0));
		wheel(root, "wheel_fl", 0, 89, 7, -13, 17, -13);
		wheel(root, "wheel_fr", 0, 89, 7, 13, 17, -13);
		wheel(root, "wheel_bl", 0, 89, 7, -13, 17, 13);
		wheel(root, "wheel_br", 0, 89, 7, 13, 17, 13);
		root.addOrReplaceChild("axle_f", CubeListBuilder.create().texOffs(32, 89).addBox(-13, -1, -1, 26, 2, 2), PartPose.offset(0, 17, -13));
		root.addOrReplaceChild("axle_b", CubeListBuilder.create().texOffs(32, 89).addBox(-13, -1, -1, 26, 2, 2), PartPose.offset(0, 17, 13));
		root.addOrReplaceChild("shaft_l", CubeListBuilder.create().texOffs(32, 93).addBox(-0.5F, -0.5F, -22, 1, 1, 22),
			PartPose.offsetAndRotation(-5, 15, -20, -0.12F, 0, 0));
		root.addOrReplaceChild("shaft_r", CubeListBuilder.create().texOffs(32, 93).addBox(-0.5F, -0.5F, -22, 1, 1, 22),
			PartPose.offsetAndRotation(5, 15, -20, -0.12F, 0, 0));
		root.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(80, 89).addBox(-8, -3, -2, 16, 3, 4), PartPose.offset(0, 13, -18));
		return LayerDefinition.create(mesh, 128, 128);
	}

	public static LayerDefinition cart() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 0).addBox(-9, 0, -13, 18, 3, 26), PartPose.offset(0, 12, 2));
		root.addOrReplaceChild("side_l", CubeListBuilder.create().texOffs(0, 29).addBox(-0.5F, -5, -13, 1, 5, 26), PartPose.offset(-9, 12, 2));
		root.addOrReplaceChild("side_r", CubeListBuilder.create().texOffs(0, 29).addBox(-0.5F, -5, -13, 1, 5, 26), PartPose.offset(9, 12, 2));
		root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(54, 29).addBox(-9, -5, -0.5F, 18, 5, 1), PartPose.offset(0, 12, -11));
		root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(54, 29).addBox(-9, -5, -0.5F, 18, 5, 1), PartPose.offset(0, 12, 15));
		wheel(root, "wheel_l", 0, 60, 8, -11, 16, 3);
		wheel(root, "wheel_r", 0, 60, 8, 11, 16, 3);
		root.addOrReplaceChild("axle", CubeListBuilder.create().texOffs(36, 60).addBox(-12, -1, -1, 24, 2, 2), PartPose.offset(0, 16, 3));
		root.addOrReplaceChild("shaft_l", CubeListBuilder.create().texOffs(36, 64).addBox(-0.5F, -0.5F, -24, 1, 1, 24),
			PartPose.offsetAndRotation(-5, 13, -11, -0.18F, 0, 0));
		root.addOrReplaceChild("shaft_r", CubeListBuilder.create().texOffs(36, 64).addBox(-0.5F, -0.5F, -24, 1, 1, 24),
			PartPose.offsetAndRotation(5, 13, -11, -0.18F, 0, 0));
		root.addOrReplaceChild("load_sacks", CubeListBuilder.create().texOffs(88, 0).addBox(-7, -5, -9, 6, 5, 7).texOffs(88, 0).addBox(1, -5, -4, 6, 5, 7)
			.texOffs(88, 0).addBox(-6, -5, 4, 6, 5, 7).texOffs(88, 0).addBox(-2, -9, -1, 6, 4, 7), PartPose.offset(0, 12, 2));
		root.addOrReplaceChild("load_barrels", CubeListBuilder.create().texOffs(88, 12).addBox(-7, -8, -9, 6, 8, 6).texOffs(88, 12).addBox(1, -8, -3, 6, 8, 6)
			.texOffs(88, 12).addBox(-7, -8, 4, 6, 8, 6), PartPose.offset(0, 12, 2));
		root.addOrReplaceChild("load_crates", CubeListBuilder.create().texOffs(88, 26).addBox(-7.5F, -7, -10, 7, 7, 7).texOffs(88, 26).addBox(0.5F, -7, -2, 7, 7, 7)
			.texOffs(88, 26).addBox(-7.5F, -7, 5, 7, 7, 7).texOffs(88, 26).addBox(-3, -14, -1, 7, 7, 7), PartPose.offset(0, 12, 2));
		root.addOrReplaceChild("load_logs", CubeListBuilder.create().texOffs(0, 92).addBox(-7, -4, -11, 4, 4, 22).texOffs(0, 92).addBox(-2, -4, -11, 4, 4, 22)
			.texOffs(0, 92).addBox(3, -4, -11, 4, 4, 22).texOffs(0, 92).addBox(-4.5F, -8, -11, 4, 4, 22).texOffs(0, 92).addBox(0.5F, -8, -11, 4, 4, 22),
			PartPose.offset(0, 12, 2));
		root.addOrReplaceChild("load_stone", CubeListBuilder.create().texOffs(88, 40).addBox(-7, -4, -9, 6, 4, 6).texOffs(88, 40).addBox(1, -4, -6, 6, 4, 6)
			.texOffs(88, 40).addBox(-6, -4, 2, 6, 4, 6).texOffs(88, 40).addBox(1, -4, 5, 6, 4, 6).texOffs(88, 40).addBox(-3, -8, -2, 6, 4, 6),
			PartPose.offset(0, 12, 2));
		return LayerDefinition.create(mesh, 128, 128);
	}

	public static LayerDefinition handcart() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 0).addBox(-6, 0, -8, 12, 2, 16), PartPose.offset(0, 15, 0));
		root.addOrReplaceChild("side_l", CubeListBuilder.create().texOffs(0, 18).addBox(-0.5F, -4, -8, 1, 4, 16), PartPose.offset(-6, 15, 0));
		root.addOrReplaceChild("side_r", CubeListBuilder.create().texOffs(0, 18).addBox(-0.5F, -4, -8, 1, 4, 16), PartPose.offset(6, 15, 0));
		wheel(root, "wheel_l", 0, 38, 5, -7, 19, 1);
		wheel(root, "wheel_r", 0, 38, 5, 7, 19, 1);
		root.addOrReplaceChild("axle", CubeListBuilder.create().texOffs(24, 38).addBox(-8, -1, -1, 16, 2, 2), PartPose.offset(0, 19, 1));
		root.addOrReplaceChild("handle_l", CubeListBuilder.create().texOffs(24, 42).addBox(-0.5F, -0.5F, -12, 1, 1, 12),
			PartPose.offsetAndRotation(-5, 14, -8, -0.3F, 0, 0));
		root.addOrReplaceChild("handle_r", CubeListBuilder.create().texOffs(24, 42).addBox(-0.5F, -0.5F, -12, 1, 1, 12),
			PartPose.offsetAndRotation(5, 14, -8, -0.3F, 0, 0));
		root.addOrReplaceChild("load_crates", CubeListBuilder.create().texOffs(60, 0).addBox(-5.5F, -5, -7, 5, 5, 5).texOffs(60, 0).addBox(0.5F, -5, -2, 5, 5, 5)
			.texOffs(60, 0).addBox(-5, -5, 3, 5, 5, 5).texOffs(80, 0).addBox(-2, -8, -4, 6, 3, 8), PartPose.offset(0, 15, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}
}
