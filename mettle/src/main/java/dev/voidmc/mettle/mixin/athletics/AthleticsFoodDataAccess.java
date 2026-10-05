package dev.voidmc.mettle.mixin.athletics;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the exhaustion that hunger accrues from, so the tour can measure Marathon and Courier's Stride. */
@Mixin(FoodData.class)
public interface AthleticsFoodDataAccess {
	@Accessor("exhaustionLevel")
	float mettle$exhaustion();
}
