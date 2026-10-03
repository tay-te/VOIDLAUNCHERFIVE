package dev.voidmc.expanse.client.life;

import dev.voidmc.expanse.client.model.ExpanseAnimalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** A deer: the elk's walk, ears that flick, antlers for bucks, and a white tail flagged up when it runs. */
public class DeerModel extends ExpanseAnimalModel<DeerRenderer.DeerRenderState> {
	private final ModelPart tail;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart leftAntler;
	private final ModelPart rightAntler;

	public DeerModel(ModelPart root) {
		super(root, 1.2F);
		this.tail = root.getChild("tail");
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
		this.leftAntler = this.head.getChild("left_antler");
		this.rightAntler = this.head.getChild("right_antler");
	}

	@Override
	public void setupAnim(DeerRenderer.DeerRenderState state) {
		super.setupAnim(state);
		this.leftAntler.visible = state.antlers;
		this.rightAntler.visible = state.antlers;
		float t = state.ageInTicks;
		if (state.walkAnimationSpeed > 0.75F) {
			// Running: the white flag goes up.
			this.tail.xRot += 2.1F;
		} else {
			this.tail.xRot += Mth.sin(t * 0.17F) * 0.12F;
		}
		float flick = Mth.sin(t * 0.09F) > 0.95F ? Mth.sin(t * 1.4F) * 0.45F : 0.0F;
		this.leftEar.zRot += flick;
		this.rightEar.zRot -= flick * 0.7F;
	}
}
