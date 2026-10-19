package dev.voidmc.hearth.quest.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.quest.model.QuestInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The quest module's packets. Server to client: a board's offers (opens the board screen) and toasts. Client to server:
 * board and journal actions, which the server checks in full (the client only ever names a quest and an intent).
 * Players' own quests reach their client through the synced attachment, not through these.
 */
public final class QuestPayloads {
	private QuestPayloads() {
	}

	/** Reads an enum ordinal sent by a client without trusting it. */
	private static <E extends Enum<E>> StreamCodec<io.netty.buffer.ByteBuf, E> safeEnum(Class<E> type, E fallback) {
		E[] values = type.getEnumConstants();
		return ByteBufCodecs.VAR_INT.map(i -> i >= 0 && i < values.length ? values[i] : fallback, Enum::ordinal);
	}

	/**
	 * A settlement's board, as one player sees it.
	 *
	 * @param entries   the offers, each with why this player cannot take it yet (empty if they can)
	 * @param handIns   the player's quests that can be handed in or delivered here
	 * @param posters   wanted posters: players the law wants in this settlement
	 * @param title     the player's title here, as words ("Friend of the town")
	 */
	public record Board(long settlementId, BlockPos boardPos, String settlementName, String tier, int reputation, List<Entry> entries,
		List<UUID> handIns, List<Poster> posters, String title) implements CustomPacketPayload {
		public static final Type<Board> TYPE = new Type<>(Hearth.id("quest/board"));
		private static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("settlement").forGetter(Board::settlementId),
			BlockPos.CODEC.fieldOf("pos").forGetter(Board::boardPos),
			Codec.STRING.fieldOf("name").forGetter(Board::settlementName),
			Codec.STRING.fieldOf("tier").forGetter(Board::tier),
			Codec.INT.fieldOf("reputation").forGetter(Board::reputation),
			Entry.CODEC.listOf().fieldOf("entries").forGetter(Board::entries),
			UUIDUtil.CODEC.listOf().fieldOf("hand_ins").forGetter(Board::handIns),
			Poster.CODEC.listOf().optionalFieldOf("posters", List.of()).forGetter(Board::posters),
			Codec.STRING.optionalFieldOf("title", "").forGetter(Board::title)
		).apply(i, Board::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Board> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

		@Override
		public Type<Board> type() {
			return TYPE;
		}

		/**
		 * A wanted poster.
		 *
		 * @param owed   the fine and bounty, as words, or empty
		 * @param outlaw an outlaw of the region (attacked on sight), not merely wanted
		 * @param crime  the worst offence's translation key ({@code hearth.offence.theft}), or empty
		 * @param player the wanted player's UUID as a string (what {@link PosterAction} names to track them), or empty
		 */
		public record Poster(String name, String owed, boolean outlaw, String crime, String player) {
			public Poster(String name, String owed, boolean outlaw) {
				this(name, owed, outlaw, "", "");
			}

			public Poster(String name, String owed, boolean outlaw, String crime) {
				this(name, owed, outlaw, crime, "");
			}

			static final Codec<Poster> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Poster::name),
				Codec.STRING.optionalFieldOf("owed", "").forGetter(Poster::owed),
				Codec.BOOL.optionalFieldOf("outlaw", false).forGetter(Poster::outlaw),
				Codec.STRING.optionalFieldOf("crime", "").forGetter(Poster::crime),
				Codec.STRING.optionalFieldOf("player", "").forGetter(Poster::player)
			).apply(i, Poster::new));
		}

		/** An offer and, if this player cannot take it, why ("Needs standing 10"). */
		public record Entry(QuestInstance quest, String lock) {
			static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				QuestInstance.CODEC.fieldOf("quest").forGetter(Entry::quest),
				Codec.STRING.optionalFieldOf("lock", "").forGetter(Entry::lock)
			).apply(i, Entry::new));
		}
	}

	/** Something happened to a quest the client should celebrate or mourn with a toast. */
	public record Toast(Kind kind, String title, String detail) implements CustomPacketPayload {
		public static final Type<Toast> TYPE = new Type<>(Hearth.id("quest/toast"));
		public static final StreamCodec<FriendlyByteBuf, Toast> STREAM_CODEC = StreamCodec.composite(
			safeEnum(Kind.class, Kind.ACCEPTED), Toast::kind,
			ByteBufCodecs.stringUtf8(256), Toast::title,
			ByteBufCodecs.stringUtf8(512), Toast::detail,
			Toast::new);

		@Override
		public Type<Toast> type() {
			return TYPE;
		}

		public enum Kind {
			ACCEPTED,
			READY,
			COMPLETED,
			FAILED
		}
	}

	/** The player pressed accept or hand-in on a board. */
	public record BoardAction(long settlementId, BlockPos boardPos, Action action, Optional<UUID> quest) implements CustomPacketPayload {
		public static final Type<BoardAction> TYPE = new Type<>(Hearth.id("quest/board_action"));
		public static final StreamCodec<FriendlyByteBuf, BoardAction> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, BoardAction::settlementId,
			BlockPos.STREAM_CODEC, BoardAction::boardPos,
			safeEnum(Action.class, Action.REFRESH), BoardAction::action,
			ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BoardAction::quest,
			BoardAction::new);

		@Override
		public Type<BoardAction> type() {
			return TYPE;
		}

		public enum Action {
			ACCEPT,
			HAND_IN,
			REFRESH
		}
	}

	/** The player tracked or abandoned a quest from the journal. */
	public record JournalAction(UUID quest, Action action) implements CustomPacketPayload {
		public static final Type<JournalAction> TYPE = new Type<>(Hearth.id("quest/journal_action"));
		public static final StreamCodec<FriendlyByteBuf, JournalAction> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, JournalAction::quest,
			safeEnum(Action.class, Action.TRACK), JournalAction::action,
			JournalAction::new);

		@Override
		public Type<JournalAction> type() {
			return TYPE;
		}

		public enum Action {
			TRACK,
			ABANDON
		}
	}

	/** The player tracks a wanted poster's quarry (a player the law wants in that settlement), or stops ({@code player} empty). */
	public record PosterAction(long settlementId, String player) implements CustomPacketPayload {
		public static final Type<PosterAction> TYPE = new Type<>(Hearth.id("quest/poster_action"));
		public static final StreamCodec<FriendlyByteBuf, PosterAction> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PosterAction::settlementId,
			ByteBufCodecs.stringUtf8(64), PosterAction::player,
			PosterAction::new);

		@Override
		public Type<PosterAction> type() {
			return TYPE;
		}
	}

	/**
	 * The tracked wanted poster, as the server sees it now (sent when it changes, at most once a second), or none
	 * ({@code active} false: untracked, paid up or pardoned).
	 *
	 * @param player       the quarry's UUID as a string
	 * @param owed         the fine and bounty in bits
	 * @param crime        the worst offence's translation key, or empty
	 * @param online       whether the quarry is on the server
	 * @param located      whether {@code near} holds: online, in the tracker's dimension
	 * @param near         where they are, to the nearest {@link #GRID} blocks (a hunt, not a pinpoint)
	 */
	public record PosterTrack(boolean active, String player, String name, long owed, boolean outlaw, String crime, long settlementId, String town,
		boolean online, boolean located, BlockPos near) implements CustomPacketPayload {
		public static final int GRID = 32;
		public static final PosterTrack NONE = new PosterTrack(false, "", "", 0, false, "", 0, "", false, false, BlockPos.ZERO);
		public static final Type<PosterTrack> TYPE = new Type<>(Hearth.id("quest/poster_track"));
		public static final StreamCodec<FriendlyByteBuf, PosterTrack> STREAM_CODEC = StreamCodec.of((buf, p) -> {
			buf.writeBoolean(p.active);
			buf.writeUtf(p.player, 64);
			buf.writeUtf(p.name, 64);
			buf.writeVarLong(p.owed);
			buf.writeBoolean(p.outlaw);
			buf.writeUtf(p.crime, 128);
			buf.writeVarLong(p.settlementId);
			buf.writeUtf(p.town, 128);
			buf.writeBoolean(p.online);
			buf.writeBoolean(p.located);
			buf.writeBlockPos(p.near);
		}, buf -> new PosterTrack(buf.readBoolean(), buf.readUtf(64), buf.readUtf(64), buf.readVarLong(), buf.readBoolean(), buf.readUtf(128),
			buf.readVarLong(), buf.readUtf(128), buf.readBoolean(), buf.readBoolean(), buf.readBlockPos()));

		@Override
		public Type<PosterTrack> type() {
			return TYPE;
		}
	}
}
