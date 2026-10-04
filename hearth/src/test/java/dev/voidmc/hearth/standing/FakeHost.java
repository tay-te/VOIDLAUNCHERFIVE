package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.rules.DeedRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** An in-memory {@link StandingHost}: towns are {@link SimpleSettlement}s, events and messages are recorded for asserts. */
final class FakeHost implements StandingHost {
	long day = 10;
	final Map<Long, Settlement> towns = new LinkedHashMap<>();
	/** Towns the atlas knows of that are not settled (no live settlement). */
	final List<HearthWorld.SiteInfo> unsettled = new ArrayList<>();
	final RegionData region = new RegionData();
	final List<UUID> witnesses = new ArrayList<>();
	final List<String> events = new ArrayList<>();
	final List<String> told = new ArrayList<>();
	final List<String> actions = new ArrayList<>();
	final Set<String> outlaws = new HashSet<>();
	int regionDirty;

	SimpleSettlement town(long id, String name, int x, int z) {
		SimpleSettlement s = new SimpleSettlement(id, name, Culture.TEMPERATE, new BlockPos(x, 64, z), 0);
		this.towns.put(id, s);
		return s;
	}

	<T extends Settlement> T add(T s) {
		this.towns.put(s.id(), s);
		return s;
	}

	@Override
	public long day() {
		return this.day;
	}

	@Override
	public @Nullable Settlement settlement(long id) {
		return this.towns.get(id);
	}

	@Override
	public Collection<? extends Settlement> settlements() {
		return this.towns.values();
	}

	@Override
	public List<HearthWorld.SiteInfo> sitesNear(Settlement origin, int radius) {
		List<HearthWorld.SiteInfo> out = new ArrayList<>();
		for (Settlement s : this.towns.values()) {
			out.add(new HearthWorld.SiteInfo(s.id(), s.name(), s.culture(), s.center(), SettlementTier.VILLAGE, true));
		}
		out.addAll(this.unsettled);
		out.removeIf(i -> Math.sqrt(i.center().distSqr(origin.center())) > radius);
		out.sort(Comparator.comparingDouble(i -> i.center().distSqr(origin.center())));
		return out;
	}

	@Override
	public RegionData region() {
		return this.region;
	}

	@Override
	public void regionDirty() {
		this.regionDirty++;
	}

	@Override
	public List<UUID> witnesses(StandingService.Deed deed, int radius, int max) {
		return this.witnesses.subList(0, Math.min(max, this.witnesses.size()));
	}

	@Override
	public void standingChanged(Settlement settlement, UUID player, StandingService.Title from, StandingService.Title to) {
		this.events.add(settlement.name() + ":" + from.getSerializedName() + "->" + to.getSerializedName());
	}

	@Override
	public void gossipArrived(UUID player, Settlement town, String from, int delta) {
		this.told.add(town.name() + "<-" + from + ":" + delta);
	}

	@Override
	public String playerName(UUID player) {
		return "Alex";
	}

	@Override
	public boolean outlawByLaw(UUID player, long settlementId) {
		return this.outlaws.contains(player + "@" + settlementId);
	}

	@Override
	public void action(StandingService.Deed deed, DeedRule rule) {
		this.actions.add(rule.action());
	}
}
