package dev.voidmc.hub.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Restarts a toast's slide-in (vanilla's own {@code resetToast}) after the hub held it. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public interface ToastInstanceAccess {

	@Invoker("resetToast")
	void voidhub$reset();
}
