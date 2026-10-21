package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.draft.Cursed;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.speech.SpeechHooks;
import dev.voidmc.mettle.character.skill.speech.SpeechRules;
import dev.voidmc.mettle.character.skill.survival.Repetition;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Speech tree (docs/BUILD_FEEL.md §14.2): XP without Hearth (villager trades, cures, Hero of the Village, piglin
 * barters; with Hearth its own reports come through {@code compat.hearth}), the perks' Mettle-side behaviours
 * ({@link SpeechHooks}), and what VOID Hearth reads: the effective Speech and Presence, the bonus keys and Speech's share
 * of the market edge ({@link #holdings}). Perk definitions are data in {@code perk/speech/}.
 */
public final class SpeechSkill {
	public static final ResourceKey<Skill> SPEECH = Progress.skill("speech");

	public static final Identifier HAGGLER = Mettle.id("speech/haggler");
	public static final Identifier GLIB = Mettle.id("speech/glib");
	public static final Identifier PROVISIONER = Mettle.id("speech/provisioner");
	public static final Identifier APPRAISER = Mettle.id("speech/appraiser");
	public static final Identifier LEDGER_KEEPER = Mettle.id("speech/ledger_keeper");
	public static final Identifier SHREWD_EYE = Mettle.id("speech/shrewd_eye");
	public static final Identifier CHARMER = Mettle.id("speech/charmer");
	public static final Identifier BARTERER = Mettle.id("speech/barterer");
	public static final Identifier COMMANDING_VOICE = Mettle.id("speech/commanding_voice");
	public static final Identifier SECOND_WORD = Mettle.id("speech/second_word");
	public static final Identifier MERCHANT_PRINCE = Mettle.id("speech/merchant_prince");
	public static final Identifier HEROS_WELCOME = Mettle.id("speech/heros_welcome");
	public static final Identifier VOICE_OF_THE_REALM = Mettle.id("speech/voice_of_the_realm");
	public static final Identifier HONEYED_LIES = Mettle.id("speech/honeyed_lies");
	public static final Identifier DEMAGOGUE = Mettle.id("speech/demagogue");
	public static final Identifier GOLDEN_MOUTH = Mettle.id("speech/golden_mouth");
	public static final Identifier MARKET_REGULAR = Mettle.id("speech/market_regular");
	public static final Identifier CARAVAN_MASTER = Mettle.id("speech/caravan_master");
	public static final Identifier TAVERN_KEEPER = Mettle.id("speech/tavern_keeper");
	public static final Identifier THE_COURTIERS_MANNERS = Mettle.id("speech/the_courtiers_manners");

	// Feat counters
	public static final String GOLDEN_MOUTH_COUNTER = "speech_golden_mouth";
	public static final String MARKET_REGULAR_COUNTER = "speech_market_regular";

	private static final Map<UUID, Map<String, Repetition>> REPETITION = new HashMap<>();

	private SpeechSkill() {
	}

	public static void init() {
		SpeechHooks.init();
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REPETITION.clear();
			SpeechHooks.clear();
		});
	}

	/** Awards Speech XP after the anti-farm taper for this kind of action (CHARACTER.md §3.3). */
	public static void award(ServerPlayer player, String kind, double base) {
		if (base <= 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		Repetition rep = REPETITION.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).computeIfAbsent(kind, k -> new Repetition());
		Progress.award(player, SPEECH, base * rep.next(player.level().getGameTime()));
	}

	/** Adds to a Speech feat's tally (creative players never earn one). */
	public static void tally(ServerPlayer player, String counter, long amount) {
		if (amount > 0 && !player.isCreative() && !player.isSpectator()) {
			FeatCounters.add(player, counter, amount);
		}
	}

	/** What the player holds of Speech's Hearth-facing effects (either side: the client's copy is the synced character). */
	public static SpeechRules.Holdings holdings(net.minecraft.world.entity.player.Player player) {
		CharacterState state = CharacterStore.get(player);
		int presence = state.attribute(CharacterAttribute.PRESENCE, CharacterStore.background(player.level().registryAccess(), state));
		boolean honeyed = Perks.has(player, HONEYED_LIES);
		double honeyedCost = honeyed ? Cursed.down(1 + SpeechRules.HONEYED_LIES_COST, Cursed.hex(player)) - 1 : 0;
		return new SpeechRules.Holdings(state.level(SPEECH), presence, Perks.rank(player, HAGGLER), Perks.rank(player, GLIB),
			CardHooks.pct(player, "speech/haggling"), CardHooks.flat(player, "speech/persuasive"), CardHooks.flat(player, "speech/shrewd"),
			CardHooks.pct(player, "speech/patient"), CardHooks.pct(player, "speech/smooth"), CardHooks.pct(player, "speech/imposing"),
			CardHooks.pct(player, "speech/charming"), Perks.has(player, SHREWD_EYE), Perks.has(player, CHARMER), Perks.has(player, COMMANDING_VOICE),
			Perks.has(player, SECOND_WORD), Perks.has(player, MERCHANT_PRINCE), Perks.has(player, HEROS_WELCOME), Perks.has(player, VOICE_OF_THE_REALM),
			honeyed, honeyedCost, Perks.has(player, DEMAGOGUE), Perks.has(player, THE_COURTIERS_MANNERS),
			Perks.has(player, PROVISIONER), CardHooks.pct(player, "speech/provisioning"), CardHooks.pct(player, "speech/appraising"),
			Perks.has(player, TAVERN_KEEPER));
	}

	/** The Speech Hearth reads ({@code PlayerSkills.level}): the level and what adds to it, at most 20. */
	public static int effectiveLevel(net.minecraft.world.entity.player.Player player) {
		return SpeechRules.effectiveSpeech(holdings(player), HearthLink.bonus());
	}

	/** The Presence Hearth reads ({@code PlayerSkills.attribute("presence")}), at most 20. */
	public static int effectivePresence(net.minecraft.world.entity.player.Player player) {
		return SpeechRules.effectivePresence(holdings(player), HearthLink.bonus());
	}

	/** A bonus key Speech answers (0 for the rest). */
	public static double bonus(ServerPlayer player, String key) {
		return SpeechRules.bonus(holdings(player), key);
	}

	/** A feedback chip, {@code mettle.feedback.speech.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback.speech." + key, args));
	}

	public static void forget(UUID player) {
		REPETITION.remove(player);
		SpeechHooks.forget(player);
	}
}
