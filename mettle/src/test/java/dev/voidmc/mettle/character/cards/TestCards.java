package dev.voidmc.mettle.character.cards;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.CardSkill;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.DraftState.Card;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;

/** The shipped card data (traits, keywords, card skills, perks) read from the resources, and a plain character sheet. */
final class TestCards {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static final Path DATA = Path.of("src/main/resources/data/mettle/mettle");
	/** The skills with drawn Mythics (three each, docs/BUILD_FEEL.md §5): Stealth has none of its own yet. */
	static final String[] MYTHIC_SKILLS = {"mining", "blades", "heavy_weapons", "marksmanship", "defense", "athletics", "survival", "smithing"};
	static final String[] RELEASED = {"mining", "blades", "heavy_weapons", "marksmanship", "defense", "athletics", "survival", "smithing", "stealth", "speech"};
	static final UUID PLAYER = new UUID(1, 2);

	static final Map<ResourceKey<Perk>, Perk> PERKS = load("perk", Perk.CODEC);
	static final Map<Identifier, Trait> TRAITS = loadIds("trait", Trait.CODEC);
	static final Map<Identifier, Keyword> KEYWORDS = loadIds("keyword", Keyword.CODEC);
	static final Map<Identifier, CardSkill> SKILLS = loadIds("card_skill", CardSkill.CODEC);
	static final DraftRules.Deck DECK = new DraftRules.Deck(PERKS);
	/** The talents (docs/BUILD_FEEL.md §13), by id ({@code mettle:fleet}). */
	static final Map<Identifier, dev.voidmc.mettle.api.Talent> TALENTS = loadIds("talent", dev.voidmc.mettle.api.Talent.CODEC);
	static final CardRules.Data DATA_SET = data();

	private TestCards() {
	}

	private static <T> Map<Identifier, T> loadIds(String dir, Codec<T> codec) {
		Map<Identifier, T> out = new HashMap<>();
		Path root = DATA.resolve(dir);
		try (Stream<Path> files = Files.walk(root)) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				String rel = root.relativize(p).toString().replace('\\', '/').replace(".json", "");
				out.put(Identifier.fromNamespaceAndPath("mettle", rel), codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(p))).getOrThrow());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	private static Map<ResourceKey<Perk>, Perk> load(String dir, Codec<Perk> codec) {
		Map<ResourceKey<Perk>, Perk> out = new HashMap<>();
		loadIds(dir, codec).forEach((id, p) -> out.put(ResourceKey.create(MettleRegistries.PERK, id), p));
		return out;
	}

	private static CardRules.Data data() {
		Map<ResourceKey<Skill>, List<CardRules.TraitEntry>> pools = new HashMap<>();
		TRAITS.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
			.forEach(e -> pools.computeIfAbsent(e.getValue().skill(), s -> new ArrayList<>()).add(new CardRules.TraitEntry(e.getKey(), e.getValue())));
		Map<ResourceKey<Perk>, Identifier> natives = new HashMap<>();
		SKILLS.values().forEach(s -> natives.putAll(s.natives()));
		return new CardRules.Data() {
			@Override
			public List<CardRules.TraitEntry> traits(ResourceKey<Skill> skill) {
				return pools.getOrDefault(skill, List.of());
			}

			@Override
			public Optional<Trait> trait(Identifier id) {
				return Optional.ofNullable(TRAITS.get(id));
			}

			@Override
			public Optional<Identifier> nativeKeyword(ResourceKey<Perk> perk) {
				return Optional.ofNullable(natives.get(perk));
			}

			@Override
			public List<Identifier> pool(ResourceKey<Skill> skill) {
				CardSkill s = SKILLS.get(skill.identifier());
				return s == null ? List.of() : s.pool();
			}

			@Override
			public String prefix(ResourceKey<Skill> skill) {
				return SKILLS.get(skill.identifier()).prefix();
			}

			@Override
			public double momentum(Identifier keyword) {
				Keyword k = KEYWORDS.get(keyword);
				return k == null ? 0 : k.momentum();
			}
		};
	}

	static ResourceKey<Perk> perk(String path) {
		return ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath("mettle", path));
	}

	static ResourceKey<Skill> skill(String path) {
		return ResourceKey.create(MettleRegistries.SKILL, Identifier.fromNamespaceAndPath("mettle", path));
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mettle", path);
	}

	static Perk def(ResourceKey<Perk> key) {
		return PERKS.get(key);
	}

	/** A character as plain maps: skill levels, one attribute value for all, cards owned per perk. */
	static final class Sheet implements CardRules.Sheet {
		final Map<ResourceKey<Skill>, Integer> levels = new HashMap<>();
		final Map<ResourceKey<Perk>, Integer> owned = new HashMap<>();
		int attribute = 20;

		Sheet level(String skill, int level) {
			this.levels.put(skill(skill), level);
			return this;
		}

		Sheet own(String perk, int n) {
			this.owned.put(perk(perk), n);
			return this;
		}

		@Override
		public int level(ResourceKey<Skill> skill) {
			return this.levels.getOrDefault(skill, 0);
		}

		@Override
		public int attribute(ResourceKey<Skill> skill) {
			return this.attribute;
		}

		@Override
		public int owned(ResourceKey<Perk> perk) {
			return this.owned.getOrDefault(perk, 0);
		}
	}

	private static long next = 1;

	/** A card record from an offer, in the binder (or slotted with {@code index} ≥ 0). */
	static CardData card(Card offer, int index) {
		Perk p = def(offer.perk());
		CardData.Place place = index >= 0 ? CardData.Place.slotted(p.skill(), index) : CardData.Place.BINDER;
		CardData.Holder h = new CardData.Holder(PLAYER, "Tester", 0);
		return new CardData(next++, p.skill(), offer.perk(), offer.rarity(), offer.rank(), offer.keywords(), offer.traits(), offer.needs(),
			new CardData.Drawn(PLAYER, "Tester", 0, 0, 0, CardData.How.GIVEN), PLAYER, CardData.History.first(h), 0, 7L, place, 0L);
	}

	/** A plain card of a perk: its native keyword (plus {@code extra}), no traits, the requirement of its rarity. */
	static CardData plain(String perk, Rarity rarity, int index, String... extra) {
		ResourceKey<Perk> key = perk(perk);
		Perk p = def(key);
		List<Identifier> keywords = new ArrayList<>();
		keywords.add(p.kind() == Perk.Kind.CURSED ? CardRules.HEX : DATA_SET.nativeKeyword(key).orElseThrow());
		for (String k : extra) {
			keywords.add(id(k));
		}
		return card(new Card(key, CardRules.rank(p, rarity), rarity, false, keywords, List.of(), CardRules.needs(p, rarity)), index);
	}

	static CardData withTraits(CardData c, CardData.TraitRoll... traits) {
		return new CardData(c.id(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), List.of(traits), c.needs(), c.drawn(), c.owner(),
			c.history(), c.scars(), c.scarSeed(), c.place(), c.attunedAt());
	}
}
