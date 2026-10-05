package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.marksmanship.Arrowstorm;
import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import dev.voidmc.mettle.character.skill.marksmanship.Hits;
import dev.voidmc.mettle.character.skill.marksmanship.HuntersMark;
import dev.voidmc.mettle.character.skill.marksmanship.Shots;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Marksmanship tree (TREES.md §6): its perks' behaviours. XP comes from {@link Combat}; the perk definitions are
 * data in {@code data/mettle/mettle/perk/marksmanship/}, mixins in {@code mixin.marksmanship}.
 *
 * <p>Where each perk lives: draw speed (Steady Aim, Quick Nock, Steppe Draw) in {@link Draw}, which both sides use so
 * the bow's animation matches the shot; what happens as arrows leave the bow (Steady Breath, Double Nock, Volley,
 * Sixty Paces, Leaping Shot, Steppe Draw's saddle) in {@link Shots}; what happens when they land (Long Shot, Pinning
 * Shot, Deadeye, Wingshot, Retrieval, Huntsman, Hunter's Mark) in {@link Hits} and {@link HuntersMark}; Arrowstorm in
 * {@link Arrowstorm}. Fletcher and Quiverwise are single hooks in their mixins.
 */
public final class MarksmanshipSkill {
	public static final Identifier STEADY_AIM = Mettle.id("marksmanship/steady_aim");
	public static final Identifier RETRIEVAL = Mettle.id("marksmanship/retrieval");
	public static final Identifier LONG_SHOT = Mettle.id("marksmanship/long_shot");
	public static final Identifier HUNTERS_MARK = Mettle.id("marksmanship/hunters_mark");
	public static final Identifier STEADY_BREATH = Mettle.id("marksmanship/steady_breath");
	public static final Identifier PINNING_SHOT = Mettle.id("marksmanship/pinning_shot");
	public static final Identifier QUICK_NOCK = Mettle.id("marksmanship/quick_nock");
	public static final Identifier FLETCHER = Mettle.id("marksmanship/fletcher");
	public static final Identifier DOUBLE_NOCK = Mettle.id("marksmanship/double_nock");
	public static final Identifier QUIVERWISE = Mettle.id("marksmanship/quiverwise");
	public static final Identifier DEADEYE = Mettle.id("marksmanship/deadeye");
	public static final Identifier VOLLEY = Mettle.id("marksmanship/volley");
	public static final Identifier ARROWSTORM = Mettle.id("marksmanship/arrowstorm");
	public static final Identifier SIXTY_PACES = Mettle.id("marksmanship/sixty_paces");
	public static final Identifier WINGSHOT = Mettle.id("marksmanship/wingshot");
	public static final Identifier STEPPE_DRAW = Mettle.id("marksmanship/steppe_draw");
	public static final Identifier HUNTSMAN = Mettle.id("marksmanship/huntsman");
	public static final Identifier LEAPING_SHOT = Mettle.id("marksmanship/leaping_shot");

	/** Feat counters (see the feat JSONs). */
	public static final String SIXTY_PACES_COUNTER = "marksmanship_hit_from_60";
	public static final String WINGSHOT_COUNTER = "marksmanship_flier_kills";

	/** A perk that fires often (every hit, every shot) says so at most once a second. */
	private static final int CHIP_GAP = 20;
	private static final Map<UUID, Map<Identifier, Long>> LAST_CHIP = new HashMap<>();

	private MarksmanshipSkill() {
	}

	public static void init() {
		HuntersMark.init();
		Hits.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Arrowstorm.tick(player);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
	}

	/** The perk's feedback chip, unless the same perk already spoke within the last second. */
	public static void chip(ServerPlayer player, Identifier perk, Component text) {
		long now = player.level().getGameTime();
		Map<Identifier, Long> last = LAST_CHIP.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Long before = last.get(perk);
		if (before != null && now - before < CHIP_GAP && now >= before) {
			return;
		}
		last.put(perk, now);
		SignaturePerks.feedback(player, perk, text);
	}

	public static Component text(Identifier perk, Object... args) {
		return Component.translatable("mettle.feedback.marksmanship." + perk.getPath().substring("marksmanship/".length()), args);
	}

	public static void forget(UUID player) {
		LAST_CHIP.remove(player);
		Draw.forget(player);
		Shots.forget(player);
		Arrowstorm.forget(player);
		HuntersMark.forget(player);
	}
}
