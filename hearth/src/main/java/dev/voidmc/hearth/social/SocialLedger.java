package dev.voidmc.hearth.social;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

/**
 * The social module's record of one settlement ({@code HearthDataKey hearth:social/ledger}, saved with it): the notices
 * on the board, the weddings and funerals to come, who holds which office, the market-day dressings standing in the
 * square (so they are always taken down, even after a crash), the inn's rooms and beds let to players, and how the folk
 * have fared at dice today. Mutated on the server thread; callers mark the settlement dirty.
 */
public final class SocialLedger {
	public static final Codec<SocialLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
		Notice.CODEC.listOf().optionalFieldOf("notices", List.of()).forGetter(l -> l.notices),
		FamilyEvent.CODEC.listOf().optionalFieldOf("family", List.of()).forGetter(l -> l.family),
		Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC).optionalFieldOf("offices", Map.of()).forGetter(l -> l.offices),
		UUIDUtil.STRING_CODEC.optionalFieldOf("last_lord").forGetter(l -> Optional.ofNullable(l.lastLord)),
		Dressing.CODEC.listOf().optionalFieldOf("dressings", List.of()).forGetter(l -> l.dressings),
		Rental.CODEC.listOf().optionalFieldOf("rooms", List.of()).forGetter(l -> l.rooms),
		Rental.CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(l -> l.beds),
		DiceTally.CODEC.listOf().optionalFieldOf("dice", List.of()).forGetter(l -> l.dice),
		Codec.LONG.optionalFieldOf("wares_day", Long.MIN_VALUE).forGetter(l -> l.waresDay),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("wares_sold", Map.of()).forGetter(l -> l.waresSold),
		Codec.LONG.optionalFieldOf("feast_day", Long.MIN_VALUE).forGetter(l -> l.feastDay),
		Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(l -> l.lastDay)
	).apply(i, SocialLedger::new));

	public final List<Notice> notices;
	public final List<FamilyEvent> family;
	/** Office id → resident. */
	public final Map<String, UUID> offices;
	/** The last lord, so the eldest grown child can inherit. */
	public @Nullable UUID lastLord;
	public final List<Dressing> dressings;
	public final List<Rental> rooms;
	public final List<Rental> beds;
	public final List<DiceTally> dice;
	/** The market day the visiting wares were stocked for, and lots sold since. */
	public long waresDay;
	public final Map<String, Integer> waresSold;
	/** The last day whose feasts (food eaten, stall fees) were settled, so a day is never counted twice. */
	public long feastDay;
	/**
	 * The last sim day the settlement lived through (its {@code DAY_PASSED}); ahead of the world's day only for a settlement
	 * fast-forwarded by command, whose own calendar town life then follows.
	 */
	public long lastDay;

	public SocialLedger() {
		this(List.of(), List.of(), Map.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), Long.MIN_VALUE, Map.of(), Long.MIN_VALUE, Long.MIN_VALUE);
	}

	private SocialLedger(List<Notice> notices, List<FamilyEvent> family, Map<String, UUID> offices, Optional<UUID> lastLord, List<Dressing> dressings,
		List<Rental> rooms, List<Rental> beds, List<DiceTally> dice, long waresDay, Map<String, Integer> waresSold, long feastDay, long lastDay) {
		this.notices = new ArrayList<>(notices);
		this.family = new ArrayList<>(family);
		this.offices = new HashMap<>(offices);
		this.lastLord = lastLord.orElse(null);
		this.dressings = new ArrayList<>(dressings);
		this.rooms = new ArrayList<>(rooms);
		this.beds = new ArrayList<>(beds);
		this.dice = new ArrayList<>(dice);
		this.waresDay = waresDay;
		this.waresSold = new HashMap<>(waresSold);
		this.feastDay = feastDay;
		this.lastDay = lastDay;
	}

	/** What the folk lost or won at dice against players today (bits, positive = the folk won). */
	public int diceNet(UUID resident, long day) {
		for (DiceTally t : this.dice) {
			if (t.resident().equals(resident) && t.day() == day) {
				return t.net();
			}
		}
		return 0;
	}

	public void addDiceNet(UUID resident, long day, int delta) {
		this.dice.removeIf(t -> t.day() < day - 1);
		for (int i = 0; i < this.dice.size(); i++) {
			DiceTally t = this.dice.get(i);
			if (t.resident().equals(resident) && t.day() == day) {
				this.dice.set(i, new DiceTally(resident, day, t.net() + delta));
				return;
			}
		}
		this.dice.add(new DiceTally(resident, day, delta));
	}

	/**
	 * A notice on the board.
	 *
	 * @param kind     what it is about ({@code birth}, {@code wedding}, {@code funeral}, {@code death}, {@code office},
	 *                 {@code market}, {@code festival}, {@code left})
	 * @param day      the day it was put up
	 * @param eventDay the day of the event it announces (= day for news)
	 * @param key      translation key of its text
	 * @param args     the text's arguments, as plain strings (names, days)
	 * @param cried    a crier has read it out in the square
	 */
	public record Notice(String kind, long day, long eventDay, String key, List<String> args, boolean cried) {
		public static final Codec<Notice> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Notice::kind),
			Codec.LONG.fieldOf("day").forGetter(Notice::day),
			Codec.LONG.optionalFieldOf("event_day", 0L).forGetter(Notice::eventDay),
			Codec.STRING.fieldOf("key").forGetter(Notice::key),
			Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(Notice::args),
			Codec.BOOL.optionalFieldOf("cried", false).forGetter(Notice::cried)
		).apply(i, Notice::new));

		public Notice asCried() {
			return new Notice(this.kind, this.day, this.eventDay, this.key, this.args, true);
		}
	}

	/**
	 * A wedding or a funeral to come (or today's).
	 *
	 * @param people the couple; or the one who died
	 * @param names  their names, kept because the dead are no longer residents
	 * @param family who walks with them: the couple's or the dead one's kin
	 * @param cause  for a funeral: {@code age}, {@code slain}, {@code killed}; empty for a wedding
	 */
	public record FamilyEvent(Kind kind, long day, List<UUID> people, List<String> names, List<UUID> family, String cause) {
		public static final Codec<FamilyEvent> CODEC = RecordCodecBuilder.create(i -> i.group(
			Kind.CODEC.fieldOf("kind").forGetter(FamilyEvent::kind),
			Codec.LONG.fieldOf("day").forGetter(FamilyEvent::day),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("people", List.of()).forGetter(FamilyEvent::people),
			Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(FamilyEvent::names),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("family", List.of()).forGetter(FamilyEvent::family),
			Codec.STRING.optionalFieldOf("cause", "").forGetter(FamilyEvent::cause)
		).apply(i, FamilyEvent::new));

		public enum Kind implements net.minecraft.util.StringRepresentable {
			WEDDING("wedding"),
			FUNERAL("funeral");

			public static final Codec<Kind> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Kind::values);
			private final String name;

			Kind(String name) {
				this.name = name;
			}

			@Override
			public String getSerializedName() {
				return this.name;
			}
		}
	}

	/**
	 * A block set up in the square for a market day or a festival.
	 *
	 * @param pos   where (packed)
	 * @param block the block id placed (it is taken down only if that block is still there)
	 * @param event the event it was set up for
	 * @param until the town tick it comes down
	 */
	public record Dressing(long pos, String block, String event, long until) {
		public static final Codec<Dressing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Dressing::pos),
			Codec.STRING.fieldOf("block").forGetter(Dressing::block),
			Codec.STRING.fieldOf("event").forGetter(Dressing::event),
			Codec.LONG.fieldOf("until").forGetter(Dressing::until)
		).apply(i, Dressing::new));
	}

	/**
	 * A bed or room let to a player at the inn.
	 *
	 * @param pos    the bed's head or the room's chest (packed)
	 * @param player who rents it
	 * @param until  a bed: the town tick of checkout; a room: the last day paid for
	 */
	public record Rental(long pos, UUID player, long until) {
		public static final Codec<Rental> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Rental::pos),
			UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Rental::player),
			Codec.LONG.fieldOf("until").forGetter(Rental::until)
		).apply(i, Rental::new));
	}

	public record DiceTally(UUID resident, long day, int net) {
		public static final Codec<DiceTally> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("resident").forGetter(DiceTally::resident),
			Codec.LONG.fieldOf("day").forGetter(DiceTally::day),
			Codec.INT.fieldOf("net").forGetter(DiceTally::net)
		).apply(i, DiceTally::new));
	}
}
