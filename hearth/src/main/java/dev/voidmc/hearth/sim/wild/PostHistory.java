package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * A post's life before a player came near it (docs/LIVING_WORLD_2.md §7.2): its record made on its site, its programme
 * laid out ({@link dev.voidmc.hearth.plan.wild.PostPlanner}) and built whole, as it stood long before the world began, then
 * two days of the books so its people have their homes and trades. Touches nothing but the terrain probe, so it runs on
 * the wild worker; the world adopts the result ({@code SimWorld.adoptRecord}) and catches it up from day 0 as any town.
 */
public final class PostHistory {
	/** Days of the books run before day 0 (homes and jobs given out). */
	static final int LEAD_DAYS = 2;

	private PostHistory() {
	}

	/** What a post starts with in store: a few weeks' food, firewood and the makings of repairs. */
	static Map<Goods, Integer> goods(PostType type, int folk) {
		Map<Goods, Integer> g = new EnumMap<>(Goods.class);
		g.put(Goods.FOOD, 60 * Math.max(1, folk));
		g.put(Goods.GRAIN, 40 * Math.max(1, folk));
		g.put(Goods.LOGS, 64);
		g.put(Goods.WOOD, 64);
		g.put(Goods.FUEL, 32);
		g.put(Goods.STONE, 64);
		return g;
	}

	/** Founders for a site: between the type's least and most, by its seed. */
	static int folk(PostSite site) {
		PostType t = site.type();
		return t.minFolk + (int) Math.floorMod(site.seed() >>> 7, (long) (t.maxFolk - t.minFolk + 1));
	}

	/**
	 * Works out a post's record.
	 *
	 * @param id the settlement id the world allocated for it
	 */
	public static SimSettlement run(PostSite site, long id, String name, Culture culture, ResourceKey<Level> dimension, SimRules rules,
		TerrainProbe probe, BlueprintService blueprints) {
		History.Context ctx = History.context(rules, probe, blueprints, dev.voidmc.hearth.plan.wild.WildPlanner.INSTANCE, site.land());
		SimSettlement s = new SimSettlement(id, name, culture, dimension, site.center(), -LEAD_DAYS, SimSettlement.NO_REGION, site.seed());
		PostData d = s.data(PostData.KEY);
		d.site = site;
		d.ground = 0;
		d.movedDay = -LEAD_DAYS;
		s.bind(ctx);
		int folk = folk(site);
		SettlementClock.found(s, ctx, folk, goods(site.type(), folk), 40L * folk);
		// it stood long before the world began: its programme is whole
		List<SimPlot> plots = new ArrayList<>(s.plots());
		for (SimPlot p : plots) {
			s.completePlot(p.id());
		}
		s.recomputeDerived();
		while (s.simTick() < 0) {
			if (SettlementClock.advance(s, 0) <= 0) {
				break;
			}
		}
		return s;
	}

	/** A random for a site's choices at founding (its name): from its seed. */
	static RandomSource random(PostSite site) {
		return RandomSource.create(site.seed() ^ 0x0057_11D0_5EEDL);
	}
}
