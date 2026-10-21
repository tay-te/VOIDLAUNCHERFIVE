package dev.voidmc.hearth.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.look.FolkLook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a folk: the player model with their composed skin (wide or slim arms), what they hold and wear, their name and
 * trade when looked at, and the quest marker floating above them.
 */
public class FolkRenderer extends MobRenderer<Folk, FolkRenderState, FolkModel> {
	/** How close the crosshair must be for the name to show. */
	private static final double NAME_DISTANCE_SQ = 20.0 * 20.0;

	private final FolkModel wide;
	private final FolkModel slim;

	public FolkRenderer(EntityRendererProvider.Context context) {
		super(context, new FolkModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new FolkModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new ItemInHandLayer<>(this));
		// the gear layer (docs/LIVING_WORLD_2.md §8.3): armour a companion was given, a bandit's helmet, a guard's kit
		this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
			net.minecraft.client.renderer.entity.ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel<FolkRenderState>::new),
			context.getEquipmentRenderer()));
	}

	@Override
	public FolkRenderState createRenderState() {
		return new FolkRenderState();
	}

	@Override
	public void extractRenderState(Folk entity, FolkRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.rightArmPose = entity.getItemHeldByArm(HumanoidArm.RIGHT).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.leftArmPose = entity.getItemHeldByArm(HumanoidArm.LEFT).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		FolkLook look = entity.look();
		state.look = look;
		state.slim = look.slim();
		state.elder = look.age() == AgeStage.ELDER;
		state.skin = FolkSkins.get(look);
		state.folkPose = entity.folkPose();
		state.marker = FolkMarkers.of(entity.getId());
		state.phase = (entity.getId() * 37) % 1000;
		state.markerAttachment = state.marker == QuestService.Marker.NONE ? null
			: new Vec3(0.0, entity.getBbHeight() + (state.nameTag != null ? 0.75 : 0.45), 0.0);
	}

	@Override
	public void submit(FolkRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
		if (state.markerAttachment != null) {
			Component mark = switch (state.marker) {
				case OFFER -> Component.literal("!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				case TURN_IN -> Component.literal("?").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				case ACTIVE -> Component.literal("?").withStyle(ChatFormatting.GRAY);
				default -> null;
			};
			if (mark != null) {
				collector.submitNameTag(poseStack, state.markerAttachment, 0, mark, true, state.lightCoords, camera);
			}
		}
	}

	@Override
	public Identifier getTextureLocation(FolkRenderState state) {
		return state.skin != null ? state.skin : DefaultPlayerSkin.getDefaultTexture();
	}

	@Override
	protected void scale(FolkRenderState state, PoseStack poseStack) {
		// The player model is drawn at 15/16 scale; children at their age scale on top of that.
		float s = 0.9375F * (state.isBaby ? state.ageScale : 1.0F);
		poseStack.scale(s, s, s);
	}

	@Override
	public Vec3 getRenderOffset(FolkRenderState state) {
		Vec3 offset = super.getRenderOffset(state);
		float s = state.isBaby ? state.ageScale : 1.0F;
		if (state.folkPose == FolkPose.SIT) {
			return offset.add(0.0, -0.55 * s, 0.0);
		}
		if (state.folkPose == FolkPose.TEND || state.folkPose == FolkPose.COWER) {
			// As the player model does when crouching.
			return offset.add(0.0, -2.0 / 16.0 * s, 0.0);
		}
		return offset;
	}

	@Override
	protected boolean shouldShowName(Folk entity, double distanceToCameraSq) {
		return distanceToCameraSq < NAME_DISTANCE_SQ && entity == this.entityRenderDispatcher.crosshairPickEntity && !entity.isInvisible();
	}

	@Override
	protected @Nullable Component getNameTag(Folk entity) {
		String name = entity.folkName();
		if (name.isEmpty()) {
			return super.getNameTag(entity);
		}
		// an office outranks the trade on the name tag: "Guard Captain", "Master Builder"
		Component job = Component.translatable(entity.ageStage() == AgeStage.CHILD ? "hearth.npc.child"
			: entity.office() != dev.voidmc.hearth.npc.person.Office.NONE ? entity.office().translationKey() : "hearth.npc.job." + entity.job().getSerializedName());
		return Component.literal(name).append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(job.copy().withStyle(ChatFormatting.GRAY));
	}
}
