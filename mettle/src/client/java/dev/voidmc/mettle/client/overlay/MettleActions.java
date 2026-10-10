package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.client.draft.CardAudio;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The calls the overlay makes (docs/OVERLAY.md), shaped like its JSON so the bridge is a thin shim. The answer to
 * {@link #chooseBackground} arrives later as a {@code mettleResult} event: the server decides.
 */
public final class MettleActions {
	private MettleActions() {
	}

	/** {@code {background: "mettle:ranger", spent: {strength: 1}, favoured: ["mettle:mining"]}} */
	public static void chooseBackground(JsonObject request) {
		var background = ResourceKey.create(MettleRegistries.BACKGROUND, Identifier.parse(request.get("background").getAsString()));
		Map<CharacterAttribute, Integer> spent = new HashMap<>();
		if (request.has("spent")) {
			for (Map.Entry<String, JsonElement> e : request.getAsJsonObject("spent").entrySet()) {
				CharacterAttribute a = CharacterAttribute.CODEC.byName(e.getKey());
				if (a != null) {
					spent.put(a, e.getValue().getAsInt());
				}
			}
		}
		List<ResourceKey<Skill>> favoured = new ArrayList<>();
		if (request.has("favoured")) {
			request.getAsJsonArray("favoured").forEach(e -> favoured.add(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(e.getAsString()))));
		}
		ClientPlayNetworking.send(new MettlePayloads.ChooseBackground(background, spent, favoured));
	}

	/**
	 * The old "buy the next rank of a perk". Perks come from drafts now (docs/DRAFT.md): nothing is sent, and a feedback
	 * chip ({@code mettle:draft/no_buying}) says so. Kept so a host that still calls it gets an answer, not a crash; use
	 * {@link #pickCard} and {@link #reroll}.
	 */
	public static void learnPerk(String id) {
		MettleState.remember("mettle:draft/no_buying", Component.translatable("mettle.draft.no_buying").getString());
	}

	/**
	 * Keeps the {@code index}-th card (0–2) of a skill's draft hand ({@code skills[].hand}); the server checks it and
	 * answers with a chip, and the next hand (if any) arrives with the synced character.
	 */
	public static void pickCard(String skill, int index) {
		// The flare, the thunk and the other two burning away play at once (docs/SOUNDS.md); the overlay plays nothing.
		CardAudio.pick(skill, index);
		ClientPlayNetworking.send(new MettlePayloads.PickCard(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill)), index));
	}

	/** {@link #pickCard(String, int)} for hosts that pass one string: {@code "mettle:mining#1"} (skill, {@code #}, index 0–2). */
	public static void pickCard(String skillAndIndex) {
		int hash = skillAndIndex.lastIndexOf('#');
		if (hash > 0) {
			try {
				pickCard(skillAndIndex.substring(0, hash), Integer.parseInt(skillAndIndex.substring(hash + 1).trim()));
			} catch (NumberFormatException ignored) {
				// A malformed call from a page: nothing to send.
			}
		}
	}

	/** Spends a reroll on a fresh hand for a skill's draft ({@code draft.rerolls} says how many are left). */
	public static void reroll(String skill) {
		CardAudio.reroll(skill);
		ClientPlayNetworking.send(new MettlePayloads.Reroll(ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skill))));
	}

	/**
	 * The card sounds the overlay times to its animations (docs/SOUNDS.md §5): {@code cardSound("deal", "mettle:mining")}
	 * as a hand fans out (its rarity stinger follows), {@code ("hover", "epic")} or {@code ("hover", "mettle:mining#2")}
	 * as the pointer reaches a card, {@code ("unhover", "")}, {@code ("mythic_reveal", "")} as the reveal starts (its
	 * markers are in {@code draft.sound}), {@code ("stop", "")} when the draft closes, and {@code ("slot", "")} /
	 * {@code ("set_complete", "")}. Picks and rerolls sound by themselves through {@link #pickCard} and {@link #reroll}.
	 */
	public static void cardSound(String cue, String arg) {
		CardAudio.call(cue, arg);
	}

	/** {@link #cardSound(String, String)} for hosts that pass one string: {@code "hover#epic"}, {@code "deal#mettle:mining"}. */
	public static void cardSound(String cueAndArg) {
		if (cueAndArg == null) {
			return;
		}
		int hash = cueAndArg.indexOf('#');
		CardAudio.call(hash < 0 ? cueAndArg : cueAndArg.substring(0, hash), hash < 0 ? "" : cueAndArg.substring(hash + 1));
	}

	public static void spendAttribute(String attribute) {
		CharacterAttribute a = CharacterAttribute.CODEC.byName(attribute);
		if (a != null) {
			ClientPlayNetworking.send(new MettlePayloads.SpendAttribute(a));
		}
	}

	public static void closeMenu() {
		Minecraft.getInstance().gui.setScreen(null);
	}
}
