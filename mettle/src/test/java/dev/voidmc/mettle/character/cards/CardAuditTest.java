package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.Mythic;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.Cursed;
import dev.voidmc.mettle.character.skill.stealth.StealthRules;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.DraftState.Rarity;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Production audit of the card effects (2026-10-12, branch audit-gameplay): every trait, set tier and Mythic is wired in
 * code, its text renders its number, its numbers match the doc's caps, and the whole-character ceilings of
 * docs/CARDS.md §14 hold for the worst legal build. Written as findings (2026-10-12) and flipped once fixed
 * (branch mettle-fix-effects): each check now asserts the correct behaviour, and its message says what broke.
 */
class CardAuditTest {
	private static final Map<Identifier, Mythic> MYTHICS = TestCardsMythics.load();
	private static final String SOURCES = sources();
	private static final Map<String, String> LANG = lang();
	private static final Set<String> CONDITIONS = Set.of("always", "holding_blade", "holding_heavy", "sprinting", "on_path", "bloodied_blade");

	private static String sources() {
		StringBuilder out = new StringBuilder();
		for (String root : new String[] {"src/main/java", "src/client/java"}) {
			try (Stream<Path> files = Files.walk(Path.of(root))) {
				for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
					out.append(Files.readString(p)).append('\n');
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return out.toString();
	}

	private static Map<String, String> lang() {
		Map<String, String> out = new HashMap<>();
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/assets/mettle/lang/en_us"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	/** The shipped en_us lines as the game's language, so components render as players read them. */
	private static void injectLang() {
		Language.inject(new Language() {
			@Override
			public String getOrDefault(String key, String fallback) {
				return LANG.getOrDefault(key, fallback);
			}

			@Override
			public boolean has(String key) {
				return LANG.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return FormattedCharSequence.EMPTY;
			}
		});
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
	}

	private static Executable text(String what, Component c, boolean needsValue) {
		return () -> {
			String k = key(c);
			if (k != null) {
				assertTrue(LANG.containsKey(k), what + ": missing lang key " + k);
				assertTrue(!LANG.get(k).isBlank(), what + ": empty lang line " + k);
				if (needsValue) {
					assertTrue(LANG.get(k).contains("%s"), what + ": its sentence has no %s for the value: " + LANG.get(k));
				}
			} else {
				assertTrue(!c.getString().isBlank(), what + ": empty text");
			}
		};
	}

	// ---------------------------------------------------------------- (1) every trait does something, as its text says

	@Test
	void everyTraitIsWiredAndSized() {
		List<Executable> checks = new ArrayList<>();
		assertEquals(148, TestCards.TRAITS.size(), "CARDS.md §4.2 lists 120 traits, BUILD_FEEL.md §14.1 Stealth's 14 and §14.2 Speech's 14");
		TestCards.TRAITS.forEach((id, t) -> {
			String path = id.getPath();
			checks.add(text(path + " name", t.name(), false));
			checks.add(text(path + " description", t.description(), true));
			Trait.Effect e = t.effect();
			if (e.attribute().isPresent()) {
				// Mettle's own attributes (carry capacity) register at mod init, not in the vanilla bootstrap.
				checks.add(() -> assertTrue(e.attribute().get().getNamespace().equals("mettle") || BuiltInRegistries.ATTRIBUTE.get(e.attribute().get()).isPresent(),
					path + ": unknown attribute " + e.attribute().get()));
				checks.add(() -> assertTrue(CONDITIONS.contains(e.when()), path + ": CardEffects.holds has no condition '" + e.when() + "'"));
				// A percent trait sizes its modifier as a fraction; a flat one in its own unit (hearts are two health).
				double want = t.percent() ? 0.01 : e.attribute().get().getPath().equals("max_health") ? 2.0 : 1.0;
				checks.add(() -> assertEquals(want, Math.abs(e.scale()), 1e-9, path + ": modifier scale doesn't match its printed unit"));
			} else {
				checks.add(() -> assertTrue(SOURCES.contains("\"" + path + "\""), path + ": behaviour trait never read in code (no \"" + path + "\" in src/main)"));
			}
			// §4.1: broad ≤ 12 % rolled, narrow ≤ 25 %; a card's cap is max × 1.25, one trait's total twice that.
			// Speech's Heroic (Hero of the Village's length) and Coinwise (coins' weight) are no power: BUILD_FEEL.md §14.2
			// gives them 25–50 % and 20–40 %.
			boolean exempt = path.equals("speech/heroic") || path.equals("speech/coinwise");
			if (t.percent() && !exempt) {
				double limit = t.scope() == Trait.Scope.BROAD ? 12 : 25;
				checks.add(() -> assertTrue(t.max() <= limit, path + ": max " + t.max() + " over the " + t.scope() + " limit " + limit));
			}
			checks.add(() -> assertEquals(CardRules.round(t.max() * 1.25), CardRules.cardCap(t), 1e-9, path + ": card cap"));
			// Twice the card cap before rounding (a 1.875 card cap prints 1.88, but two of them total 3.75, as the doc says).
			checks.add(() -> assertEquals(CardRules.round(t.max() * 1.25 * 2), CardRules.totalCap(t), 1e-9, path + ": total cap"));
			// The value the tooltip prints for a viewer at 20 is the value the server applies (CardRules.effective both sides).
			CardRules.Attributes twenty = a -> 20;
			// Whole-level traits (Speech's Persuasive and Shrewd) are never boosted.
			double boosted = t.boosted() ? CardRules.round(t.max() * 1.255) : t.max();
			checks.add(() -> assertEquals(Math.min(CardRules.cardCap(t), boosted), CardRules.effective(t, t.max(), twenty), 1e-9,
				path + ": boosted value"));
		});
		assertAll(checks);
	}

	/** A stray '%' next to a "%s" makes Minecraft fall back to the raw template: the player reads "%s" and no number. */
	@Test
	void everyTraitSentenceShowsItsValue() {
		injectLang();
		List<Executable> checks = new ArrayList<>();
		TestCards.TRAITS.forEach((id, t) -> checks.add(() -> {
			String shown = CardText.sentence(t, t.max()).getString();
			assertTrue(shown.contains(t.format(t.max())) && !shown.contains("%s"), id.getPath() + " renders as: " + shown);
		}));
		assertAll(checks);
	}

	@Test
	void cardTraitTotalsStackAndCap() {
		// Three Epic Deepborn copies at 12 % with Strength 20: 15 % each after boost and card cap, 30 % in all (twice 15).
		Identifier deepborn = TestCards.id("mining/deepborn");
		List<CardData> cards = List.of(
			TestCards.withTraits(TestCards.plain("mining/quarryman", Rarity.EPIC, 0), new TraitRoll(deepborn, 12)),
			TestCards.withTraits(TestCards.plain("mining/tunneler", Rarity.EPIC, 1), new TraitRoll(deepborn, 12)),
			TestCards.withTraits(TestCards.plain("mining/prospector", Rarity.EPIC, 2), new TraitRoll(deepborn, 12)),
			TestCards.withTraits(TestCards.plain("mining/rock_hauler", Rarity.EPIC, -1), new TraitRoll(deepborn, 12)));
		CardRules.Derived d = CardRules.derive(cards, s -> 20, 3, a -> 20, k -> java.util.Optional.ofNullable(TestCards.PERKS.get(k)), TestCards.DATA_SET,
			List.of(), List.of());
		assertEquals(30.0, d.traits().get(deepborn), 1e-9, "three slotted copies cap at twice the card cap; the binder copy counts for nothing");
	}

	// ---------------------------------------------------------------- (1) sets and Mythics

	@Test
	void everySetTierIsWired() {
		List<Executable> checks = new ArrayList<>();
		assertEquals(14, TestCards.KEYWORDS.size());
		TestCards.KEYWORDS.forEach((id, k) -> {
			checks.add(text(id.getPath() + " name", k.name(), false));
			for (int tier : new int[] {2, 4, 6}) {
				Keyword.SetBonus b = k.sets().get(tier);
				String what = id.getPath() + " " + tier;
				checks.add(() -> assertTrue(b != null, what + ": no bonus"));
				if (b == null) {
					continue;
				}
				checks.add(text(what, b.description(), false));
				for (Keyword.Modifier m : b.effects()) {
					checks.add(() -> assertTrue(BuiltInRegistries.ATTRIBUTE.get(m.attribute()).isPresent(), what + ": unknown attribute " + m.attribute()));
					checks.add(() -> assertTrue(CONDITIONS.contains(m.when()), what + ": unknown condition " + m.when()));
				}
				if (b.behaviour().isPresent()) {
					String kw = id.getPath();
					Pattern p = Pattern.compile("set\\([^;]{0,40}\"" + kw + "\"\\)\\s*(>=|<)\\s*" + tier);
					// Echo reads its tier into a local (cooldownScale); Hex 2 / 4 live in EffectsApplier.hexed.
					boolean echo = kw.equals("echo") && SOURCES.contains("echo >= " + tier);
					boolean hex = kw.equals("hex") && (tier == 2 ? SOURCES.contains("hex >= 2") : tier == 4 && SOURCES.contains("hexTier >= 4"));
					checks.add(() -> assertTrue(p.matcher(SOURCES).find() || echo || hex, what + ": behaviour " + b.behaviour().get() + " never checked in code"));
				}
			}
		});
		assertAll(checks);
	}

	/**
	 * Hex 2 / 4 promise to soften every cursed card's downside and strengthen its upside: the coded halves in Cursed.java
	 * (Heavy Draw, Snap Shot, Brittle Edge, Slapdash, Greedy Pick's crumble, Wolf's Hunger's drain) as well as the
	 * attribute ones (EffectsApplier.hexed).
	 */
	@Test
	void hexReachesTheCodedCursedCards() {
		List<String> coded = new ArrayList<>();
		TestCards.PERKS.forEach((k, p) -> {
			if (p.kind() == Perk.Kind.CURSED && p.effects().isEmpty()) {
				coded.add(k.identifier().getPath());
			}
		});
		assertEquals(Set.of("marksmanship/heavy_draw", "marksmanship/snap_shot", "smithing/brittle_edge", "smithing/slapdash", "stealth/cowards_edge",
			"speech/honeyed_lies", "speech/demagogue"),
			Set.copyOf(coded),
			"the cursed cards with no attribute effect at all");
		assertAll(
			// Heavy Draw: slower draw (down), harder arrows (up).
			() -> assertEquals(1 - 0.25 * 0.75, Cursed.drawSpeed(true, false, 2), 1e-6, "Heavy Draw's slower draw at Hex 2"),
			() -> assertEquals(1 + 0.35 * 1.15, Cursed.arrowDamage(true, false, 4), 1e-6, "Heavy Draw's harder arrows at Hex 4"),
			() -> assertEquals(Cursed.HEAVY_DRAW_DAMAGE, Cursed.arrowDamage(true, false, 2), 1e-6, "Hex 2 leaves Heavy Draw's upside"),
			// Snap Shot: faster draw (up), weaker arrows (down).
			() -> assertEquals(1 + 1.0 * 1.15, Cursed.drawSpeed(false, true, 4), 1e-6, "Snap Shot's quicker draw at Hex 4"),
			() -> assertEquals(1 - 0.3 * 0.75, Cursed.arrowDamage(false, true, 2), 1e-6, "Snap Shot's weaker arrows at Hex 2"),
			// Brittle Edge: +2 damage (up), half the durability (down).
			() -> assertEquals(2.0 * 1.15, Cursed.brittleEdgeDamage(4), 1e-9, "Brittle Edge's damage at Hex 4"),
			() -> assertEquals(-0.5 * 0.75, Cursed.brittleEdgeDurability(2), 1e-9, "Brittle Edge's lost durability at Hex 2"),
			// Slapdash: XP ×1.6 (up), a quarter less durable (down).
			() -> assertEquals(1 + 0.6 * 1.15, Cursed.slapdashXp(4), 1e-9, "Slapdash's XP at Hex 4"),
			() -> assertEquals(-0.25 * 0.75, Cursed.slapdashDurability(2), 1e-9, "Slapdash's lost durability at Hex 2"),
			// Greedy Pick's crumble and Wolf's Hunger's drain are downsides.
			() -> assertEquals(0.125 * 0.75, Cursed.crumbleChance(2), 1e-6, "Greedy Pick's crumble at Hex 2"),
			() -> assertEquals(1 + 0.4 * 0.75, Cursed.wolfsHunger(2), 1e-6, "Wolf's Hunger's drain at Hex 2"),
			// Coward's Edge (coded in Stealth): +50 % sneak attacks (up), 15 % more taken in the open (down); Skulker's bright-light cost (down).
			() -> assertEquals(0.5 * 1.15, Cursed.up(1 + StealthRules.COWARD, 4) - 1, 1e-9, "Coward's Edge's sneak attacks at Hex 4"),
			() -> assertEquals(1 + 0.15 * 0.75, Cursed.down(1 + StealthRules.COWARD_TAKEN, 2), 1e-9, "Coward's Edge's cost at Hex 2"),
			() -> assertEquals(1 + 0.25 * 0.75, Cursed.down(StealthRules.SKULKER_COST, 2), 1e-9, "Skulker's cost at Hex 2"),
			// Without Hex nothing changes.
			() -> assertEquals(Cursed.HEAVY_DRAW_SPEED, Cursed.drawSpeed(true, false, 0), 1e-6),
			() -> assertEquals(Cursed.GREEDY_PICK_CRUMBLE, Cursed.crumbleChance(0), 1e-6),
			() -> assertEquals(Cursed.WOLFS_HUNGER_DRAIN, Cursed.wolfsHunger(0), 1e-6));
	}

	@Test
	void everyMythicIsWiredAndWorded() {
		List<Executable> checks = new ArrayList<>();
		assertEquals(36, MYTHICS.size());
		MYTHICS.forEach((id, m) -> {
			String path = id.getPath();
			checks.add(text(path + " name", m.name(), false));
			checks.add(text(path + " lore", m.lore(), false));
			checks.add(text(path + " effect", m.effect(), false));
			checks.add(text(path + " deed", m.deed(), false));
			boolean polymath = path.equals("polymath") && SOURCES.contains("POLYMATH");
			Pattern p = Pattern.compile("(has|active)\\([^)]{0,30}\"" + path + "\"\\)");
			checks.add(() -> assertTrue(polymath || p.matcher(SOURCES).find(), path + ": its effect is never read in code"));
		});
		assertAll(checks);
	}

	@Test
	void everyDrafterPerkIsWorded() {
		List<Executable> checks = new ArrayList<>();
		TestCards.PERKS.forEach((k, p) -> {
			checks.add(text(k.identifier().getPath() + " name", p.name(), false));
			checks.add(text(k.identifier().getPath() + " description", p.description(), false));
		});
		assertAll(checks);
	}

	// ---------------------------------------------------------------- (3) whole-character ceilings (CARDS.md §14)

	private static double total(String trait) {
		return CardRules.totalCap(TestCards.TRAITS.get(TestCards.id(trait))) / 100.0;
	}

	private static double setAmount(String keyword, int tier, String attribute) {
		return TestCards.KEYWORDS.get(TestCards.id(keyword)).sets().get(tier).effects().stream().filter(m -> m.attribute().getPath().equals(attribute))
			.mapToDouble(Keyword.Modifier::amount).sum();
	}

	private static double cursedUpside(String perk, String attribute) {
		return TestCards.def(TestCards.perk(perk)).effects().stream().filter(e -> e.attribute().getPath().equals(attribute))
			.mapToDouble(e -> e.amounts().getFirst()).sum() * 1.15; // Hex 4 (EffectsApplier.hexed)
	}

	/** Vanilla jump physics: the apex a jump of this upward speed reaches. */
	private static double apex(double v) {
		double y = 0;
		double best = 0;
		for (int t = 0; t < 60; t++) {
			y += v;
			v = (v - 0.08) * 0.98;
			best = Math.max(best, y);
		}
		return best;
	}

	@Test
	void wholeCharacterCeilings() {
		StringBuilder report = new StringBuilder("\nWhole-character worst cases from cards, sets and Mythics alone (no perks, no attributes):\n");
		List<Executable> checks = new ArrayList<>();

		// Movement: every ADD_MULTIPLIED_BASE speed source a card can hold, sprinting on a road with a blade drawn.
		double move = total("athletics/fleet") + total("blades/fleet") + total("survival/wayfaring") + total("athletics/long_winded")
			+ setAmount("swift", 2, "movement_speed") + cursedUpside("athletics/featherweight", "movement_speed")
			+ cursedUpside("blades/glass_dancer", "movement_speed");
		double moveNoRoad = total("athletics/fleet") + total("athletics/long_winded") + setAmount("swift", 2, "movement_speed")
			+ cursedUpside("athletics/featherweight", "movement_speed");
		// In game the cards' share of movement speed is corrected to the ceiling (Ceilings.movement).
		double moveApplied = move + Ceilings.movementCorrection(move);
		double moveNoRoadApplied = moveNoRoad + Ceilings.movementCorrection(moveNoRoad);
		report.append(String.format(Locale.ROOT, "  movement: +%.0f %% (road, blade, 10 s sprint) → +%.0f %% applied; +%.0f %% plain sprint → +%.0f %%; ceiling +35 %%%n",
			move * 100, moveApplied * 100, moveNoRoad * 100, moveNoRoadApplied * 100));
		checks.add(() -> assertTrue(moveApplied <= 0.35 + 1e-9, String.format(Locale.ROOT, "movement +%.0f %% passes the +35 %% ceiling", moveApplied * 100)));
		checks.add(() -> assertTrue(moveNoRoadApplied <= 0.35 + 1e-9, "plain sprint passes the movement ceiling"));

		// Melee: a blade crit on a boss that targets you (no Ambushing), three sets (Keen 6, Brutal, Ember 6) and no Mythic. The
		// attribute share (Brutal 2, Reckless Edge) is already in the hit; the rest is the hook's multiplier.
		double attribute = (1 + setAmount("brutal", 2, "attack_damage")) * (1 + cursedUpside("blades/reckless_edge", "attack_damage"));
		double hit = (1 + total("blades/keen")) * 1.08 /* Keen 2 */ * 1.2 /* Weak Point */ * 1.15 /* Kindled */;
		double melee = attribute * hit;
		double meleeApplied = attribute * Ceilings.melee(attribute, hit, false);
		double hitMythic = hit * 1.15 /* Lone Banner */;
		double meleeMythic = attribute * hitMythic * Ceilings.TWIN_EDGE;
		// The Twin Edge strikes every fourth hit again for the hit's full damage: a quarter more over a fight.
		double meleeMythicApplied = attribute * Ceilings.melee(attribute, hitMythic, true) * Ceilings.TWIN_EDGE;
		double hitExecute = hitMythic * (1 + total("blades/finishing"));
		double meleeExecute = attribute * hitExecute * Ceilings.TWIN_EDGE;
		double meleeExecuteApplied = attribute * Ceilings.melee(attribute, hitExecute, true) * Ceilings.TWIN_EDGE;
		report.append(String.format(Locale.ROOT, "  melee: ×%.2f (cards and sets) → ×%.2f applied, ×%.2f (+ Twin Edge, Lone Banner) → ×%.2f, ×%.2f below 30 %% health → ×%.2f; "
			+ "ceiling ×1.60%n", melee, meleeApplied, meleeMythic, meleeMythicApplied, meleeExecute, meleeExecuteApplied));
		for (double applied : new double[] {meleeApplied, meleeMythicApplied, meleeExecuteApplied}) {
			checks.add(() -> assertTrue(applied <= 1.6 + 1e-9, String.format(Locale.ROOT, "melee ×%.2f passes the +60 %% ceiling", applied)));
		}

		// Digging stone below y 0: the cursed cards' break speed is in the attribute, the rest in the hook.
		double digAttribute = 1 + cursedUpside("mining/greedy_pick", "block_break_speed") + cursedUpside("mining/black_lung", "block_break_speed");
		double digHook = (1 + total("mining/deepborn")) * (1 + total("mining/rockbreaker")) * 1.05;
		double dig = digAttribute * digHook;
		double digApplied = digAttribute * Ceilings.dig(digAttribute, digHook);
		report.append(String.format(Locale.ROOT, "  dig speed (stone, below y 0): +%.0f %% → +%.0f %% applied; ceiling +120 %%%n", (dig - 1) * 100, (digApplied - 1) * 100));
		checks.add(() -> assertTrue(digApplied <= 2.2 + 1e-9, String.format(Locale.ROOT, "dig +%.0f %% passes the +120 %% ceiling", (digApplied - 1) * 100)));

		// Jumps: Springing prints "X higher", and the strength it adds now gives X of height (CardPhysics).
		double spring = total("athletics/springing");
		double printed = spring * 100;
		double real = (apex(0.42 * (1 + CardPhysics.jumpStrengthFor(spring))) / apex(0.42) - 1) * 100;
		// With Gale 4 and the Spring perk (+0.085) on top, a sprint-jump still peaks under two blocks (Ceilings.jumpPower).
		double strength = 0.42 * (1 + CardPhysics.jumpStrengthFor(spring) + CardPhysics.jumpStrengthFor(setAmount("gale", 4, "jump_strength"))) + 0.085;
		double withGale = apex(Ceilings.jumpPower((float) strength, strength, 0f));
		report.append(String.format(Locale.ROOT, "  Springing at its total cap: prints +%.1f %% higher, jumps +%.1f %% higher; with Gale 4 and Spring a sprint-jump "
			+ "clears %.2f blocks (vanilla %.2f, uncapped %.2f)%n", printed, real, withGale, apex(0.42), apex(strength)));
		checks.add(() -> assertEquals(printed, real, 2.0, "Springing's printed height is not the height gained"));
		checks.add(() -> assertTrue(withGale < 2.0, String.format(Locale.ROOT, "a sprint-jump clears %.2f blocks: over a two-block wall", withGale)));

		// Knockback resistance (attribute max 1): Unyielding + Grounded + Bracing while sprinting with a blade.
		double kb = total("defense/unyielding") + total("blades/grounded") + total("athletics/bracing");
		report.append(String.format(Locale.ROOT, "  knockback resistance: %.0f %% (vanilla clamps at 100 %%)%n", kb * 100));
		System.out.println(report);
		assertAll(checks);
	}

	// ---------------------------------------------------------------- (2) what one change costs on the wire

	/** The cache attachment syncs whole on every change: how big a 300-card binder is. */
	@Test
	void fullBinderSyncSize() throws IOException {
		List<CardData> cards = new ArrayList<>();
		List<Identifier> traits = TestCards.TRAITS.keySet().stream().filter(t -> t.getPath().startsWith("mining/")).limit(3).toList();
		List<CardData.Holder> owners = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			owners.add(new CardData.Holder(UUID.randomUUID(), "Ashford_the_" + i, 41 + i));
		}
		for (int i = 0; i < 300; i++) {
			CardData base = TestCards.plain("mining/quarryman", Rarity.EPIC, i < 10 ? i : -1, "deep");
			CardData c = TestCards.withTraits(base, traits.stream().map(t -> new TraitRoll(t, 11.5)).toArray(TraitRoll[]::new));
			cards.add(new CardData(c.id(), c.skill(), c.perk(), c.rarity(), c.rank(), c.keywords(), c.traits(), c.needs(), c.drawn(), c.owner(),
				new CardData.History(9, owners), 2, c.scarSeed(), c.place(), c.attunedAt()));
		}
		CardsState state = CardsState.EMPTY.withCards(cards).withVersion(1);
		CompoundTag tag = (CompoundTag) CardsState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.write(tag, new DataOutputStream(bytes));
		System.out.printf(Locale.ROOT, "%nA 300-card binder's cache attachment: %,d bytes of NBT per sync%n", bytes.size());
		// derive() runs on every EffectsApplier.apply (slot, keep, trade, attribute point, respawn), never per tick.
		for (int i = 0; i < 2000; i++) {
			CardRules.derive(cards, s -> 20, 14, a -> 20, k -> java.util.Optional.ofNullable(TestCards.PERKS.get(k)), TestCards.DATA_SET, List.of(), List.of());
		}
		long start = System.nanoTime();
		int n = 5000;
		for (int i = 0; i < n; i++) {
			CardRules.derive(cards, s -> 20, 14, a -> 20, k -> java.util.Optional.ofNullable(TestCards.PERKS.get(k)), TestCards.DATA_SET, List.of(), List.of());
		}
		System.out.printf(Locale.ROOT, "derive() over a 300-card binder: %.1f µs a call%n", (System.nanoTime() - start) / 1000.0 / n);
		assertTrue(bytes.size() > 0);
	}
}
