package dev.voidmc.bestiary.anim;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.fray.client.rig.RigEntityRenderer;
import dev.voidmc.fray.client.rig.RigModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The Gallows Knight's renderer: Fray's rig renderer, with the gibbet cage (and its brace) hidden once the knight has
 * torn it off its back (phase 2; the cage is then its own rolling entity).
 */
public final class GallowsKnightRenderer extends RigEntityRenderer<GallowsKnight> {
	public GallowsKnightRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, Bestiary.id("gallows_knight"));
	}

	@Override
	public void extractRenderState(GallowsKnight knight, LivingEntityRenderState state, float partial) {
		super.extractRenderState(knight, state, partial);
		long hidden = 0;
		if (!knight.caged()) {
			int cage = this.model.rig.bone("cage");
			if (cage >= 0 && cage < 64) {
				hidden |= 1L << cage;
			}
		}
		state.setData(RigModel.HIDDEN, hidden);
	}
}
