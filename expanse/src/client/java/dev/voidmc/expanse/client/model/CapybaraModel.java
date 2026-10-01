package dev.voidmc.expanse.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class CapybaraModel extends ExpanseAnimalModel<LivingEntityRenderState> {
	private final ModelPart leftEar;
	private final ModelPart rightEar;

	public CapybaraModel(ModelPart root) {
		super(root, 1.3F);
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float wiggle = Mth.sin(state.ageInTicks * 0.2F) > 0.9F ? 0.3F : 0.0F;
		this.leftEar.zRot -= wiggle;
		this.rightEar.zRot += wiggle;
		if (state.isInWater) {
			// Swimming: chin up, legs paddling.
			this.head.xRot -= 0.25F;
		}
	}
}
