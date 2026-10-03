package dev.voidmc.hearth.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.hearth.npc.entity.FolkPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The player model (wide or slim arms, both skin layers) animated for folk: their trade's motions, sitting, praying,
 * reading, waving, cowering, children's games, an elder's stoop, and a little idle sway so nobody stands like a post.
 */
public class FolkModel extends HumanoidModel<FolkRenderState> {
	private final boolean slim;

	public FolkModel(ModelPart root, boolean slim) {
		super(root, RenderTypes::entityTranslucent);
		this.slim = slim;
	}

	@Override
	public void setupAnim(FolkRenderState state) {
		boolean sitting = state.folkPose == FolkPose.SIT;
		boolean wasPassenger = state.isPassenger;
		boolean wasCrouching = state.isCrouching;
		if (sitting) {
			// The vanilla riding pose is a good seated pose.
			state.isPassenger = true;
		}
		if (state.folkPose == FolkPose.TEND || state.folkPose == FolkPose.COWER) {
			// Bent to the ground: the vanilla crouch, arms reaching down.
			state.isCrouching = true;
		}
		super.setupAnim(state);
		state.isPassenger = wasPassenger;
		state.isCrouching = wasCrouching;
		float t = state.ageInTicks + state.phase;
		float still = 1.0F - Math.min(1.0F, state.walkAnimationSpeed * 2.0F);

		if (state.isBaby) {
			// Children: a big head on a small body.
			this.head.xScale = 1.22F;
			this.head.yScale = 1.22F;
			this.head.zScale = 1.22F;
		}
		if (state.elder && !sitting && state.folkPose == FolkPose.NONE) {
			// A slight stoop.
			this.head.xRot += 0.12F;
			this.body.xRot += 0.08F;
			this.head.z -= 0.6F;
		}
		// Breathing and a slow shift of weight while standing.
		if (still > 0.5F && state.folkPose == FolkPose.NONE && !sitting) {
			this.body.yRot += Mth.sin(t * 0.021F) * 0.03F;
			this.head.zRot += Mth.sin(t * 0.017F + 1.3F) * 0.04F;
		}

		switch (state.folkPose) {
			case HAMMER -> {
				// Raise and strike with the main arm, the other steadying the work.
				float stroke = Mth.sin(t * 0.45F);
				ModelPart main = state.mainArm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
				ModelPart off = main == this.rightArm ? this.leftArm : this.rightArm;
				main.xRot = -1.6F + stroke * 0.85F;
				main.zRot = 0.0F;
				off.xRot = -0.75F;
				off.yRot = main == this.rightArm ? 0.35F : -0.35F;
				this.head.xRot = 0.45F;
			}
			case CRAFT -> {
				float a = Mth.sin(t * 0.3F) * 0.18F;
				this.rightArm.xRot = -0.95F + a;
				this.leftArm.xRot = -0.95F - a;
				this.rightArm.yRot = -0.3F;
				this.leftArm.yRot = 0.3F;
				this.head.xRot = 0.5F;
			}
			case READ -> {
				this.rightArm.xRot = -0.9F;
				this.leftArm.xRot = -0.9F;
				this.rightArm.yRot = -0.45F;
				this.leftArm.yRot = 0.45F;
				this.head.xRot = 0.55F + Mth.sin(t * 0.05F) * 0.04F;
				this.head.yRot += Mth.sin(t * 0.08F) * 0.12F;
			}
			case PRAY -> {
				this.rightArm.xRot = -1.0F;
				this.leftArm.xRot = -1.0F;
				this.rightArm.yRot = -0.55F;
				this.leftArm.yRot = 0.55F;
				this.head.xRot = 0.6F;
			}
			case WAVE -> {
				ModelPart arm = state.mainArm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
				arm.xRot = (float) Math.PI;
				arm.zRot = (arm == this.rightArm ? -1 : 1) * (0.3F + Mth.sin(t * 0.6F) * 0.35F);
			}
			case TALK -> {
				float g = Mth.sin(t * 0.15F);
				this.rightArm.xRot = -0.45F + g * 0.25F;
				this.leftArm.xRot = -0.2F - g * 0.15F;
				this.rightArm.zRot = 0.1F;
				this.head.xRot += Mth.sin(t * 0.33F) * 0.06F;
			}
			case TEND -> {
				this.head.xRot = 0.7F;
				this.rightArm.xRot = -0.5F + Mth.sin(t * 0.25F) * 0.35F;
				this.leftArm.xRot = -0.3F;
			}
			case COWER -> {
				this.rightArm.xRot = -2.4F;
				this.leftArm.xRot = -2.4F;
				this.rightArm.zRot = 0.5F;
				this.leftArm.zRot = -0.5F;
				this.head.xRot = 0.7F;
			}
			case CHEER -> {
				float b = Math.abs(Mth.sin(t * 0.4F));
				this.rightArm.xRot = (float) Math.PI;
				this.leftArm.xRot = (float) Math.PI;
				this.rightArm.zRot = -0.3F - b * 0.3F;
				this.leftArm.zRot = 0.3F + b * 0.3F;
			}
			case SIT -> {
				this.rightArm.xRot = -0.3F;
				this.leftArm.xRot = -0.3F;
			}
			default -> {
			}
		}
	}

	@Override
	public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
		this.root().translateAndRotate(poseStack);
		ModelPart part = this.getArm(arm);
		if (this.slim) {
			float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
			part.x += offset;
			part.translateAndRotate(poseStack);
			part.x -= offset;
		} else {
			part.translateAndRotate(poseStack);
		}
	}
}
