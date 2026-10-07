package dev.voidmc.gloam.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether a lightning flash is lighting the sky (0 when the player hides flashes), so Gloam's dark nights leave it alone. */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
	@Invoker("getSkyFlashTime")
	int gloam$skyFlashTime();
}
