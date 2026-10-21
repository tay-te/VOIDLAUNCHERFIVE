package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.cards.legend.LegendSenses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Deep Lantern: no hostile spawns of its own near a holder below y 0. */
@Mixin(NaturalSpawner.class)
abstract class LegendNaturalSpawnerMixin {
	@ModifyReturnValue(method = "isValidPositionForMob", at = @At("RETURN"))
	private static boolean mettle$deepLantern(boolean valid, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Mob mob) {
		return valid && !LegendSenses.lanternForbids(level, mob, mob.blockPosition());
	}
}
