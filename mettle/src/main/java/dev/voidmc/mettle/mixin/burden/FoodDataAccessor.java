package dev.voidmc.mettle.mixin.burden;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the hidden exhaustion counter, so the tour can measure the hunger multiplier. */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
	@Accessor("exhaustionLevel")
	float mettle$exhaustion();

	@Accessor("exhaustionLevel")
	void mettle$setExhaustion(float value);
}
