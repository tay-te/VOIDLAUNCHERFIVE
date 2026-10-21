package dev.voidmc.hearth.client.npc.travel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws a cart: its kind's model and texture, turned to its heading, tilted with the ground, wheels turning as it rolls. */
public class CartRenderer extends EntityRenderer<CartEntity, CartRenderState> {
	private final Map<CartEntity.Kind, CartModel> models = new EnumMap<>(CartEntity.Kind.class);
	private final Map<CartEntity.Kind, Identifier> textures = new EnumMap<>(CartEntity.Kind.class);

	public CartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.9F;
		for (CartEntity.Kind k : CartEntity.Kind.values()) {
			this.models.put(k, new CartModel(context.bakeLayer(TravelClient.layer(k)), k));
			this.textures.put(k, Hearth.id("textures/entity/cart/" + k.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
		}
	}

	@Override
	public CartRenderState createRenderState() {
		return new CartRenderState();
	}

	@Override
	public void extractRenderState(CartEntity entity, CartRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.load = entity.load();
		state.yRot = entity.getYRot(partialTicks);
		state.tilt = entity.tilt();
		state.rolled = (float) Mth.lerp(partialTicks, entity.rolledO, entity.rolled);
	}

	@Override
	public void submit(CartRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.rotateDegrees(Axis.XP, state.tilt);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		CartModel model = this.models.get(state.kind);
		collector.submitModel(model, state, poseStack, this.textures.get(state.kind), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
