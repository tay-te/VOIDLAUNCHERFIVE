package dev.voidmc.mettle.client.moment;

import com.google.gson.JsonObject;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.character.moment.MomentQueue;
import dev.voidmc.mettle.character.moment.SoundThrottle;
import dev.voidmc.mettle.client.MettleClientOptions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.client.overlay.OverlayBridge;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/**
 * Progress moments on the client (UI.md §6b): the chime, the toast at the top of the screen and the points nudge by
 * the hotbar, driven by the server's {@link MettlePayloads.Moment}. Each moment is also pushed to the overlay as
 * {@code mettleMoment} and kept a few seconds in the state's {@code moments} for hosts that hide the HUD.
 */
public final class MomentClient {
	/** The nudge stays this long after a level-up (or joining with points unspent), then fades. */
	public static final long NUDGE_MS = 20_000;
	/** A {@code Feedback} chip this soon after its moment is left off the action bar: the toast says it. */
	private static final long COVER_MS = 2_000;

	static final MomentQueue QUEUE = new MomentQueue();
	private static final SoundThrottle THROTTLE = new SoundThrottle();
	private static final Map<Identifier, Long> COVERED = new HashMap<>();
	private static long nudgeFrom = -1;
	private static long lastTickAt = -1;
	private static boolean joinChecked;
	private static int toastsDrawn;
	/** "kind subject" of every moment received since {@link #resetForTest}, for the client tour. */
	private static final java.util.List<String> RECEIVED = new java.util.ArrayList<>();

	private MomentClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MettlePayloads.Moment.TYPE, (payload, context) -> receive(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(MomentClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());
		HudElementRegistry.addLast(Mettle.id("character/moment"), MomentHud::extract);
	}

	/** The words of each milestone moment by "kind subject tier", for the native toast (MomentText). */
	private static final Map<String, net.minecraft.network.chat.Component[]> MILESTONE_TEXT = new HashMap<>();
	private static long lastHurtAt = Long.MIN_VALUE / 2;

	static net.minecraft.network.chat.Component[] milestoneText(MomentKind kind, String perk, int tier) {
		return MILESTONE_TEXT.getOrDefault(kind.serializedName() + " " + perk + " " + tier, new net.minecraft.network.chat.Component[2]);
	}

	static void receive(Minecraft mc, MettlePayloads.Moment m) {
		long now = Util.getMillis();
		if (m.kind().milestone()) {
			MILESTONE_TEXT.put(m.kind().serializedName() + " " + m.perk().map(Identifier::toString).orElse(null) + " " + m.tier(),
				new net.minecraft.network.chat.Component[] {m.text().orElse(null), m.sub().orElse(null)});
		}
		COVERED.put(m.feedbackId(), now);
		RECEIVED.add(m.kind().serializedName() + " " + m.feedbackId());
		JsonObject json = json(m);
		OverlayBridge.push("mettleMoment", json);
		MettleState.rememberMoment(json);
		MomentKind kind = m.kind();
		// A card kept through the overlay already played its flare and thunk (CardAudio): no chime on top.
		boolean hushed = kind.bought() && dev.voidmc.mettle.client.draft.CardAudio.hushesMoment();
		if (MettleClientOptions.sounds() && !hushed && THROTTLE.allow(kind, now)) {
			if (kind.milestone()) {
				MilestoneSounds.play(kind, m.tier());
			} else {
				MomentSounds.play(kind);
			}
		}
		// A perk bought from a screen: that screen already shows it, so no toast; the click-chime is the answer.
		boolean fromScreen = kind.bought() && mc.gui.screen() != null;
		if (MettleClientOptions.toasts() && !OverlayBridge.hasListener() && !fromScreen) {
			QUEUE.offer(kind, m.skill().map(k -> k.identifier().toString()).orElse(null),
				m.perk().map(Identifier::toString).orElse(null), m.level(), m.tier(), m.rank());
		}
		if (kind.levelUp()) {
			nudgeFrom = now;
		}
	}

	/** Whether a feedback chip is already told by a moment's toast (so the action bar can stay quiet). */
	public static boolean covers(Identifier feedbackPerk) {
		if (!MettleClientOptions.toasts()) {
			return false;
		}
		Long at = COVERED.get(feedbackPerk);
		return at != null && Util.getMillis() - at < COVER_MS;
	}

	private static void tick(Minecraft mc) {
		MomentSounds.tick();
		long now = Util.getMillis();
		long dt = lastTickAt < 0 ? 0 : now - lastTickAt;
		lastTickAt = now;
		// Toasts wait while a screen is open, rather than expiring unseen behind it.
		if (mc.player != null && mc.player.hurtTime > 0) {
			lastHurtAt = now;
		}
		// Milestones also wait for 5 s of calm after harm (BUILD_FEEL.md §2.4).
		QUEUE.advance(mc.gui.screen() == null && mc.player != null ? dt : 0, MomentQueue.calm(mc.gui.screen() != null, now - lastHurtAt));
		COVERED.values().removeIf(at -> now - at > COVER_MS);
		if (!joinChecked && mc.player != null && mc.player.tickCount > 60) {
			joinChecked = true;
			if (unspentPoints(mc.player) > 0) {
				nudgeFrom = now;
			}
		}
	}

	/** 0–1: how visible the points nudge is now. */
	static float nudgeVisibility(Player player) {
		if (nudgeFrom < 0 || !MettleClientOptions.pointsNudge()) {
			return 0f;
		}
		long age = Util.getMillis() - nudgeFrom;
		if (age >= NUDGE_MS || unspentPoints(player) <= 0) {
			return 0f;
		}
		return Math.min(1f, Math.min(age / 300f, (NUDGE_MS - age) / 1200f));
	}

	/** Drafts waiting in every released skill plus attribute points waiting to be placed, from the synced character. */
	public static int unspentPoints(Player player) {
		CharacterState state = CharacterStore.get(player);
		if (!state.hasBackground()) {
			return 0;
		}
		return Perks.attributePoints(player) + dev.voidmc.mettle.character.draft.Drafts.queuedReleased(player);
	}

	static JsonObject json(MettlePayloads.Moment m) {
		JsonObject o = new JsonObject();
		o.addProperty("kind", m.kind().serializedName());
		m.skill().ifPresent(k -> o.addProperty("skill", k.identifier().toString()));
		m.perk().ifPresent(p -> o.addProperty("perk", p.toString()));
		o.addProperty("level", m.level());
		o.addProperty("tier", m.tier());
		o.addProperty("rank", m.rank());
		// Build feel's milestones: the server's words, and whether the banner shows (a byname tier, a 6-set).
		o.addProperty("milestone", m.kind().milestone());
		o.addProperty("big", m.kind().big(m.tier()));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.getConnection() != null) {
			MomentText.Lines lines = MomentText.lines(mc, m.kind(), m.skill().map(k -> k.identifier().toString()).orElse(null),
				m.perk().map(Identifier::toString).orElse(null), m.level(), m.tier(), m.rank());
			o.addProperty("title", lines.lead().getString() + (lines.rest() == null ? "" : " · " + lines.rest().getString()));
			if (m.kind().milestone()) {
				m.text().ifPresent(t -> o.addProperty("text", t.getString()));
			}
			if (lines.subtitle() != null) {
				o.addProperty("subtitle", lines.subtitle().getString());
			}
			o.addProperty("colour", String.format(java.util.Locale.ROOT, "#%06X", lines.accent() & 0xFFFFFF));
		}
		return o;
	}

	static void drawn() {
		toastsDrawn++;
	}

	// ---------------------------------------------------------------- for the client tour

	/** Forgets queued toasts and the kinds seen, so a journey starts clean. */
	public static void resetForTest() {
		QUEUE.clear();
		THROTTLE.clear();
		RECEIVED.clear();
		toastsDrawn = 0;
	}

	/** Every moment received since {@link #resetForTest}, as "kind id" ("tier mettle:level/mining"). */
	public static java.util.List<String> received() {
		return java.util.List.copyOf(RECEIVED);
	}

	/** The toast on screen now as "kind subject" ("learned mettle:mining/quarryman"), or null. */
	public static String showingKey() {
		MomentQueue.Entry e = QUEUE.current();
		return e == null ? null : e.kind().serializedName() + " " + (e.perk() != null ? e.perk() : e.skill() != null ? e.skill() : "character");
	}

	public static java.util.Set<MomentKind> seenKinds() {
		return QUEUE.seen();
	}

	public static int offered() {
		return QUEUE.offered();
	}

	/** The kind on screen now, or null. */
	public static MomentKind showing() {
		return QUEUE.current() == null ? null : QUEUE.current().kind();
	}

	/** Whether the points nudge is up (or fading) now. */
	public static boolean nudgeShowing() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && nudgeVisibility(mc.player) > 0f;
	}

	/** Frames in which a toast was drawn since {@link #resetForTest}. */
	public static int toastsDrawn() {
		return toastsDrawn;
	}

	private static void reset() {
		QUEUE.clear();
		THROTTLE.clear();
		MomentSounds.clear();
		COVERED.clear();
		MILESTONE_TEXT.clear();
		nudgeFrom = -1;
		lastTickAt = -1;
		joinChecked = false;
	}
}
