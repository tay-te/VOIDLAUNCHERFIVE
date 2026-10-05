package dev.voidmc.mettle.mixin.survival;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exhaustion has no getter or setter; the client tour reads it to measure Self-Sufficient. */
@Mixin(FoodData.class)
public interface SurvivalFoodDataAccess {
	@Accessor("exhaustionLevel")
	float mettle$exhaustion();

	@Accessor("exhaustionLevel")
	void mettle$setExhaustion(float exhaustion);
}
