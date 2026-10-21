package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The town events' packets for the living-world HUD lines (docs/LIVING_WORLD_2.md §10 F6, F7; docs/OVERLAY.md
 * {@code contest}, {@code arrival}; 2026-10-21). Both carry JSON the server builds (text in the server's language); the
 * client draws them natively under the quest HUD and the arrival banner, and passes them to hosts as state.
 */
public final class EventPayloads {
	private EventPayloads() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ContestLine.TYPE, ContestLine.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ArrivalEvents.TYPE, ArrivalEvents.CODEC);
	}

	/**
	 * The contest line under the quest HUD (F6): the archery contest or the melee the player entered, while they are at the
	 * range or the ring, and once more with the result. {@code json} empty: nothing to show (the line folds away).
	 */
	public record ContestLine(String json) implements CustomPacketPayload {
		public static final ContestLine NONE = new ContestLine("");
		public static final Type<ContestLine> TYPE = new Type<>(Hearth.id("events/contest_line"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ContestLine> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.json, 1 << 14),
			buf -> new ContestLine(buf.readUtf(1 << 14)));

		@Override
		public Type<ContestLine> type() {
			return TYPE;
		}
	}

	/**
	 * Today's events in the town the player just walked into (F7: the line under the arrival banner), at most two, a kind
	 * once: {@code json} is {@code [{kind, text, when, tone, venue, start, begun}]}. Sent once a town a day.
	 */
	public record ArrivalEvents(long settlementId, String json) implements CustomPacketPayload {
		public static final Type<ArrivalEvents> TYPE = new Type<>(Hearth.id("events/arrival_events"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ArrivalEvents> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarLong(p.settlementId);
			buf.writeUtf(p.json, 1 << 13);
		}, buf -> new ArrivalEvents(buf.readVarLong(), buf.readUtf(1 << 13)));

		@Override
		public Type<ArrivalEvents> type() {
			return TYPE;
		}
	}
}
