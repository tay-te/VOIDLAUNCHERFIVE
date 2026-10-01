package dev.voidmc.expanse.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class ElkModel extends ExpanseAnimalModel<LivingEntityRenderState> {
	private final ModelPart tail;
	private final ModelPart leftEar;
	private final ModelPart rightEar;

	public ElkModel(ModelPart root) {
		super(root, 1.1F);
		this.tail = root.getChild("tail");
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.tail.xRot += Mth.sin(t * 0.13F) * 0.15F;
		// An ear flick every few seconds, not a constant flap.
		float flick = Mth.sin(t * 0.07F) > 0.96F ? Mth.sin(t * 1.3F) * 0.4F : 0.0F;
		this.leftEar.zRot += flick;
		this.rightEar.zRot -= flick * 0.6F;
	}
}
