package dev.voidmc.mettle.mixin.cards;

import dev.voidmc.mettle.character.cards.Mythics;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mobs from spawn eggs, commands and spawners never earn a world first (docs/CARDS.md §9.2). */
@Mixin(Mob.class)
abstract class CardsMobMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void mettle$unnatural(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND || reason == EntitySpawnReason.SPAWNER
			|| reason == EntitySpawnReason.TRIAL_SPAWNER || reason == EntitySpawnReason.DISPENSER) {
			((Mob) (Object) this).addTag(Mythics.UNNATURAL);
		}
	}
}
