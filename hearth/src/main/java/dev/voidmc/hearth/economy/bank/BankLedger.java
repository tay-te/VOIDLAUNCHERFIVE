package dev.voidmc.hearth.economy.bank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * A player's ledger at the bank counters (docs/OVERLAY.md, {@code bank.ledger}): the last {@link #MAX} things they did
 * there that moved coin, newest first, at every bank and town hall alike. Kept with the player (an attachment that
 * survives death), written by {@link Bank#handle} from what the action changed.
 */
public final class BankLedger {
	/** Lines kept per player. */
	public static final int MAX = 24;

	public static final String DEPOSIT = "deposit";
	public static final String WITHDRAW = "withdraw";
	public static final String SELL_EMERALDS = "sell_emeralds";
	public static final String BUY_EMERALDS = "buy_emeralds";
	public static final String BUY_PLOT = "buy_plot";
	public static final String HIRE = "hire";
	public static final String FUND = "fund";
	public static final String DELIVER = "deliver";

	/**
	 * One line.
	 *
	 * @param day     the game day it happened
	 * @param kind    {@link #DEPOSIT}, {@link #WITHDRAW}, {@link #SELL_EMERALDS}, {@link #BUY_EMERALDS}, {@link #BUY_PLOT},
	 *                {@link #HIRE}, {@link #FUND} or {@link #DELIVER}
	 * @param purse   bits into (positive) or out of (negative) the coin the player carries
	 * @param deposit bits onto (positive) or off (negative) their deposit
	 * @param town    the settlement's name where it happened
	 * @param subject what it was about: the building's archetype path (a plot, a business, public works), the goods
	 *                (a contract), the emeralds' count, or empty
	 */
	public record Line(long day, String kind, long purse, long deposit, String town, String subject) {
		static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Line::day),
			Codec.STRING.fieldOf("kind").forGetter(Line::kind),
			Codec.LONG.optionalFieldOf("purse", 0L).forGetter(Line::purse),
			Codec.LONG.optionalFieldOf("deposit", 0L).forGetter(Line::deposit),
			Codec.STRING.optionalFieldOf("town", "").forGetter(Line::town),
			Codec.STRING.optionalFieldOf("subject", "").forGetter(Line::subject)
		).apply(i, Line::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.of((buf, l) -> {
			ByteBufCodecs.VAR_LONG.encode(buf, l.day);
			ByteBufCodecs.STRING_UTF8.encode(buf, l.kind);
			buf.writeLong(l.purse);
			buf.writeLong(l.deposit);
			ByteBufCodecs.STRING_UTF8.encode(buf, l.town);
			ByteBufCodecs.STRING_UTF8.encode(buf, l.subject);
		}, buf -> new Line(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong(), buf.readLong(),
			ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));
	}

	static final Codec<List<Line>> LINES = Line.CODEC.listOf();
	/** The attachment, in a holder so the pure parts load without Fabric's registry (tests). */
	private static final class Store {
		static final AttachmentType<List<Line>> TYPE = AttachmentRegistry.create(Hearth.id("economy/bank_ledger"), b -> b
			.persistent(LINES)
			.copyOnDeath());
	}

	private BankLedger() {
	}

	/** The player's lines, newest first. */
	public static List<Line> lines(Player player) {
		List<Line> l = player.getAttached(Store.TYPE);
		return l == null ? List.of() : l;
	}

	/** Adds a line at the top (nothing when it moved no coin). */
	public static void add(Player player, Line line) {
		if (line.purse() == 0 && line.deposit() == 0) {
			return;
		}
		player.setAttached(Store.TYPE, push(lines(player), line));
	}

	/** The lines with this one on top, the oldest past {@link #MAX} dropped. Pure. */
	static List<Line> push(List<Line> lines, Line line) {
		List<Line> next = new ArrayList<>(Math.min(MAX, lines.size() + 1));
		next.add(line);
		for (int i = 0; i < lines.size() && next.size() < MAX; i++) {
			next.add(lines.get(i));
		}
		return List.copyOf(next);
	}

	/** The kind of line an economy action makes, or null for one that never moves coin. */
	static String kindOf(int action) {
		return switch (action) {
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.DEPOSIT -> DEPOSIT;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.WITHDRAW -> WITHDRAW;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.SELL_EMERALDS -> SELL_EMERALDS;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.BUY_EMERALDS -> BUY_EMERALDS;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.BUY_PLOT -> BUY_PLOT;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.HIRE -> HIRE;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.FUND -> FUND;
			case dev.voidmc.hearth.economy.net.EconomyActionPayload.DELIVER -> DELIVER;
			default -> null;
		};
	}

	/** Forces the class (and so the attachment) to register during init. */
	public static void init() {
		Object type = Store.TYPE;
	}
}
