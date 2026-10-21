package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.client.combat.hud.FrayHud;
import dev.voidmc.fray.combat.net.CombatHandshake;
import dev.voidmc.fray.combat.net.CombatPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Fray combat's client: keys and input, prediction, the combat handshake's answer, the payload receivers and the
 * native combat HUD (Figma 3a, H1-H10). Called from Fray's client entrypoint after Fray Motion's (so the predicted
 * dodge's movement runs after the clip's root motion).
 */
public final class FrayCombatClient {
	private static boolean serverHasFray;

	private FrayCombatClient() {
	}

	/** True once the server answered Fray's combat payloads (a Fray server); otherwise vanilla input stays. */
	public static boolean serverHasFray() {
		return serverHasFray;
	}

	public static void init() {
		CombatKeys.register();
		FrayClientOptions.get();
		ClientConfigurationNetworking.registerGlobalReceiver(CombatPayloads.Hello.TYPE, (payload, context) -> {
			context.responseSender().sendPacket(CombatHandshake.clientHello());
		});
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Ack.TYPE, (p, c) -> CombatInput.onAck(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.ActionPlay.TYPE, (p, c) -> CombatInput.onActionPlay(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Self.TYPE, (p, c) -> {
			serverHasFray = true;
			ClientCombat.onSelf(p);
		});
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.SelfStatuses.TYPE, (p, c) -> {
			ClientCombat.statuses = p.lines();
			ClientCombat.statusesAt = ClientCombat.ms();
		});
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Meters.TYPE, (p, c) -> ClientCombat.onMeters(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Profile.TYPE, (p, c) -> ClientCombat.PROFILES.put(p.entity(), p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Hit.TYPE, (p, c) -> ClientCombat.onHit(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Tell.TYPE, (p, c) -> ClientCombat.onTell(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.EncounterBar.TYPE, (p, c) -> ClientCombat.onBar(p));
		ClientPlayNetworking.registerGlobalReceiver(CombatPayloads.Banner.TYPE, (p, c) -> ClientCombat.onBanner(p));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientCombat.clear();
			serverHasFray = ClientPlayNetworking.canSend(CombatPayloads.Input.TYPE);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCombat.clear();
			serverHasFray = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			CombatKeys.checkConflicts(mc);
			CombatInput.tick(mc);
		});
		FrayHud.register();
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
