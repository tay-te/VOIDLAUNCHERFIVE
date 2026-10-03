package dev.voidmc.expanse.client.model;

import dev.voidmc.expanse.client.render.MammothRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class MammothModel extends ExpanseAnimalModel<MammothRenderState> {
	private final ModelPart coat;
	private final ModelPart trunk;
	private final ModelPart trunkMid;
	private final ModelPart trunkTip;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart tail;

	public MammothModel(ModelPart root) {
		super(root, 0.6F);
		this.coat = root.getChild("body").getChild("coat");
		this.trunk = this.head.getChild("trunk");
		this.trunkMid = this.trunk.getChild("trunk_mid");
		this.trunkTip = this.trunkMid.getChild("trunk_tip");
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
		this.tail = root.getChild("tail");
	}

	@Override
	public void setupAnim(MammothRenderState state) {
		super.setupAnim(state);
		this.coat.visible = !state.sheared;
		float t = state.ageInTicks;
		// The trunk swings in a slow pendulum, each joint lagging the one above it.
		this.trunk.xRot += Mth.sin(t * 0.06F) * 0.08F;
		this.trunk.zRot += Mth.sin(t * 0.045F) * 0.12F;
		this.trunkMid.xRot += Mth.sin(t * 0.06F - 0.6F) * 0.14F;
		this.trunkTip.xRot += Mth.sin(t * 0.06F - 1.2F) * 0.22F + state.walkAnimationSpeed * 0.3F;
		this.leftEar.yRot += Mth.sin(t * 0.05F) * 0.12F;
		this.rightEar.yRot -= Mth.sin(t * 0.05F + 0.4F) * 0.12F;
		this.tail.zRot += Mth.sin(t * 0.1F) * 0.2F;
	}
}
