package dev.voidmc.bestiary.client;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.creature.RevenantSoldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/**
 * The revenant soldier: vanilla's humanoid model and armour layers (Minecraft's shape), its own texture, and the shield
 * raised in vanilla's block pose while its guard is up (the server keeps the shield "in use").
 */
public final class RevenantRenderer extends HumanoidMobRenderer<RevenantSoldier, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Bestiary.id("textures/entity/revenant_soldier.png");

	public RevenantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, ctx.getModelSet(), HumanoidModel::new),
			ctx.getEquipmentRenderer()));
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(RevenantSoldier mob, HumanoidArm arm) {
		if (mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND && arm != mob.getMainArm() && mob.getOffhandItem().is(Items.SHIELD)) {
			return HumanoidModel.ArmPose.BLOCK;
		}
		return super.getArmPose(mob, arm);
	}
}
