package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's quest state, saved with the settlement (a {@code SETTLEMENT}-scope data key): the offers on its board,
 * the needs that will turn into quests (stalled builds and workshops short of inputs), the things that happened to it
 * (fires, collapses, caravans robbed, sites spent), the monster pressure and crime on it, and what it knows of the
 * landmarks round it. Everything here is small and bounded so a world of hundreds of towns stays cheap to save.
 */
public final class SettlementQuests {
	/** At most this many needs remembered (the newest win). */
	public static final int NEED_LIMIT = 8;
	/** At most this many located targets remembered. */
	public static final int TARGET_LIMIT = 24;
	/** At most this many happenings remembered (the newest win). */
	public static final int HAPPENING_LIMIT = 12;

	public static final Codec<SettlementQuests> CODEC = RecordCodecBuilder.create(i -> i.group(
		QuestInstance.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(s -> s.offers),
		StalledNeed.CODEC.listOf().optionalFieldOf("needs", List.of()).forGetter(s -> s.needs),
		Codec.INT.optionalFieldOf("pressure", 0).forGetter(s -> s.pressure),
		Codec.LONG.optionalFieldOf("refreshed_day", Long.MIN_VALUE).forGetter(s -> s.refreshedDay),
		Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(s -> s.cooldowns),
		LocatedTarget.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(s -> s.targets),
		Codec.INT.optionalFieldOf("issued", 0).forGetter(s -> s.issued),
		Happening.CODEC.listOf().optionalFieldOf("happenings", List.of()).forGetter(s -> s.happenings),
		Codec.INT.optionalFieldOf("next_happening", 1).forGetter(s -> s.nextHappening),
		Codec.INT.optionalFieldOf("crime", 0).forGetter(s -> s.crime),
		Codec.LONG.optionalFieldOf("declining_since", Long.MIN_VALUE).forGetter(s -> s.decliningSince)
	).apply(i, SettlementQuests::new));

	public final List<QuestInstance> offers;
	public final List<StalledNeed> needs;
	/** Night monster pressure, accrued while players see monsters round the embodied town; decays each day. */
	public int pressure;
	/** The sim day the board was last refreshed. */
	public long refreshedDay;
	/** Template id → the first day it may be offered again here. */
	public final Map<Identifier, Long> cooldowns;
	public final List<LocatedTarget> targets;
	/** How many quests this settlement has issued, ever: a seed for variety. */
	public int issued;
	/** Things that happened to the town that quests answer (newest first). */
	public final List<Happening> happenings;
	public int nextHappening;
	/** Recent crime: grows with each crime the law confirms here, halves each day. */
	public int crime;
	/** The day the town's fortunes turned down ({@code PROSPERITY_CHANGED}), or {@link Long#MIN_VALUE} while it is not declining. */
	public long decliningSince;

	public SettlementQuests() {
		this(List.of(), List.of(), 0, Long.MIN_VALUE, Map.of(), List.of(), 0, List.of(), 1, 0, Long.MIN_VALUE);
	}

	public SettlementQuests(List<QuestInstance> offers, List<StalledNeed> needs, int pressure, long refreshedDay, Map<Identifier, Long> cooldowns,
		List<LocatedTarget> targets, int issued) {
		this(offers, needs, pressure, refreshedDay, cooldowns, targets, issued, List.of(), 1, 0, Long.MIN_VALUE);
	}

	public SettlementQuests(List<QuestInstance> offers, List<StalledNeed> needs, int pressure, long refreshedDay, Map<Identifier, Long> cooldowns,
		List<LocatedTarget> targets, int issued, List<Happening> happenings, int nextHappening, int crime, long decliningSince) {
		this.offers = new ArrayList<>(offers.size());
		for (QuestInstance q : offers) {
			this.offers.add(q.copy());
		}
		this.needs = new ArrayList<>(needs);
		this.pressure = pressure;
		this.refreshedDay = refreshedDay;
		this.cooldowns = new HashMap<>(cooldowns);
		this.targets = new ArrayList<>(targets);
		this.issued = issued;
		this.happenings = new ArrayList<>(happenings);
		this.nextHappening = Math.max(1, nextHappening);
		this.crime = crime;
		this.decliningSince = decliningSince;
	}

	public @Nullable QuestInstance offer(UUID id) {
		for (QuestInstance q : this.offers) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		return null;
	}

	/** Records a stall (or a workshop's shortage), merging with an existing need for the same plot, goods and source. */
	public void addNeed(StalledNeed need) {
		this.needs.removeIf(n -> n.plot() == need.plot() && n.goods() == need.goods() && n.source().equals(need.source()));
		this.needs.addFirst(need);
		while (this.needs.size() > NEED_LIMIT) {
			this.needs.removeLast();
		}
	}

	/** Units still wanted for a plot's goods after a delivery; drops the need when met. */
	public void satisfy(int plot, Goods goods, int units) {
		satisfy(plot, goods, "", units);
	}

	/** Units still wanted for a need (a stalled plot's goods, or a workshop's input) after a delivery; drops it when met. */
	public void satisfy(int plot, Goods goods, String source, int units) {
		for (int i = 0; i < this.needs.size(); i++) {
			StalledNeed n = this.needs.get(i);
			if (n.plot() == plot && n.goods() == goods && n.source().equals(source)) {
				int left = n.missing() - units;
				if (left <= 0) {
					this.needs.remove(i);
				} else {
					this.needs.set(i, new StalledNeed(n.plot(), n.goods(), left, n.day(), n.place(), n.source()));
				}
				return;
			}
		}
	}

	public @Nullable LocatedTarget target(Identifier structure) {
		for (LocatedTarget t : this.targets) {
			if (t.structure().equals(structure)) {
				return t;
			}
		}
		return null;
	}

	public void putTarget(LocatedTarget target) {
		this.targets.removeIf(t -> t.structure().equals(target.structure()));
		this.targets.addFirst(target);
		while (this.targets.size() > TARGET_LIMIT) {
			this.targets.removeLast();
		}
	}

	public boolean declining() {
		return this.decliningSince != Long.MIN_VALUE;
	}

	/**
	 * Records a happening (its id assigned here) and returns it. An unresolved happening of the same kind at the same plot
	 * (or place) involving the same other settlement merges with it, adding the amounts.
	 */
	public Happening addHappening(String kind, long day, int plot, BlockPos pos, long other, int amount, String goods, String detail) {
		for (int i = 0; i < this.happenings.size(); i++) {
			Happening h = this.happenings.get(i);
			if (!h.resolved() && h.kind().equals(kind) && h.plot() == plot && h.other() == other && (plot >= 0 || h.pos().equals(pos))) {
				Happening merged = new Happening(h.id(), kind, day, plot, pos, other, h.amount() + amount, goods.isEmpty() ? h.goods() : goods,
					detail.isEmpty() ? h.detail() : detail, false);
				this.happenings.set(i, merged);
				return merged;
			}
		}
		Happening h = new Happening(this.nextHappening++, kind, day, plot, pos, other, amount, goods, detail, false);
		this.happenings.addFirst(h);
		while (this.happenings.size() > HAPPENING_LIMIT) {
			this.happenings.removeLast();
		}
		return h;
	}

	public @Nullable Happening happening(int id) {
		for (Happening h : this.happenings) {
			if (h.id() == id) {
				return h;
			}
		}
		return null;
	}

	/** Marks a happening dealt with (the fire is out, the miners are free); its untaken offers come down. */
	public boolean resolve(int id) {
		for (int i = 0; i < this.happenings.size(); i++) {
			Happening h = this.happenings.get(i);
			if (h.id() == id && !h.resolved()) {
				this.happenings.set(i, h.asResolved());
				this.offers.removeIf(q -> q.takenBy.isEmpty() && happeningOf(q) == id);
				return true;
			}
		}
		return false;
	}

	/** Unresolved happenings older than {@code days} are let go (nobody came; the town managed on its own). */
	public void forgetOlderThan(long day, int days) {
		for (int i = 0; i < this.happenings.size(); i++) {
			Happening h = this.happenings.get(i);
			if (!h.resolved() && day - h.day() > days) {
				this.happenings.set(i, h.asResolved());
			}
		}
	}

	/** The happening an offer answers, or 0. */
	public static int happeningOf(QuestInstance q) {
		for (Objective o : q.objectives) {
			int id = o.data.getIntOr(Objective.Keys.HAPPENING, 0);
			if (id != 0) {
				return id;
			}
		}
		return 0;
	}

	/**
	 * A construction stalled for goods, or (with a {@code source}) a workshop short of an input it works.
	 *
	 * @param place  what is being built, for quest text ("cottage", "bridge"), or the workshop ("smelter")
	 * @param source empty for a stalled build; the workshop's job name ({@code smelter}) for a standing contract
	 */
	public record StalledNeed(int plot, Goods goods, int missing, long day, String place, String source) {
		public static final Codec<StalledNeed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(StalledNeed::plot),
			Goods.CODEC.fieldOf("goods").forGetter(StalledNeed::goods),
			Codec.INT.fieldOf("missing").forGetter(StalledNeed::missing),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(StalledNeed::day),
			Codec.STRING.optionalFieldOf("place", "").forGetter(StalledNeed::place),
			Codec.STRING.optionalFieldOf("source", "").forGetter(StalledNeed::source)
		).apply(i, StalledNeed::new));

		public StalledNeed(int plot, Goods goods, int missing, long day, String place) {
			this(plot, goods, missing, day, place, "");
		}

		/** A workshop's shortage rather than a stalled build. */
		public boolean contract() {
			return !this.source.isEmpty();
		}
	}

	/**
	 * Something that happened to the town that quests answer.
	 *
	 * @param kind     one of the kind names below ({@code fire}, {@code collapse}, {@code caravan_robbed}...)
	 * @param plot     the plot it happened at, or -1
	 * @param pos      where (a plot's middle, a site's centre, a point on the road)
	 * @param other    another settlement it involves (a caravan's other end), or -1
	 * @param amount   blocks damaged, units of cargo, ...
	 * @param goods    the goods it concerns (a caravan's main cargo, a spent site's yield), or empty
	 * @param detail   words for quest text (the site kind, the cargo, the trapped miners' names)
	 * @param resolved dealt with: no more offers are made for it
	 */
	public record Happening(int id, String kind, long day, int plot, BlockPos pos, long other, int amount, String goods, String detail,
		boolean resolved) {
		public static final String FIRE = "fire";
		public static final String COLLAPSE = "collapse";
		public static final String BLAST = "blast";
		public static final String DEPLETED = "depleted";
		public static final String DECLINING = "declining";
		public static final String CARAVAN_DEPARTED = "caravan_departed";
		public static final String CARAVAN_ROBBED = "caravan_robbed";
		public static final String CARAVAN_LOST = "caravan_lost";
		/** Every kind a template may name in {@code when.events}. */
		public static final Set<String> KINDS = Set.of(FIRE, COLLAPSE, BLAST, DEPLETED, DECLINING, CARAVAN_DEPARTED, CARAVAN_ROBBED, CARAVAN_LOST);

		public static final Codec<Happening> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Happening::id),
			Codec.STRING.fieldOf("kind").forGetter(Happening::kind),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Happening::day),
			Codec.INT.optionalFieldOf("plot", -1).forGetter(Happening::plot),
			BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(Happening::pos),
			Codec.LONG.optionalFieldOf("other", -1L).forGetter(Happening::other),
			Codec.INT.optionalFieldOf("amount", 0).forGetter(Happening::amount),
			Codec.STRING.optionalFieldOf("goods", "").forGetter(Happening::goods),
			Codec.STRING.optionalFieldOf("detail", "").forGetter(Happening::detail),
			Codec.BOOL.optionalFieldOf("resolved", false).forGetter(Happening::resolved)
		).apply(i, Happening::new));

		public Happening asResolved() {
			return new Happening(this.id, this.kind, this.day, this.plot, this.pos, this.other, this.amount, this.goods, this.detail, true);
		}
	}

	/**
	 * A structure searched for near this settlement: found at {@code pos} with bounds, or not found within the search
	 * radius ({@code found} false; searched again after a while).
	 */
	public record LocatedTarget(Identifier structure, boolean found, BlockPos pos, BlockPos min, BlockPos max, long day) {
		public static final Codec<LocatedTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("structure").forGetter(LocatedTarget::structure),
			Codec.BOOL.optionalFieldOf("found", true).forGetter(LocatedTarget::found),
			BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(LocatedTarget::pos),
			BlockPos.CODEC.optionalFieldOf("min", BlockPos.ZERO).forGetter(LocatedTarget::min),
			BlockPos.CODEC.optionalFieldOf("max", BlockPos.ZERO).forGetter(LocatedTarget::max),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(LocatedTarget::day)
		).apply(i, LocatedTarget::new));
	}
}
