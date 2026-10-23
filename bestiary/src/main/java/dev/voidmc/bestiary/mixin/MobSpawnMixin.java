package dev.voidmc.bestiary.mixin;

import dev.voidmc.bestiary.ai.CreatureData;
import java.util.Locale;
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
 * Records why a creature spawned (natural, spawner, structure, ...) so the elite roll on its first load knows: spawner
 * creatures are reworked but never elite (BESTIARY.md §6.4). Nothing else of vanilla's spawning changes.
 */
@Mixin(Mob.class)
abstract class MobSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void bestiary$reason(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		Mob self = (Mob) (Object) this;
		if (CreatureData.get(self) == null) {
			CreatureData.set(self, CreatureData.FRESH.withReason(reason.name().toLowerCase(Locale.ROOT)));
		}
	}
}
