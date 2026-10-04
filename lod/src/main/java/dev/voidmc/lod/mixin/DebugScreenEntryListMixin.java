package dev.voidmc.lod.mixin;

import dev.voidmc.lod.gui.LodDebugEntry;
import java.util.Map;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the LOD's lines in the F3 overlay of vanilla's built-in debug profiles, the way a mod's own lines
 * usually come with it. A profile the player has customised keeps whatever they chose.
 */
@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {
	@Shadow
	@Final
	private Map<Identifier, DebugScreenEntryStatus> allStatuses;

	@Inject(method = "resetToProfile", at = @At("TAIL"))
	private void voidLod$showInProfile(DebugScreenProfile profile, CallbackInfo ci) {
		this.allStatuses.putIfAbsent(LodDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
	}
}
