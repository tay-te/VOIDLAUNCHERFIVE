package dev.voidmc.hearth.client.social.lives;

import com.google.gson.JsonElement;
import dev.voidmc.hearth.client.overlay.HearthStateSections;
import dev.voidmc.hearth.client.overlay.HearthViews;
import dev.voidmc.hearth.client.overlay.LivingWorldJson;
import dev.voidmc.hearth.client.overlay.Portraits;
import dev.voidmc.hearth.social.lives.LivesPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The People section of the Hearth state (docs/LIVING_WORLD_2.md §4.1, §10 F4; Figma "People A, list and story", picked
 * 2026-10-21): the named characters the player has met, by town, with each one's story — the beats as they went, the
 * choice that moved it, where it may go, the relations it touches, where to find them — and how they stand with the
 * player ({@link LivingWorldJson#people}). The page's "Waypoint" follows one ({@code HearthActions.trackPerson}); where the
 * followed one is comes as the {@code personTrack} section. The same list is told in conversation ("Who are the folk of
 * note here?").
 */
public final class PeopleSection implements HearthStateSections.Section {
	private static volatile LivesPayloads.@Nullable People last;
	private static volatile LivesPayloads.@Nullable Followed followed;

	private PeopleSection() {
	}

	/** Called once from the social module's client init. */
	public static void init() {
		HearthStateSections.register("people", new PeopleSection());
		HearthStateSections.register("personTrack", new HearthStateSections.Section() {
			@Override
			public JsonElement build(Minecraft mc) {
				return LivingWorldJson.personTrack(followed, mc.player == null ? null : mc.player.blockPosition(),
					mc.player == null ? 0 : mc.player.getYRot());
			}

			@Override
			public long fingerprint(Minecraft mc) {
				LivesPayloads.Followed f = followed;
				long h = System.identityHashCode(f);
				if (f != null && f.active() && mc.player != null) {
					h = h * 31 + mc.player.blockPosition().asLong();
					h = h * 31 + Math.round(mc.player.getYRot() / 5);
				}
				return h;
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LivesPayloads.People.TYPE, (payload, context) -> {
			last = payload;
			HearthViews.bump();
		});
		ClientPlayNetworking.registerGlobalReceiver(LivesPayloads.Followed.TYPE, (payload, context) -> {
			followed = payload.active() ? payload : null;
			HearthViews.bump();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			last = null;
			followed = null;
		});
	}

	/** The People list as the server last sent it, or null. */
	public static LivesPayloads.@Nullable People people() {
		return last;
	}

	/** The character followed now (the People page's waypoint), or null: for the map. */
	public static LivesPayloads.@Nullable Followed followed() {
		return followed;
	}

	@Override
	public JsonElement build(Minecraft mc) {
		return LivingWorldJson.people(last, Portraits::ofKey, followed);
	}

	/** The section as the host would get it now (tours). */
	public static String snapshot(Minecraft mc) {
		return new PeopleSection().build(mc).toString();
	}

	@Override
	public long fingerprint(Minecraft mc) {
		return System.identityHashCode(last) * 31L + System.identityHashCode(followed);
	}
}
