package dev.voidmc.bestiary.client;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.fray.client.ClientMotion;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.entity.EntityTypes;

/** Bestiary's client: which creatures loop which idle (client-side, like locomotion; never sent). */
public final class BestiaryClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientMotion.registerBase(EntityTypes.ZOMBIE, Bestiary.id("zombie/idle"));
	}
}
