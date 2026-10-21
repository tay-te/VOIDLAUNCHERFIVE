package dev.voidmc.hearth.client.social.lives;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.voidmc.hearth.client.overlay.HearthStateSections;
import dev.voidmc.hearth.client.overlay.HearthViews;
import dev.voidmc.hearth.social.lives.LivesPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The People section of the Hearth state (docs/LIVING_WORLD_2.md §4.1, §10 F4): the named characters the player has met,
 * a line on where each one's story stands, and how they stand with the player. The journal's People page that draws it
 * is still to be designed in Figma (requests/lwB2.md); until then the section is there for the host and the same list is
 * told in conversation ("Who are the folk of note here?").
 *
 * <pre>
 * "people": { "rows": [ { "name": "Vojta Kovář", "role": "the drunk smith", "town": "Lhota",
 *                         "story": "Drinks every night; his work suffers.", "ended": false, "opinion": 12, "yours": false } ] }
 * </pre>
 */
public final class PeopleSection implements HearthStateSections.Section {
	private static volatile LivesPayloads.@Nullable People last;

	private PeopleSection() {
	}

	/** Called once from the social module's client init. */
	public static void init() {
		HearthStateSections.register("people", new PeopleSection());
		ClientPlayNetworking.registerGlobalReceiver(LivesPayloads.People.TYPE, (payload, context) -> {
			last = payload;
			HearthViews.bump();
		});
	}

	@Override
	public JsonElement build(Minecraft mc) {
		LivesPayloads.People p = last;
		if (p == null) {
			return JsonNull.INSTANCE;
		}
		JsonArray rows = new JsonArray();
		for (LivesPayloads.Row r : p.rows()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", r.name());
			o.addProperty("role", Component.translatable("hearth.lives.role." + r.role()).getString());
			o.addProperty("town", r.town());
			o.addProperty("story", Component.translatable("hearth.lives.story." + r.thread() + "." + r.beat(), r.given()).getString());
			o.addProperty("ended", r.ended());
			o.addProperty("opinion", r.relation());
			o.addProperty("yours", r.by());
			rows.add(o);
		}
		JsonObject out = new JsonObject();
		out.add("rows", rows);
		return out;
	}

	/** The section as the host would get it now (tours). */
	public static String snapshot(Minecraft mc) {
		return new PeopleSection().build(mc).toString();
	}

	@Override
	public long fingerprint(Minecraft mc) {
		return System.identityHashCode(last);
	}
}
