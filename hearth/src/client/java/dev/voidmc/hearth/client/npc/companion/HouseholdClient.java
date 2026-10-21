package dev.voidmc.hearth.client.npc.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.client.overlay.HearthScreens;
import dev.voidmc.hearth.client.overlay.HearthStateSections;
import dev.voidmc.hearth.client.overlay.LivingWorldJson;
import dev.voidmc.hearth.client.overlay.Portraits;
import dev.voidmc.hearth.npc.companion.PlaceSummary;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of the player's place (docs/LIVING_WORLD_2.md §10 F2/F3/F5): the server's {@link PlaceSummary} kept as the
 * hub state's {@code household} section (companions and their orders, gear and wages; houses, servants and the larder;
 * ranks with lords; burgher towns), with wages as coin, faces, and what the player holds for "Take this"
 * ({@link LivingWorldJson#household}). The Household page (Figma "Household B, the roster", picked 2026-10-21) and the
 * Companion gear screen (F3) draw it; their calls go through {@code HearthActions.companion*}. "Let me see your gear"
 * opens the gear screen through the {@code gear} opener. Lane B4 (§11.3); 2026-10-21 the screens.
 */
public final class HouseholdClient {
	private static volatile JsonElement last = JsonNull.INSTANCE;
	private static volatile long revision;
	private static volatile @Nullable String gearFor;

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
		ClientPlayNetworking.registerGlobalReceiver(PlaceSummary.OpenGear.TYPE, (payload, ctx) -> openGear(payload.resident()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			last = JsonNull.INSTANCE;
			gearFor = null;
			revision++;
		});
		HearthStateSections.register("household", new HearthStateSections.Section() {
			@Override
			public JsonElement build(Minecraft mc) {
				JsonElement raw = last;
				return LivingWorldJson.household(raw.isJsonObject() ? raw.getAsJsonObject() : null, Portraits::ofKey, hands(mc.player), gearFor);
			}

			@Override
			public long fingerprint(Minecraft mc) {
				return revision;
			}
		});
	}

	/** The summary as the server last sent it (null before any), for the reputation page's retinue and burgher cards. */
	public static @Nullable JsonObject raw() {
		JsonElement raw = last;
		return raw.isJsonObject() ? raw.getAsJsonObject() : null;
	}

	/** The companion whose gear screen is open (their resident id), or null. */
	public static @Nullable String gearFor() {
		return gearFor;
	}

	/** The gear screen opened for a companion: the host's {@code gear} opener shows it (there is no Java screen for it). */
	static void openGear(String resident) {
		gearFor = resident;
		revision++;
		HearthScreens.offerHosted(HearthScreens.GEAR);
	}

	/** The gear screen closed (the host's {@code close("gear")}). */
	public static void gearClosed() {
		if (gearFor != null) {
			gearFor = null;
			revision++;
		}
	}

	/** The hotbar and the off hand, for "Take this". */
	static List<LivingWorldJson.Hand> hands(@Nullable LocalPlayer player) {
		List<LivingWorldJson.Hand> out = new ArrayList<>(10);
		if (player == null) {
			return out;
		}
		var inv = player.getInventory();
		for (int i = 0; i < 9; i++) {
			out.add(new LivingWorldJson.Hand(i, inv.getItem(i)));
		}
		out.add(new LivingWorldJson.Hand(40, inv.getItem(40)));
		return out;
	}
}
