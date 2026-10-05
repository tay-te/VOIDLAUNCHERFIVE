package dev.voidmc.mettle.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import dev.voidmc.mettle.client.character.BackgroundPickerScreen;
import dev.voidmc.mettle.client.character.CharacterScreen;
import dev.voidmc.mettle.client.athletics.AthleticsClient;
import dev.voidmc.mettle.client.burden.BurdenClient;
import dev.voidmc.mettle.client.gear.GearClient;
import dev.voidmc.mettle.client.heavy.HeavyWeaponsClient;
import dev.voidmc.mettle.client.smithing.SmithingClient;
import dev.voidmc.mettle.client.survival.SurvivalClient;
import dev.voidmc.mettle.client.marksmanship.MarksmanshipClient;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.client.overlay.OverlayBridge;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client side: the class screens (native fallback until the Ultralight overlay), the K key, the packets that drive them,
 * and the two tooltip lines signature perks add. State changes are pushed to {@link OverlayBridge} for the overlay.
 */
public final class MettleClient implements ClientModInitializer {
	public static KeyMapping characterKey;
	/** The server asked for the picker; it opens as soon as the world is on screen and nothing else is open. */
	private static boolean pickerPending;
	private static boolean pickerByOperator;

	@Override
	public void onInitializeClient() {
		// Every client that can see a thrown axe needs its renderer, or the game crashes when one is in view.
		HeavyWeaponsClient.init();
		MarksmanshipClient.init();
		AthleticsClient.init();
		SurvivalClient.init();
		SmithingClient.init();
		BurdenClient.init();
		GearClient.init();
		characterKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mettle.character", InputConstants.KEY_K,
			KeyMapping.Category.register(Mettle.id("character"))));

		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.OpenPicker.TYPE, (payload, context) -> {
			JsonObject open = new JsonObject();
			open.addProperty("byOperator", payload.byOperator());
			OverlayBridge.push("mettlePicker", open);
			pickerPending = !OverlayBridge.hasListener();
			pickerByOperator = payload.byOperator();
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.BackgroundChosen.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof BackgroundPickerScreen) {
				mc.gui.setScreen(null);
			}
			if (mc.player != null && !OverlayBridge.hasListener()) {
				mc.player.sendOverlayMessage(Component.translatable("mettle.hint.press_k", characterKey.getTranslatedKeyMessage()));
			}
			result(true, null);
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.BackgroundRejected.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof BackgroundPickerScreen picker) {
				picker.reject(Component.translatable(payload.reason().translationKey()));
			}
			result(false, payload.reason().name().toLowerCase(java.util.Locale.ROOT));
		});
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.Feedback.TYPE, (payload, context) -> {
			JsonObject chip = new JsonObject();
			chip.addProperty("perk", payload.perk().toString());
			chip.addProperty("text", payload.text().getString());
			OverlayBridge.push("mettleFeedback", chip);
			// Cut and Run and Sword and Board charge the next swing on the server; show it charged here too.
			if ((payload.perk().equals(BladesSkill.CUT_AND_RUN) || payload.perk().equals(BladesSkill.SWORD_AND_BOARD)) && context.client().player != null) {
				var player = context.client().player;
				((BladesLivingAccess) player).mettle$setAttackStrengthTicker((int) Math.ceil(player.getCurrentItemAttackStrengthDelay()) + 1);
			}
			// Until the overlay's HUD chips exist, the action bar says it.
			if (!OverlayBridge.hasListener() && context.client().player != null) {
				context.client().player.sendOverlayMessage(payload.text());
			}
		});

		ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}
			dev.voidmc.mettle.character.TomeItem.tooltip(stack, mc.level, line -> lines.add(1, line));
			if (stack.has(SignaturePerks.HERB_LORE_BREWED)) {
				lines.add(Component.translatable("mettle.tooltip.herb_lore").withStyle(ChatFormatting.GRAY));
			}
			// Smith's Eye: exact durability, unless the advanced tooltip already shows it.
			if (stack.isDamageableItem() && !flag.isAdvanced() && SignaturePerks.has(mc.player, SignaturePerks.SMITHS_EYE)) {
				int max = stack.getMaxDamage();
				lines.add(Component.translatable("mettle.tooltip.durability", max - stack.getDamageValue(), max).withStyle(ChatFormatting.GRAY));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (characterKey.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					mc.gui.setScreen(CharacterStore.get(mc.player).hasBackground() ? new CharacterScreen() : new BackgroundPickerScreen(false));
				}
			}
			if (pickerPending && mc.player != null && mc.level != null && mc.gui.screen() == null) {
				pickerPending = false;
				if (!CharacterStore.get(mc.player).hasBackground()) {
					mc.gui.setScreen(new BackgroundPickerScreen(pickerByOperator));
				}
			}
			if (OverlayBridge.hasListener() && mc.player != null && mc.player.tickCount % 10 == 0) {
				OverlayBridge.push("mettle", MettleState.build(mc));
			}
		});
	}

	private static void result(boolean ok, String reason) {
		JsonObject r = new JsonObject();
		r.addProperty("ok", ok);
		if (reason != null) {
			r.addProperty("reason", reason);
		}
		OverlayBridge.push("mettleResult", r);
	}

	/** Opens the picker at once (client game test). */
	public static void openPicker() {
		Minecraft.getInstance().gui.setScreen(new BackgroundPickerScreen(false));
	}
}
