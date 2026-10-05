package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hunter's Mark: a mob you hit is outlined for you alone for 6 s. Vanilla's glowing is one flag every player sees, so
 * the server tells only the shooter (a {@link Mark} packet) and the client draws the outline for it alone.
 */
public final class HuntersMark {
	/** Server → client: outline this entity for {@code ticks}. */
	public record Mark(int entity, int ticks) implements CustomPacketPayload {
		public static final Type<Mark> TYPE = new Type<>(Mettle.id("marksmanship/mark"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Mark::entity,
			ByteBufCodecs.VAR_INT, Mark::ticks,
			Mark::new);

		@Override
		public Type<Mark> type() {
			return TYPE;
		}
	}

	/** Per shooter: entity id → game time the mark fades. Only for "is this a new mark?" (the chip) and tests. */
	private static final Map<UUID, Map<Integer, Long>> MARKED = new HashMap<>();

	private HuntersMark() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Mark.TYPE, Mark.STREAM_CODEC);
	}

	public static void mark(ServerPlayer player, LivingEntity target) {
		long now = player.level().getGameTime();
		Map<Integer, Long> marks = MARKED.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		marks.values().removeIf(until -> until < now);
		Long until = marks.put(target.getId(), now + MarksmanshipRules.HUNTERS_MARK_TICKS);
		if (ServerPlayNetworking.canSend(player, Mark.TYPE)) {
			ServerPlayNetworking.send(player, new Mark(target.getId(), MarksmanshipRules.HUNTERS_MARK_TICKS));
		}
		if (until == null) {
			MarksmanshipSkill.chip(player, MarksmanshipSkill.HUNTERS_MARK, MarksmanshipSkill.text(MarksmanshipSkill.HUNTERS_MARK, target.getDisplayName()));
		}
	}

	/** Whether {@code target} is currently marked for {@code player} (server view). */
	public static boolean marked(ServerPlayer player, LivingEntity target) {
		Long until = MARKED.getOrDefault(player.getUUID(), Map.of()).get(target.getId());
		return until != null && until >= player.level().getGameTime();
	}

	public static void forget(UUID player) {
		MARKED.remove(player);
	}
}
