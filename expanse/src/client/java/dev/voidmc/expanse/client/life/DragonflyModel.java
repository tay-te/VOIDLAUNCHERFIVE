package dev.voidmc.expanse.client.life;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A dragonfly: a long body and four glassy wings (drawn translucent). On the wing the pairs whirr out of
 * step with each other; at rest all four lie flat and still.
 */
public class DragonflyModel extends EntityModel<CritterRenderState> {
	private final ModelPart body;
	private final ModelPart leftFore;
	private final ModelPart rightFore;
	private final ModelPart leftHind;
	private final ModelPart rightHind;

	public DragonflyModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.body = root.getChild("body");
		this.leftFore = this.body.getChild("left_forewing");
		this.rightFore = this.body.getChild("right_forewing");
		this.leftHind = this.body.getChild("left_hindwing");
		this.rightHind = this.body.getChild("right_hindwing");
	}

	@Override
	public void setupAnim(CritterRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks + state.seed * 2.3F;
		if (state.resting) {
			this.leftHind.yRot = -0.25F;
			this.rightHind.yRot = 0.25F;
			this.leftFore.yRot = 0.1F;
			this.rightFore.yRot = -0.1F;
			return;
		}
		float fore = Mth.sin(t * 3.0F) * 0.5F;
		float hind = Mth.sin(t * 3.0F + 1.7F) * 0.5F;
		this.leftFore.zRot = -fore;
		this.rightFore.zRot = fore;
		this.leftHind.zRot = -hind;
		this.rightHind.zRot = hind;
		this.body.y += Mth.sin(t * 0.37F) * 0.3F;
		this.body.xRot = -0.08F;
	}
}
