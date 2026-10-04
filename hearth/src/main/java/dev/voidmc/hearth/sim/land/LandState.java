package dev.voidmc.hearth.sim.land;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.ResourceSite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's land and fortunes (docs/SIM.md §16): its territory, the resource sites its survey found there, its
 * prosperity and carrying capacity, and its trade. Saved with the settlement under {@code hearth:sim/land} (a settlement
 * data key, so phase 2 saves simply start without it and are surveyed when they load).
 */
public final class LandState {
	public static final Codec<LandState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("territory", 0).forGetter(l -> l.territory),
		Codec.INT.optionalFieldOf("surveyed", 0).forGetter(l -> l.surveyed),
		Codec.INT.optionalFieldOf("next_site", 1).forGetter(l -> l.nextSite),
		SimSite.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(l -> l.sites),
		Codec.DOUBLE.optionalFieldOf("prosperity", 0.5).forGetter(l -> l.prosperity),
		Codec.INT.optionalFieldOf("mood", 0).forGetter(l -> l.mood),
		Codec.INT.optionalFieldOf("capacity", Integer.MAX_VALUE).forGetter(l -> l.capacity),
		Codec.DOUBLE.optionalFieldOf("exports", 0.0).forGetter(l -> l.exports),
		Codec.DOUBLE.optionalFieldOf("imports", 0.0).forGetter(l -> l.imports),
		Codec.LONG.optionalFieldOf("last_caravan", Long.MIN_VALUE).forGetter(l -> l.lastCaravanDay),
		Codec.LONG.optionalFieldOf("last_survey", Long.MIN_VALUE).forGetter(l -> l.lastSurveyDay),
		Codec.STRING.optionalFieldOf("why", "").forGetter(l -> l.why),
		Codec.INT.listOf().optionalFieldOf("deserted", List.of()).forGetter(l -> List.copyOf(l.deserted))
	).apply(i, (territory, surveyed, next, sites, prosperity, mood, capacity, exports, imports, caravan, survey, why, deserted) -> {
		LandState l = new LandState();
		l.territory = territory;
		l.surveyed = surveyed;
		l.nextSite = next;
		l.sites.addAll(sites);
		l.prosperity = prosperity;
		l.mood = mood;
		l.capacity = capacity;
		l.exports = exports;
		l.imports = imports;
		l.lastCaravanDay = caravan;
		l.lastSurveyDay = survey;
		l.why = why;
		l.deserted.addAll(deserted);
		return l;
	}));

	public static final HearthDataKey<LandState> KEY = HearthDataKey.register(Hearth.id("sim/land"), HearthDataKey.Scope.SETTLEMENT, CODEC,
		LandState::new);

	/** The radius of land the settlement works, blocks from its heart (0 until first surveyed). */
	int territory;
	/** The territory radius the last survey covered. */
	int surveyed;
	int nextSite = 1;
	final List<SimSite> sites = new ArrayList<>();
	/** 0 (ruin) to 1 (boom), a slow average of the town's fortunes. */
	double prosperity = 0.5;
	/** -1 declining, 0 steady, +1 rising: the last {@code PROSPERITY_CHANGED} said so. */
	int mood;
	/** People the land and the trade can feed. */
	int capacity = Integer.MAX_VALUE;
	/** Coin a day earned by selling to other towns and passing traders, and spent buying in (slow averages). */
	double exports;
	double imports;
	long lastCaravanDay = Long.MIN_VALUE;
	long lastSurveyDay = Long.MIN_VALUE;
	/** Why the fortunes last turned, for commands and other modules' dialogue. */
	String why = "";
	/** Houses left standing empty in a decline (ABANDONED plots that folk may move back into). */
	final java.util.LinkedHashSet<Integer> deserted = new java.util.LinkedHashSet<>();

	/** Transient: a survey for this settlement is running off the server thread. */
	boolean surveying;
	private @Nullable List<ResourceSite> views;

	/** Whether a survey of this settlement is running off the server thread (transient). */
	public boolean isSurveying() {
		return this.surveying;
	}

	public void setSurveying(boolean surveying) {
		this.surveying = surveying;
	}

	public int territory() {
		return this.territory;
	}

	public int surveyed() {
		return this.surveyed;
	}

	public List<SimSite> sites() {
		return Collections.unmodifiableList(this.sites);
	}

	public @Nullable SimSite site(int id) {
		for (SimSite s : this.sites) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	public double prosperity() {
		return this.prosperity;
	}

	public int mood() {
		return this.mood;
	}

	public int capacity() {
		return this.capacity;
	}

	public double exports() {
		return this.exports;
	}

	public double imports() {
		return this.imports;
	}

	public String why() {
		return this.why;
	}

	/** Plots left standing empty in a decline (the build module may dress them as decaying; folk may move back in). */
	public java.util.Set<Integer> deserted() {
		return java.util.Collections.unmodifiableSet(this.deserted);
	}

	public boolean isDeserted(int plot) {
		return this.deserted.contains(plot);
	}

	public void desert(int plot) {
		this.deserted.add(plot);
	}

	public boolean resettle(int plot) {
		return this.deserted.remove(plot);
	}

	public void setProsperity(double p) {
		this.prosperity = Math.max(0, Math.min(1, p));
	}

	public void setMood(int mood, String why) {
		this.mood = mood;
		this.why = why;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	/** The trade averages decay a little each day (about ten days' worth). */
	public void decayTrade(double keep) {
		this.exports *= keep;
		this.imports *= keep;
	}

	public long lastCaravanDay() {
		return this.lastCaravanDay;
	}

	public void caravanSent(long day) {
		this.lastCaravanDay = day;
	}

	/** Coin earned from or spent on trade with other towns: folded into the slow averages prosperity reads. */
	public void traded(double earned, double spent) {
		this.exports += earned;
		this.imports += spent;
	}

	/** The api views, rebuilt when a site changes. */
	public List<ResourceSite> views() {
		List<ResourceSite> v = this.views;
		if (v == null) {
			List<ResourceSite> out = new ArrayList<>(this.sites.size());
			for (SimSite s : this.sites) {
				out.add(s.view());
			}
			v = this.views = Collections.unmodifiableList(out);
		}
		return v;
	}

	/** A site's numbers moved: the cached views are stale. */
	public void touched() {
		this.views = null;
	}
}
