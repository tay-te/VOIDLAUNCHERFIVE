package dev.voidmc.fray.client.rig;

import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigJson;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a living entity with a custom rig (ANIMATION.md §4.3) through vanilla's {@code LivingEntityRenderer}: the same
 * extract, submit, layers and shadow pass as any mob, so Fray's sampling hook, Sodium and Iris all apply unchanged. The
 * Motion Studio's reload swaps in a model rebuilt from the new rig file.
 */
public class RigEntityRenderer<T extends LivingEntity> extends LivingEntityRenderer<T, LivingEntityRenderState, RigModel> {
	private final Identifier rigId;
	private Identifier texture;

	public RigEntityRenderer(EntityRendererProvider.Context context, Identifier rig) {
		this(context, rig, RigJson.require(rig));
	}

	private RigEntityRenderer(EntityRendererProvider.Context context, Identifier rigId, Rig rig) {
		super(context, new RigModel(rig), rig.shadowRadius);
		this.rigId = rigId;
		this.texture = rig.texture == null ? Identifier.withDefaultNamespace("textures/misc/white.png") : rig.texture;
		Consumer<Map<Identifier, Rig>> onRigs = rigs -> {
			Rig r = rigs.get(this.rigId);
			if (r != null && r != this.model.rig) {
				this.model = new RigModel(r);
				this.shadowRadius = r.shadowRadius;
				if (r.texture != null) {
					this.texture = r.texture;
				}
			}
		};
		ClientClips.ON_RIGS.add(onRigs);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return this.texture;
	}

	@Override
	protected void scale(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		float s = this.model.rig.modelScale;
		if (s != 1) {
			poseStack.scale(s, s, s);
		}
	}
}
