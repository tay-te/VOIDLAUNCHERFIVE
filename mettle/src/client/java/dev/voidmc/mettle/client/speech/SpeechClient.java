package dev.voidmc.mettle.client.speech;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SpeechSkill;
import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Speech on the client: the "Cooked by" line (Tavern-Keeper) and Appraiser's price line in item tooltips. Appraiser asks
 * the server (the market here, or the nearest villager) at most once a second for a stack and keeps the answer 10 s.
 */
public final class SpeechClient {
	private static final long ASK_MS = 1000;
	private static final long KEEP_MS = 10_000;

	private record Answer(Component line, long at) {
	}

	private static final Map<Integer, Answer> ANSWERS = new HashMap<>();
	private static final Map<Integer, Long> ASKED = new HashMap<>();

	private SpeechClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.Appraised.TYPE, (payload, context) ->
			ANSWERS.put(key(payload.stack()), new Answer(payload.line(), System.currentTimeMillis())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ANSWERS.clear();
			ASKED.clear();
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			String cook = stack.get(SpeechHooks.COOKED_BY);
			if (cook != null) {
				lines.add(Component.translatable("mettle.speech.cooked_by", cook).withStyle(ChatFormatting.GOLD));
			}
			var player = Minecraft.getInstance().player;
			if (player == null || stack.isEmpty() || !Perks.has(player, SpeechSkill.APPRAISER)) {
				return;
			}
			int key = key(stack);
			long now = System.currentTimeMillis();
			Answer a = ANSWERS.get(key);
			if (a != null && now - a.at() < KEEP_MS) {
				lines.add(a.line().copy().withStyle(ChatFormatting.YELLOW));
			}
			Long asked = ASKED.get(key);
			if ((a == null || now - a.at() >= KEEP_MS) && (asked == null || now - asked >= ASK_MS) && ClientPlayNetworking.canSend(MettlePayloads.Appraise.TYPE)) {
				ASKED.put(key, now);
				if (ASKED.size() > 512) {
					ASKED.clear();
				}
				ClientPlayNetworking.send(new MettlePayloads.Appraise(stack.copy()));
			}
		});
	}

	private static int key(ItemStack stack) {
		return ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
	}
}
