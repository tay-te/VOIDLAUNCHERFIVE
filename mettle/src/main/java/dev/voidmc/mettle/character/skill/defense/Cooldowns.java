package dev.voidmc.mettle.character.skill.defense;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * The long Defense cooldowns (Last Stand's ten minutes, Second Wind's five, Still Standing's one), saved with the
 * player so that logging out and back in does not reset them. Game-time ticks at which each is ready again.
 */
public final class Cooldowns {
	public static final String LAST_STAND = "last_stand";
	public static final String SECOND_WIND = "second_wind";
	public static final String STILL_STANDING = "still_standing";

	public static final AttachmentType<Map<String, Long>> READY_AT = AttachmentRegistry.create(Mettle.id("character/defense_cooldowns"), b -> b
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
		.copyOnDeath());

	private Cooldowns() {
	}

	/** Loads the class, so the attachment is registered before any player is read from disk. */
	public static void init() {
	}

	public static boolean ready(ServerPlayer player, String what) {
		return DefenseRules.ready(now(player), player.getAttachedOrElse(READY_AT, Map.of()).getOrDefault(what, 0L));
	}

	/** Ticks left (0 when ready), for status lines and tests. */
	public static long remaining(ServerPlayer player, String what) {
		return Math.max(0, player.getAttachedOrElse(READY_AT, Map.of()).getOrDefault(what, 0L) - now(player));
	}

	public static void start(ServerPlayer player, String what, int ticks) {
		// The Echo set (cards) shortens every perk cooldown (docs/CARDS.md §6.3, Cooldowns.scale).
		// Echo 6 (Reverberate): one time in four the perk is ready again at once.
		set(player, what, now(player) + (dev.voidmc.mettle.character.cards.CardHooks.reverberates(player) ? 0 : Math.round(ticks * scale(player))));
	}

	/** How long cooldowns last for this player (1 = as written): the Echo set's 2 and 4 tiers make them shorter. */
	public static float scale(net.minecraft.world.entity.player.Player player) {
		return dev.voidmc.mettle.character.cards.CardHooks.cooldownScale(player);
	}

	/** Makes one ready now (tests, a future "rested" bonus). */
	public static void reset(ServerPlayer player, String what) {
		set(player, what, 0L);
	}

	private static void set(ServerPlayer player, String what, long at) {
		Map<String, Long> next = new HashMap<>(player.getAttachedOrElse(READY_AT, Map.of()));
		next.put(what, at);
		player.setAttached(READY_AT, Map.copyOf(next));
	}

	private static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}
}
