package dev.voidmc.mettle.character;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Perk;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quiet tallies behind the feats ("mine_diamond_ore", "ticks_below_zero"…), kept per player and never synced. When a
 * tally reaches a feat's target, the feat is granted.
 */
public final class FeatCounters {
	public static final AttachmentType<Map<String, Long>> COUNTERS = AttachmentRegistry.create(Mettle.id("character/journal"), b -> b
		.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
		.copyOnDeath());

	private FeatCounters() {
	}

	public static void init() {
	}

	public static long get(ServerPlayer player, String counter) {
		return player.getAttachedOrElse(COUNTERS, Map.of()).getOrDefault(counter, 0L);
	}

	public static void add(ServerPlayer player, String counter, long amount) {
		Map<String, Long> next = new HashMap<>(player.getAttachedOrElse(COUNTERS, Map.of()));
		long value = next.merge(counter, amount, Long::sum);
		player.setAttached(COUNTERS, Map.copyOf(next));
		for (Holder.Reference<Perk> ref : Perks.registry(player).listElements().toList()) {
			Perk p = ref.value();
			if (p.kind() == Perk.Kind.FEAT && p.feat().isPresent() && p.feat().get().counter().equals(counter)
				&& value >= p.feat().get().target()) {
				Perks.grant(player, ref.key());
			}
		}
	}
}
