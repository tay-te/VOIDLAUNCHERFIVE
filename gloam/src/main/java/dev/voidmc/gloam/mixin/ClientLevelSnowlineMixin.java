package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.season.Snowline;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Whether it rains or snows where the client shows weather, with the snowline moved by the season ({@link Snowline}):
 * vanilla's rain and snow renderer, the splashes and the rain's sound ask this. The server's own (snow lying, water
 * freezing) stays vanilla's.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSnowlineMixin {
	@Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
	private void gloam$snowline(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
		Biome.Precipitation vanilla = cir.getReturnValue();
		if (vanilla != Biome.Precipitation.NONE && Snowline.active()) {
			cir.setReturnValue(Snowline.precipitation((ClientLevel) (Object) this, pos, vanilla));
		}
	}
}
