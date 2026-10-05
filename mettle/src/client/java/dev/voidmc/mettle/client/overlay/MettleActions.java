package dev.voidmc.mettle.client.overlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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

	/** Buys the next rank of a perk; the server answers with a feedback chip. */
	public static void learnPerk(String id) {
		ClientPlayNetworking.send(new MettlePayloads.LearnPerk(ResourceKey.create(MettleRegistries.PERK, Identifier.parse(id))));
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
