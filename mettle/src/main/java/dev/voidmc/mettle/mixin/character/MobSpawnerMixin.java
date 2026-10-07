package dev.voidmc.mettle.mixin.character;

import dev.voidmc.mettle.character.skill.Combat;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti-farm (CHARACTER.md §3.3): a mob a monster spawner made carries a tag, and combat XP from it is a tenth
 * ({@link Combat#targetFactor}). Trial spawners are a fight, not a farm, and keep full XP.
 */
@Mixin(Mob.class)
abstract class MobSpawnerMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void mettle$markSpawner(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.SPAWNER) {
			((Mob) (Object) this).addTag(Combat.SPAWNER_TAG);
		}
	}
}
