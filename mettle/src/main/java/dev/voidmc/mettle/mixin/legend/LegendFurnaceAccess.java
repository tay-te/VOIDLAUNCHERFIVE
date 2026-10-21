package dev.voidmc.mettle.mixin.legend;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The Ever-Ember times the item in the fire again for whoever filled the furnace last. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface LegendFurnaceAccess {
	@Accessor("cookingTimer")
	int mettle$cookingTimer();

	@Accessor("cookingTimer")
	void mettle$setCookingTimer(int ticks);

	@Accessor("cookingTotalTime")
	int mettle$cookingTotalTime();

	@Accessor("cookingTotalTime")
	void mettle$setCookingTotalTime(int ticks);

	@Invoker("getTotalCookTime")
	static int mettle$totalCookTime(ServerLevel level, AbstractFurnaceBlockEntity furnace) {
		throw new AssertionError();
	}
}
