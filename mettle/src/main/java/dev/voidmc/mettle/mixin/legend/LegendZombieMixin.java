package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.cards.legend.LegendSenses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Deep Lantern: a zombie calls no reinforcements into the ground within 12 blocks of a holder below y 0. */
@Mixin(Zombie.class)
abstract class LegendZombieMixin {
	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/SpawnPlacements;checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"))
	private boolean mettle$deepLantern(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random,
		Operation<Boolean> original) {
		if (level instanceof ServerLevel server && LegendSenses.lanternForbids(server, (Zombie) (Object) this, pos)) {
			return false;
		}
		return original.call(type, level, reason, pos, random);
	}
}
