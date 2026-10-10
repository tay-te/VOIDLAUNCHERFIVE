package dev.voidmc.mettle.client.draft;

import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.draft.CardCues;
import dev.voidmc.mettle.character.draft.Drafts;
import dev.voidmc.mettle.client.MettleClientOptions;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.DraftState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

/**
 * Plays the card draft's sounds (docs/SOUNDS.md) on the client, from the cues in {@link CardCues}. Every layer is a
 * local UI sound (the UI volume slider governs it), scheduled to the millisecond: due layers start on the next client
 * tick or the next frame of whatever screen is open, so a 75 ms card fan lands as written.
 *
 * <p>Results play by themselves: {@code MettleActions.pickCard} (flare, thunk, the other two burn away) and
 * {@code reroll} (shuffle, or a dull no when none are left) call in here, and a new draft waiting brings a quiet nudge.
 * The presentation is the overlay's to time: it calls {@code MettleActions.cardSound} as its animations run (the deal,
 * hovers, the Mythic reveal).
 */
public final class CardAudio {
	private record Note(long dueMs, Identifier sound, float pitch, float volume) {
	}

	private static final List<Note> PENDING = new ArrayList<>();
	private static final List<SoundInstance> LIVE = new ArrayList<>();
	private static final Random RANDOM = new Random();
	/** Cues played since {@link #resetForTest}, as "cue" or "cue@arg", for the client tour. */
	private static final List<String> PLAYED = new ArrayList<>();

	private static long lastHoverAt = Long.MIN_VALUE / 2;
	private static long lastPickAt = Long.MIN_VALUE / 2;
	private static long lastNudgeAt = Long.MIN_VALUE / 2;
	private static long nudgeDue = -1;
	private static int lastWaiting = -1;
	private static MythicShimmer shimmer;
	/** The sound engine refused the shimmer (no device, or volume off), for the tour. */
	private static boolean silent;

	private CardAudio() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CardAudio::tick);
		// Frame-accurate starts while a screen (the draft) is open; the client tick covers the rest.
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> flush()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());
	}

	// ---------------------------------------------------------------- what the overlay calls (MettleActions.cardSound)

	/**
	 * One call for every sound the overlay times: {@code deal} (arg: the skill whose hand fanned out; its stinger
	 * follows), {@code hover} (arg: a rarity, or {@code skill#index}), {@code unhover}, {@code mythic_reveal},
	 * {@code stop}, or any cue by name ({@code slot}, {@code set_complete}, {@code burn}; a {@code stinger} or
	 * {@code pick} with a rarity arg).
	 */
	public static void call(String cue, String arg) {
		String a = arg == null ? "" : arg.trim();
		switch (cue == null ? "" : cue.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "deal" -> deal(a);
			case "hover" -> {
				int hash = a.lastIndexOf('#');
				if (hash > 0) {
					try {
						hoverCard(a.substring(0, hash), Integer.parseInt(a.substring(hash + 1).trim()));
					} catch (NumberFormatException ignored) {
						hover(a.substring(0, hash));
					}
				} else {
					hover(a);
				}
			}
			case "unhover" -> unhover();
			case "mythic_reveal" -> mythicReveal();
			case "stop" -> stop();
			case "pick" -> pickSounds(CardCues.rarityKey(a, false));
			case "stinger" -> play(CardCues.get().forRarity("stinger", CardCues.rarityKey(a, false)), 0, a);
			case "" -> {
			}
			default -> play(cue.trim(), 0, a);
		}
	}

	/** The hand of a skill fanned out: the deal, and the stinger of its rarest card (a Mythic gets its reveal instead). */
	public static void deal(String skill) {
		CardCues cues = CardCues.get();
		play("deal", 0, skill);
		Set<String> keys = new LinkedHashSet<>();
		for (DraftState.Card card : hand(skill)) {
			keys.add(CardCues.rarityKey(card.rarity().getSerializedName(), card.wildcard()));
		}
		if (!keys.contains("mythic")) {
			cues.stinger(keys).ifPresent(s -> play(s, cues.rules().stingerAtMs(), skill));
		}
	}

	/** The pointer is on a card of this rarity ({@code "epic"}, {@code "cursed"}, {@code "wildcard"}, {@code "mythic"}...). */
	public static void hover(String rarity) {
		long now = Util.getMillis();
		CardCues cues = CardCues.get();
		if (now - lastHoverAt < cues.rules().hoverGapMs()) {
			return;
		}
		lastHoverAt = now;
		String key = CardCues.rarityKey(rarity, false);
		play(cues.forRarity("hover", key), 0, key);
		if (key.equals("mythic")) {
			startShimmer(cues);
		} else {
			unhover();
		}
	}

	/** {@link #hover(String)} for the {@code index}-th card of a skill's hand, read from the synced character. */
	public static void hoverCard(String skill, int index) {
		List<DraftState.Card> hand = hand(skill);
		if (index >= 0 && index < hand.size()) {
			DraftState.Card card = hand.get(index);
			hover(CardCues.rarityKey(card.rarity().getSerializedName(), card.wildcard()));
		}
	}

	/** The pointer left the cards: the Mythic shimmer fades. */
	public static void unhover() {
		if (shimmer != null && !shimmer.releasing()) {
			shimmer.release();
			if (Minecraft.getInstance().isPaused()) {
				Minecraft.getInstance().getSoundManager().stop(shimmer);
				shimmer = null;
			}
		}
	}

	/** The Mythic reveal (darkness, tremble, cracks, swell, burst, tail); markers in {@link #describe()} time the visuals. */
	public static void mythicReveal() {
		stop();
		play("mythic_reveal", 0, "");
	}

	/** Cuts everything the draft is playing (the overlay closed, or the reveal was skipped). */
	public static void stop() {
		PENDING.clear();
		var sounds = Minecraft.getInstance().getSoundManager();
		for (SoundInstance s : LIVE) {
			sounds.stop(s);
		}
		LIVE.clear();
		if (shimmer != null) {
			sounds.stop(shimmer);
			shimmer = null;
		}
	}

	// ---------------------------------------------------------------- results (MettleActions)

	/** A card kept: its flare and thunk, its rarity's sweetener, and the other two burning away. */
	public static void pick(String skill, int index) {
		List<DraftState.Card> hand = hand(skill);
		String key = "common";
		if (index >= 0 && index < hand.size()) {
			DraftState.Card card = hand.get(index);
			key = CardCues.rarityKey(card.rarity().getSerializedName(), card.wildcard());
		}
		pickSounds(key);
	}

	private static void pickSounds(String key) {
		CardCues cues = CardCues.get();
		unhover();
		lastPickAt = Util.getMillis();
		play("pick", 0, key);
		if (cues.has("pick." + key)) {
			play("pick." + key, 0, key);
		}
		play("burn", cues.rules().burnAfterPickMs(), key);
	}

	/** A reroll asked for: the hand swept away and the deck shuffled, or a dull "no" when there is nothing to reroll. */
	public static void reroll(String skill) {
		Minecraft mc = Minecraft.getInstance();
		boolean can = mc.player != null && CharacterStore.get(mc.player).drafts().rerolls() > 0 && !hand(skill).isEmpty();
		play(can ? "reroll" : "reroll_denied", 0, skill);
	}

	/** Whether a pick just played its own sounds, so the "learned" moment chime that follows can stay quiet. */
	public static boolean hushesMoment() {
		return Util.getMillis() - lastPickAt < CardCues.get().rules().pickHushesMomentMs();
	}

	/** The timing the overlay can sync its animations to: {@code draft.sound} in the state (docs/SOUNDS.md §5). */
	public static JsonObject describe() {
		CardCues cues = CardCues.get();
		JsonObject o = new JsonObject();
		o.add("markers", cues.markers());
		o.addProperty("hoverGapMs", cues.rules().hoverGapMs());
		o.addProperty("stingerAtMs", cues.rules().stingerAtMs());
		o.addProperty("burnAfterPickMs", cues.rules().burnAfterPickMs());
		return o;
	}

	// ---------------------------------------------------------------- scheduling

	private static void play(String cue, int offsetMs, String arg) {
		CardCues cues = CardCues.get();
		if (!cues.has(cue)) {
			return;
		}
		PLAYED.add(arg == null || arg.isEmpty() ? cue : cue + "@" + arg);
		long start = Util.getMillis() + offsetMs;
		for (CardCues.Layer l : cues.cue(cue)) {
			float jitter = l.jitter() <= 0 ? 1f : 1f + (RANDOM.nextFloat() * 2f - 1f) * l.jitter();
			PENDING.add(new Note(start + l.atMs(), l.id(), l.pitch() * jitter, l.volume()));
		}
		flush();
	}

	private static void startShimmer(CardCues cues) {
		if (shimmer != null && !shimmer.isStopped()) {
			shimmer.hold();
			return;
		}
		cues.loop("mythic_idle").ifPresent(loop -> {
			Minecraft mc = Minecraft.getInstance();
			shimmer = new MythicShimmer(loop, mc.isPaused());
			if (mc.getSoundManager().play(shimmer) == net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED) {
				// No sound device (or UI volume at zero): nothing to hold.
				shimmer = null;
				silent = true;
			}
		});
	}

	private static void flush() {
		if (PENDING.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		for (Iterator<Note> it = PENDING.iterator(); it.hasNext(); ) {
			Note n = it.next();
			if (n.dueMs() <= now) {
				it.remove();
				SimpleSoundInstance s = new SimpleSoundInstance(n.sound(), SoundSource.UI, n.volume(), n.pitch(),
					SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
				mc.getSoundManager().play(s);
				LIVE.add(s);
			}
		}
	}

	private static void tick(Minecraft mc) {
		flush();
		var sounds = mc.getSoundManager();
		LIVE.removeIf(s -> !sounds.isActive(s));
		if (shimmer != null && (shimmer.isStopped() || !sounds.isActive(shimmer) || (shimmer.releasing() && mc.isPaused()))) {
			if (!shimmer.isStopped()) {
				sounds.stop(shimmer);
			}
			shimmer = null;
		}
		nudge(mc);
	}

	/** A new draft waiting brings a quiet nudge after the level-up chime, unless a screen is up (the draft deals there). */
	private static void nudge(Minecraft mc) {
		long now = Util.getMillis();
		if (mc.player == null || mc.player.tickCount < 60) {
			lastWaiting = -1;
			return;
		}
		CharacterState state = CharacterStore.get(mc.player);
		int waiting = state.hasBackground() ? Drafts.queuedReleased(mc.player) : 0;
		CardCues.Rules rules = CardCues.get().rules();
		if (lastWaiting >= 0 && waiting > lastWaiting && now - lastNudgeAt > rules.nudgeGapMs()) {
			nudgeDue = now + rules.nudgeDelayMs();
		}
		lastWaiting = waiting;
		if (nudgeDue > 0 && now >= nudgeDue) {
			nudgeDue = -1;
			if (mc.gui.screen() == null && MettleClientOptions.sounds()) {
				lastNudgeAt = now;
				play("nudge", 0, "");
			}
		}
	}

	private static List<DraftState.Card> hand(String skill) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || skill == null || skill.isBlank()) {
			return List.of();
		}
		Identifier id = Identifier.tryParse(skill.trim());
		if (id == null) {
			return List.of();
		}
		ResourceKey<Skill> key = ResourceKey.create(MettleRegistries.SKILL, id);
		return CharacterStore.get(mc.player).drafts().hand(key);
	}

	private static void reset() {
		PENDING.clear();
		LIVE.clear();
		shimmer = null;
		nudgeDue = -1;
		lastWaiting = -1;
	}

	// ---------------------------------------------------------------- for the client tour

	public static void resetForTest() {
		stop();
		PLAYED.clear();
		silent = false;
		lastHoverAt = Long.MIN_VALUE / 2;
	}

	/** Cues played since {@link #resetForTest}. */
	public static List<String> played() {
		return List.copyOf(PLAYED);
	}

	/** Layers waiting to start. */
	public static int pending() {
		return PENDING.size();
	}

	/** Whether the Mythic shimmer is playing (or fading). */
	public static boolean shimmering() {
		return shimmer != null && !shimmer.isStopped();
	}

	/** Whether the sound engine refused to start the shimmer (no device, or the UI volume at zero). */
	public static boolean silent() {
		return silent;
	}
}
