package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.draft.Cursed;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.EffectsApplier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The whole-character ceilings of docs/CARDS.md §14 on what cards add: traits, set bonuses, Mythics and cursed cards'
 * upsides together. Movement +35 %, melee damage +60 %, damage taken −45 %, dig speed +120 %, extra-drop chance +50 %
 * per drop; and a jump never reaches two blocks. Every hook asks these functions for the share it may apply, so no legal
 * deck passes a ceiling in any state (sprinting on a road with a blade drawn, a critical on a foe below 30 %, the Twin
 * Edge and the Lone Banner together). {@link Build} and the {@code potential} functions say what a deck would add without
 * the ceilings, for the deck screen ("capped at +35 %") and the worst-case search in CardReportTest.
 */
public final class Ceilings {
	/** Movement speed cards may add, as a share of base speed. */
	public static final double MOVEMENT = 0.35;
	/** Melee damage cards may multiply a hit by. */
	public static final double MELEE = 1.60;
	/** The least share of a hit cards may leave you taking (damage taken −45 %). */
	public static final double TAKEN = 0.55;
	/** Dig speed cards may multiply by. */
	public static final double DIG = 2.20;
	/** The chance cards may add of one more of a drop (+50 % per drop). */
	public static final double EXTRA_DROP = 0.50;
	/** The most jump strength a player has from attributes: a jump peaks at about 1.9 blocks (vanilla 1.25), under a two-block wall. */
	public static final double JUMP_STRENGTH = 0.53;
	/** The Twin Edge strikes every fourth blade hit again: a quarter more damage over a fight, counted against the melee ceiling. */
	public static final double TWIN_EDGE = 1.25;

	private static final Identifier MOVEMENT_ID = Mettle.id("ceiling/movement_speed");

	private Ceilings() {
	}

	// ---------------------------------------------------------------- the clamps (pure)

	/** The correction a movement ceiling needs: 0 under it, else minus what cards add beyond +35 %. */
	public static double movementCorrection(double fromCards) {
		return fromCards > MOVEMENT ? MOVEMENT - fromCards : 0;
	}

	/**
	 * The multiplier a melee hit may apply after the cards' attribute share (Brutal 2, cursed attack damage) is already in
	 * its damage: their product never passes ×1.60, or ×1.28 a hit with the Twin Edge (which strikes a quarter of them
	 * again).
	 */
	public static double melee(double attributeFactor, double hitFactor, boolean twinEdge) {
		double limit = MELEE / (twinEdge ? TWIN_EDGE : 1);
		return attributeFactor * hitFactor <= limit ? hitFactor : limit / attributeFactor;
	}

	/** What cards leave of a hit (never under 55 %). */
	public static double taken(double factor) {
		return Math.max(TAKEN, factor);
	}

	/** The dig multiplier a hook may apply after the cards' attribute share (cursed block break speed). */
	public static double dig(double attributeFactor, double factor) {
		return attributeFactor * factor <= DIG ? factor : DIG / attributeFactor;
	}

	/** How much of each extra-drop chance on one drop may stand: all of it up to 50 % in sum, scaled down beyond. */
	public static double dropScale(double chanceSum) {
		return chanceSum > EXTRA_DROP ? EXTRA_DROP / chanceSum : 1;
	}

	/** A jump's power with the attribute part held to {@link #JUMP_STRENGTH} (potions keep their own share). */
	public static float jumpPower(float power, double strength, float boost) {
		if (strength <= JUMP_STRENGTH) {
			return power;
		}
		return (float) (boost + (power - boost) * JUMP_STRENGTH / strength);
	}

	// ---------------------------------------------------------------- what cards put in attributes (both sides)

	private static final Set<String> CURSED = Cursed.ALL.stream().map(Identifier::getPath).collect(Collectors.toUnmodifiableSet());

	/** Whether a modifier comes from cards: a trait, a set, a Mythic, or a cursed card's own effect. */
	public static boolean fromCards(Identifier id) {
		if (!id.getNamespace().equals(Mettle.ID)) {
			return false;
		}
		String path = id.getPath();
		if (path.startsWith("trait/") || path.startsWith("set/") || path.startsWith("trait_timed/") || path.startsWith("mythic_timed/")
			|| path.startsWith("mythic/")) {
			return true;
		}
		if (path.startsWith("perk/")) {
			// perk/<skill>/<perk>/<attribute>
			String[] parts = path.split("/");
			return parts.length >= 3 && CURSED.contains(parts[1] + "/" + parts[2]);
		}
		return false;
	}

	/** The sum of the cards' ADD_MULTIPLIED_BASE modifiers on an attribute. */
	public static double cardSum(AttributeInstance instance) {
		double sum = 0;
		for (AttributeModifier m : instance.getModifiers()) {
			if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE && fromCards(m.id())) {
				sum += m.amount();
			}
		}
		return sum;
	}

	/** How many times larger an attribute is with the cards' modifiers than without them (1 when cards add nothing). */
	public static double cardFactor(AttributeInstance instance) {
		double base = 0;
		double cards = 0;
		double total = 1;
		for (AttributeModifier m : instance.getModifiers()) {
			switch (m.operation()) {
				case ADD_MULTIPLIED_BASE -> {
					base += m.amount();
					if (fromCards(m.id())) {
						cards += m.amount();
					}
				}
				case ADD_MULTIPLIED_TOTAL -> {
					if (fromCards(m.id())) {
						total *= 1 + m.amount();
					}
				}
				default -> {
				}
			}
		}
		double without = 1 + base - cards;
		return without <= 0 ? total : (1 + base) / without * total;
	}

	/** Holds the cards' share of movement speed to +35 % with one correcting modifier (server; the attribute syncs). */
	public static void movement(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		double correction = movementCorrection(cardSum(speed));
		AttributeModifier current = speed.getModifier(MOVEMENT_ID);
		if (correction == 0) {
			if (current != null) {
				speed.removeModifier(MOVEMENT_ID);
			}
			return;
		}
		if (current != null && Math.abs(current.amount() - correction) < 1e-9) {
			return;
		}
		speed.removeModifier(MOVEMENT_ID);
		speed.addTransientModifier(new AttributeModifier(MOVEMENT_ID, correction, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	// ---------------------------------------------------------------- what a deck would add (for the deck screen and the search)

	/** The capped stats, with their ceiling as the deck screen prints it. */
	public enum Stat {
		MOVEMENT("movement", Ceilings.MOVEMENT, true),
		MELEE("melee", Ceilings.MELEE - 1, false),
		TAKEN("taken", 1 - Ceilings.TAKEN, false),
		DIG("dig", Ceilings.DIG - 1, false),
		EXTRA_DROP("extra_drop", Ceilings.EXTRA_DROP, true);

		public final String key;
		/** The most cards add, as a share: +35 %, +60 %, −45 %, +120 %, +50 %. */
		public final double ceiling;
		public final boolean additive;

		Stat(String key, double ceiling, boolean additive) {
			this.key = key;
			this.ceiling = ceiling;
			this.additive = additive;
		}
	}

	/** Traits that add movement speed (as attribute modifiers or timed ones). */
	public static final List<String> MOVEMENT_TRAITS = List.of("athletics/fleet", "blades/fleet", "survival/wayfaring", "athletics/long_winded",
		"blades/flourishing", "talent/fleet");
	/** Traits that multiply dig speed (Surefooted only takes back vanilla's slowness off the ground and is not counted). */
	public static final List<String> DIG_TRAITS = List.of("mining/deepborn", "mining/rockbreaker", "mining/netherborn", "heavy_weapons/woodcutters");
	/** Traits that multiply melee damage. */
	public static final List<String> MELEE_TRAITS = List.of("blades/keen", "blades/sweeping", "blades/finishing", "blades/ambushing", "heavy_weapons/crushing",
		"heavy_weapons/thunderous", "heavy_weapons/charging", "heavy_weapons/raging");
	/** Traits that make one more of a drop. */
	public static final List<String> DROP_TRAITS = List.of("mining/greedy", "mining/lucky", "mining/seamfinder", "mining/geodic", "heavy_weapons/splitting",
		"survival/foraging", "survival/skinning", "survival/butchering", "blades/trophy", "talent/scavenger");
	/** Traits that lessen damage taken. */
	public static final List<String> TAKEN_TRAITS = List.of("mining/stonehide", "mining/cavern_wise", "defense/arrowcatcher", "defense/fireproof",
		"defense/blastproof", "survival/weathered", "survival/thornwalker", "blades/parrying", "talent/weathered_hide", "talent/grit", "talent/cold_blood");

	/**
	 * A deck's worth for the ceilings: trait totals (in their unit, 9 = 9 %), the active set tiers, the Mythics slotted
	 * and the cursed cards held. Allies' help (Pacesetting, the Trailmaker, Warden 4, Shielding, Bodyguard) is a separate
	 * flag of each potential.
	 */
	public record Build(Map<String, Double> traits, Map<String, Integer> sets, Set<String> mythics, Map<String, Perk> cursed) {
		public double pct(String trait) {
			return this.traits.getOrDefault(trait, 0.0) / 100.0;
		}

		public int set(String keyword) {
			return this.sets.getOrDefault(keyword, 0);
		}

		public boolean mythic(String id) {
			return this.mythics.contains(id);
		}

		/** The cursed cards' amounts on an attribute and operation, with Hex 2 / 4 as EffectsApplier applies them. */
		public double cursed(String attribute, String operation) {
			double sum = 0;
			for (Perk p : this.cursed.values()) {
				for (Perk.AttributeEffect e : p.effects()) {
					if (e.attribute().getPath().equals(attribute) && e.operation().equals(operation) && !e.amounts().isEmpty()) {
						double a = e.amounts().getFirst();
						sum += set("hex") >= 2 ? EffectsApplier.hexed(e.attribute(), a, set("hex")) : a;
					}
				}
			}
			return sum;
		}

		/** What a player's own cards hold now. */
		public static Build of(CardsState cards, Function<ResourceKey<Perk>, Optional<Perk>> perks) {
			Map<String, Double> traits = new HashMap<>();
			cards.traits().forEach((id, v) -> traits.put(id.getPath(), v));
			Map<String, Integer> sets = new HashMap<>();
			cards.tiers().forEach((id, t) -> sets.put(id.getPath(), t));
			Set<String> mythics = new java.util.HashSet<>();
			Map<String, Perk> cursed = new HashMap<>();
			cards.active().forEach((key, rank) -> {
				String path = key.identifier().getPath();
				if (path.startsWith("mythic/")) {
					mythics.add(path.substring("mythic/".length()));
				} else if (CURSED.contains(path)) {
					perks.apply(key).ifPresent(p -> cursed.put(path, p));
				}
			});
			return new Build(traits, sets, mythics, cursed);
		}
	}

	/** Movement cards could add at once (sprinting on a road with a blade drawn, ten seconds in, after a kill, unarmoured). */
	public static double movementPotential(Build b, boolean allies) {
		double sum = 0;
		for (String t : MOVEMENT_TRAITS) {
			sum += b.pct(t);
		}
		sum += b.set("swift") >= 2 ? 0.03 : 0;
		sum += b.set("wild") >= 6 ? 0.08 : 0;
		sum += b.mythic("unclad") ? 0.10 : 0;
		sum += b.cursed("movement_speed", "add_multiplied_base");
		if (allies) {
			sum += 0.125 /* the best Pacesetting an ally can hold */ + 0.10 /* the Trailmaker of an ally ahead */;
		}
		return sum;
	}

	/**
	 * The most cards could multiply one melee hit by: the better weapon (a blade's critical, first or sweeping hit on a foe
	 * below 30 % that isn't looking at you; a heavy weapon's charged smash on full armour after three hits taken), every
	 * set that adds to hits, the Lone Banner, and the Twin Edge as the quarter more it strikes over a fight.
	 */
	public static double meleePotential(Build b) {
		return meleeAttribute(b) * meleeHit(b) * (b.mythic("twin_edge") ? TWIN_EDGE : 1);
	}

	/** The cards' share of attack damage (Brutal 2, cursed cards): already in a hit's damage when the hook sees it. */
	public static double meleeAttribute(Build b) {
		return (1 + (b.set("brutal") >= 2 ? 0.04 : 0) + b.cursed("attack_damage", "add_multiplied_base")) * (1 + b.cursed("attack_damage", "add_multiplied_total"));
	}

	/** The most the damage hook could multiply one melee hit by (without the Twin Edge's second strike). */
	public static double meleeHit(Build b) {
		double keen2 = b.set("keen") >= 2 ? 1.08 : 1;
		double first = b.set("keen") >= 4 ? 1.5 : 1;
		double blade = Math.max(Math.max((1 + b.pct("blades/keen")) * keen2, first), 1 + b.pct("blades/sweeping")) * (1 + b.pct("blades/finishing"))
			* (1 + b.pct("blades/ambushing"));
		double heavy = (1 + 4 * b.pct("heavy_weapons/crushing")) * (1 + b.pct("heavy_weapons/thunderous")) * (1 + b.pct("heavy_weapons/charging"))
			* (1 + 3 * b.pct("heavy_weapons/raging")) * Math.max(keen2, first);
		double m = Math.max(blade, heavy);
		m *= b.set("keen") >= 6 ? 1.2 : 1;
		m *= b.set("ember") >= 6 ? 1.15 : 1;
		m *= b.set("swift") >= 6 ? 1.3 : 1;
		m *= b.set("tide") >= 6 ? 1.15 : 1;
		m *= b.set("deep") >= 6 ? 1.5 : 1;
		m *= b.mythic("lone_banner") ? 1.15 : 1;
		return m;
	}

	/**
	 * The least share of a hit cards could leave (the best damage kind for this deck): its own traits and sets, the
	 * Unclad, and either the Lone Banner alone or allies' Warden 4, Shielding and Bodyguard.
	 */
	public static double takenPotential(Build b, boolean allies) {
		// Talents (BUILD_FEEL.md §13): Weathered Hide on every hit, Grit below 30 % health (the worst moment counts it).
		double common = (b.mythic("unclad") ? 0.75 : 1) * (b.set("deep") >= 2 ? 0.95 : 1) * (1 - b.pct("talent/weathered_hide")) * (1 - b.pct("talent/grit"));
		double company = allies ? 0.92 * (1 - 0.125) * (2.0 / 3) : 1;
		double alone = b.mythic("lone_banner") ? 0.85 : 1;
		common *= Math.min(company, alone);
		double melee = (1 - b.pct("mining/cavern_wise")) * (b.pct("blades/parrying") > 0 ? 0.5 : 1);
		double fire = (1 - b.pct("defense/fireproof")) * (b.set("ember") >= 2 ? 0.85 : 1);
		double kinds = Math.min(Math.min(melee, fire), Math.min(Math.min(1 - b.pct("defense/blastproof"), 1 - b.pct("defense/arrowcatcher")),
			Math.min(Math.min((1 - b.pct("survival/weathered")) * (1 - b.pct("talent/cold_blood")), 1 - b.pct("mining/stonehide")), 1 - b.pct("survival/thornwalker"))));
		return common * kinds;
	}

	/** The most cards could multiply dig speed by (below y 0, on the block kind this deck digs best). */
	public static double digPotential(Build b) {
		return digAttribute(b) * digHook(b);
	}

	/** The cards' share of block break speed (cursed cards): already in the speed when the hook sees it. */
	public static double digAttribute(Build b) {
		return 1 + b.cursed("block_break_speed", "add_multiplied_base");
	}

	/** The most the dig hook could multiply by. */
	public static double digHook(Build b) {
		double kind = Math.max(Math.max(1 + b.pct("mining/rockbreaker"), 1 + b.pct("mining/netherborn")), 1 + b.pct("heavy_weapons/woodcutters"));
		return (1 + b.pct("mining/deepborn")) * (b.set("deep") >= 2 ? 1.05 : 1) * kind;
	}

	/** The most extra-drop chance cards could put on one drop (an ore, a log, a plant, a hide, a cut of meat). */
	public static double dropPotential(Build b) {
		double hoard = b.set("hoard") >= 4 ? 0.04 : 0;
		double wild = b.set("wild") >= 2 ? 0.10 : 0;
		double seam = b.pct("mining/seamfinder");
		double ore = Math.max(b.pct("mining/greedy"), b.pct("mining/lucky")) + seam + hoard;
		double geode = b.pct("mining/geodic") + hoard;
		double plant = b.pct("survival/foraging") + wild + hoard;
		double animal = Math.max(b.pct("survival/skinning"), b.pct("survival/butchering")) + b.pct("blades/trophy") + b.pct("talent/scavenger") + wild + hoard;
		return Math.max(Math.max(ore, geode), Math.max(Math.max(plant, animal), b.pct("heavy_weapons/splitting")));
	}

	/** Each capped stat's potential as a share (+0.47 = +47 %, taken 0.40 = −40 %), for the deck screen. */
	public static Map<Stat, Double> potential(Build b) {
		Map<Stat, Double> out = new LinkedHashMap<>();
		out.put(Stat.MOVEMENT, movementPotential(b, false));
		out.put(Stat.MELEE, meleePotential(b) - 1);
		out.put(Stat.TAKEN, 1 - takenPotential(b, false));
		out.put(Stat.DIG, digPotential(b) - 1);
		out.put(Stat.EXTRA_DROP, dropPotential(b));
		return out;
	}

	/** A stat's share as the deck screen prints it: "+47 %", taken as "−40 %". */
	public static String percent(Stat stat, double share) {
		long n = Math.round(share * 100);
		return (stat == Stat.TAKEN ? "−" : "+") + n + " %";
	}

	/** The stats this deck would pass without the ceilings, and by how much (its potential share). */
	public static Map<Stat, Double> capped(Build b) {
		Map<Stat, Double> out = new LinkedHashMap<>();
		potential(b).forEach((stat, v) -> {
			if (v > stat.ceiling + 1e-9) {
				out.put(stat, v);
			}
		});
		return out;
	}

	/** "Movement from cards: up to +47 %, capped at +35 %". */
	public static net.minecraft.network.chat.Component line(Stat stat, double potential) {
		return net.minecraft.network.chat.Component.translatable("mettle.cards.ceiling.line", net.minecraft.network.chat.Component.translatable(
			"mettle.cards.ceiling." + stat.key), percent(stat, potential), percent(stat, stat.ceiling));
	}

	/** A trait total's line with its stat's ceiling: "Fleet 12 % of 15 % · movement capped at +35 %". */
	public static net.minecraft.network.chat.Component traitNote(net.minecraft.network.chat.Component total, Stat stat) {
		return net.minecraft.network.chat.Component.translatable("mettle.cards.ceiling.trait", total, net.minecraft.network.chat.Component.translatable(
			"mettle.cards.ceiling." + stat.key), percent(stat, stat.ceiling));
	}

	/** The stat a trait feeds, if it feeds a capped one. */
	public static Optional<Stat> statOf(String trait) {
		if (MOVEMENT_TRAITS.contains(trait)) {
			return Optional.of(Stat.MOVEMENT);
		}
		if (MELEE_TRAITS.contains(trait)) {
			return Optional.of(Stat.MELEE);
		}
		if (DIG_TRAITS.contains(trait)) {
			return Optional.of(Stat.DIG);
		}
		if (DROP_TRAITS.contains(trait)) {
			return Optional.of(Stat.EXTRA_DROP);
		}
		if (TAKEN_TRAITS.contains(trait)) {
			return Optional.of(Stat.TAKEN);
		}
		return Optional.empty();
	}
}
