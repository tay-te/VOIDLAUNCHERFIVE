package dev.voidmc.mettle.mixin.survival;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.survival.GeneralFeats;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Seen It All: one more attribute point to place. A stand-in until Perks#attributePoints counts perk-granted points
 * itself (docs/requests/survival.md).
 */
@Mixin(Perks.class)
abstract class SurvivalPerksMixin {
	@ModifyReturnValue(method = "attributePoints", at = @At("RETURN"))
	private static int mettle$seenItAll(int points, Player player) {
		return points + GeneralFeats.bonusAttributePoints(player);
	}
}
