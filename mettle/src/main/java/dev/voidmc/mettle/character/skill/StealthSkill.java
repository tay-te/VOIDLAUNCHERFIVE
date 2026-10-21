package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.stealth.Sneaking;
import dev.voidmc.mettle.character.skill.stealth.StealthHooks;
import dev.voidmc.mettle.character.skill.stealth.StealthRules;
import dev.voidmc.mettle.character.skill.survival.Repetition;
import dev.voidmc.mettle.compat.HearthLink;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Stealth tree (docs/BUILD_FEEL.md §14.1): XP from sneaking near hostiles that haven't seen you, from sneak attacks and
 * from slipping past a Warden; the perks' behaviours ({@code skill.stealth}, reached from {@code mixin.stealth}); and the
 * effective Stealth VOID Hearth reads ({@link #holdings}). Perk definitions are data in {@code perk/stealth/}; Soft Step
 * and Skulker's speed are attribute effects there.
 */
public final class StealthSkill {
	public static final ResourceKey<Skill> STEALTH = Progress.skill("stealth");

	public static final Identifier SOFT_STEP = Mettle.id("stealth/soft_step");
	public static final Identifier SHADOW = Mettle.id("stealth/shadow");
	public static final Identifier QUIET_FEET = Mettle.id("stealth/quiet_feet");
	public static final Identifier CLOAK_OF_DUSK = Mettle.id("stealth/cloak_of_dusk");
	public static final Identifier VANISH = Mettle.id("stealth/vanish");
	public static final Identifier SILENT_FALL = Mettle.id("stealth/silent_fall");
	public static final Identifier AMBUSHER = Mettle.id("stealth/ambusher");
	public static final Identifier HAMSTRING = Mettle.id("stealth/hamstring");
	public static final Identifier UNSEEN_BLADE = Mettle.id("stealth/unseen_blade");
	public static final Identifier POISONER = Mettle.id("stealth/poisoner");
	public static final Identifier GHOST = Mettle.id("stealth/ghost");
	public static final Identifier SHADOWSTEP = Mettle.id("stealth/shadowstep");
	public static final Identifier ASSASSIN = Mettle.id("stealth/assassin");
	public static final Identifier SKULKER = Mettle.id("stealth/skulker");
	public static final Identifier COWARDS_EDGE = Mettle.id("stealth/cowards_edge");
	public static final Identifier NEVER_SEEN = Mettle.id("stealth/never_seen");
	public static final Identifier UNDER_ITS_NOSE = Mettle.id("stealth/under_its_nose");
	public static final Identifier THE_QUIET_WAY = Mettle.id("stealth/the_quiet_way");
	public static final Identifier NIGHT_HUNTER = Mettle.id("stealth/night_hunter");
	public static final Identifier FOOTPAD = Mettle.id("stealth/footpad");

	// Feat counters
	public static final String NEVER_SEEN_COUNTER = "stealth_never_seen";
	public static final String WARDEN_PASS_COUNTER = "stealth_warden_pass";

	/** Talent Silver Tongue (docs/BUILD_FEEL.md §13.2): a share of market edge, read from the cards' trait totals. */
	public static final Identifier SILVER_TONGUE = Mettle.id("talent/silver_tongue");

	private static final Map<UUID, Map<String, Repetition>> REPETITION = new HashMap<>();
	private static final Map<UUID, Map<Identifier, Long>> LAST_CHIP = new HashMap<>();

	private StealthSkill() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Sneaking.tick(player, tick);
			}
			StealthHooks.tickMobs(server);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StealthHooks::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(StealthHooks::died);
		// Arrows loosed while sneaking stay sneak attacks wherever they land (Night Hunter).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> StealthHooks.arrowLoaded(entity));
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REPETITION.clear();
			LAST_CHIP.clear();
			Sneaking.clear();
			StealthHooks.clear();
		});
	}

	/** Awards Stealth XP after the anti-farm taper for this kind of action (CHARACTER.md §3.3). */
	public static void award(ServerPlayer player, String kind, double base) {
		if (base <= 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		Repetition rep = REPETITION.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).computeIfAbsent(kind, k -> new Repetition());
		Progress.award(player, STEALTH, base * rep.next(player.level().getGameTime()));
	}

	/** What the player holds of Stealth's Hearth-facing effects, and the moment's conditions. */
	public static StealthRules.Holdings holdings(ServerPlayer player) {
		int level = CharacterStore.get(player).level(STEALTH);
		boolean sneaking = Sneaking.sneaking(player);
		boolean dark = Sneaking.dark(player);
		boolean night = player.level().isDarkOutside();
		return new StealthRules.Holdings(level, Perks.rank(player, SHADOW), Perks.has(player, CLOAK_OF_DUSK), StealthHooks.pct(player, "stealth/unseen"),
			Perks.has(player, GHOST), Perks.has(player, THE_QUIET_WAY), StealthHooks.pct(player, "stealth/light_fingered"),
			CardStore.get(player).trait(SILVER_TONGUE) / 100.0, sneaking, dark, night, Sneaking.attacking(player));
	}

	/** The Stealth Hearth reads (PlayerSkills.level): the level, Shadow, and the fallbacks while Hearth reads no keys; at most 20. */
	public static int effectiveLevel(ServerPlayer player) {
		return StealthRules.effectiveLevel(holdings(player), HearthLink.bonus());
	}

	/** A bonus key Stealth answers (0 for the rest). */
	public static double bonus(ServerPlayer player, String key) {
		return StealthRules.bonus(holdings(player), key);
	}

	/** A feedback chip, {@code mettle.feedback.stealth.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback.stealth." + key, args));
	}

	/** The same, at most once per {@code ticks} for this perk. */
	public static void chipThrottled(ServerPlayer player, Identifier perk, int ticks, String key, Object... args) {
		long now = player.level().getGameTime();
		Map<Identifier, Long> last = LAST_CHIP.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Long before = last.get(perk);
		if (before == null || now - before >= ticks || now < before) {
			last.put(perk, now);
			chip(player, perk, key, args);
		}
	}

	public static void forget(UUID player) {
		REPETITION.remove(player);
		LAST_CHIP.remove(player);
		Sneaking.forget(player);
	}
}
