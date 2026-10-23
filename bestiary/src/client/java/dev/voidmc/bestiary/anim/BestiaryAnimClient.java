package dev.voidmc.bestiary.anim;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.client.rig.RigEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityTypes;

/**
 * Which creatures loop which clips (client-side, never sent) and how custom rigs are drawn. Variants are picked per
 * entity so a crowd doesn't move in step.
 */
public final class BestiaryAnimClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientMotion.registerBase(EntityTypes.ZOMBIE, Bestiary.id("zombie/idle"), Bestiary.id("zombie/idle_b"));
		ClientMotion.registerLocomotion(EntityTypes.ZOMBIE, Bestiary.id("zombie/walk"), Bestiary.id("zombie/walk_b"));
		EntityRendererRegistry.register(BestiaryAnim.GALLOWS_KNIGHT, GallowsKnightRenderer::new);
		EntityRendererRegistry.register(BestiaryAnim.GIBBET_CAGE, ctx -> new RigEntityRenderer<>(ctx, Bestiary.id("gibbet_cage")));
		ClientMotion.registerBase(BestiaryAnim.GIBBET_CAGE, Bestiary.id("gibbet_cage/rest"));
		ClientMotion.registerLocomotion(BestiaryAnim.GIBBET_CAGE, Bestiary.id("gibbet_cage/roll"));
		ClientMotion.registerBase(BestiaryAnim.GALLOWS_KNIGHT, Bestiary.id("gallows_knight/idle"));
		ClientMotion.registerLocomotion(BestiaryAnim.GALLOWS_KNIGHT, Bestiary.id("gallows_knight/walk"));
	}
}
