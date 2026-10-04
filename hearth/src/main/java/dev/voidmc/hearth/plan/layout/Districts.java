package dev.voidmc.hearth.plan.layout;

import dev.voidmc.hearth.plan.PlanContext;
import dev.voidmc.hearth.plan.model.District;
import dev.voidmc.hearth.plan.model.Site;
import dev.voidmc.hearth.plan.terrain.Ground;

/**
 * Quarters for a town (PLANNING.md, "Districts"), laid out once when it reaches town tier: the market round the
 * square; the civic quarter on the rise; the rich houses on the high ground; the castle hill on the most commanding
 * ground; the crafts (and a harbour) by the water, or at the low, far side from the rich; the poor quarter downwind at
 * the edge on the low side (LIVING_WORLD.md §8.1); residential quarters between; farms outside, on the flattest land.
 * Seeds and weights, so the quarters have soft, organic edges and stay put as the town grows.
 */
public final class Districts {
	private Districts() {
	}

	/** An angle part of the way from {@code a} toward {@code b}, the short way round. */
	private static double blend(double a, double b, double t) {
		double d = Math.IEEEremainder(b - a, 2 * Math.PI);
		return a + d * t;
	}

	public static void plan(PlanContext ctx) {
		var list = ctx.state.districts;
		list.clear();
		int hx = ctx.heartX();
		int hz = ctx.heartZ();
		double core = ctx.coreRadius();
		Site site = ctx.state.site;
		Ground g = ctx.ground;
		list.add(new District(District.Kind.MARKET, hx, hz, 1.25));
		// the high side: the direction within reach where the ground rises most
		double bestRise = -1e9;
		double highAngle = 0;
		for (int a = 0; a < 16; a++) {
			double ang = a * Math.PI / 8;
			int x = hx + (int) Math.round(StrictMath.cos(ang) * core);
			int z = hz + (int) Math.round(StrictMath.sin(ang) * core);
			double rise = g.ground(x, z) - ctx.state.heartY + ctx.hash(x, z, 51) * 0.5;
			if (!g.isWater(x, z) && rise > bestRise) {
				bestRise = rise;
				highAngle = ang;
			}
		}
		if (site != null && Math.hypot(site.riseX() - hx, site.riseZ() - hz) < core * 1.6) {
			list.add(new District(District.Kind.CIVIC, site.riseX(), site.riseZ(), 0.85));
		} else {
			int x = hx + (int) Math.round(StrictMath.cos(highAngle + 0.9) * core * 0.5);
			int z = hz + (int) Math.round(StrictMath.sin(highAngle + 0.9) * core * 0.5);
			list.add(new District(District.Kind.CIVIC, x, z, 0.75));
		}
		list.add(new District(District.Kind.RICH, hx + (int) Math.round(StrictMath.cos(highAngle) * core * 0.95),
			hz + (int) Math.round(StrictMath.sin(highAngle) * core * 0.95), 0.95));
		// the castle hill: the most commanding ground within reach, kept for the lord's keep
		if (ctx.config.living().nobility()) {
			int[] hill = dev.voidmc.hearth.plan.society.Nobility.hill(ctx);
			list.add(new District(District.Kind.CASTLE, hill[0], hill[1], 0.55));
		}
		double lowAngle = highAngle + Math.PI;
		if (site != null && site.hasWater() && site.waterDistance() < core * 2) {
			double ang = StrictMath.atan2(site.waterZ() - hz, site.waterX() - hx);
			double d = Math.min(site.waterDistance(), core * 1.1);
			list.add(new District(District.Kind.CRAFTS, hx + (int) Math.round(StrictMath.cos(ang + 0.5) * d),
				hz + (int) Math.round(StrictMath.sin(ang + 0.5) * d), 0.9));
			list.add(new District(District.Kind.HARBOUR, hx + (int) Math.round(StrictMath.cos(ang) * d),
				hz + (int) Math.round(StrictMath.sin(ang) * d), 0.8));
			lowAngle = ang;
		} else {
			list.add(new District(District.Kind.CRAFTS, hx + (int) Math.round(StrictMath.cos(lowAngle) * core * 0.9),
				hz + (int) Math.round(StrictMath.sin(lowAngle) * core * 0.9), 0.9));
		}
		// the poor quarter: downwind at the edge, on the low side, where the tannery's reek goes
		if (ctx.config.living().stations()) {
			double[] w = ctx.territory().windToward();
			double windAngle = StrictMath.atan2(w[1], w[0]);
			double ang = blend(windAngle, lowAngle, 0.35);
			list.add(new District(District.Kind.POOR, hx + (int) Math.round(StrictMath.cos(ang) * core * 1.05),
				hz + (int) Math.round(StrictMath.sin(ang) * core * 1.05), 0.8));
		}
		for (int side = -1; side <= 1; side += 2) {
			double ang = highAngle + side * Math.PI / 2;
			list.add(new District(District.Kind.RESIDENTIAL, hx + (int) Math.round(StrictMath.cos(ang) * core * 0.9),
				hz + (int) Math.round(StrictMath.sin(ang) * core * 0.9), 1.1));
		}
		// farms beyond the town on its flattest sides
		for (int k = 0; k < 3; k++) {
			double ang = lowAngle + (k - 1) * 2.1;
			list.add(new District(District.Kind.FARMS, hx + (int) Math.round(StrictMath.cos(ang) * core * 2.6),
				hz + (int) Math.round(StrictMath.sin(ang) * core * 2.6), 1.6));
		}
	}
}
