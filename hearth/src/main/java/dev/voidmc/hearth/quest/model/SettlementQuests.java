package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's quest state, saved with the settlement (a {@code SETTLEMENT}-scope data key): the offers on its board,
 * the needs that will turn into quests, the monster pressure on it at night, and what it knows of the landmarks round it.
 * Everything here is small and bounded so a world of hundreds of towns stays cheap to save.
 */
public final class SettlementQuests {
	/** At most this many needs remembered (the newest win). */
	public static final int NEED_LIMIT = 8;
	/** At most this many located targets remembered. */
	public static final int TARGET_LIMIT = 24;

	public static final Codec<SettlementQuests> CODEC = RecordCodecBuilder.create(i -> i.group(
		QuestInstance.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(s -> s.offers),
		StalledNeed.CODEC.listOf().optionalFieldOf("needs", List.of()).forGetter(s -> s.needs),
		Codec.INT.optionalFieldOf("pressure", 0).forGetter(s -> s.pressure),
		Codec.LONG.optionalFieldOf("refreshed_day", Long.MIN_VALUE).forGetter(s -> s.refreshedDay),
		Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(s -> s.cooldowns),
		LocatedTarget.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(s -> s.targets),
		Codec.INT.optionalFieldOf("issued", 0).forGetter(s -> s.issued)
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

	public SettlementQuests() {
		this(List.of(), List.of(), 0, Long.MIN_VALUE, Map.of(), List.of(), 0);
	}

	public SettlementQuests(List<QuestInstance> offers, List<StalledNeed> needs, int pressure, long refreshedDay, Map<Identifier, Long> cooldowns,
		List<LocatedTarget> targets, int issued) {
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
	}

	public @Nullable QuestInstance offer(UUID id) {
		for (QuestInstance q : this.offers) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		return null;
	}

	/** Records a stall, merging with an existing need for the same plot and goods. */
	public void addNeed(StalledNeed need) {
		this.needs.removeIf(n -> n.plot() == need.plot() && n.goods() == need.goods());
		this.needs.addFirst(need);
		while (this.needs.size() > NEED_LIMIT) {
			this.needs.removeLast();
		}
	}

	/** Units still wanted for a plot's goods after a delivery; drops the need when met. */
	public void satisfy(int plot, Goods goods, int units) {
		for (int i = 0; i < this.needs.size(); i++) {
			StalledNeed n = this.needs.get(i);
			if (n.plot() == plot && n.goods() == goods) {
				int left = n.missing() - units;
				if (left <= 0) {
					this.needs.remove(i);
				} else {
					this.needs.set(i, new StalledNeed(n.plot(), n.goods(), left, n.day(), n.place()));
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

	/**
	 * A construction stalled for goods.
	 *
	 * @param place what is being built, for quest text ("cottage", "bridge")
	 */
	public record StalledNeed(int plot, Goods goods, int missing, long day, String place) {
		public static final Codec<StalledNeed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("plot").forGetter(StalledNeed::plot),
			Goods.CODEC.fieldOf("goods").forGetter(StalledNeed::goods),
			Codec.INT.fieldOf("missing").forGetter(StalledNeed::missing),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(StalledNeed::day),
			Codec.STRING.optionalFieldOf("place", "").forGetter(StalledNeed::place)
		).apply(i, StalledNeed::new));
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
