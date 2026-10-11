package dev.voidmc.hub.mixin;

import dev.voidmc.hub.screen.HubScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's toasts ("New Recipe(s) Unlocked!", advancements, tutorials) wait while a VOID Hub screen is open: they would
 * sit over the shell's top-right close island. Nothing is lost: a toast added meanwhile stays queued, and toasts that
 * were already showing start their slide-in again once the hub closes, so each still gets its full time on screen.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private List<?> visibleToasts;

	@Unique
	private boolean voidhub$held;

	@Unique
	private boolean voidhub$hubOpen() {
		return this.minecraft.gui != null && this.minecraft.gui.screen() instanceof HubScreen<?>;
	}

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void voidhub$holdUpdate(CallbackInfo ci) {
		if (this.voidhub$hubOpen()) {
			this.voidhub$held = true;
			ci.cancel();
			return;
		}
		if (this.voidhub$held) {
			this.voidhub$held = false;
			for (Object t : this.visibleToasts) {
				((ToastInstanceAccess) t).voidhub$reset();
			}
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void voidhub$holdRender(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (this.voidhub$hubOpen()) {
			ci.cancel();
		}
	}
}
