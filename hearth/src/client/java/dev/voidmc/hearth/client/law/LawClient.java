package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.law.net.LawPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Client side of the law module: the red hand and the sneaking eye on the HUD, the owner of an open container, wanted
 * and jail status, and the lockpicking, pickpocketing and confrontation screens. It decides nothing; it shows what the
 * server sends and sends back the player's intents.
 */
public final class LawClient {
	private LawClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Hint.TYPE, (p, ctx) -> LawHud.hint = p);
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Status.TYPE, (p, ctx) -> LawHud.status(p));
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Stealth.TYPE, (p, ctx) -> LawHud.stealth(p));
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.ContainerOwner.TYPE, (p, ctx) -> ContainerOwnerOverlay.accept(p));
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.LockpickStart.TYPE, (p, ctx) -> ctx.client().gui.setScreen(new LockpickScreen(p)));
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.LockpickEnd.TYPE, (p, ctx) -> {
			if (ctx.client().gui.screen() instanceof LockpickScreen screen && screen.session() == p.session()) {
				screen.end(p);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Pocket.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof PickpocketScreen screen && screen.session() == p.session()) {
				screen.update(p);
			} else if (p.state() == LawPayloads.Pocket.OPEN) {
				mc.gui.setScreen(new PickpocketScreen(p));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Confront.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof ConfrontScreen screen && screen.entity() == p.entity()) {
				if (p.open()) {
					screen.update(p);
				} else {
					screen.closeQuietly();
				}
			} else if (p.open()) {
				mc.gui.setScreen(new ConfrontScreen(p));
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Hearth.id("law/hud"), new LawHud());
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.afterExtract(screen).register(ContainerOwnerOverlay::draw);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			LawHud.reset();
			ContainerOwnerOverlay.reset();
		});
	}
}
