package dev.voidmc.atlas.client.mixin;

import dev.voidmc.atlas.client.AtlasClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block the client changes (placed, broken, sent by the server one at a time or a section at a time, blown up)
 * passes through here on its way to the chunk renderer: Atlas notes its chunk, to read it again once the changes there
 * settle, so the map stays true.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void atlas$blockChanged(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo ci) {
		if (old != current) {
			AtlasClient.blockChanged((ClientLevel) (Object) this, pos);
		}
	}
}
