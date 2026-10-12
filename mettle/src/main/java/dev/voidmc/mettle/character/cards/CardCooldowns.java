package dev.voidmc.mettle.character.cards;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * The cooldowns of sets and Mythics (Pardon, Pact, Homeward, the Dragon Standard, Rally Cry, Updraft, Kindled, the
 * Stride, Aegis, Worldsplitter, Heart of the Mountain): the game time each is ready again, saved with the player and kept
 * through death, so logging out, a restart or dying never resets one. Synced to its owner, so the client's own moves
 * (Updraft, the Stride) wait as the server does.
 */
public final class CardCooldowns {
	public static final String PARDON = "pardon";
	public static final String PACT = "pact";
	public static final String HOMEWARD = "homeward";
	public static final String STANDARD = "standard";
	public static final String RALLY = "rally";
	public static final String UPDRAFT = "updraft";
	public static final String KINDLED = "kindled";
	public static final String DASH = "dash";
	public static final String AEGIS = "aegis";
	public static final String SPLIT = "split";
	public static final String BORE = "bore";

	public static final AttachmentType<Map<String, Long>> READY_AT = AttachmentRegistry.create(Mettle.id("character/card_cooldowns"), b -> b
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
		.syncWith(ByteBufCodecs.<io.netty.buffer.ByteBuf, String, Long, Map<String, Long>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
			AttachmentSyncPredicate.targetOnly())
		.copyOnDeath());

	private CardCooldowns() {
	}

	/** Loads the class, so the attachment is registered before any player is read from disk. */
	static void init() {
	}

	/** The game time a cooldown ends (0 when it never ran). Both sides. */
	public static long readyAt(Player player, String what) {
		return player.getAttachedOrElse(READY_AT, Map.of()).getOrDefault(what, 0L);
	}

	public static boolean ready(Player player, String what) {
		return player.level().getGameTime() >= readyAt(player, what);
	}

	/** Ticks left (0 when ready), for tests and status lines. */
	public static long remaining(Player player, String what) {
		return Math.max(0, readyAt(player, what) - player.level().getGameTime());
	}

	/** Starts one: ready again {@code ticks} from now (server). */
	public static void start(Player player, String what, long ticks) {
		Map<String, Long> next = new HashMap<>(player.getAttachedOrElse(READY_AT, Map.of()));
		long now = player.level().getGameTime();
		// Forget the ones long past, so the map stays small.
		next.values().removeIf(at -> at < now - 20);
		next.put(what, now + ticks);
		player.setAttached(READY_AT, Map.copyOf(next));
	}

	/** Makes one ready now (tests, ops). */
	public static void reset(Player player, String what) {
		Map<String, Long> next = new HashMap<>(player.getAttachedOrElse(READY_AT, Map.of()));
		if (next.remove(what) != null) {
			player.setAttached(READY_AT, next.isEmpty() ? null : Map.copyOf(next));
		}
	}
}
