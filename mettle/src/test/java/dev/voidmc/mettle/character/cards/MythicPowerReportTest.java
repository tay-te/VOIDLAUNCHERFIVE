package dev.voidmc.mettle.character.cards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.draft.DraftRules;
import dev.voidmc.mettle.core.DraftState.Card;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The Mythic power simulator (docs/MYTHIC_POWER.md). Test-only, like BuildFeelReportTest: the proposed rules (power bands
 * that grow with depth, felt floors, fewer and stronger slots, 2 / 3 / 5 sets, techniques, the stat contract, PvP and boss
 * rules) are written here against the shipped data, so nothing in the game changes until the build plan's waves.
 *
 * <p>Two tests. {@link #curveHolds()} runs on every build and asserts the curve, the archetype targets (specialist,
 * hybrid pairs, balanced, Wayfarer), the floors, the fairness checks and the stat contract. {@link #report()} runs with
 * {@code -Dmettle.balance=true}, adds the slot and set study (real careers through BuildFeelReportTest's harness and the
 * game's own deal) and writes {@code build/reports/mettle-mythic-power.md}.
 */
class MythicPowerReportTest {

	// ================================================================ §3 the power bands

	/**
	 * A band: what Mettle as a whole (attributes, perks, cards, traits, sets, talents, Mythics, bonds) may add. Offense is
	 * a multiplier on the hit (passive = always-on sources only; full = with triggers and techniques). Taken is the least
	 * share of a hit Mettle leaves (passive / full). Health in points. Road stats as shares. Sustain: Mettle healing in a
	 * fight, health points a second.
	 */
	enum Band {
		I("I", 0, 1.15, 1.22, 1.25, 0.95, 0.93, 0.92, 2, 0.15, 0.50, 0.20, 0.30, 0.5),
		II("II", 6, 1.25, 1.36, 1.45, 0.93, 0.91, 0.89, 6, 0.20, 0.90, 0.30, 0.40, 0.6),
		III("III", 12, 1.35, 1.52, 1.70, 0.91, 0.89, 0.87, 12, 0.26, 1.40, 0.40, 0.50, 0.8),
		IV("IV · Master", 18, 1.45, 1.72, 2.00, 0.90, 0.87, 0.85, 18, 0.32, 1.80, 0.50, 0.60, 1.0),
		APEX("Apex", 18, 1.50, 1.82, 2.15, 0.90, 0.86, 0.84, 19, 0.36, 2.00, 0.55, 0.65, 1.0),
		LEGEND("Legend", 18, 1.55, 1.92, 2.30, 0.90, 0.86, 0.83, 20, 0.40, 2.20, 0.60, 0.70, 1.0);

		final String label;
		final int depth;
		/** Offense: always-on sources; with conditions met; with techniques landed (the full ceiling). */
		final double offPassive;
		final double offCond;
		final double offFull;
		final double takenPassive;
		final double takenCond;
		final double takenFull;
		final double health;
		final double move;
		final double gather;
		final double yield;
		final double sustain;
		final double hps;

		Band(String label, int depth, double offPassive, double offCond, double offFull, double takenPassive, double takenCond, double takenFull, double health,
			double move, double gather, double yield, double sustain, double hps) {
			this.label = label;
			this.depth = depth;
			this.offPassive = offPassive;
			this.offCond = offCond;
			this.offFull = offFull;
			this.takenPassive = takenPassive;
			this.takenCond = takenCond;
			this.takenFull = takenFull;
			this.health = health;
			this.move = move;
			this.gather = gather;
			this.yield = yield;
			this.sustain = sustain;
			this.hps = hps;
		}

		/** The most fight power a band allows: √(offense × toughness), toughness = health factor ÷ taken. */
		double fightCap() {
			return Math.sqrt(this.offFull * (20 + this.health) / 20 / this.takenFull);
		}

		/** The same without techniques: what plain play can reach. */
		double plainCap() {
			return Math.sqrt(this.offCond * (20 + this.health) / 20 / this.takenCond);
		}
	}

	/** Depth: the better of the three best skills' mean and the six best skills' mean + 3 (the Wayfarer's road). */
	static int depth(int... levels) {
		int[] l = levels.clone();
		Arrays.sort(l);
		double top3 = 0;
		double top6 = 0;
		for (int i = 0; i < 6; i++) {
			int v = l.length - 1 - i >= 0 ? l[l.length - 1 - i] : 0;
			if (i < 3) {
				top3 += v;
			}
			top6 += v;
		}
		return (int) Math.min(20, Math.max(Math.floor(top3 / 3), Math.floor(top6 / 6 + 3)));
	}

	/**
	 * A character's band: depth sets I-IV; past Master, a Mythic or a top set lifts it to Apex and both to Legend, but only
	 * when the three best skills average 18 or more (commitment, not breadth, opens the very top: §3.1).
	 */
	static Caps characterBand(Arch a, int depth, boolean topSet, boolean mythic) {
		double progress = Math.min(1.0, depth / (double) depth(a.levels));
		int[] l = a.levels.clone();
		Arrays.sort(l);
		double top3 = (l[l.length - 1] + l[l.length - 2] + l[l.length - 3]) / 3.0 * progress;
		boolean committed = top3 >= 18 - 1e-9;
		return caps(depth, topSet && committed, mythic && committed);
	}

	static Band band(int depth, boolean topSet, boolean mythic) {
		Band b = depth >= 18 ? Band.IV : depth >= 12 ? Band.III : depth >= 6 ? Band.II : Band.I;
		if (b == Band.IV && topSet && mythic) {
			return Band.LEGEND;
		}
		return b == Band.IV && (topSet || mythic) ? Band.APEX : b;
	}

	/**
	 * The ceilings at a depth: the bands are anchors (I at depth 2, II at 6, III at 12, IV at 18) and the ceiling rises
	 * with every point of depth between them, so no stretch of play is flat; past 18, Apex and Legend as {@link #band}.
	 */
	record Caps(String label, boolean legend, double offPassive, double offCond, double offFull, double takenPassive, double takenCond, double takenFull,
		double health, double move, double gather, double yield, double sustain, double hps) {
		static Caps of(Band b) {
			return new Caps(b.label, b == Band.LEGEND, b.offPassive, b.offCond, b.offFull, b.takenPassive, b.takenCond, b.takenFull, b.health, b.move, b.gather,
				b.yield, b.sustain, b.hps);
		}

		static Caps lerp(Band a, Band b, double t, String label) {
			return new Caps(label, false, l(a.offPassive, b.offPassive, t), l(a.offCond, b.offCond, t), l(a.offFull, b.offFull, t), l(a.takenPassive, b.takenPassive, t),
				l(a.takenCond, b.takenCond, t), l(a.takenFull, b.takenFull, t), l(a.health, b.health, t), l(a.move, b.move, t), l(a.gather, b.gather, t),
				l(a.yield, b.yield, t), l(a.sustain, b.sustain, t), l(a.hps, b.hps, t));
		}

		private static double l(double x, double y, double t) {
			return x + (y - x) * t;
		}

		double fightCap() {
			return Math.sqrt(this.offFull * (20 + this.health) / 20 / this.takenFull);
		}
	}

	static final int[] ANCHORS = {2, 6, 12, 18};

	static Caps caps(int depth, boolean topSet, boolean mythic) {
		if (depth >= 18) {
			return Caps.of(band(depth, topSet, mythic));
		}
		if (depth <= ANCHORS[0]) {
			return Caps.of(Band.I);
		}
		Band[] b = Band.values();
		for (int i = 0; i < 3; i++) {
			if (depth <= ANCHORS[i + 1]) {
				double t = (depth - ANCHORS[i]) / (double) (ANCHORS[i + 1] - ANCHORS[i]);
				return t >= 1 - 1e-9 ? Caps.of(b[i + 1]) : Caps.lerp(b[i], b[i + 1], t, b[i].label + "→" + b[i + 1].label.split(" ")[0] + String.format(Locale.ROOT, " %.0f %%", 100 * t));
			}
		}
		return Caps.of(Band.IV);
	}

	/** PvP caps (§3.4): what Mettle may add to a hit between players, whatever the band. */
	static final double PVP_OFFENSE = 1.30;
	static final double PVP_TAKEN = 0.75;
	static final double PVP_HEALTH = 4;
	static final double PVP_MOVE = 0.20;
	/** Against a boss, Mettle's offense bonus counts 70 %; executes never apply; Mettle healing counts half (§9). */
	static final double BOSS_OFFENSE_SHARE = 0.70;
	static final double BOSS_SUSTAIN_SHARE = 0.5;
	/** Share of general foes resisting one damage kind (Bestiary resistances, assumed), and what they leave of it. */
	static final double RESIST_SHARE = 0.3;
	static final double RESIST_LEFT = 0.6;

	// ================================================================ the power model

	enum Stat { DMG, RATE, TAKEN, HEALTH, MOVE, GATHER, YIELD, CARRY, SUSTAIN, HPS }

	enum Kind { ATTR, PASSIVE, COND, TECH }

	enum Area { MELEE, RANGED, SPELL, ANY }

	enum Need { NONE, TOP_SET, MYTHIC }

	enum Origin { ATTR, PERK, CARD }

	/**
	 * One source of power. Values are at Epic for scaled sources (Common 0.6×, Rare 0.8× while the depth is below 6 / 12);
	 * {@code plain} and {@code skilled} are the uptimes for a player who only swings and one who lands techniques (ATTR and
	 * PASSIVE are always 1). {@code from}: the depth it arrives at. {@code share}: an ATTR source's share of attribute points.
	 */
	record Src(String name, Kind kind, Stat stat, Area area, double value, double plain, double skilled, int from, boolean scaled, Need need,
		boolean opener, boolean noBoss, Origin origin, double share) {
	}

	static final class Build {
		final List<Src> list = new ArrayList<>();

		Build attr(String name, Stat stat, Area area, double perPoint, double share) {
			this.list.add(new Src(name, Kind.ATTR, stat, area, perPoint, 1, 1, 0, false, Need.NONE, false, false, Origin.ATTR, share));
			return this;
		}

		Build p(String name, Stat stat, Area area, double value, int from, boolean scaled, Origin origin) {
			this.list.add(new Src(name, Kind.PASSIVE, stat, area, value, 1, 1, from, scaled, Need.NONE, false, false, origin, 0));
			return this;
		}

		Build p(String name, Stat stat, Area area, double value, int from, boolean scaled) {
			return p(name, stat, area, value, from, scaled, Origin.CARD);
		}

		Build c(String name, Stat stat, Area area, double value, double plain, double skilled, int from, boolean scaled, Origin origin) {
			this.list.add(new Src(name, Kind.COND, stat, area, value, plain, skilled, from, scaled, Need.NONE, false, false, origin, 0));
			return this;
		}

		Build c(String name, Stat stat, Area area, double value, double plain, double skilled, int from) {
			return c(name, stat, area, value, plain, skilled, from, false, Origin.CARD);
		}

		Build t(String name, Stat stat, Area area, double value, double skilled, int from) {
			this.list.add(new Src(name, Kind.TECH, stat, area, value, 0, skilled, from, false, Need.NONE, false, false, Origin.CARD, 0));
			return this;
		}

		/** Changes the last source: needs a top set or a Mythic, opens fights, never on bosses. */
		Build need(Need n) {
			Src s = this.list.removeLast();
			this.list.add(new Src(s.name, s.kind, s.stat, s.area, s.value, s.plain, s.skilled, s.from, s.scaled, n, s.opener, s.noBoss, s.origin, s.share));
			return this;
		}

		Build opener() {
			Src s = this.list.removeLast();
			this.list.add(new Src(s.name, s.kind, s.stat, s.area, s.value, s.plain, s.skilled, s.from, s.scaled, s.need, true, s.noBoss, s.origin, s.share));
			return this;
		}

		Build noBoss() {
			Src s = this.list.removeLast();
			this.list.add(new Src(s.name, s.kind, s.stat, s.area, s.value, s.plain, s.skilled, s.from, s.scaled, s.need, s.opener, true, s.origin, s.share));
			return this;
		}

		/** The talents every character slots (Hardy, Weathered Hide), at the proposed numbers (§7). */
		Build talents() {
			return p("Hardy (talent)", Stat.HEALTH, Area.ANY, 3, 2, true).p("Weathered Hide (talent)", Stat.TAKEN, Area.ANY, 0.06, 2, true);
		}

		/** Vitality: +1 health a point above 3 (today +1 per two points), so a committed body reaches the overview's 40 health (§7.3). */
		Build vitality(double share) {
			return attr("Vitality", Stat.HEALTH, Area.ANY, 1.0, share);
		}
	}

	enum Group { SPECIALIST, HYBRID, BALANCED, WAYFARER }

	/** Areas an archetype can own: the three damage kinds, the ambush (first strike), the road. */
	enum Own { MELEE, RANGED, SPELL, AMBUSH, ROAD, NONE }

	/**
	 * An archetype at character level 50. {@code kinds}: the skill governing each damage kind and the ambush (melee = the
	 * better of Blades and Heavy Weapons, ranged = Marksmanship, spell = Arcana, stealth = Stealth), whose own band sets
	 * that kind's offense ceiling (§3.2).
	 */
	record Arch(String name, Group group, Own own, String skills, int[] levels, int[] kinds, Area devoted, int smithing, double kite, boolean topSet,
		boolean mythic, Build after, Build before, List<String> parents) {
	}

	/**
	 * Devotion (§3.3), the specialist's edge: when the top set is native to your best skill and its own drawn Mythic is
	 * slotted, that skill's damage kind gets this much more ceiling (conditions / full).
	 */
	static final double DEVOTION_COND = 0.10;
	static final double DEVOTION_FULL = 0.20;

	/** Better gear from your own forge (Smithing over 10, VOID Arsenal's parts): the item's base, outside Mettle's ceilings. */
	static double gear(Arch a) {
		return 1 + 0.006 * Math.max(0, a.smithing - 10);
	}

	record Result(Map<Area, Double> offense, double taken, double health, double move, double gather, double yield, double carry, double sustain,
		double hps, Caps band, double rawPassive, double rawFull, double gear) {

		double toughness(double kite) {
			return (20 + this.health) / 20 / this.taken * (1 + kite * this.move) * this.gear;
		}

		double off(Area a) {
			return this.offense.get(a);
		}

		double best() {
			return Math.max(off(Area.MELEE), Math.max(off(Area.RANGED), off(Area.SPELL)));
		}

		/** General fights: some foes resist your best kind; you switch to your other kind if it is better than what's left. */
		double general() {
			Area best = Area.MELEE;
			for (Area a : List.of(Area.RANGED, Area.SPELL)) {
				if (off(a) > off(best)) {
					best = a;
				}
			}
			boolean elemental = best == Area.SPELL;
			double other = elemental ? Math.max(off(Area.MELEE), off(Area.RANGED)) : off(Area.SPELL);
			return (1 - RESIST_SHARE) * off(best) + RESIST_SHARE * Math.max(off(best) * RESIST_LEFT, other);
		}

		double road() {
			return Math.pow((1 + this.move) * (1 + this.gather) * (1 + this.yield) * (1 + this.carry / 80) * (1 + this.sustain), 1.0 / 5);
		}
	}

	enum Rules { BEFORE, AFTER }

	enum Mode { NORMAL, BOSS, PVP, AMBUSH }

	static double rarityScale(int depth) {
		return depth < 6 ? 0.6 : depth < 12 ? 0.8 : 1.0;
	}

	/** Attribute points above 3 that an attribute with this share of the points holds at a character level (17 at most). */
	static double attrPoints(int characterLevel, double share) {
		return Math.min(17, share * (characterLevel + 3));
	}

	static Result compute(Arch a, Rules rules, int characterLevel, int depth, boolean topSet, boolean mythic, boolean skilled, Mode mode) {
		Build b = rules == Rules.AFTER ? a.after : a.before;
		Caps band = characterBand(a, depth, topSet, mythic);
		Map<Area, double[]> pools = new EnumMap<>(Area.class); // dmg/rate: passive, cond, tech; before: cards, perks
		for (Area ar : Area.values()) {
			pools.put(ar, new double[10]);
		}
		double takenP = 1;
		double takenC = 1;
		double takenT = 1;
		double takenCard = 1;
		double takenPerk = 1;
		double health = 0;
		double moveCard = 0;
		double move = 0;
		double gather = 0;
		double yield = 0;
		double carry = 0;
		double sustain = 0;
		double hps = 0;
		for (Src s : b.list) {
			if (s.from > depth || s.need == Need.TOP_SET && !topSet || s.need == Need.MYTHIC && !mythic) {
				continue;
			}
			if (mode == Mode.BOSS && s.noBoss) {
				continue;
			}
			double v = s.kind == Kind.ATTR ? s.value * attrPoints(characterLevel, s.share) : s.scaled ? s.value * rarityScale(depth) : s.value;
			double u = switch (s.kind) {
				case ATTR, PASSIVE -> 1;
				case COND, TECH -> mode == Mode.AMBUSH && s.opener ? 1 : skilled ? s.skilled : s.plain;
			};
			if (u <= 0) {
				continue;
			}
			boolean passive = s.kind == Kind.ATTR || s.kind == Kind.PASSIVE;
			switch (s.stat) {
				case DMG, RATE -> {
					int idx = s.stat == Stat.DMG ? 0 : 1;
					List<Area> to = s.area == Area.ANY ? List.of(Area.MELEE, Area.RANGED, Area.SPELL) : List.of(s.area);
					for (Area ar : to) {
						double[] p = pools.get(ar);
						if (rules == Rules.AFTER) {
							p[(passive ? 0 : s.kind == Kind.COND ? 2 : 4) + idx] += v * u;
						} else {
							p[(s.origin == Origin.CARD ? 6 : 8) + idx] += v * u;
						}
					}
				}
				case TAKEN -> {
					double left = 1 - v * u;
					if (rules == Rules.AFTER) {
						if (passive) {
							takenP *= left;
						} else if (s.kind == Kind.COND) {
							takenC *= left;
						} else {
							takenT *= left;
						}
					} else if (s.origin == Origin.CARD) {
						takenCard *= left;
					} else {
						takenPerk *= left;
					}
				}
				case HEALTH -> health += v * u;
				case MOVE -> {
					if (rules == Rules.BEFORE && s.origin == Origin.CARD) {
						moveCard += v * u;
					} else {
						move += v * u;
					}
				}
				case GATHER -> gather += v * u;
				case YIELD -> yield += v * u;
				case CARRY -> carry += v * u;
				case SUSTAIN -> sustain += v * u;
				case HPS -> hps += v * u;
			}
		}
		Map<Area, Double> off = new EnumMap<>(Area.class);
		double rawPassive = 0;
		double rawFull = 0;
		double progress = Math.min(1.0, depth / (double) depth(a.levels));
		for (Area ar : List.of(Area.MELEE, Area.RANGED, Area.SPELL)) {
			double[] p = pools.get(ar);
			// An opening strike on an unaware foe has Stealth's own allowance: a knife in the back more than an arrow from the dark.
			double ambush = mode == Mode.AMBUSH ? 1 + Math.round(a.kinds[3] * progress) / (ar == Area.MELEE ? 40.0 : 80.0) : 1;
			double o;
			if (rules == Rules.AFTER) {
				// The kind's own ceiling: the band of the skill that governs it, never above the character's band (§3.2).
				int lvl = (int) Math.round(a.kinds[ar.ordinal()] * progress);
				Caps kb = caps(lvl, topSet && lvl >= 18, mythic && lvl >= 18);
				if (kb.offFull > band.offFull) {
					kb = band;
				}
				double passive = (1 + p[0]) * (1 + p[1]);
				double cond = (1 + p[2]) * (1 + p[3]);
				double tech = (1 + p[4]) * (1 + p[5]);
				// The Rogue's devotion is Stealth's: its openers, not every blade hit (a Stealth Mythic, §6.4).
				boolean devoted = kb.legend && (a.group == Group.SPECIALIST && a.own == Own.AMBUSH ? mode == Mode.AMBUSH && ar == Area.MELEE : ar == a.devoted);
				double stage = Math.min((kb.offCond + (devoted ? DEVOTION_COND : 0)) * ambush, Math.min(kb.offPassive, passive) * cond);
				o = Math.min((kb.offFull + (devoted ? DEVOTION_FULL : 0)) * ambush, stage * tech);
				o *= gear(a);
				rawPassive = Math.max(rawPassive, passive);
				rawFull = Math.max(rawFull, passive * cond * tech);
			} else {
				// Today: the §14 ceiling clamps what cards add (×1.6); perks and attributes sit outside it.
				o = Math.min(Ceilings.MELEE, (1 + p[6]) * (1 + p[7])) * (1 + p[8]) * (1 + p[9]);
				rawFull = Math.max(rawFull, (1 + p[6]) * (1 + p[7]) * (1 + p[8]) * (1 + p[9]));
			}
			if (mode == Mode.PVP) {
				o = Math.min(PVP_OFFENSE, o);
			}
			if (mode == Mode.BOSS) {
				o = 1 + BOSS_OFFENSE_SHARE * (o - 1);
			}
			off.put(ar, o);
		}
		double taken;
		if (rules == Rules.AFTER) {
			taken = Math.max(band.takenFull, Math.max(band.takenCond, Math.max(band.takenPassive, takenP) * takenC) * takenT);
			health = Math.min(band.health, health);
			move = Math.min(band.move, move);
			gather = Math.min(band.gather, gather);
			yield = Math.min(band.yield, yield);
			sustain = Math.min(band.sustain, sustain);
			hps = Math.min(band.hps, hps);
		} else {
			taken = Math.max(Ceilings.TAKEN, takenCard) * takenPerk;
			move = move + Math.min(Ceilings.MOVEMENT, moveCard);
		}
		if (mode == Mode.PVP) {
			taken = Math.max(PVP_TAKEN, taken);
			health = Math.min(PVP_HEALTH, health);
			move = Math.min(PVP_MOVE, move);
		}
		if (mode == Mode.BOSS) {
			hps *= BOSS_SUSTAIN_SHARE;
		}
		return new Result(off, taken, health, move, gather, yield, carry, sustain, hps, band, rawPassive, rawFull, rules == Rules.AFTER ? gear(a) : 1);
	}

	/**
	 * Hybrids whose second discipline draws on another resource (mana beside stamina) fight with both: the second kind
	 * fills the gaps the first leaves (§6.1). The share of the second kind's bonus that adds to a general fight.
	 */
	static final Map<String, Area> SECOND_KIND = Map.of("Spellblade", Area.SPELL, "Venomblade", Area.SPELL);
	static final double SECOND_SHARE = 0.25;

	static double fight(Arch a, Result r) {
		double o = r.general();
		Area second = SECOND_KIND.get(a.name);
		if (second != null) {
			o += SECOND_SHARE * (r.off(second) - 1);
		}
		return Math.sqrt(o * r.toughness(a.kite));
	}

	static double area(Arch a, Result r, Area area) {
		return Math.sqrt(r.off(area) * r.toughness(a.kite));
	}

	/** Scores at the archetype's own levels, character level 50, skilled play. */
	record Scores(double melee, double ranged, double spell, double ambush, double combat, double boss, double road, double general, double pvp, Caps band) {
		double own(Own o) {
			return switch (o) {
				case MELEE -> this.melee;
				case RANGED -> this.ranged;
				case SPELL -> this.spell;
				case AMBUSH -> this.ambush;
				case ROAD -> this.road;
				case NONE -> this.general;
			};
		}
	}

	/** General play: fights, bosses and the road (exploring, gathering, surviving), weighted. */
	static final double GENERAL_COMBAT = 0.45;
	static final double GENERAL_BOSS = 0.15;
	static final double GENERAL_ROAD = 0.25;
	static final double GENERAL_AMBUSH = 0.15;

	static Scores scores(Arch a, int characterLevel) {
		int d = depth(a.levels);
		Result r = compute(a, Rules.AFTER, characterLevel, d, a.topSet, a.mythic, true, Mode.NORMAL);
		Result amb = compute(a, Rules.AFTER, characterLevel, d, a.topSet, a.mythic, true, Mode.AMBUSH);
		Result boss = compute(a, Rules.AFTER, characterLevel, d, a.topSet, a.mythic, true, Mode.BOSS);
		Result pvp = compute(a, Rules.AFTER, characterLevel, d, a.topSet, a.mythic, true, Mode.PVP);
		double combat = fight(a, r);
		Area second = SECOND_KIND.get(a.name);
		double bossScore = Math.sqrt((boss.best() + (second == null ? 0 : SECOND_SHARE * (boss.off(second) - 1))) * boss.toughness(a.kite));
		double road = r.road();
		double ambushScore = Math.sqrt(amb.best() * amb.toughness(a.kite));
		double general = Math.pow(combat, GENERAL_COMBAT) * Math.pow(bossScore, GENERAL_BOSS) * Math.pow(road, GENERAL_ROAD) * Math.pow(ambushScore, GENERAL_AMBUSH);
		return new Scores(area(a, r, Area.MELEE), area(a, r, Area.RANGED), area(a, r, Area.SPELL), ambushScore, combat,
			bossScore, road, general, Math.sqrt(pvp.best() * pvp.toughness(0)), r.band);
	}

	// ================================================================ §10 the archetypes (data)

	static final List<Arch> ARCHES = new ArrayList<>();

	static Arch arch(String name) {
		return ARCHES.stream().filter(x -> x.name.equals(name)).findFirst().orElseThrow();
	}

	/** Athletics as a support deck (Runner, Pack Mule, Combat Roll). */
	static Build athletics(Build b, double share) {
		return b.p("Runner (root)", Stat.MOVE, Area.ANY, 0.16 * share, 2, true).p("Pack Mule (root)", Stat.CARRY, Area.ANY, 24 * share, 2, true)
			.t("Combat Roll (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 6);
	}

	static Build survival(Build b, double share) {
		return b.p("Gatherer (root)", Stat.YIELD, Area.ANY, 0.20 * share, 2, true).p("Iron Stomach", Stat.SUSTAIN, Area.ANY, 0.25 * share, 6, false)
			.c("Pathfinder", Stat.MOVE, Area.ANY, 0.10, 0.5, 0.5, 6);
	}

	static Build mining(Build b, double share) {
		return b.p("Quarryman (root)", Stat.GATHER, Area.ANY, 1.0 * share, 2, true).p("Prospector (root)", Stat.YIELD, Area.ANY, 0.20 * share, 2, true)
			.p("Rock Hauler", Stat.CARRY, Area.ANY, 12 * share, 6, false);
	}

	static {
		// ---- specialists: one discipline, every support feeding it
		Build warrior = new Build().attr("Strength", Stat.DMG, Area.MELEE, 0.02, 0.45).vitality(0.30).talents()
			.p("Heft (root)", Stat.DMG, Area.MELEE, 0.30, 2, true).p("Armour Crusher", Stat.DMG, Area.MELEE, 0.12, 6, false)
			.p("Crushing (trait)", Stat.DMG, Area.MELEE, 0.14, 3, true).p("Swinging (trait)", Stat.RATE, Area.MELEE, 0.10, 6, true)
			.p("Brutal 2", Stat.DMG, Area.MELEE, 0.12, 3, false)
			.c("Berserker (Mastery)", Stat.DMG, Area.MELEE, 0.40, 0.15, 0.25, 18).c("Charging (trait)", Stat.DMG, Area.MELEE, 0.20, 0.2, 0.35, 8)
			.c("Brutal 5 · Overkill", Stat.DMG, Area.MELEE, 0.25, 0.1, 0.25, 13).need(Need.TOP_SET)
			.t("Brutal 3 · stagger", Stat.DMG, Area.MELEE, 0.10, 0.6, 8).t("Momentum Swing (technique)", Stat.DMG, Area.MELEE, 0.20, 0.5, 6)
			.t("Guard Break (technique)", Stat.DMG, Area.MELEE, 0.15, 0.5, 12).t("Worldsplitter (Mythic)", Stat.DMG, Area.MELEE, 0.12, 1, 18).need(Need.MYTHIC)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).p("Iron 2", Stat.TAKEN, Area.ANY, 0.05, 3, false)
			.p("Stout (trait)", Stat.HEALTH, Area.ANY, 3, 6, true).c("Hold the Line", Stat.TAKEN, Area.ANY, 0.15, 0.3, 0.5, 12)
			.c("Iron 3 · poise", Stat.TAKEN, Area.ANY, 0.10, 0.3, 0.5, 8).t("Unstoppable (technique)", Stat.TAKEN, Area.ANY, 0.25, 0.3, 12)
			.t("Perfect Guard (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 6).c("Bloodthirsty (trait)", Stat.HPS, Area.ANY, 0.5, 0.5, 0.6, 8);
		athletics(warrior, 0.6);
		Build warriorBefore = new Build().attr("Strength", Stat.DMG, Area.MELEE, 0.02, 0.45).attr("Vitality", Stat.HEALTH, Area.ANY, 0.5, 0.30)
			.p("Hardy (talent)", Stat.HEALTH, Area.ANY, 2, 2, true).p("Weathered Hide (talent)", Stat.TAKEN, Area.ANY, 0.03, 2, true)
			.p("Heft (root)", Stat.DMG, Area.MELEE, 0.15, 2, true, Origin.PERK).p("Armour Crusher", Stat.DMG, Area.MELEE, 0.12, 6, false, Origin.PERK)
			.p("Crushing (trait)", Stat.DMG, Area.MELEE, 0.10, 3, true).p("Swinging (trait)", Stat.RATE, Area.MELEE, 0.06, 6, true)
			.p("Brutal 2", Stat.DMG, Area.MELEE, 0.04, 3, false)
			.c("Berserker (Mastery)", Stat.DMG, Area.MELEE, 0.40, 0.15, 0.25, 18, false, Origin.PERK).c("Charging (trait)", Stat.DMG, Area.MELEE, 0.12, 0.2, 0.35, 8, true, Origin.CARD)
			.c("Raging (trait)", Stat.DMG, Area.MELEE, 0.09, 0.3, 0.4, 8, true, Origin.CARD)
			.c("Brutal 6 · Overkill", Stat.DMG, Area.MELEE, 0.25, 0.1, 0.25, 13).need(Need.TOP_SET)
			.c("Worldsplitter (Mythic)", Stat.DMG, Area.MELEE, 0.05, 0.5, 1, 18).need(Need.MYTHIC)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.08, 2, true, Origin.PERK).p("Stalwart (trait)", Stat.TAKEN, Area.ANY, 0.04, 3, true)
			.p("Iron 4", Stat.TAKEN, Area.ANY, 0.03, 8, false).p("Stout (trait)", Stat.HEALTH, Area.ANY, 1.6, 6, true)
			.c("Hold the Line", Stat.TAKEN, Area.ANY, 0.15, 0.3, 0.5, 12, false, Origin.PERK);
		ARCHES.add(new Arch("Warrior", Group.SPECIALIST, Own.MELEE, "Heavy Weapons 20, Defense 20, Athletics 16, Smithing 12",
			new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 0, 0}, Area.MELEE, 12, 0, true, true, warrior, warriorBefore, List.of()));

		Build ranger = new Build().attr("Agility", Stat.RATE, Area.RANGED, 0.015, 0.45).vitality(0.30).talents()
			.p("Steady Aim (root)", Stat.RATE, Area.RANGED, 0.40, 2, true).p("Heavy-tipped (trait)", Stat.DMG, Area.RANGED, 0.11, 3, true)
			.p("Quickdraw (trait)", Stat.RATE, Area.RANGED, 0.14, 6, true).p("Keen 2", Stat.DMG, Area.RANGED, 0.08, 3, false)
			.c("Long Shot", Stat.DMG, Area.RANGED, 0.30, 0.4, 0.6, 6).c("Poised (trait)", Stat.DMG, Area.RANGED, 0.16, 0.4, 0.5, 8, true, Origin.CARD)
			.c("Keen 3 · opening crit", Stat.DMG, Area.RANGED, 0.5, 0.15, 0.2, 8).opener()
			.c("Keen 5 · Weak Point", Stat.DMG, Area.RANGED, 0.25, 0.3, 0.6, 13).need(Need.TOP_SET)
			.c("Deadeye (Mastery)", Stat.DMG, Area.RANGED, 1.0, 0.08, 0.3, 18).t("Charged Arrow (technique)", Stat.DMG, Area.RANGED, 0.5, 0.4, 6)
			.t("Evasive Shot (technique)", Stat.DMG, Area.RANGED, 0.4, 0.2, 12)
			.c("The Seeking Fletch (Mythic)", Stat.DMG, Area.RANGED, 0.15, 0.5, 1, 18).need(Need.MYTHIC)
			.c("Hunter's Mark (marked foes take more)", Stat.DMG, Area.RANGED, 0.15, 0.5, 0.7, 6)
			.c("Bond · Huntsman (Marksmanship + Survival)", Stat.DMG, Area.RANGED, 0.20, 0.5, 0.7, 12)
			.t("Pin Down (technique)", Stat.TAKEN, Area.ANY, 0.10, 0.4, 12).c("Sixth Sense (Survival)", Stat.TAKEN, Area.ANY, 0.10, 0.5, 0.6, 12).p("Gale 2", Stat.MOVE, Area.ANY, 0.04, 3, false);
		athletics(ranger, 0.8);
		survival(ranger, 1.0);
		Build rangerBefore = new Build().attr("Agility", Stat.RATE, Area.RANGED, 0.0, 0.45).attr("Vitality", Stat.HEALTH, Area.ANY, 0.5, 0.20)
			.p("Hardy (talent)", Stat.HEALTH, Area.ANY, 2, 2, true).p("Weathered Hide (talent)", Stat.TAKEN, Area.ANY, 0.03, 2, true)
			.p("Steady Aim (root)", Stat.RATE, Area.RANGED, 0.30, 2, true, Origin.PERK).p("Heavy-tipped (trait)", Stat.DMG, Area.RANGED, 0.07, 3, true)
			.p("Quickdraw (trait)", Stat.RATE, Area.RANGED, 0.08, 6, true).p("Keen 2", Stat.DMG, Area.RANGED, 0.03, 3, false)
			.c("Long Shot", Stat.DMG, Area.RANGED, 0.30, 0.4, 0.6, 6, false, Origin.PERK).c("Poised (trait)", Stat.DMG, Area.RANGED, 0.10, 0.4, 0.5, 8, true, Origin.CARD)
			.c("Keen 4", Stat.DMG, Area.RANGED, 0.5, 0.15, 0.2, 8).c("Keen 6 · Weak Point", Stat.DMG, Area.RANGED, 0.20, 0.3, 0.6, 13).need(Need.TOP_SET)
			.c("Deadeye (Mastery)", Stat.DMG, Area.RANGED, 1.0, 0.08, 0.3, 18, false, Origin.PERK)
			.c("The Seeking Fletch (Mythic)", Stat.DMG, Area.RANGED, 0.10, 0.5, 1, 18).need(Need.MYTHIC)
			.p("Runner (root)", Stat.MOVE, Area.ANY, 0.09, 2, true, Origin.PERK);
		ARCHES.add(new Arch("Ranger", Group.SPECIALIST, Own.RANGED, "Marksmanship 20, Survival 20, Athletics 16, Stealth 12",
			new int[] {20, 20, 16, 12, 8, 6}, new int[] {0, 20, 0, 12}, Area.RANGED, 0, 0.4, true, true, ranger, rangerBefore, List.of()));

		Build rogue = new Build().attr("Agility", Stat.RATE, Area.MELEE, 0.015, 0.45).vitality(0.30).talents()
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.30, 2, true).p("Keen (trait)", Stat.DMG, Area.MELEE, 0.07, 3, true)
			.c("Quick Draw (root)", Stat.DMG, Area.MELEE, 0.70, 0.15, 0.3, 2, true, Origin.CARD).opener()
			.c("Bleed", Stat.DMG, Area.MELEE, 0.15, 0.4, 0.5, 12).c("Executioner (Mastery)", Stat.DMG, Area.MELEE, 0.5, 0.1, 0.15, 18).noBoss()
			.c("Ambushing (trait)", Stat.DMG, Area.MELEE, 0.20, 0.2, 0.4, 8, true, Origin.CARD).opener()
			.c("Keen 3 · opening crit", Stat.DMG, Area.MELEE, 0.5, 0.1, 0.2, 8).opener()
			.c("Swift 5 · Slipstream", Stat.DMG, Area.MELEE, 0.6, 0.1, 0.35, 13).need(Need.TOP_SET).opener()
			.c("The Unseen Hand (Stealth Mythic)", Stat.DMG, Area.MELEE, 0.40, 0.2, 0.3, 18).need(Need.MYTHIC).opener()
			.t("Backstab (technique)", Stat.DMG, Area.MELEE, 1.5, 0.15, 6).opener().noBoss().t("Feint (technique)", Stat.DMG, Area.MELEE, 0.3, 0.3, 6)
			.t("Blade Dance (technique)", Stat.DMG, Area.MELEE, 0.25, 0.3, 12)
			.c("Deep 2", Stat.TAKEN, Area.ANY, 0.12, 0.3, 0.3, 3).t("Swift 3 · perfect dodge", Stat.TAKEN, Area.ANY, 0.10, 0.4, 8)
			.t("Vanish Step (technique)", Stat.TAKEN, Area.ANY, 0.10, 0.3, 12).p("Swift 2", Stat.MOVE, Area.ANY, 0.08, 3, false)
			.c("Evasion (Stealth)", Stat.TAKEN, Area.ANY, 0.10, 0.4, 0.6, 8);
		athletics(rogue, 0.8);
		Build rogueBefore = new Build().attr("Agility", Stat.RATE, Area.MELEE, 0.015, 0.45).attr("Vitality", Stat.HEALTH, Area.ANY, 0.5, 0.20)
			.p("Hardy (talent)", Stat.HEALTH, Area.ANY, 2, 2, true).p("Weathered Hide (talent)", Stat.TAKEN, Area.ANY, 0.03, 2, true)
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.15, 2, true, Origin.PERK).p("Keen (trait)", Stat.DMG, Area.MELEE, 0.04, 3, true)
			.c("Quick Draw (root)", Stat.DMG, Area.MELEE, 0.60, 0.15, 0.3, 2, true, Origin.PERK).opener()
			.c("Bleed", Stat.DMG, Area.MELEE, 0.15, 0.4, 0.5, 12, false, Origin.PERK)
			.c("Executioner (Mastery)", Stat.DMG, Area.MELEE, 0.5, 0.1, 0.15, 18, false, Origin.PERK).noBoss()
			.c("Ambushing (trait)", Stat.DMG, Area.MELEE, 0.12, 0.2, 0.4, 8, true, Origin.CARD).opener()
			.c("Keen 4", Stat.DMG, Area.MELEE, 0.5, 0.1, 0.2, 8).opener()
			.c("Swift 6 · Slipstream", Stat.DMG, Area.MELEE, 0.3, 0.1, 0.35, 13).need(Need.TOP_SET).opener()
			.c("The Twin Edge (Mythic)", Stat.DMG, Area.MELEE, 0.25, 1, 1, 18).need(Need.MYTHIC)
			.c("Deep 2", Stat.TAKEN, Area.ANY, 0.05, 0.3, 0.3, 3).p("Swift 2", Stat.MOVE, Area.ANY, 0.03, 3, false)
			.p("Runner (root)", Stat.MOVE, Area.ANY, 0.09, 2, true, Origin.PERK);
		ARCHES.add(new Arch("Rogue", Group.SPECIALIST, Own.AMBUSH, "Blades 20, Stealth 20, Athletics 16, Survival 12",
			new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 0, 20}, Area.MELEE, 0, 0.2, true, true, rogue, rogueBefore, List.of()));

		Build mage = new Build().attr("Wits", Stat.DMG, Area.SPELL, 0.02, 0.45).vitality(0.30).talents()
			.p("Focused Will (Arcana root)", Stat.DMG, Area.SPELL, 0.30, 2, true).p("Spellwright (trait)", Stat.DMG, Area.SPELL, 0.14, 3, true)
			.p("Echo 2", Stat.RATE, Area.SPELL, 0.15, 3, false).p("Echo 3 · second charge", Stat.RATE, Area.SPELL, 0.15, 8, false)
			.c("Ember 2", Stat.DMG, Area.SPELL, 0.15, 0.5, 0.7, 3).p("Ember 3 · fire conversion", Stat.DMG, Area.SPELL, 0.10, 8, false)
			.c("Ember 5 · Kindled", Stat.DMG, Area.SPELL, 0.25, 0.3, 0.5, 13).need(Need.TOP_SET)
			.c("Elixir of Focus (Alchemy brew)", Stat.DMG, Area.SPELL, 0.15, 0.5, 0.8, 6).c("Archmage (Mastery)", Stat.DMG, Area.SPELL, 0.3, 0.3, 0.4, 18)
			.t("Overchannel (technique)", Stat.DMG, Area.SPELL, 0.5, 0.3, 12).t("Quickcast (technique)", Stat.RATE, Area.SPELL, 0.2, 0.5, 6)
			.c("The Second Voice (Mythic)", Stat.DMG, Area.SPELL, 0.125, 1, 1, 18).need(Need.MYTHIC)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).t("Mana Shield (technique)", Stat.TAKEN, Area.ANY, 0.30, 0.35, 6)
			.c("Fortify (Alchemy brew)", Stat.TAKEN, Area.ANY, 0.10, 0.5, 0.8, 6).p("Restoratives (Alchemy)", Stat.SUSTAIN, Area.ANY, 0.25, 6, false)
			.c("Healing draughts (Alchemy)", Stat.HPS, Area.ANY, 0.6, 0.4, 0.6, 6);
		ARCHES.add(new Arch("Mage", Group.SPECIALIST, Own.SPELL, "Arcana 20, Alchemy 20, Defense 16, Athletics 12",
			new int[] {20, 20, 16, 12, 8, 6}, new int[] {0, 0, 20, 0}, Area.SPELL, 0, 0.25, true, true, mage, null, List.of()));

		// ---- hybrid pairs: two mains of different disciplines, joined by a bond (§6)
		Build spellblade = new Build().attr("Agility", Stat.RATE, Area.MELEE, 0.015, 0.30).attr("Wits", Stat.DMG, Area.SPELL, 0.02, 0.30).vitality(0.30).talents()
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.30, 2, true).p("Focused Will (Arcana root)", Stat.DMG, Area.SPELL, 0.30, 2, true)
			.p("Keen (trait)", Stat.DMG, Area.MELEE, 0.07, 3, true).p("Spellwright (trait)", Stat.DMG, Area.SPELL, 0.14, 3, true)
			.c("Bleed", Stat.DMG, Area.MELEE, 0.15, 0.4, 0.5, 12).t("Feint (technique)", Stat.DMG, Area.MELEE, 0.3, 0.3, 6)
			.t("Quickcast (technique)", Stat.RATE, Area.SPELL, 0.2, 0.5, 6)
			.c("Bond · Spellblade: Imbue", Stat.DMG, Area.MELEE, 0.25, 0.4, 0.7, 12).c("Bond · Spellblade: Arcane Riposte", Stat.TAKEN, Area.ANY, 0.12, 0.2, 0.5, 15)
			.c("Echo 5 · Reverberate", Stat.DMG, Area.ANY, 0.15, 0.3, 0.5, 13).need(Need.TOP_SET)
			.c("Pair Mythic · The Runed Edge", Stat.DMG, Area.ANY, 0.15, 1, 1, 18).need(Need.MYTHIC)
			.t("Mana Shield (technique)", Stat.TAKEN, Area.ANY, 0.30, 0.35, 6).p("Swift 2", Stat.MOVE, Area.ANY, 0.08, 3, false);
		athletics(spellblade, 0.8);
		ARCHES.add(new Arch("Spellblade", Group.HYBRID, Own.NONE, "Blades 20, Arcana 20, Athletics 16, Alchemy 12", new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 20, 0}, null, 0, 0.2,
			true, true, spellblade, null, List.of("Rogue", "Mage")));

		Build deepwarden = new Build().attr("Strength", Stat.DMG, Area.MELEE, 0.02, 0.35).vitality(0.35).talents()
			.p("Heft (root)", Stat.DMG, Area.MELEE, 0.30, 2, true).p("Brutal 2", Stat.DMG, Area.MELEE, 0.12, 3, false)
			.t("Guard Break (technique)", Stat.DMG, Area.MELEE, 0.15, 0.5, 12)
			.p("Crushing (trait)", Stat.DMG, Area.MELEE, 0.14, 3, true).t("Momentum Swing (technique)", Stat.DMG, Area.MELEE, 0.20, 0.5, 6)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).p("Stout (trait)", Stat.HEALTH, Area.ANY, 3, 6, true)
			.c("Hold the Line", Stat.TAKEN, Area.ANY, 0.15, 0.3, 0.5, 12).t("Perfect Guard (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 6)
			.c("Deep Delver (Mastery)", Stat.TAKEN, Area.ANY, 0.30, 0.4, 0.4, 18).c("Iron 5 · Ironskin", Stat.TAKEN, Area.ANY, 0.12, 0.5, 0.6, 13).need(Need.TOP_SET)
			.c("Bond · Deepwarden: Bedrock", Stat.DMG, Area.MELEE, 0.25, 0.4, 0.6, 12).c("Bond · Deepwarden: Stoneguard", Stat.TAKEN, Area.ANY, 0.15, 0.4, 0.6, 15)
			.c("Pair Mythic · The Mountain's Oath", Stat.TAKEN, Area.ANY, 0.10, 1, 1, 18).need(Need.MYTHIC)
			.c("Bloodthirsty (trait)", Stat.HPS, Area.ANY, 0.5, 0.5, 0.6, 8)
			.c("Deep 3 · first hit from the dark", Stat.DMG, Area.MELEE, 0.5, 0.2, 0.3, 8).opener();
		mining(deepwarden, 1.0);
		ARCHES.add(new Arch("Deepwarden", Group.HYBRID, Own.NONE, "Defense 20, Mining 20, Heavy Weapons 16, Smithing 12", new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 0, 0}, null, 12, 0,
			true, true, deepwarden, null, List.of("Warrior")));

		Build shadowbow = new Build().attr("Agility", Stat.RATE, Area.RANGED, 0.015, 0.45).vitality(0.30).talents()
			.p("Steady Aim (root)", Stat.RATE, Area.RANGED, 0.40, 2, true).p("Heavy-tipped (trait)", Stat.DMG, Area.RANGED, 0.11, 3, true)
			.c("Long Shot", Stat.DMG, Area.RANGED, 0.30, 0.4, 0.6, 6).t("Charged Arrow (technique)", Stat.DMG, Area.RANGED, 0.5, 0.4, 6)
			.c("Bond · Shadowbow: Unseen Shot", Stat.DMG, Area.RANGED, 0.6, 0.15, 0.25, 12).opener()
			.c("Keen 5 · Weak Point", Stat.DMG, Area.RANGED, 0.25, 0.3, 0.6, 13).need(Need.TOP_SET)
			.c("Pair Mythic · Nightfall Quiver", Stat.DMG, Area.RANGED, 0.15, 1, 1, 18).need(Need.MYTHIC)
			.t("Vanish Step (technique)", Stat.TAKEN, Area.ANY, 0.10, 0.3, 12).c("Bond · Shadowbow: Slip Away", Stat.TAKEN, Area.ANY, 0.12, 0.3, 0.5, 15)
			.p("Swift 2", Stat.MOVE, Area.ANY, 0.08, 3, false);
		survival(shadowbow, 0.8);
		athletics(shadowbow, 0.5);
		ARCHES.add(new Arch("Shadowbow", Group.HYBRID, Own.NONE, "Stealth 20, Marksmanship 20, Survival 16, Athletics 12", new int[] {20, 20, 16, 12, 8, 6}, new int[] {0, 20, 0, 20}, null, 0, 0.4,
			true, true, shadowbow, null, List.of("Ranger", "Rogue")));

		Build battlesmith = new Build().attr("Strength", Stat.DMG, Area.MELEE, 0.02, 0.45).vitality(0.30).talents()
			.p("Heft (root)", Stat.DMG, Area.MELEE, 0.30, 2, true).p("Keen Edge (forged)", Stat.DMG, Area.MELEE, 0.10, 6, false)
			.p("Brutal 2", Stat.DMG, Area.MELEE, 0.12, 3, false).t("Momentum Swing (technique)", Stat.DMG, Area.MELEE, 0.20, 0.5, 6)
			.c("Bond · Battlesmith: Field Whetstone", Stat.DMG, Area.MELEE, 0.15, 0.8, 0.9, 12)
			.c("Ember 5 · Kindled", Stat.DMG, Area.MELEE, 0.25, 0.3, 0.5, 13).need(Need.TOP_SET)
			.c("Pair Mythic · The Anvil's Voice", Stat.DMG, Area.MELEE, 0.12, 1, 1, 18).need(Need.MYTHIC)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).p("Proofed armour (Smithing)", Stat.TAKEN, Area.ANY, 0.08, 6, false)
			.c("Bond · Battlesmith: Riveted", Stat.TAKEN, Area.ANY, 0.10, 0.6, 0.6, 15).t("Perfect Guard (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 6)
			.c("Hold the Line", Stat.TAKEN, Area.ANY, 0.15, 0.3, 0.5, 12);
		mining(battlesmith, 0.8);
		battlesmith.c("Bond · Battlesmith: Field Repairs (gear never wears out mid-fight)", Stat.SUSTAIN, Area.ANY, 0.20, 1, 1, 12);
		ARCHES.add(new Arch("Battlesmith", Group.HYBRID, Own.NONE, "Heavy Weapons 20, Smithing 20, Defense 16, Mining 12", new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 0, 0}, null, 20, 0,
			true, true, battlesmith, null, List.of("Warrior")));

		Build venomblade = new Build().attr("Agility", Stat.RATE, Area.MELEE, 0.015, 0.40).vitality(0.30).talents()
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.30, 2, true).p("Keen (trait)", Stat.DMG, Area.MELEE, 0.07, 3, true)
			.c("Quick Draw (root)", Stat.DMG, Area.MELEE, 0.70, 0.15, 0.3, 2, true, Origin.CARD).opener()
			.p("Venom (Alchemy: poison status on every coated hit)", Stat.DMG, Area.SPELL, 0.30, 6, true).c("Blade coatings (Alchemy brew)", Stat.DMG, Area.ANY, 0.25, 0.6, 0.8, 6)
			.c("Bond · Venomblade: Venom Edge", Stat.DMG, Area.ANY, 0.30, 0.5, 0.7, 12).c("Wild 5 · Feral (statuses tick faster)", Stat.DMG, Area.ANY, 0.20, 0.5, 0.6, 13).need(Need.TOP_SET)
			.c("Pair Mythic · The Green Vial", Stat.DMG, Area.ANY, 0.12, 1, 1, 18).need(Need.MYTHIC)
			.c("Fortify (Alchemy brew)", Stat.TAKEN, Area.ANY, 0.10, 0.5, 0.8, 6).c("Healing draughts (Alchemy)", Stat.HPS, Area.ANY, 0.6, 0.4, 0.6, 6)
			.p("Restoratives (Alchemy)", Stat.SUSTAIN, Area.ANY, 0.25, 6, false).t("Feint (technique)", Stat.DMG, Area.MELEE, 0.3, 0.3, 6)
			.c("Bond · Venomblade: Antivenom", Stat.TAKEN, Area.ANY, 0.10, 0.4, 0.4, 15).p("Mithridate (Alchemy)", Stat.TAKEN, Area.ANY, 0.10, 6, false)
			.t("Smoke Bomb (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 12).t("Toxic Bloom (technique)", Stat.DMG, Area.ANY, 0.3, 0.4, 12);
		survival(venomblade, 0.5);
		ARCHES.add(new Arch("Venomblade", Group.HYBRID, Own.NONE, "Blades 20, Alchemy 20, Survival 16, Stealth 12", new int[] {20, 20, 16, 12, 8, 6}, new int[] {20, 0, 20, 12}, null, 0, 0.15, true, true,
			venomblade, null, List.of("Rogue")));

		// ---- a balanced fighter: six skills at 16, no skill at 20 (so no Mastery cards and fewer shards: no Mythic)
		Build balanced = new Build().attr("Strength", Stat.DMG, Area.MELEE, 0.02, 0.25).attr("Agility", Stat.RATE, Area.ANY, 0.015, 0.25).vitality(0.25).talents()
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.30, 2, true).p("Steady Aim (root)", Stat.RATE, Area.RANGED, 0.40, 2, true)
			.c("Long Shot", Stat.DMG, Area.RANGED, 0.30, 0.4, 0.6, 6).c("Bleed", Stat.DMG, Area.MELEE, 0.15, 0.4, 0.5, 12)
			.t("Feint (technique)", Stat.DMG, Area.MELEE, 0.3, 0.3, 6).t("Charged Arrow (technique)", Stat.DMG, Area.RANGED, 0.5, 0.4, 6)
			.p("Keen 2", Stat.DMG, Area.ANY, 0.08, 3, false).c("Keen 5 · Weak Point", Stat.DMG, Area.ANY, 0.25, 0.3, 0.6, 13).need(Need.TOP_SET)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).c("Hold the Line", Stat.TAKEN, Area.ANY, 0.15, 0.3, 0.5, 12)
			.t("Perfect Guard (technique)", Stat.TAKEN, Area.ANY, 0.15, 0.4, 6).c("Bond · Sword and Board", Stat.TAKEN, Area.ANY, 0.10, 0.4, 0.5, 15);
		athletics(balanced, 0.8);
		survival(balanced, 0.6);
		mining(balanced, 0.5);
		ARCHES.add(new Arch("Balanced fighter", Group.BALANCED, Own.NONE, "Blades, Marksmanship, Defense, Athletics, Survival, Mining at 16",
			new int[] {16, 16, 16, 16, 16, 16}, new int[] {16, 16, 0, 0}, null, 0, 0.25, true, false, balanced, null, List.of()));

		// ---- the Wayfarer: nine skills at 13-17, talents and the Wayfarer keyword (decks counted, not cards)
		Build wayfarer = new Build().attr("Agility", Stat.RATE, Area.ANY, 0.015, 0.25).vitality(0.30).talents()
			.p("Fleet (talent)", Stat.MOVE, Area.ANY, 0.06, 2, true).p("Broad Shoulders (talent)", Stat.CARRY, Area.ANY, 10, 2, true)
			.p("Iron Gut (talent)", Stat.SUSTAIN, Area.ANY, 0.12, 2, true).p("Scavenger (talent)", Stat.YIELD, Area.ANY, 0.06, 2, true)
			.p("Wayfarer 3 · Roadwise", Stat.MOVE, Area.ANY, 0.08, 6, false).p("Wayfarer 5 · Many Hands", Stat.GATHER, Area.ANY, 0.30, 12, false)
			.p("Wayfarer 5 · Many Hands", Stat.YIELD, Area.ANY, 0.10, 12, false)
			.c("Wayfarer 7 · Wanderer's Grace", Stat.DMG, Area.ANY, 0.20, 0.5, 0.6, 15).need(Need.TOP_SET)
			.c("Wayfarer 7 · Wanderer's Grace", Stat.TAKEN, Area.ANY, 0.15, 0.5, 0.6, 15).need(Need.TOP_SET)
			.p("Steady Aim (root)", Stat.RATE, Area.RANGED, 0.40, 2, true).c("Long Shot", Stat.DMG, Area.RANGED, 0.30, 0.4, 0.6, 6)
			.p("Fencer (root)", Stat.RATE, Area.MELEE, 0.30, 2, true).t("Charged Arrow (technique)", Stat.DMG, Area.RANGED, 0.5, 0.4, 6)
			.p("Thick Skin (root)", Stat.TAKEN, Area.ANY, 0.12, 2, true).c("Bond · Trailblazer", Stat.MOVE, Area.ANY, 0.08, 0.6, 0.6, 12)
			.c("Long Walker (deed Mythic)", Stat.MOVE, Area.ANY, 0.10, 0.6, 0.6, 18).need(Need.MYTHIC)
			.c("Living off the Land (Mastery)", Stat.SUSTAIN, Area.ANY, 0.25, 1, 1, 17)
			.c("Healing herbs (Survival)", Stat.HPS, Area.ANY, 0.4, 0.4, 0.5, 8);
		athletics(wayfarer, 1.0);
		survival(wayfarer, 1.0);
		mining(wayfarer, 0.8);
		ARCHES.add(new Arch("Wayfarer", Group.WAYFARER, Own.ROAD, "Athletics 17, Survival 17, Mining 16, Smithing 15, Marksmanship 15, Blades 14, Speech 14, Stealth 13, Defense 13",
			new int[] {17, 17, 16, 15, 15, 14, 14, 13, 13}, new int[] {14, 15, 0, 13}, null, 15, 0.3, true, true, wayfarer, null, List.of()));
	}

	/** Progression points: (character level, depth, a top set, a Mythic), a focused character as BUILD_FEEL.md §13.3 paces it. */
	static final int[][] PROGRESSION = {{5, 3, 0, 0}, {12, 7, 0, 0}, {22, 12, 0, 0}, {30, 16, 1, 0}, {38, 20, 0, 0}, {38, 20, 1, 0}, {50, 20, 1, 1}};

	// ================================================================ §2 floors (data)

	/** Felt steps: the least change a player feels, per stat (§2.1). */
	record Root(String perk, String stat, double felt, double[] after, boolean inverse) {
	}

	static final List<Root> ROOTS = List.of(
		new Root("athletics/pack_mule", "carry kg", 10, new double[] {10, 16, 24}, false),
		new Root("athletics/runner", "sprint speed %", 8, new double[] {8, 12, 16}, false),
		new Root("blades/fencer", "swing speed %", 15, new double[] {15, 22, 30}, false),
		new Root("blades/quick_draw", "first-hit damage %", 15, new double[] {30, 50, 70}, false),
		new Root("defense/armour_training", "armour weight %", 15, new double[] {25, 40, 55}, false),
		new Root("defense/thick_skin", "armour points", 3, new double[] {3, 4, 5}, false),
		new Root("heavy_weapons/feller", "chop speed %", 15, new double[] {25, 45, 65}, false),
		new Root("heavy_weapons/heft", "heavy damage %", 15, new double[] {15, 22, 30}, false),
		new Root("marksmanship/retrieval", "arrow back %", 15, new double[] {25, 40, 55}, false),
		new Root("marksmanship/steady_aim", "draw speed %", 15, new double[] {20, 30, 40}, false),
		new Root("mining/prospector", "extra ore %", 15, new double[] {15, 25, 35}, false),
		new Root("mining/quarryman", "stone dig speed %", 15, new double[] {20, 35, 50}, false),
		new Root("smithing/thrifty", "ingot back %", 15, new double[] {15, 25, 35}, false),
		new Root("smithing/tinker", "anvil levels off", 2, new double[] {2, 3, 4}, false),
		new Root("stealth/shadow", "noticed closer, %", 15, new double[] {80, 70, 60}, true),
		new Root("stealth/soft_step", "quieter %", 15, new double[] {20, 35, 50}, false),
		new Root("survival/field_cook", "cooking %", 15, new double[] {20, 35, 50}, false),
		new Root("survival/gatherer", "bonus plant %", 15, new double[] {20, 35, 50}, false));

	/** Trait floors after the retune (§2.2): broad 8 %, narrow 15 %; flats ×1.5. */
	static final double BROAD_FLOOR = 8;
	static final double NARROW_FLOOR = 15;
	static final double BROAD_MAX = 18;

	static double[] traitAfter(Trait t) {
		if (!"percent".equals(t.unit())) {
			return new double[] {t.min() * 1.5, t.max() * 1.5};
		}
		boolean broad = t.scope() == Trait.Scope.BROAD;
		double min = Math.max(Math.round(t.min() * 1.5), broad ? BROAD_FLOOR : NARROW_FLOOR);
		double max = Math.max(Math.round(t.max() * 1.35), Math.round(min * 1.5));
		if (broad) {
			max = Math.min(BROAD_MAX, max);
		}
		return new double[] {min, max};
	}

	/** Set 2-tiers before and after (§5), as {felt step, before, after} in the set's own unit. */
	static final Map<String, double[]> SET_TWO = new LinkedHashMap<>();

	static {
		SET_TWO.put("ember: fire taken less %", new double[] {15, 15, 35});
		SET_TWO.put("deep: dig faster below y 0 %", new double[] {15, 5, 20});
		SET_TWO.put("swift: movement %", new double[] {8, 3, 8});
		SET_TWO.put("brutal: melee damage %", new double[] {10, 4, 12});
		SET_TWO.put("echo: cooldowns shorter %", new double[] {15, 10, 15});
		SET_TWO.put("warden: armour", new double[] {3, 1, 3});
		SET_TWO.put("keen: crit damage %", new double[] {15, 8, 15});
		SET_TWO.put("wild: bonus drops %", new double[] {15, 10, 20});
		SET_TWO.put("iron: armour toughness", new double[] {2, 0, 2});
		SET_TWO.put("tide: swim speed %", new double[] {15, 10, 25});
		SET_TWO.put("gale: fall blocks", new double[] {2, 1.5, 3});
		SET_TWO.put("hoard: weight less %", new double[] {10, 5, 12});
		SET_TWO.put("hex: cursed downside smaller %", new double[] {30, 25, 40});
		SET_TWO.put("kin: allies' XP and stamina regen %", new double[] {10, 5, 10});
	}

	// ================================================================ §8 the stat contract

	/** One stat the other mods read: id, unit, the range by band (I, II, III, IV, Apex, Legend), PvP cap, reader. */
	record ContractStat(String id, String unit, double[] range, double pvp, String reader, boolean lowerIsStronger) {
	}

	static final List<ContractStat> CONTRACT = List.of(
		new ContractStat("mettle:stamina_max", "+ points (Fray's base 100)", new double[] {5, 20, 40, 60, 70, 80}, 30, "Fray (pool), Bestiary (actions)", false),
		new ContractStat("mettle:stamina_regen", "× regen", new double[] {1.10, 1.20, 1.30, 1.40, 1.45, 1.50}, 1.25, "Fray, Bestiary", false),
		new ContractStat("mettle:stamina_cost.dodge", "× cost (floor)", new double[] {0.95, 0.90, 0.80, 0.72, 0.68, 0.65}, 0.85, "Fray, Bestiary", true),
		new ContractStat("mettle:stamina_cost.parry|block|heavy|charged|sprint", "× cost (floor)", new double[] {0.90, 0.80, 0.70, 0.60, 0.55, 0.50}, 0.75, "Fray, Bestiary", true),
		new ContractStat("mettle:stamina_refund", "share of an action's cost, on a perfect timing", new double[] {0.20, 0.30, 0.40, 0.50, 0.50, 0.50}, 0.25, "Fray, Bestiary", false),
		new ContractStat("mettle:dodge_iframes", "+ ms", new double[] {20, 40, 60, 80, 90, 100}, 40, "Fray, Bestiary", false),
		new ContractStat("mettle:parry_window", "+ ms", new double[] {20, 40, 60, 80, 90, 100}, 40, "Fray, Bestiary", false),
		new ContractStat("mettle:charge_speed", "× windup speed", new double[] {1.10, 1.20, 1.30, 1.40, 1.45, 1.50}, 1.20, "Fray, Bestiary, Arsenal", false),
		new ContractStat("mettle:poise", "+ % stagger resistance", new double[] {10, 25, 40, 60, 70, 80}, 30, "Fray, Bestiary", false),
		new ContractStat("mettle:stagger_power", "+ % poise damage dealt (bosses take half)", new double[] {10, 25, 40, 60, 70, 80}, 30, "Fray, Bestiary", false),
		new ContractStat("mettle:melee_power | ranged_power | spell_power", "× hit (the band's offense ceiling)", new double[] {1.25, 1.45, 1.70, 2.00, 2.15, 2.30}, PVP_OFFENSE, "Fray (damage pipeline), Arsenal, Alembic", false),
		new ContractStat("mettle:crit_chance", "+ points", new double[] {5, 10, 15, 20, 22, 25}, 10, "Fray (crits beyond the jump crit)", false),
		new ContractStat("mettle:crit_damage", "+ %", new double[] {15, 25, 35, 50, 55, 60}, 25, "Fray, Bestiary", false),
		new ContractStat("mettle:damage.<type>", "+ % (slash pierce blunt fire frost shock poison arcane), inside the offense ceiling", new double[] {15, 25, 35, 45, 50, 55}, 20, "Fray (damage pipeline), Arsenal, Alembic", false),
		new ContractStat("mettle:resist.<type>", "% less of that type, inside the taken ceiling", new double[] {15, 25, 35, 45, 50, 55}, 30, "Fray", false),
		new ContractStat("mettle:status_chance.<status>", "+ points (bleed burn chill shock poison stagger)", new double[] {5, 10, 15, 20, 25, 30}, 10, "Fray (statuses), Arsenal, Alembic", false),
		new ContractStat("mettle:status_power", "× status damage / strength", new double[] {1.10, 1.20, 1.30, 1.40, 1.45, 1.50}, 1.15, "Fray (statuses), Alembic", false),
		new ContractStat("mettle:status_taken", "× duration of harmful statuses on you (floor)", new double[] {0.90, 0.80, 0.70, 0.60, 0.55, 0.50}, 0.80, "Fray (statuses), Alembic", true),
		new ContractStat("mettle:draw_speed", "× bow draw / crossbow load", new double[] {1.20, 1.30, 1.40, 1.50, 1.55, 1.60}, 1.25, "vanilla bows, Arsenal", false),
		new ContractStat("mettle:projectile_velocity", "× speed", new double[] {1.10, 1.15, 1.25, 1.30, 1.35, 1.40}, 1.15, "vanilla, Arsenal, Alembic", false),
		new ContractStat("mettle:projectile_spread", "× spread (floor)", new double[] {0.85, 0.70, 0.55, 0.45, 0.40, 0.35}, 0.70, "vanilla, Arsenal", true),
		new ContractStat("mettle:ammo_save", "chance an arrow or charge isn't spent", new double[] {0.10, 0.20, 0.25, 0.30, 0.35, 0.40}, 0.20, "vanilla, Arsenal", false),
		new ContractStat("mettle:mana_max", "+ points (Fray's base 40)", new double[] {10, 50, 110, 160, 180, 200}, 80, "Fray (pool), Alembic (spells)", false),
		new ContractStat("mettle:mana_regen", "× regen", new double[] {1.10, 1.20, 1.30, 1.40, 1.50, 1.60}, 1.25, "Fray (pool)", false),
		new ContractStat("minecraft:max_health (Mettle's modifiers)", "+ health points (the overview's 22 / 26 / 32 / 38 / 39 / 40)", new double[] {2, 6, 12, 18, 19, 20}, PVP_HEALTH, "vanilla", false),
		new ContractStat("mettle:spell_cost", "× mana cost (floor)", new double[] {0.95, 0.90, 0.80, 0.70, 0.65, 0.60}, 0.85, "Alembic (spells)", true),
		new ContractStat("mettle:cast_speed", "× cast and channel speed", new double[] {1.10, 1.15, 1.25, 1.30, 1.35, 1.40}, 1.15, "Alembic (spells)", false),
		new ContractStat("mettle:cooldown_scale", "× cooldowns (floor; Echo and Reverberate read it)", new double[] {0.95, 0.85, 0.75, 0.65, 0.62, 0.60}, 0.85, "Mettle, Arsenal, Alembic, Bestiary", true),
		new ContractStat("mettle:brew_strength", "× a brew's continuous numbers", new double[] {1.10, 1.20, 1.30, 1.40, 1.45, 1.50}, 1.15, "Alembic (brews)", false),
		new ContractStat("mettle:brew_amplifier", "+ levels (whole; never past the brew's max + 1)", new double[] {0, 0, 1, 1, 1, 1}, 0, "Alembic (brews)", false),
		new ContractStat("mettle:brew_duration", "× duration", new double[] {1.20, 1.40, 1.60, 1.80, 1.90, 2.00}, 1.30, "Alembic (brews)", false),
		new ContractStat("mettle:brew_doses", "+ doses per brew", new double[] {0, 1, 1, 2, 2, 2}, 2, "Alembic (brews)", false),
		new ContractStat("mettle:healing_received", "× healing", new double[] {1.05, 1.10, 1.20, 1.30, 1.35, 1.40}, 1.10, "everyone who heals", false),
		new ContractStat("mettle:combat_sustain", "most Mettle healing a second in a fight (hp/s; bosses half)", new double[] {0.5, 0.6, 0.8, 1.0, 1.0, 1.0}, 0.4, "Mettle (Fray marks boss fights)", false));

	// ================================================================ §9 fairness: bosses, sustain, loops (assumptions §1.2)

	static final double BOSS_HP = 600;
	static final double BOSS_HIT = 14;
	static final double BOSS_HIT_EVERY = 2.5;
	static final double BOSS_SLAM = 24;
	static final double BOSS_SLAM_EVERY = 12;
	/** A geared player's damage a second on the boss (netherite-class weapon, after the boss's armour 10). */
	static final double GEARED_DPS = 8;
	static final double ARMOUR = 20;
	static final double TOUGHNESS = 8;

	static double afterArmour(double damage, double armour, double toughness) {
		double effective = Math.min(20, Math.max(armour / 5, armour - 4 * damage / (toughness + 8)));
		return damage * (1 - effective / 25);
	}

	/** Seconds a face-tanking player lives against the boss, and seconds the boss takes to fall. */
	static double[] bossFight(Arch a, boolean geared) {
		int d = depth(a.levels);
		Result boss = compute(a, Rules.AFTER, 50, d, a.topSet, a.mythic, true, Mode.BOSS);
		double armour = geared ? ARMOUR : 0;
		double tough = geared ? TOUGHNESS : 0;
		double incoming = (afterArmour(BOSS_HIT, armour, tough) / BOSS_HIT_EVERY + afterArmour(BOSS_SLAM, armour, tough) / BOSS_SLAM_EVERY) * boss.taken;
		double net = Math.max(1e-6, incoming - boss.hps);
		double lives = (20 + boss.health) / net;
		double ttk = BOSS_HP / (GEARED_DPS * boss.best());
		double hitsNaked = (20 + boss.health) / (BOSS_HIT * boss.taken);
		return new double[] {lives, ttk, incoming, boss.hps, hitsNaked};
	}

	// stamina model (the combat mod's base numbers, assumed: §1.2)
	static final double STAMINA = 100;
	static final double STAMINA_REGEN = 25;
	static final double DODGE_COST = 25;
	static final double DODGE_EVERY = 0.4;

	// ================================================================ the tests

	private final List<String> problems = new ArrayList<>();

	private void check(boolean ok, String what) {
		if (!ok) {
			this.problems.add(what);
		}
	}

	@Test
	void curveHolds() {
		String text = curve(new StringBuilder()).toString();
		System.out.println(text);
		assertTrue(this.problems.isEmpty(), String.join(System.lineSeparator(), this.problems));
	}

	@Test
	void report() throws IOException {
		Assumptions.assumeTrue(Boolean.getBoolean("mettle.balance"), "Mythic power report: run with -Dmettle.balance=true");
		StringBuilder out = new StringBuilder("# Mythic power simulation (docs/MYTHIC_POWER.md)\n\n");
		curve(out);
		slotStudy(out);
		String text = out.toString();
		Path file = Path.of("build", "reports", "mettle-mythic-power.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		System.out.println(text);
		assertTrue(this.problems.isEmpty(), String.join(System.lineSeparator(), this.problems));
	}

	private StringBuilder curve(StringBuilder out) {
		floors(out);
		bands(out);
		progression(out);
		archetypes(out);
		fairness(out);
		contract(out);
		return out;
	}

	// ---------------------------------------------------------------- §2 floors

	private void floors(StringBuilder out) {
		out.append("## 1. Floors: every card felt on slotting (§2)\n\n| Root | stat | felt step | today I / II / III | after | today's I felt? |\n|---|---|---|---|---|---|\n");
		int feltBefore = 0;
		Map<String, double[]> rootBefore = new HashMap<>();
		for (Root r : ROOTS) {
			Perk p = TestCards.def(TestCards.perk(r.perk));
			double[] v = p.rankValues().stream().mapToDouble(Double::parseDouble).toArray();
			rootBefore.put(r.perk, v);
			double eff = r.inverse ? 100 - v[0] : v[0];
			double effAfter = r.inverse ? 100 - r.after[0] : r.after[0];
			boolean felt = eff >= r.felt - 1e-9;
			feltBefore += felt ? 1 : 0;
			check(effAfter >= r.felt - 1e-9, r.perk + ": a Common copy is not felt after the retune");
			check(r.after[0] < r.after[1] == !r.inverse && r.after[1] < r.after[2] == !r.inverse, r.perk + ": ranks must grow");
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %s |%n", r.perk, r.stat, fmt(r.felt), fmt(v[0]) + " / " + fmt(v[1]) + " / " + fmt(v[2]),
				fmt(r.after[0]) + " / " + fmt(r.after[1]) + " / " + fmt(r.after[2]), felt ? "yes" : "**no**"));
		}
		check(ROOTS.size() == TestCards.PERKS.values().stream().filter(p -> p.kind() == Perk.Kind.ROOT).count(), "every root has an after value");
		out.append(String.format(Locale.ROOT, "%nRoots felt at rank I: today **%d of %d**, after **%d of %d**.%n%n", feltBefore, ROOTS.size(), ROOTS.size(), ROOTS.size()));

		// Traits: share of traits whose lowest roll (a Common's) is under the floor.
		int under = 0;
		int underAfter = 0;
		int n = 0;
		double meanBefore = 0;
		double meanAfter = 0;
		for (Trait t : TestCards.TRAITS.values()) {
			if (!"percent".equals(t.unit())) {
				continue;
			}
			n++;
			double floor = t.scope() == Trait.Scope.BROAD ? BROAD_FLOOR : NARROW_FLOOR;
			double[] after = traitAfter(t);
			under += t.min() < floor ? 1 : 0;
			underAfter += after[0] < floor - 1e-9 ? 1 : 0;
			meanBefore += (t.min() + t.max()) / 2;
			meanAfter += (after[0] + after[1]) / 2;
			check(after[0] >= floor - 1e-9, "trait " + t.name().getString() + " rolls under its floor after the retune");
			check(t.scope() != Trait.Scope.BROAD || after[1] <= BROAD_MAX, "broad trait over " + BROAD_MAX + " %");
		}
		out.append(String.format(Locale.ROOT, "Percent traits (%d): lowest roll under the floor (broad %s %%, narrow %s %%) today **%d**, after **%d**; "
			+ "mean of a range today %.1f %%, after %.1f %%. Flat traits ×1.5.%n%n", n, fmt(BROAD_FLOOR), fmt(NARROW_FLOOR), under, underAfter, meanBefore / n, meanAfter / n));

		// Dealt cards: how many cards dealt at each skill band are felt by their perk alone.
		int[][] felt = new int[3][2];
		int[] dealt = new int[3];
		int runs = 400;
		for (String name : TestCards.RELEASED) {
			ResourceKey<Skill> skill = TestCards.skill(name);
			for (int run = 0; run < runs; run++) {
				TestCards.Sheet sheet = new TestCards.Sheet();
				long seed = new Random(run * 1_000_003L + 77).nextLong();
				int n2 = 0;
				for (int level = 2; level <= 20; level++) {
					sheet.levels.put(skill, level);
					if (DraftRules.draftsFor(level - 1, level) == 0) {
						continue;
					}
					List<Card> hand = CardRules.deal(skill, sheet, TestCards.DECK, TestCards.DATA_SET, DraftRules.random(seed, n2++), List.of(), Set.of());
					int bandIdx = level < 6 ? 0 : level < 12 ? 1 : 2;
					for (Card c : hand) {
						Perk p = TestCards.def(c.perk());
						dealt[bandIdx]++;
						boolean before = true;
						boolean after = true;
						if (p.kind() == Perk.Kind.ROOT) {
							String key = c.perk().identifier().getPath();
							Root r = ROOTS.stream().filter(x -> x.perk.equals(key)).findFirst().orElseThrow();
							double vb = rootBefore.get(key)[c.rank() - 1];
							double va = r.after[c.rank() - 1];
							before = (r.inverse ? 100 - vb : vb) >= r.felt - 1e-9;
							after = (r.inverse ? 100 - va : va) >= r.felt - 1e-9;
						}
						felt[bandIdx][0] += before ? 1 : 0;
						felt[bandIdx][1] += after ? 1 : 0;
					}
					if (!hand.isEmpty()) {
						sheet.owned.merge(hand.getFirst().perk(), 1, Integer::sum);
					}
				}
			}
		}
		out.append("Cards dealt in real hands (the game's deal, " + runs + " careers a skill, all released skills), felt by their perk alone:\n\n"
			+ "| Skill band | today | after |\n|---|---|---|\n");
		String[] names = {"2-5", "6-11", "12-20"};
		for (int i = 0; i < 3; i++) {
			out.append(String.format(Locale.ROOT, "| %s | %.0f %% | %.0f %% |%n", names[i], 100.0 * felt[i][0] / dealt[i], 100.0 * felt[i][1] / dealt[i]));
			check(felt[i][1] == dealt[i], "a dealt card is not felt after the retune (skill " + names[i] + ")");
		}
		int setFelt = 0;
		int setFeltAfter = 0;
		out.append("\nSet 2-tiers (felt step · today · after): ");
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, double[]> e : SET_TWO.entrySet()) {
			double[] v = e.getValue();
			setFelt += v[1] >= v[0] ? 1 : 0;
			setFeltAfter += v[2] >= v[0] ? 1 : 0;
			check(v[2] >= v[0], "set 2-tier not felt: " + e.getKey());
			parts.add(e.getKey() + " " + fmt(v[0]) + " · " + fmt(v[1]) + " · " + fmt(v[2]));
		}
		out.append(String.join("; ", parts)).append(String.format(Locale.ROOT, ".%n%nSet 2-tiers felt: today **%d of 14**, after **%d of 14**.%n%n", setFelt, setFeltAfter));
	}

	// ---------------------------------------------------------------- §3 bands

	private void bands(StringBuilder out) {
		out.append("## 2. The power bands (§3)\n\n| Band | depth | offense: always / conditions / techniques | taken: always / conditions / techniques | health | movement | dig | "
			+ "extra drops | fight power: plain play / full | PvP |\n|---|---|---|---|---|---|---|---|---|---|\n");
		Band prev = null;
		for (Band b : Band.values()) {
			out.append(String.format(Locale.ROOT, "| %s | %s | ×%.2f / ×%.2f / ×%.2f | %.0f / %.0f / %.0f %% | +%s | +%.0f %% | +%.0f %% | +%.0f %% | %.2f× / **%.2f×** | ×%.2f, %.0f %%, +%s |%n",
				b.label, b == Band.APEX ? "IV + a Mythic or a top set (three best skills 18+)" : b == Band.LEGEND ? "IV + both" : b == Band.I ? "0-2" : String.valueOf(b.depth),
				b.offPassive, b.offCond, b.offFull, 100 * b.takenPassive, 100 * b.takenCond, 100 * b.takenFull, fmt(b.health), 100 * b.move, 100 * b.gather,
				100 * b.yield, b.plainCap(), b.fightCap(), PVP_OFFENSE, 100 * PVP_TAKEN, fmt(PVP_HEALTH)));
			check(b.offPassive < b.offFull, b + ": the passive ceiling must sit under the full one");
			check(b.offFull - b.offPassive >= (b.ordinal() >= 3 ? 0.4 : 0.05), b + ": too little of the ceiling earned in the fight");
			if (prev != null) {
				check(b.offFull > prev.offFull && b.takenFull < prev.takenFull && b.health >= prev.health && b.move > prev.move && b.fightCap() > prev.fightCap(),
					b + ": bands must grow");
			}
			prev = b;
		}
		check(Band.I.fightCap() <= 1.35, "band I too strong: " + Band.I.fightCap());
		check(Band.IV.fightCap() >= 2.0, "Master band under 2×: " + Band.IV.fightCap());
		check(Band.LEGEND.fightCap() >= 2.3 && Band.LEGEND.fightCap() <= 2.6, "Legend band off 2.3-2.6×: " + Band.LEGEND.fightCap());
		double pvpCap = Math.sqrt(PVP_OFFENSE * (20 + PVP_HEALTH) / 20 / PVP_TAKEN);
		check(pvpCap <= 1.5, "PvP cap too high: " + pvpCap);
		out.append(String.format(Locale.ROOT, "%nFight power = √(offense × toughness), toughness = (20 + health) ÷ 20 ÷ taken; a PvP fight caps at **%.2f×**. "
			+ "Depth = the better of the three best skills' mean and the six best skills' mean + 3; between the anchors (I at depth 2, II at 6, III at 12, "
			+ "IV at 18) every ceiling rises with each point of depth.%n%n", pvpCap));
	}

	// ---------------------------------------------------------------- §3.3 the curve by progression

	private void progression(StringBuilder out) {
		out.append("## 3. The curve by progression: specialists, skilled play (plain play in brackets); today → after\n\n"
			+ "| Character level · depth · state | band | Warrior | Ranger | Rogue | Mage |\n|---|---|---|---|---|---|\n");
		for (int[] pt : PROGRESSION) {
			int cl = pt[0];
			int d = pt[1];
			boolean six = pt[2] == 1;
			boolean myth = pt[3] == 1;
			Caps band = caps(d, six, myth);
			StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "| %d · %d · %s | %s |", cl, d, six && myth ? "top set + Mythic" : six ? "top set" : myth ? "Mythic" : "—",
				band.label));
			for (String name : List.of("Warrior", "Ranger", "Rogue", "Mage")) {
				Arch a = arch(name);
				Result after = compute(a, Rules.AFTER, cl, d, six, myth, true, Mode.NORMAL);
				Result plain = compute(a, Rules.AFTER, cl, d, six, myth, false, Mode.NORMAL);
				double pa = fight(a, after);
				double pp = fight(a, plain);
				String before = "—";
				if (a.before != null) {
					Result b = compute(a, Rules.BEFORE, cl, d, six, myth, true, Mode.NORMAL);
					before = String.format(Locale.ROOT, "%.2f", fight(a, b));
				}
				row.append(String.format(Locale.ROOT, " %s → **%.2f** (%.2f) |", before, pa, pp));
				check(pa <= band.fightCap() * (1 + a.kite * band.move) + 1e-9, name + " passes its band's cap at level " + cl);
				if (cl == 5) {
					check(pa >= 1.10 && pa <= 1.35, name + " at level 5 should feel the cards but stay modest: " + pa);
				}
				if (cl == 50) {
					check(pa >= 2.0, name + " at level 50 should reach 2×: " + pa);
					check(pp <= 0.92 * pa, name + " plain play gets too close to skilled play at 50: " + pp + " vs " + pa);
				}
			}
			out.append(row).append("\n");
		}
		out.append("\n(\"Today\" counts today's numbers: the §14 ceilings clamp only what cards add; perks and attributes sit outside them, which is why today's "
			+ "top end is uneven. The model's uptimes, techniques and the assumptions behind them are in docs/MYTHIC_POWER.md §1.2 and §10.)\n\n");
	}

	// ---------------------------------------------------------------- §10 archetypes

	private void archetypes(StringBuilder out) {
		out.append("## 4. Archetypes at character level 50 (skilled play; fight power ×vanilla)\n\n"
			+ "| Archetype | group | skills | band | melee | ranged | spell | ambush | **combat** | boss | road | **general** | PvP |\n"
			+ "|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		Map<String, Scores> s = new LinkedHashMap<>();
		for (Arch a : ARCHES) {
			Scores sc = scores(a, 50);
			s.put(a.name, sc);
			out.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %.2f | %.2f | %.2f | %.2f | **%.2f** | %.2f | %.2f | **%.2f** | %.2f |%n", a.name,
				a.group.name().toLowerCase(Locale.ROOT), a.skills, sc.band.label, sc.melee, sc.ranged, sc.spell, sc.ambush, sc.combat, sc.boss, sc.road, sc.general, sc.pvp));
			check(sc.pvp <= 1.5 + 1e-9, a.name + ": PvP power over 1.5×");
		}
		List<Arch> specialists = ARCHES.stream().filter(a -> a.group == Group.SPECIALIST).toList();
		double specGeneral = specialists.stream().mapToDouble(a -> s.get(a.name).general).average().orElseThrow();
		double specCombat = specialists.stream().mapToDouble(a -> s.get(a.name).combat).average().orElseThrow();
		out.append(String.format(Locale.ROOT, "%nGeneral play = combat^%.2f × boss^%.2f × road^%.2f × ambush^%.2f (general fights: %.0f %% of foes resist your best damage kind and take %.0f %% of it, "
			+ "so a second kind helps). Specialists' mean: general **%.2f**, combat **%.2f**.%n%n", GENERAL_COMBAT, GENERAL_BOSS, GENERAL_ROAD, GENERAL_AMBUSH, 100 * RESIST_SHARE, 100 * RESIST_LEFT, specGeneral, specCombat));
		out.append("| Target (asserted) | result |\n|---|---|\n");
		// 1. A specialist clearly wins its own area.
		for (Arch a : specialists) {
			double mine = s.get(a.name).own(a.own);
			int gov = a.own == Own.MELEE ? 0 : a.own == Own.RANGED ? 1 : a.own == Own.SPELL ? 2 : 3;
			for (boolean sharing : new boolean[] {false, true}) {
				double rival = 0;
				String who = "—";
				for (Arch b : ARCHES) {
					boolean shares = b.kinds[gov] >= 20;
					if (b != a && shares == sharing && s.get(b.name).own(a.own) > rival) {
						rival = s.get(b.name).own(a.own);
						who = b.name;
					}
				}
				if (rival == 0) {
					continue;
				}
				double margin = sharing ? 1.04 : 1.08;
				boolean ok = mine >= margin * rival;
				check(ok, a.name + " doesn't clearly win its own area (" + a.own + "): " + mine + " vs " + who + " " + rival);
				out.append(String.format(Locale.ROOT, "| %s wins %s by ≥ %.0f %% over %s | %.2f vs %s %.2f (%+.0f %%) %s |%n", a.name, a.own.name().toLowerCase(Locale.ROOT),
					100 * (margin - 1), sharing ? "builds sharing its main skill at 20" : "everyone else", mine, who, rival, 100 * (mine / rival - 1), ok ? "✓" : "✗"));
			}
		}
		// 2. A good hybrid pair matches or beats a pure specialist overall.
		for (Arch a : ARCHES.stream().filter(x -> x.group == Group.HYBRID).toList()) {
			double mine = s.get(a.name).general;
			double best = a.parents.stream().mapToDouble(p -> s.get(p).general).max().orElse(specGeneral);
			boolean ok = mine >= 0.97 * specGeneral && mine >= 0.93 * best;
			check(ok, a.name + " falls behind the specialists overall: " + mine + " vs mean " + specGeneral + ", parent " + best);
			out.append(String.format(Locale.ROOT, "| %s matches a specialist overall (≥ 97 %% of the specialists' mean; ≥ 93 %% of %s) | %.2f vs %.2f (%+.0f %%) · %.2f (%+.0f %%) %s |%n", a.name,
				String.join(", ", a.parents), mine, specGeneral, 100 * (mine / specGeneral - 1), best, 100 * (mine / best - 1), ok ? "✓" : "✗"));
		}
		// 3. A balanced build stays within about 15-20 % of a specialist in general play.
		Scores bal = s.get("Balanced fighter");
		boolean balOk = bal.general >= 0.80 * specGeneral && bal.general <= 0.97 * specGeneral;
		check(balOk, "the balanced build is not within 20 % of the specialists in general play: " + bal.general + " vs " + specGeneral);
		out.append(String.format(Locale.ROOT, "| Balanced fighter within 20 %% of a specialist in general play, and under it | %.2f vs %.2f (%+.0f %%) %s |%n", bal.general, specGeneral,
			100 * (bal.general / specGeneral - 1), balOk ? "✓" : "✗"));
		// 4. The Wayfarer is viable: general play near the specialists, the best on the road, a fighter that can hold its own.
		Scores way = s.get("Wayfarer");
		boolean roadBest = ARCHES.stream().allMatch(a -> a.name.equals("Wayfarer") || s.get(a.name).road < way.road);
		boolean wayOk = way.general >= 0.85 * specGeneral && roadBest && way.combat >= 0.75 * specCombat;
		check(wayOk, "the Wayfarer is not viable: general " + way.general + ", combat " + way.combat + ", road best " + roadBest);
		out.append(String.format(Locale.ROOT, "| Wayfarer viable: general ≥ 85 %%, combat ≥ 75 %% of the specialists, best on the road | general %.2f (%+.0f %%), combat %.2f (%.0f %%), road %.2f (best: %s) %s |%n",
			way.general, 100 * (way.general / specGeneral - 1), way.combat, 100 * way.combat / specCombat, way.road, roadBest ? "yes" : "no", wayOk ? "✓" : "✗"));
		double broadCombat = Math.max(bal.combat, way.combat);
		boolean commitOk = specCombat >= 1.08 * broadCombat;
		check(commitOk, "commitment doesn't pay in a fight: specialists " + specCombat + " vs the broad builds' best " + broadCombat);
		out.append(String.format(Locale.ROOT, "| Commitment pays in a fight: the specialists' mean combat ≥ 8 %% over the best broad build | %.2f vs %.2f (+%.0f %%) %s |%n",
			specCombat, broadCombat, 100 * (specCombat / broadCombat - 1), commitOk ? "✓" : "✗"));
		out.append("\n");
	}

	// ---------------------------------------------------------------- §9 fairness

	private void fairness(StringBuilder out) {
		out.append("## 5. Fairness (§9)\n\n### Bosses (assumed: " + fmt(BOSS_HP) + " health, armour 10, a " + fmt(BOSS_HIT) + "-damage hit every " + fmt(BOSS_HIT_EVERY)
			+ " s and a " + fmt(BOSS_SLAM) + "-damage slam every " + fmt(BOSS_SLAM_EVERY) + " s; geared = armour 20, toughness 8, " + fmt(GEARED_DPS) + " damage a second)\n\n"
			+ "| Build | boss falls in (s) | you last, face-tanking (s) | hits you must avoid | Mettle healing vs damage in | naked: boss hits to die |\n|---|---|---|---|---|---|\n");
		double vanillaIn = afterArmour(BOSS_HIT, ARMOUR, TOUGHNESS) / BOSS_HIT_EVERY + afterArmour(BOSS_SLAM, ARMOUR, TOUGHNESS) / BOSS_SLAM_EVERY;
		double vanillaTtk = BOSS_HP / GEARED_DPS;
		double vanillaLives = 20 / vanillaIn;
		out.append(String.format(Locale.ROOT, "| vanilla, geared | %.0f | %.0f | %.0f %% | — | %.1f |%n", vanillaTtk, vanillaLives, 100 * (1 - vanillaLives / vanillaTtk), 20 / BOSS_HIT));
		for (Arch a : ARCHES) {
			double[] f = bossFight(a, true);
			double[] naked = bossFight(a, false);
			double avoid = 1 - f[0] / f[1];
			out.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %.0f %% | %.2f of %.2f hp/s | %.1f |%n", a.name, f[1], f[0], 100 * avoid, f[3], f[2], naked[4]));
			check(avoid >= 0.15, a.name + " can face-tank a boss (must avoid only " + avoid + ")");
			check(f[1] >= 25, a.name + " kills a boss too fast: " + f[1] + " s");
			check(f[3] <= 0.6 * f[2], a.name + ": Mettle healing outpaces a boss");
			check(naked[4] <= 4.0, a.name + " survives more than four boss hits with no armour: " + naked[4]);
		}
		// Elites: 3× health and 1.5× damage want √4.5 = 2.12× to feel like a vanilla fight.
		out.append(String.format(Locale.ROOT, "%nAn elite (3× health, 1.5× damage, assumed) fights like a vanilla mob at **%.2f×**: the Master band (%.2f×) is about there, "
			+ "Legend (%.2f×) a little past it.%n%n", Math.sqrt(4.5), Band.IV.fightCap(), Band.LEGEND.fightCap()));

		out.append("### Loops\n\n");
		ContractStat dodge = CONTRACT.stream().filter(c -> c.id.equals("mettle:stamina_cost.dodge")).findFirst().orElseThrow();
		ContractStat regen = CONTRACT.stream().filter(c -> c.id.equals("mettle:stamina_regen")).findFirst().orElseThrow();
		ContractStat refund = CONTRACT.stream().filter(c -> c.id.equals("mettle:stamina_refund")).findFirst().orElseThrow();
		ContractStat cd = CONTRACT.stream().filter(c -> c.id.equals("mettle:cooldown_scale")).findFirst().orElseThrow();
		double spend = DODGE_COST * dodge.range[5] / DODGE_EVERY;
		double back = STAMINA_REGEN * regen.range[5];
		check(spend > back, "a dodge chain sustains itself at Legend: " + spend + " vs " + back);
		check(refund.range[5] <= 0.5, "refunds over half an action's cost");
		double effective = cd.range[5] / (1 + 1.0 / 3);
		check(effective >= 0.4, "cooldowns shorter than 40 % of base with Reverberate");
		out.append(String.format(Locale.ROOT, "- Dodging as fast as allowed (every %.1f s) at Legend's cheapest dodge spends %.1f stamina a second; the best regeneration, with no "
			+ "pause after spending, gives %.1f: an endless dodge chain runs dry. Refunds (perfect timing only, which needs an incoming attack) are at most %.0f %% of the cost.%n",
			DODGE_EVERY, spend, back, 100 * refund.range[5]));
		out.append(String.format(Locale.ROOT, "- Cooldowns: the floor ×%.2f with Reverberate (one in three, never twice in a row) leaves an ability at %.0f %% of its base cooldown "
			+ "at best (floor 40 %%).%n- Mettle healing in a fight is capped by band (%.1f hp/s at Legend), half while a boss is in the fight.%n%n", cd.range[5], 100 * effective, Band.LEGEND.hps));
	}

	// ---------------------------------------------------------------- §8 contract

	private void contract(StringBuilder out) {
		out.append("## 6. The stat contract (§8): ranges by band (I · II · III · IV · Apex · Legend) and PvP\n\n| Stat | unit | range | PvP | read by |\n|---|---|---|---|---|\n");
		for (ContractStat c : CONTRACT) {
			StringBuilder r = new StringBuilder();
			for (int i = 0; i < c.range.length; i++) {
				r.append(i == 0 ? "" : " · ").append(fmt(c.range[i]));
				if (i > 0) {
					check(c.lowerIsStronger ? c.range[i] <= c.range[i - 1] + 1e-9 : c.range[i] >= c.range[i - 1] - 1e-9, c.id + ": the range must grow with the band");
				}
			}
			check(c.range.length == Band.values().length, c.id + ": a range for every band");
			check(c.lowerIsStronger ? c.pvp >= c.range[5] - 1e-9 : c.pvp <= c.range[5] + 1e-9, c.id + ": the PvP cap must not pass Legend");
			check(!c.reader.isBlank(), c.id + ": who reads it");
			out.append(String.format(Locale.ROOT, "| `%s` | %s | %s | %s | %s |%n", c.id, c.unit, r, fmt(c.pvp), c.reader));
		}
		out.append("\n");
	}

	// ---------------------------------------------------------------- §4 slots and sets (careers; report only)

	record Variant(String name, int base, int every, int max, int[] wits, int[] sets, double[] rarity) {
	}

	static final double[] RARITY_NOW = {70, -1.5, 24, 1, 6, 0.5};
	static final double[] RARITY_AFTER = {64, -1.6, 28, 1.0, 8, 0.6};

	private void slotStudy(StringBuilder out) {
		List<Variant> variants = List.of(
			new Variant("today: 3 → 8 slots (+2 Wits), sets 2/4/6", 3, 3, 8, new int[] {8, 14}, null, RARITY_NOW),
			new Variant("2 → 5 slots (+1 Wits 12), sets 2/4/6", 2, 4, 5, new int[] {12}, null, RARITY_AFTER),
			new Variant("3 → 6 slots (+1 Wits 12), sets 2/3/5", 3, 4, 6, new int[] {12}, new int[] {2, 3, 5}, RARITY_AFTER),
			new Variant("**2 → 5 slots (+1 Wits 12), sets 2/3/5 (chosen)**", 2, 4, 5, new int[] {12}, new int[] {2, 3, 5}, RARITY_AFTER));
		out.append("## 7. Fewer, stronger slots (§4): real careers (the game's deal, momentum, Focus, boons; BuildFeelReportTest's harness)\n\n"
			+ "Committed players: Focus on one keyword from the start, mains chosen for it (BUILD_FEEL.md §9). \"Top set\" is the 6 (or the 5 in 2/3/5). "
			+ "Focused: three mains, two sides, three dabbled, mains to 20. All skills: every released skill to 20.\n\n"
			+ "| Variant | slotted cards: focused · all skills | committed top set by 20: worst keyword · mean | first top set, mean main skill: common · scarce | "
			+ "focused guided: own Focus middle tier by mid-game · top by 20 | Jaccard one skill · chasing one keyword | identical decks |\n|---|---|---|---|---|---|---|\n");
		int committedRuns = 150;
		int focusedRuns = 300;
		int singleRuns = 200;
		Variant chosen = variants.getLast();
		for (Variant v : variants) {
			CardConfig config = new CardConfig();
			config.slots_base = v.base;
			config.slots_every = v.every;
			config.slots_max = v.max;
			config.wits_slots = v.wits;
			config.rarity_odds = v.rarity;
			config.momentum_perk_lean = BuildFeelReportTest.PROPOSED.perkLean();
			config.momentum_keyword_lean = BuildFeelReportTest.PROPOSED.keywordLean();
			config.focus_weight = BuildFeelReportTest.PROPOSED.focus();
			config.six_tier_skill = BuildFeelReportTest.PROPOSED.sixGate();
			config.temper_step = BuildFeelReportTest.PROPOSED.temperStep();
			config.temper_per_deck = BuildFeelReportTest.TEMPER_DECK_CAP;
			config.inscribe_per_deck = BuildFeelReportTest.PROPOSED.inscribeCap();
			config.inscribe_needs_seed = BuildFeelReportTest.INSCRIBE_NEEDS_SEED;
			CardConfig.use(config);
			BuildFeelReportTest.SET_COUNTS = v.sets;
			try {
				// slotted cards
				double focusedSlots = 0;
				double focusMid = 0;
				double focusTop = 0;
				for (int run = 0; run < focusedRuns; run++) {
					BuildFeelReportTest.Char c = BuildFeelReportTest.focused(BuildFeelReportTest.PROPOSED, BuildFeelReportTest.Policy.GUIDED, run);
					c.play();
					focusedSlots += c.decks.values().stream().mapToInt(List::size).sum();
					int mid = 0;
					for (int m = 1; m <= 20; m++) {
						if (c.charLevelAt[m] >= 20) {
							mid = m;
							break;
						}
					}
					focusMid += mid > 0 && c.focusTier[mid] >= 4 ? 1 : 0;
					focusTop += c.focusTier[20] >= 6 ? 1 : 0;
				}
				double allSlots = 0;
				for (int run = 0; run < 40; run++) {
					List<ResourceKey<Skill>> order = new ArrayList<>();
					for (String s : TestCards.RELEASED) {
						order.add(TestCards.skill(s));
					}
					BuildFeelReportTest.Char c = new BuildFeelReportTest.Char(BuildFeelReportTest.PROPOSED, BuildFeelReportTest.Policy.GUIDED, null, order,
						BuildFeelReportTest.ALL_EIGHT, new Random(run * 1_000_003L + 41).nextLong(), 12);
					c.play();
					allSlots += c.decks.values().stream().mapToInt(List::size).sum();
				}
				// committed players, every keyword
				double worst = 1;
				double meanTop = 0;
				double commonSkill = 0;
				int commonN = 0;
				double scarceSkill = 0;
				int scarceN = 0;
				double chaseJac = 0;
				int chaseN = 0;
				Map<Identifier, Double> byKeyword = new LinkedHashMap<>();
				for (Identifier k : TestCards.KEYWORDS.keySet().stream().sorted(Comparator.comparing(Identifier::getPath)).toList()) {
					int top = 0;
					List<Set<String>> sigs = new ArrayList<>();
					for (int run = 0; run < committedRuns; run++) {
						BuildFeelReportTest.Char c = BuildFeelReportTest.committed(BuildFeelReportTest.PROPOSED, k, run);
						c.play();
						if (c.focusTier[20] >= 6) {
							top++;
						}
						for (int m = 1; m <= 20; m++) {
							if (c.focusTier[m] >= 6) {
								boolean scarce = Set.of("tide", "deep", "kin", "warden").contains(k.getPath());
								if (scarce) {
									scarceSkill += m;
									scarceN++;
								} else {
									commonSkill += m;
									commonN++;
								}
								break;
							}
						}
						Set<String> sig = new HashSet<>();
						for (BuildFeelReportTest.Owned o : c.decks.get(c.order.getFirst())) {
							String p = o.card.perk().identifier().getPath();
							sig.add(p);
							o.card.traits().forEach(tr -> sig.add(p + "+" + tr.trait().getPath()));
						}
						sigs.add(sig);
					}
					double share = top / (double) committedRuns;
					byKeyword.put(k, share);
					if (!k.equals(CardRules.HEX)) {
						worst = Math.min(worst, share);
					}
					meanTop += share;
					for (int i = 0; i + 1 < sigs.size(); i++) {
						chaseJac += BuildFeelReportTest.jaccard(sigs.get(i), sigs.get((i * 7 + 13) % sigs.size()));
						chaseN++;
					}
				}
				meanTop /= byKeyword.size();
				// single-skill variety
				double jac = 0;
				int jacN = 0;
				int same = 0;
				for (String name : TestCards.RELEASED) {
					ResourceKey<Skill> skill = TestCards.skill(name);
					List<Set<String>> sigs = new ArrayList<>();
					List<String> exact = new ArrayList<>();
					for (int run = 0; run < singleRuns; run++) {
						BuildFeelReportTest.Char c = new BuildFeelReportTest.Char(BuildFeelReportTest.PROPOSED, BuildFeelReportTest.Policy.POINTS, null, new ArrayList<>(List.of(skill)),
							BuildFeelReportTest.SINGLE, new Random(run * 1_000_003L + 11).nextLong(), 3);
						c.play();
						Set<String> sig = new HashSet<>();
						StringBuilder ex = new StringBuilder();
						for (BuildFeelReportTest.Owned o : c.decks.get(skill)) {
							String p = o.card.perk().identifier().getPath();
							sig.add(p);
							o.card.traits().forEach(tr -> sig.add(p + "+" + tr.trait().getPath()));
							ex.append(p).append(o.card.rarity()).append(o.card.keywords()).append(o.card.traits()).append(';');
						}
						sigs.add(sig);
						exact.add(ex.toString());
					}
					for (int i = 0; i + 1 < sigs.size(); i++) {
						int o = (i * 7 + 13) % sigs.size();
						jac += BuildFeelReportTest.jaccard(sigs.get(i), sigs.get(o));
						jacN++;
						if (o != i && exact.get(i).equals(exact.get(o))) {
							same++;
						}
					}
				}
				double singleJac = jac / jacN;
				double chase = chaseJac / chaseN;
				out.append(String.format(Locale.ROOT, "| %s | %.1f · %.1f | %.0f %% · %.0f %% | %.1f · %.1f | %.0f %% · %.0f %% | %.3f · %.3f | %d |%n", v.name, focusedSlots / focusedRuns,
					allSlots / 40, 100 * worst, 100 * meanTop, commonN == 0 ? 0 : commonSkill / commonN, scarceN == 0 ? 0 : scarceSkill / scarceN, 100 * focusMid / focusedRuns,
					100 * focusTop / focusedRuns, singleJac, chase, same));
				if (v == chosen) {
					check(singleJac < 0.3, "chosen slots: one skill's decks overlap too much: " + singleJac);
					check(chase < 0.35, "chosen slots: players chasing one keyword converge: " + chase);
					check(same == 0, "chosen slots: identical decks");
					check(worst >= 0.6, "chosen slots: a keyword's committed top set by 20 under 60 %: " + worst);
					check(commonN > 0 && commonSkill / commonN >= 12 && commonSkill / commonN <= 17, "chosen slots: the common keywords' top set lands outside skill 12-17");
					check(scarceN > 0 && scarceSkill / scarceN >= 12 && scarceSkill / scarceN <= 17.5, "chosen slots: the scarce keywords' top set lands outside skill 12-17");
					check(focusTop / focusedRuns >= 0.8, "chosen slots: a guided player's own Focus top set by 20 under 80 %");
					StringBuilder kw = new StringBuilder("\nChosen variant, committed top set by 20 per keyword: ");
					byKeyword.forEach((k, sh) -> kw.append(k.getPath()).append(String.format(Locale.ROOT, " %.0f %%, ", 100 * sh)));
					this.chosenKeywords = kw.substring(0, kw.length() - 2) + ".\n";
				}
			} finally {
				CardConfig.reset();
				BuildFeelReportTest.SET_COUNTS = null;
			}
		}
		out.append(this.chosenKeywords).append("\n");
		loadouts(out, chosen);
	}

	private String chosenKeywords = "";

	/** Loadouts (§11): a focused player's second loadout for its second keyword, from the same binder. */
	private void loadouts(StringBuilder out, Variant v) {
		CardConfig config = new CardConfig();
		config.slots_base = v.base;
		config.slots_every = v.every;
		config.slots_max = v.max;
		config.wits_slots = v.wits;
		config.rarity_odds = v.rarity;
		CardConfig.use(config);
		BuildFeelReportTest.SET_COUNTS = v.sets;
		try {
			int runs = 200;
			double shared = 0;
			double slotted = 0;
			double secondTier = 0;
			double firstTier = 0;
			for (int run = 0; run < runs; run++) {
				BuildFeelReportTest.Char c = BuildFeelReportTest.focused(BuildFeelReportTest.PROPOSED, BuildFeelReportTest.Policy.GUIDED, run);
				c.play();
				Map<Identifier, Integer> counts = new HashMap<>();
				Set<BuildFeelReportTest.Owned> a = new HashSet<>();
				for (List<BuildFeelReportTest.Owned> d : c.decks.values()) {
					a.addAll(d);
					d.forEach(o -> o.card.keywords().forEach(k -> counts.merge(k, 1, Integer::sum)));
				}
				Set<ResourceKey<Skill>> two = Set.of(TestCards.skill("mining"), TestCards.skill("blades"));
				Identifier second = counts.entrySet().stream()
					.filter(e -> !e.getKey().equals(c.focus) && !e.getKey().equals(CardRules.HEX) && BuildFeelReportTest.studyTier(e.getValue(), two, x -> 20, 0) < 6)
					.max(Map.Entry.<Identifier, Integer>comparingByValue().thenComparing(e -> e.getKey().getPath())).map(Map.Entry::getKey).orElse(null);
				if (second == null) {
					continue;
				}
				// Loadout B: every deck rebuilt from the same binder valuing the second keyword's cards +1 point.
				Map<Identifier, Integer> countsB = new HashMap<>();
				int inB = 0;
				int sharedHere = 0;
				for (Map.Entry<ResourceKey<Skill>, List<BuildFeelReportTest.Owned>> e : c.owned.entrySet()) {
					int slots = CardRules.slots(c.level(e.getKey()), c.wits);
					List<BuildFeelReportTest.Owned> pick = e.getValue().stream().filter(o -> o.card.needs() <= c.level(e.getKey()))
						.sorted(Comparator.comparingDouble((BuildFeelReportTest.Owned o) -> CardReportTest.points(o.card) + (o.card.keywords().contains(second) ? 2 : 0)).reversed())
						.toList();
					Set<ResourceKey<Perk>> perks = new HashSet<>();
					int n = 0;
					for (BuildFeelReportTest.Owned o : pick) {
						if (n >= slots || TestCards.def(o.card.perk()).kind() == Perk.Kind.MASTERY || !perks.add(o.card.perk())) {
							continue;
						}
						n++;
						inB++;
						sharedHere += a.contains(o) ? 1 : 0;
						o.card.keywords().forEach(k -> countsB.merge(k, 1, Integer::sum));
					}
				}
				shared += inB == 0 ? 0 : sharedHere / (double) inB;
				slotted += inB;
				secondTier += BuildFeelReportTest.studyTier(countsB.getOrDefault(second, 0), Set.of(TestCards.skill("mining"), TestCards.skill("blades")), s -> 20, 0);
				firstTier += BuildFeelReportTest.studyTier(counts.getOrDefault(second, 0), Set.of(TestCards.skill("mining"), TestCards.skill("blades")), s -> 20, 0);
			}
			out.append(String.format(Locale.ROOT, "### Loadouts (§11)%n%nA focused player at mains 20 builds a second loadout from the same binder for the best keyword it has "
				+ "not completed (not its Focus, not Hex): it "
				+ "slots %.1f cards, %.0f %% of them shared with the first (the same cards, never counted twice: one loadout is live at a time); the second keyword's set goes "
				+ "from tier %.1f to %.1f (2 / 4 / 6 = the 2 / 3 / 5 tiers).%n%n", slotted / runs, 100 * shared / runs, firstTier / runs, secondTier / runs));
			check(secondTier > firstTier, "a second loadout should strengthen the second keyword");
		} finally {
			CardConfig.reset();
			BuildFeelReportTest.SET_COUNTS = null;
		}
	}

	static String fmt(double v) {
		return Math.abs(v - Math.rint(v)) < 1e-9 ? String.format(Locale.ROOT, "%d", (long) Math.rint(v)) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
	}

}
