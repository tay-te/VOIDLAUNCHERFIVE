package dev.voidmc.mettle.client.heavy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voidmc.mettle.character.skill.heavy.ThrownAxe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A thrown axe: the axe itself, turning end over end along its flight, and lodged blade-first where it landed. The
 * item is drawn with its own model, so every axe (and any resource pack's) looks like itself.
 */
public class ThrownAxeRenderer extends EntityRenderer<ThrownAxe, ThrownAxeRenderer.State> {
	/** Degrees per tick: about two and a half turns a second. */
	private static final float SPIN = 45f;
	/** How a landed axe sits: head forward and down, as if it bit into the block. */
	private static final float LODGED = -60f;
	/**
	 * The axe turns in a plane tipped this far about its line of flight, so the thrower, looking straight down that
	 * line, sees the blade rather than the thin edge of a flat item.
	 */
	private static final float TILT = 40f;

	private final ItemModelResolver itemModelResolver;

	public ThrownAxeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float yRot;
		public float spin;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownAxe entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.FIXED, entity);
		state.yRot = entity.getYRot(partialTicks);
		state.spin = entity.landed() ? LODGED : -(entity.tickCount + partialTicks) * SPIN;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.25f, 0f);
		// Face the flight: after this, X runs along the axe's path and the item turns about it, tipped towards the viewer.
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90f);
		poseStack.rotateDegrees(Axis.XP, TILT);
		poseStack.rotateDegrees(Axis.ZP, state.spin);
		poseStack.scale(0.85f, 0.85f, 0.85f);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
