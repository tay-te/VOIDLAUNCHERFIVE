package dev.voidmc.mettle.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearSpec;
import dev.voidmc.mettle.gear.WornGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Worn gear on the player model (BURDEN.md §8): a pack on the back sized by tier, a belt at the waist, a quiver over
 * the shoulder, all drawn over armour. Reads the synced {@code mettle:gear/worn} attachment, so everyone sees it.
 */
public final class GearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public GearLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.isSpectator || Minecraft.getInstance().level == null) {
			return;
		}
		Entity entity = Minecraft.getInstance().level.getEntity(state.id);
		if (!(entity instanceof Player player) || GearClient.hidden(player)) {
			return;
		}
		WornGear worn = player.getAttached(WornGear.TYPE);
		if (worn == null || worn.isEmpty()) {
			return;
		}
		boolean chest = !state.chestEquipment.isEmpty();
		int beltLayer = chest ? 2 : state.legsEquipment.isEmpty() ? 0 : 1;
		float off = chest ? 1.05f : 0f;

		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().body.translateAndRotate(pose);
		float packDepth = 0f;
		for (GearSlot slot : GearSlot.values()) {
			ItemStack stack = worn.get(slot);
			GearSpec spec = stack.get(GearComponents.GEAR);
			if (spec == null || spec.slot() != slot) {
				continue;
			}
			String fallback = switch (slot) {
				case BACK -> "leather_pack";
				case BELT -> "belt";
				case QUIVER -> "quiver";
			};
			GearModels.Model model = GearModels.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), fallback, beltLayer);
			DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
			Identifier dyeTexture = dye == null ? model.texture() : model.dyedTexture();
			int tint = dye == null ? -1 : ARGB.opaque(dye.rgb());
			float back = slot == GearSlot.BELT ? 0f : off + (slot == GearSlot.QUIVER ? packDepth : 0f);
			float front = slot == GearSlot.BELT ? 0f : -off;

			pose.pushPose();
			pose.translate(0f, 0f, back / 16f);
			part(out, pose, model.backDye(), dyeTexture, light, tint);
			part(out, pose, model.backFixed(), model.texture(), light, -1);
			pose.popPose();
			pose.pushPose();
			pose.translate(0f, 0f, front / 16f);
			part(out, pose, model.frontDye(), dyeTexture, light, tint);
			part(out, pose, model.frontFixed(), model.texture(), light, -1);
			pose.popPose();
			if (slot == GearSlot.BACK) {
				packDepth = model.depth();
			}
		}
		pose.popPose();
	}

	private static void part(SubmitNodeCollector out, PoseStack pose, ModelPart part, Identifier texture, int light, int tint) {
		if (part != null) {
			out.submitModelPart(part, pose, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, null, tint);
		}
	}
}
