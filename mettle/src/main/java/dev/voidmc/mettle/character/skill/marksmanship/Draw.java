package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

/**
 * Bow draw speed: Steady Aim, Quick Nock and Steppe Draw. Runs on both sides with the same inputs (the owner's synced
 * perks and the game time), so the client's pull animation, zoom and arm match the power the server gives the shot.
 *
 * <p>Each side keeps its own "last shot" times because in singleplayer both live in one JVM.
 */
public final class Draw {
	private static final Map<UUID, Long> SERVER_LAST_SHOT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CLIENT_LAST_SHOT = new ConcurrentHashMap<>();

	private Draw() {
	}

	private static Map<UUID, Long> lastShots(Player player) {
		return player.level().isClientSide() ? CLIENT_LAST_SHOT : SERVER_LAST_SHOT;
	}

	/** Whether a draw that began {@code ticksHeld} ago was nocked within a second of the last shot (Quick Nock). */
	public static boolean quickNock(Player player, int ticksHeld) {
		if (!Perks.has(player, MarksmanshipSkill.QUICK_NOCK)) {
			return false;
		}
		long last = lastShots(player).getOrDefault(player.getUUID(), -1L);
		return MarksmanshipRules.quickNock(player.level().getGameTime() - ticksHeld, last);
	}

	/** How many times faster than vanilla this player is drawing the bow they have held for {@code ticksHeld}. */
	public static float speed(Player player, int ticksHeld) {
		return MarksmanshipRules.drawSpeed(Perks.rank(player, MarksmanshipSkill.STEADY_AIM), quickNock(player, ticksHeld),
			Perks.has(player, MarksmanshipSkill.STEPPE_DRAW)) * dev.voidmc.mettle.character.draft.Cursed.drawSpeed(player)
			* dev.voidmc.mettle.character.cards.CardHooks.drawSpeed(player);
	}

	/** The draw ticks the bow counts after perks (vanilla reaches full power at 20). */
	public static float effectiveTicks(Player player, float ticksHeld) {
		return ticksHeld * speed(player, (int) ticksHeld);
	}

	/** The bow's power (0–1) after perks, replacing {@code BowItem.getPowerForTime} in {@code releaseUsing}. */
	public static float power(Player player, int ticksHeld) {
		return MarksmanshipRules.bowPower(effectiveTicks(player, ticksHeld));
	}

	/** Whether the player is holding a fully drawn bow right now. */
	public static boolean fullyDrawn(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem
			&& effectiveTicks(player, player.getTicksUsingItem()) >= MarksmanshipRules.FULL_DRAW;
	}

	/** A bow shot left the string; on the server, says so when Quick Nock sped it up. */
	public static void loosed(Player player, int ticksHeld) {
		if (player instanceof ServerPlayer sp && quickNock(player, ticksHeld)) {
			MarksmanshipSkill.chip(sp, MarksmanshipSkill.QUICK_NOCK, MarksmanshipSkill.text(MarksmanshipSkill.QUICK_NOCK));
		}
		lastShots(player).put(player.getUUID(), player.level().getGameTime());
	}

	public static void forget(UUID player) {
		SERVER_LAST_SHOT.remove(player);
	}

	/** Client side: forget everyone when leaving a world. */
	public static void forgetClient() {
		CLIENT_LAST_SHOT.clear();
	}
}
