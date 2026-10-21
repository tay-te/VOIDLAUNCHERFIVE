package dev.voidmc.hearth.client.npc.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.client.overlay.HearthStateSections;
import dev.voidmc.hearth.npc.companion.PlaceSummary;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The client's side of the player's place (docs/LIVING_WORLD_2.md §10 F2/F5): the server's {@link PlaceSummary} kept as the
 * hub state's {@code household} section — companions and their orders and gear, houses and servants and the larder, ranks
 * with lords, burgher towns — for the Household page once it is designed in Figma. No screen of its own: until then the
 * conversations are the way to act. Lane B4 (§11.3).
 */
public final class HouseholdClient {
	private static volatile JsonElement last = JsonNull.INSTANCE;
	private static volatile long revision;

	private HouseholdClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PlaceSummary.Payload.TYPE, (payload, ctx) -> {
			JsonElement json;
			try {
				json = JsonParser.parseString(payload.json());
			} catch (RuntimeException e) {
				json = JsonNull.INSTANCE;
			}
			last = json;
			revision++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			last = JsonNull.INSTANCE;
			revision++;
		});
		HearthStateSections.register("household", new HearthStateSections.Section() {
			@Override
			public JsonElement build(Minecraft mc) {
				return last.deepCopy();
			}

			@Override
			public long fingerprint(Minecraft mc) {
				return revision;
			}
		});
	}
}
