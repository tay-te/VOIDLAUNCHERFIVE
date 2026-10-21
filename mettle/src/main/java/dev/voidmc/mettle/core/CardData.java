package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

/**
 * One card (docs/CARDS.md §2.1): a rolled, world-unique object with an id. The world ledger
 * ({@code character.cards.CardLedger}) holds the truth for every card; a player's {@link CardsState} keeps a copy of the
 * cards they own so their client can draw the binder and the decks. Immutable: change with the {@code with*} methods.
 *
 * @param id       world-unique, never reused (a released card keeps its id)
 * @param skill    the deck it belongs to (a wildcard's own skill)
 * @param rank     roots: the rank its rarity gives (Common I, Rare II, Epic III); 1 for everything else
 * @param keywords the perk's native keyword, then a second one if rolled
 * @param traits   0–3 traits with their rolled values (before attributes)
 * @param needs    the skill level needed to slot it (§3.3)
 * @param scarSeed decides which trait a scar takes, so the preview is honest (§7.2)
 * @param attunedAt the game time from which it may be slotted, traded or listed (0 = attuned)
 * @param marks    what build feel left on it: a Temper, an Inscription, the Signatures it was (docs/BUILD_FEEL.md §2.2, §3)
 */
public record CardData(
	long id,
	ResourceKey<Skill> skill,
	ResourceKey<Perk> perk,
	Rarity rarity,
	int rank,
	List<Identifier> keywords,
	List<TraitRoll> traits,
	int needs,
	Drawn drawn,
	UUID owner,
	History history,
	int scars,
	long scarSeed,
	Place place,
	long attunedAt,
	Marks marks
) {
	/** A rolled trait: its id ({@code mettle:mining/deepborn}) and value, in the trait's unit. */
	public record TraitRoll(Identifier trait, double value) {
		public static final Codec<TraitRoll> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("trait").forGetter(TraitRoll::trait),
			Codec.DOUBLE.fieldOf("value").forGetter(TraitRoll::value)
		).apply(i, TraitRoll::new));
	}

	/** How a card came to be. */
	public enum How implements StringRepresentable {
		DRAFT, WILDCARD, MIGRATED, GIVEN, FUSED, MYTHIC, FORGED;

		public static final StringRepresentable.EnumCodec<How> CODEC = StringRepresentable.fromEnum(How::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Who drew it, at what skill level, on which in-game day and real date, and how. */
	public record Drawn(UUID player, String name, int level, long day, long millis, How how) {
		public static final Codec<Drawn> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Drawn::player),
			Codec.STRING.fieldOf("name").forGetter(Drawn::name),
			Codec.INT.fieldOf("level").forGetter(Drawn::level),
			Codec.LONG.fieldOf("day").forGetter(Drawn::day),
			Codec.LONG.fieldOf("millis").forGetter(Drawn::millis),
			How.CODEC.fieldOf("how").forGetter(Drawn::how)
		).apply(i, Drawn::new));
	}

	/** One holder in a card's history. */
	public record Holder(UUID player, String name, long day) {
		public static final Codec<Holder> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Holder::player),
			Codec.STRING.fieldOf("name").forGetter(Holder::name),
			Codec.LONG.fieldOf("day").forGetter(Holder::day)
		).apply(i, Holder::new));
	}

	/** How many players have held it, and the last five (most recent last). */
	public record History(int owners, List<Holder> last) {
		public static final int KEEP = 5;
		public static final Codec<History> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("owners").forGetter(History::owners),
			Holder.CODEC.listOf().optionalFieldOf("last", List.of()).forGetter(History::last)
		).apply(i, History::new));

		public History {
			last = List.copyOf(last);
		}

		public static History first(Holder holder) {
			return new History(1, List.of(holder));
		}

		public History passedTo(Holder holder) {
			List<Holder> next = new ArrayList<>(this.last);
			next.add(holder);
			while (next.size() > KEEP) {
				next.removeFirst();
			}
			return new History(this.owners + 1, next);
		}
	}

	/** Where a card is. */
	public enum PlaceKind implements StringRepresentable {
		BINDER, SLOTTED, TRADE, LISTED, RELEASED;

		public static final StringRepresentable.EnumCodec<PlaceKind> CODEC = StringRepresentable.fromEnum(PlaceKind::values);

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Where a card is: in the binder, slotted in a deck ({@code deck} and slot {@code index}), held by a trade session
	 * or a listing ({@code ref}), or released.
	 */
	public record Place(PlaceKind kind, Optional<ResourceKey<Skill>> deck, int index, long ref) {
		public static final Place BINDER = new Place(PlaceKind.BINDER, Optional.empty(), 0, 0);
		public static final Place RELEASED = new Place(PlaceKind.RELEASED, Optional.empty(), 0, 0);
		public static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
			PlaceKind.CODEC.fieldOf("kind").forGetter(Place::kind),
			ResourceKey.codec(MettleRegistries.SKILL).optionalFieldOf("deck").forGetter(Place::deck),
			Codec.INT.optionalFieldOf("index", 0).forGetter(Place::index),
			Codec.LONG.optionalFieldOf("ref", 0L).forGetter(Place::ref)
		).apply(i, Place::new));

		public static Place slotted(ResourceKey<Skill> deck, int index) {
			return new Place(PlaceKind.SLOTTED, Optional.of(deck), index, 0);
		}

		public boolean slotted() {
			return this.kind == PlaceKind.SLOTTED;
		}
	}

	/** A Temper on a card (§3.2): which trait rose, from and to, by whom, at what skill level, on which day. Once per card. */
	public record Tempered(Identifier trait, double before, double after, String by, int level, long day) {
		public static final Codec<Tempered> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("trait").forGetter(Tempered::trait),
			Codec.DOUBLE.optionalFieldOf("before", 0.0).forGetter(Tempered::before),
			Codec.DOUBLE.optionalFieldOf("after", 0.0).forGetter(Tempered::after),
			Codec.STRING.optionalFieldOf("by", "").forGetter(Tempered::by),
			Codec.INT.optionalFieldOf("level", 0).forGetter(Tempered::level),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Tempered::day)
		).apply(i, Tempered::new));
	}

	/** An Inscription (§3.3): the keyword set as the card's second, the one it replaced, by whom, when. Once per card. */
	public record Inscribed(Identifier keyword, Optional<Identifier> was, String by, int level, long day) {
		public static final Codec<Inscribed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("keyword").forGetter(Inscribed::keyword),
			Identifier.CODEC.optionalFieldOf("was").forGetter(Inscribed::was),
			Codec.STRING.optionalFieldOf("by", "").forGetter(Inscribed::by),
			Codec.INT.optionalFieldOf("level", 0).forGetter(Inscribed::level),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Inscribed::day)
		).apply(i, Inscribed::new));
	}

	/** A stretch as someone's Signature (§2.2): "Signature of Ashford, day 41-90"; {@code to} is -1 while it still is. */
	public record Signed(String by, long from, long to) {
		public static final Codec<Signed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("by", "").forGetter(Signed::by),
			Codec.LONG.optionalFieldOf("from", 0L).forGetter(Signed::from),
			Codec.LONG.optionalFieldOf("to", -1L).forGetter(Signed::to)
		).apply(i, Signed::new));
	}

	/**
	 * What build feel left on a card. Kept through trades and scars; fusion makes a new card without them. Every part is
	 * optional and read leniently, so an old card (no marks) and a damaged entry both load.
	 */
	public record Marks(Optional<Tempered> tempered, Optional<Inscribed> inscribed, List<Signed> signed) {
		public static final int KEEP_SIGNED = 5;
		public static final Marks NONE = new Marks(Optional.empty(), Optional.empty(), List.of());
		public static final Codec<Marks> CODEC = RecordCodecBuilder.create(i -> i.group(
			Tempered.CODEC.optionalFieldOf("tempered").forGetter(Marks::tempered),
			Inscribed.CODEC.optionalFieldOf("inscribed").forGetter(Marks::inscribed),
			MettleCodecs.lenientList(Signed.CODEC, "signature line").optionalFieldOf("signed", List.of()).forGetter(Marks::signed)
		).apply(i, Marks::new));

		public Marks {
			signed = signed.size() > KEEP_SIGNED ? List.copyOf(signed.subList(signed.size() - KEEP_SIGNED, signed.size())) : List.copyOf(signed);
		}

		public Marks withTempered(Tempered t) {
			return new Marks(Optional.of(t), this.inscribed, this.signed);
		}

		public Marks withInscribed(Inscribed in) {
			return new Marks(this.tempered, Optional.of(in), this.signed);
		}

		/** A Signature started (to -1) or ended (the open line closed on {@code day}). */
		public Marks signedFrom(String by, long day) {
			List<Signed> next = new ArrayList<>(this.signed);
			next.add(new Signed(by, day, -1));
			return new Marks(this.tempered, this.inscribed, next);
		}

		public Marks signedUntil(long day) {
			List<Signed> next = new ArrayList<>();
			for (Signed s : this.signed) {
				next.add(s.to() < 0 ? new Signed(s.by(), s.from(), day) : s);
			}
			return new Marks(this.tempered, this.inscribed, next);
		}
	}

	public static final Codec<CardData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("id").forGetter(CardData::id),
		ResourceKey.codec(MettleRegistries.SKILL).fieldOf("skill").forGetter(CardData::skill),
		ResourceKey.codec(MettleRegistries.PERK).fieldOf("perk").forGetter(CardData::perk),
		Rarity.CODEC.fieldOf("rarity").forGetter(CardData::rarity),
		Codec.INT.optionalFieldOf("rank", 1).forGetter(CardData::rank),
		Identifier.CODEC.listOf().optionalFieldOf("keywords", List.of()).forGetter(CardData::keywords),
		TraitRoll.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(CardData::traits),
		Codec.INT.optionalFieldOf("needs", 0).forGetter(CardData::needs),
		Drawn.CODEC.fieldOf("drawn").forGetter(CardData::drawn),
		UUIDUtil.CODEC.fieldOf("owner").forGetter(CardData::owner),
		History.CODEC.fieldOf("history").forGetter(CardData::history),
		Codec.INT.optionalFieldOf("scars", 0).forGetter(CardData::scars),
		Codec.LONG.optionalFieldOf("scar_seed", 0L).forGetter(CardData::scarSeed),
		Place.CODEC.optionalFieldOf("place", Place.BINDER).forGetter(CardData::place),
		Codec.LONG.optionalFieldOf("attuned_at", 0L).forGetter(CardData::attunedAt),
		Marks.CODEC.optionalFieldOf("marks", Marks.NONE).forGetter(CardData::marks)
	).apply(i, CardData::new));

	public CardData {
		keywords = List.copyOf(keywords);
		traits = List.copyOf(traits);
		marks = marks == null ? Marks.NONE : marks;
	}

	/** A card without marks (every card before build feel, and every new one). */
	public CardData(long id, ResourceKey<Skill> skill, ResourceKey<Perk> perk, Rarity rarity, int rank, List<Identifier> keywords, List<TraitRoll> traits,
		int needs, Drawn drawn, UUID owner, History history, int scars, long scarSeed, Place place, long attunedAt) {
		this(id, skill, perk, rarity, rank, keywords, traits, needs, drawn, owner, history, scars, scarSeed, place, attunedAt, Marks.NONE);
	}

	public boolean tempered() {
		return this.marks.tempered().isPresent();
	}

	public boolean inscribed() {
		return this.marks.inscribed().isPresent();
	}

	public CardData withMarks(Marks m) {
		return new CardData(this.id, this.skill, this.perk, this.rarity, this.rank, this.keywords, this.traits, this.needs, this.drawn, this.owner,
			this.history, this.scars, this.scarSeed, this.place, this.attunedAt, m);
	}

	/** The card after a Temper or Inscription: its traits and keywords as given, the marks as given. */
	public CardData withBuild(List<Identifier> k, List<TraitRoll> t, Marks m) {
		return new CardData(this.id, this.skill, this.perk, this.rarity, this.rank, k, t, this.needs, this.drawn, this.owner,
			this.history, this.scars, this.scarSeed, this.place, this.attunedAt, m);
	}

	public CardData withPlace(Place p) {
		return new CardData(this.id, this.skill, this.perk, this.rarity, this.rank, this.keywords, this.traits, this.needs, this.drawn, this.owner,
			this.history, this.scars, this.scarSeed, p, this.attunedAt, this.marks);
	}

	public CardData withTraits(List<TraitRoll> t, int scarCount) {
		return new CardData(this.id, this.skill, this.perk, this.rarity, this.rank, this.keywords, t, this.needs, this.drawn, this.owner,
			this.history, scarCount, this.scarSeed, this.place, this.attunedAt, this.marks);
	}

	public CardData withOwner(UUID newOwner, History h, long attuned) {
		return new CardData(this.id, this.skill, this.perk, this.rarity, this.rank, this.keywords, this.traits, this.needs, this.drawn, newOwner,
			h, this.scars, this.scarSeed, this.place, attuned, this.marks);
	}

	/** Whether it may be slotted, traded or listed at {@code gameTime}. */
	public boolean attuned(long gameTime) {
		return gameTime >= this.attunedAt;
	}

	public boolean slotted() {
		return this.place.slotted();
	}

	/** A Mythic card (§9): its perk key is {@code mettle:mythic/<id>}, its rarity MYTHIC. */
	public boolean mythic() {
		return this.rarity == Rarity.MYTHIC;
	}

	/** The Mythic's id ({@code mettle:twin_edge}), or empty for an ordinary card. */
	public Optional<Identifier> mythicId() {
		return mythic() ? Optional.of(Identifier.fromNamespaceAndPath(this.perk.identifier().getNamespace(),
			this.perk.identifier().getPath().substring(this.perk.identifier().getPath().indexOf('/') + 1))) : Optional.empty();
	}
}
