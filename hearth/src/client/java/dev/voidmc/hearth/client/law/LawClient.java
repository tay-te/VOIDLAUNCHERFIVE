package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.law.net.LawPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.voidmc.hearth.client.overlay.HearthScreens;
import dev.voidmc.hearth.client.overlay.HearthViews;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
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
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Hint.TYPE, (p, ctx) -> {
			LawHud.hint = p;
			HearthViews.hint(p);
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Status.TYPE, (p, ctx) -> {
			LawHud.status(p);
			HearthViews.status(p, Util.getMillis());
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Stealth.TYPE, (p, ctx) -> {
			LawHud.stealth(p);
			HearthViews.stealth(p);
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.ContainerOwner.TYPE, (p, ctx) -> ContainerOwnerOverlay.accept(p));
		// The event screens below open through HearthScreens: a host (the VOID Hub) shows them if it registered an opener,
		// else Hearth's own screens do. Updates go to whichever screen shows the event (the host's embedded native one too).
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.LockpickStart.TYPE, (p, ctx) -> {
			HearthViews.lockpickStart(p);
			HearthScreens.show(HearthScreens.LOCKPICK, () -> new LockpickScreen(p));
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.LockpickEnd.TYPE, (p, ctx) -> {
			HearthViews.lockpickEnd(p);
			LockpickScreen screen = HearthScreens.live(LockpickScreen.class);
			if (screen != null && screen.session() == p.session()) {
				screen.end(p);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Pocket.TYPE, (p, ctx) -> {
			LawPayloads.Pocket before = HearthViews.pocket();
			HearthViews.pocket(p);
			PickpocketScreen screen = HearthScreens.live(PickpocketScreen.class);
			if (screen != null && screen.session() == p.session()) {
				screen.update(p);
			} else if (p.state() == LawPayloads.Pocket.OPEN && !(HearthScreens.PICKPOCKET.equals(HearthScreens.hosted()) && before != null
				&& before.session() == p.session())) {
				HearthScreens.show(HearthScreens.PICKPOCKET, () -> new PickpocketScreen(p));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.ConfrontDetail.TYPE, (p, ctx) -> HearthViews.confrontDetail(p));
		ClientPlayNetworking.registerGlobalReceiver(LawPayloads.Confront.TYPE, (p, ctx) -> {
			Minecraft mc = ctx.client();
			LawPayloads.Confront before = HearthViews.confront();
			HearthViews.confront(p);
			if (mc.gui.screen() instanceof ConfrontScreen screen && screen.entity() == p.entity()) {
				if (p.open()) {
					screen.update(p);
				} else {
					screen.closeQuietly();
				}
			} else if (p.open() && !(HearthScreens.CONFRONT.equals(HearthScreens.hosted()) && before != null && before.entity() == p.entity())) {
				HearthScreens.show(HearthScreens.CONFRONT, () -> new ConfrontScreen(p));
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Hearth.id("law/hud"), new LawHud());
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.afterExtract(screen).register(ContainerOwnerOverlay::draw);
			}
		});
		LawClientTour.install();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			LawHud.reset();
			ContainerOwnerOverlay.reset();
		});
	}
}
